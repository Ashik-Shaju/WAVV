package com.wavv.app

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String?,
    val fileSizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val codec: String? = null,
    val container: String? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val metadataSource: String = "unknown",
    val genre: String? = null,
    val genres: List<String> = emptyList(),
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val languageTags: List<String> = emptyList(),
    val languageMetadataChecked: Boolean = false,
    val inferredGenres: List<String> = emptyList(),
    val author: String? = null,
    val writer: String? = null,
    val recordingDate: String? = null,
    val isCompilation: Boolean? = null,
    val bitrate: Int? = null,
    val trackCount: Int? = null,
)

private val artistCreditSeparator = Regex("\\s*[,/]\\s*")
private val featuredArtistSeparator = Regex("(?i)\\s+(?:feat(?:uring)?\\.?|ft\\.?)\\s+")

internal fun String.splitArtistCredit(): List<String> = split(artistCreditSeparator)
    .flatMap { credit -> credit.split(featuredArtistSeparator) }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy { it.lowercase() }

internal fun Song.artistNames(): List<String> = artist.splitArtistCredit()

internal fun Song.mainArtistName(): String = artistNames().firstOrNull().orEmpty()

internal fun Song.contributingArtistNames(): List<String> = artistNames().drop(1)

data class UserPlaylist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val updatedAt: Long,
)

data class PlaybackHistoryEntry(
    val id: Long,
    val song: Song,
    val playedAt: Long,
)

internal fun albumTrackOrder(songs: List<Song>): List<Song> = songs.sortedWith(
    compareBy<Song> { it.discNumber ?: 1 }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() },
)
