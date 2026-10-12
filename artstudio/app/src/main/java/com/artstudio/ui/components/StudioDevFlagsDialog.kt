package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.artstudio.core.FeatureFlags
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

data class DevFeatureFlagItem(
    val name: String,
    val description: String,
    val isEnabled: () -> Boolean,
    val onToggle: (Boolean) -> Unit,
    val releaseStatus: String
)

/**
 * Debug-only developer panel listing every feature flag and its current runtime value.
 * Complies with strict architectural constraints: <= 150 lines per composable.
 */
@Composable
fun StudioDevFlagsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(cornerRadius.l)),
            color = colors.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .padding(spacing.l)
                    .verticalScroll(rememberScrollState())
            ) {
                DevFlagsHeader(onDismissRequest)
                Spacer(modifier = Modifier.height(spacing.m))
                DevFlagsList()
                Spacer(modifier = Modifier.height(spacing.m))
                StudioButton(
                    text = "Close Panel",
                    onClick = onDismissRequest,
                    variant = StudioButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth().height(StudioAccessibility.MinTouchTarget)
                )
            }
        }
    }
}

@Composable
private fun DevFlagsHeader(onDismissRequest: () -> Unit) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Developer Feature Flags",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "Debug runtime subsystem gates",
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
        StudioButton(
            text = "✕",
            onClick = onDismissRequest,
            variant = StudioButtonVariant.Ghost,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        )
    }
}

@Composable
private fun DevFlagsList() {
    var flagUpdateCounter by remember { mutableIntStateOf(0) }
    val spacing = StudioTheme.spacing

    val flags = remember(flagUpdateCounter) {
        listOf(
            DevFeatureFlagItem("AutoDraw v1", "Classical CV linework tracing & stroke playback", { FeatureFlags.isAutoDrawV1Enabled }, { FeatureFlags.isAutoDrawV1Enabled = it }, "SHIPPED"),
            DevFeatureFlagItem("PSD Format", "Streaming Photoshop import and export", { FeatureFlags.isPsdEnabled }, { FeatureFlags.isPsdEnabled = it }, "SHIPPED"),
            DevFeatureFlagItem("SVG Engine", "XML-safe vector format interchange", { FeatureFlags.isSvgEnabled }, { FeatureFlags.isSvgEnabled = it }, "SHIPPED"),
            DevFeatureFlagItem("Animation", "Multi-frame timeline & GIF export", { FeatureFlags.isAnimationEnabled }, { FeatureFlags.isAnimationEnabled = it }, "SHIPPED"),
            DevFeatureFlagItem("Colour Lab", "Multi-stage procedural region coloring", { FeatureFlags.isColorStackEnabled }, { FeatureFlags.isColorStackEnabled = it }, "SHIPPED"),
            DevFeatureFlagItem("Deformation", "2D Mesh warp and liquify brushes", { FeatureFlags.isDeformationEnabled }, { FeatureFlags.isDeformationEnabled = it }, "SHIPPED"),
            DevFeatureFlagItem("AI Neural Models", "MediaPipe / TFLite segmentation (Option A)", { FeatureFlags.isAiEnabled }, { FeatureFlags.isAiEnabled = it }, "DISABLED (v1)")
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        flags.forEach { item ->
            DevFlagRowItem(item = item, onToggled = { enabled ->
                item.onToggle(enabled)
                flagUpdateCounter++
            })
        }
    }
}

@Composable
private fun DevFlagRowItem(
    item: DevFeatureFlagItem,
    onToggled: (Boolean) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val isEnabled = item.isEnabled()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(8.dp))
            .padding(spacing.m)
            .semantics { contentDescription = "Flag ${item.name}: ${if (isEnabled) "Enabled" else "Disabled"}" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.name, style = typography.titleSmall, color = colors.textPrimary)
                Text(
                    text = "[${item.releaseStatus}]",
                    style = typography.labelSmall,
                    color = if (item.releaseStatus.startsWith("SHIPPED")) colors.success else colors.textTertiary
                )
            }
            Text(text = item.description, style = typography.bodySmall, color = colors.textSecondary)
        }
        StudioButton(
            text = if (isEnabled) "ON" else "OFF",
            onClick = { onToggled(!isEnabled) },
            variant = if (isEnabled) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
            modifier = Modifier.height(StudioAccessibility.MinTouchTarget).width(64.dp)
        )
    }
}
