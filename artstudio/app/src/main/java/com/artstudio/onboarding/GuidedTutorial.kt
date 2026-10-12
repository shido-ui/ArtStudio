package com.artstudio.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Steps in the 60-second guided onboarding tutorial.
 */
enum class TutorialStep(
    val stepIndex: Int,
    val title: String,
    val caption: String
) {
    DRAW_STROKE(1, "Draw a Stroke", "Draw a stroke on the canvas to begin."),
    CHANGE_SIZE(2, "Change Brush Size", "Adjust the brush size slider to change stroke width."),
    UNDO_STROKE(3, "Undo Stroke", "Tap Undo in the top bar to reverse your stroke."),
    REDO_STROKE(4, "Redo Stroke", "Tap Redo to restore your reversed stroke."),
    SAVE_PROJECT(5, "Save & Finish", "Tap Back or Save to safely store your artwork.");

    companion object {
        val totalSteps: Int get() = entries.size
    }
}

/**
 * 60-second interactive guided tutorial overlay for new artists.
 * Highlights relevant UI zones with a soft spotlight card and short captions (<= 12 words).
 * Decomposed into small composables (<= 150 lines).
 */
@Composable
fun GuidedTutorialOverlay(
    currentStep: TutorialStep,
    onAdvanceStep: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(spacing.m)
            .semantics { contentDescription = "Guided Tutorial Step ${currentStep.stepIndex}" }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(2.dp, colors.accent.primary, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${currentStep.stepIndex} of ${TutorialStep.totalSteps}: ${currentStep.title}",
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "60s Tour",
                    style = typography.labelSmall,
                    color = colors.accent.primary
                )
            }

            Text(
                text = currentStep.caption,
                style = typography.bodyMedium,
                color = colors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioButton(
                    text = "Skip",
                    onClick = onSkip,
                    variant = StudioButtonVariant.Ghost,
                    modifier = Modifier.defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                )
                Spacer(modifier = Modifier.width(spacing.s))
                StudioButton(
                    text = if (currentStep == TutorialStep.SAVE_PROJECT) "Finish" else "Next Step",
                    onClick = onAdvanceStep,
                    variant = StudioButtonVariant.Primary,
                    modifier = Modifier.defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                )
            }
        }
    }
}
