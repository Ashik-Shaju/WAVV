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
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.roundToLong
import kotlin.math.roundToInt

private val WAVV_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()

object PlaybackPlayerFactory {
    @OptIn(markerClass = [UnstableApi::class])
    fun create(context: Context, handlesAudioFocus: Boolean = true): ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext)
            .setEnableAudioFloatOutput(true)
            .setEnableDecoderFallback(true),
    )
        .setAudioAttributes(WAVV_AUDIO_ATTRIBUTES, handlesAudioFocus)
        .setHandleAudioBecomingNoisy(handlesAudioFocus)
        .build()
}

private const val MEDIA_EXTRA_UHQ_INTERNAL_SWITCH = "com.wavv.app.UHQ_INTERNAL_SWITCH"

class PlaybackService : MediaSessionService() {
    private data class CrossfadeQueueKey(
        val currentIndex: Int,
        val nextIndex: Int,
        val items: List<String>,
        val repeatMode: Int,
        val shuffleEnabled: Boolean,
    )

    private data class ActiveCrossfade(
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val durationMs: Long,
        var handedOff: Boolean = false,
    )

    private var player: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null
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
    private var crossfadePreferences: SharedPreferences? = null
    private var preparedCrossfadeKey: CrossfadeQueueKey? = null
    private var activeCrossfade: ActiveCrossfade? = null
    private val crossfadeStart = Runnable(::beginCrossfade)
    private val crossfadeFrame = Runnable(::updateCrossfade)
    private val crossfadePreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CrossfadeSettings.preferenceKey()) {
            feedbackHandler.post {
                if (CrossfadeSettings.durationSeconds(this) == 0) {
                    cancelCrossfade()
                    releaseStandbyPlayer()
                } else {
                    prepareNextCrossfadePlayer()
                }
            }
        }
    }
    private val uhqPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == UHQPlaybackPreference.preferenceKey() || key == UHQPlaybackPreference.trackOverrideKey()) {
            feedbackHandler.post {
                cancelCrossfade()
                player?.currentMediaItem?.let { item ->
                    if (shouldEnhanceTrack(item.mediaId)) updateUHQ(item, force = true)
                    else disableUHQ(item)
                }
                prepareNextCrossfadePlayer()
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
            activeCrossfade?.let { fade ->
                val peer = if (current === fade.outgoing) fade.incoming else fade.outgoing
                peer.playWhenReady = current.playWhenReady
                if (current.playWhenReady) feedbackHandler.post(crossfadeFrame)
                else feedbackHandler.removeCallbacks(crossfadeFrame)
            } ?: if (isPlaying) {
                scheduleCrossfadeStart()
            } else {
                feedbackHandler.removeCallbacks(crossfadeStart)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val current = player ?: return
            val item = mediaItem ?: return
            if (item.mediaMetadata.extras?.getBoolean(MEDIA_EXTRA_UHQ_INTERNAL_SWITCH) == true) return
            if (activeCrossfade != null) cancelCrossfade()
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

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (activeCrossfade != null) cancelCrossfade()
            preparedCrossfadeKey = null
            prepareNextCrossfadePlayer()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && activeCrossfade != null) cancelCrossfade()
            if (activeCrossfade == null) scheduleCrossfadeStart()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            preparedCrossfadeKey = null
            prepareNextCrossfadePlayer()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            preparedCrossfadeKey = null
            prepareNextCrossfadePlayer()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            standbyPlayer?.playbackParameters = playbackParameters
            if (activeCrossfade == null) scheduleCrossfadeStart()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                record(feedback.onEnded(SystemClock.elapsedRealtime(), System.currentTimeMillis()))
            } else if (playbackState == Player.STATE_READY) {
                if (activeCrossfade != null) feedbackHandler.post(crossfadeFrame)
                else scheduleCrossfadeStart()
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

    private val crossfadePlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && activeCrossfade == null) scheduleCrossfadeStart()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val failedPlayer = standbyPlayer ?: return
            if (activeCrossfade?.incoming === failedPlayer) cancelCrossfade()
            if (standbyPlayer === failedPlayer) releaseStandbyPlayer()
            Log.w(TAG, "Standby player failed; using the normal track transition", error)
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
        crossfadePreferences = CrossfadeSettings.preferences(this).also {
            it.registerOnSharedPreferenceChangeListener(crossfadePreferenceListener)
        }
        player = PlaybackPlayerFactory.create(this)
        player?.addListener(feedbackListener)
        AudioOutputRouting.attach(this)
        mediaSession = MediaSession.Builder(this, player!!)
            .build()
        feedbackHandler.postDelayed(feedbackSampler, FEEDBACK_SAMPLE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @OptIn(markerClass = [UnstableApi::class])
    override fun onTaskRemoved(rootIntent: Intent?) {
        standbyPlayer?.pause()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        uhqPreferences?.unregisterOnSharedPreferenceChangeListener(uhqPreferenceListener)
        uhqPreferences = null
        crossfadePreferences?.unregisterOnSharedPreferenceChangeListener(crossfadePreferenceListener)
        crossfadePreferences = null
        AudioOutputRouting.detach()
        uhqJob?.cancel()
        feedbackHandler.removeCallbacks(feedbackSampler)
        feedbackHandler.removeCallbacks(applyEqualizer)
        feedbackHandler.removeCallbacks(crossfadeStart)
        feedbackHandler.removeCallbacks(crossfadeFrame)
        runCatching { audioEqualizer?.release() }
        audioEqualizer = null
        PlaybackEffectStatus.reset()
        player?.removeListener(feedbackListener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        standbyPlayer?.removeListener(crossfadePlayerListener)
        standbyPlayer?.release()
        standbyPlayer = null
        activeCrossfade = null
        eventScope.cancel()
        super.onDestroy()
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun prepareNextCrossfadePlayer() {
        feedbackHandler.removeCallbacks(crossfadeStart)
        val active = player ?: return
        if (activeCrossfade != null) return
        if (CrossfadeSettings.durationSeconds(this) == 0) {
            releaseStandbyPlayer()
            return
        }
        val currentItem = active.currentMediaItem ?: run {
            releaseStandbyPlayer()
            return
        }
        val nextIndex = active.nextMediaItemIndex
        if (nextIndex !in 0 until active.mediaItemCount) {
            releaseStandbyPlayer()
            return
        }
        val nextItem = active.getMediaItemAt(nextIndex)
        if (!isCrossfadeEligible(
                currentItem.mediaId,
                nextItem.mediaId,
                repeatCurrentTrack = active.repeatMode == Player.REPEAT_MODE_ONE,
                currentNeedsUHQ = requiresUncrossfadedUHQ(currentItem),
                nextNeedsUHQ = requiresUncrossfadedUHQ(nextItem),
            )
        ) {
            releaseStandbyPlayer()
            return
        }

        val key = CrossfadeQueueKey(
            currentIndex = active.currentMediaItemIndex,
            nextIndex = nextIndex,
            items = (0 until active.mediaItemCount).map { index ->
                active.getMediaItemAt(index).let { "${it.mediaId}|${it.localConfiguration?.uri}" }
            },
            repeatMode = active.repeatMode,
            shuffleEnabled = active.shuffleModeEnabled,
        )
        val standby = ensureStandbyPlayer(active)
        if (key == preparedCrossfadeKey && standby.playbackState != Player.STATE_IDLE &&
            standby.currentMediaItemIndex == nextIndex && standby.currentMediaItem?.mediaId == nextItem.mediaId
        ) {
            scheduleCrossfadeStart()
            return
        }

        preparedCrossfadeKey = null
        standby.pause()
        standby.volume = 0f
        standby.pauseAtEndOfMediaItems = false
        standby.clearMediaItems()
        standby.setMediaItems((0 until active.mediaItemCount).map(active::getMediaItemAt), nextIndex, 0L)
        standby.setShuffleOrder(active.shuffleOrder)
        standby.shuffleModeEnabled = active.shuffleModeEnabled
        standby.repeatMode = active.repeatMode
        standby.playbackParameters = active.playbackParameters
        standby.setSkipSilenceEnabled(active.skipSilenceEnabled)
        if (active.audioSessionId > 0) standby.setAudioSessionId(active.audioSessionId)
        preparedCrossfadeKey = key
        standby.prepare()
        scheduleCrossfadeStart()
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun ensureStandbyPlayer(active: ExoPlayer): ExoPlayer {
        val standby = standbyPlayer ?: PlaybackPlayerFactory.create(this, handlesAudioFocus = false).also {
            standbyPlayer = it
            it.addListener(crossfadePlayerListener)
        }
        if (active.audioSessionId > 0 && standby.audioSessionId != active.audioSessionId) {
            standby.setAudioSessionId(active.audioSessionId)
        }
        return standby
    }

    private fun requiresUncrossfadedUHQ(item: MediaItem): Boolean =
        item.mediaMetadata.extras?.containsKey(MEDIA_EXTRA_UHQ_SOURCE_URI) == true || shouldEnhanceTrack(item.mediaId)

    private fun releaseStandbyPlayer() {
        feedbackHandler.removeCallbacks(crossfadeStart)
        preparedCrossfadeKey = null
        standbyPlayer?.takeUnless { it === player }?.let { standby ->
            standby.removeListener(crossfadePlayerListener)
            standby.pause()
            standby.release()
            standbyPlayer = null
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun scheduleCrossfadeStart() {
        feedbackHandler.removeCallbacks(crossfadeStart)
        val active = player ?: return
        val standby = standbyPlayer ?: return
        if (activeCrossfade != null || !active.isPlaying || CrossfadeSettings.durationSeconds(this) == 0) return
        val nextIndex = active.nextMediaItemIndex
        if (nextIndex !in 0 until active.mediaItemCount) return
        val expectedKey = CrossfadeQueueKey(
            currentIndex = active.currentMediaItemIndex,
            nextIndex = nextIndex,
            items = (0 until active.mediaItemCount).map { index ->
                active.getMediaItemAt(index).let { "${it.mediaId}|${it.localConfiguration?.uri}" }
            },
            repeatMode = active.repeatMode,
            shuffleEnabled = active.shuffleModeEnabled,
        )
        if (expectedKey != preparedCrossfadeKey) {
            prepareNextCrossfadePlayer()
            return
        }
        if (standby.playbackState != Player.STATE_READY || standby.currentMediaItemIndex != nextIndex) return
        val duration = effectiveCrossfadeDurationMs(
            CrossfadeSettings.durationSeconds(this) * 1_000L,
            active.duration,
            standby.duration,
        )
        if (duration <= 0L) return
        val remaining = active.duration - active.currentPosition
        if (remaining <= 0L) return
        val speed = active.playbackParameters.speed.toDouble().takeIf { it > 0.0 } ?: 1.0
        val delayMs = (((remaining - duration).coerceAtLeast(0L) / speed).roundToLong() - CROSSFADE_START_EARLY_MS)
            .coerceAtLeast(0L)
        feedbackHandler.postDelayed(crossfadeStart, delayMs)
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun beginCrossfade() {
        val active = player ?: return
        val incoming = standbyPlayer ?: return
        val currentItem = active.currentMediaItem ?: return
        val nextIndex = active.nextMediaItemIndex
        if (!active.isPlaying || nextIndex !in 0 until active.mediaItemCount ||
            incoming.playbackState != Player.STATE_READY || incoming.currentMediaItemIndex != nextIndex
        ) return
        val nextItem = active.getMediaItemAt(nextIndex)
        if (!isCrossfadeEligible(
                currentItem.mediaId,
                nextItem.mediaId,
                repeatCurrentTrack = active.repeatMode == Player.REPEAT_MODE_ONE,
                currentNeedsUHQ = requiresUncrossfadedUHQ(currentItem),
                nextNeedsUHQ = requiresUncrossfadedUHQ(nextItem),
            )
        ) {
            prepareNextCrossfadePlayer()
            return
        }
        val requestedDuration = effectiveCrossfadeDurationMs(
            CrossfadeSettings.durationSeconds(this) * 1_000L,
            active.duration,
            incoming.duration,
        )
        val remaining = active.duration - active.currentPosition
        if (requestedDuration <= 0L || remaining <= 0L) return
        if (remaining > requestedDuration + CROSSFADE_START_EARLY_MS) {
            scheduleCrossfadeStart()
            return
        }
        if (remaining < requestedDuration * 3L / 4L) return
        val duration = minOf(requestedDuration, remaining)

        active.pauseAtEndOfMediaItems = true
        incoming.seekTo(nextIndex, 0L)
        incoming.volume = 0f
        activeCrossfade = ActiveCrossfade(active, incoming, duration)
        incoming.play()
        feedbackHandler.post(crossfadeFrame)
    }

    private fun updateCrossfade() {
        val fade = activeCrossfade ?: return
        val progress = (fade.incoming.currentPosition.toFloat() / fade.durationMs).coerceIn(0f, 1f)
        val gains = equalPowerCrossfadeGains(progress)
        fade.outgoing.volume = gains.outgoing
        fade.incoming.volume = gains.incoming
        if (!fade.handedOff && progress >= 0.5f && !handoffCrossfade(fade)) {
            cancelCrossfade()
            return
        }
        if (progress >= 1f) {
            finishCrossfade(fade)
        } else if (fade.incoming.playWhenReady) {
            feedbackHandler.postDelayed(crossfadeFrame, CROSSFADE_FRAME_INTERVAL_MS)
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun handoffCrossfade(fade: ActiveCrossfade): Boolean {
        val outgoing = fade.outgoing
        val incoming = fade.incoming
        val session = mediaSession ?: return false
        if (player !== outgoing) return false
        val sharedSessionId = outgoing.audioSessionId
        outgoing.removeListener(feedbackListener)
        incoming.removeListener(crossfadePlayerListener)
        outgoing.addListener(crossfadePlayerListener)
        try {
            setSessionOwner(outgoing, owns = false)
            setSessionOwner(incoming, owns = true)
            if (sharedSessionId > 0) {
                outgoing.setAudioSessionId(sharedSessionId)
                incoming.setAudioSessionId(sharedSessionId)
            }
            player = incoming
            standbyPlayer = outgoing
            preparedCrossfadeKey = null
            session.setPlayer(incoming)
            incoming.addListener(feedbackListener)
        } catch (error: Exception) {
            incoming.removeListener(feedbackListener)
            outgoing.removeListener(crossfadePlayerListener)
            incoming.addListener(crossfadePlayerListener)
            outgoing.addListener(feedbackListener)
            player = outgoing
            standbyPlayer = incoming
            runCatching { setSessionOwner(outgoing, owns = true) }
            runCatching { setSessionOwner(incoming, owns = false) }
            if (sharedSessionId > 0) {
                runCatching { outgoing.setAudioSessionId(sharedSessionId) }
                runCatching { incoming.setAudioSessionId(sharedSessionId) }
            }
            runCatching { session.setPlayer(outgoing) }
            Log.w(TAG, "Couldn't hand playback controls to the incoming track", error)
            return false
        }
        fade.handedOff = true
        if (sharedSessionId > 0) equalizerSessionId = sharedSessionId
        incoming.currentMediaItem?.let {
            onTrackTransition(it, TrackTransition.AUTOMATIC, incoming.currentMediaItemIndex)
        }
        return true
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun finishCrossfade(fade: ActiveCrossfade) {
        if (activeCrossfade !== fade || !fade.handedOff) return
        feedbackHandler.removeCallbacks(crossfadeFrame)
        activeCrossfade = null
        player?.volume = 1f
        fade.outgoing.pauseAtEndOfMediaItems = false
        fade.outgoing.pause()
        fade.outgoing.volume = 0f
        fade.outgoing.clearMediaItems()
        preparedCrossfadeKey = null
        prepareNextCrossfadePlayer()
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun cancelCrossfade() {
        feedbackHandler.removeCallbacks(crossfadeStart)
        feedbackHandler.removeCallbacks(crossfadeFrame)
        val fade = activeCrossfade
        activeCrossfade = null
        if (fade != null) {
            val active = player
            if (active === fade.outgoing) fade.outgoing.volume = 1f
            else active?.volume = 1f
            val discarded = if (active === fade.outgoing) fade.incoming else fade.outgoing
            if (discarded !== active) {
                discarded.pauseAtEndOfMediaItems = false
                discarded.pause()
                discarded.volume = 0f
                discarded.clearMediaItems()
            }
        }
        player?.pauseAtEndOfMediaItems = false
        preparedCrossfadeKey = null
    }

    private fun setSessionOwner(target: ExoPlayer, owns: Boolean) {
        target.setAudioAttributes(WAVV_AUDIO_ATTRIBUTES, owns)
        target.setHandleAudioBecomingNoisy(owns)
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
        val override = UHQPlaybackPreference.trackOverride(this)
        if (override != null && override.songId != id) UHQPlaybackPreference.clearTrackOverride(this)
        if (shouldEnhanceTrack(item.mediaId)) updateUHQ(item)
        else disableUHQ(item)
        prepareNextCrossfadePlayer()
    }

    private fun shouldEnhanceTrack(songId: String): Boolean {
        val id = songId.toLongOrNull()
        val override = UHQPlaybackPreference.trackOverride(this)
        return if (id != null && override?.songId == id) override.enabled
        else UHQPlaybackPreference.isEnabled(this)
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
                if (!shouldEnhanceTrack(songId)) return@launch
                val destination = UHQAacCache.file(this@PlaybackService, key)
                if (destination.isFile && destination.length() > 44L) {
                    destination.setLastModified(System.currentTimeMillis())
                    withContext(Dispatchers.Main.immediate) {
                        val current = player?.currentMediaItem
                        if (shouldEnhanceTrack(songId) && current?.mediaId == songId) {
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
                                if (shouldEnhanceTrack(songId) && current?.mediaId == songId) {
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
                if (currentTrackMatches && shouldEnhanceTrack(songId)) {
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
        const val CROSSFADE_START_EARLY_MS = 50L
        const val CROSSFADE_FRAME_INTERVAL_MS = 20L
    }
}
