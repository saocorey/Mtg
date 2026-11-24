package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.LedgerBackupBundle
import com.example.domain.MatchHistoryBackup
import com.example.model.MatchOutcome
import com.example.model.MatchRecord
import com.example.model.WinnerType
import com.example.ui.theme.DefeatCrimson
import com.example.ui.theme.DefeatCrimsonSoft
import com.example.ui.theme.DrawAmber
import com.example.ui.theme.DrawAmberSoft
import com.example.ui.theme.GildedGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VictoryEmerald
import com.example.ui.theme.VictoryEmeraldSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MatchOutcomeFilter(val label: String) {
    ALL("All"),
    WIN("Wins"),
    LOSS("Losses"),
    DRAW("Draws")
}

@Composable
fun MatchesScreen(
    data: LedgerData,
    isBusy: Boolean,
    onDeleteMatch: (String) -> Unit,
    onOpenLogMatch: () -> Unit,
    onImportBackup: (LedgerBackupBundle) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outcomeFilter by rememberSaveable { mutableStateOf(MatchOutcomeFilter.ALL) }
    var selectedDeckFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteMatch by remember { mutableStateOf<MatchRecord?>(null) }
    var pendingImportBundle by remember { mutableStateOf<LedgerBackupBundle?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val writeResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = MatchHistoryBackup.serialize(
                            decks = data.decks,
                            opponents = data.opponents,
                            matches = data.matches
                        )
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                            stream.flush()
                        } ?: error("Unable to open destination file for writing.")
                    }
                }
                writeResult.fold(
                    onSuccess = {
                        onShowMessage("Exported ${data.matches.size} match${if (data.matches.size == 1) "" else "es"} to backup file")
                    },
                    onFailure = { err ->
                        onShowMessage(err.localizedMessage ?: "Failed to export match history")
                    }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val parseResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val maxBytes = 5 * 1024 * 1024
                            val bytes = input.readBytes()
                            require(bytes.size <= maxBytes) { "Backup file is too large (max 5 MB)." }
                            bytes
                        } ?: error("Unable to read selected file.")
                        val text = rawBytes.toString(Charsets.UTF_8)
                        MatchHistoryBackup.parse(text).getOrThrow()
                    }
                }
                parseResult.fold(
                    onSuccess = { bundle -> pendingImportBundle = bundle },
                    onFailure = { err ->
                        onShowMessage(err.localizedMessage ?: "Invalid match history backup file")
                    }
                )
            }
        }
    }

    if (pendingDeleteMatch != null) {
        val target = pendingDeleteMatch!!
        AlertDialog(
            onDismissRequest = { pendingDeleteMatch = null },
            title = { Text("Delete Match Log?") },
            text = {
                Text("Removing this match will automatically recalculate Elo ratings and win/loss records for all decks and opponents.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMatch(target.id)
                        pendingDeleteMatch = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_match_button")
                ) {
                    Text("Delete & Recalculate")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMatch = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingImportBundle != null) {
        val bundle = pendingImportBundle!!
        AlertDialog(
            onDismissRequest = { pendingImportBundle = null },
            title = { Text("Import Match History?") },
            text = {
                Text(
                    "Found ${bundle.matches.size} match${if (bundle.matches.size == 1) "" else "es"}, " +
                        "${bundle.decks.size} deck${if (bundle.decks.size == 1) "" else "s"}, and " +
                        "${bundle.opponents.size} opponent${if (bundle.opponents.size == 1) "" else "s"} in this backup.\n\n" +
                        "Missing decks and opponents will be added automatically, duplicate matches will be skipped, and Elo ratings will be recalculated."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportBundle = null
                        onImportBackup(bundle)
                    },
                    modifier = Modifier.testTag("confirm_import_backup_button")
                ) {
                    Text("Import & Recalculate")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportBundle = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filteredMatches = data.matches.filter { match ->
        val matchesOutcome = when (outcomeFilter) {
            MatchOutcomeFilter.ALL -> true
            MatchOutcomeFilter.WIN -> match.matchOutcome == MatchOutcome.WIN
            MatchOutcomeFilter.LOSS -> match.matchOutcome == MatchOutcome.LOSS
            MatchOutcomeFilter.DRAW -> match.matchOutcome == MatchOutcome.DRAW
        }
        val matchesDeck = selectedDeckFilterId == null || match.deckId == selectedDeckFilterId
        matchesOutcome && matchesDeck
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = "Match History",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${data.matches.size} Commander pods logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            exportLauncher.launch("command-ledger-backup-$dateStamp.json")
                        },
                        enabled = !isBusy && (data.matches.isNotEmpty() || data.decks.isNotEmpty() || data.opponents.isNotEmpty()),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_matches_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = "Export match history",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export History", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("import_matches_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Import match history",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import History", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MatchOutcomeFilter.entries.forEach { f ->
                    FilterChip(
                        selected = outcomeFilter == f,
                        onClick = { outcomeFilter = f },
                        label = { Text(f.label) },
                        modifier = Modifier.testTag("match_filter_${f.name.lowercase()}")
                    )
                }
                if (data.decks.isNotEmpty()) {
                    FilterChip(
                        selected = selectedDeckFilterId == null,
                        onClick = { selectedDeckFilterId = null },
                        label = { Text("All Decks") }
                    )
                    data.decks.forEach { deck ->
                        FilterChip(
                            selected = selectedDeckFilterId == deck.id,
                            onClick = {
                                selectedDeckFilterId = if (selectedDeckFilterId == deck.id) null else deck.id
                            },
                            label = { Text(deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }

        if (filteredMatches.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (data.matches.isEmpty()) "No Matches Recorded" else "No Matches Match Filters",
                    subtitle = if (data.matches.isEmpty()) {
                        "Tap Log Match to record your first Commander pod, track opponent wins, and start updating Elo rankings."
                    } else {
                        "Try clearing the deck or outcome filter to view more logged pods."
                    },
                    actionLabel = if (data.matches.isEmpty()) "Log First Match" else null,
                    onActionClick = if (data.matches.isEmpty()) onOpenLogMatch else null,
                    testTag = "matches_empty_log_button"
                )
            }
        } else {
            items(filteredMatches, key = { it.id }) { match ->
                val recomputed = data.recomputed.matchUpdates[match.id]
                MatchHistoryCard(
                    match = match,
                    deckEloBefore = recomputed?.deckEloBefore ?: match.deckEloBefore.toInt(),
                    deckEloAfter = recomputed?.deckEloAfter ?: match.deckEloAfter.toInt(),
                    deckEloDelta = recomputed?.deckEloDelta ?: match.deckEloDelta.toInt(),
                    opponentDeltas = recomputed?.opponentEloDeltas ?: match.opponentEloDeltas,
                    onDeleteClick = { pendingDeleteMatch = match }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchHistoryCard(
    match: MatchRecord,
    deckEloBefore: Int,
    deckEloAfter: Int,
    deckEloDelta: Int,
    opponentDeltas: Map<String, Long>,
    onDeleteClick: () -> Unit
) {
    val isTeamGame = match.matchWinnerType == WinnerType.TEAM
    val (outcomeLabel, badgeBg, badgeFg) = when (match.matchOutcome) {
        MatchOutcome.WIN -> Triple(
            if (isTeamGame) "TEAM WIN" else "VICTORY",
            VictoryEmeraldSoft,
            VictoryEmerald
        )
        MatchOutcome.LOSS -> Triple(
            if (isTeamGame) "TEAM LOSS" else "DEFEAT",
            DefeatCrimsonSoft,
            DefeatCrimson
        )
        MatchOutcome.DRAW -> Triple("DRAW", DrawAmberSoft, DrawAmber)
    }
    val winningOppIds = remember(match) { match.resolvedWinningOpponentIds().toSet() }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val dateLabel = remember(match.playedAtMillis) {
        dateFormatter.format(Date(match.playedAtMillis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = outcomeLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${match.podSize}-Player Pod" + (match.turnCount?.let { " • T$it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_match_${match.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete match",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ManaIdentityRow(colorCodes = match.deckColorIdentity, pipSize = 18.dp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (match.matchOutcome == MatchOutcome.WIN && isTeamGame) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Your team won",
                                    tint = GildedGold,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = match.deckName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = match.deckCommander,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    EloDeltaPill(delta = deckEloDelta)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$deckEloBefore -> $deckEloAfter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                match.opponentIds.forEachIndexed { idx, oppId ->
                    val oppName = match.opponentNames.getOrNull(idx) ?: "Opponent"
                    val oppCmd = match.opponentCommanders.getOrNull(idx)?.takeIf { it.isNotBlank() }
                    val oppDelta = (opponentDeltas[oppId] ?: 0L).toInt()
                    val isWinner = match.matchWinnerType != WinnerType.DRAW && oppId in winningOppIds
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isWinner) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        border = if (isWinner) BorderStroke(1.dp, GildedGold.copy(alpha = 0.6f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isWinner) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Pod winner",
                                    tint = GildedGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (oppCmd != null) "$oppName ($oppCmd)" else oppName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (oppDelta != 0) {
                                val deltaText = if (oppDelta > 0) "+$oppDelta" else "$oppDelta"
                                Text(
                                    text = deltaText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (oppDelta > 0) VictoryEmerald else DefeatCrimson
                                )
                            }
                        }
                    }
                }
            }

            if (!match.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = match.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}
