package com.example.domain

import com.example.model.Deck
import com.example.model.EloPoint
import com.example.model.EloPreviewResult
import com.example.model.HeadToHeadStat
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.OpponentEloPreview
import com.example.model.WinnerType
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Multiplayer Commander Pairwise Elo Engine.
 *
 * Every Deck and Opponent begins at 1200 Elo.
 * In an N-player pod (2..6 players), every pair (i, j) evaluates expected score:
 *   E_ij = 1 / (1 + 10^((R_j - R_i) / 400))
 * Actual pairwise score S_ij:
 *   - Winner vs Loser: 1.0 (Winner) / 0.0 (Loser)
 *   - Winner vs Winner (teammates) or Loser vs Loser: 0.5 each (tied placement)
 *   - Draw: 0.5 for all pairs
 * Normalized Elo delta per participant i:
 *   delta_i = round((K_FACTOR / (N - 1)) * sum_{j != i}(S_ij - E_ij))
 *
 * Notice that in a balanced 4-player pod (all at 1200) with K = 40:
 *   - Winner earns +20 Elo
 *   - Each of the 3 losers drops -7 Elo (exact -6.67)
 * Winning 1 in 4 equal-Elo pods maintains 1200 Elo.
 */
object EloEngine {
    const val INITIAL_ELO = 1200
    const val K_FACTOR = 40.0
    const val MIN_ELO = 100
    const val MAX_ELO = 4000

    data class ComputedDeckStats(
        val elo: Int = INITIAL_ELO,
        val peakElo: Int = INITIAL_ELO,
        val gamesPlayed: Int = 0,
        val wins: Int = 0,
        val losses: Int = 0,
        val draws: Int = 0,
        val lastEloDelta: Int = 0,
        val recentForm: List<MatchOutcome> = emptyList(),
        val eloHistory: List<EloPoint> = listOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
    )

    data class ComputedOpponentStats(
        val elo: Int = INITIAL_ELO,
        val peakElo: Int = INITIAL_ELO,
        val gamesPlayed: Int = 0,
        val wins: Int = 0,
        val winsAgainstUser: Int = 0,
        val lossesToUser: Int = 0,
        val draws: Int = 0,
        val lastEloDelta: Int = 0,
        val eloHistory: List<EloPoint> = listOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
    )

    data class RecomputedMatch(
        val matchId: String,
        val deckEloBefore: Int,
        val deckEloAfter: Int,
        val deckEloDelta: Int,
        val opponentEloDeltas: Map<String, Long>
    )

    data class RecomputedLedger(
        val deckStats: Map<String, ComputedDeckStats>,
        val opponentStats: Map<String, ComputedOpponentStats>,
        val matchUpdates: Map<String, RecomputedMatch>
    )

