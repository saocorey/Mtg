package com.example.domain

import com.example.model.Deck
import com.example.model.ManaColor
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.Opponent
import com.example.model.WinnerType
import com.example.ui.theme.OpponentAvatarPalette
import org.json.JSONArray
import org.json.JSONObject

data class ExportedDeck(
    val sourceId: String,
    val name: String,
    val commander: String,
    val partnerCommander: String? = null,
    val colorIdentity: List<String> = emptyList(),
    val bracket: Int = 3,
    val notes: String? = null,
    val archived: Boolean = false
)

data class ExportedOpponent(
    val sourceId: String,
    val name: String,
    val favoriteCommander: String? = null,
    val notes: String? = null,
    val avatarColorHex: String = "#00F0FF"
)

data class ExportedMatch(
    val sourceId: String,
    val deckId: String,
    val deckName: String,
    val deckCommander: String,
    val deckColorIdentity: List<String> = emptyList(),
    val opponentIds: List<String>,
    val opponentNames: List<String>,
    val opponentCommanders: List<String> = emptyList(),
    val outcome: String,
    val winnerType: String,
    val winningOpponentId: String? = null,
    val winningOpponentName: String? = null,
    val winningOpponentIds: List<String> = emptyList(),
    val winningOpponentNames: List<String> = emptyList(),
    val podSize: Int,
    val turnCount: Int? = null,
    val notes: String? = null,
    val playedAtMillis: Long
)

