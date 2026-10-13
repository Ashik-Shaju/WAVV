package com.wavv.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object PlaybackPlayerFactory {
    @OptIn(markerClass = [UnstableApi::class])
    fun create(context: Context): ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true),
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
}

private const val MEDIA_EXTRA_UHQ_INTERNAL_SWITCH = "com.wavv.app.UHQ_INTERNAL_SWITCH"

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var libraryStore: LibraryStore
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feedback = PlaybackFeedbackReducer(WavvSession.id)
    private val feedbackHandler = Handler(Looper.getMainLooper())
    private var previousSongId: Long? = null
    private var previousItemIndex: Int = C.INDEX_UNSET
    private var uhqSongId: String? = null
    @Volatile private var uhqWorkName: String? = null
    private var uhqJob: Job? = null
    private var equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
    private var audioEqualizer: Equalizer? = null
    private var unavailableEqualizerSessionId = C.AUDIO_SESSION_ID_UNSET
    private val applyEqualizer = Runnable { syncEqualizer() }
    private var uhqPreferences: SharedPreferences? = null
    private val uhqPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == UHQPlaybackPreference.preferenceKey()) {
            feedbackHandler.post {
                player?.currentMediaItem?.let { item ->
                    if (UHQPlaybackPreference.isEnabled(this)) updateUHQ(item, force = true)
                    else disableUHQ(item)
                }
            }
        } else if (key?.startsWith(AUDIO_EQUALIZER_PREFERENCE_PREFIX) == true) {
            feedbackHandler.removeCallbacks(applyEqualizer)
            feedbackHandler.postDelayed(applyEqualizer, EQUALIZER_UPDATE_DELAY_MS)
        }
    }
    private val feedbackSampler = object : Runnable {
        override fun run() {
            player?.let { current ->
                feedback.sample(current.currentPosition, SystemClock.elapsedRealtime(), current.duration)
            }
            if (player != null) feedbackHandler.postDelayed(this, FEEDBACK_SAMPLE_INTERVAL_MS)
        }
    }
    private val feedbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = player ?: return
            val item = current.currentMediaItem ?: return
            val id = item.mediaId.toLongOrNull() ?: return
            if (previousSongId != id) {
                onTrackTransition(item, TrackTransition.INITIAL, current.currentMediaItemIndex)
            }
            record(feedback.setPlaying(isPlaying, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val current = player ?: return
            val item = mediaItem ?: return
            if (item.mediaMetadata.extras?.getBoolean(MEDIA_EXTRA_UHQ_INTERNAL_SWITCH) == true) return
            val nextIndex = current.currentMediaItemIndex
            val transition = when {
                previousSongId == null -> TrackTransition.INITIAL
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> TrackTransition.AUTOMATIC
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> TrackTransition.REPEAT
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextIndex > previousItemIndex -> TrackTransition.FORWARD_SKIP
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextIndex < previousItemIndex -> TrackTransition.PREVIOUS
                else -> TrackTransition.QUEUE_CHANGE
            }
            onTrackTransition(item, transition, nextIndex)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                record(feedback.onEnded(SystemClock.elapsedRealtime(), System.currentTimeMillis()))
            }
        }

        @OptIn(markerClass = [UnstableApi::class])
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (equalizerSessionId == audioSessionId) return
            runCatching { audioEqualizer?.release() }
            audioEqualizer = null
            equalizerSessionId = audioSessionId
            unavailableEqualizerSessionId = C.AUDIO_SESSION_ID_UNSET
            syncEqualizer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        PlaybackEffectStatus.reset()
        PlaybackEffectStatus.updateUHQ(
            trackId = null,
            status = if (UHQPlaybackPreference.isEnabled(this)) UhqPlaybackStatus.Waiting else UhqPlaybackStatus.Disabled,
        )
        PlaybackEffectStatus.updateEqualizer(
            if (AudioEqualizerSettings.isEnabled(this)) EqualizerPlaybackStatus.Waiting else EqualizerPlaybackStatus.Disabled,
        )
        libraryStore = LibraryStore(WavvDatabase.get(this))
        uhqPreferences = UHQPlaybackPreference.preferences(this).also {
            it.registerOnSharedPreferenceChangeListener(uhqPreferenceListener)
        }
        player = PlaybackPlayerFactory.create(this)
        player?.addListener(feedbackListener)
        mediaSession = MediaSession.Builder(this, player!!).build()
        feedbackHandler.postDelayed(feedbackSampler, FEEDBACK_SAMPLE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @OptIn(markerClass = [UnstableApi::class])
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        uhqPreferences?.unregisterOnSharedPreferenceChangeListener(uhqPreferenceListener)
        uhqPreferences = null
        uhqJob?.cancel()
        feedbackHandler.removeCallbacks(feedbackSampler)
        feedbackHandler.removeCallbacks(applyEqualizer)
        runCatching { audioEqualizer?.release() }
        audioEqualizer = null
        PlaybackEffectStatus.reset()
        player?.removeListener(feedbackListener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        eventScope.cancel()
        super.onDestroy()
    }

    private fun syncEqualizer() {
        val sessionId = equalizerSessionId
        val enabled = AudioEqualizerSettings.isEnabled(this)
        if (sessionId <= 0) {
            PlaybackEffectStatus.updateEqualizer(
                if (enabled) EqualizerPlaybackStatus.Waiting else EqualizerPlaybackStatus.Disabled,
            )
            return
        }
        if (unavailableEqualizerSessionId == sessionId) {
            PlaybackEffectStatus.updateEqualizer(EqualizerPlaybackStatus.Unavailable)
            return
        }
        try {
            val effect = audioEqualizer ?: Equalizer(0, sessionId).also { audioEqualizer = it }
            val bandCount = effect.numberOfBands.toInt()
            val levels = effect.bandLevelRange
            if (bandCount <= 0 || levels.size < 2) error("Android returned invalid equalizer capabilities")
            val gains = AudioEqualizerSettings.activeProfile(this).gainsDb
            for (band in 0 until bandCount) {
                val frequencyHz = effect.getCenterFreq(band.toShort()) / 1_000
                val levelMb = (EqualizerProfiles.gainAtFrequency(gains, frequencyHz) * 100)
                    .roundToInt()
                    .coerceIn(levels[0].toInt(), levels[1].toInt())
                effect.setBandLevel(band.toShort(), levelMb.toShort())
            }
            effect.enabled = enabled
            PlaybackEffectStatus.updateEqualizer(
                if (enabled) EqualizerPlaybackStatus.Applied else EqualizerPlaybackStatus.Disabled,
            )
        } catch (error: Exception) {
            runCatching { audioEqualizer?.release() }
            audioEqualizer = null
            unavailableEqualizerSessionId = sessionId
            PlaybackEffectStatus.updateEqualizer(EqualizerPlaybackStatus.Unavailable)
            Log.w(TAG, "Android equalizer is unavailable for the active playback session", error)
        }
    }

    private fun onTrackTransition(item: MediaItem, transition: TrackTransition, index: Int) {
        val id = item.mediaId.toLongOrNull() ?: return
        val current = player ?: return
        val extras = item.mediaMetadata.extras
        record(
            feedback.onTrackChanged(
                songId = id,
                durationMs = current.duration.takeIf { it > 0L } ?: 0L,
                positionMs = current.currentPosition,
                transition = transition,
                index = index,
                selectionSource = extras?.getString(MEDIA_EXTRA_SELECTION_SOURCE),
                recommendationId = extras?.getString(MEDIA_EXTRA_RECOMMENDATION_ID),
                nowElapsedMs = SystemClock.elapsedRealtime(),
                isPlaying = current.isPlaying,
                timestampMs = System.currentTimeMillis(),
            ),
        )
        previousSongId = id
        previousItemIndex = index
        if (UHQPlaybackPreference.isEnabled(this)) updateUHQ(item)
        else disableUHQ(item)
    }

    private fun updateUHQ(item: MediaItem, force: Boolean = false) {
        val songId = item.mediaId
        if (uhqSongId != songId) {
            uhqJob?.cancel()
            uhqWorkName?.let { WorkManager.getInstance(this).cancelUniqueWork(it) }
            uhqWorkName = null
            uhqSongId = songId
        } else if (!force && uhqJob?.isActive == true) {
            return
        }
        val songIdLong = songId.toLongOrNull()
        if (item.mediaMetadata.extras?.containsKey(MEDIA_EXTRA_UHQ_SOURCE_URI) == true) {
            PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Enhanced)
            return
        }
        val source = item.localConfiguration?.uri ?: run {
            PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Unsupported)
            return
        }
        PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Preparing)
        uhqJob?.cancel()
        uhqJob = eventScope.launch {
            try {
                val key = UHQAacCache.key(this@PlaybackService, source) ?: run {
                    PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Unsupported)
                    return@launch
                }
                if (!UHQPlaybackPreference.isEnabled(this@PlaybackService)) return@launch
                val destination = UHQAacCache.file(this@PlaybackService, key)
                if (destination.isFile && destination.length() > 44L) {
                    destination.setLastModified(System.currentTimeMillis())
                    withContext(Dispatchers.Main.immediate) {
                        val current = player?.currentMediaItem
                        if (UHQPlaybackPreference.isEnabled(this@PlaybackService) && current?.mediaId == songId) {
                            routeToEnhanced(current, source, destination)
                            PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Enhanced)
                        }
                    }
                    return@launch
                }

                val workManager = WorkManager.getInstance(this@PlaybackService)
                val workName = UHQAacCache.workName(key)
                uhqWorkName = workName
                val request = OneTimeWorkRequestBuilder<UHQEnhancementWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(UHQEnhancementWorker.INPUT_URI, source.toString())
                            .putString(UHQEnhancementWorker.INPUT_KEY, key)
                            .build(),
                    )
                    .build()
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request).result.get()
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val infos = workManager.getWorkInfosForUniqueWork(workName).get()
                    val info = infos.firstOrNull { it.id == request.id }
                        ?: infos.lastOrNull { !it.state.isFinished }
                        ?: infos.lastOrNull()
                    val progress = info?.progress
                    val percent = progress?.getInt(UHQEnhancementWorker.KEY_PROGRESS, -1) ?: -1
                    if (percent in 0..99) {
                        PlaybackEffectStatus.updateUHQ(
                            songIdLong,
                            UhqPlaybackStatus.Preparing,
                            progressPercent = percent,
                            progressStage = progress?.getString(UHQEnhancementWorker.KEY_STAGE),
                        )
                    }
                    if (info?.state?.isFinished == true) {
                        if (info.state == WorkInfo.State.SUCCEEDED && destination.isFile) {
                            withContext(Dispatchers.Main.immediate) {
                                val current = player?.currentMediaItem
                                if (UHQPlaybackPreference.isEnabled(this@PlaybackService) && current?.mediaId == songId) {
                                    routeToEnhanced(current, source, destination)
                                    PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Enhanced)
                                }
                            }
                        } else {
                            PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Failed)
                        }
                        return@launch
                    }
                    delay(750L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val currentTrackMatches = player?.currentMediaItem?.mediaId == songId
                if (currentTrackMatches && UHQPlaybackPreference.isEnabled(this@PlaybackService)) {
                    PlaybackEffectStatus.updateUHQ(songIdLong, UhqPlaybackStatus.Failed)
                }
                Log.w(TAG, "UHQ enhancement could not be completed", error)
            }
        }
    }

    private fun disableUHQ(item: MediaItem) {
        PlaybackEffectStatus.updateUHQ(item.mediaId.toLongOrNull(), UhqPlaybackStatus.Disabled)
        if (uhqSongId != item.mediaId) uhqSongId = item.mediaId
        uhqJob?.cancel()
        uhqWorkName?.let { WorkManager.getInstance(this).cancelUniqueWork(it) }
        uhqWorkName = null
        val source = item.mediaMetadata.extras?.getString(MEDIA_EXTRA_UHQ_SOURCE_URI)?.let(Uri::parse) ?: return
        routeToOriginal(item, source)
    }

    private fun routeToEnhanced(item: MediaItem, source: Uri, enhanced: java.io.File) {
        if (item.localConfiguration?.uri == Uri.fromFile(enhanced)) return
        val extras = item.mediaMetadata.extras?.let(::Bundle) ?: Bundle()
        extras.putString(MEDIA_EXTRA_UHQ_SOURCE_URI, source.toString())
        extras.putBoolean(MEDIA_EXTRA_UHQ_INTERNAL_SWITCH, true)
        replaceCurrentMediaItem(item, Uri.fromFile(enhanced), extras)
    }

    private fun routeToOriginal(item: MediaItem, source: Uri) {
        val extras = item.mediaMetadata.extras?.let(::Bundle) ?: Bundle()
        extras.remove(MEDIA_EXTRA_UHQ_SOURCE_URI)
        extras.putBoolean(MEDIA_EXTRA_UHQ_INTERNAL_SWITCH, true)
        replaceCurrentMediaItem(item, source, extras)
    }

    private fun replaceCurrentMediaItem(item: MediaItem, uri: Uri, extras: Bundle) {
        val current = player ?: return
        val index = current.currentMediaItemIndex
        if (index == C.INDEX_UNSET || current.currentMediaItem?.mediaId != item.mediaId) return
        val position = current.currentPosition
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        current.replaceMediaItem(index, item.buildUpon().setUri(uri).setMediaMetadata(metadata).build())
        current.seekTo(index, position)
    }

    private fun record(events: List<LocalListeningEvent>) {
        if (events.isEmpty()) return
        eventScope.launch {
            events.forEach { event ->
                try {
                    libraryStore.recordObservedEvent(event)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "Couldn't persist local playback feedback", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "WavvPlayback"
        const val FEEDBACK_SAMPLE_INTERVAL_MS = 1_000L
        const val EQUALIZER_UPDATE_DELAY_MS = 40L
    }
}
