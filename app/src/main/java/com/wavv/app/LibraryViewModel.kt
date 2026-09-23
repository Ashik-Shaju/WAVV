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
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object SelectSource : LibraryUiState
    data object PermissionRequired : LibraryUiState
    data class Ready(
        val songs: List<Song>,
        val favoriteIds: Set<Long> = emptySet(),
        val recentlyPlayed: List<Song> = emptyList(),
        val semanticResults: List<Song>? = null,
        val semanticLoading: Boolean = false,
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val repository: MediaLibraryRepository,
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
    private var semanticJob: Job? = null

    fun showSourceSelection() {
        _state.value = LibraryUiState.SelectSource
    }

    fun showPermissionRequired() {
        _state.value = LibraryUiState.PermissionRequired
    }

    fun selectAllDevice() {
        cancelIndexing()
        sourceStore.saveAllDevice()
        _source.value = LibrarySource.AllDevice
    }

    fun selectFiles(uris: List<Uri>) {
        val values = uris.map(Uri::toString).distinct()
        if (values.isEmpty()) return
        cancelIndexing()
        sourceStore.saveFiles(uris)
        _source.value = LibrarySource.Files(values)
    }

    fun selectFolder(uri: Uri) {
        cancelIndexing()
        sourceStore.saveFolder(uri)
        _source.value = LibrarySource.Folder(uri.toString())
    }

    fun clearSource() {
        cancelIndexing()
        sourceStore.clear()
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
            try {
            if (!sourceStore.isIndexed(selectedSource)) {
                val songs = repository.loadSongs(selectedSource)
                libraryStore.replaceSongs(songs)
                sourceStore.markIndexed(selectedSource)
                publishReady()
                dclapScheduler.enqueue()
                return@launch
            }

                publishReady()
                val workId = indexScheduler.enqueue()
                val workInfo = indexScheduler.observe(workId).first { it?.state?.isFinished == true }
                if (workInfo?.state == WorkInfo.State.SUCCEEDED && isCurrentSource(selectedSource)) {
                    publishReady()
                    dclapScheduler.enqueue()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value !is LibraryUiState.Ready) {
                    _state.value = LibraryUiState.Error(error.message ?: "Could not load music")
                }
            }
        }
    }

    private suspend fun publishReady() {
        _state.value = LibraryUiState.Ready(
            songs = libraryStore.getSongs(),
            favoriteIds = libraryStore.getFavoriteIds(),
            recentlyPlayed = libraryStore.getRecentlyPlayed(),
        )
    }

    private fun isCurrentSource(source: LibrarySource): Boolean =
        _source.value?.fingerprint() == source.fingerprint()

    private fun cancelIndexing() {
        loadJob?.cancel()
        loadJob = null
        indexScheduler.cancel()
        dclapScheduler.cancel()
    }

    fun search(query: String) {
        semanticJob?.cancel()
        if (query.isBlank()) {
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(semanticResults = null, semanticLoading = false) else state
            }
            return
        }
        val songs = (state.value as? LibraryUiState.Ready)?.songs.orEmpty()
        _state.update { state ->
            if (state is LibraryUiState.Ready) state.copy(semanticResults = null, semanticLoading = true) else state
        }
        semanticJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = textSearch.search(query, songs, libraryStore)
                _state.update { state ->
                    if (state is LibraryUiState.Ready) state.copy(semanticResults = results, semanticLoading = false) else state
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                    _state.update { state ->
                        if (state is LibraryUiState.Ready) state.copy(semanticResults = emptyList(), semanticLoading = false) else state
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
        semanticJob?.cancel()
        textSearch.close()
        super.onCleared()
    }
}
