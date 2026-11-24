package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Opponent
import com.example.ui.theme.DefeatCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OpponentAvatarPalette
import com.example.ui.theme.VictoryEmerald

private enum class OpponentSort(val label: String) {
    ELO("Elo"),
    WIN_RATE_VS_YOU("Win % vs You"),
    GAMES("Games")
}

@Composable
fun OpponentsScreen(
    data: LedgerData,
    onOpponentClick: (Opponent) -> Unit,
    onAddOpponentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sort by rememberSaveable { mutableStateOf(OpponentSort.ELO) }

    val sortedOpponents = when (sort) {
        OpponentSort.ELO -> data.opponents.sortedByDescending { it.elo }
        OpponentSort.WIN_RATE_VS_YOU -> data.opponents.sortedWith(
            compareByDescending<Opponent> { it.winRateAgainstUserPercent }.thenByDescending { it.gamesPlayed }
        )
        OpponentSort.GAMES -> data.opponents.sortedByDescending { it.gamesPlayed }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playgroup Opponents",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${data.opponents.size} opponents tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddOpponentClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_opponent_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add opponent",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Player")
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
                OpponentSort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { sort = option },
                        label = { Text("Sort: ${option.label}") },
                        modifier = Modifier.testTag("opponent_sort_${option.name.lowercase()}")
                    )
                }
            }
        }

        if (sortedOpponents.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Add Your Commander Pod",
                    subtitle = "Save your regular opponents by name to track their Elo rankings and head-to-head win rates against you.",
                    actionLabel = "Add Opponent",
                    onActionClick = onAddOpponentClick,
                    testTag = "opponents_empty_add_button"
                )
            }
        } else {
            items(sortedOpponents, key = { it.id }) { opponent ->
                OpponentCard(
                    opponent = opponent,
                    onClick = { onOpponentClick(opponent) }
                )
            }
        }
    }
}

@Composable
private fun OpponentCard(
    opponent: Opponent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("opponent_card_${opponent.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarCircle(
                    name = opponent.name,
                    hexColor = opponent.avatarColorHex,
                    size = 46.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = opponent.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = opponent.favoriteCommander ?: "No signature commander set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                EloBadge(
                    elo = opponent.elo.toInt(),
                    delta = opponent.lastEloDelta.toInt()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    label = "THEIR WR VS YOU",
                    value = "${opponent.winRateAgainstUserPercent}%",
                    subtext = "${opponent.winsAgainstUser}W - ${opponent.lossesToUser}L h2h",
                    accentColor = if (opponent.winRateAgainstUserPercent > 50) DefeatCrimson else VictoryEmerald,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "OVERALL POD WR",
                    value = "${opponent.overallWinRatePercent}%",
                    subtext = "${opponent.wins} wins / ${opponent.gamesPlayed} pods",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentEditorSheet(
    existingOpponent: Opponent?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        favoriteCommander: String?,
        avatarColorHex: String,
        notes: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(existingOpponent?.name ?: "") }
    var favoriteCommander by rememberSaveable { mutableStateOf(existingOpponent?.favoriteCommander ?: "") }
    var avatarColorHex by rememberSaveable {
        mutableStateOf(existingOpponent?.avatarColorHex ?: OpponentAvatarPalette.first())
    }
    var notes by rememberSaveable { mutableStateOf(existingOpponent?.notes ?: "") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

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
            Text(
                text = if (existingOpponent == null) "Add Opponent" else "Edit Opponent",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 50) name = it
                    validationError = null
                },
                label = { Text("Opponent Name *") },
                placeholder = { Text("e.g., Marcus") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opponent_name_input")
            )

            OutlinedTextField(
                value = favoriteCommander,
                onValueChange = { if (it.length <= 80) favoriteCommander = it },
                label = { Text("Signature Commander (Optional)") },
                placeholder = { Text("e.g., Yuriko, the Tiger's Shadow") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opponent_commander_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Badge Color",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OpponentAvatarPalette.forEach { hex ->
                        val color = parseHexColorOrDefault(hex, MaterialTheme.colorScheme.primary)
                        val selected = avatarColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.28f))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) color else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { avatarColorHex = hex }
                                .testTag("opponent_color_${hex.drop(1).lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected badge color",
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 300) notes = it },
                label = { Text("Playgroup Notes (Optional)") },
                placeholder = { Text("Playstyle, usual decks, threat assessment...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opponent_notes_input")
            )

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("opponent_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            validationError = "Please enter your opponent's name."
                        } else {
                            onSave(
                                name.trim(),
                                favoriteCommander.trim().ifEmpty { null },
                                avatarColorHex,
                                notes.trim().ifEmpty { null }
                            )
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("opponent_save_button")
                ) {
                    Text(if (isSaving) "Saving..." else if (existingOpponent == null) "Save Player" else "Update")
                }
            }
        }
    }
}
