package com.wavv.app

import android.content.Context
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class QueueMode { Shuffle, Off }

data class PlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueMode: QueueMode = QueueMode.Off,
    val errorMessage: String? = null,
)

class PlaybackController(context: Context) {
    private data class PendingPlay(
        val song: Song,
        val queue: List<Song>,
        val recommendedSongIds: Set<Long>,
        val selectionSource: String,
        val recommendationId: String?,
    )

    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var pendingPlay: PendingPlay? = null
    private val playbackStartConfirmation = PlaybackStartConfirmation()
    private var onPlaybackStarted: ((Song) -> Unit)? = null
    private var catalog: Map<Long, Song> = emptyMap()
    private var managedQueue: List<Song> = emptyList()
    private var released = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val retryPolicy = ControllerReconnectPolicy()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnect = Runnable(::connectAttempt)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) confirmRequestedPlaybackStart()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.let(::songFor)
            if (song != null) {
                _state.value = _state.value.copy(
                    song = song,
                    isPlaying = controller?.isPlaying == true,
                    positionMs = 0L,
                    durationMs = controller?.duration.takeIf { it != null && it > 0L } ?: song.durationMs,
                    errorMessage = null,
                )
                confirmRequestedPlaybackStart()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let(::syncQueue)
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackStartConfirmation.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                errorMessage = playbackErrorMessage(error.errorCode, error.message),
            )
        }
    }

    fun connect() {
        if (released || controllerFuture != null || controller != null) return
        mainHandler.removeCallbacks(reconnect)
        retryPolicy.reset()
        connectAttempt()
    }

    private fun connectAttempt() {
        if (released || controllerFuture != null || controller != null) return
        val future = try {
            MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
            ).buildAsync()
        } catch (_: Exception) {
            scheduleReconnectOrReportFailure()
            return
        }
        controllerFuture = future
        future.addListener({
            if (released || controllerFuture !== future) return@addListener
            val connectedController = runCatching { future.get() }.getOrNull() ?: run {
                scheduleReconnectOrReportFailure(future)
                return@addListener
            }
            retryPolicy.reset()
            controller = connectedController
            connectedController.addListener(listener)
            _state.value = _state.value.copy(errorMessage = null)
            syncControllerState(connectedController)
            pendingPlay?.let { pending ->
                pendingPlay = null
                play(
                    pending.song,
                    pending.queue,
                    pending.recommendedSongIds,
                    pending.selectionSource,
                    pending.recommendationId,
                )
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun scheduleReconnectOrReportFailure(future: ListenableFuture<MediaController>? = null) {
        if (future != null) {
            if (controllerFuture !== future) return
            controllerFuture = null
            MediaController.releaseFuture(future)
        }
        val retryDelayMs = retryPolicy.nextDelayMs()
        if (retryDelayMs != null) {
            mainHandler.postDelayed(reconnect, retryDelayMs)
        } else {
            _state.value = _state.value.copy(
                isPlaying = false,
                errorMessage = "Unable to connect to playback service. Try again.",
            )
        }
    }

    fun updateCatalog(songs: List<Song>) {
        catalog = songs.associateBy(Song::id)
        controller?.let(::syncControllerState)
    }

    fun setPlaybackStartedListener(listener: ((Song) -> Unit)?) {
        onPlaybackStarted = listener
    }

    fun play(
        song: Song,
        queue: List<Song>,
        recommendedSongIds: Set<Long> = emptySet(),
        selectionSource: String = "manual",
        recommendationId: String? = null,
    ) {
        if (queue.isEmpty()) return
        val currentController = controller ?: run {
            pendingPlay = PendingPlay(song, queue, recommendedSongIds, selectionSource, recommendationId)
            connect()
            return
        }
        playbackStartConfirmation.request(song.id)
        managedQueue = queue
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _state.value = PlaybackState(
            song = song,
            isPlaying = false,
            durationMs = song.durationMs,
            queue = queue,
        )
        currentController.setMediaItems(
            queue.map { queuedSong ->
                val source = when {
                    queuedSong.id == song.id -> selectionSource
                    queuedSong.id in recommendedSongIds -> "autoplay_recommendation"
                    else -> "queue"
                }
                mediaItem(queuedSong, source, recommendationId.takeIf { queuedSong.id in recommendedSongIds || queuedSong.id == song.id })
            },
            startIndex,
            0L,
        )
        currentController.prepare()
        currentController.play()
    }

    fun toggle() {
        val currentController = controller ?: return
        if (currentController.isPlaying) currentController.pause() else currentController.play()
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun playNext(song: Song) {
        val player = controller ?: return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (song.id == currentId) return
        val existing = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }
        if (existing != null) player.removeMediaItem(existing)
        val insertionIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertionIndex, mediaItem(song, "queue"))
        val managed = managedQueue.filterNot { it.id == song.id }.toMutableList()
        val managedCurrentIndex = managed.indexOfFirst { it.id == currentId }
        managed.add(if (managedCurrentIndex < 0) managed.size else managedCurrentIndex + 1, song)
        managedQueue = managed
        val currentQueue = readQueue(player)
        _state.value = _state.value.copy(queue = currentQueue)
    }

    fun addToQueue(song: Song) {
        val player = controller ?: return
        if ((0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }) return
        player.addMediaItem(mediaItem(song, "queue"))
        managedQueue = managedQueue + song
        _state.value = _state.value.copy(queue = readQueue(player))
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount || index == player.currentMediaItemIndex) return
        val removedId = player.getMediaItemAt(index).mediaId.toLongOrNull() ?: return
        player.removeMediaItem(index)
        managedQueue = managedQueue.filterNot { it.id == removedId }
        _state.value = _state.value.copy(queue = readQueue(player))
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val player = controller ?: return
        if (fromIndex !in 0 until player.mediaItemCount || toIndex !in 0 until player.mediaItemCount) return
        player.moveMediaItem(fromIndex, toIndex)
        managedQueue = readQueue(player)
        _state.value = _state.value.copy(queue = managedQueue)
    }

    fun setQueueMode(mode: QueueMode) {
        val player = controller ?: return
        when (mode) {
            QueueMode.Shuffle -> applyQueueOrder(shuffleUpcoming(readQueue(player), player.currentMediaItemIndex, System.nanoTime()))
            QueueMode.Off -> applyQueueOrder(managedQueue)
        }
        _state.value = _state.value.copy(queue = readQueue(player), queueMode = mode)
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    fun refreshProgress() {
        val song = _state.value.song ?: return
        val currentController = controller ?: return
        val duration = currentController.duration.takeIf { it > 0L } ?: song.durationMs
        _state.value = _state.value.copy(
            positionMs = currentController.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            isPlaying = currentController.isPlaying,
        )
    }

    fun release() {
        released = true
        disconnect()
    }

    fun disconnect() {
        mainHandler.removeCallbacks(reconnect)
        retryPolicy.reset()
        val future = controllerFuture
        controllerFuture = null
        controller?.removeListener(listener)
        controller = null
        if (future != null) MediaController.releaseFuture(future)
    }

    private fun syncControllerState(currentController: MediaController) {
        if (currentController.mediaItemCount == 0) return
        val queue = readQueue(currentController)
        val song = currentController.currentMediaItem?.let(::songFor) ?: return
        managedQueue = queue
        _state.value = PlaybackState(
            song = song,
            isPlaying = currentController.isPlaying,
            positionMs = currentController.currentPosition.coerceAtLeast(0L),
            durationMs = currentController.duration.takeIf { it > 0L } ?: song.durationMs,
            queue = queue,
        )
    }

    private fun syncQueue(player: MediaController) {
        _state.value = _state.value.copy(queue = readQueue(player))
    }

    private fun confirmRequestedPlaybackStart() {
        val currentSongId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
        playbackStartConfirmation.confirm(controller?.isPlaying == true, currentSongId)
            ?.let { songId -> catalog[songId]?.let { onPlaybackStarted?.invoke(it) } }
    }

    private fun readQueue(player: MediaController): List<Song> = (0 until player.mediaItemCount)
        .mapNotNull { index -> songFor(player.getMediaItemAt(index)) }

    private fun applyQueueOrder(order: List<Song>) {
        val player = controller ?: return
        val currentOrder = readQueue(player)
        if (order.size != currentOrder.size || order.map(Song::id).toSet() != currentOrder.map(Song::id).toSet()) return
        order.forEachIndexed { targetIndex, song ->
            val currentIndex = (targetIndex until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id
            } ?: return
            if (currentIndex != targetIndex) player.moveMediaItem(currentIndex, targetIndex)
        }
    }

    private fun songFor(item: MediaItem): Song? {
        val id = item.mediaId.toLongOrNull() ?: return null
        return catalog[id] ?: Song(
            id = id,
            title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" },
            artist = item.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
            album = item.mediaMetadata.albumTitle?.toString().orEmpty().ifBlank { "Unknown album" },
            durationMs = controller?.duration?.takeIf { it > 0L } ?: 0L,
            uri = item.localConfiguration?.uri?.toString().orEmpty(),
            albumArtUri = null,
        )
    }

    private fun mediaItem(song: Song, selectionSource: String = "queue", recommendationId: String? = null) = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setExtras(Bundle().apply {
                    putString(MEDIA_EXTRA_SELECTION_SOURCE, selectionSource)
                    recommendationId?.let { putString(MEDIA_EXTRA_RECOMMENDATION_ID, it) }
                })
                .build(),
        )
        .build()

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}

