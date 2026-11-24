package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.EloPoint
import com.example.model.ManaColor
import com.example.model.MatchOutcome
import com.example.ui.theme.DefeatCrimson
import com.example.ui.theme.DefeatCrimsonSoft
import com.example.ui.theme.DrawAmber
import com.example.ui.theme.DrawAmberSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.StatDeltaStyle
import com.example.ui.theme.SunsetTangerine
import com.example.ui.theme.SynthBg
import com.example.ui.theme.VictoryEmerald
import com.example.ui.theme.VictoryEmeraldSoft

/**
 * Retro-futuristic Vaporwave backdrop featuring a deep midnight-violet sky gradient,
 * a subtle magenta/tangerine sunset horizon glow, and a subtle perspective laser grid.
 */
@Composable
fun VaporwaveBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SynthBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.62f

            // Sky vertical synth gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0416),
                        Color(0xFF15082A),
                        Color(0xFF210B38),
                        Color(0xFF0E061B)
                    )
                )
            )

            // Soft sunset glow near the horizon
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonMagenta.copy(alpha = 0.16f),
                        SunsetTangerine.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, horizonY * 0.86f),
                    radius = w * 0.68f
                ),
                center = Offset(w * 0.5f, horizonY * 0.86f),
                radius = w * 0.68f
            )

            // Subtle ambient cyan glow at top
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.08f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.15f, h * 0.08f),
                radius = w * 0.55f
            )

            // Perspective vaporwave laser grid below horizon
            val gridColor = NeonCyan.copy(alpha = 0.085f)
            val magentaGridColor = NeonMagenta.copy(alpha = 0.07f)

            // Horizon line
            drawLine(
                color = magentaGridColor,
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 1.dp.toPx()
            )

            // Perspective horizontal receding lines
            val rows = 10
            for (i in 1..rows) {
                val t = i.toFloat() / rows.toFloat()
                val y = horizonY + (h - horizonY) * (t * t)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 0.9.dp.toPx()
                )
            }

            // Perspective vertical converging rays
            val vanish = Offset(w * 0.5f, horizonY - h * 0.06f)
            val cols = 10
            for (i in -cols..cols) {
                val bottomX = w * 0.5f + i * (w * 0.14f)
                val tStart = (horizonY - vanish.y) / (h - vanish.y)
                val startX = vanish.x + (bottomX - vanish.x) * tStart
                drawLine(
                    color = if (i % 2 == 0) gridColor else magentaGridColor,
                    start = Offset(startX, horizonY),
                    end = Offset(bottomX, h),
                    strokeWidth = 0.9.dp.toPx()
                )
            }
        }
        content()
    }
}

