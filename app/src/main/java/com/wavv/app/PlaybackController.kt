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
    val currentQueueIndex: Int = 0,
    val queueMode: QueueMode = QueueMode.Off,
    val autoplaySongs: List<Song> = emptyList(),
    val autoplayEnabled: Boolean = true,
    val playingFrom: String? = null,
    val isUHQEnhanced: Boolean = false,
    val errorMessage: String? = null,
)

data class PlaybackProgress(val positionMs: Long = 0L, val durationMs: Long = 0L)

class PlaybackController(context: Context) {
    private data class PendingPlay(
        val song: Song,
        val queue: List<Song>,
        val autoplayRecommendations: List<Song>,
        val selectionSource: String,
        val recommendationId: String?,
        val playingFrom: String?,
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var autoplayCandidates: List<Song> = emptyList()
    private var playingFrom: String? = null
    private var lastShuffleOrder: List<Long>? = null
    private var advancingAutoplay = false
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
    private val _state = MutableStateFlow(
        PlaybackState(autoplayEnabled = preferences.getBoolean(PREF_AUTOPLAY_ENABLED, true)),
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            refreshProgress()
            if (!isPlaying) {
                _state.value = _state.value.copy(
                    positionMs = _progress.value.positionMs,
                    durationMs = _progress.value.durationMs,
                )
            }
            if (isPlaying) confirmRequestedPlaybackStart()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.let(::songFor)
            if (song != null) {
                advancingAutoplay = false
                if (mediaItem.mediaMetadata.extras?.getString(MEDIA_EXTRA_SELECTION_SOURCE) == "autoplay_recommendation") {
                    this@PlaybackController.playingFrom = "AutoPlay"
                }
                _state.value = _state.value.copy(
                    song = song,
                    isUHQEnhanced = mediaItem.mediaMetadata.extras?.containsKey(MEDIA_EXTRA_UHQ_SOURCE_URI) == true,
                    isPlaying = controller?.isPlaying == true,
                    positionMs = 0L,
                    durationMs = controller?.duration.takeIf { it != null && it > 0L } ?: song.durationMs,
                    queue = controller?.let(::readQueue).orEmpty(),
                    currentQueueIndex = controller?.currentMediaItemIndex?.coerceAtLeast(0) ?: 0,
                    autoplaySongs = autoplayCandidates.take(AUTOPLAY_VISIBLE_LIMIT),
                    playingFrom = playingFrom,
                    errorMessage = null,
                )
                refreshProgress()
                confirmRequestedPlaybackStart()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) advanceToAutoplayIfNeeded()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val current = controller?.currentMediaItem?.mediaMetadata?.extras
            _state.value = _state.value.copy(
                isUHQEnhanced = current?.containsKey(MEDIA_EXTRA_UHQ_SOURCE_URI) == true,
            )
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
                    pending.autoplayRecommendations,
                    pending.selectionSource,
                    pending.recommendationId,
                    pending.playingFrom,
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
        autoplayRecommendations: List<Song> = emptyList(),
        selectionSource: String = "manual",
        recommendationId: String? = null,
        playingFrom: String? = null,
    ) {
        if (queue.isEmpty()) return
        val currentController = controller ?: run {
            pendingPlay = PendingPlay(song, queue, autoplayRecommendations, selectionSource, recommendationId, playingFrom)
            connect()
            return
        }
        playbackStartConfirmation.request(song.id)
        val sourceQueue = queue.distinctBy(Song::id)
        val selectedIndex = sourceQueue.indexOfFirst { it.id == song.id }
        // A direct selection starts a fresh session at the selected song. Keep its
        // remaining source order after it, but do not put earlier tracks ahead of it.
        val sessionQueue = if (selectedIndex >= 0) {
            sourceQueue.drop(selectedIndex)
        } else {
            listOf(song) + sourceQueue
        }
        managedQueue = sessionQueue
        lastShuffleOrder = null
        autoplayCandidates = autoplayRecommendations.asSequence()
            .filterNot { candidate -> candidate.id == song.id || sessionQueue.any { it.id == candidate.id } }
            .distinctBy(Song::id)
            .toList()
        this.playingFrom = playingFrom
        advancingAutoplay = false
        val startIndex = 0
        _state.value = PlaybackState(
            song = song,
            isPlaying = false,
            durationMs = song.durationMs,
            queue = sessionQueue,
            currentQueueIndex = startIndex,
            queueMode = QueueMode.Off,
            autoplaySongs = autoplayCandidates.take(AUTOPLAY_VISIBLE_LIMIT),
            autoplayEnabled = _state.value.autoplayEnabled,
            playingFrom = playingFrom,
        )
        _progress.value = PlaybackProgress(durationMs = song.durationMs)
        currentController.setMediaItems(
            sessionQueue.map { queuedSong ->
                val source = when {
                    queuedSong.id == song.id -> selectionSource
                    else -> "queue"
                }
                mediaItem(queuedSong, source, recommendationId.takeIf { queuedSong.id == song.id })
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

    fun playNext(song: Song, fromAutoplay: Boolean = false) {
        val player = controller ?: return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (song.id == currentId) return
        val cameFromAutoplay = fromAutoplay || autoplayCandidates.any { it.id == song.id }
        if (cameFromAutoplay) removeAutoplayCandidate(song.id)
        val existing = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }
        if (existing != null) player.removeMediaItem(existing)
        val currentIndex = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == currentId
        } ?: return
        val insertionIndex = (currentIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertionIndex, mediaItem(song, if (cameFromAutoplay) "autoplay_recommendation" else "queue"))
        val managed = managedQueue.filterNot { it.id == song.id }.toMutableList()
        val managedCurrentIndex = managed.indexOfFirst { it.id == currentId }
        managed.add(if (managedCurrentIndex < 0) managed.size else managedCurrentIndex + 1, song)
        managedQueue = managed
        lastShuffleOrder = null
        syncQueue(player)
    }

    fun addToQueue(song: Song, fromAutoplay: Boolean = false) {
        val player = controller ?: return
        if ((0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }) return
        val cameFromAutoplay = fromAutoplay || autoplayCandidates.any { it.id == song.id }
        if (cameFromAutoplay) removeAutoplayCandidate(song.id)
        player.addMediaItem(mediaItem(song, if (cameFromAutoplay) "autoplay_recommendation" else "queue"))
        managedQueue = managedQueue + song
        lastShuffleOrder = null
        syncQueue(player)
    }

    fun playQueueSong(song: Song) {
        val player = controller ?: return
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id
        } ?: return
        player.seekTo(index, 0L)
        player.play()
    }

    fun playAutoplaySong(song: Song) {
        val player = controller ?: return
        if (autoplayCandidates.none { it.id == song.id }) return
        removeAutoplayCandidate(song.id)
        val existing = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id
        }
        playingFrom = "AutoPlay"
        _state.value = _state.value.copy(playingFrom = playingFrom)
        if (existing != null) {
            // Keep an already queued or played copy in place; selecting an AutoPlay row
            // must never rebuild or reorder the existing session queue.
            player.seekTo(existing, 0L)
            player.play()
            return
        }
        // Append to the current session, preserving its played history and queue order.
        val targetIndex = player.mediaItemCount
        player.addMediaItem(targetIndex, mediaItem(song, "autoplay_recommendation"))
        managedQueue = managedQueue + song
        lastShuffleOrder = null
        player.seekTo(targetIndex, 0L)
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        val absoluteIndex = index
        if (absoluteIndex !in 0 until player.mediaItemCount || absoluteIndex == player.currentMediaItemIndex) return
        val removedId = player.getMediaItemAt(absoluteIndex).mediaId.toLongOrNull() ?: return
        player.removeMediaItem(absoluteIndex)
        managedQueue = managedQueue.filterNot { it.id == removedId }
        lastShuffleOrder = lastShuffleOrder?.filterNot { it == removedId }
        syncQueue(player)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val player = controller ?: return
        val currentOrder = readQueue(player)
        if (fromIndex !in currentOrder.indices || toIndex !in currentOrder.indices) return
        player.moveMediaItem(fromIndex, toIndex)
        val reorderedQueue = currentOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        managedQueue = reorderedQueue
        lastShuffleOrder = readUpcomingQueue(player).map(Song::id)
        syncQueue(player)
    }

