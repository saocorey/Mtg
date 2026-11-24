package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.EloEngine
import com.example.domain.ImportBackupSummary
import com.example.domain.LedgerBackupBundle
import com.example.model.Deck
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.WinnerType
import com.example.ui.theme.OpponentAvatarPalette
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LedgerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    private fun decksCollection(userId: String) =
        db.collection("users").document(userId).collection("decks")

    private fun opponentsCollection(userId: String) =
        db.collection("users").document(userId).collection("opponents")

    private fun matchesCollection(userId: String) =
        db.collection("users").document(userId).collection("matches")

    fun observeDecks(userId: String): Flow<List<Deck>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/decks"
        emitAll(
            decksCollection(uid)
                .orderBy("elo", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Deck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeOpponents(userId: String): Flow<List<Opponent>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/opponents"
        emitAll(
            opponentsCollection(uid)
                .orderBy("elo", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Opponent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMatches(userId: String): Flow<List<MatchRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/matches"
        emitAll(
            matchesCollection(uid)
                .orderBy("playedAtMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MatchRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    suspend fun saveDeck(
        userId: String,
        existingDeckId: String?,
        name: String,
        commander: String,
        partnerCommander: String?,
        colorIdentity: List<String>,
        bracket: Int,
        notes: String?
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedCommander = commander.trim()
        val trimmedPartner = partnerCommander?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        val cleanColors = colorIdentity.distinct().take(5)
        val cleanBracket = bracket.coerceIn(1, 5).toLong()

        var docPath = "users/$userId/decks"
        return try {
            val uid = requireUserId(userId)
            require(trimmedName.length in 1..60) { "Deck name must be 1 to 60 characters." }
            require(trimmedCommander.length in 1..80) { "Commander name must be 1 to 80 characters." }
            if (trimmedPartner != null) {
                require(trimmedPartner.length <= 80) { "Partner commander must be 80 characters or fewer." }
            }
            if (trimmedNotes != null) {
                require(trimmedNotes.length <= 300) { "Notes must be 300 characters or fewer." }
            }

            if (existingDeckId.isNullOrBlank()) {
                val docRef = decksCollection(uid).document()
                docPath = docRef.path
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("name", trimmedName)
                    put("commander", trimmedCommander)
                    if (trimmedPartner != null) put("partnerCommander", trimmedPartner)
                    put("colorIdentity", cleanColors)
                    put("bracket", cleanBracket)
                    if (trimmedNotes != null) put("notes", trimmedNotes)
                    put("archived", false)
                    put("elo", EloEngine.INITIAL_ELO.toLong())
                    put("peakElo", EloEngine.INITIAL_ELO.toLong())
                    put("gamesPlayed", 0L)
                    put("wins", 0L)
                    put("losses", 0L)
                    put("draws", 0L)
                    put("lastEloDelta", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = decksCollection(uid).document(existingDeckId)
                docPath = docRef.path
                val updatePayload = buildMap<String, Any> {
                    put("name", trimmedName)
                    put("commander", trimmedCommander)
                    put("partnerCommander", trimmedPartner ?: FieldValue.delete())
                    put("colorIdentity", cleanColors)
                    put("bracket", cleanBracket)
                    put("notes", trimmedNotes ?: FieldValue.delete())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
                Result.success(existingDeckId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (existingDeckId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE
            handleFirestoreError(e, op, docPath)
            Result.failure(e)
        }
    }

    suspend fun setDeckArchived(userId: String, deckId: String, archived: Boolean): Result<Unit> {
        var docPath = "users/$userId/decks/$deckId"
        return try {
            val uid = requireUserId(userId)
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val docRef = decksCollection(uid).document(deckId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDeck(userId: String, deckId: String): Result<Unit> {
        var docPath = "users/$userId/decks/$deckId"
        return try {
            val uid = requireUserId(userId)
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val docRef = decksCollection(uid).document(deckId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveOpponent(
        userId: String,
        existingOpponentId: String?,
        name: String,
        favoriteCommander: String?,
        avatarColorHex: String,
        notes: String?
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedFav = favoriteCommander?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        val validHex = if (avatarColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            avatarColorHex.uppercase()
        } else {
            "#5EA8E6"
        }

        var docPath = "users/$userId/opponents"
        return try {
            val uid = requireUserId(userId)
            require(trimmedName.length in 1..50) { "Opponent name must be 1 to 50 characters." }
            if (trimmedFav != null) {
                require(trimmedFav.length <= 80) { "Commander name must be 80 characters or fewer." }
            }
            if (trimmedNotes != null) {
                require(trimmedNotes.length <= 300) { "Notes must be 300 characters or fewer." }
            }

            if (existingOpponentId.isNullOrBlank()) {
                val docRef = opponentsCollection(uid).document()
                docPath = docRef.path
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("name", trimmedName)
                    if (trimmedFav != null) put("favoriteCommander", trimmedFav)
                    if (trimmedNotes != null) put("notes", trimmedNotes)
                    put("avatarColorHex", validHex)
                    put("elo", EloEngine.INITIAL_ELO.toLong())
                    put("peakElo", EloEngine.INITIAL_ELO.toLong())
                    put("gamesPlayed", 0L)
                    put("wins", 0L)
                    put("winsAgainstUser", 0L)
                    put("lossesToUser", 0L)
                    put("draws", 0L)
                    put("lastEloDelta", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = opponentsCollection(uid).document(existingOpponentId)
                docPath = docRef.path
                val updatePayload = buildMap<String, Any> {
                    put("name", trimmedName)
                    put("favoriteCommander", trimmedFav ?: FieldValue.delete())
                    put("notes", trimmedNotes ?: FieldValue.delete())
                    put("avatarColorHex", validHex)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
                Result.success(existingOpponentId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (existingOpponentId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE
            handleFirestoreError(e, op, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOpponent(userId: String, opponentId: String): Result<Unit> {
        var docPath = "users/$userId/opponents/$opponentId"
        return try {
            val uid = requireUserId(userId)
            require(opponentId.isNotBlank()) { "Opponent ID cannot be blank." }
            val docRef = opponentsCollection(uid).document(opponentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchAndRecompute(
        userId: String,
        deck: Deck,
        selectedOpponents: List<Opponent>,
        opponentCommanders: List<String>,
        userWon: Boolean,
        winningOpponents: List<Opponent>,
        isDraw: Boolean,
        turnCount: Int?,
        notes: String?,
        playedAtMillis: Long,
        allDecks: List<Deck>,
        allOpponents: List<Opponent>,
        existingMatches: List<MatchRecord>
    ): Result<Unit> {
        var docPath = "users/$userId/matches"
        return try {
            val uid = requireUserId(userId)
            require(deck.id.isNotBlank()) { "Please select a deck." }
            require(selectedOpponents.size in 1..5) { "Select between 1 and 5 opponents." }

            val uniqueWinningOpps = winningOpponents
                .distinctBy { it.id }
                .filter { winOpp -> selectedOpponents.any { it.id == winOpp.id } }

            val winnerType: WinnerType
            val outcome: MatchOutcome
            if (isDraw) {
                winnerType = WinnerType.DRAW
                outcome = MatchOutcome.DRAW
            } else {
                val totalWinners = (if (userWon) 1 else 0) + uniqueWinningOpps.size
                val podSize = 1 + selectedOpponents.size
                require(totalWinners >= 1) { "Select at least one winner or Pod Draw." }
                require(totalWinners < podSize) { "Choose Pod Draw if every player won, or leave at least one non-winner." }
                when {
                    userWon && uniqueWinningOpps.isEmpty() -> {
                        winnerType = WinnerType.USER
                        outcome = MatchOutcome.WIN
                    }
                    !userWon && uniqueWinningOpps.size == 1 -> {
                        winnerType = WinnerType.OPPONENT
                        outcome = MatchOutcome.LOSS
                    }
                    else -> {
                        winnerType = WinnerType.TEAM
                        outcome = if (userWon) MatchOutcome.WIN else MatchOutcome.LOSS
                    }
                }
            }

            val cleanTurn = turnCount?.takeIf { it in 1..99 }?.toLong()
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300)

            val newMatchRef = matchesCollection(uid).document()
            docPath = newMatchRef.path
            val oppIds = selectedOpponents.map { it.id }
            val oppNames = selectedOpponents.map { it.name.take(50) }
            val oppCmds = selectedOpponents.indices.map { idx ->
                opponentCommanders.getOrNull(idx)?.trim()?.take(80) ?: ""
            }
            val singleWinOpp = if (winnerType == WinnerType.OPPONENT) uniqueWinningOpps.firstOrNull() else null
            val teamWinIds = if (winnerType == WinnerType.TEAM) uniqueWinningOpps.map { it.id } else emptyList()
            val teamWinNames = if (winnerType == WinnerType.TEAM) uniqueWinningOpps.map { it.name.take(50) } else emptyList()

            val draftMatch = MatchRecord(
                id = newMatchRef.id,
                userId = uid,
                deckId = deck.id,
                deckName = deck.name.take(60),
                deckCommander = deck.commander.take(80),
                deckColorIdentity = deck.colorIdentity.take(5),
                opponentIds = oppIds,
                opponentNames = oppNames,
                opponentCommanders = oppCmds,
                outcome = outcome.name,
                winnerType = winnerType.name,
                winningOpponentId = singleWinOpp?.id,
                winningOpponentName = singleWinOpp?.name?.take(50),
                winningOpponentIds = teamWinIds,
                winningOpponentNames = teamWinNames,
                podSize = (1 + selectedOpponents.size).toLong(),
                turnCount = cleanTurn,
                notes = cleanNotes,
                playedAtMillis = playedAtMillis.coerceAtLeast(1L)
            )

            val updatedMatches = existingMatches + draftMatch
            val ledger = EloEngine.recomputeAll(allDecks, allOpponents, updatedMatches)
            val matchElo = ledger.matchUpdates[newMatchRef.id]

            val batchOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>()

            val createPayload = buildMap<String, Any> {
                put("userId", uid)
                put("deckId", draftMatch.deckId)
                put("deckName", draftMatch.deckName)
                put("deckCommander", draftMatch.deckCommander)
                put("deckColorIdentity", draftMatch.deckColorIdentity)
                put("opponentIds", draftMatch.opponentIds)
                put("opponentNames", draftMatch.opponentNames)
                put("opponentCommanders", draftMatch.opponentCommanders)
                put("outcome", draftMatch.outcome)
                put("winnerType", draftMatch.winnerType)
                if (draftMatch.winningOpponentId != null) put("winningOpponentId", draftMatch.winningOpponentId)
                if (draftMatch.winningOpponentName != null) put("winningOpponentName", draftMatch.winningOpponentName)
                if (draftMatch.winningOpponentIds.isNotEmpty()) put("winningOpponentIds", draftMatch.winningOpponentIds)
                if (draftMatch.winningOpponentNames.isNotEmpty()) put("winningOpponentNames", draftMatch.winningOpponentNames)
                put("podSize", draftMatch.podSize)
                if (draftMatch.turnCount != null) put("turnCount", draftMatch.turnCount)
                if (draftMatch.notes != null) put("notes", draftMatch.notes)
                put("deckEloBefore", (matchElo?.deckEloBefore ?: EloEngine.INITIAL_ELO).toLong())
                put("deckEloAfter", (matchElo?.deckEloAfter ?: EloEngine.INITIAL_ELO).toLong())
                put("deckEloDelta", (matchElo?.deckEloDelta ?: 0).toLong())
                put("opponentEloDeltas", matchElo?.opponentEloDeltas ?: emptyMap<String, Long>())
                put("playedAtMillis", draftMatch.playedAtMillis)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batchOps.add(newMatchRef to (true to createPayload))

            appendRecomputedUpdates(
                uid = uid,
                ledger = ledger,
                allDecks = allDecks,
                allOpponents = allOpponents,
                existingMatches = existingMatches,
                batchOps = batchOps
            )

            commitBatches(batchOps)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMatchAndRecompute(
        userId: String,
        matchId: String,
        allDecks: List<Deck>,
        allOpponents: List<Opponent>,
        existingMatches: List<MatchRecord>
    ): Result<Unit> {
        var docPath = "users/$userId/matches/$matchId"
        return try {
            val uid = requireUserId(userId)
            require(matchId.isNotBlank()) { "Match ID cannot be blank." }
            val matchRef = matchesCollection(uid).document(matchId)
            docPath = matchRef.path

            val remainingMatches = existingMatches.filter { it.id != matchId }
            val ledger = EloEngine.recomputeAll(allDecks, allOpponents, remainingMatches)

            val batch = db.batch()
            batch.delete(matchRef)
            batch.commit().await()

            val updateOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>()
            appendRecomputedUpdates(
                uid = uid,
                ledger = ledger,
                allDecks = allDecks,
                allOpponents = allOpponents,
                existingMatches = remainingMatches,
                batchOps = updateOps
            )
            commitBatches(updateOps)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun importLedgerBackup(
        userId: String,
        bundle: LedgerBackupBundle,
        existingDecks: List<Deck>,
        existingOpponents: List<Opponent>,
        existingMatches: List<MatchRecord>
    ): Result<ImportBackupSummary> {
        var docPath = "users/$userId/matches"
        return try {
            val uid = requireUserId(userId)

            val setupOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>()

            val existingDecksById = existingDecks.associateBy { it.id }
            val existingDecksByKey = existingDecks.associateBy {
                it.name.trim().lowercase() to it.commander.trim().lowercase()
            }.toMutableMap()
            val resolvedDecksBySourceId = mutableMapOf<String, Deck>()
            val allDecksAfterImport = existingDecks.toMutableList()
            var createdDecks = 0

            fun ensureDeck(
                sourceId: String,
                name: String,
                commander: String,
                partnerCommander: String?,
                colorIdentity: List<String>,
                bracket: Int,
                notes: String?,
                archived: Boolean
            ): Deck {
                resolvedDecksBySourceId[sourceId]?.let { return it }
                val existing = existingDecksById[sourceId]
                    ?: existingDecksByKey[name.trim().lowercase() to commander.trim().lowercase()]
                if (existing != null) {
                    resolvedDecksBySourceId[sourceId] = existing
                    return existing
                }

                val docRef = decksCollection(uid).document()
                val cleanName = name.trim().take(60).ifEmpty { "Imported Deck" }
                val cleanCmd = commander.trim().take(80).ifEmpty { "Commander" }
                val cleanPartner = partnerCommander?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
                val cleanColors = colorIdentity.distinct().take(5)
                val cleanBracket = bracket.coerceIn(1, 5).toLong()
                val cleanNotes = notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

                val newDeck = Deck(
                    id = docRef.id,
                    userId = uid,
                    name = cleanName,
                    commander = cleanCmd,
                    partnerCommander = cleanPartner,
                    colorIdentity = cleanColors,
                    bracket = cleanBracket,
                    notes = cleanNotes,
                    archived = archived
                )
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("name", cleanName)
                    put("commander", cleanCmd)
                    if (cleanPartner != null) put("partnerCommander", cleanPartner)
                    put("colorIdentity", cleanColors)
                    put("bracket", cleanBracket)
                    if (cleanNotes != null) put("notes", cleanNotes)
                    put("archived", archived)
                    put("elo", EloEngine.INITIAL_ELO.toLong())
                    put("peakElo", EloEngine.INITIAL_ELO.toLong())
                    put("gamesPlayed", 0L)
                    put("wins", 0L)
                    put("losses", 0L)
                    put("draws", 0L)
                    put("lastEloDelta", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                setupOps.add(docRef to (true to payload))
                resolvedDecksBySourceId[sourceId] = newDeck
                existingDecksByKey[cleanName.lowercase() to cleanCmd.lowercase()] = newDeck
                allDecksAfterImport.add(newDeck)
                createdDecks++
                return newDeck
            }

            bundle.decks.forEach { d ->
                ensureDeck(
                    sourceId = d.sourceId,
                    name = d.name,
                    commander = d.commander,
                    partnerCommander = d.partnerCommander,
                    colorIdentity = d.colorIdentity,
                    bracket = d.bracket,
                    notes = d.notes,
                    archived = d.archived
                )
            }

            val existingOppsById = existingOpponents.associateBy { it.id }
            val existingOppsByName = existingOpponents.associateBy {
                it.name.trim().lowercase()
            }.toMutableMap()
            val resolvedOppsBySourceId = mutableMapOf<String, Opponent>()
            val allOppsAfterImport = existingOpponents.toMutableList()
            var createdOpponents = 0

            fun ensureOpponent(
                sourceId: String,
                name: String,
                favoriteCommander: String?,
                notes: String?,
                avatarColorHex: String?
            ): Opponent {
                resolvedOppsBySourceId[sourceId]?.let { return it }
                val existing = existingOppsById[sourceId]
                    ?: existingOppsByName[name.trim().lowercase()]
                if (existing != null) {
                    resolvedOppsBySourceId[sourceId] = existing
                    return existing
                }

                val docRef = opponentsCollection(uid).document()
                val cleanName = name.trim().take(50).ifEmpty { "Opponent" }
                val cleanFav = favoriteCommander?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
                val cleanNotes = notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() }
                val hex = if (avatarColorHex != null && avatarColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    avatarColorHex.uppercase()
                } else {
                    OpponentAvatarPalette[allOppsAfterImport.size % OpponentAvatarPalette.size]
                }

                val newOpp = Opponent(
                    id = docRef.id,
                    userId = uid,
                    name = cleanName,
                    favoriteCommander = cleanFav,
                    notes = cleanNotes,
                    avatarColorHex = hex
                )
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("name", cleanName)
                    if (cleanFav != null) put("favoriteCommander", cleanFav)
                    if (cleanNotes != null) put("notes", cleanNotes)
                    put("avatarColorHex", hex)
                    put("elo", EloEngine.INITIAL_ELO.toLong())
                    put("peakElo", EloEngine.INITIAL_ELO.toLong())
                    put("gamesPlayed", 0L)
                    put("wins", 0L)
                    put("winsAgainstUser", 0L)
                    put("lossesToUser", 0L)
                    put("draws", 0L)
                    put("lastEloDelta", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                setupOps.add(docRef to (true to payload))
                resolvedOppsBySourceId[sourceId] = newOpp
                existingOppsByName[cleanName.lowercase()] = newOpp
                allOppsAfterImport.add(newOpp)
                createdOpponents++
                return newOpp
            }

            bundle.opponents.forEach { o ->
                ensureOpponent(
                    sourceId = o.sourceId,
                    name = o.name,
                    favoriteCommander = o.favoriteCommander,
                    notes = o.notes,
                    avatarColorHex = o.avatarColorHex
                )
            }

            // Ensure any decks/opponents referenced only inside matches also exist
            bundle.matches.forEach { m ->
                ensureDeck(
                    sourceId = m.deckId,
                    name = m.deckName,
                    commander = m.deckCommander,
                    partnerCommander = null,
                    colorIdentity = m.deckColorIdentity,
                    bracket = 3,
                    notes = null,
                    archived = false
                )
                m.opponentIds.forEachIndexed { idx, oldOppId ->
                    val oppName = m.opponentNames.getOrNull(idx) ?: "Opponent ${idx + 1}"
                    val oppCmd = m.opponentCommanders.getOrNull(idx)?.takeIf { it.isNotBlank() }
                    ensureOpponent(
                        sourceId = oldOppId,
                        name = oppName,
                        favoriteCommander = oppCmd,
                        notes = null,
                        avatarColorHex = null
                    )
                }
            }

            // Commit new decks & opponents first so Firestore rules `exists(deckId)` passes for matches
            if (setupOps.isNotEmpty()) {
                docPath = "users/$uid/decks"
                commitBatches(setupOps)
            }

            fun matchSignature(m: MatchRecord): String = listOf(
                m.deckId,
                m.playedAtMillis.toString(),
                m.podSize.toString(),
                m.outcome,
                m.winnerType,
                m.opponentIds.sorted().joinToString(","),
                m.resolvedWinningOpponentIds().sorted().joinToString(",")
            ).joinToString("|")

            val seenSignatures = existingMatches.map { matchSignature(it) }.toMutableSet()
            val newMatchRefs = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, MatchRecord>>()
            var skippedDuplicates = 0

            bundle.matches.forEach { em ->
                val resolvedDeck = ensureDeck(
                    sourceId = em.deckId,
                    name = em.deckName,
                    commander = em.deckCommander,
                    partnerCommander = null,
                    colorIdentity = em.deckColorIdentity,
                    bracket = 3,
                    notes = null,
                    archived = false
                )
                val resolvedOpps = em.opponentIds.mapIndexed { idx, oldOppId ->
                    val name = em.opponentNames.getOrNull(idx) ?: "Opponent ${idx + 1}"
                    ensureOpponent(oldOppId, name, null, null, null)
                }
                val oppIds = resolvedOpps.map { it.id }
                val oppNames = resolvedOpps.map { it.name.take(50) }
                val oppCmds = resolvedOpps.indices.map { idx ->
                    em.opponentCommanders.getOrNull(idx)?.trim()?.take(80) ?: ""
                }

                // Resolve winning opponents against the pod
                val rawWinOppIds = buildList {
                    if (em.winningOpponentIds.isNotEmpty()) {
                        addAll(em.winningOpponentIds)
                    } else if (!em.winningOpponentId.isNullOrBlank()) {
                        add(em.winningOpponentId)
                    }
                }
                val resolvedWinOpps = rawWinOppIds.mapIndexedNotNull { wIdx, oldWinId ->
                    val bySource = resolvedOppsBySourceId[oldWinId]
                    val candidate = if (bySource != null && bySource.id in oppIds) {
                        bySource
                    } else {
                        val fallbackName = em.winningOpponentNames.getOrNull(wIdx) ?: em.winningOpponentName
                        fallbackName?.let { name ->
                            resolvedOpps.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                        }
                    }
                    candidate
                }.distinctBy { it.id }

                val rawWinnerType = WinnerType.fromRaw(em.winnerType)
                val rawOutcome = MatchOutcome.fromRaw(em.outcome)
                val userWon = rawWinnerType == WinnerType.USER ||
                    (rawWinnerType == WinnerType.TEAM && rawOutcome == MatchOutcome.WIN)

                val finalWinnerType: WinnerType
                val finalOutcome: MatchOutcome
                when {
                    rawWinnerType == WinnerType.DRAW -> {
                        finalWinnerType = WinnerType.DRAW
                        finalOutcome = MatchOutcome.DRAW
                    }
                    userWon && resolvedWinOpps.isEmpty() -> {
                        finalWinnerType = WinnerType.USER
                        finalOutcome = MatchOutcome.WIN
                    }
                    !userWon && resolvedWinOpps.size == 1 -> {
                        finalWinnerType = WinnerType.OPPONENT
                        finalOutcome = MatchOutcome.LOSS
                    }
                    (if (userWon) 1 else 0) + resolvedWinOpps.size >= 2 &&
                        (if (userWon) 1 else 0) + resolvedWinOpps.size < (1 + resolvedOpps.size) -> {
                        finalWinnerType = WinnerType.TEAM
                        finalOutcome = if (userWon) MatchOutcome.WIN else MatchOutcome.LOSS
                    }
                    else -> {
                        finalWinnerType = WinnerType.DRAW
                        finalOutcome = MatchOutcome.DRAW
                    }
                }

                val singleWinOpp = if (finalWinnerType == WinnerType.OPPONENT) resolvedWinOpps.firstOrNull() else null
                val teamWinIds = if (finalWinnerType == WinnerType.TEAM) resolvedWinOpps.map { it.id } else emptyList()
                val teamWinNames = if (finalWinnerType == WinnerType.TEAM) resolvedWinOpps.map { it.name.take(50) } else emptyList()

                val matchRef = matchesCollection(uid).document()
                val draftMatch = MatchRecord(
                    id = matchRef.id,
                    userId = uid,
                    deckId = resolvedDeck.id,
                    deckName = resolvedDeck.name.take(60),
                    deckCommander = resolvedDeck.commander.take(80),
                    deckColorIdentity = resolvedDeck.colorIdentity.take(5),
                    opponentIds = oppIds,
                    opponentNames = oppNames,
                    opponentCommanders = oppCmds,
                    outcome = finalOutcome.name,
                    winnerType = finalWinnerType.name,
                    winningOpponentId = singleWinOpp?.id,
                    winningOpponentName = singleWinOpp?.name?.take(50),
                    winningOpponentIds = teamWinIds,
                    winningOpponentNames = teamWinNames,
                    podSize = (1 + resolvedOpps.size).toLong(),
                    turnCount = em.turnCount?.takeIf { it in 1..99 }?.toLong(),
                    notes = em.notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                    playedAtMillis = em.playedAtMillis.coerceAtLeast(1L)
                )

                val sig = matchSignature(draftMatch)
                if (!seenSignatures.add(sig)) {
                    skippedDuplicates++
                } else {
                    newMatchRefs.add(matchRef to draftMatch)
                }
            }

            if (newMatchRefs.isNotEmpty()) {
                docPath = "users/$uid/matches"
                val newMatches = newMatchRefs.map { it.second }
                val allMatchesCombined = existingMatches + newMatches
                val ledger = EloEngine.recomputeAll(allDecksAfterImport, allOppsAfterImport, allMatchesCombined)

                val matchBatchOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>()
                newMatchRefs.forEach { (ref, draft) ->
                    val matchElo = ledger.matchUpdates[draft.id]
                    val payload = buildMap<String, Any> {
                        put("userId", uid)
                        put("deckId", draft.deckId)
                        put("deckName", draft.deckName)
                        put("deckCommander", draft.deckCommander)
                        put("deckColorIdentity", draft.deckColorIdentity)
                        put("opponentIds", draft.opponentIds)
                        put("opponentNames", draft.opponentNames)
                        put("opponentCommanders", draft.opponentCommanders)
                        put("outcome", draft.outcome)
                        put("winnerType", draft.winnerType)
                        if (draft.winningOpponentId != null) put("winningOpponentId", draft.winningOpponentId)
                        if (draft.winningOpponentName != null) put("winningOpponentName", draft.winningOpponentName)
                        if (draft.winningOpponentIds.isNotEmpty()) put("winningOpponentIds", draft.winningOpponentIds)
                        if (draft.winningOpponentNames.isNotEmpty()) put("winningOpponentNames", draft.winningOpponentNames)
                        put("podSize", draft.podSize)
                        if (draft.turnCount != null) put("turnCount", draft.turnCount)
                        if (draft.notes != null) put("notes", draft.notes)
                        put("deckEloBefore", (matchElo?.deckEloBefore ?: EloEngine.INITIAL_ELO).toLong())
                        put("deckEloAfter", (matchElo?.deckEloAfter ?: EloEngine.INITIAL_ELO).toLong())
                        put("deckEloDelta", (matchElo?.deckEloDelta ?: 0).toLong())
                        put("opponentEloDeltas", matchElo?.opponentEloDeltas ?: emptyMap<String, Long>())
                        put("playedAtMillis", draft.playedAtMillis)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    matchBatchOps.add(ref to (true to payload))
                }

                appendRecomputedUpdates(
                    uid = uid,
                    ledger = ledger,
                    allDecks = allDecksAfterImport,
                    allOpponents = allOppsAfterImport,
                    existingMatches = existingMatches,
                    batchOps = matchBatchOps
                )
                commitBatches(matchBatchOps)
            }

            Result.success(
                ImportBackupSummary(
                    importedMatches = newMatchRefs.size,
                    skippedDuplicates = skippedDuplicates,
                    createdDecks = createdDecks,
                    createdOpponents = createdOpponents
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun appendRecomputedUpdates(
        uid: String,
        ledger: EloEngine.RecomputedLedger,
        allDecks: List<Deck>,
        allOpponents: List<Opponent>,
        existingMatches: List<MatchRecord>,
        batchOps: MutableList<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>
    ) {
        allDecks.forEach { deck ->
            val computed = ledger.deckStats[deck.id] ?: return@forEach
            if (deck.elo.toInt() != computed.elo ||
                deck.peakElo.toInt() != computed.peakElo ||
                deck.gamesPlayed.toInt() != computed.gamesPlayed ||
                deck.wins.toInt() != computed.wins ||
                deck.losses.toInt() != computed.losses ||
                deck.draws.toInt() != computed.draws ||
                deck.lastEloDelta.toInt() != computed.lastEloDelta
            ) {
                batchOps.add(
                    decksCollection(uid).document(deck.id) to (
                        false to mapOf(
                            "elo" to computed.elo.toLong(),
                            "peakElo" to computed.peakElo.toLong(),
                            "gamesPlayed" to computed.gamesPlayed.toLong(),
                            "wins" to computed.wins.toLong(),
                            "losses" to computed.losses.toLong(),
                            "draws" to computed.draws.toLong(),
                            "lastEloDelta" to computed.lastEloDelta.toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
            }
        }

        allOpponents.forEach { opp ->
            val computed = ledger.opponentStats[opp.id] ?: return@forEach
            if (opp.elo.toInt() != computed.elo ||
                opp.peakElo.toInt() != computed.peakElo ||
                opp.gamesPlayed.toInt() != computed.gamesPlayed ||
                opp.wins.toInt() != computed.wins ||
                opp.winsAgainstUser.toInt() != computed.winsAgainstUser ||
                opp.lossesToUser.toInt() != computed.lossesToUser ||
                opp.draws.toInt() != computed.draws ||
                opp.lastEloDelta.toInt() != computed.lastEloDelta
            ) {
                batchOps.add(
                    opponentsCollection(uid).document(opp.id) to (
                        false to mapOf(
                            "elo" to computed.elo.toLong(),
                            "peakElo" to computed.peakElo.toLong(),
                            "gamesPlayed" to computed.gamesPlayed.toLong(),
                            "wins" to computed.wins.toLong(),
                            "winsAgainstUser" to computed.winsAgainstUser.toLong(),
                            "lossesToUser" to computed.lossesToUser.toLong(),
                            "draws" to computed.draws.toLong(),
                            "lastEloDelta" to computed.lastEloDelta.toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
            }
        }

        existingMatches.forEach { match ->
            val recomputed = ledger.matchUpdates[match.id] ?: return@forEach
            if (match.deckEloBefore.toInt() != recomputed.deckEloBefore ||
                match.deckEloAfter.toInt() != recomputed.deckEloAfter ||
                match.deckEloDelta.toInt() != recomputed.deckEloDelta ||
                match.opponentEloDeltas != recomputed.opponentEloDeltas
            ) {
                batchOps.add(
                    matchesCollection(uid).document(match.id) to (
                        false to mapOf(
                            "deckEloBefore" to recomputed.deckEloBefore.toLong(),
                            "deckEloAfter" to recomputed.deckEloAfter.toLong(),
                            "deckEloDelta" to recomputed.deckEloDelta.toLong(),
                            "opponentEloDeltas" to recomputed.opponentEloDeltas,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
            }
        }
    }

    private suspend fun commitBatches(
        ops: List<Pair<com.google.firebase.firestore.DocumentReference, Pair<Boolean, Map<String, Any>>>>
    ) {
        if (ops.isEmpty()) return
        ops.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, pair) ->
                val (isCreate, payload) = pair
                if (isCreate) batch.set(ref, payload) else batch.update(ref, payload)
            }
            batch.commit().await()
        }
    }
}
