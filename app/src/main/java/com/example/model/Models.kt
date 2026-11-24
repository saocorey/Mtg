package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

enum class MatchOutcome {
    WIN,
    LOSS,
    DRAW;

    companion object {
        fun fromRaw(raw: String?): MatchOutcome =
            entries.firstOrNull { it.name == raw } ?: LOSS
    }
}

enum class WinnerType {
    USER,
    OPPONENT,
    TEAM,
    DRAW;

    companion object {
        fun fromRaw(raw: String?): WinnerType =
            entries.firstOrNull { it.name == raw } ?: OPPONENT
    }
}

@Keep
@IgnoreExtraProperties
data class Deck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val commander: String = "",
    val partnerCommander: String? = null,
    val colorIdentity: List<String> = emptyList(),
    val bracket: Long = 3L,
    val notes: String? = null,
    val archived: Boolean = false,
    val elo: Long = 1200L,
    val peakElo: Long = 1200L,
    val gamesPlayed: Long = 0L,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val draws: Long = 0L,
    val lastEloDelta: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val winRatePercent: Int
        get() = if (gamesPlayed <= 0L) 0 else ((wins.toDouble() / gamesPlayed.toDouble()) * 100.0).roundToInt()

    val commanderDisplay: String
        get() = if (partnerCommander.isNullOrBlank()) commander else "$commander // $partnerCommander"
}

@Keep
@IgnoreExtraProperties
data class Opponent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val favoriteCommander: String? = null,
    val notes: String? = null,
    val avatarColorHex: String = "#5EA8E6",
    val elo: Long = 1200L,
    val peakElo: Long = 1200L,
    val gamesPlayed: Long = 0L,
    val wins: Long = 0L,
    val winsAgainstUser: Long = 0L,
    val lossesToUser: Long = 0L,
    val draws: Long = 0L,
    val lastEloDelta: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    /**
     * Opponent's win rate across all logged pods they played in.
     */
    val overallWinRatePercent: Int
        get() = if (gamesPlayed <= 0L) 0 else ((wins.toDouble() / gamesPlayed.toDouble()) * 100.0).roundToInt()

    /**
     * Opponent's head-to-head win rate vs you (winsAgainstUser / (winsAgainstUser + lossesToUser)).
     * Also provides decided pod rate vs you when either of you won.
     */
    val winRateAgainstUserPercent: Int
        get() {
            val decided = winsAgainstUser + lossesToUser
            return if (decided <= 0L) 0 else ((winsAgainstUser.toDouble() / decided.toDouble()) * 100.0).roundToInt()
        }

    /**
     * Opponent's win rate in all games played against you (winsAgainstUser / gamesPlayed).
     */
    val podWinRateAgainstUserPercent: Int
        get() = if (gamesPlayed <= 0L) 0 else ((winsAgainstUser.toDouble() / gamesPlayed.toDouble()) * 100.0).roundToInt()
}

@Keep
@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deckId: String = "",
    val deckName: String = "",
    val deckCommander: String = "",
    val deckColorIdentity: List<String> = emptyList(),
    val opponentIds: List<String> = emptyList(),
    val opponentNames: List<String> = emptyList(),
    val opponentCommanders: List<String> = emptyList(),
    val outcome: String = MatchOutcome.WIN.name,
    val winnerType: String = WinnerType.USER.name,
    val winningOpponentId: String? = null,
    val winningOpponentName: String? = null,
    val winningOpponentIds: List<String> = emptyList(),
    val winningOpponentNames: List<String> = emptyList(),
    val podSize: Long = 4L,
    val turnCount: Long? = null,
    val notes: String? = null,
    val deckEloBefore: Long = 1200L,
    val deckEloAfter: Long = 1200L,
    val deckEloDelta: Long = 0L,
    val opponentEloDeltas: Map<String, Long> = emptyMap(),
    val playedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val matchOutcome: MatchOutcome
        get() = MatchOutcome.fromRaw(outcome)

    val matchWinnerType: WinnerType
        get() = WinnerType.fromRaw(winnerType)

    fun resolvedWinningOpponentIds(): List<String> =
        when {
            winningOpponentIds.isNotEmpty() -> winningOpponentIds
            winningOpponentId != null -> listOf(winningOpponentId)
            else -> emptyList()
        }

    fun resolvedWinningOpponentNames(): List<String> =
        when {
            winningOpponentNames.isNotEmpty() -> winningOpponentNames
            winningOpponentName != null -> listOf(winningOpponentName)
            else -> emptyList()
        }
}

data class EloPoint(
    val matchIndex: Int,
    val elo: Int,
    val delta: Int,
    val label: String
)

data class HeadToHeadStat(
    val entityId: String,
    val entityName: String,
    val subtitle: String,
    val gamesTogether: Int,
    val yourWins: Int,
    val theirWins: Int,
    val draws: Int,
    val avatarColorHex: String? = null,
    val colorIdentity: List<String> = emptyList()
) {
    val yourWinRatePercent: Int
        get() = if (gamesTogether <= 0) 0 else ((yourWins.toDouble() / gamesTogether.toDouble()) * 100.0).roundToInt()

    val theirWinRatePercent: Int
        get() = if (gamesTogether <= 0) 0 else ((theirWins.toDouble() / gamesTogether.toDouble()) * 100.0).roundToInt()
}

data class EloPreviewResult(
    val deckEloBefore: Int,
    val deckEloAfter: Int,
    val deckEloDelta: Int,
    val opponentPreviews: List<OpponentEloPreview>
)

data class OpponentEloPreview(
    val opponentId: String,
    val opponentName: String,
    val eloBefore: Int,
    val eloAfter: Int,
    val delta: Int
)

data class PodOverviewStats(
    val totalMatches: Int = 0,
    val totalWins: Int = 0,
    val overallWinRatePercent: Int = 0,
    val activeDeckCount: Int = 0,
    val opponentCount: Int = 0,
    val highestEloDeck: Deck? = null,
    val topNemesis: Opponent? = null
)
