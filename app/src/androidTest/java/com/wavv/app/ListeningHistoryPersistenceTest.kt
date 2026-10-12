package com.wavv.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningHistoryPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pruningOldEventsAlsoExpiresRecentPlaybackWithoutRemovingFavorites() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val song = song("content://wavv/history")
            store.replaceSongs(listOf(song))
            store.toggleFavorite(song.id, "history-test")
            val now = System.currentTimeMillis()
            val stalePlayedAt = now - LISTENING_HISTORY_RETENTION_MS - 1L
            database.songDao().markPlayed(song.id, stalePlayedAt)
            database.songUserStateDao().upsert(
                SongUserStateEntity(song.uri, isFavorite = true, lastPlayedAt = stalePlayedAt),
            )

            store.recordObservedEvent(
                LocalListeningEvent(
                    songId = song.id,
                    timestampMs = stalePlayedAt,
                    eventType = "play",
                    sessionId = "old-session",
                ),
            )
            store.recordObservedEvent(
                LocalListeningEvent(
                    songId = song.id,
                    timestampMs = now,
                    eventType = "completion",
                    sessionId = "current-session",
                ),
            )

            val events = store.observeDiscoveryEvents(0L).first()
            assertFalse(events.any { it.eventType == "play" })
            assertTrue(events.any { it.eventType == "completion" })
            assertTrue(store.getFavoriteIds().contains(song.id))
            assertTrue(store.getRecentlyPlayed().isEmpty())
            assertTrue(database.songUserStateDao().getForUris(listOf(song.uri)).single().lastPlayedAt == null)
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingHistoryPreservesFavoritesAndUserPlaylists() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val favorite = song("content://wavv/favorite")
            val playlistSong = song("content://wavv/playlist", id = 2L)
            store.replaceSongs(listOf(favorite, playlistSong))
            store.toggleFavorite(favorite.id, "history-test")
            val playlistId = store.createPlaylist("Keep this playlist")
            store.addToPlaylist(playlistId, playlistSong.id)
            store.recordObservedEvent(
                LocalListeningEvent(
                    songId = favorite.id,
                    timestampMs = System.currentTimeMillis(),
                    eventType = "play",
                    sessionId = "current-session",
                ),
            )

            store.clearListeningHistory()

            assertTrue(store.observeDiscoveryEvents(0L).first().isEmpty())
            assertTrue(store.getRecentlyPlayed().isEmpty())
            assertEquals(setOf(favorite.id), store.getFavoriteIds())
            assertEquals(listOf(playlistSong.id), store.getPlaylists().single().songIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun addingTracksToPlaylistIsDeduplicatedAndRecordedAsOneBatch() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val songs = (1L..3L).map { song("content://wavv/batch-$it", it) }
            store.replaceSongs(songs)
            val playlistId = store.createPlaylist("Batch")
            store.addToPlaylist(playlistId, songs.first().id)

            store.addSongsToPlaylist(playlistId, listOf(songs[1].id, songs[1].id, songs[2].id))

            assertEquals(songs.map(Song::id), store.getPlaylists().single().songIds)
            val addEvents = store.observeDiscoveryEvents(0L).first().filter { it.eventType == "playlist_add" }
            assertEquals(setOf(songs[0].id, songs[1].id, songs[2].id), addEvents.map(LocalListeningEvent::songId).toSet())
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase() = Room.inMemoryDatabaseBuilder(context, WavvDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun song(uri: String, id: Long = 1L) = Song(
        id = id,
        title = "Local track $id",
        artist = "Local artist",
        album = "Local album",
        durationMs = 10_000L,
        uri = uri,
        albumArtUri = null,
        fileSizeBytes = 123L,
        modifiedAt = 456L,
        metadataSource = "local",
    )
}
