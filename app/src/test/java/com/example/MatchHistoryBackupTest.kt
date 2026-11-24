package com.example

import com.example.domain.MatchHistoryBackup
import com.example.model.Deck
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.WinnerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MatchHistoryBackupTest {

    @Test
    fun serializeAndParse_roundTripsDecksOpponentsAndTeamMatches() {
        val deck = Deck(
            id = "d1",
            name = "Atraxa",
            commander = "Atraxa, Praetors' Voice",
            partnerCommander = null,
            colorIdentity = listOf("W", "U", "B", "G"),
            bracket = 4L,
            notes = "Counters"
        )
        val opp1 = Opponent(id = "o1", name = "Marcus", favoriteCommander = "Krenko", avatarColorHex = "#00F0FF")
        val opp2 = Opponent(id = "o2", name = "Elena", favoriteCommander = "Yuriko", avatarColorHex = "#FF4FD8")
        val opp3 = Opponent(id = "o3", name = "Devon", avatarColorHex = "#FFD166")

        val teamMatch = MatchRecord(
            id = "m1",
            deckId = "d1",
            deckName = "Atraxa",
            deckCommander = "Atraxa, Praetors' Voice",
            deckColorIdentity = listOf("W", "U", "B", "G"),
            opponentIds = listOf("o1", "o2", "o3"),
            opponentNames = listOf("Marcus", "Elena", "Devon"),
            opponentCommanders = listOf("Krenko", "Yuriko", ""),
            outcome = MatchOutcome.WIN.name,
            winnerType = WinnerType.TEAM.name,
            winningOpponentIds = listOf("o1"),
            winningOpponentNames = listOf("Marcus"),
            podSize = 4L,
            turnCount = 9L,
            notes = "2v2 win",
            playedAtMillis = 1_700_000_000_000L
        )

        val json = MatchHistoryBackup.serialize(
            decks = listOf(deck),
            opponents = listOf(opp1, opp2, opp3),
            matches = listOf(teamMatch),
            exportedAtMillis = 1_700_000_005_000L
        )

        val parsed = MatchHistoryBackup.parse(json).getOrThrow()
        assertEquals(1, parsed.decks.size)
        assertEquals("Atraxa", parsed.decks.single().name)
        assertEquals(listOf("W", "U", "B", "G"), parsed.decks.single().colorIdentity)
        assertEquals(3, parsed.opponents.size)
        assertEquals(1, parsed.matches.size)

        val m = parsed.matches.single()
        assertEquals("TEAM", m.winnerType)
        assertEquals("WIN", m.outcome)
        assertEquals(listOf("o1"), m.winningOpponentIds)
        assertEquals(listOf("Marcus"), m.winningOpponentNames)
        assertEquals(9, m.turnCount)
        assertEquals("2v2 win", m.notes)
    }

    @Test
    fun parse_invalidJson_returnsFailure() {
        val result = MatchHistoryBackup.parse("not json")
        assertTrue(result.isFailure)
    }
}
