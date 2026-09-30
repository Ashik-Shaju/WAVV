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
)

data class UserPlaylist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val updatedAt: Long,
)
