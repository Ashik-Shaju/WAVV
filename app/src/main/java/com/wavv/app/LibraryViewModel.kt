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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
        val analysisProgress: MusicAnalysisProgress? = null,
        val personalizedRecommendations: List<Song> = emptyList(),
        val smartMixes: List<SmartMix> = emptyList(),
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val libraryStore: LibraryStore,
    private val sourceStore: LibrarySourceStore,
    private val indexScheduler: LibraryIndexScheduler,
    private val dclapScheduler: DclapIndexScheduler,
    private val textSearch: DclapTextSearch,
    private val metadataRepository: MediaLibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private val _source = MutableStateFlow(sourceStore.read())
    val source: StateFlow<LibrarySource?> = _source.asStateFlow()
    private val favoriteIds = MutableStateFlow<Set<Long>>(emptySet())

    private val sessionId = WavvSession.id
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var discoveryJob: Job? = null

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
                        val analysisWorkId = dclapScheduler.enqueue()
                        val analysisInfo = dclapScheduler.observe(analysisWorkId)
                            .onEach { info ->
                                if (info != null && isCurrentSource(selectedSource)) {
                                    _state.update { current ->
                                        if (current is LibraryUiState.Ready) {
                                            current.copy(analysisProgress = info.toMusicAnalysisProgress())
                                        } else current
                                    }
                                }
                            }
                            .first { it?.state?.isFinished == true }
                        if (!isCurrentSource(selectedSource)) return@launch
                        when (analysisInfo?.state) {
                            WorkInfo.State.FAILED -> publishIndexError(
                                analysisInfo.outputData.getString(DclapIndexWorker.KEY_ERROR)
                                    ?: "Music analysis could not be completed",
                            )
                            WorkInfo.State.CANCELLED -> publishIndexError("Music analysis was canceled")
                            else -> Unit
                        }
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
        libraryStore.pruneListeningHistory()
        val songs = libraryStore.getSongs()
        val currentFavorites = libraryStore.getFavoriteIds()
        favoriteIds.value = currentFavorites
        _state.value = LibraryUiState.Ready(
            songs = songs,
            favoriteIds = currentFavorites,
            recentlyPlayed = libraryStore.getRecentlyPlayed(),
            playlists = libraryStore.getPlaylists(),
        )
        observeDiscovery(songs)
    }

    private fun observeDiscovery(songs: List<Song>) {
        discoveryJob?.cancel()
        if (songs.isEmpty()) return
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val vectors = libraryStore.audioVectors(songs)
            val tagsBySong = libraryStore.musicTags(songs)
            val cutoff = System.currentTimeMillis() - DISCOVERY_HISTORY_MS
            combine(favoriteIds, libraryStore.observeDiscoveryEvents(cutoff)) { favorites, events -> favorites to events }
                .collectLatest { (favorites, events) ->
                    val discovery = withContext(Dispatchers.Default) {
                        buildPersonalizedDiscovery(
                            songs = songs,
                            events = events,
                            favoriteIds = favorites,
                            audioVectors = vectors,
                            tagsBySong = tagsBySong,
                            currentSessionId = sessionId,
                            nowMs = System.currentTimeMillis(),
                        )
                    }
                    val recentlyPlayed = libraryStore.getRecentlyPlayed()
                    _state.update { state ->
                        if (state is LibraryUiState.Ready && state.songs.map(Song::id) == songs.map(Song::id)) {
                            state.copy(
                                recentlyPlayed = recentlyPlayed,
                                personalizedRecommendations = discovery.recommendations,
                                smartMixes = discovery.mixes,
                            )
                        } else state
                    }
                }
        }
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
                val intent = withContext(Dispatchers.Default) { parseMusicQuery(query, songs) }
                val searchableSongs = if (intent.languageCode == null) songs else withContext(Dispatchers.IO) {
                    songs.map { song ->
                        if (song.languageMetadataChecked || song.languageTags.isNotEmpty()) return@map song
                        try {
                            val tags = metadataRepository.readLanguageTags(song)
                            libraryStore.setLanguageTags(song.id, tags)
                            song.copy(languageTags = tags, languageMetadataChecked = true)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            song
                        }
                    }
                }
                val tagsBySong = withContext(Dispatchers.IO) { libraryStore.musicTags(searchableSongs) }
                _state.update { current ->
                    if (current is LibraryUiState.Ready) current.copy(songs = searchableSongs) else current
                }
                val semanticMatches = if (intent.semanticText.isBlank()) emptyList() else try {
                    withContext(Dispatchers.IO) { textSearch.search(intent.semanticText, searchableSongs, libraryStore) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyList()
                }
                val results = withContext(Dispatchers.Default) {
                    rankLocalSearch(searchableSongs, intent, semanticMatches, tagsBySong)
                }
                _state.update { state ->
                    if (state is LibraryUiState.Ready) {
                        state.copy(
                            searchResults = results,
                            semanticLoading = false,
                            semanticSearchComplete = intent.semanticText.isNotBlank() && semanticMatches.isNotEmpty(),
                        )
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
            val ids = libraryStore.getFavoriteIds()
            favoriteIds.value = ids
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = ids) else state
            }
        }
    }

    fun setFavorites(songIds: Set<Long>, favorite: Boolean) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            libraryStore.setFavorites(songIds, favorite, sessionId)
            val ids = libraryStore.getFavoriteIds()
            favoriteIds.value = ids
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = ids) else state
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                libraryStore.clearListeningHistory()
                _state.update { current ->
                    if (current is LibraryUiState.Ready) current.copy(recentlyPlayed = emptyList()) else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishIndexError(error.message ?: "Couldn't clear listening history")
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

    override fun onCleared() {
        searchJob?.cancel()
        discoveryJob?.cancel()
        textSearch.close()
        super.onCleared()
    }
}

private const val DISCOVERY_HISTORY_MS = LISTENING_HISTORY_RETENTION_MS

private fun androidx.work.WorkInfo.toMusicAnalysisProgress() = MusicAnalysisProgress(
    status = state.toMusicAnalysisStatus(),
    phase = when (progress.getString(DclapIndexWorker.KEY_PHASE)) {
        DclapIndexWorker.PHASE_DCLAP -> MusicAnalysisPhase.DCLAP
        DclapIndexWorker.PHASE_MUSIC_UNDERSTANDING -> MusicAnalysisPhase.MUSIC_UNDERSTANDING
        else -> null
    },
    completed = progress.getInt(DclapIndexWorker.KEY_COMPLETED, 0),
    total = progress.getInt(DclapIndexWorker.KEY_TOTAL, 0),
)
