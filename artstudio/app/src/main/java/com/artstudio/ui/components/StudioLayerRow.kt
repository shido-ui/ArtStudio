package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.document.model.BlendModeType
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Functional Layer Row component for digital art layer management.
 * Provides accessible controls for layer selection, visibility, lock status, opacity, and blend mode.
 */
@Composable
fun StudioLayerRow(
    layerName: String,
    isSelected: Boolean,
    isVisible: Boolean,
    isLocked: Boolean,
    opacityPercent: Int,
    onSelectLayer: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    blendMode: BlendModeType = BlendModeType.Normal,
    thumbnailColor: Color = Color(0xFF607D8B),
    isClippingMask: Boolean = false,
    isAlphaLocked: Boolean = false,
    hasMask: Boolean = false,
    isMaskEnabled: Boolean = true,
    isEditingMask: Boolean = false,
    enabled: Boolean = true
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val rowBg = when {
        isSelected -> colors.accent.container.copy(alpha = 0.35f)
        else -> colors.surfaceElevated
    }
    val borderColor = if (isSelected) colors.accent.primary else colors.outlineSubtle

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(rowBg)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius.m))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelectLayer
            )
            .padding(horizontal = spacing.s, vertical = spacing.xs)
            .semantics {
                this.role = Role.Button
                this.contentDescription = "$layerName, ${if (isSelected) "selected" else "unselected"}, ${if (isVisible) "visible" else "hidden"}, ${if (isLocked) "locked" else "unlocked"}, ${if (isClippingMask) "clipping mask, " else ""}${if (isAlphaLocked) "alpha locked, " else ""}${if (hasMask) "has mask, " else ""}opacity $opacityPercent percent, blend mode ${blendMode.displayName}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Thumbnail preview
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(cornerRadius.xs))
                .background(thumbnailColor)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.xs)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isClippingMask) "✂" else layerName.take(1).uppercase(),
                style = typography.labelSmall,
                color = Color.White
            )
        }

        // Layer Name & Opacity readout
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (isClippingMask) {
                    Text(
                        text = "✂️",
                        style = typography.labelSmall,
                        color = colors.accent.primary
                    )
                }
                Text(
                    text = layerName,
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                if (isAlphaLocked) {
                    Text(
                        text = "🔒α",
                        style = typography.labelSmall,
                        color = colors.warning
                    )
                }
                if (hasMask) {
                    Text(
                        text = if (isMaskEnabled) "🎭" else "🎭🚫",
                        style = typography.labelSmall,
                        color = if (isEditingMask) colors.accent.primary else colors.textSecondary
                    )
                }
            }
            val blendSubtitle = buildString {
                append("Opacity: $opacityPercent%")
                if (blendMode != BlendModeType.Normal) {
                    append(" • ${blendMode.displayName}")
                }
                if (isClippingMask) {
                    append(" • Clip")
                }
                if (isAlphaLocked) {
                    append(" • α-Lock")
                }
                if (hasMask) {
                    append(if (isMaskEnabled) " • Mask" else " • Mask (Off)")
                    if (isEditingMask) {
                        append(" [Editing Mask]")
                    }
                }
            }
            Text(
                text = blendSubtitle,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }

        // Visibility Toggle Button (Eye)
        Box(
            modifier = Modifier
                .size(StudioAccessibility.MinTouchTarget) // 48dp touch target
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleVisibility
                )
                .semantics {
                    this.role = Role.Checkbox
                    this.contentDescription = if (isVisible) "Hide layer $layerName" else "Show layer $layerName"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isVisible) "👁" else "🚫",
                style = typography.titleMedium,
                color = if (isVisible) colors.textPrimary else colors.textTertiary
            )
        }

        // Lock Toggle Button (Padlock)
        Box(
            modifier = Modifier
                .size(StudioAccessibility.MinTouchTarget) // 48dp touch target
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleLock
                )
                .semantics {
                    this.role = Role.Checkbox
                    this.contentDescription = if (isLocked) "Unlock layer $layerName" else "Lock layer $layerName"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLocked) "🔒" else "🔓",
                style = typography.titleMedium,
                color = if (isLocked) colors.warning else colors.textTertiary
            )
        }
    }
}
