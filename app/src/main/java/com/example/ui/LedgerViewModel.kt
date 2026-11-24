package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LedgerRepository
import com.example.domain.EloEngine
import com.example.domain.LedgerBackupBundle
import com.example.model.Deck
import com.example.model.EloPreviewResult
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.PodOverviewStats
import com.example.model.WinnerType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LedgerData(
    val decks: List<Deck>,
    val opponents: List<Opponent>,
    val matches: List<MatchRecord>,
    val recomputed: EloEngine.RecomputedLedger,
    val overview: PodOverviewStats
)

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val ledgerState: StateFlow<UiState<LedgerData>> = combine(
        repository.observeDecks(currentUserId),
        repository.observeOpponents(currentUserId),
        repository.observeMatches(currentUserId)
    ) { decks, opponents, matches ->
        val recomputed = EloEngine.recomputeAll(decks, opponents, matches)

        // Overlay computed Elo/WLD immediately so local display always reflects replay
        val hydratedDecks = decks.map { deck ->
            val stats = recomputed.deckStats[deck.id]
            if (stats != null) {
                deck.copy(
                    elo = stats.elo.toLong(),
                    peakElo = stats.peakElo.toLong(),
                    gamesPlayed = stats.gamesPlayed.toLong(),
                    wins = stats.wins.toLong(),
                    losses = stats.losses.toLong(),
                    draws = stats.draws.toLong(),
                    lastEloDelta = stats.lastEloDelta.toLong()
                )
            } else deck
        }.sortedByDescending { it.elo }

        val hydratedOpponents = opponents.map { opp ->
            val stats = recomputed.opponentStats[opp.id]
            if (stats != null) {
                opp.copy(
                    elo = stats.elo.toLong(),
                    peakElo = stats.peakElo.toLong(),
                    gamesPlayed = stats.gamesPlayed.toLong(),
                    wins = stats.wins.toLong(),
                    winsAgainstUser = stats.winsAgainstUser.toLong(),
                    lossesToUser = stats.lossesToUser.toLong(),
                    draws = stats.draws.toLong(),
                    lastEloDelta = stats.lastEloDelta.toLong()
                )
            } else opp
        }.sortedByDescending { it.elo }

        val totalMatches = matches.size
        val totalWins = matches.count { it.matchOutcome == MatchOutcome.WIN }
        val overallWinRate = if (totalMatches == 0) 0 else ((totalWins.toDouble() / totalMatches.toDouble()) * 100.0).roundToInt()

        val activeDecks = hydratedDecks.filter { !it.archived }
        val highestEloDeck = activeDecks.maxByOrNull { it.elo } ?: hydratedDecks.maxByOrNull { it.elo }
        val topNemesis = hydratedOpponents
            .filter { it.gamesPlayed > 0 }
            .maxWithOrNull(compareBy<Opponent> { it.winRateAgainstUserPercent }.thenBy { it.winsAgainstUser })

        LedgerData(
            decks = hydratedDecks,
            opponents = hydratedOpponents,
            matches = matches,
            recomputed = recomputed,
            overview = PodOverviewStats(
                totalMatches = totalMatches,
                totalWins = totalWins,
                overallWinRatePercent = overallWinRate,
                activeDeckCount = activeDecks.size,
                opponentCount = hydratedOpponents.size,
                highestEloDeck = highestEloDeck,
                topNemesis = topNemesis
            )
        )
    }
        .map<LedgerData, UiState<LedgerData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing ledger", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load Commander ledger"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun previewElo(
        deck: Deck,
        opponents: List<Opponent>,
        userWon: Boolean,
        winningOpponentIds: Set<String>,
        isDraw: Boolean
    ): EloPreviewResult {
        return EloEngine.previewMatchElo(
            deckElo = deck.elo.toInt(),
            opponents = opponents,
            userWon = userWon,
            winningOpponentIds = winningOpponentIds,
            isDraw = isDraw
        )
    }

    fun saveDeck(
        existingDeckId: String?,
        name: String,
        commander: String,
        partnerCommander: String?,
        colorIdentity: List<String>,
        bracket: Int,
        notes: String?,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveDeck(
                userId = currentUserId,
                existingDeckId = existingDeckId,
                name = name,
                commander = commander,
                partnerCommander = partnerCommander,
                colorIdentity = colorIdentity,
                bracket = bracket,
                notes = notes
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit(if (existingDeckId == null) "Deck added to ledger" else "Deck updated")
                    onComplete(true)
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to save deck")
                    onComplete(false)
                }
            )
        }
    }

    fun setDeckArchived(deckId: String, archived: Boolean) {
        viewModelScope.launch {
            val result = repository.setDeckArchived(currentUserId, deckId, archived)
            result.fold(
                onSuccess = { _events.tryEmit(if (archived) "Deck archived" else "Deck restored") },
                onFailure = { _events.tryEmit(it.localizedMessage ?: "Failed to update deck") }
            )
        }
    }

    fun deleteDeck(deckId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteDeck(currentUserId, deckId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Deck deleted")
                    onDeleted()
                },
                onFailure = { _events.tryEmit(it.localizedMessage ?: "Failed to delete deck") }
            )
        }
    }

    fun saveOpponent(
        existingOpponentId: String?,
        name: String,
        favoriteCommander: String?,
        avatarColorHex: String,
        notes: String?,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveOpponent(
                userId = currentUserId,
                existingOpponentId = existingOpponentId,
                name = name,
                favoriteCommander = favoriteCommander,
                avatarColorHex = avatarColorHex,
                notes = notes
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { newId ->
                    _events.tryEmit(if (existingOpponentId == null) "Opponent added" else "Opponent updated")
                    onComplete(newId)
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to save opponent")
                    onComplete(null)
                }
            )
        }
    }

    fun deleteOpponent(opponentId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteOpponent(currentUserId, opponentId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Opponent deleted")
                    onDeleted()
                },
                onFailure = { _events.tryEmit(it.localizedMessage ?: "Failed to delete opponent") }
            )
        }
    }

    fun recordMatch(
        deck: Deck,
        selectedOpponents: List<Opponent>,
        opponentCommanders: List<String>,
        userWon: Boolean,
        winningOpponents: List<Opponent>,
        isDraw: Boolean,
        turnCount: Int?,
        notes: String?,
        playedAtMillis: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val current = (ledgerState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.recordMatchAndRecompute(
                userId = currentUserId,
                deck = deck,
                selectedOpponents = selectedOpponents,
                opponentCommanders = opponentCommanders,
                userWon = userWon,
                winningOpponents = winningOpponents,
                isDraw = isDraw,
                turnCount = turnCount,
                notes = notes,
                playedAtMillis = playedAtMillis,
                allDecks = current.decks,
                allOpponents = current.opponents,
                existingMatches = current.matches
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Pod match logged & Elo updated")
                    onComplete(true)
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to record match")
                    onComplete(false)
                }
            )
        }
    }

    fun deleteMatch(matchId: String) {
        val current = (ledgerState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteMatchAndRecompute(
                userId = currentUserId,
                matchId = matchId,
                allDecks = current.decks,
                allOpponents = current.opponents,
                existingMatches = current.matches
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { _events.tryEmit("Match removed & Elo recalculated") },
                onFailure = { _events.tryEmit(it.localizedMessage ?: "Failed to delete match") }
            )
        }
    }

    fun importBackup(
        bundle: LedgerBackupBundle,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val current = (ledgerState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.importLedgerBackup(
                userId = currentUserId,
                bundle = bundle,
                existingDecks = current.decks,
                existingOpponents = current.opponents,
                existingMatches = current.matches
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { summary ->
                    val message = buildString {
                        if (summary.importedMatches > 0) {
                            append("Imported ${summary.importedMatches} match")
                            if (summary.importedMatches != 1) append("es")
                        } else {
                            append("No new matches imported")
                        }
                        val extras = buildList {
                            if (summary.createdDecks > 0) add("${summary.createdDecks} deck${if (summary.createdDecks == 1) "" else "s"}")
                            if (summary.createdOpponents > 0) add("${summary.createdOpponents} opponent${if (summary.createdOpponents == 1) "" else "s"}")
                            if (summary.skippedDuplicates > 0) add("${summary.skippedDuplicates} duplicate${if (summary.skippedDuplicates == 1) "" else "s"} skipped")
                        }
                        if (extras.isNotEmpty()) {
                            append(" (${extras.joinToString(", ")})")
                        }
                    }
                    _events.tryEmit(message)
                    onComplete(true)
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to import match history")
                    onComplete(false)
                }
            )
        }
    }

    fun notifyUser(message: String) {
        _events.tryEmit(message)
    }

    private companion object {
        const val TAG = "LedgerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
