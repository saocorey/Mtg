package com.example

import com.example.domain.EloEngine
import com.example.model.Deck
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.WinnerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloEngineTest {

    private val deckA = Deck(id = "deck_a", name = "Atraxa", commander = "Atraxa, Praetors' Voice")
    private val opp1 = Opponent(id = "opp_1", name = "Marcus")
    private val opp2 = Opponent(id = "opp_2", name = "Elena")
    private val opp3 = Opponent(id = "opp_3", name = "Devon")

    @Test
    fun equalFourPlayerPod_winAndLossMath() {
        val previewWin = EloEngine.previewMatchElo(
            deckElo = 1200,
            opponents = listOf(opp1, opp2, opp3),
            userWon = true,
            winningOpponentIds = emptySet(),
            isDraw = false
        )
        assertEquals(20, previewWin.deckEloDelta)
        assertEquals(1220, previewWin.deckEloAfter)
        previewWin.opponentPreviews.forEach { opp ->
            assertEquals(-7, opp.delta)
            assertEquals(1193, opp.eloAfter)
        }
    }

    @Test
    fun fourGames_oneWinThreeLosses_keepsDeckNearInitialElo() {
        val matches = listOf(
            MatchRecord(
                id = "m1", deckId = "deck_a",
                opponentIds = listOf("opp_1", "opp_2", "opp_3"),
                outcome = MatchOutcome.WIN.name, winnerType = WinnerType.USER.name,
                podSize = 4, playedAtMillis = 1000L
            ),
            MatchRecord(
                id = "m2", deckId = "deck_a",
                opponentIds = listOf("opp_1", "opp_2", "opp_3"),
                outcome = MatchOutcome.LOSS.name, winnerType = WinnerType.OPPONENT.name,
                winningOpponentId = "opp_1", winningOpponentName = "Marcus",
                podSize = 4, playedAtMillis = 2000L
            ),
            MatchRecord(
                id = "m3", deckId = "deck_a",
                opponentIds = listOf("opp_1", "opp_2", "opp_3"),
                outcome = MatchOutcome.LOSS.name, winnerType = WinnerType.OPPONENT.name,
                winningOpponentId = "opp_2", winningOpponentName = "Elena",
                podSize = 4, playedAtMillis = 3000L
            ),
            MatchRecord(
                id = "m4", deckId = "deck_a",
                opponentIds = listOf("opp_1", "opp_2", "opp_3"),
                outcome = MatchOutcome.LOSS.name, winnerType = WinnerType.OPPONENT.name,
                winningOpponentId = "opp_3", winningOpponentName = "Devon",
                podSize = 4, playedAtMillis = 4000L
            )
        )

        val ledger = EloEngine.recomputeAll(
            decks = listOf(deckA),
            opponents = listOf(opp1, opp2, opp3),
            matches = matches
        )

        val deckStats = ledger.deckStats.getValue("deck_a")
        assertEquals(4, deckStats.gamesPlayed)
        assertEquals(1, deckStats.wins)
        assertEquals(3, deckStats.losses)
        assertTrue("Expected Elo near 1200, was ${deckStats.elo}", deckStats.elo in 1195..1205)

        val marcusStats = ledger.opponentStats.getValue("opp_1")
        assertEquals(1, marcusStats.wins)
        assertEquals(1, marcusStats.winsAgainstUser)
        assertEquals(1, marcusStats.lossesToUser)
    }

    @Test
    fun upsetVictory_earnsMoreEloThanFavoriteVictory() {
        val underdogPreview = EloEngine.previewMatchElo(
            deckElo = 1050,
            opponents = listOf(
                opp1.copy(elo = 1350),
                opp2.copy(elo = 1350),
                opp3.copy(elo = 1350)
            ),
            userWon = true,
            winningOpponentIds = emptySet(),
            isDraw = false
        )

        val favoritePreview = EloEngine.previewMatchElo(
            deckElo = 1350,
            opponents = listOf(
                opp1.copy(elo = 1050),
                opp2.copy(elo = 1050),
                opp3.copy(elo = 1050)
            ),
            userWon = true,
            winningOpponentIds = emptySet(),
            isDraw = false
        )

        assertTrue(underdogPreview.deckEloDelta > favoritePreview.deckEloDelta)
    }

    @Test
    fun twoVsTwoTeamWin_splitsEloZeroSumAndPreservesTeammateH2H() {
        val preview = EloEngine.previewMatchElo(
            deckElo = 1200,
            opponents = listOf(opp1, opp2, opp3),
            userWon = true,
            winningOpponentIds = setOf("opp_1"),
            isDraw = false
        )
        assertEquals(13, preview.deckEloDelta)
        assertEquals(1213, preview.deckEloAfter)
        val opp1Prev = preview.opponentPreviews.first { it.opponentId == "opp_1" }
        val opp2Prev = preview.opponentPreviews.first { it.opponentId == "opp_2" }
        val opp3Prev = preview.opponentPreviews.first { it.opponentId == "opp_3" }
        assertEquals(13, opp1Prev.delta)
        assertEquals(-13, opp2Prev.delta)
        assertEquals(-13, opp3Prev.delta)

        val teamWinMatch = MatchRecord(
            id = "m_team_win",
            deckId = "deck_a",
            opponentIds = listOf("opp_1", "opp_2", "opp_3"),
            opponentNames = listOf("Marcus", "Elena", "Devon"),
            outcome = MatchOutcome.WIN.name,
            winnerType = WinnerType.TEAM.name,
            winningOpponentIds = listOf("opp_1"),
            winningOpponentNames = listOf("Marcus"),
            podSize = 4,
            playedAtMillis = 1000L
        )
        val ledger = EloEngine.recomputeAll(
            decks = listOf(deckA),
            opponents = listOf(opp1, opp2, opp3),
            matches = listOf(teamWinMatch)
        )

        assertEquals(1213, ledger.deckStats.getValue("deck_a").elo)
        assertEquals(1, ledger.deckStats.getValue("deck_a").wins)

        val teammateStats = ledger.opponentStats.getValue("opp_1")
        assertEquals(1213, teammateStats.elo)
        assertEquals(1, teammateStats.wins)
        assertEquals(0, teammateStats.winsAgainstUser)
        assertEquals(0, teammateStats.lossesToUser)

        val losingOppStats = ledger.opponentStats.getValue("opp_2")
        assertEquals(1187, losingOppStats.elo)
        assertEquals(0, losingOppStats.wins)
        assertEquals(1, losingOppStats.lossesToUser)

        val h2h = EloEngine.computeDeckHeadToHead("deck_a", listOf(teamWinMatch), listOf(opp1, opp2, opp3))
        val h2hMarcus = h2h.first { it.entityId == "opp_1" }
        val h2hElena = h2h.first { it.entityId == "opp_2" }
        assertEquals(0, h2hMarcus.yourWins)
        assertEquals(0, h2hMarcus.theirWins)
        assertEquals(1, h2hElena.yourWins)
        assertEquals(0, h2hElena.theirWins)
    }

    @Test
    fun twoVsTwoTeamLoss_creditsBothWinningOpponentsAgainstUser() {
        val teamLossMatch = MatchRecord(
            id = "m_team_loss",
            deckId = "deck_a",
            opponentIds = listOf("opp_1", "opp_2", "opp_3"),
            opponentNames = listOf("Marcus", "Elena", "Devon"),
            outcome = MatchOutcome.LOSS.name,
            winnerType = WinnerType.TEAM.name,
            winningOpponentIds = listOf("opp_2", "opp_3"),
            winningOpponentNames = listOf("Elena", "Devon"),
            podSize = 4,
            playedAtMillis = 2000L
        )
        val ledger = EloEngine.recomputeAll(
            decks = listOf(deckA),
            opponents = listOf(opp1, opp2, opp3),
            matches = listOf(teamLossMatch)
        )

        val deckStats = ledger.deckStats.getValue("deck_a")
        assertEquals(1187, deckStats.elo)
        assertEquals(1, deckStats.losses)

        val elenaStats = ledger.opponentStats.getValue("opp_2")
        val devonStats = ledger.opponentStats.getValue("opp_3")
        val marcusStats = ledger.opponentStats.getValue("opp_1")
        assertEquals(1213, elenaStats.elo)
        assertEquals(1, elenaStats.wins)
        assertEquals(1, elenaStats.winsAgainstUser)
        assertEquals(1213, devonStats.elo)
        assertEquals(1, devonStats.winsAgainstUser)
        assertEquals(1187, marcusStats.elo)
        assertEquals(0, marcusStats.winsAgainstUser)
        assertEquals(0, marcusStats.lossesToUser)

        val h2h = EloEngine.computeOpponentHeadToHead("opp_2", listOf(teamLossMatch), listOf(deckA))
        assertEquals(1, h2h.single().theirWins)
        assertEquals(0, h2h.single().yourWins)
    }

    @Test
    fun matchRecord_resolvedWinningOpponentIds_supportsLegacySingleWinner() {
        val legacy = MatchRecord(
            winnerType = WinnerType.OPPONENT.name,
            winningOpponentId = "opp_1",
            winningOpponentName = "Marcus"
        )
        assertEquals(listOf("opp_1"), legacy.resolvedWinningOpponentIds())
        assertEquals(listOf("Marcus"), legacy.resolvedWinningOpponentNames())
    }
}

