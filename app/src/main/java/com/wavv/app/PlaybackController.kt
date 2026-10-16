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

internal enum class NextPlaybackAction { Queue, Autoplay, None }

internal fun nextPlaybackAction(
    hasNextInQueue: Boolean,
    autoplayEnabled: Boolean,
    hasAutoplayCandidate: Boolean,
): NextPlaybackAction = when {
    hasNextInQueue -> NextPlaybackAction.Queue
    autoplayEnabled && hasAutoplayCandidate -> NextPlaybackAction.Autoplay
    else -> NextPlaybackAction.None
}

internal fun autoplayStageCandidateId(
    enabled: Boolean,
    candidateIds: List<Long>,
    occupiedIds: Set<Long>,
): Long? = if (enabled) candidateIds.firstOrNull { it !in occupiedIds } else null

internal enum class QueueActionResult { Added, AlreadyPlaying, AlreadyNext, AlreadyQueued, Unavailable }

internal fun queueActionMessage(result: QueueActionResult): String? = when (result) {
    QueueActionResult.Added -> null
    QueueActionResult.AlreadyPlaying -> "This track is already playing."
    QueueActionResult.AlreadyNext -> "This track is already next."
    QueueActionResult.AlreadyQueued -> "This track is already in the queue."
    QueueActionResult.Unavailable -> "Playback isn't ready yet. Try again."
}

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
    internal val audioOutputState: StateFlow<AudioOutputRoutingState> = AudioOutputRouting.state
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var autoplayCandidates: List<Song> = emptyList()
    private val consumedAutoplayStageIds = mutableSetOf<Long>()
    private val dismissedAutoplayIds = mutableSetOf<Long>()
    private var autoplayManuallyOrdered = false
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
                val songId = mediaItem.mediaId.toLongOrNull()
                val selectionSource = mediaItem.mediaMetadata.extras?.getString(MEDIA_EXTRA_SELECTION_SOURCE)
                if (selectionSource == "autoplay_recommendation" || selectionSource == "autoplay_auto") {
                    this@PlaybackController.playingFrom = "AutoPlay"
                }
                if (mediaItem.mediaMetadata.extras?.getBoolean(MEDIA_EXTRA_AUTOPLAY_STAGED) == true &&
                    songId != null && songId !in consumedAutoplayStageIds
                ) {
                    consumedAutoplayStageIds += songId
                    autoplayCandidates = autoplayCandidates.filterNot { it.id == songId }
                    managedQueue = managedQueue.filterNot { it.id == songId } + song
                    syncAutoplayStaging(controller)
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

    fun refreshAudioRoutes() = AudioOutputRouting.refresh()

    fun selectAudioRoute(routeId: String) = AudioOutputRouting.selectRoute(routeId)

    fun setAudioRouteVolume(routeId: String, volume: Int) = AudioOutputRouting.setRouteVolume(routeId, volume)

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
        dismissedAutoplayIds.clear()
        consumedAutoplayStageIds.clear()
        autoplayManuallyOrdered = false
        autoplayCandidates = reconcileAutoplayRecommendations(
            current = emptyList(),
            ranked = autoplayRecommendations,
            queuedIds = sessionQueue.mapTo(mutableSetOf(), Song::id) + song.id,
            dismissedIds = dismissedAutoplayIds,
            preserveManualOrder = false,
        )
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
        syncAutoplayStaging(currentController)
        currentController.prepare()
        currentController.play()
    }

    fun toggle() {
        val currentController = controller ?: return
        if (currentController.isPlaying) currentController.pause() else currentController.play()
    }

    fun dismissCurrentPlayback() {
        val wasAutoplayEnabled = _state.value.autoplayEnabled
        playbackStartConfirmation.cancel()
        pendingPlay = null
        controller?.run {
            stop()
            clearMediaItems()
        }
        managedQueue = emptyList()
        lastShuffleOrder = null
        dismissedAutoplayIds.clear()
        consumedAutoplayStageIds.clear()
        autoplayManuallyOrdered = false
        autoplayCandidates = emptyList()
        advancingAutoplay = false
        playingFrom = null
        _state.value = PlaybackState(autoplayEnabled = wasAutoplayEnabled)
        _progress.value = PlaybackProgress()
    }

    fun next() {
        val player = controller ?: return
        when (nextPlaybackAction(player.hasNextMediaItem(), _state.value.autoplayEnabled, autoplayCandidates.isNotEmpty())) {
            NextPlaybackAction.Queue -> player.seekToNextMediaItem()
            NextPlaybackAction.Autoplay -> advanceToAutoplayIfNeeded()
            NextPlaybackAction.None -> Unit
        }
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

    internal fun playNext(song: Song, fromAutoplay: Boolean = false): QueueActionResult {
        val player = controller ?: return QueueActionResult.Unavailable
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return QueueActionResult.Unavailable
        if (song.id == currentId) {
            val insertionIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            player.addMediaItem(insertionIndex, mediaItem(song, "queue"))
            managedQueue = readQueue(player)
            lastShuffleOrder = null
            syncAutoplayStaging(player)
            syncQueue(player)
            return QueueActionResult.Added
        }
        val currentIndex = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == currentId
        } ?: return QueueActionResult.Unavailable
        val existing = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }
        val cameFromAutoplay = fromAutoplay || autoplayCandidates.any { it.id == song.id }
        if (existing == currentIndex + 1) {
            if (cameFromAutoplay) {
                removeAutoplayCandidate(song.id)
                promoteStagedAutoplay(song.id, player)
                managedQueue = readQueue(player)
                lastShuffleOrder = null
                syncAutoplayStaging(player)
                syncQueue(player)
            }
            return QueueActionResult.AlreadyNext
        }
        if (cameFromAutoplay) {
            removeAutoplayCandidate(song.id)
            promoteStagedAutoplay(song.id, player)
        }
        if (existing != null) player.removeMediaItem(existing)
        val updatedCurrentIndex = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == currentId
        } ?: return QueueActionResult.Unavailable
        val insertionIndex = (updatedCurrentIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertionIndex, mediaItem(song, if (cameFromAutoplay) "autoplay_recommendation" else "queue"))
        managedQueue = readQueue(player)
        lastShuffleOrder = null
        syncAutoplayStaging(player)
        syncQueue(player)
        return QueueActionResult.Added
    }

    internal fun addToQueue(song: Song, fromAutoplay: Boolean = false): QueueActionResult {
        val player = controller ?: return QueueActionResult.Unavailable
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val cameFromAutoplay = fromAutoplay || autoplayCandidates.any { it.id == song.id }
        if (currentId == song.id) {
            if (cameFromAutoplay) removeAutoplayCandidate(song.id)
            val insertionIndex = pendingAutoplayStageIndex(player) ?: player.mediaItemCount
            player.addMediaItem(
                insertionIndex,
                mediaItem(song, if (cameFromAutoplay) "autoplay_recommendation" else "queue"),
            )
            managedQueue = readQueue(player)
            lastShuffleOrder = null
            syncAutoplayStaging(player)
            syncQueue(player)
            return QueueActionResult.Added
        }
        val existing = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id }
        if (existing != null) {
            if (cameFromAutoplay) {
                removeAutoplayCandidate(song.id)
                promoteStagedAutoplay(song.id, player)
                managedQueue = readQueue(player)
                lastShuffleOrder = null
                syncAutoplayStaging(player)
                syncQueue(player)
            }
            return QueueActionResult.AlreadyQueued
        }
        if (cameFromAutoplay) removeAutoplayCandidate(song.id)
        val insertionIndex = pendingAutoplayStageIndex(player) ?: player.mediaItemCount
        player.addMediaItem(insertionIndex, mediaItem(song, if (cameFromAutoplay) "autoplay_recommendation" else "queue"))
        managedQueue = readQueue(player)
        lastShuffleOrder = null
        syncAutoplayStaging(player)
        syncQueue(player)
        return QueueActionResult.Added
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
        val existing = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id
        }
        removeAutoplayCandidate(song.id)
        promoteStagedAutoplay(song.id, player)
        playingFrom = "AutoPlay"
        _state.value = _state.value.copy(playingFrom = playingFrom)
        managedQueue = managedQueue.filterNot { it.id == song.id } + song
        if (existing != null) {
            // Keep an already queued or played copy in place; selecting an AutoPlay row
            // must never rebuild or reorder the existing session queue.
            syncAutoplayStaging(player)
            player.seekTo(existing, 0L)
            player.play()
            return
        }
        syncAutoplayStaging(player)
        // Place this selection before the staged suggestion so later autoplay keeps its order.
        val targetIndex = pendingAutoplayStageIndex(player) ?: player.mediaItemCount
        player.addMediaItem(targetIndex, mediaItem(song, "autoplay_recommendation"))
        lastShuffleOrder = null
        managedQueue = readQueue(player)
        syncAutoplayStaging(player)
        player.seekTo(targetIndex, 0L)
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        val absoluteIndex = index
        if (absoluteIndex !in 0 until player.mediaItemCount || absoluteIndex == player.currentMediaItemIndex) return
        player.getMediaItemAt(absoluteIndex).mediaId.toLongOrNull() ?: return
        player.removeMediaItem(absoluteIndex)
        managedQueue = readQueue(player)
        lastShuffleOrder = null
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

    internal fun moveAutoplayToQueue(song: Song, queueIndex: Int): QueueActionResult {
        val player = controller ?: return QueueActionResult.Unavailable
        if (player.currentMediaItem?.mediaId?.toLongOrNull() == song.id) {
            return QueueActionResult.AlreadyPlaying
        }
        val existingIndex = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull() == song.id
        }
        val stagedIndex = existingIndex?.takeIf { isPendingAutoplayStage(player, it) }
        if (existingIndex != null && stagedIndex == null) {
            return QueueActionResult.AlreadyQueued
        }
        if (autoplayCandidates.none { it.id == song.id }) return QueueActionResult.Unavailable

        val insertionIndex = queueIndex.coerceIn(0, readQueue(player).size)
        autoplayCandidates = autoplayCandidates.filterNot { it.id == song.id }
        if (stagedIndex != null) {
            promoteStagedAutoplay(song.id, player)
            player.moveMediaItem(stagedIndex, insertionIndex)
        } else {
            player.addMediaItem(insertionIndex, mediaItem(song, "autoplay_recommendation"))
        }
        managedQueue = readQueue(player)
        lastShuffleOrder = readUpcomingQueue(player).map(Song::id)
        updateAutoplayState()
        syncAutoplayStaging(player)
        syncQueue(player)
        return QueueActionResult.Added
    }

    fun setQueueMode(mode: QueueMode) {
        val player = controller ?: return
        val currentOrder = readQueue(player)
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, currentOrder.lastIndex.coerceAtLeast(0))
        val stablePrefix = currentOrder.take(currentIndex + 1)
        val upcoming = currentOrder.drop(currentIndex + 1)
        if (mode == QueueMode.Shuffle) {
            val cachedOrder = lastShuffleOrder.orEmpty().filter { id -> upcoming.any { it.id == id } }
            val cachedIsComplete = cachedOrder.size == upcoming.size &&
                cachedOrder.groupingBy { it }.eachCount() == upcoming.groupingBy(Song::id).eachCount()
            val shuffled = if (cachedIsComplete) {
                val remaining = upcoming.toMutableList()
                cachedOrder.mapNotNull { id ->
                    val index = remaining.indexOfFirst { it.id == id }
                    if (index < 0) null else remaining.removeAt(index)
                }
            } else {
                upcoming.shuffled(Random(System.nanoTime())).also { lastShuffleOrder = it.map(Song::id) }
            }
            if (managedQueue.isEmpty()) managedQueue = currentOrder
            applyQueueOrder(stablePrefix + shuffled)
        } else {
            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
            val managedCurrentIndex = managedQueue.indexOfFirst { it.id == currentId }
            val preferred = if (managedCurrentIndex >= 0) managedQueue.drop(managedCurrentIndex + 1) else emptyList()
            val remaining = upcoming.toMutableList()
            val restored = preferred.mapNotNull { preferredSong ->
                val index = remaining.indexOfFirst { it.id == preferredSong.id }
                if (index < 0) null else remaining.removeAt(index)
            } + remaining
            applyQueueOrder(stablePrefix + restored)
        }
        _state.value = _state.value.copy(
            queue = readQueue(player),
            currentQueueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            queueMode = mode,
        )
    }

    fun moveAutoplayItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in autoplayCandidates.indices || toIndex !in autoplayCandidates.indices || fromIndex == toIndex) return
        autoplayCandidates = autoplayCandidates.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        autoplayManuallyOrdered = true
        updateAutoplayState()
        syncAutoplayStaging(controller)
    }

    fun removeAutoplaySong(song: Song) {
        dismissedAutoplayIds += song.id
        autoplayCandidates = autoplayCandidates.filterNot { it.id == song.id }
        controller?.let { player ->
            pendingAutoplayStageIndex(player, song.id)?.let(player::removeMediaItem)
            syncAutoplayStaging(player)
        }
        updateAutoplayState()
    }

    fun updateAutoplayRecommendations(recommendations: List<Song>) {
        val player = controller?.takeIf { it.mediaItemCount > 0 } ?: return
        val queuedIds = readQueue(player).mapTo(mutableSetOf(), Song::id)
        autoplayCandidates = reconcileAutoplayRecommendations(
            current = autoplayCandidates,
            ranked = recommendations,
            queuedIds = queuedIds,
            dismissedIds = dismissedAutoplayIds,
            preserveManualOrder = autoplayManuallyOrdered,
        )
        updateAutoplayState()
        syncAutoplayStaging(player)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_AUTOPLAY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(autoplayEnabled = enabled)
        if (!enabled) {
            removePendingAutoplayStage(controller)
            controller?.let(::syncQueue)
        } else if (controller?.playbackState == Player.STATE_ENDED) {
            advanceToAutoplayIfNeeded()
        } else {
            syncAutoplayStaging(controller)
        }
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    fun setCurrentTrackUHQ(songId: Long, enabled: Boolean) {
        UHQPlaybackPreference.setTrackOverride(appContext, songId, enabled)
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
        player.addMediaItem(mediaItem(nextSong, "autoplay_auto"))
        managedQueue = managedQueue + nextSong
        syncAutoplayStaging(player)
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
        .filterNot { index -> isPendingAutoplayStage(player, index) }
        .mapNotNull { index -> songFor(player.getMediaItemAt(index)) }

    private fun isPendingAutoplayStage(player: Player, index: Int): Boolean {
        if (index <= player.currentMediaItemIndex || index !in 0 until player.mediaItemCount) return false
        val item = player.getMediaItemAt(index)
        val id = item.mediaId.toLongOrNull() ?: return false
        return item.mediaMetadata.extras?.getBoolean(MEDIA_EXTRA_AUTOPLAY_STAGED) == true &&
            id !in consumedAutoplayStageIds
    }

    private fun pendingAutoplayStageIndex(player: Player, songId: Long? = null): Int? =
        (player.currentMediaItemIndex + 1 until player.mediaItemCount).firstOrNull { index ->
            isPendingAutoplayStage(player, index) &&
                (songId == null || player.getMediaItemAt(index).mediaId.toLongOrNull() == songId)
        }

    private fun promoteStagedAutoplay(songId: Long, player: Player) {
        val index = pendingAutoplayStageIndex(player, songId) ?: return
        consumedAutoplayStageIds += songId
        val item = player.getMediaItemAt(index)
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            remove(MEDIA_EXTRA_AUTOPLAY_STAGED)
        }
        player.replaceMediaItem(
            index,
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build(),
        )
    }

    private fun removePendingAutoplayStage(player: Player?) {
        if (player == null) return
        val stageIndices = (player.currentMediaItemIndex + 1 until player.mediaItemCount)
            .filter { isPendingAutoplayStage(player, it) }
        stageIndices.asReversed().forEach(player::removeMediaItem)
    }

    private fun syncAutoplayStaging(player: MediaController?) {
        if (player == null || player.mediaItemCount == 0) return
        val oldStageIndex = pendingAutoplayStageIndex(player)
        val occupiedIds = (0 until player.mediaItemCount)
            .filterNot { it == oldStageIndex }
            .mapNotNullTo(mutableSetOf()) { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        val nextSongId = autoplayStageCandidateId(
            enabled = _state.value.autoplayEnabled,
            candidateIds = autoplayCandidates.map(Song::id),
            occupiedIds = occupiedIds,
        )
        val nextSong = autoplayCandidates.firstOrNull { it.id == nextSongId }
        if (oldStageIndex != null && nextSong != null) {
            val stagedItem = player.getMediaItemAt(oldStageIndex)
            if (stagedItem.mediaId.toLongOrNull() == nextSong.id &&
                stagedItem.localConfiguration?.uri?.toString() == nextSong.uri
            ) {
                return
            }
        }
        if (nextSong == null) {
            oldStageIndex?.let(player::removeMediaItem)
            return
        }
        consumedAutoplayStageIds.remove(nextSong.id)
        val stagedItem = mediaItem(nextSong, "autoplay_auto", stagedAutoplay = true)
        if (oldStageIndex != null) player.replaceMediaItem(oldStageIndex, stagedItem)
        else player.addMediaItem(player.mediaItemCount, stagedItem)
    }

    private fun applyQueueOrder(order: List<Song>) {
        val player = controller ?: return
        val currentOrder = readQueue(player)
        if (order.size != currentOrder.size ||
            order.groupingBy(Song::id).eachCount() != currentOrder.groupingBy(Song::id).eachCount()
        ) return
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

    private fun mediaItem(
        song: Song,
        selectionSource: String = "queue",
        recommendationId: String? = null,
        stagedAutoplay: Boolean = false,
    ) = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setAlbumArtist(song.albumArtist)
                .setComposer(song.composer)
                .setWriter(song.writer)
                .setGenre(song.fileGenreNames().takeIf(List<String>::isNotEmpty)?.joinToString(" / "))
                .setRecordingYear(song.year)
                .setTrackNumber(song.trackNumber)
                .setDiscNumber(song.discNumber)
                .setTotalTrackCount(song.trackCount)
                .setExtras(Bundle().apply {
                    putString(MEDIA_EXTRA_SELECTION_SOURCE, selectionSource)
                    recommendationId?.let { putString(MEDIA_EXTRA_RECOMMENDATION_ID, it) }
                    putBoolean(MEDIA_EXTRA_AUTOPLAY_STAGED, stagedAutoplay)
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
internal const val MEDIA_EXTRA_AUTOPLAY_STAGED = "com.wavv.app.AUTOPLAY_STAGED"

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
