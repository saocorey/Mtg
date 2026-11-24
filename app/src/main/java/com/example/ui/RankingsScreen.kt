package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Deck
import com.example.model.Opponent
import com.example.ui.theme.DefeatCrimson
import com.example.ui.theme.GildedGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMagentaContainer
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OnNeonMagentaContainer
import com.example.ui.theme.StatNumberStyle
import com.example.ui.theme.SynthSurface
import com.example.ui.theme.VictoryEmerald

private enum class LeaderboardMode(val label: String) {
    DECKS("Decks"),
    OPPONENTS("Opponents"),
    COMBINED("Pod Power")
}

private sealed interface RankedEntry {
    val id: String
    val name: String
    val subtitle: String
    val elo: Int
    val delta: Int
    val gamesPlayed: Int

    data class DeckEntry(val deck: Deck) : RankedEntry {
        override val id: String = "deck_${deck.id}"
        override val name: String = deck.name
        override val subtitle: String = deck.commanderDisplay
        override val elo: Int = deck.elo.toInt()
        override val delta: Int = deck.lastEloDelta.toInt()
        override val gamesPlayed: Int = deck.gamesPlayed.toInt()
    }

    data class OpponentEntry(val opponent: Opponent) : RankedEntry {
        override val id: String = "opp_${opponent.id}"
        override val name: String = opponent.name
        override val subtitle: String = opponent.favoriteCommander ?: "Playgroup Opponent"
        override val elo: Int = opponent.elo.toInt()
        override val delta: Int = opponent.lastEloDelta.toInt()
        override val gamesPlayed: Int = opponent.gamesPlayed.toInt()
    }
}

@Composable
fun RankingsScreen(
    data: LedgerData,
    onDeckClick: (Deck) -> Unit,
    onOpponentClick: (Opponent) -> Unit,
    onOpenLogMatch: () -> Unit,
    onOpenAddDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(LeaderboardMode.DECKS) }
    var showEloDialog by rememberSaveable { mutableStateOf(false) }

    if (showEloDialog) {
        EloFormulaInfoDialog(onDismiss = { showEloDialog = false })
    }

    val activeDecks = data.decks.filter { !it.archived }
    val entries: List<RankedEntry> = when (mode) {
        LeaderboardMode.DECKS -> activeDecks.map { RankedEntry.DeckEntry(it) }
        LeaderboardMode.OPPONENTS -> data.opponents.map { RankedEntry.OpponentEntry(it) }
        LeaderboardMode.COMBINED -> (
            activeDecks.map { RankedEntry.DeckEntry(it) } +
                data.opponents.map { RankedEntry.OpponentEntry(it) }
            ).sortedByDescending { it.elo }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OverviewMetricsGrid(data = data)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Elo Rankings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = { showEloDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("elo_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "How Commander Elo is calculated",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                LeaderboardMode.entries.forEachIndexed { index, itemMode ->
                    SegmentedButton(
                        selected = mode == itemMode,
                        onClick = { mode = itemMode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = LeaderboardMode.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = NeonMagentaContainer,
                            activeContentColor = OnNeonMagentaContainer,
                            inactiveContainerColor = SynthSurface,
                            inactiveContentColor = NeonPurple
                        ),
                        modifier = Modifier.testTag("ranking_mode_${itemMode.name.lowercase()}")
                    ) {
                        Text(
                            text = itemMode.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                val emptyTitle = when (mode) {
                    LeaderboardMode.DECKS -> "Build Your Armory"
                    LeaderboardMode.OPPONENTS -> "Add Your Playgroup"
                    LeaderboardMode.COMBINED -> "No Rankings Yet"
                }
                val emptySubtitle = when (mode) {
                    LeaderboardMode.DECKS -> "Add your first Commander deck to start tracking win rates and 1200-base Elo."
                    LeaderboardMode.OPPONENTS -> "Add opponents in the Opponents tab or directly while logging a match."
                    LeaderboardMode.COMBINED -> "Add decks and opponents to see combined table Elo standings."
                }
                EmptyStateCard(
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    actionLabel = if (mode == LeaderboardMode.DECKS) "Add First Deck" else "Log a Match",
                    onActionClick = if (mode == LeaderboardMode.DECKS) onOpenAddDeck else onOpenLogMatch,
                    testTag = "rankings_empty_cta"
                )
            }
        } else {
            itemsIndexed(entries, key = { _, item -> item.id }) { index, entry ->
                RankedEntryCard(
                    rank = index + 1,
                    entry = entry,
                    onClick = {
                        when (entry) {
                            is RankedEntry.DeckEntry -> onDeckClick(entry.deck)
                            is RankedEntry.OpponentEntry -> onOpponentClick(entry.opponent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricsGrid(data: LedgerData) {
    val overview = data.overview
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "MY WIN RATE",
                value = "${overview.overallWinRatePercent}%",
                caption = "${overview.totalWins}W in ${overview.totalMatches} pods",
                accentColor = if (overview.overallWinRatePercent >= 25) VictoryEmerald else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "ACTIVE ROSTER",
                value = "${overview.activeDeckCount} / ${overview.opponentCount}",
                caption = "Decks • Opponents",
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val topDeck = overview.highestEloDeck
            MetricTile(
                label = "TOP DECK ELO",
                value = topDeck?.elo?.toString() ?: "—",
                caption = topDeck?.name ?: "No decks yet",
                accentColor = GildedGold,
                modifier = Modifier.weight(1f)
            )
            val nemesis = overview.topNemesis
            MetricTile(
                label = "TOP NEMESIS",
                value = nemesis?.let { "${it.winRateAgainstUserPercent}%" } ?: "—",
                caption = nemesis?.let { "${it.name} (${it.winsAgainstUser}-${it.lossesToUser})" } ?: "No pods yet",
                accentColor = DefeatCrimson,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    caption: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = StatNumberStyle,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RankedEntryCard(
    rank: Int,
    entry: RankedEntry,
    onClick: () -> Unit
) {
    val isLeader = rank == 1 && entry.gamesPlayed > 0
    val borderStroke = if (isLeader) {
        BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))
        )
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("ranking_card_${entry.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLeader) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.width(34.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLeader) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Rank 1 leader",
                        tint = GildedGold,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        text = "#$rank",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (entry) {
                is RankedEntry.DeckEntry -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ManaIdentityRow(colorCodes = entry.deck.colorIdentity, pipSize = 16.dp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "DECK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonPurple,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.deck.winRatePercent}% WR • ${entry.deck.wins}-${entry.deck.losses}-${entry.deck.draws}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is RankedEntry.OpponentEntry -> {
                    AvatarCircle(
                        name = entry.opponent.name,
                        hexColor = entry.opponent.avatarColorHex,
                        size = 40.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonPurple,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "OPP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${entry.opponent.winRateAgainstUserPercent}% vs You (${entry.opponent.winsAgainstUser}-${entry.opponent.lossesToUser}) • ${entry.opponent.overallWinRatePercent}% pod WR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                EloBadge(
                    elo = entry.elo,
                    delta = entry.delta
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${entry.gamesPlayed}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
