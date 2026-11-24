package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.EloEngine
import com.example.model.Deck
import com.example.model.HeadToHeadStat
import com.example.model.ManaColor
import com.example.model.Opponent
import com.example.ui.theme.DefeatCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VictoryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailSheet(
    deck: Deck,
    data: LedgerData,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${deck.name}?") },
            text = { Text("Existing match history snapshots remain, and you can always log new decks later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    val computed = data.recomputed.deckStats[deck.id] ?: EloEngine.ComputedDeckStats()
    val headToHead = remember(deck.id, data.matches, data.opponents) {
        EloEngine.computeDeckHeadToHead(deck.id, data.matches, data.opponents)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ManaIdentityRow(colorCodes = deck.colorIdentity, pipSize = 20.dp)
                        Text(
                            text = "${ManaColor.identityName(deck.colorIdentity)} • Bracket ${deck.bracket}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = deck.commanderDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EloBadge(elo = deck.elo.toInt(), delta = deck.lastEloDelta.toInt())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailStatBox(
                    label = "WIN RATE",
                    value = "${deck.winRatePercent}%",
                    sub = "${deck.wins}W-${deck.losses}L-${deck.draws}D",
                    modifier = Modifier.weight(1f)
                )
                DetailStatBox(
                    label = "PEAK ELO",
                    value = deck.peakElo.toString(),
                    sub = "${deck.gamesPlayed} pods",
                    modifier = Modifier.weight(1f)
                )
            }

            WinLossDrawBar(
                wins = deck.wins.toInt(),
                losses = deck.losses.toInt(),
                draws = deck.draws.toInt()
            )

            EloSparklineChart(
                points = computed.eloHistory,
                lineColor = NeonCyan
            )

            if (!deck.notes.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = deck.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Matchups vs Opponents",
                style = MaterialTheme.typography.titleMedium
            )
            if (headToHead.isEmpty()) {
                Text(
                    text = "Log a pod with this deck to see opponent-by-opponent win rates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                headToHead.forEach { stat ->
                    HeadToHeadRow(stat = stat, isDeckView = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("deck_detail_edit_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onToggleArchive,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("deck_detail_archive_button")
                ) {
                    Icon(
                        imageVector = if (deck.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (deck.archived) "Restore" else "Archive")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("deck_detail_delete_button")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete deck", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentDetailSheet(
    opponent: Opponent,
    data: LedgerData,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${opponent.name}?") },
            text = { Text("This removes ${opponent.name} from your active playgroup roster.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    val computed = data.recomputed.opponentStats[opponent.id] ?: EloEngine.ComputedOpponentStats()
    val headToHead = remember(opponent.id, data.matches, data.decks) {
        EloEngine.computeOpponentHeadToHead(opponent.id, data.matches, data.decks)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(
                    name = opponent.name,
                    hexColor = opponent.avatarColorHex,
                    size = 54.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = opponent.name,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = opponent.favoriteCommander ?: "Playgroup Member",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EloBadge(elo = opponent.elo.toInt(), delta = opponent.lastEloDelta.toInt())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailStatBox(
                    label = "VS YOU WIN %",
                    value = "${opponent.winRateAgainstUserPercent}%",
                    sub = "${opponent.winsAgainstUser}W - ${opponent.lossesToUser}L h2h",
                    modifier = Modifier.weight(1f)
                )
                DetailStatBox(
                    label = "POD WR / PEAK ELO",
                    value = "${opponent.overallWinRatePercent}%",
                    sub = "Peak ${opponent.peakElo} (${opponent.gamesPlayed}g)",
                    modifier = Modifier.weight(1f)
                )
            }

            EloSparklineChart(
                points = computed.eloHistory,
                lineColor = parseHexColorOrDefault(opponent.avatarColorHex, MaterialTheme.colorScheme.secondary)
            )

            if (!opponent.notes.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = opponent.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Record vs Your Decks",
                style = MaterialTheme.typography.titleMedium
            )
            if (headToHead.isEmpty()) {
                Text(
                    text = "No shared pods logged with ${opponent.name} yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                headToHead.forEach { stat ->
                    HeadToHeadRow(stat = stat, isDeckView = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("opponent_detail_edit_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Player")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("opponent_detail_delete_button")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete opponent", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailStatBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeadToHeadRow(
    stat: HeadToHeadStat,
    isDeckView: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDeckView && stat.avatarColorHex != null) {
                AvatarCircle(name = stat.entityName, hexColor = stat.avatarColorHex, size = 34.dp)
            } else {
                ManaIdentityRow(colorCodes = stat.colorIdentity, pipSize = 15.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.entityName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stat.gamesTogether} shared pods • ${stat.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val primaryRate = if (isDeckView) stat.yourWinRatePercent else stat.theirWinRatePercent
                val rateLabel = if (isDeckView) "You $primaryRate%" else "Them $primaryRate%"
                Text(
                    text = rateLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeckView) VictoryEmerald else DefeatCrimson
                )
                Text(
                    text = "You ${stat.yourWins} - ${stat.theirWins} Them",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
