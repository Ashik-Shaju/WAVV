package com.wavv.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.PrimaryKey
import androidx.room.withTransaction

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
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
)

@Entity(tableName = "listening_events")
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

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<SongEntity>

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
}

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity)
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)
}

@Dao
interface AnalysisJobDao {
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

    @Query("DELETE FROM song_embeddings WHERE songId NOT IN (:songIds)")
    suspend fun deleteExcept(songIds: List<Long>)
}

@Database(
    entities = [SongEntity::class, ListeningEventEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, AnalysisJobEntity::class, SongEmbeddingEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class WavvDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun analysisJobDao(): AnalysisJobDao
    abstract fun songEmbeddingDao(): SongEmbeddingDao
    companion object {
        @Volatile
        private var instance: WavvDatabase? = null

        fun get(context: Context): WavvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WavvDatabase::class.java,
                "wavv.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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
    }
}

class LibraryStore(private val database: WavvDatabase) {
    suspend fun replaceSongs(songs: List<Song>) {
        database.withTransaction {
            val favoriteIds = database.songDao().getFavoriteIds().toSet()
            val lastPlayedAt = database.songDao().getAll().associate { it.id to it.lastPlayedAt }
            database.songDao().deleteAll()
            database.songDao().upsertAll(
                songs.map { song -> song.toEntity(song.id in favoriteIds).copy(lastPlayedAt = lastPlayedAt[song.id]) },
            )
            if (songs.isEmpty()) database.songEmbeddingDao().deleteAll()
            else database.songEmbeddingDao().deleteExcept(songs.map(Song::id))
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

    suspend fun getEmbedding(songId: Long): SongEmbeddingEntity? = database.songEmbeddingDao().get(songId)

    suspend fun saveEmbedding(embedding: SongEmbeddingEntity) = database.songEmbeddingDao().upsert(embedding)

    suspend fun saveAnalysis(songId: Long, jobType: String, status: String, error: String? = null) {
        database.analysisJobDao().upsert(
            AnalysisJobEntity(songId, jobType, status, lastError = error, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun searchEmbeddings(query: FloatArray, songs: List<Song>, limit: Int): List<Song> {
        val byId = songs.associateBy(Song::id)
        // ponytail: linear scan is enough for the current library; add ANN only after a measured large-library bottleneck.
        return database.songEmbeddingDao().getAll()
            .asSequence()
            .mapNotNull { embedding ->
                val song = byId[embedding.songId] ?: return@mapNotNull null
                val vector = embedding.vector.toFloatArray()
                if (
                    embedding.modelId != DCLAP_MODEL_ID ||
                    embedding.sourceSizeBytes != song.fileSizeBytes ||
                    embedding.sourceModifiedAt != song.modifiedAt ||
                    vector.size != query.size ||
                    !embedding.normalized
                ) return@mapNotNull null
                song to query.indices.sumOf { index -> query[index].toDouble() * vector[index] }
            }
            .sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()
    }

    suspend fun toggleFavorite(songId: Long, sessionId: String): Boolean = database.withTransaction {
        val current = database.songDao().isFavorite(songId) ?: return@withTransaction false
        val next = !current
        database.songDao().setFavorite(songId, next)
        database.listeningEventDao().insert(
            ListeningEventEntity(
                userId = LOCAL_USER,
                songId = songId,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId,
                eventType = if (next) "favorite" else "unfavorite",
            ),
        )
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
                database.songDao().markPlayed(songId, timestamp)
            }
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return database.playlistDao().insert(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long, position: Int) {
        database.playlistDao().upsertItems(listOf(PlaylistItemEntity(playlistId, songId, position, System.currentTimeMillis())))
    }

    private companion object {
        const val LOCAL_USER = "local"
        const val METADATA_JOB = "metadata_index"
        const val COMPLETED = "completed"
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
    isFavorite = isFavorite,
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
)
