package com.wavv.app

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingBackdropRenderTest {
    @Test
    fun visibleMiniPlayerRendersOutsideItsBackdropCaptureLayer() {
        val song = Song(
            id = 1L,
            title = "Render test",
            artist = "Wavv",
            album = "Render test",
            durationMs = 180_000L,
            uri = "content://com.wavv.test/audio/1",
            albumArtUri = null,
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    WavvContent(
                        state = WavvContentState(
                            libraryState = LibraryUiState.Ready(songs = listOf(song)),
                            playbackState = PlaybackState(song = song, durationMs = song.durationMs, queue = listOf(song)),
                            source = LibrarySource.Folder("content://com.wavv.test/tree/music"),
                        ),
                        actions = WavvContentActions(
                            onSearch = {},
                            onChooseAllDevice = {},
                            onChooseFiles = {},
                            onChooseFolder = {},
                            onAddMusicFolder = {},
                            onRemoveMusicFolder = {},
                            onRetry = {},
                            onChooseSource = {},
                            onPlay = { _, _ -> },
                            onPlayWithRecommendations = { _, _, _, _, _ -> },
                            onTogglePlayback = {},
                            onNext = {},
                            onPrevious = {},
                            onSeek = {},
                            onToggleFavorite = {},
                            onSetFavorites = { _, _ -> },
                            onUnavailable = {},
                            onRepeatMode = {},
                            onQueueMode = {},
                            onPlayNext = {},
                            onAddToQueue = {},
                            onRemoveQueueItem = {},
                            onMoveQueueItem = { _, _ -> },
                            onCreatePlaylist = { _, _ -> },
                            onRenamePlaylist = { _, _ -> },
                            onDeletePlaylist = {},
                            onAddSongToPlaylist = { _, _ -> },
                            onRemoveSongFromPlaylist = { _, _ -> },
                            onClearListeningHistory = {},
                        ),
                    )
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
