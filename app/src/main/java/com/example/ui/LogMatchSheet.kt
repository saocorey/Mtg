package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.EloEngine
import com.example.model.Deck
import com.example.model.Opponent
import com.example.ui.theme.GildedGold
import com.example.ui.theme.OpponentAvatarPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogMatchSheet(
    decks: List<Deck>,
    opponents: List<Opponent>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onQuickAddOpponent: (name: String, favCmd: String?, colorHex: String, onCreated: (String?) -> Unit) -> Unit,
    onOpenNewDeck: () -> Unit,
    onSaveMatch: (
        deck: Deck,
        selectedOpponents: List<Opponent>,
        opponentCommanders: List<String>,
        userWon: Boolean,
        winningOpponents: List<Opponent>,
        isDraw: Boolean,
        turnCount: Int?,
        notes: String?,
        playedAtMillis: Long
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeDecks = remember(decks) { decks.filter { !it.archived } }

    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOpponentIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val opponentCommanderMap = remember { mutableStateMapOf<String, String>() }
    var userWon by rememberSaveable { mutableStateOf(false) }
    var winningOpponentIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var isDraw by rememberSaveable { mutableStateOf(false) }
    var turnCountText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    var showQuickAddOpponent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(decks) {
        if (selectedDeckId != null && decks.none { it.id == selectedDeckId }) {
            selectedDeckId = null
        }
    }

    if (showQuickAddOpponent) {
        var quickName by rememberSaveable { mutableStateOf("") }
        var quickCmd by rememberSaveable { mutableStateOf("") }
        val nextColor = OpponentAvatarPalette[opponents.size % OpponentAvatarPalette.size]
        AlertDialog(
            onDismissRequest = { showQuickAddOpponent = false },
            title = { Text("Quick Add Opponent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quickName,
                        onValueChange = { if (it.length <= 50) quickName = it },
                        label = { Text("Player Name *") },
                        placeholder = { Text("e.g., Elena") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_opponent_name_input")
                    )
                    OutlinedTextField(
                        value = quickCmd,
                        onValueChange = { if (it.length <= 80) quickCmd = it },
                        label = { Text("Commander (Optional)") },
                        placeholder = { Text("e.g., Krenko, Mob Boss") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_opponent_cmd_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (quickName.isNotBlank()) {
                            onQuickAddOpponent(
                                quickName.trim(),
                                quickCmd.trim().ifEmpty { null },
                                nextColor
                            ) { createdId ->
                                if (createdId != null && selectedOpponentIds.size < 5) {
                                    selectedOpponentIds = selectedOpponentIds + createdId
                                    if (quickCmd.isNotBlank()) {
                                        opponentCommanderMap[createdId] = quickCmd.trim()
                                    }
                                }
                                showQuickAddOpponent = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("quick_opponent_save_button")
                ) {
                    Text("Add to Pod")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddOpponent = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
    val selectedOpponents = selectedOpponentIds.mapNotNull { id -> opponents.firstOrNull { it.id == id } }
    val winningOpponents = selectedOpponents.filter { it.id in winningOpponentIds }
    val winningOpponentIdSet = winningOpponents.map { it.id }.toSet()
    val totalWinners = (if (userWon) 1 else 0) + winningOpponentIdSet.size
    val totalPlayers = 1 + selectedOpponents.size
    val hasValidOutcome = isDraw || (selectedOpponents.isNotEmpty() && totalWinners in 1 until totalPlayers)

    val eloPreview = remember(selectedDeck, selectedOpponents, userWon, winningOpponentIdSet, isDraw, hasValidOutcome) {
        if (selectedDeck != null && selectedOpponents.isNotEmpty() && hasValidOutcome) {
            EloEngine.previewMatchElo(
                deckElo = selectedDeck.elo.toInt(),
                opponents = selectedOpponents,
                userWon = userWon,
                winningOpponentIds = winningOpponentIdSet,
                isDraw = isDraw
            )
        } else null
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Log Commander Pod",
                style = MaterialTheme.typography.headlineSmall
            )

            // 1. Select Your Deck
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Your Deck", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onOpenNewDeck) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Deck")
                    }
                }
                if (decks.isEmpty()) {
                    Text(
                        text = "Add a Commander deck first before logging a match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (activeDecks.ifEmpty { decks }).forEach { deck ->
                            val isSelected = deck.id == selectedDeckId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedDeckId = deck.id
                                    validationError = null
                                },
                                leadingIcon = {
                                    ManaIdentityRow(colorCodes = deck.colorIdentity, pipSize = 14.dp)
                                },
                                label = {
                                    Text(
                                        text = "${deck.name} (${deck.elo})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("match_pick_deck_${deck.id}")
                            )
                        }
                    }
                }
            }

            // 2. Select Opponents (1-5)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Opponents in Pod (${selectedOpponents.size}/5)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(
                        onClick = { showQuickAddOpponent = true },
                        modifier = Modifier.testTag("match_quick_add_opponent_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Player")
                    }
                }

                if (opponents.isEmpty()) {
                    OutlinedButton(
                        onClick = { showQuickAddOpponent = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Your First Opponent")
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        opponents.forEach { opp ->
                            val selected = opp.id in selectedOpponentIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    validationError = null
                                    if (selected) {
                                        selectedOpponentIds = selectedOpponentIds - opp.id
                                        winningOpponentIds = winningOpponentIds - opp.id
                                    } else if (selectedOpponentIds.size < 5) {
                                        selectedOpponentIds = selectedOpponentIds + opp.id
                                        if (opponentCommanderMap[opp.id].isNullOrBlank() && !opp.favoriteCommander.isNullOrBlank()) {
                                            opponentCommanderMap[opp.id] = opp.favoriteCommander
                                        }
                                    }
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text("${opp.name} (${opp.elo})") },
                                modifier = Modifier.testTag("match_pick_opponent_${opp.id}")
                            )
                        }
                    }
                }

                if (selectedOpponents.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedOpponents.forEach { opp ->
                            OutlinedTextField(
                                value = opponentCommanderMap[opp.id] ?: "",
                                onValueChange = {
                                    if (it.length <= 80) opponentCommanderMap[opp.id] = it
                                },
                                label = { Text("${opp.name}'s Commander (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 3. Who Won the Pod? (Supports solo or multi-winner team games)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Pod Outcome", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Tap 1 winner, or select multiple players for team games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WinnerOptionChip(
                        label = selectedDeck?.let { "You (${it.name})" } ?: "You Won",
                        selected = !isDraw && userWon,
                        isCrown = true,
                        onClick = {
                            if (isDraw) {
                                isDraw = false
                                userWon = true
                            } else {
                                userWon = !userWon
                            }
                            validationError = null
                        },
                        testTag = "winner_option_user"
                    )
                    selectedOpponents.forEach { opp ->
                        val oppSelected = !isDraw && opp.id in winningOpponentIdSet
                        WinnerOptionChip(
                            label = "${opp.name} Won",
                            selected = oppSelected,
                            isCrown = true,
                            onClick = {
                                isDraw = false
                                winningOpponentIds = if (opp.id in winningOpponentIds) {
                                    winningOpponentIds - opp.id
                                } else {
                                    winningOpponentIds + opp.id
                                }
                                validationError = null
                            },
                            testTag = "winner_option_opp_${opp.id}"
                        )
                    }
                    WinnerOptionChip(
                        label = "Pod Draw",
                        selected = isDraw,
                        isCrown = false,
                        onClick = {
                            isDraw = true
                            userWon = false
                            winningOpponentIds = emptyList()
                            validationError = null
                        },
                        testTag = "winner_option_draw"
                    )
                }

                val winnerNames = buildList {
                    if (userWon) add("You")
                    winningOpponents.forEach { add(it.name) }
                }
                val allSelectedAsWinners = !isDraw && selectedOpponents.isNotEmpty() && winnerNames.size >= totalPlayers
                val outcomeHintText = when {
                    isDraw -> "Pod Draw — all players tie"
                    winnerNames.isEmpty() -> "Select winner(s) or Pod Draw"
                    allSelectedAsWinners -> "All players selected — tap Pod Draw instead, or deselect one player"
                    winnerNames.size > 1 -> "Team Victory • ${winnerNames.joinToString(" + ")}"
                    else -> "Solo Winner • ${winnerNames.first()}"
                }
                val outcomeHintColor = when {
                    allSelectedAsWinners -> MaterialTheme.colorScheme.error
                    !isDraw && winnerNames.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                    !isDraw && winnerNames.size > 1 -> GildedGold
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    text = outcomeHintText,
                    style = MaterialTheme.typography.labelMedium,
                    color = outcomeHintColor,
                    modifier = Modifier.testTag("match_outcome_summary")
                )
            }

            // 4. Live Elo Impact Preview
            if (eloPreview != null && selectedDeck != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LIVE ELO PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDeck.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${eloPreview.deckEloBefore} -> ${eloPreview.deckEloAfter}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                EloDeltaPill(delta = eloPreview.deckEloDelta)
                            }
                        }
                        eloPreview.opponentPreviews.forEach { oppPrev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = oppPrev.opponentName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${oppPrev.eloBefore} -> ${oppPrev.eloAfter}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    EloDeltaPill(delta = oppPrev.delta)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Optional Turn Count & Notes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = turnCountText,
                    onValueChange = { input ->
                        if (input.length <= 2 && input.all { it.isDigit() }) {
                            turnCountText = input
                        }
                    },
                    label = { Text("Turn (Opt)") },
                    placeholder = { Text("8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(110.dp)
                        .testTag("match_turn_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 300) notes = it },
                    label = { Text("Match Notes (Optional)") },
                    placeholder = { Text("Wincon, highlight play...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("match_notes_input")
                )
            }

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
                        .testTag("match_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val totalWinners = (if (userWon) 1 else 0) + winningOpponents.size
                        val podSize = 1 + selectedOpponents.size
                        when {
                            selectedDeck == null -> validationError = "Select one of your decks."
                            selectedOpponents.isEmpty() -> validationError = "Select at least 1 opponent in the pod."
                            !isDraw && totalWinners == 0 -> {
                                validationError = "Select at least 1 winner or Pod Draw."
                            }
                            !isDraw && totalWinners >= podSize -> {
                                validationError = "Choose Pod Draw if everyone won, or leave at least one non-winner."
                            }
                            else -> {
                                val parsedTurn = turnCountText.toIntOrNull()?.takeIf { it in 1..99 }
                                val commanders = selectedOpponents.map { opponentCommanderMap[it.id]?.trim() ?: "" }
                                onSaveMatch(
                                    selectedDeck,
                                    selectedOpponents,
                                    commanders,
                                    userWon,
                                    winningOpponents,
                                    isDraw,
                                    parsedTurn,
                                    notes.trim().ifEmpty { null },
                                    System.currentTimeMillis()
                                )
                            }
                        }
                    },
                    enabled = !isSaving && decks.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("match_save_button")
                ) {
                    Text(if (isSaving) "Recording..." else "Save Match")
                }
            }
        }
    }
}

@Composable
private fun WinnerOptionChip(
    label: String,
    selected: Boolean,
    isCrown: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) GildedGold else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isCrown) Icons.Filled.EmojiEvents else Icons.Filled.Handshake,
                contentDescription = null,
                tint = if (selected) GildedGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
