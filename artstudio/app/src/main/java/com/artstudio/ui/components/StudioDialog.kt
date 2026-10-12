package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Modal Dialog for user confirmations, alerts, and settings.
 */
@Composable
fun StudioDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    cancelText: String? = "Cancel",
    isDestructive: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.xl)
                .semantics {
                    this.role = Role.Button
                    this.contentDescription = "$title dialog: $body"
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                Text(
                    text = title,
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )

                Text(
                    text = body,
                    style = typography.bodyMedium,
                    color = colors.textSecondary
                )

                content?.invoke()

                Spacer(modifier = Modifier.height(spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cancelText != null) {
                        StudioButton(
                            text = cancelText,
                            onClick = onDismiss,
                            variant = StudioButtonVariant.Ghost,
                            modifier = Modifier.padding(end = spacing.s)
                        )
                    }

                    StudioButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = if (isDestructive) StudioButtonVariant.Destructive else StudioButtonVariant.Primary
                    )
                }
            }
        }
    }
}
