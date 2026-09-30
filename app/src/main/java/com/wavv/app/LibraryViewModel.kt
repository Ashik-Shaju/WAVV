package com.wavv.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object SelectSource : LibraryUiState
    data object PermissionRequired : LibraryUiState
    data class Ready(
        val songs: List<Song>,
        val favoriteIds: Set<Long> = emptySet(),
        val recentlyPlayed: List<Song> = emptyList(),
        val playlists: List<UserPlaylist> = emptyList(),
        val searchResults: List<Song>? = null,
        val semanticLoading: Boolean = false,
        val semanticSearchComplete: Boolean = false,
        val indexingError: String? = null,
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val libraryStore: LibraryStore,
    private val sourceStore: LibrarySourceStore,
    private val indexScheduler: LibraryIndexScheduler,
    private val dclapScheduler: DclapIndexScheduler,
    private val textSearch: DclapTextSearch,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private val _source = MutableStateFlow(sourceStore.read())
    val source: StateFlow<LibrarySource?> = _source.asStateFlow()

    private val sessionId = java.util.UUID.randomUUID().toString()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun showSourceSelection() {
        _state.value = LibraryUiState.SelectSource
    }

    fun showPermissionRequired() {
        _state.value = LibraryUiState.PermissionRequired
    }

    fun selectAllDevice() {
        sourceStore.saveAllDevice()
        cancelIndexing()
        _source.value = LibrarySource.AllDevice
    }

    fun selectFiles(uris: List<Uri>): Int {
        val accepted = sourceStore.saveFiles(uris)
        val failedCount = uris.distinct().size - accepted.size
        if (accepted.isEmpty()) return failedCount
        cancelIndexing()
        _source.value = LibrarySource.Files(accepted.map(Uri::toString))
        return failedCount
    }

    fun selectFolder(uri: Uri): Boolean {
        if (!sourceStore.saveFolder(uri)) return false
        cancelIndexing()
        _source.value = LibrarySource.Folder(uri.toString())
        return true
    }

    fun addFolder(uri: Uri): Boolean {
        if (!sourceStore.addFolder(uri)) return false
        cancelIndexing()
        _source.value = sourceStore.read()
        return true
    }

    fun removeFolder(uri: String) {
        sourceStore.removeFolder(uri)
        cancelIndexing()
        _source.value = sourceStore.read()
    }

    fun clearSource() {
        sourceStore.clear()
        cancelIndexing()
        _source.value = null
        showSourceSelection()
    }

    fun loadSongs() {
        val selectedSource = _source.value ?: run {
            showSourceSelection()
            return
        }
        _state.value = LibraryUiState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            var hasIndexedCatalog = false
            try {
                hasIndexedCatalog = sourceStore.isIndexed(selectedSource)
                if (hasIndexedCatalog) publishReady()
                val workId = indexScheduler.enqueue()
                val workInfo = indexScheduler.observe(workId).first { it?.state?.isFinished == true }
                if (!isCurrentSource(selectedSource)) return@launch
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        publishReady()
                        dclapScheduler.enqueue()
                    }
                    WorkInfo.State.FAILED -> publishIndexError(
                        workInfo.outputData.getString(LibraryIndexWorker.KEY_ERROR) ?: "Could not refresh the music library",
                    )
                    WorkInfo.State.CANCELLED -> publishIndexError("Music library indexing was canceled")
                    else -> publishIndexError("Music library indexing did not complete")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishIndexError(error.message ?: "Could not load music")
            }
        }
    }

    private fun publishIndexError(message: String) {
        _state.update { state ->
            if (state is LibraryUiState.Ready) state.copy(indexingError = message)
            else LibraryUiState.Error(message)
        }
    }

    private suspend fun publishReady() {
        _state.value = LibraryUiState.Ready(
            songs = libraryStore.getSongs(),
            favoriteIds = libraryStore.getFavoriteIds(),
            recentlyPlayed = libraryStore.getRecentlyPlayed(),
            playlists = libraryStore.getPlaylists(),
        )
    }

    private suspend fun refreshPlaylists() {
        val playlists = libraryStore.getPlaylists()
        _state.update { state -> if (state is LibraryUiState.Ready) state.copy(playlists = playlists) else state }
    }

    private fun isCurrentSource(source: LibrarySource): Boolean =
        sourceStore.read()?.fingerprint() == source.fingerprint()

    private fun cancelIndexing() {
        loadJob?.cancel()
        loadJob = null
        indexScheduler.cancel()
        dclapScheduler.cancel()
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { state ->
                if (state is LibraryUiState.Ready) {
                    state.copy(searchResults = null, semanticLoading = false, semanticSearchComplete = false)
                } else state
            }
            return
        }
        val songs = (state.value as? LibraryUiState.Ready)?.songs ?: return
        _state.update { state ->
            if (state is LibraryUiState.Ready) {
                state.copy(searchResults = emptyList(), semanticLoading = true, semanticSearchComplete = false)
            } else state
        }
        searchJob = viewModelScope.launch {
            try {
                val localResults = withContext(Dispatchers.Default) { searchSongs(songs, query) }
                _state.update { state ->
                    if (state is LibraryUiState.Ready) state.copy(searchResults = localResults) else state
                }
                delay(250)
                val semanticSongs = withContext(Dispatchers.IO) { textSearch.search(query, songs, libraryStore) }
                val results = withContext(Dispatchers.Default) { searchSongs(songs, query, semanticSongs) }
                _state.update { state ->
                    if (state is LibraryUiState.Ready) {
                        state.copy(searchResults = results, semanticLoading = false, semanticSearchComplete = true)
                    } else state
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { state ->
                    if (state is LibraryUiState.Ready) state.copy(semanticLoading = false) else state
                }
            }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.toggleFavorite(songId, sessionId)
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = libraryStore.getFavoriteIds()) else state
            }
        }
    }

    fun setFavorites(songIds: Set<Long>, favorite: Boolean) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.setFavorites(songIds, favorite, sessionId)
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = libraryStore.getFavoriteIds()) else state
            }
        }
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { libraryStore.createPlaylist(name) }
            val playlists = withContext(Dispatchers.IO) { libraryStore.getPlaylists() }
            _state.update { state -> if (state is LibraryUiState.Ready) state.copy(playlists = playlists) else state }
            onCreated(id)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.renamePlaylist(playlistId, name)
            refreshPlaylists()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.deletePlaylist(playlistId)
            refreshPlaylists()
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.addToPlaylist(playlistId, songId)
            refreshPlaylists()
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.removeFromPlaylist(playlistId, songId)
            refreshPlaylists()
        }
    }

    fun recordPlaybackEvent(
        eventType: String,
        songId: Long,
        selectionSource: String? = null,
        skipPositionMs: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.recordEvent(
                eventType = eventType,
                songId = songId,
                sessionId = sessionId,
                selectionSource = selectionSource,
                skipPositionSeconds = skipPositionMs?.div(1_000L),
            )
            if (eventType == "play") {
                _state.update { state ->
                    if (state is LibraryUiState.Ready) state.copy(recentlyPlayed = libraryStore.getRecentlyPlayed()) else state
                }
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        textSearch.close()
        super.onCleared()
    }
}