internal const val MEDIA_EXTRA_SELECTION_SOURCE = "com.wavv.app.SELECTION_SOURCE"
internal const val MEDIA_EXTRA_RECOMMENDATION_ID = "com.wavv.app.RECOMMENDATION_ID"

internal class PlaybackStartConfirmation {
    private var requestedSongId: Long? = null

    fun request(songId: Long) {
        requestedSongId = songId
    }

    fun confirm(isPlaying: Boolean, currentSongId: Long?): Long? {
        val requested = requestedSongId ?: return null
        if (!isPlaying || requested != currentSongId) return null
        requestedSongId = null
        return requested
    }

    fun cancel() {
        requestedSongId = null
    }
}

internal class ControllerReconnectPolicy(
    private val delaysMs: List<Long> = listOf(300L, 900L, 2_000L),
) {
    private var retries = 0

    fun nextDelayMs(): Long? {
        if (retries >= delaysMs.size) return null
        return delaysMs[retries++]
    }

    fun reset() {
        retries = 0
    }
}

internal fun <T> moveQueueItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun nextQueuedSongs(queue: List<Song>, currentSongId: Long, limit: Int = 3): List<Song> {
    if (limit <= 0) return emptyList()
    val currentIndex = queue.indexOfFirst { it.id == currentSongId }
    if (currentIndex < 0) return emptyList()
    return queue.drop(currentIndex + 1).take(limit)
}

internal fun <T> shuffleUpcoming(items: List<T>, currentIndex: Int, seed: Long): List<T> {
    if (items.isEmpty()) return items
    val current = currentIndex.coerceIn(0, items.lastIndex)
    return items.take(current + 1) + items.drop(current + 1).shuffled(Random(seed))
}

internal fun playbackErrorMessage(errorCode: Int, fallbackMessage: String?): String = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This device can't play this audio format."
    else -> fallbackMessage ?: "Playback failed"
}