@Composable
fun ManaIdentityRow(
    colorCodes: List<String>,
    pipSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val ordered = ManaColor.orderedList(colorCodes).ifEmpty { listOf(ManaColor.C) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ordered.forEach { mana ->
            Box(
                modifier = Modifier
                    .size(pipSize)
                    .clip(CircleShape)
                    .background(mana.bgColor)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .semantics { contentDescription = "${mana.label} mana" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mana.code,
                    color = mana.fgColor,
                    fontSize = (pipSize.value * 0.54f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManaColorSelector(
    selectedCodes: List<String>,
    onToggleColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ManaColor.entries.forEach { mana ->
            val selected = mana.code in selectedCodes
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleColor(mana.code) }
                    .testTag("mana_toggle_${mana.code.lowercase()}"),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) mana.bgColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) mana.bgColor else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(mana.bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mana.code,
                            color = mana.fgColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                    Text(
                        text = mana.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EloBadge(
    elo: Int,
    delta: Int? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.65f),
                    NeonMagenta.copy(alpha = 0.65f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = elo.toString(),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = NeonCyan
            )
            if (delta != null && delta != 0) {
                EloDeltaPill(delta = delta)
            }
        }
    }
}

@Composable
fun EloDeltaPill(delta: Int, modifier: Modifier = Modifier) {
    val isPositive = delta > 0
    val bgColor = when {
        delta > 0 -> VictoryEmeraldSoft
        delta < 0 -> DefeatCrimsonSoft
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fgColor = when {
        delta > 0 -> VictoryEmerald
        delta < 0 -> DefeatCrimson
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = if (isPositive) "+$delta" else delta.toString()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            style = StatDeltaStyle,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WinLossDrawBar(
    wins: Int,
    losses: Int,
    draws: Int,
    modifier: Modifier = Modifier
) {
    val total = (wins + losses + draws).coerceAtLeast(1)
    val winFrac = wins.toFloat() / total.toFloat()
    val drawFrac = draws.toFloat() / total.toFloat()
    val lossFrac = losses.toFloat() / total.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (wins + losses + draws > 0) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (winFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(winFrac)
                            .fillMaxSize()
                            .background(VictoryEmerald)
                    )
                }
                if (drawFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(drawFrac)
                            .fillMaxSize()
                            .background(DrawAmber)
                    )
                }
                if (lossFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(lossFrac)
                            .fillMaxSize()
                            .background(DefeatCrimson.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
fun RecentFormStrip(
    outcomes: List<MatchOutcome>,
    modifier: Modifier = Modifier
) {
    if (outcomes.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        outcomes.forEach { outcome ->
            val (label, bg, fg) = when (outcome) {
                MatchOutcome.WIN -> Triple("W", VictoryEmeraldSoft, VictoryEmerald)
                MatchOutcome.LOSS -> Triple("L", DefeatCrimsonSoft, DefeatCrimson)
                MatchOutcome.DRAW -> Triple("D", DrawAmberSoft, DrawAmber)
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = fg
                )
            }
        }
    }
}

@Composable
fun AvatarCircle(
    name: String,
    hexColor: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val parsedColor = parseHexColorOrDefault(hexColor, MaterialTheme.colorScheme.secondary)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(parsedColor.copy(alpha = 0.24f))
            .border(1.75.dp, parsedColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = parsedColor,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun EloSparklineChart(
    points: List<EloPoint>,
    lineColor: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainer

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceBg),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.45f), NeonMagenta.copy(alpha = 0.45f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val minElo = (points.minOfOrNull { it.elo } ?: 1200).coerceAtMost(1160)
            val maxElo = (points.maxOfOrNull { it.elo } ?: 1200).coerceAtLeast(1240)
            val span = (maxElo - minElo).coerceAtLeast(40)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Elo Trajectory",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Range $minElo - $maxElo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                val w = size.width
                val h = size.height
                val padTop = 10.dp.toPx()
                val padBottom = 10.dp.toPx()
                val usableHeight = (h - padTop - padBottom).coerceAtLeast(1f)

                fun yForElo(elo: Int): Float {
                    val norm = (elo - minElo).toFloat() / span.toFloat()
                    return padTop + usableHeight * (1f - norm)
                }

                // Dashed 1200 baseline in Sunset Gold
                val baselineY = yForElo(1200)
                drawLine(
                    color = DrawAmber.copy(alpha = 0.55f),
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                val synthBrush = Brush.horizontalGradient(
                    colors = listOf(lineColor, NeonMagenta)
                )

                if (points.size >= 2) {
                    val stepX = w / (points.size - 1).toFloat()
                    val strokePath = Path()
                    val fillPath = Path()

                    points.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = yForElo(pt.elo)
                        if (idx == 0) {
                            strokePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        if (idx == points.lastIndex) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                NeonMagenta.copy(alpha = 0.28f),
                                lineColor.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    drawPath(
                        path = strokePath,
                        brush = synthBrush,
                        style = Stroke(
                            width = 2.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    points.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = yForElo(pt.elo)
                        val radius = if (idx == points.lastIndex) 5.5.dp.toPx() else 3.dp.toPx()
                        drawCircle(
                            color = if (idx == points.lastIndex) NeonMagenta else lineColor,
                            radius = radius,
                            center = Offset(x, y)
                        )
                    }
                } else {
                    val y = yForElo(points.firstOrNull()?.elo ?: 1200)
                    drawCircle(
                        color = lineColor,
                        radius = 5.dp.toPx(),
                        center = Offset(w / 2f, y)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onActionClick: (() -> Unit)?,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.5f), NeonMagenta.copy(alpha = 0.5f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_pod),
                contentDescription = "Vaporwave Commander pod tabletop illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun EloFormulaInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Elo rating system info",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Commander Pod Elo",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Every deck and opponent starts at 1200 Elo. Matchups are scored pairwise across all players in the pod (K = 40, normalized by pod size).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "In a balanced 4-player pod where everyone is 1200 Elo, a solo winner gains +20 Elo and each opponent drops -7 Elo. In 2v2 team pods, each winning teammate gains +13 Elo and losers drop -13 Elo — beating higher-rated pods earns bonus Elo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Deleting a logged match automatically replays your chronological match history so every deck and opponent Elo stays accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("elo_info_close_button")
            ) {
                Text("Got it")
            }
        }
    )
}
