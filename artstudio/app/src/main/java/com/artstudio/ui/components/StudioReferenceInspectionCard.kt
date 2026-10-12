package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.reference.LikelyImageClass
import com.artstudio.reference.MemoryAdvice
import com.artstudio.reference.ReconstructionMode
import com.artstudio.reference.ReferenceInspectionReport
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Jetpack Compose card/dialog for inspecting an imported reference image
 * and configuring its reconstruction mode before starting expensive analysis.
 */
@Composable
fun StudioReferenceInspectionCard(
    report: ReferenceInspectionReport,
    onConfirmMode: (mode: ReconstructionMode) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    var selectedMode by remember(report) {
        mutableStateOf(report.suggestion.effectiveMode)
    }

    CardContainer(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.m)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Source Name and Format Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reference Pre-Inspection",
                        style = typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = report.sourceName,
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }

                FormatBadge(formatName = report.format.name)
            }

            Spacer(modifier = Modifier.height(spacing.s))

            // Dimensions and EXIF Orientation Status
            val (normW, normH) = report.normalizedDimensions
            val (rawW, rawH) = report.rawDimensions
            val orientationNotice = if (report.exifOrientation != 1) {
                " (EXIF ${report.exifOrientation} corrected from ${rawW}x${rawH})"
            } else ""

            InfoRow(
                label = "Dimensions",
                value = "${normW}x${normH} px$orientationNotice"
            )

            // Transparency Profile
            val transText = when {
                report.transparencyProfile == null -> "Unknown"
                !report.transparencyProfile.hasAlpha -> "Fully Opaque (No Alpha)"
                report.transparencyProfile.type.name == "BINARY_MASK" -> "Cutout Alpha Mask (${"%.1f".format(report.transparencyProfile.alphaRatio * 100)}% transparent)"
                else -> "Semi-Transparent Gradients (${"%.1f".format(report.transparencyProfile.alphaRatio * 100)}% transparent)"
            }
            InfoRow(label = "Transparency", value = transText)

            // Memory Footprint & Advice
            val memBytes = report.memoryEstimate.estimatedDecodedBytes
            val memMb = memBytes.toFloat() / (1024 * 1024f)
            val adviceBadgeColor = when (report.memoryEstimate.advice) {
                MemoryAdvice.NORMAL_MEMORY -> Color(0xFF2E7D32)
                MemoryAdvice.HIGH_MEMORY_WARN -> Color(0xFFF57F17)
                MemoryAdvice.EXCEEDS_BUDGET_DOWNSAMPLE_RECOMMENDED -> Color(0xFFC62828)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Estimated Memory",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${"%.1f".format(memMb)} MB",
                        style = typography.bodySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(adviceBadgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = report.memoryEstimate.advice.name.replace("_", " "),
                            fontSize = 10.sp,
                            color = adviceBadgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // Suggested Reconstruction Mode Section
            Text(
                text = "Reconstruction Mode",
                style = typography.labelLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(spacing.xxs))

            // Suggestion Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.accent.primary.copy(alpha = 0.4f), RoundedCornerShape(cornerRadius.s))
                    .padding(spacing.s)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatModeName(report.suggestion.suggestedMode),
                            style = typography.bodyMedium,
                            color = colors.accent.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${(report.suggestion.confidence * 100).toInt()}% Confidence",
                                fontSize = 11.sp,
                                color = colors.accent.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.suggestion.reasoning,
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )

                    if (report.suggestion.visibleLimitations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Visible Limitations:",
                            style = typography.labelSmall,
                            color = Color(0xFFEF6C00),
                            fontWeight = FontWeight.Bold
                        )
                        for (limitation in report.suggestion.visibleLimitations) {
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    style = typography.bodySmall,
                                    color = Color(0xFFEF6C00)
                                )
                                Text(
                                    text = limitation,
                                    style = typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // User Mode Override Radio Options
            Text(
                text = "Override Reconstruction Mode",
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(spacing.xxs))

            Column(modifier = Modifier.fillMaxWidth()) {
                ReconstructionMode.entries.forEach { mode ->
                    val isSelected = (mode == selectedMode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .clickable(
                                role = Role.RadioButton,
                                onClick = { selectedMode = mode }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .semantics {
                                contentDescription = "Select ${formatModeName(mode)}"
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Radio circle
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    2.dp,
                                    if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                    CircleShape
                                )
                                .background(if (isSelected) colors.accent.primary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = formatModeName(mode),
                                style = typography.bodyMedium,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.l))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.surfaceElevated)
                        .clickable(onClick = onDismissRequest)
                        .semantics { role = Role.Button; contentDescription = "Cancel Reference Inspection" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        style = typography.bodyMedium,
                        color = colors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.accent.primary)
                        .clickable(onClick = { onConfirmMode(selectedMode) })
                        .semantics { role = Role.Button; contentDescription = "Proceed to Analysis with Selected Mode" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Proceed",
                        style = typography.bodyMedium,
                        color = colors.accent.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CardContainer(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = StudioTheme.colors
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
    ) {
        content()
    }
}

@Composable
private fun FormatBadge(formatName: String) {
    val colors = StudioTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = formatName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent.primary
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = typography.bodySmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatModeName(mode: ReconstructionMode): String {
    return when (mode) {
        ReconstructionMode.LINE_ART_VECTORIZATION -> "Line Art Vectorization"
        ReconstructionMode.PENCIL_CLEANUP_AND_INK -> "Pencil Cleanup & Ink"
        ReconstructionMode.FLAT_REGION_CEL_SHADING -> "Flat Region Cel Shading"
        ReconstructionMode.MULTI_LAYER_HYBRID_DECOMPOSITION -> "Multi-Layer Hybrid Decomposition"
        ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER -> "Continuous-Tone Paint Layer"
    }
}
