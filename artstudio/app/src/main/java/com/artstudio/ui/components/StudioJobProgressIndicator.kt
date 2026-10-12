package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.job.JobSnapshot
import com.artstudio.job.JobState
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Real-time accessible job progress card and action controls for Studio background jobs.
 * Enforces zero fake completion states and provides explicit Cancel/Pause/Resume actions.
 */
@Composable
fun StudioJobProgressCard(
    snapshot: JobSnapshot,
    onCancel: () -> Unit,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val stateColor = when (snapshot.state) {
        JobState.QUEUED -> colors.textTertiary
        JobState.RUNNING -> colors.accent.primary
        JobState.PAUSED -> colors.warning
        JobState.SUCCEEDED -> colors.success
        JobState.FAILED -> colors.error
        JobState.CANCELLED -> colors.textTertiary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .padding(spacing.m)
            .semantics {
                this.contentDescription = "Background Job ${snapshot.name}, State: ${snapshot.state.name}, Progress: ${snapshot.progress.percentage}%"
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Job Name and Lane Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.name,
                        style = typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${snapshot.lane.displayName} • ${snapshot.state.name}",
                        style = typography.bodySmall,
                        color = stateColor
                    )
                }

                // Action Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (snapshot.isResumable) {
                        if (snapshot.state == JobState.RUNNING && onPause != null) {
                            StudioButton(
                                text = "Pause",
                                onClick = onPause,
                                modifier = Modifier
                                    .padding(end = spacing.xs)
                                    .heightIn(min = StudioAccessibility.MinTouchTarget)
                            )
                        } else if (snapshot.state == JobState.PAUSED && onResume != null) {
                            StudioButton(
                                text = "Resume",
                                onClick = onResume,
                                modifier = Modifier
                                    .padding(end = spacing.xs)
                                    .heightIn(min = StudioAccessibility.MinTouchTarget)
                            )
                        }
                    }

                    if (snapshot.state == JobState.RUNNING || snapshot.state == JobState.QUEUED || snapshot.state == JobState.PAUSED) {
                        StudioButton(
                            text = "Cancel",
                            onClick = onCancel,
                            modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            // Determinate Progress Bar
            StudioProgressBar(
                progress = snapshot.progress.progress,
                label = snapshot.progress.currentStep.ifEmpty { "Executing..." },
                color = stateColor
            )

            // Error display if failed
            if (snapshot.state == JobState.FAILED && snapshot.error != null) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "Error: ${snapshot.error.message} (${snapshot.error.code})",
                    style = typography.bodySmall,
                    color = colors.error
                )
            }
        }
    }
}
