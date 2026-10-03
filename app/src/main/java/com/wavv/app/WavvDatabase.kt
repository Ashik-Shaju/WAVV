package com.wavv.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.PrimaryKey
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String?,
    val fileSizeBytes: Long,
    val modifiedAt: Long,
    val codec: String?,
    val container: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val metadataSource: String,
    val genre: String?,
    @ColumnInfo(defaultValue = "'[]'") val genresJson: String = "[]",
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    @ColumnInfo(defaultValue = "'[]'") val languageTagsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val languageMetadataChecked: Boolean = false,
)

@Entity(tableName = "song_user_state")
data class SongUserStateEntity(
    @PrimaryKey val uri: String,
    val isFavorite: Boolean,
    val lastPlayedAt: Long?,
)

@Entity(tableName = "listening_events", indices = [Index(value = ["timestamp"])])
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val songId: Long,
    val timestamp: Long,
    val sessionId: String,
    val eventType: String,
    val listenSeconds: Long = 0L,
    val completionRatio: Float? = null,
    val skipPositionSeconds: Long? = null,
    val selectionSource: String? = null,
    val playlistId: Long? = null,
    val recommendationId: String? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "songId"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "analysis_jobs", primaryKeys = ["songId", "jobType"])
data class AnalysisJobEntity(
    val songId: Long,
    val jobType: String,
    val status: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long,
)

@Entity(tableName = "song_embeddings")
data class SongEmbeddingEntity(
    @PrimaryKey val songId: Long,
    val modelId: String,
    val sourceSizeBytes: Long,
    val sourceModifiedAt: Long,
    val dimension: Int,
    val dtype: String,
    val normalized: Boolean,
    val vector: ByteArray,
    val updatedAt: Long,
)

