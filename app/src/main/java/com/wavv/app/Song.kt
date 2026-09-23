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
)

fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return songs

    return songs.filter { song ->
        song.title.contains(normalizedQuery, ignoreCase = true) ||
            song.artist.contains(normalizedQuery, ignoreCase = true) ||
            song.album.contains(normalizedQuery, ignoreCase = true)
    }
}
