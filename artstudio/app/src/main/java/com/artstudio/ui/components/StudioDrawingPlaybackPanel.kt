package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.autodraw.playback.*
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.abs
import kotlin.math.max

/**
 * Interactive, accessible Jetpack Compose panel for controllable visual drawing process playback on canvas.
 *
 * Implements play, pause, resume, restart, variable speed, step forward/back,
 * skip/replay region, layer progress, current stroke telemetry, and checkpoint restore.
 * Exposes estimated remaining time only when derived from measured progress, otherwise showing percentage/stage.
 */
@Composable
fun StudioDrawingPlaybackPanel(
    progressInfo: PlaybackProgressInfo,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetMode: (PlaybackMode) -> Unit,
    onSkipRegion: () -> Unit,
    onReplayRegion: () -> Unit,
    onSaveCheckpoint: () -> Unit,
    onRestoreCheckpoint: (ScheduleCheckpoint) -> Unit,
    onCancelPlayback: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l))
            .padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 AutoDraw Playback",
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )
                // State Badge
                val stateColor = when (progressInfo.state) {
                    PlaybackStateEnum.PLAYING -> colors.success
                    PlaybackStateEnum.PAUSED -> colors.warning
                    PlaybackStateEnum.COMPLETED -> colors.accent.primary
                    PlaybackStateEnum.CANCELLED -> colors.textTertiary
                    PlaybackStateEnum.IDLE -> colors.outlineSubtle
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(stateColor.copy(alpha = 0.2f))
                        .border(1.dp, stateColor, RoundedCornerShape(cornerRadius.s))
                        .padding(horizontal = spacing.s, vertical = 2.dp)
                ) {
                    Text(
                        text = progressInfo.state.name,
                        style = typography.labelSmall,
                        color = stateColor
                    )
                }
            }

            StudioIconButton(
                contentDescription = "Close Playback Panel",
                onClick = onClose
            ) { tint ->
                Text("✕", style = typography.titleSmall, color = tint)
            }
        }

        // 2. Stage & Time Estimation Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressInfo.currentStageName,
                style = typography.bodyMedium,
                color = colors.textPrimary
            )

            // Truthful Time Estimation: Only expose when derived from measured progress
            if (progressInfo.hasMeasuredProgress && progressInfo.estimatedRemainingTimeMs != null) {
                val remSeconds = max(0L, progressInfo.estimatedRemainingTimeMs / 1000L)
                val remMinutes = remSeconds / 60L
                val remSec = remSeconds % 60L
                val timeStr = if (remMinutes > 0) "${remMinutes}m ${remSec}s" else "${remSec}s"
                Text(
                    text = "⏱️ ~$timeStr remaining",
                    style = typography.labelSmall,
                    color = colors.accent.primary
                )
            } else {
                Text(
                    text = "Stage Progress (${(progressInfo.overallProgressFraction * 100).toInt()}%)",
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
            }
        }

        // 3. Overall Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceBase)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progressInfo.overallProgressFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.accent.primary)
            )
        }

        // Stroke and Layer Progress Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stroke: ${progressInfo.currentStrokeIndex} / ${progressInfo.totalStrokes}",
                style = typography.labelSmall,
                color = colors.textTertiary
            )
            progressInfo.currentLayerProgress?.let { lp ->
                Text(
                    text = "${lp.layerName}: ${lp.strokesCompleted}/${lp.totalStrokes} (${(lp.progressFraction * 100).toInt()}%)",
                    style = typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        // 4. Playback Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            PlaybackMode.values().forEach { m ->
                val isSelected = progressInfo.mode == m
                val label = when (m) {
                    PlaybackMode.LINE_ART_FIRST -> "Line-Art First"
                    PlaybackMode.LAYER_BY_LAYER -> "Layer by Layer"
                    PlaybackMode.DRAW_COLOUR_INTERLEAVED -> "Interleaved"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary.copy(alpha = 0.2f) else colors.surfaceBase)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(cornerRadius.s)
                        )
                        .clickable { onSetMode(m) }
                        .padding(horizontal = spacing.m, vertical = spacing.s)
                ) {
                    Text(
                        text = label,
                        style = typography.labelSmall,
                        color = if (isSelected) colors.accent.primary else colors.textSecondary
                    )
                }
            }
        }

        // 5. Main Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Restart
            StudioIconButton(
                contentDescription = "Restart Playback",
                onClick = onRestart
            ) { _ ->
                Text("⏮️", style = typography.titleSmall)
            }

            // Step Backward
            StudioIconButton(
                contentDescription = "Step Backward",
                onClick = onStepBackward
            ) { _ ->
                Text("⏪", style = typography.titleSmall)
            }

            // Play / Pause Toggle
            val isPlaying = progressInfo.state == PlaybackStateEnum.PLAYING
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(cornerRadius.pill))
                    .background(colors.accent.primary)
                    .clickable { if (isPlaying) onPause() else onResume() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    style = typography.titleMedium,
                    color = colors.accent.onPrimary
                )
            }

            // Step Forward
            StudioIconButton(
                contentDescription = "Step Forward",
                onClick = onStepForward
            ) { _ ->
                Text("⏩", style = typography.titleSmall)
            }

            // Cancel
            StudioIconButton(
                contentDescription = "Cancel Playback",
                onClick = onCancelPlayback
            ) { _ ->
                Text("⏹️", style = typography.titleSmall)
            }
        }

        // 6. Speed Control Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed:",
                style = typography.labelSmall,
                color = colors.textTertiary
            )
            listOf(0.5f, 1.0f, 2.0f, 4.0f, 8.0f, 100.0f).forEach { spd ->
                val isSelected = abs(progressInfo.speedMultiplier - spd) < 0.05f
                val label = if (spd >= 50f) "Max" else "${spd}x"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.container else colors.surfaceBase)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(cornerRadius.s)
                        )
                        .clickable { onSetSpeed(spd) }
                        .padding(horizontal = spacing.s, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        style = typography.labelSmall,
                        color = if (isSelected) colors.accent.onContainer else colors.textSecondary
                    )
                }
            }
        }

        // 7. Region & Checkpoint Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                StudioButton(
                    text = "Skip Region",
                    onClick = onSkipRegion
                )
                StudioButton(
                    text = "Replay Region",
                    onClick = onReplayRegion
                )
            }

            StudioButton(
                text = "Save Checkpoint",
                onClick = onSaveCheckpoint
            )
        }

        // 8. Saved Checkpoints List (if any)
        if (progressInfo.checkpoints.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Restore:",
                    style = typography.labelSmall,
                    color = colors.textTertiary
                )
                progressInfo.checkpoints.forEach { cp ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(colors.surfaceBase)
                        .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                        .clickable { onRestoreCheckpoint(cp) }
                        .padding(horizontal = spacing.s, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${cp.name} (stk ${cp.strokeIndex})",
                            style = typography.labelSmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