@Entity(tableName = "music_understanding_results")
data class MusicUnderstandingResultEntity(
    @PrimaryKey val songId: Long,
    val modelId: String,
    val sourceSizeBytes: Long,
    val sourceModifiedAt: Long,
    val tagsJson: String,
    val updatedAt: Long,
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun get(songId: Long): SongEntity?

    @Query("SELECT id FROM songs WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<Long>

    @Query("SELECT isFavorite FROM songs WHERE id = :songId")
    suspend fun isFavorite(songId: Long): Boolean?

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 8")
    suspend fun getRecentlyPlayed(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    @Query("UPDATE songs SET lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun markPlayed(songId: Long, timestamp: Long)

    @Query("UPDATE songs SET lastPlayedAt = NULL WHERE lastPlayedAt < :cutoffMs")
    suspend fun clearLastPlayedBefore(cutoffMs: Long)

    @Query("UPDATE songs SET lastPlayedAt = NULL WHERE lastPlayedAt IS NOT NULL")
    suspend fun clearLastPlayed()

    @Query("UPDATE songs SET languageTagsJson = :languageTagsJson, languageMetadataChecked = 1 WHERE id = :songId")
    suspend fun setLanguageTags(songId: Long, languageTagsJson: String)
}

@Dao
interface SongUserStateDao {
    @Query("SELECT * FROM song_user_state WHERE uri IN (:uris)")
    suspend fun getForUris(uris: List<String>): List<SongUserStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SongUserStateEntity)

    @Query("DELETE FROM song_user_state WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("UPDATE song_user_state SET lastPlayedAt = NULL WHERE lastPlayedAt < :cutoffMs")
    suspend fun clearLastPlayedBefore(cutoffMs: Long)

    @Query("DELETE FROM song_user_state WHERE isFavorite = 0 AND lastPlayedAt IS NULL")
    suspend fun deleteEmpty()

    @Query("UPDATE song_user_state SET lastPlayedAt = NULL")
    suspend fun clearLastPlayed()
}

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity)

    @Query("SELECT * FROM listening_events WHERE timestamp >= :sinceMs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(sinceMs: Long, limit: Int): Flow<List<ListeningEventEntity>>

    @Query("DELETE FROM listening_events WHERE timestamp < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long)

    @Query("DELETE FROM listening_events")
    suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<PlaylistEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("SELECT songId FROM playlist_items WHERE playlistId = :playlistId ORDER BY position, addedAt")
    suspend fun getSongIds(playlistId: Long): List<Long>

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItems(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)
}

@Dao
interface AnalysisJobDao {
    @Query("SELECT * FROM analysis_jobs WHERE songId = :songId AND jobType = :jobType")
    suspend fun get(songId: Long, jobType: String): AnalysisJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<AnalysisJobEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: AnalysisJobEntity)
}

@Dao
interface SongEmbeddingDao {
    @Query("SELECT * FROM song_embeddings")
    suspend fun getAll(): List<SongEmbeddingEntity>

    @Query("SELECT * FROM song_embeddings WHERE songId = :songId")
    suspend fun get(songId: Long): SongEmbeddingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: SongEmbeddingEntity)

    @Query("DELETE FROM song_embeddings")
    suspend fun deleteAll()

    @Query("DELETE FROM song_embeddings WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteMissingSongs()
}

@Dao
interface MusicUnderstandingDao {
    @Query("SELECT * FROM music_understanding_results")
    suspend fun getAll(): List<MusicUnderstandingResultEntity>

    @Query("SELECT * FROM music_understanding_results WHERE songId = :songId")
    suspend fun get(songId: Long): MusicUnderstandingResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: MusicUnderstandingResultEntity)

    @Query("DELETE FROM music_understanding_results WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteMissingSongs()
}

internal data class CatalogUserState(val isFavorite: Boolean, val lastPlayedAt: Long?)

internal fun resolveCatalogUserState(
    previous: CatalogUserState?,
    persisted: CatalogUserState?,
): CatalogUserState = persisted ?: previous ?: CatalogUserState(isFavorite = false, lastPlayedAt = null)

@Database(
    entities = [SongEntity::class, SongUserStateEntity::class, ListeningEventEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, AnalysisJobEntity::class, SongEmbeddingEntity::class, MusicUnderstandingResultEntity::class],
    version = 8,
    exportSchema = true,
)
abstract class WavvDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songUserStateDao(): SongUserStateDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun analysisJobDao(): AnalysisJobDao
    abstract fun songEmbeddingDao(): SongEmbeddingDao
    abstract fun musicUnderstandingDao(): MusicUnderstandingDao
    companion object {
        @Volatile
        private var instance: WavvDatabase? = null

        fun get(context: Context): WavvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WavvDatabase::class.java,
                "wavv.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_embeddings (
                        songId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        sourceSizeBytes INTEGER NOT NULL,
                        sourceModifiedAt INTEGER NOT NULL,
                        dimension INTEGER NOT NULL,
                        dtype TEXT NOT NULL,
                        normalized INTEGER NOT NULL,
                        vector BLOB NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(songId)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS song_embeddings")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_embeddings (
                        songId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        sourceSizeBytes INTEGER NOT NULL,
                        sourceModifiedAt INTEGER NOT NULL,
                        dimension INTEGER NOT NULL,
                        dtype TEXT NOT NULL,
                        normalized INTEGER NOT NULL,
                        vector BLOB NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(songId)
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT")
            }
        }

        internal val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN genresJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_understanding_results (
                        songId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        sourceSizeBytes INTEGER NOT NULL,
                        sourceModifiedAt INTEGER NOT NULL,
                        tagsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(songId)
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_user_state (
                        uri TEXT NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        lastPlayedAt INTEGER,
                        PRIMARY KEY(uri)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO song_user_state (uri, isFavorite, lastPlayedAt)
                    SELECT uri, MAX(isFavorite), MAX(lastPlayedAt) FROM songs
                    WHERE isFavorite = 1 OR lastPlayedAt IS NOT NULL
                    GROUP BY uri
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN albumArtist TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN composer TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN year INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN languageTagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE songs ADD COLUMN languageMetadataChecked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_events_timestamp ON listening_events(timestamp)")
            }
        }
    }
}

class LibraryStore(private val database: WavvDatabase) {
    suspend fun pruneListeningHistory(nowMs: Long = System.currentTimeMillis()) {
        database.withTransaction { pruneListeningHistoryBefore(listeningHistoryCutoff(nowMs)) }
    }

