package com.wavv.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 250L

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object SelectSource : LibraryUiState
    data object PermissionRequired : LibraryUiState
    data class Ready(
        val songs: List<Song>,
        val favoriteIds: Set<Long> = emptySet(),
        val recentlyPlayed: List<Song> = emptyList(),
        val playbackHistory: List<PlaybackHistoryEntry> = emptyList(),
        val playlists: List<UserPlaylist> = emptyList(),
        val searchResults: List<Song>? = null,
        val semanticLoading: Boolean = false,
        val semanticSearchComplete: Boolean = false,
        val searchError: String? = null,
        val searchNotice: String? = null,
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
    private val actionFeedbackChannel = Channel<String>(Channel.BUFFERED)
    val actionFeedback: Flow<String> = actionFeedbackChannel.receiveAsFlow()

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
                        val catalogChanged = workInfo.outputData.getBoolean(LibraryIndexWorker.KEY_CATALOG_CHANGED, true)
                        if (!hasIndexedCatalog || catalogChanged) publishReady(startDiscovery = false)
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
                        val analysisResultsChanged = when (analysisInfo?.state) {
                            WorkInfo.State.SUCCEEDED -> analysisInfo.outputData.getBoolean(
                                DclapIndexWorker.KEY_RESULTS_CHANGED,
                                true,
                            )
                            WorkInfo.State.FAILED -> {
                                publishIndexError(
                                    analysisInfo.outputData.getString(DclapIndexWorker.KEY_ERROR)
                                        ?: "Music analysis could not be completed",
                                )
                                analysisInfo.outputData.getBoolean(DclapIndexWorker.KEY_RESULTS_CHANGED, true)
                            }
                            WorkInfo.State.CANCELLED -> {
                                publishIndexError("Music analysis was canceled")
                                true
                            }
                            else -> true
                        }
                        if (!hasIndexedCatalog || catalogChanged || analysisResultsChanged) {
                            refreshMusicUnderstandingGenres()
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

    private suspend fun publishReady(startDiscovery: Boolean = true) {
        libraryStore.pruneListeningHistory()
        val indexedSongs = libraryStore.getSongs()
        val tagsBySong = libraryStore.musicTags(indexedSongs)
        val songs = indexedSongs.map { it.withMusicUnderstandingGenres(tagsBySong[it.id].orEmpty()) }
        val currentFavorites = libraryStore.getFavoriteIds()
        favoriteIds.value = currentFavorites
        _state.value = LibraryUiState.Ready(
            songs = songs,
            favoriteIds = currentFavorites,
            recentlyPlayed = libraryStore.getRecentlyPlayed(),
            playbackHistory = libraryStore.getPlaybackHistory(),
            playlists = libraryStore.getPlaylists(),
        )
        if (startDiscovery) {
            observeDiscovery(songs, tagsBySong)
        } else {
            discoveryJob?.cancel()
            discoveryJob = null
        }
    }

    private suspend fun refreshMusicUnderstandingGenres() {
        val currentSongs = (state.value as? LibraryUiState.Ready)?.songs.orEmpty()
        if (currentSongs.isEmpty()) return
        val tagsBySong = libraryStore.musicTags(currentSongs)
        val songs = currentSongs.map { it.withMusicUnderstandingGenres(tagsBySong[it.id].orEmpty()) }
        _state.update { current ->
            if (current is LibraryUiState.Ready) current.copy(songs = songs) else current
        }
        observeDiscovery(songs, tagsBySong)
    }

    private fun observeDiscovery(songs: List<Song>, tagsBySong: Map<Long, List<MusicTag>>) {
        discoveryJob?.cancel()
        if (songs.isEmpty()) return
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val vectors = libraryStore.audioVectors(songs)
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
                    val playbackHistory = libraryStore.getPlaybackHistory()
                    _state.update { state ->
                        if (state is LibraryUiState.Ready && state.songs.map(Song::id) == songs.map(Song::id)) {
                            state.copy(
                                recentlyPlayed = recentlyPlayed,
                                playbackHistory = playbackHistory,
                                personalizedRecommendations = discovery.recommendations,
                                smartMixes = discovery.mixes,
                            )
                        } else state
                    }
                }
        }
    }

    private suspend fun refreshPlaylists() {
        val playlists = runStoreAction("Couldn't refresh playlists. Reopen Library to try again.") {
            libraryStore.getPlaylists()
        } ?: return
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
                    state.copy(
                        searchResults = null,
                        semanticLoading = false,
                        semanticSearchComplete = false,
                        searchError = null,
                        searchNotice = null,
                    )
                } else state
            }
            return
        }
        val songs = (state.value as? LibraryUiState.Ready)?.songs ?: return
        _state.update { state ->
            if (state is LibraryUiState.Ready) {
                state.copy(
                    searchResults = emptyList(),
                    semanticLoading = true,
                    semanticSearchComplete = false,
                    searchError = null,
                    searchNotice = null,
                )
            } else state
        }
        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DEBOUNCE_MS)
                val catalogIntent = withContext(Dispatchers.Default) { parseMusicQuery(query, songs) }
                val languageTaggedSongs = if (catalogIntent.languageCode == null) songs else withContext(Dispatchers.IO) {
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
                val tagsBySong = withContext(Dispatchers.IO) { libraryStore.musicTags(languageTaggedSongs) }
                val searchableSongs = languageTaggedSongs.map { song ->
                    song.withMusicUnderstandingGenres(tagsBySong[song.id].orEmpty())
                }
                val intent = withContext(Dispatchers.Default) { parseMusicQuery(query, searchableSongs) }
                _state.update { current ->
                    if (current is LibraryUiState.Ready) current.copy(songs = searchableSongs) else current
                }
                var semanticSearchFailed = false
                val semanticMatches = if (intent.semanticText.isBlank()) emptyList() else try {
                    withContext(Dispatchers.IO) { textSearch.search(intent.semanticText, searchableSongs, libraryStore) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    semanticSearchFailed = true
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
                            searchError = null,
                            searchNotice = when {
                                semanticSearchFailed -> "Semantic matching is unavailable; showing local metadata results."
                                searchableSongs.isNotEmpty() && intent.semanticText.isNotBlank() && semanticMatches.isEmpty() ->
                                    "No current DCLAP audio embeddings are available; showing local metadata results."
                                else -> null
                            },
                        )
                    } else state
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { state ->
                    if (state is LibraryUiState.Ready) state.copy(
                        searchResults = emptyList(),
                        semanticLoading = false,
                        searchError = "Search couldn't be completed. Try again.",
                        searchNotice = null,
                    ) else state
                }
            }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            val ids = runStoreAction("Couldn't update this favorite. Try again.") {
                libraryStore.toggleFavorite(songId, sessionId)
                libraryStore.getFavoriteIds()
            } ?: return@launch
            favoriteIds.value = ids
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = ids) else state
            }
        }
    }

    fun setFavorites(songIds: Set<Long>, favorite: Boolean) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            val ids = runStoreAction("Couldn't update favorites. Try again.") {
                libraryStore.setFavorites(songIds, favorite, sessionId)
                libraryStore.getFavoriteIds()
            } ?: return@launch
            favoriteIds.value = ids
            _state.update { state ->
                if (state is LibraryUiState.Ready) state.copy(favoriteIds = ids) else state
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            if (runStoreAction("Couldn't clear listening history. Try again.") {
                    libraryStore.clearListeningHistory()
                } == null
            ) return@launch
            _state.update { current ->
                if (current is LibraryUiState.Ready) current.copy(
                    recentlyPlayed = emptyList(),
                    playbackHistory = emptyList(),
                ) else current
            }
        }
    }

    fun createPlaylist(name: String, songId: Long? = null, onComplete: (Boolean) -> Unit = {}) {
        if (name.isBlank()) {
            actionFeedbackChannel.trySend("Enter a playlist name.")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val id = runStoreAction("Couldn't create the playlist. Try again.") {
                libraryStore.createPlaylist(name, songId)
            }
            if (id == null) {
                onComplete(false)
                return@launch
            }
            refreshPlaylists()
            onComplete(true)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, onComplete: (Boolean) -> Unit = {}) {
        if (name.isBlank()) {
            actionFeedbackChannel.trySend("Enter a playlist name.")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            if (runStoreAction("Couldn't rename the playlist. Try again.") {
                libraryStore.renamePlaylist(playlistId, name)
            } == null) {
                onComplete(false)
                return@launch
            }
            refreshPlaylists()
            onComplete(true)
        }
    }

    fun deletePlaylist(playlistId: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (runStoreAction("Couldn't delete the playlist. Try again.") {
                libraryStore.deletePlaylist(playlistId)
            } == null) {
                onComplete(false)
                return@launch
            }
            refreshPlaylists()
            onComplete(true)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>, onComplete: (Boolean) -> Unit = {}) {
        if (songIds.isEmpty()) {
            onComplete(true)
            return
        }
        viewModelScope.launch {
            if (runStoreAction("Couldn't add tracks to the playlist. Try again.") {
                libraryStore.addSongsToPlaylist(playlistId, songIds)
            } == null) {
                onComplete(false)
                return@launch
            }
            refreshPlaylists()
            onComplete(true)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            if (runStoreAction("Couldn't remove the song from the playlist. Try again.") {
                    libraryStore.removeFromPlaylist(playlistId, songId)
                } == null
            ) return@launch
            refreshPlaylists()
        }
    }

    private suspend fun <T> runStoreAction(message: String, action: suspend () -> T): T? = try {
        withContext(Dispatchers.IO) { action() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        actionFeedbackChannel.trySend(message)
        null
    }

    override fun onCleared() {
        searchJob?.cancel()
        discoveryJob?.cancel()
        actionFeedbackChannel.close()
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