data class LedgerBackupBundle(
    val version: Int = CURRENT_VERSION,
    val exportedAtMillis: Long,
    val decks: List<ExportedDeck>,
    val opponents: List<ExportedOpponent>,
    val matches: List<ExportedMatch>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class ImportBackupSummary(
    val importedMatches: Int,
    val skippedDuplicates: Int,
    val createdDecks: Int,
    val createdOpponents: Int
)

object MatchHistoryBackup {

    fun serialize(
        decks: List<Deck>,
        opponents: List<Opponent>,
        matches: List<MatchRecord>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject().apply {
            put("app", "Command Ledger")
            put("version", LedgerBackupBundle.CURRENT_VERSION)
            put("exportedAtMillis", exportedAtMillis)
            put("decks", JSONArray().apply {
                decks.forEach { deck ->
                    put(
                        JSONObject().apply {
                            put("sourceId", deck.id)
                            put("name", deck.name)
                            put("commander", deck.commander)
                            if (!deck.partnerCommander.isNullOrBlank()) {
                                put("partnerCommander", deck.partnerCommander)
                            }
                            put("colorIdentity", JSONArray(deck.colorIdentity))
                            put("bracket", deck.bracket.toInt())
                            if (!deck.notes.isNullOrBlank()) {
                                put("notes", deck.notes)
                            }
                            put("archived", deck.archived)
                        }
                    )
                }
            })
            put("opponents", JSONArray().apply {
                opponents.forEach { opp ->
                    put(
                        JSONObject().apply {
                            put("sourceId", opp.id)
                            put("name", opp.name)
                            if (!opp.favoriteCommander.isNullOrBlank()) {
                                put("favoriteCommander", opp.favoriteCommander)
                            }
                            if (!opp.notes.isNullOrBlank()) {
                                put("notes", opp.notes)
                            }
                            put("avatarColorHex", opp.avatarColorHex)
                        }
                    )
                }
            })
            put("matches", JSONArray().apply {
                matches.sortedBy { it.playedAtMillis }.forEach { match ->
                    put(
                        JSONObject().apply {
                            put("sourceId", match.id)
                            put("deckId", match.deckId)
                            put("deckName", match.deckName)
                            put("deckCommander", match.deckCommander)
                            put("deckColorIdentity", JSONArray(match.deckColorIdentity))
                            put("opponentIds", JSONArray(match.opponentIds))
                            put("opponentNames", JSONArray(match.opponentNames))
                            put("opponentCommanders", JSONArray(match.opponentCommanders))
                            put("outcome", match.outcome)
                            put("winnerType", match.winnerType)
                            if (!match.winningOpponentId.isNullOrBlank()) {
                                put("winningOpponentId", match.winningOpponentId)
                            }
                            if (!match.winningOpponentName.isNullOrBlank()) {
                                put("winningOpponentName", match.winningOpponentName)
                            }
                            if (match.winningOpponentIds.isNotEmpty()) {
                                put("winningOpponentIds", JSONArray(match.winningOpponentIds))
                            }
                            if (match.winningOpponentNames.isNotEmpty()) {
                                put("winningOpponentNames", JSONArray(match.winningOpponentNames))
                            }
                            put("podSize", match.podSize.toInt())
                            if (match.turnCount != null) {
                                put("turnCount", match.turnCount.toInt())
                            }
                            if (!match.notes.isNullOrBlank()) {
                                put("notes", match.notes)
                            }
                            put("playedAtMillis", match.playedAtMillis)
                        }
                    )
                }
            })
        }
        return root.toString(2)
    }

    fun parse(jsonText: String): Result<LedgerBackupBundle> = runCatching {
        val trimmed = jsonText.trim()
        require(trimmed.isNotEmpty()) { "Selected file is empty." }
        val root = JSONObject(trimmed)
        val version = root.optInt("version", 1)
        require(version >= 1) { "Unsupported backup file version." }
        require(root.has("matches") || root.has("decks") || root.has("opponents")) {
            "File does not appear to be a Command Ledger backup."
        }

        val exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis())

        val decksArray = root.optJSONArray("decks") ?: JSONArray()
        val decks = buildList {
            for (i in 0 until decksArray.length()) {
                val obj = decksArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(60)
                val commander = obj.optString("commander", "").trim().take(80)
                if (name.isEmpty() || commander.isEmpty()) continue
                val sourceId = obj.optString("sourceId", "").trim().ifEmpty { "deck_import_$i" }
                val partner = obj.optString("partnerCommander", "").trim().take(80).ifEmpty { null }
                val colors = obj.optJSONArray("colorIdentity").toCleanColorList()
                val bracket = obj.optInt("bracket", 3).coerceIn(1, 5)
                val notes = obj.optString("notes", "").trim().take(300).ifEmpty { null }
                val archived = obj.optBoolean("archived", false)
                add(
                    ExportedDeck(
                        sourceId = sourceId,
                        name = name,
                        commander = commander,
                        partnerCommander = partner,
                        colorIdentity = colors,
                        bracket = bracket,
                        notes = notes,
                        archived = archived
                    )
                )
            }
        }

        val opponentsArray = root.optJSONArray("opponents") ?: JSONArray()
        val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")
        val opponents = buildList {
            for (i in 0 until opponentsArray.length()) {
                val obj = opponentsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(50)
                if (name.isEmpty()) continue
                val sourceId = obj.optString("sourceId", "").trim().ifEmpty { "opp_import_$i" }
                val favCmd = obj.optString("favoriteCommander", "").trim().take(80).ifEmpty { null }
                val notes = obj.optString("notes", "").trim().take(300).ifEmpty { null }
                val rawHex = obj.optString("avatarColorHex", "").trim()
                val hex = if (rawHex.matches(hexRegex)) rawHex.uppercase() else OpponentAvatarPalette[i % OpponentAvatarPalette.size]
                add(
                    ExportedOpponent(
                        sourceId = sourceId,
                        name = name,
                        favoriteCommander = favCmd,
                        notes = notes,
                        avatarColorHex = hex
                    )
                )
            }
        }

        val matchesArray = root.optJSONArray("matches") ?: JSONArray()
        val matches = buildList {
            for (i in 0 until matchesArray.length()) {
                val obj = matchesArray.optJSONObject(i) ?: continue
                val deckName = obj.optString("deckName", "").trim().take(60).ifEmpty { "Imported Deck" }
                val deckCommander = obj.optString("deckCommander", "").trim().take(80).ifEmpty { "Commander" }
                val deckId = obj.optString("deckId", "").trim().ifEmpty { "deck_ref_${deckName}_${deckCommander}" }
                val deckColors = obj.optJSONArray("deckColorIdentity").toCleanColorList()

                val rawOppIds = obj.optJSONArray("opponentIds").toStringList().take(5)
                val rawOppNames = obj.optJSONArray("opponentNames").toStringList().map { it.trim().take(50) }.take(5)
                val pairCount = maxOf(rawOppIds.size, rawOppNames.size).coerceAtMost(5)
                if (pairCount == 0) continue

                val oppIds = (0 until pairCount).map { idx ->
                    rawOppIds.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "opp_ref_${idx}_${rawOppNames.getOrNull(idx) ?: "Player"}"
                }
                val oppNames = (0 until pairCount).map { idx ->
                    rawOppNames.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Opponent ${idx + 1}"
                }
                val rawOppCmds = obj.optJSONArray("opponentCommanders").toStringList()
                val oppCmds = (0 until pairCount).map { idx ->
                    rawOppCmds.getOrNull(idx)?.trim()?.take(80) ?: ""
                }

                val outcome = MatchOutcome.fromRaw(obj.optString("outcome", "LOSS")).name
                val winnerType = WinnerType.fromRaw(obj.optString("winnerType", "OPPONENT")).name
                val winOppId = obj.optString("winningOpponentId", "").trim().ifEmpty { null }
                val winOppName = obj.optString("winningOpponentName", "").trim().take(50).ifEmpty { null }
                val winOppIds = obj.optJSONArray("winningOpponentIds").toStringList().take(5)
                val winOppNames = obj.optJSONArray("winningOpponentNames").toStringList().map { it.trim().take(50) }.take(5)
                val turnCount = if (obj.has("turnCount") && !obj.isNull("turnCount")) {
                    obj.optInt("turnCount", 0).takeIf { it in 1..99 }
                } else null
                val notes = obj.optString("notes", "").trim().take(300).ifEmpty { null }
                val playedAtMillis = obj.optLong("playedAtMillis", 0L).let {
                    if (it > 0L) it else (exportedAtMillis - ((matchesArray.length() - i) * 1000L)).coerceAtLeast(1L)
                }

                add(
                    ExportedMatch(
                        sourceId = obj.optString("sourceId", "").trim().ifEmpty { "match_import_$i" },
                        deckId = deckId,
                        deckName = deckName,
                        deckCommander = deckCommander,
                        deckColorIdentity = deckColors,
                        opponentIds = oppIds,
                        opponentNames = oppNames,
                        opponentCommanders = oppCmds,
                        outcome = outcome,
                        winnerType = winnerType,
                        winningOpponentId = winOppId,
                        winningOpponentName = winOppName,
                        winningOpponentIds = winOppIds,
                        winningOpponentNames = winOppNames,
                        podSize = (1 + pairCount).coerceIn(2, 6),
                        turnCount = turnCount,
                        notes = notes,
                        playedAtMillis = playedAtMillis
                    )
                )
            }
        }

        require(decks.isNotEmpty() || opponents.isNotEmpty() || matches.isNotEmpty()) {
            "No decks, opponents, or matches found in this backup file."
        }

        LedgerBackupBundle(
            version = version,
            exportedAtMillis = exportedAtMillis,
            decks = decks,
            opponents = opponents,
            matches = matches
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optString(i, "").trim()
                if (item.isNotEmpty() || i < length()) {
                    add(item)
                }
            }
        }
    }

    private fun JSONArray?.toCleanColorList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val code = optString(i, "").trim().uppercase()
                if (ManaColor.fromCode(code) != null && code !in this) {
                    add(code)
                }
            }
        }.take(5)
    }
}