    suspend fun clearListeningHistory() {
        database.withTransaction {
            database.listeningEventDao().deleteAll()
            database.songDao().clearLastPlayed()
            database.songUserStateDao().clearLastPlayed()
            database.songUserStateDao().deleteEmpty()
        }
    }

    suspend fun replaceSongs(songs: List<Song>) {
        database.withTransaction {
            val previousSongsByUri = database.songDao().getAll().associateBy(SongEntity::uri)
            val uris = songs.asSequence()
                .map(Song::uri)
                .distinct()
                .toList()
                .chunked(STATE_QUERY_BATCH_SIZE)
            val stateDao = database.songUserStateDao()
            val userStatesByUri = mutableMapOf<String, SongUserStateEntity>()
            for (uriBatch in uris) {
                stateDao.getForUris(uriBatch).forEach { userStatesByUri[it.uri] = it }
            }
            database.songDao().deleteAll()
            database.songDao().upsertAll(
                songs.map { song ->
                    val previous = previousSongsByUri[song.uri]
                    val userState = userStatesByUri[song.uri]
                    val restoredState = resolveCatalogUserState(
                        previous = previous?.let { CatalogUserState(it.isFavorite, it.lastPlayedAt) },
                        persisted = userState?.let { CatalogUserState(it.isFavorite, it.lastPlayedAt) },
                    )
                    song.copy(id = previous?.id ?: song.id)
                        .toEntity(restoredState.isFavorite)
                        .copy(
                            lastPlayedAt = restoredState.lastPlayedAt,
                            languageTagsJson = if (song.languageMetadataChecked || song.languageTags.isNotEmpty()) {
                                song.languageTags.toJsonArray()
                            } else previous?.languageTagsJson ?: "[]",
                            languageMetadataChecked = song.languageMetadataChecked || (previous?.languageMetadataChecked == true),
                        )
                },
            )
            database.songEmbeddingDao().deleteMissingSongs()
            database.musicUnderstandingDao().deleteMissingSongs()
            database.analysisJobDao().upsertAll(
                songs.map { song ->
                    AnalysisJobEntity(song.id, METADATA_JOB, COMPLETED, updatedAt = System.currentTimeMillis())
                },
            )
        }
    }

    suspend fun getSongs(): List<Song> = database.songDao().getAll().map(SongEntity::toSong)

    suspend fun getFavoriteIds(): Set<Long> = database.songDao().getFavoriteIds().toSet()

    suspend fun getRecentlyPlayed(): List<Song> = database.songDao().getRecentlyPlayed().map(SongEntity::toSong)

    suspend fun getPlaylists(): List<UserPlaylist> = database.playlistDao().getAll().map { playlist ->
        UserPlaylist(playlist.id, playlist.name, database.playlistDao().getSongIds(playlist.id), playlist.updatedAt)
    }

    suspend fun getEmbedding(songId: Long): SongEmbeddingEntity? = database.songEmbeddingDao().get(songId)

    suspend fun saveEmbedding(embedding: SongEmbeddingEntity) = database.songEmbeddingDao().upsert(embedding)

    suspend fun getMusicUnderstanding(songId: Long): MusicUnderstandingResultEntity? =
        database.musicUnderstandingDao().get(songId)

    suspend fun saveMusicUnderstanding(result: MusicUnderstandingResultEntity) =
        database.musicUnderstandingDao().upsert(result)

