package com.wavv.app

import android.content.Context
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val errorMessage: String? = null,
)

class PlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var pendingPlay: Pair<Song, List<Song>>? = null
    private var catalog: Map<Long, Song> = emptyMap()
    private var released = false
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()
    private val queuedSongs = mutableMapOf<Long, Song>()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.let(::songFor)
            if (song != null) {
                _state.value = _state.value.copy(
                    song = song,
                    positionMs = 0L,
                    durationMs = controller?.duration.takeIf { it != null && it > 0L } ?: song.durationMs,
                    errorMessage = null,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                isPlaying = false,
                errorMessage = error.message ?: "Playback failed",
            )
        }
    }

    init {
        controllerFuture.addListener({
            if (released) return@addListener
            val connectedController = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = connectedController
            connectedController.addListener(listener)
            syncControllerState(connectedController)
            pendingPlay?.let { (song, queue) ->
                pendingPlay = null
                play(song, queue)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun updateCatalog(songs: List<Song>) {
        catalog = songs.associateBy(Song::id)
        controller?.let(::syncControllerState)
    }

    fun play(song: Song, queue: List<Song>) {
        val currentController = controller ?: run {
            pendingPlay = song to queue
            return
        }
        if (queue.isEmpty()) return
        queuedSongs.clear()
        queuedSongs.putAll(queue.associateBy(Song::id))
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        currentController.setMediaItems(queue.map(::mediaItem), startIndex, 0L)
        currentController.prepare()
        currentController.play()
        _state.value = PlaybackState(
            song = song,
            isPlaying = true,
            durationMs = song.durationMs,
            queue = queue,
            errorMessage = null,
        )
    }

    fun toggle() {
        val currentController = controller ?: return
        if (currentController.isPlaying) currentController.pause() else currentController.play()
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
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
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        controllerFuture.cancel(false)
    }

    private fun syncControllerState(currentController: MediaController) {
        if (currentController.mediaItemCount == 0) return
        val queue = (0 until currentController.mediaItemCount)
            .mapNotNull { index -> songFor(currentController.getMediaItemAt(index)) }
        val song = currentController.currentMediaItem?.let(::songFor) ?: return
        queuedSongs.clear()
        queuedSongs.putAll(queue.associateBy(Song::id))
        _state.value = PlaybackState(
            song = song,
            isPlaying = currentController.isPlaying,
            positionMs = currentController.currentPosition.coerceAtLeast(0L),
            durationMs = currentController.duration.takeIf { it > 0L } ?: song.durationMs,
            queue = queue,
        )
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

    private fun mediaItem(song: Song) = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build(),
        )
        .build()
}
