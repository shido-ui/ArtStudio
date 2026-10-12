package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationLimits
import com.artstudio.animation.AnimationTimeline
import com.artstudio.animation.LoopMode
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Jetpack Compose drawer for frame-by-frame animation timeline controls.
 *
 * Provides transport controls (play, pause, step), frame management (add, duplicate,
 * delete, duration), and onion skin toggling conforming to WCAG AAA touch targets.
 */
@Composable
fun StudioTimelinePanel(
    timeline: AnimationTimeline,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: (duplicateCels: Boolean) -> Unit,
    onDeleteFrame: () -> Unit,
    onSetFrameDuration: (Int, Int) -> Unit,
    onUpdateFps: (Int) -> Unit,
    onToggleLoopMode: () -> Unit,
    onToggleOnionSkin: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onScrub: ((Int) -> Unit)? = null,
    onFirstFrame: (() -> Unit)? = null,
    onLastFrame: (() -> Unit)? = null,
    onUpdateOnionSkinSettings: ((com.artstudio.animation.OnionSkinSettings) -> Unit)? = null,
    isFastPreviewEnabled: Boolean = false,
    onToggleFastPreview: ((Boolean) -> Unit)? = null
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    var showOnionSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.96f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l))
            .padding(spacing.m)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🎞️ Animation Timeline",
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "${timeline.frames.size} frames | ${timeline.totalDurationTicks} ticks (${timeline.totalDurationMs}ms)",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                // Low Memory Fast Preview Badge/Toggle
                if (onToggleFastPreview != null) {
                    StudioButton(
                        text = if (isFastPreviewEnabled) "⚡ Fast Preview ON" else "⚡ Fast Preview",
                        onClick = { onToggleFastPreview(!isFastPreviewEnabled) },
                        variant = if (isFastPreviewEnabled) StudioButtonVariant.Primary else StudioButtonVariant.Ghost
                    )
                }

                StudioIconButton(
                    contentDescription = "Close Timeline",
                    onClick = onClose,
                    icon = { Text("✕", style = typography.titleMedium, color = colors.textPrimary) }
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // 2. Transport Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First Frame
            StudioIconButton(
                contentDescription = "First Frame",
                onClick = { onFirstFrame?.invoke() ?: onSelectFrame(0) },
                icon = { Text("⏮|", style = typography.bodySmall, color = colors.textPrimary) }
            )

            // Step Backward
            StudioIconButton(
                contentDescription = "Step Backward",
                onClick = onStepBackward,
                icon = { Text("◀", style = typography.bodyMedium, color = colors.textPrimary) }
            )

            // Play / Pause
            StudioButton(
                text = if (isPlaying) "⏸ Pause" else "▶ Play",
                onClick = onPlayPauseToggle,
                variant = if (isPlaying) StudioButtonVariant.Primary else StudioButtonVariant.Secondary
            )

            // Step Forward
            StudioIconButton(
                contentDescription = "Step Forward",
                onClick = onStepForward,
                icon = { Text("▶", style = typography.bodyMedium, color = colors.textPrimary) }
            )

            // Last Frame
            StudioIconButton(
                contentDescription = "Last Frame",
                onClick = { onLastFrame?.invoke() ?: onSelectFrame(timeline.frames.lastIndex) },
                icon = { Text("|⏭", style = typography.bodySmall, color = colors.textPrimary) }
            )

            // Stop
            StudioIconButton(
                contentDescription = "Stop Animation",
                onClick = onStop,
                icon = { Text("⏹", style = typography.bodyMedium, color = colors.textPrimary) }
            )

            // Loop Mode Chip
            StudioButton(
                text = when (timeline.loopMode) {
                    LoopMode.LOOP -> "🔁 Loop"
                    LoopMode.ONCE -> "🔂 Once"
                    LoopMode.PING_PONG -> "🔀 Ping-Pong"
                },
                onClick = onToggleLoopMode,
                variant = StudioButtonVariant.Ghost
            )

            Spacer(modifier = Modifier.weight(1f))

            // Frame Status
            Text(
                text = "Frame ${timeline.currentFrameIndex + 1}/${timeline.frames.size}",
                style = typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = spacing.xs)
            )
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // 3. Interactive Timeline Scrubber Slider Row
        if (timeline.frames.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Text(
                    text = "Scrub:",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(42.dp)
                )
                Slider(
                    value = timeline.currentFrameIndex.toFloat(),
                    onValueChange = { targetFrame ->
                        val targetIdx = targetFrame.toInt().coerceIn(timeline.frames.indices)
                        onScrub?.invoke(targetIdx) ?: onSelectFrame(targetIdx)
                    },
                    valueRange = 0f..(timeline.frames.size - 1).toFloat(),
                    steps = if (timeline.frames.size > 2) timeline.frames.size - 2 else 0,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${timeline.currentFrameIndex + 1}",
                    style = typography.titleSmall,
                    color = colors.accent.primary,
                    modifier = Modifier.width(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.xs))
        }

        // 4. Settings Bar (FPS Slider & Onion Skin Switch)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${timeline.fps} FPS",
                    style = typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.width(55.dp)
                )
                Slider(
                    value = timeline.fps.toFloat(),
                    onValueChange = { onUpdateFps(it.toInt()) },
                    valueRange = 1f..60f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(spacing.m))

            // Onion Skin Toggle & Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(
                    text = "🧅 Onion Skin",
                    style = typography.bodySmall,
                    color = colors.textPrimary
                )
                Switch(
                    checked = timeline.onionSkin.enabled,
                    onCheckedChange = onToggleOnionSkin
                )
                if (timeline.onionSkin.enabled) {
                    StudioIconButton(
                        contentDescription = "Onion Skin Settings",
                        onClick = { showOnionSettings = !showOnionSettings },
                        icon = { Text(if (showOnionSettings) "▲" else "⚙️", style = typography.bodySmall, color = colors.textPrimary) }
                    )
                }
            }
        }

        // 5. Expandable Onion Skin Detailed Controls
        if (timeline.onionSkin.enabled && showOnionSettings) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xs)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceOverlay)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .padding(spacing.s)
            ) {
                Text(
                    text = "🧅 Onion Skin Settings (Non-Destructive Guides)",
                    style = typography.labelMedium,
                    color = colors.accent.primary
                )

                Spacer(modifier = Modifier.height(spacing.xs))

                // Frames Before / After Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    // Before
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Past (Orange): ${timeline.onionSkin.framesBefore}f (${(timeline.onionSkin.opacityBefore * 100).toInt()}%)",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Slider(
                            value = timeline.onionSkin.framesBefore.toFloat(),
                            onValueChange = {
                                onUpdateOnionSkinSettings?.invoke(timeline.onionSkin.copy(framesBefore = it.toInt()))
                            },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }

                    // After
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Future (Cyan): ${timeline.onionSkin.framesAfter}f (${(timeline.onionSkin.opacityAfter * 100).toInt()}%)",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Slider(
                            value = timeline.onionSkin.framesAfter.toFloat(),
                            onValueChange = {
                                onUpdateOnionSkinSettings?.invoke(timeline.onionSkin.copy(framesAfter = it.toInt()))
                            },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                }

                // Tint Mode & Falloff Mode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text("Color Mode:", style = typography.bodySmall, color = colors.textSecondary)
                        StudioButton(
                            text = if (timeline.onionSkin.tintMode == com.artstudio.animation.OnionSkinTintMode.TINTED) "🎨 Tinted (Orange/Cyan)" else "🌈 Original Colors",
                            onClick = {
                                val nextMode = if (timeline.onionSkin.tintMode == com.artstudio.animation.OnionSkinTintMode.TINTED) {
                                    com.artstudio.animation.OnionSkinTintMode.ORIGINAL_COLOR
                                } else {
                                    com.artstudio.animation.OnionSkinTintMode.TINTED
                                }
                                onUpdateOnionSkinSettings?.invoke(timeline.onionSkin.copy(tintMode = nextMode))
                            },
                            variant = StudioButtonVariant.Ghost
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text("Playback Guide:", style = typography.bodySmall, color = colors.textSecondary)
                        StudioButton(
                            text = if (timeline.onionSkin.suppressDuringPlayback) "⚡ Mute on Play" else "👁 Always On",
                            onClick = {
                                onUpdateOnionSkinSettings?.invoke(timeline.onionSkin.copy(suppressDuringPlayback = !timeline.onionSkin.suppressDuringPlayback))
                            },
                            variant = StudioButtonVariant.Ghost
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // 4. Horizontal Frame Strip (Thumbnails / Indicators)
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            timeline.frames.forEachIndexed { index, frame ->
                val isSelected = index == timeline.currentFrameIndex
                val borderCol = if (isSelected) colors.accent.primary else colors.outlineSubtle
                val bgCol = if (isSelected) colors.accent.primary.copy(alpha = 0.15f) else colors.surfaceElevated

                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(bgCol)
                        .border(if (isSelected) 2.dp else 1.dp, borderCol, RoundedCornerShape(cornerRadius.s))
                        .clickable { onSelectFrame(index) }
                        .padding(spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = typography.titleSmall,
                            color = if (isSelected) colors.accent.primary else colors.textPrimary
                        )

                        Text(
                            text = "${frame.durationTicks}x",
                            style = typography.labelSmall,
                            color = colors.textSecondary
                        )

                        Text(
                            text = if (frame.celLayerIds.isEmpty()) "Empty" else "${frame.celLayerIds.size} cel",
                            style = typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // 5. Frame Management Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add new drawing frame
            StudioButton(
                text = "+ Add Frame",
                onClick = onAddFrame,
                variant = StudioButtonVariant.Secondary
            )

            // Hold cel exposure
            StudioButton(
                text = "📋 Hold Cel",
                onClick = { onDuplicateFrame(false) },
                variant = StudioButtonVariant.Ghost
            )

            // Clone cel drawing
            StudioButton(
                text = "✨ Clone Drawing",
                onClick = { onDuplicateFrame(true) },
                variant = StudioButtonVariant.Ghost
            )

            // Duration - / +
            val currentDuration = timeline.currentFrame.durationTicks
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Dur:",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
                StudioIconButton(
                    contentDescription = "Decrease Duration",
                    enabled = currentDuration > 1,
                    onClick = {
                        onSetFrameDuration(timeline.currentFrameIndex, currentDuration - 1)
                    },
                    icon = { Text("-", style = typography.bodyMedium, color = colors.textPrimary) }
                )
                Text(
                    text = "$currentDuration",
                    style = typography.bodyMedium,
                    color = colors.textPrimary
                )
                StudioIconButton(
                    contentDescription = "Increase Duration",
                    enabled = currentDuration < AnimationLimits.MAX_FRAME_DURATION,
                    onClick = {
                        onSetFrameDuration(timeline.currentFrameIndex, currentDuration + 1)
                    },
                    icon = { Text("+", style = typography.bodyMedium, color = colors.textPrimary) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete Frame
            StudioButton(
                text = "🗑️ Delete",
                onClick = onDeleteFrame,
                enabled = timeline.frames.size > AnimationLimits.MIN_FRAMES,
                variant = StudioButtonVariant.Destructive
            )
        }
    }
}
