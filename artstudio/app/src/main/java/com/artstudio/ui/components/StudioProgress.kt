package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Determinate progress bar displaying real task progress with percentage readout.
 */
@Composable
fun StudioProgressBar(
    progress: Float, // 0.0 to 1.0
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val clampedProgress = progress.coerceIn(0f, 1f)
    val percentage = (clampedProgress * 100).roundToInt()
    val progressColor = color ?: colors.accent.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = "$label: $percentage percent complete"
                this.progressBarRangeInfo = ProgressBarRangeInfo(clampedProgress, 0f..1f)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = typography.labelMedium,
                color = colors.textPrimary
            )
            Text(
                text = "$percentage%",
                style = typography.labelMedium,
                color = colors.accent.primary
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(cornerRadius.pill))
                .background(colors.outlineSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clampedProgress)
                    .clip(RoundedCornerShape(cornerRadius.pill))
                    .background(progressColor)
            )
        }
    }
}

/**
 * Indeterminate progress spinner with accessible description.
 */
@Composable
fun StudioProgressIndicator(
    contentDescription: String = "Loading, please wait",
    modifier: Modifier = Modifier,
    size: Dp = StudioAccessibility.IconMedium,
    color: Color? = null
) {
    val colors = StudioTheme.colors
    val spinnerColor = color ?: colors.accent.primary

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = spinnerColor,
            strokeWidth = 2.5.dp
        )
    }
}
