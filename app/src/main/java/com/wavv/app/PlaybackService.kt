package com.wavv.app

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var libraryStore: LibraryStore
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feedback = PlaybackFeedbackReducer(WavvSession.id)
    private val feedbackHandler = Handler(Looper.getMainLooper())
    private var previousSongId: Long? = null
    private var previousItemIndex: Int = C.INDEX_UNSET
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
    }

    override fun onCreate() {
        super.onCreate()
        libraryStore = LibraryStore(WavvDatabase.get(this))
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
        feedbackHandler.removeCallbacks(feedbackSampler)
        player?.removeListener(feedbackListener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        eventScope.cancel()
        super.onDestroy()
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
    }
}