    fun setQueueMode(mode: QueueMode) {
        val player = controller ?: return
        val currentOrder = readQueue(player)
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, currentOrder.lastIndex.coerceAtLeast(0))
        val stablePrefix = currentOrder.take(currentIndex + 1)
        val upcoming = currentOrder.drop(currentIndex + 1)
        if (mode == QueueMode.Shuffle) {
            val cachedOrder = lastShuffleOrder.orEmpty().filter { id -> upcoming.any { it.id == id } }
            val cachedIsComplete = cachedOrder.size == upcoming.size && cachedOrder.toSet() == upcoming.mapTo(mutableSetOf(), Song::id)
            val shuffled = if (cachedIsComplete) {
                cachedOrder.mapNotNull { id -> upcoming.firstOrNull { it.id == id } }
            } else {
                upcoming.shuffled(Random(System.nanoTime())).also { lastShuffleOrder = it.map(Song::id) }
            }
            if (managedQueue.isEmpty()) managedQueue = currentOrder
            applyQueueOrder(stablePrefix + shuffled)
        } else {
            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
            val managedCurrentIndex = managedQueue.indexOfFirst { it.id == currentId }
            val preferred = if (managedCurrentIndex >= 0) managedQueue.drop(managedCurrentIndex + 1) else emptyList()
            val upcomingIds = upcoming.mapTo(mutableSetOf(), Song::id)
            val restored = preferred.filter { it.id in upcomingIds } +
                upcoming.filter { candidate -> preferred.none { it.id == candidate.id } }
            applyQueueOrder(stablePrefix + restored)
        }
        _state.value = _state.value.copy(
            queue = readQueue(player),
            currentQueueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            queueMode = mode,
        )
    }

    fun moveAutoplayItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in autoplayCandidates.indices || toIndex !in autoplayCandidates.indices) return
        autoplayCandidates = autoplayCandidates.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        updateAutoplayState()
    }

    fun removeAutoplaySong(song: Song) {
        if (autoplayCandidates.none { it.id == song.id }) return
        autoplayCandidates = autoplayCandidates.filterNot { it.id == song.id }
        updateAutoplayState()
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_AUTOPLAY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(autoplayEnabled = enabled)
        if (enabled && controller?.playbackState == Player.STATE_ENDED) advanceToAutoplayIfNeeded()
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
        _progress.value = PlaybackProgress(
            positionMs = currentController.currentPosition.coerceAtLeast(0L),
            durationMs = currentController.duration.takeIf { it > 0L } ?: song.durationMs,
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
        val currentQueue = readQueue(currentController)
        if (managedQueue.isEmpty()) managedQueue = currentQueue
        val item = currentController.currentMediaItem ?: return
        val song = songFor(item) ?: return
        _state.value = PlaybackState(
            song = song,
            isPlaying = currentController.isPlaying,
            positionMs = currentController.currentPosition.coerceAtLeast(0L),
            durationMs = currentController.duration.takeIf { it > 0L } ?: song.durationMs,
            queue = currentQueue,
            currentQueueIndex = currentController.currentMediaItemIndex.coerceAtLeast(0),
            queueMode = _state.value.queueMode,
            autoplaySongs = autoplayCandidates.take(AUTOPLAY_VISIBLE_LIMIT),
            autoplayEnabled = _state.value.autoplayEnabled,
            playingFrom = playingFrom,
            isUHQEnhanced = item.mediaMetadata.extras?.containsKey(MEDIA_EXTRA_UHQ_SOURCE_URI) == true,
        )
        refreshProgress()
    }

    private fun syncQueue(player: MediaController) {
        _state.value = _state.value.copy(
            queue = readQueue(player),
            currentQueueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
        )
    }

    private fun readUpcomingQueue(player: MediaController): List<Song> = readQueue(player)
        .drop((player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount))

    private fun updateAutoplayState() {
        _state.value = _state.value.copy(autoplaySongs = autoplayCandidates.take(AUTOPLAY_VISIBLE_LIMIT))
    }

    private fun removeAutoplayCandidate(songId: Long) {
        autoplayCandidates = autoplayCandidates.filterNot { it.id == songId }
        updateAutoplayState()
    }

    private fun advanceToAutoplayIfNeeded() {
        val player = controller ?: return
        if (advancingAutoplay || !_state.value.autoplayEnabled || player.hasNextMediaItem()) return
        val nextSong = autoplayCandidates.firstOrNull() ?: return
        advancingAutoplay = true
        autoplayCandidates = autoplayCandidates.drop(1)
        updateAutoplayState()
        val index = player.mediaItemCount
        player.addMediaItem(mediaItem(nextSong, "autoplay_recommendation"))
        managedQueue = managedQueue + nextSong
        playingFrom = "AutoPlay"
        _state.value = _state.value.copy(playingFrom = playingFrom)
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
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
        const val AUTOPLAY_VISIBLE_LIMIT = 10
        const val PREFERENCES_NAME = "playback_preferences"
        const val PREF_AUTOPLAY_ENABLED = "autoplay_enabled"
    }
}

internal const val MEDIA_EXTRA_SELECTION_SOURCE = "com.wavv.app.SELECTION_SOURCE"
internal const val MEDIA_EXTRA_RECOMMENDATION_ID = "com.wavv.app.RECOMMENDATION_ID"
internal const val MEDIA_EXTRA_UHQ_SOURCE_URI = "com.wavv.app.UHQ_SOURCE_URI"

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
