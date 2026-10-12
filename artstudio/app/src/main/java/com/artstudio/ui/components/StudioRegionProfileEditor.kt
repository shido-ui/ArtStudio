package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.cv.regionprofile.RegionAnalysisProfile
import com.artstudio.cv.regionprofile.RegionProfileOverride
import com.artstudio.cv.regionprofile.RegionProfilePreset
import com.artstudio.cv.regionprofile.ResolvedRegionProfile
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Jetpack Compose editor for region analysis profiles.
 *
 * Displays factory presets (Standard, High Detail, Fine Lines, Broad Strokes),
 * exposes all 9 concrete numerical parameters with sliders, clearly marks overridden
 * vs inherited values, and enables per-parameter or full reset actions.
 */
@Composable
fun StudioRegionProfileEditor(
    resolvedProfile: ResolvedRegionProfile,
    activePreset: RegionProfilePreset,
    activeOverride: RegionProfileOverride,
    onSelectPreset: (RegionProfilePreset) -> Unit,
    onUpdateOverride: (RegionProfileOverride) -> Unit,
    onResetAllOverrides: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState())
    ) {
        // Section Header
        Text(
            text = "Region Analysis Effort Profile",
            style = typography.titleSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tune feature sensitivity and processing passes for this region",
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(spacing.m))

        // Preset Chips
        Text(
            text = "Profile Preset",
            style = typography.labelLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            RegionProfilePreset.entries.forEach { p ->
                val isSelected = (p == activePreset)
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(cornerRadius.s)
                        )
                        .clickable { onSelectPreset(p) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "Profile preset: ${p.displayName}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = p.displayName,
                        fontSize = 12.sp,
                        color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.m))

        // Parameter Sliders with Inheritance Badges
        Text(
            text = "Individual Parameters & Overrides",
            style = typography.labelLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        // 1. Min Feature Size
        ProfileParamRow(
            label = "Min Feature Size",
            valueStr = String.format("%.1f px", resolvedProfile.minFeatureSize),
            value = resolvedProfile.minFeatureSize,
            range = 0.5f..16.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("minFeatureSize"),
            onValueChange = { onUpdateOverride(activeOverride.copy(minFeatureSize = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(minFeatureSize = null)) }
        )

        // 2. Edge Sensitivity
        ProfileParamRow(
            label = "Edge Sensitivity",
            valueStr = String.format("%.2f", resolvedProfile.edgeSensitivity),
            value = resolvedProfile.edgeSensitivity,
            range = 0.0f..1.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("edgeSensitivity"),
            onValueChange = { onUpdateOverride(activeOverride.copy(edgeSensitivity = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(edgeSensitivity = null)) }
        )

        // 3. Curve Fitting Tolerance
        ProfileParamRow(
            label = "Curve Fitting Tolerance",
            valueStr = String.format("%.2f px", resolvedProfile.curveFittingTolerance),
            value = resolvedProfile.curveFittingTolerance,
            range = 0.1f..5.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("curveFittingTolerance"),
            onValueChange = { onUpdateOverride(activeOverride.copy(curveFittingTolerance = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(curveFittingTolerance = null)) }
        )

        // 4. Simplification Tolerance
        ProfileParamRow(
            label = "Simplification (RDP)",
            valueStr = String.format("%.2f px", resolvedProfile.simplificationTolerance),
            value = resolvedProfile.simplificationTolerance,
            range = 0.1f..5.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("simplificationTolerance"),
            onValueChange = { onUpdateOverride(activeOverride.copy(simplificationTolerance = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(simplificationTolerance = null)) }
        )

        // 5. Gap Closing Radius
        ProfileParamRow(
            label = "Gap Detection Radius",
            valueStr = "${resolvedProfile.gapClosingRadius} px",
            value = resolvedProfile.gapClosingRadius.toFloat(),
            range = 0f..8f,
            isOverridden = resolvedProfile.isPropertyOverridden("gapClosingRadius"),
            onValueChange = { onUpdateOverride(activeOverride.copy(gapClosingRadius = it.toInt())) },
            onReset = { onUpdateOverride(activeOverride.copy(gapClosingRadius = null)) }
        )

        // 6. Detail Preservation
        ProfileParamRow(
            label = "Detail Preservation",
            valueStr = String.format("%.2f", resolvedProfile.detailPreservation),
            value = resolvedProfile.detailPreservation,
            range = 0.0f..1.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("detailPreservation"),
            onValueChange = { onUpdateOverride(activeOverride.copy(detailPreservation = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(detailPreservation = null)) }
        )

        // 7. Review Sensitivity
        ProfileParamRow(
            label = "Review Sensitivity",
            valueStr = String.format("%.2f", resolvedProfile.reviewSensitivity),
            value = resolvedProfile.reviewSensitivity,
            range = 0.0f..1.0f,
            isOverridden = resolvedProfile.isPropertyOverridden("reviewSensitivity"),
            onValueChange = { onUpdateOverride(activeOverride.copy(reviewSensitivity = it)) },
            onReset = { onUpdateOverride(activeOverride.copy(reviewSensitivity = null)) }
        )

        // 8. Boundary Margin
        ProfileParamRow(
            label = "Boundary Margin",
            valueStr = "${resolvedProfile.boundaryMarginPx} px",
            value = resolvedProfile.boundaryMarginPx.toFloat(),
            range = 0f..16f,
            isOverridden = resolvedProfile.isPropertyOverridden("boundaryMarginPx"),
            onValueChange = { onUpdateOverride(activeOverride.copy(boundaryMarginPx = it.toInt())) },
            onReset = { onUpdateOverride(activeOverride.copy(boundaryMarginPx = null)) }
        )

        // 9. Additional Passes
        ProfileParamRow(
            label = "Additional Passes",
            valueStr = "+${resolvedProfile.additionalPasses} passes",
            value = resolvedProfile.additionalPasses.toFloat(),
            range = 0f..4f,
            isOverridden = resolvedProfile.isPropertyOverridden("additionalPasses"),
            onValueChange = { onUpdateOverride(activeOverride.copy(additionalPasses = it.toInt())) },
            onReset = { onUpdateOverride(activeOverride.copy(additionalPasses = null)) }
        )

        Spacer(modifier = Modifier.height(spacing.m))

        // Reset All Overrides Action
        if (activeOverride.hasOverrides()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .clickable { onResetAllOverrides() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset All Overrides to Preset",
                    style = typography.labelMedium,
                    color = colors.accent.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileParamRow(
    label: String,
    valueStr: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isOverridden: Boolean,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = typography.bodySmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Inheritance Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.xs))
                        .background(if (isOverridden) colors.accent.container else colors.surfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isOverridden) "Overridden" else "Inherited",
                        fontSize = 9.sp,
                        color = if (isOverridden) colors.accent.onContainer else colors.textTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueStr,
                    style = typography.labelSmall,
                    color = if (isOverridden) colors.accent.primary else colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                if (isOverridden) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "↺",
                        style = typography.bodyMedium,
                        color = colors.accent.primary,
                        modifier = Modifier
                            .clickable { onReset() }
                            .padding(4.dp)
                            .semantics {
                                contentDescription = "Reset $label override"
                            }
                    )
                }
            }
        }

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 36.dp)
        )
    }
}