    fun expectedScore(ratingA: Double, ratingB: Double): Double {
        return 1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))
    }

    fun previewMatchElo(
        deckElo: Int,
        opponents: List<Opponent>,
        userWon: Boolean,
        winningOpponentIds: Set<String>,
        isDraw: Boolean
    ): EloPreviewResult {
        val participants = buildList {
            add("USER_DECK" to deckElo.toDouble())
            opponents.forEach { add(it.id to it.elo.toDouble()) }
        }
        val winnerParticipantIds = if (isDraw) {
            emptySet()
        } else {
            buildSet {
                if (userWon) add("USER_DECK")
                opponents.forEach { opp ->
                    if (opp.id in winningOpponentIds) add(opp.id)
                }
            }
        }

        val deltas = computePodDeltas(participants, winnerParticipantIds)
        val deckDelta = deltas["USER_DECK"] ?: 0
        val newDeckElo = (deckElo + deckDelta).coerceIn(MIN_ELO, MAX_ELO)

        val oppPreviews = opponents.map { opp ->
            val before = opp.elo.toInt()
            val delta = deltas[opp.id] ?: 0
            val after = (before + delta).coerceIn(MIN_ELO, MAX_ELO)
            OpponentEloPreview(
                opponentId = opp.id,
                opponentName = opp.name,
                eloBefore = before,
                eloAfter = after,
                delta = delta
            )
        }

        return EloPreviewResult(
            deckEloBefore = deckElo,
            deckEloAfter = newDeckElo,
            deckEloDelta = deckDelta,
            opponentPreviews = oppPreviews
        )
    }

    fun recomputeAll(
        decks: List<Deck>,
        opponents: List<Opponent>,
        matches: List<MatchRecord>
    ): RecomputedLedger {
        val deckRatings = decks.associate { it.id to INITIAL_ELO.toDouble() }.toMutableMap()
        val deckPeak = decks.associate { it.id to INITIAL_ELO }.toMutableMap()
        val deckGames = decks.associate { it.id to 0 }.toMutableMap()
        val deckWins = decks.associate { it.id to 0 }.toMutableMap()
        val deckLosses = decks.associate { it.id to 0 }.toMutableMap()
        val deckDraws = decks.associate { it.id to 0 }.toMutableMap()
        val deckLastDelta = decks.associate { it.id to 0 }.toMutableMap()
        val deckForm = decks.associate { it.id to mutableListOf<MatchOutcome>() }.toMutableMap()
        val deckHistory = decks.associate {
            it.id to mutableListOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
        }.toMutableMap()

        val oppRatings = opponents.associate { it.id to INITIAL_ELO.toDouble() }.toMutableMap()
        val oppPeak = opponents.associate { it.id to INITIAL_ELO }.toMutableMap()
        val oppGames = opponents.associate { it.id to 0 }.toMutableMap()
        val oppWins = opponents.associate { it.id to 0 }.toMutableMap()
        val oppWinsVsUser = opponents.associate { it.id to 0 }.toMutableMap()
        val oppLossesToUser = opponents.associate { it.id to 0 }.toMutableMap()
        val oppDraws = opponents.associate { it.id to 0 }.toMutableMap()
        val oppLastDelta = opponents.associate { it.id to 0 }.toMutableMap()
        val oppHistory = opponents.associate {
            it.id to mutableListOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
        }.toMutableMap()

        val matchUpdates = mutableMapOf<String, RecomputedMatch>()

        val orderedMatches = matches.sortedWith(
            compareBy<MatchRecord> { it.playedAtMillis }
                .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )

        orderedMatches.forEachIndexed { idx, match ->
            val deckKey = "DECK:${match.deckId}"
            val currentDeckRating = deckRatings.getOrPut(match.deckId) { INITIAL_ELO.toDouble() }
            val distinctOpponentIds = match.opponentIds.distinct()

            val participants = buildList {
                add(deckKey to currentDeckRating)
                distinctOpponentIds.forEach { oppId ->
                    val oppRating = oppRatings.getOrPut(oppId) { INITIAL_ELO.toDouble() }
                    add("OPP:$oppId" to oppRating)
                }
            }

            val winningOppIdSet = match.resolvedWinningOpponentIds().toSet()
            val winnerKeys: Set<String> = when (match.matchWinnerType) {
                WinnerType.DRAW -> emptySet()
                WinnerType.USER -> setOf(deckKey)
                WinnerType.OPPONENT -> winningOppIdSet.map { "OPP:$it" }.toSet()
                WinnerType.TEAM -> buildSet {
                    if (match.matchOutcome == MatchOutcome.WIN) add(deckKey)
                    winningOppIdSet.forEach { add("OPP:$it") }
                }
            }

            val deltas = computePodDeltas(participants, winnerKeys)
            val deckDelta = deltas[deckKey] ?: 0
            val deckBeforeInt = currentDeckRating.roundToInt().coerceIn(MIN_ELO, MAX_ELO)
            val newDeckRating = (currentDeckRating + deckDelta).coerceIn(MIN_ELO.toDouble(), MAX_ELO.toDouble())
            val deckAfterInt = newDeckRating.roundToInt().coerceIn(MIN_ELO, MAX_ELO)

            deckRatings[match.deckId] = newDeckRating
            deckPeak[match.deckId] = maxOf(deckPeak[match.deckId] ?: INITIAL_ELO, deckAfterInt)
            deckGames[match.deckId] = (deckGames[match.deckId] ?: 0) + 1
            deckLastDelta[match.deckId] = deckDelta

            when (match.matchOutcome) {
                MatchOutcome.WIN -> deckWins[match.deckId] = (deckWins[match.deckId] ?: 0) + 1
                MatchOutcome.LOSS -> deckLosses[match.deckId] = (deckLosses[match.deckId] ?: 0) + 1
                MatchOutcome.DRAW -> deckDraws[match.deckId] = (deckDraws[match.deckId] ?: 0) + 1
            }

            deckForm.getOrPut(match.deckId) { mutableListOf() }.add(match.matchOutcome)
            deckHistory.getOrPut(match.deckId) {
                mutableListOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
            }.add(
                EloPoint(
                    matchIndex = (deckGames[match.deckId] ?: idx + 1),
                    elo = deckAfterInt,
                    delta = deckDelta,
                    label = match.matchOutcome.name
                )
            )

            val oppDeltaMap = mutableMapOf<String, Long>()
            distinctOpponentIds.forEach { oppId ->
                val oppBefore = oppRatings[oppId] ?: INITIAL_ELO.toDouble()
                val oppDelta = deltas["OPP:$oppId"] ?: 0
                val newOppRating = (oppBefore + oppDelta).coerceIn(MIN_ELO.toDouble(), MAX_ELO.toDouble())
                val newOppInt = newOppRating.roundToInt().coerceIn(MIN_ELO, MAX_ELO)

                oppRatings[oppId] = newOppRating
                oppPeak[oppId] = maxOf(oppPeak[oppId] ?: INITIAL_ELO, newOppInt)
                oppGames[oppId] = (oppGames[oppId] ?: 0) + 1
                oppLastDelta[oppId] = oppDelta
                oppDeltaMap[oppId] = oppDelta.toLong()

                val oppWon = match.matchWinnerType != WinnerType.DRAW && oppId in winningOppIdSet
                val userWon = match.matchWinnerType != WinnerType.DRAW && match.matchOutcome == MatchOutcome.WIN

                when {
                    match.matchWinnerType == WinnerType.DRAW -> {
                        oppDraws[oppId] = (oppDraws[oppId] ?: 0) + 1
                    }
                    oppWon -> {
                        oppWins[oppId] = (oppWins[oppId] ?: 0) + 1
                        if (!userWon) {
                            oppWinsVsUser[oppId] = (oppWinsVsUser[oppId] ?: 0) + 1
                        }
                    }
                    userWon -> {
                        oppLossesToUser[oppId] = (oppLossesToUser[oppId] ?: 0) + 1
                    }
                }

                oppHistory.getOrPut(oppId) {
                    mutableListOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
                }.add(
                    EloPoint(
                        matchIndex = (oppGames[oppId] ?: idx + 1),
                        elo = newOppInt,
                        delta = oppDelta,
                        label = when {
                            match.matchWinnerType == WinnerType.DRAW -> "DRAW"
                            oppWon -> "WIN"
                            else -> "LOSS"
                        }
                    )
                )
            }

            if (match.id.isNotBlank()) {
                matchUpdates[match.id] = RecomputedMatch(
                    matchId = match.id,
                    deckEloBefore = deckBeforeInt,
                    deckEloAfter = deckAfterInt,
                    deckEloDelta = deckDelta,
                    opponentEloDeltas = oppDeltaMap
                )
            }
        }

        val finalDeckStats = deckRatings.keys.associateWith { deckId ->
            val currentElo = (deckRatings[deckId] ?: INITIAL_ELO.toDouble()).roundToInt().coerceIn(MIN_ELO, MAX_ELO)
            ComputedDeckStats(
                elo = currentElo,
                peakElo = maxOf(deckPeak[deckId] ?: INITIAL_ELO, currentElo),
                gamesPlayed = deckGames[deckId] ?: 0,
                wins = deckWins[deckId] ?: 0,
                losses = deckLosses[deckId] ?: 0,
                draws = deckDraws[deckId] ?: 0,
                lastEloDelta = deckLastDelta[deckId] ?: 0,
                recentForm = (deckForm[deckId] ?: emptyList()).takeLast(5),
                eloHistory = deckHistory[deckId] ?: listOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
            )
        }

        val finalOppStats = oppRatings.keys.associateWith { oppId ->
            val currentElo = (oppRatings[oppId] ?: INITIAL_ELO.toDouble()).roundToInt().coerceIn(MIN_ELO, MAX_ELO)
            ComputedOpponentStats(
                elo = currentElo,
                peakElo = maxOf(oppPeak[oppId] ?: INITIAL_ELO, currentElo),
                gamesPlayed = oppGames[oppId] ?: 0,
                wins = oppWins[oppId] ?: 0,
                winsAgainstUser = oppWinsVsUser[oppId] ?: 0,
                lossesToUser = oppLossesToUser[oppId] ?: 0,
                draws = oppDraws[oppId] ?: 0,
                lastEloDelta = oppLastDelta[oppId] ?: 0,
                eloHistory = oppHistory[oppId] ?: listOf(EloPoint(0, INITIAL_ELO, 0, "Start"))
            )
        }

        return RecomputedLedger(
            deckStats = finalDeckStats,
            opponentStats = finalOppStats,
            matchUpdates = matchUpdates
        )
    }

    private fun computePodDeltas(
        participants: List<Pair<String, Double>>,
        winnerKeys: Set<String>
    ): Map<String, Int> {
        val n = participants.size
        if (n < 2) return participants.associate { it.first to 0 }
        val scale = K_FACTOR / (n - 1).toDouble()
        val isDrawOrTieAll = winnerKeys.isEmpty() || winnerKeys.size >= n

        return participants.associate { (idA, ratingA) ->
            var deltaSum = 0.0
            for ((idB, ratingB) in participants) {
                if (idA == idB) continue
                val expected = expectedScore(ratingA, ratingB)
                val actual = if (isDrawOrTieAll) {
                    0.5
                } else {
                    val aWon = idA in winnerKeys
                    val bWon = idB in winnerKeys
                    when {
                        aWon && !bWon -> 1.0
                        !aWon && bWon -> 0.0
                        else -> 0.5
                    }
                }
                deltaSum += (actual - expected)
            }
            val rawDelta = (scale * deltaSum).roundToInt().coerceIn(-500, 500)
            idA to rawDelta
        }
    }

    fun computeDeckHeadToHead(
        deckId: String,
        matches: List<MatchRecord>,
        opponents: List<Opponent>
    ): List<HeadToHeadStat> {
        val oppById = opponents.associateBy { it.id }
        val deckMatches = matches.filter { it.deckId == deckId }
        val oppIdsSeen = deckMatches.flatMap { it.opponentIds }.distinct()

        return oppIdsSeen.map { oppId ->
            val shared = deckMatches.filter { oppId in it.opponentIds }
            val yourWins = shared.count {
                it.matchOutcome == MatchOutcome.WIN && oppId !in it.resolvedWinningOpponentIds()
            }
            val theirWins = shared.count {
                it.matchOutcome == MatchOutcome.LOSS && oppId in it.resolvedWinningOpponentIds()
            }
            val draws = shared.count { it.matchOutcome == MatchOutcome.DRAW }
            val opp = oppById[oppId]
            val fallbackName = shared.firstOrNull()?.let { m ->
                val idx = m.opponentIds.indexOf(oppId)
                m.opponentNames.getOrNull(idx)
            } ?: "Opponent"
            HeadToHeadStat(
                entityId = oppId,
                entityName = opp?.name ?: fallbackName,
                subtitle = opp?.favoriteCommander ?: "Playgroup Member",
                gamesTogether = shared.size,
                yourWins = yourWins,
                theirWins = theirWins,
                draws = draws,
                avatarColorHex = opp?.avatarColorHex ?: "#5EA8E6"
            )
        }.sortedByDescending { it.gamesTogether }
    }

    fun computeOpponentHeadToHead(
        opponentId: String,
        matches: List<MatchRecord>,
        decks: List<Deck>
    ): List<HeadToHeadStat> {
        val deckById = decks.associateBy { it.id }
        val sharedMatches = matches.filter { opponentId in it.opponentIds }
        val deckIdsSeen = sharedMatches.map { it.deckId }.distinct()

        return deckIdsSeen.map { deckId ->
            val vsDeck = sharedMatches.filter { it.deckId == deckId }
            val yourWins = vsDeck.count {
                it.matchOutcome == MatchOutcome.WIN && opponentId !in it.resolvedWinningOpponentIds()
            }
            val theirWins = vsDeck.count {
                it.matchOutcome == MatchOutcome.LOSS && opponentId in it.resolvedWinningOpponentIds()
            }
            val draws = vsDeck.count { it.matchOutcome == MatchOutcome.DRAW }
            val deck = deckById[deckId]
            val fallback = vsDeck.firstOrNull()
            HeadToHeadStat(
                entityId = deckId,
                entityName = deck?.name ?: fallback?.deckName ?: "Deck",
                subtitle = deck?.commanderDisplay ?: fallback?.deckCommander ?: "",
                gamesTogether = vsDeck.size,
                yourWins = yourWins,
                theirWins = theirWins,
                draws = draws,
                colorIdentity = deck?.colorIdentity ?: fallback?.deckColorIdentity ?: emptyList()
            )
        }.sortedByDescending { it.gamesTogether }
    }
}
