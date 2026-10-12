package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.comic.*
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Compose drawer panel for Comic and Manga creation tools.
 * Includes Panel frame generation, Gutter slicing, Speech & Thought balloons,
 * Procedural Screentones, and Manga page guides.
 */
@Composable
fun StudioComicPanel(
    onAddPanel: (ComicPanel) -> Unit,
    onSplitPanel: (Boolean, Float) -> Unit, // isHorizontal, gutter
    onAddBubble: (SpeechBubble) -> Unit,
    onAddScreentone: (ScreentoneConfig) -> Unit,
    onToggleGuides: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    canvasWidth: Float = 2048f,
    canvasHeight: Float = 2048f,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Panels, 1: Bubbles, 2: Screentone, 3: Guides

    // Panels state
    var gutterWidth by remember { mutableFloatStateOf(20f) }
    var panelBorderWidth by remember { mutableFloatStateOf(4f) }

    // Bubbles state
    var bubbleType by remember { mutableStateOf(SpeechBubbleType.SPEECH_OVAL) }
    var tailType by remember { mutableStateOf(BubbleTailType.STRAIGHT_POINTER) }
    var bubbleText by remember { mutableStateOf("Hello!") }
    var bubbleFontSize by remember { mutableFloatStateOf(24f) }

    // Screentone state
    var tonePattern by remember { mutableStateOf(ScreentonePattern.DOT) }
    var toneDensity by remember { mutableFloatStateOf(0.25f) }
    var toneFrequency by remember { mutableFloatStateOf(16f) }
    var toneAngle by remember { mutableFloatStateOf(45f) }

    // Guides state
    var guidesEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .width(330.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated.copy(alpha = 0.95f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Comic & Manga Studio",
                style = typography.titleSmall,
                color = colors.textPrimary
            )
            StudioButton(
                text = "Done",
                onClick = onDismissRequest,
                modifier = Modifier.height(32.dp)
            )
        }

        // Subsystem Tab Navigation
        val tabs = listOf("Frames", "Balloons", "Screentone", "Guides")
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surfaceBase,
            contentColor = colors.accent.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = typography.labelSmall, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        when (selectedTab) {
            0 -> {
                // =============================================================
                // TAB 0: Comic Panels & Gutter Slicing
                // =============================================================
                Text("Panel Presets", style = typography.labelMedium, color = colors.textSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StudioButton(
                        text = "4-Koma",
                        onClick = {
                            val panels = ComicGeometryEngine.create4KomaLayout(canvasWidth, canvasHeight)
                            panels.forEach { onAddPanel(it) }
                        },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                    StudioButton(
                        text = "2x2 Grid",
                        onClick = {
                            val layout = ComicPanelLayout(pageWidth = canvasWidth, pageHeight = canvasHeight)
                            val panels = ComicGeometryEngine.createGridPanels(layout, 2, 2)
                            panels.forEach { onAddPanel(it) }
                        },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                    StudioButton(
                        text = "1 Frame",
                        onClick = {
                            val p = ComicPanel.createRectangular(
                                x = 80f,
                                y = 80f,
                                width = canvasWidth - 160f,
                                height = canvasHeight - 160f,
                                strokeWidth = panelBorderWidth
                            )
                            onAddPanel(p)
                        },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                }

                Text("Gutter Spacing: ${gutterWidth.toInt()} px", style = typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = gutterWidth,
                    onValueChange = { gutterWidth = it },
                    valueRange = 8f..48f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent.primary, activeTrackColor = colors.accent.primary)
                )

                Text("Border Width: ${panelBorderWidth.toInt()} px", style = typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = panelBorderWidth,
                    onValueChange = { panelBorderWidth = it },
                    valueRange = 1f..12f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent.primary, activeTrackColor = colors.accent.primary)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StudioButton(
                        text = "Split Horiz",
                        onClick = { onSplitPanel(true, gutterWidth) },
                        modifier = Modifier.weight(1f).height(34.dp)
                    )
                    StudioButton(
                        text = "Split Vert",
                        onClick = { onSplitPanel(false, gutterWidth) },
                        modifier = Modifier.weight(1f).height(34.dp)
                    )
                }
            }
            1 -> {
                // =============================================================
                // TAB 1: Speech & Thought Balloons
                // =============================================================
                Text("Balloon Style", style = typography.labelMedium, color = colors.textSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SpeechBubbleType.entries.toList(), key = { it.name }) { bType ->
                        val isSel = bubbleType == bType
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                            .clickable { bubbleType = bType }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(bType.displayName, style = typography.labelSmall, color = if (isSel) colors.accent.onPrimary else colors.textPrimary)
                        }
                    }
                }

                Text("Tail Connector", style = typography.labelMedium, color = colors.textSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BubbleTailType.entries.toList(), key = { it.name }) { tType ->
                        val isSel = tailType == tType
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                            .clickable { tailType = tType }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(tType.displayName, style = typography.labelSmall, color = if (isSel) colors.accent.onPrimary else colors.textPrimary)
                        }
                    }
                }

                Text("Dialogue Text", style = typography.labelMedium, color = colors.textSecondary)
                OutlinedTextField(
                    value = bubbleText,
                    onValueChange = { bubbleText = it },
                    textStyle = typography.bodyMedium.copy(color = colors.textPrimary),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent.primary,
                        unfocusedBorderColor = colors.outlineSubtle
                    )
                )

                Text("Font Size: ${bubbleFontSize.toInt()} pt", style = typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = bubbleFontSize,
                    onValueChange = { bubbleFontSize = it },
                    valueRange = 12f..64f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent.primary, activeTrackColor = colors.accent.primary)
                )

                StudioButton(
                    text = "Insert Speech Bubble",
                    onClick = {
                        val bubble = SpeechBubble(
                            bubbleType = bubbleType,
                            x = canvasWidth / 2f - 120f,
                            y = canvasHeight / 2f - 80f,
                            width = 240f,
                            height = 160f,
                            tailType = tailType,
                            tailTipX = canvasWidth / 2f - 60f,
                            tailTipY = canvasHeight / 2f + 140f,
                            text = bubbleText,
                            fontSize = bubbleFontSize
                        )
                        onAddBubble(bubble)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
            }
            2 -> {
                // =============================================================
                // TAB 2: Screentone & Hatching Fills
                // =============================================================
                Text("Tone Pattern", style = typography.labelMedium, color = colors.textSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ScreentonePattern.entries.toList(), key = { it.name }) { pat ->
                        val isSel = tonePattern == pat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                            .clickable { tonePattern = pat }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(pat.displayName, style = typography.labelSmall, color = if (isSel) colors.accent.onPrimary else colors.textPrimary)
                        }
                    }
                }

                Text("Density Coverage", style = typography.labelMedium, color = colors.textSecondary)
                val densities = listOf(0.10f to "10%", 0.20f to "20%", 0.30f to "30%", 0.50f to "50%", 0.70f to "70%")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    densities.forEach { (dens, label) ->
                        val isSel = (toneDensity - dens).let { it >= -0.02f && it <= 0.02f }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                                .clickable { toneDensity = dens }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = typography.labelSmall, color = if (isSel) colors.accent.onPrimary else colors.textPrimary)
                        }
                    }
                }

                Text("Frequency Pitch: ${toneFrequency.toInt()} px", style = typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = toneFrequency,
                    onValueChange = { toneFrequency = it },
                    valueRange = 6f..48f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent.primary, activeTrackColor = colors.accent.primary)
                )

                Text("Screen Angle: ${toneAngle.toInt()}°", style = typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = toneAngle,
                    onValueChange = { toneAngle = it },
                    valueRange = 0f..90f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent.primary, activeTrackColor = colors.accent.primary)
                )

                StudioButton(
                    text = "Apply Procedural Tone Fill",
                    onClick = {
                        val config = ScreentoneConfig(
                            name = "${tonePattern.displayName} ${(toneDensity * 100).toInt()}%",
                            pattern = tonePattern,
                            frequency = toneFrequency,
                            density = toneDensity,
                            angleDeg = toneAngle
                        )
                        onAddScreentone(config)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
            }
            3 -> {
                // =============================================================
                // TAB 3: Manga Template Guides
                // =============================================================
                Text("Page Template Guides", style = typography.labelMedium, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manga Bleed / Trim / Live Area", style = typography.bodySmall, color = colors.textPrimary)
                    Switch(
                        checked = guidesEnabled,
                        onCheckedChange = {
                            guidesEnabled = it
                            onToggleGuides(it)
                        }
                    )
                }

                Text(
                    text = "• Trim Line: 40 px inside edge\n• Safe Live Area: 120 px inside edge\n• Gutter Alignment: 20 px / 14 px",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}
