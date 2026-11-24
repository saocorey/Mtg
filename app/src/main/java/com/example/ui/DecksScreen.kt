package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.EloEngine
import com.example.model.Deck
import com.example.model.ManaColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VictoryEmerald

private enum class DeckFilter(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ALL("All")
}

private enum class DeckSort(val label: String) {
    ELO("Elo"),
    WIN_RATE("Win %"),
    GAMES("Games")
}

@Composable
fun DecksScreen(
    data: LedgerData,
    onDeckClick: (Deck) -> Unit,
    onAddDeckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf(DeckFilter.ACTIVE) }
    var sort by rememberSaveable { mutableStateOf(DeckSort.ELO) }

    val filteredDecks = data.decks
        .filter { deck ->
            when (filter) {
                DeckFilter.ACTIVE -> !deck.archived
                DeckFilter.ARCHIVED -> deck.archived
                DeckFilter.ALL -> true
            }
        }
        .let { list ->
            when (sort) {
                DeckSort.ELO -> list.sortedByDescending { it.elo }
                DeckSort.WIN_RATE -> list.sortedWith(
                    compareByDescending<Deck> { it.winRatePercent }.thenByDescending { it.gamesPlayed }
                )
                DeckSort.GAMES -> list.sortedByDescending { it.gamesPlayed }
            }
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
                        text = "Your Decks",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${data.decks.count { !it.archived }} active • ${data.decks.count { it.archived }} archived",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddDeckClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_deck_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add deck",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Deck")
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        modifier = Modifier.testTag("deck_filter_${f.name.lowercase()}")
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                DeckSort.entries.forEach { s ->
                    FilterChip(
                        selected = sort == s,
                        onClick = { sort = s },
                        label = { Text("Sort: ${s.label}") },
                        modifier = Modifier.testTag("deck_sort_${s.name.lowercase()}")
                    )
                }
            }
        }

        if (filteredDecks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (filter == DeckFilter.ARCHIVED) "No Archived Decks" else "No Commander Decks Yet",
                    subtitle = if (filter == DeckFilter.ARCHIVED) {
                        "Archived decks appear here when you retire them from rotation."
                    } else {
                        "Register your Commander deck with its color identity and power bracket to track win rates and Elo."
                    },
                    actionLabel = if (filter == DeckFilter.ARCHIVED) null else "Create Deck",
                    onActionClick = if (filter == DeckFilter.ARCHIVED) null else onAddDeckClick,
                    testTag = "decks_empty_add_button"
                )
            }
        } else {
            items(filteredDecks, key = { it.id }) { deck ->
                val computed = data.recomputed.deckStats[deck.id] ?: EloEngine.ComputedDeckStats()
                DeckCard(
                    deck = deck,
                    recentForm = computed.recentForm,
                    onClick = { onDeckClick(deck) }
                )
            }
        }
    }
}

@Composable
private fun DeckCard(
    deck: Deck,
    recentForm: List<com.example.model.MatchOutcome>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("deck_card_${deck.id}"),
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
                    ManaIdentityRow(colorCodes = deck.colorIdentity, pipSize = 20.dp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Bracket ${deck.bracket}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (deck.archived) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Archived deck",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                EloBadge(
                    elo = deck.elo.toInt(),
                    delta = deck.lastEloDelta.toInt()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = deck.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = deck.commanderDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            WinLossDrawBar(
                wins = deck.wins.toInt(),
                losses = deck.losses.toInt(),
                draws = deck.draws.toInt()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${deck.winRatePercent}% WR",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (deck.winRatePercent >= 25 && deck.gamesPlayed > 0) VictoryEmerald else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${deck.wins}W - ${deck.losses}L - ${deck.draws}D",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RecentFormStrip(outcomes = recentForm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditorSheet(
    existingDeck: Deck?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        commander: String,
        partner: String?,
        colors: List<String>,
        bracket: Int,
        notes: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(existingDeck?.name ?: "") }
    var commander by rememberSaveable { mutableStateOf(existingDeck?.commander ?: "") }
    var partner by rememberSaveable { mutableStateOf(existingDeck?.partnerCommander ?: "") }
    var colors by rememberSaveable { mutableStateOf(existingDeck?.colorIdentity ?: emptyList()) }
    var bracket by rememberSaveable { mutableIntStateOf(existingDeck?.bracket?.toInt() ?: 3) }
    var notes by rememberSaveable { mutableStateOf(existingDeck?.notes ?: "") }
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
                text = if (existingDeck == null) "New Commander Deck" else "Edit Deck",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 60) name = it
                    validationError = null
                },
                label = { Text("Deck Name *") },
                placeholder = { Text("e.g., Atraxa Superfriends") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_name_input")
            )

            OutlinedTextField(
                value = commander,
                onValueChange = {
                    if (it.length <= 80) commander = it
                    validationError = null
                },
                label = { Text("Commander *") },
                placeholder = { Text("e.g., Atraxa, Praetors' Voice") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_commander_input")
            )

            OutlinedTextField(
                value = partner,
                onValueChange = { if (it.length <= 80) partner = it },
                label = { Text("Partner / Background (Optional)") },
                placeholder = { Text("e.g., Tymna the Weaver") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_partner_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Color Identity (${if (colors.isEmpty()) "None selected" else ManaColor.identityName(colors)})",
                    style = MaterialTheme.typography.titleSmall
                )
                ManaColorSelector(
                    selectedCodes = colors,
                    onToggleColor = { code ->
                        colors = if (code == "C") {
                            if ("C" in colors) emptyList() else listOf("C")
                        } else {
                            val withoutColorless = colors.filter { it != "C" }
                            if (code in withoutColorless) withoutColorless - code else withoutColorless + code
                        }
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Commander Bracket ($bracket)",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = bracket == level,
                            onClick = { bracket = level },
                            label = { Text("B$level") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("deck_bracket_$level")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 300) notes = it },
                label = { Text("Deck Notes (Optional)") },
                placeholder = { Text("Gameplan, combos, power level notes...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_notes_input")
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
                        .testTag("deck_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        when {
                            name.isBlank() -> validationError = "Please enter a deck name."
                            commander.isBlank() -> validationError = "Please enter your commander's name."
                            else -> onSave(
                                name.trim(),
                                commander.trim(),
                                partner.trim().ifEmpty { null },
                                colors,
                                bracket,
                                notes.trim().ifEmpty { null }
                            )
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("deck_save_button")
                ) {
                    Text(if (isSaving) "Saving..." else if (existingDeck == null) "Save Deck" else "Update")
                }
            }
        }
    }
}