    suspend fun saveAnalysis(songId: Long, jobType: String, status: String, error: String? = null) {
        database.analysisJobDao().upsert(
            AnalysisJobEntity(songId, jobType, status, lastError = error, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun runAnalysisJob(songId: Long, jobType: String, analyze: suspend () -> Unit): Exception? {
        saveAnalysis(songId, jobType, "running")
        try {
            analyze()
            saveAnalysis(songId, jobType, "completed")
            return null
        } catch (error: CancellationException) {
            withContext(NonCancellable) { runCatching { saveAnalysis(songId, jobType, "cancelled") } }
            throw error
        } catch (error: Exception) {
            saveAnalysis(
                songId,
                jobType,
                "failed",
                (error.message ?: error::class.simpleName ?: "Analysis failed").take(512),
            )
            return error
        } catch (error: Error) {
            withContext(NonCancellable) { runCatching { saveAnalysis(songId, jobType, "failed", "Fatal analysis error") } }
            throw error
        }
    }

    internal suspend fun searchEmbeddings(query: FloatArray, songs: List<Song>, limit: Int): List<SemanticMatch> {
        val byId = songs.associateBy(Song::id)
        // ponytail: linear scan is enough for the current library; add ANN only after a measured large-library bottleneck.
        return database.songEmbeddingDao().getAll()
            .asSequence()
            .mapNotNull { embedding ->
                val song = byId[embedding.songId] ?: return@mapNotNull null
                val vector = embedding.vector.toFloatArray()
                if (
                    embedding.modelId != DCLAP_MODEL_ID ||
                    !sourceFingerprintMatches(
                        embedding.sourceSizeBytes,
                        embedding.sourceModifiedAt,
                        song.fileSizeBytes,
                        song.modifiedAt,
                    ) ||
                    vector.size != query.size ||
                    !embedding.normalized
                ) return@mapNotNull null
                song to query.indices.sumOf { index -> query[index].toDouble() * vector[index] }
            }
            .sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.id })
            .take(limit)
            .map { SemanticMatch(it.first.id, it.second.toFloat()) }
            .toList()
    }

    suspend fun audioVectors(songs: List<Song>): Map<Long, FloatArray> {
        val byId = songs.associateBy(Song::id)
        return database.songEmbeddingDao().getAll().mapNotNull { embedding ->
            val song = byId[embedding.songId] ?: return@mapNotNull null
            if (
                embedding.modelId != DCLAP_MODEL_ID || !embedding.normalized || embedding.dimension != 512 ||
                !sourceFingerprintMatches(embedding.sourceSizeBytes, embedding.sourceModifiedAt, song.fileSizeBytes, song.modifiedAt)
            ) return@mapNotNull null
            val vector = runCatching { embedding.vector.toFloatArray() }.getOrNull()
                ?.takeIf { it.size == embedding.dimension && it.all(Float::isFinite) }
                ?: return@mapNotNull null
            song.id to vector
        }.toMap()
    }

    internal suspend fun musicTags(songs: List<Song>): Map<Long, List<MusicTag>> {
        val byId = songs.associateBy(Song::id)
        return database.musicUnderstandingDao().getAll().mapNotNull { result ->
            val song = byId[result.songId] ?: return@mapNotNull null
            if (
                result.sourceSizeBytes != song.fileSizeBytes || result.sourceModifiedAt != song.modifiedAt
            ) return@mapNotNull null
            song.id to result.tagsJson.toMusicTags()
        }.toMap()
    }

    internal fun observeDiscoveryEvents(sinceMs: Long, limit: Int = MAX_DISCOVERY_EVENTS) =
        database.listeningEventDao().observeRecent(sinceMs, limit).map { rows -> rows.map(ListeningEventEntity::toLocalEvent) }

    suspend fun setLanguageTags(songId: Long, languageTags: List<String>) {
        database.songDao().setLanguageTags(songId, languageTags.toJsonArray())
    }

    internal suspend fun recordObservedEvent(event: LocalListeningEvent) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            database.listeningEventDao().insert(
                ListeningEventEntity(
                    userId = LOCAL_USER,
                    songId = event.songId,
                    timestamp = event.timestampMs,
                    sessionId = event.sessionId,
                    eventType = event.eventType,
                    listenSeconds = event.listenSeconds,
                    completionRatio = event.completionRatio,
                    skipPositionSeconds = event.skipPositionSeconds,
                    selectionSource = event.selectionSource,
                    playlistId = event.playlistId,
                    recommendationId = event.recommendationId,
                ),
            )
            if (event.eventType == "play") {
                database.songDao().get(event.songId)?.let { song ->
                    database.songDao().markPlayed(event.songId, event.timestampMs)
                    saveUserState(song, song.isFavorite, event.timestampMs)
                }
            }
            pruneListeningHistoryBefore(listeningHistoryCutoff(now))
        }
    }

    suspend fun toggleFavorite(songId: Long, sessionId: String): Boolean = database.withTransaction {
        val song = database.songDao().get(songId) ?: return@withTransaction false
        val now = System.currentTimeMillis()
        val next = !song.isFavorite
        database.songDao().setFavorite(songId, next)
        saveUserState(song, next, song.lastPlayedAt)
        database.listeningEventDao().insert(
            ListeningEventEntity(
                userId = LOCAL_USER,
                songId = songId,
                timestamp = now,
                sessionId = sessionId,
                eventType = if (next) "favorite" else "unfavorite",
            ),
        )
        pruneListeningHistoryBefore(listeningHistoryCutoff(now))
        next
    }

    suspend fun recordEvent(
        eventType: String,
        songId: Long,
        sessionId: String,
        selectionSource: String? = null,
        skipPositionSeconds: Long? = null,
    ) {
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            database.listeningEventDao().insert(
                ListeningEventEntity(
                    userId = LOCAL_USER,
                    songId = songId,
                    timestamp = timestamp,
                    sessionId = sessionId,
                    eventType = eventType,
                    selectionSource = selectionSource,
                    skipPositionSeconds = skipPositionSeconds,
                ),
            )
            if (eventType == "play") {
                database.songDao().get(songId)?.let { song ->
                    database.songDao().markPlayed(songId, timestamp)
                    saveUserState(song, song.isFavorite, timestamp)
                }
            }
            pruneListeningHistoryBefore(listeningHistoryCutoff(timestamp))
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name cannot be empty" }
        val now = System.currentTimeMillis()
        return database.playlistDao().insert(PlaylistEntity(name = normalizedName, createdAt = now, updatedAt = now))
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long) {
        database.withTransaction {
            val playlistDao = database.playlistDao()
            val songIds = playlistDao.getSongIds(playlistId)
            if (songId !in songIds) {
                val now = System.currentTimeMillis()
                playlistDao.upsertItems(listOf(PlaylistItemEntity(playlistId, songId, songIds.size, now)))
                database.listeningEventDao().insert(
                    ListeningEventEntity(userId = LOCAL_USER, songId = songId, timestamp = now, sessionId = WavvSession.id, eventType = "playlist_add", playlistId = playlistId),
                )
                pruneListeningHistoryBefore(listeningHistoryCutoff(now))
            }
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) {
        database.withTransaction {
            val playlistDao = database.playlistDao()
            if (songId in playlistDao.getSongIds(playlistId)) {
                val now = System.currentTimeMillis()
                playlistDao.removeSong(playlistId, songId)
                database.listeningEventDao().insert(
                    ListeningEventEntity(userId = LOCAL_USER, songId = songId, timestamp = now, sessionId = WavvSession.id, eventType = "playlist_remove", playlistId = playlistId),
                )
                pruneListeningHistoryBefore(listeningHistoryCutoff(now))
            }
        }
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name cannot be empty" }
        database.playlistDao().rename(playlistId, normalizedName, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        database.withTransaction {
            database.playlistDao().deleteItems(playlistId)
            database.playlistDao().delete(playlistId)
        }
    }

    suspend fun setFavorites(songIds: Set<Long>, favorite: Boolean, sessionId: String) {
        database.withTransaction {
            val songs = database.songDao()
            val events = database.listeningEventDao()
            val now = System.currentTimeMillis()
            songIds.forEach { songId ->
                val song = songs.get(songId) ?: return@forEach
                if (song.isFavorite != favorite) {
                    songs.setFavorite(songId, favorite)
                    saveUserState(song, favorite, song.lastPlayedAt)
                    events.insert(
                        ListeningEventEntity(
                            userId = LOCAL_USER,
                            songId = songId,
                            timestamp = now,
                            sessionId = sessionId,
                            eventType = if (favorite) "favorite" else "unfavorite",
                        ),
                    )
                }
            }
            pruneListeningHistoryBefore(listeningHistoryCutoff(now))
        }
    }

    private suspend fun pruneListeningHistoryBefore(cutoffMs: Long) {
        database.listeningEventDao().deleteBefore(cutoffMs)
        database.songDao().clearLastPlayedBefore(cutoffMs)
        database.songUserStateDao().clearLastPlayedBefore(cutoffMs)
        database.songUserStateDao().deleteEmpty()
    }

    private companion object {
        const val LOCAL_USER = "local"
        const val METADATA_JOB = "metadata_index"
        const val COMPLETED = "completed"
        const val STATE_QUERY_BATCH_SIZE = 400
        const val MAX_DISCOVERY_EVENTS = 10_000
    }

    private suspend fun saveUserState(song: SongEntity, isFavorite: Boolean, lastPlayedAt: Long?) {
        val stateDao = database.songUserStateDao()
        if (!isFavorite && lastPlayedAt == null) {
            stateDao.delete(song.uri)
        } else {
            stateDao.upsert(SongUserStateEntity(song.uri, isFavorite, lastPlayedAt))
        }
    }
}

private fun ByteArray.toFloatArray(): FloatArray {
    require(size % Float.SIZE_BYTES == 0) { "Invalid embedding byte length: $size" }
    return java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { buffer ->
        FloatArray(buffer.remaining()).also(buffer::get)
    }
}

private fun Song.toEntity(isFavorite: Boolean) = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    uri = uri,
    albumArtUri = albumArtUri,
    fileSizeBytes = fileSizeBytes,
    modifiedAt = modifiedAt,
    codec = codec,
    container = container,
    sampleRate = sampleRate,
    channels = channels,
    metadataSource = metadataSource,
    genre = genre,
    genresJson = org.json.JSONArray().apply { genreNames().forEach { put(it) } }.toString(),
    isFavorite = isFavorite,
    albumArtist = albumArtist,
    composer = composer,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    languageTagsJson = languageTags.toJsonArray(),
    languageMetadataChecked = languageMetadataChecked,
)

private fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    uri = uri,
    albumArtUri = albumArtUri,
    fileSizeBytes = fileSizeBytes,
    modifiedAt = modifiedAt,
    codec = codec,
    container = container,
    sampleRate = sampleRate,
    channels = channels,
    metadataSource = metadataSource,
    genre = genre,
    genres = runCatching {
        val storedGenres = org.json.JSONArray(genresJson)
        List(storedGenres.length()) { index -> storedGenres.getString(index) }
    }.getOrDefault(emptyList()),
    albumArtist = albumArtist,
    composer = composer,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    languageTags = runCatching {
        val languageTags = org.json.JSONArray(languageTagsJson)
        List(languageTags.length()) { index -> languageTags.getString(index) }
    }.getOrDefault(emptyList()),
    languageMetadataChecked = languageMetadataChecked,
)

private fun List<String>.toJsonArray() = org.json.JSONArray().apply { forEach(::put) }.toString()

private fun ListeningEventEntity.toLocalEvent() = LocalListeningEvent(
    songId = songId,
    timestampMs = timestamp,
    eventType = eventType,
    sessionId = sessionId,
    listenSeconds = listenSeconds,
    completionRatio = completionRatio,
    skipPositionSeconds = skipPositionSeconds,
    selectionSource = selectionSource,
    recommendationId = recommendationId,
    playlistId = playlistId,
)

private fun String.toMusicTags(): List<MusicTag> = runCatching {
    val json = org.json.JSONArray(this)
    List(json.length()) { index ->
        val tag = json.getJSONObject(index)
        MusicTag(
            task = tag.optString("task"),
            taxonomy = tag.optString("taxonomy"),
            label = tag.optString("label"),
            labelSource = tag.optString("labelSource"),
            probability = tag.optDouble("probability").toFloat(),
            threshold = tag.optDouble("threshold").toFloat(),
        )
    }.filter { it.label.isNotBlank() && it.probability.isFinite() && it.threshold.isFinite() }
}.getOrDefault(emptyList())
