package com.artstudio.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.R
import com.artstudio.ui.components.StudioIconButton
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioIcons
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel

/**
 * Accessible top bar for Studio canvas.
 * Manages project navigation, history (Undo/Redo), Zoom-to-fit, Import, Export, and Aid drawers.
 * Strictly decomposed so every composable is well under 150 lines.
 */
@Composable
fun StudioTopAppBar(
    projectTitle: String,
    viewModel: StudioViewModel,
    canUndo: Boolean,
    canRedo: Boolean,
    zoomLevel: Float,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFitToScreen: () -> Unit,
    onShowImport: () -> Unit,
    userMode: com.artstudio.ui.preferences.StudioUserMode = com.artstudio.ui.preferences.StudioUserMode.FULL,
    onUnlockAdvanced: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.surfaceBase.copy(alpha = 0.95f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(bottomStart = cornerRadius.m, bottomEnd = cornerRadius.m))
            .padding(horizontal = spacing.m, vertical = spacing.xs)
            .semantics { contentDescription = "Studio Top Bar" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioTopAppBarTitle(
            projectTitle = projectTitle,
            onNavigateBack = onNavigateBack
        )

        StudioTopAppBarActions(
            viewModel = viewModel,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
            onFitToScreen = onFitToScreen,
            onShowImport = onShowImport,
            userMode = userMode,
            onUnlockAdvanced = onUnlockAdvanced,
            onOpenHelp = onOpenHelp
        )
    }
}

@Composable
private fun StudioTopAppBarTitle(
    projectTitle: String,
    onNavigateBack: () -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        StudioIconButton(
            contentDescription = stringResource(R.string.action_back),
            onClick = onNavigateBack,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.ArrowBack,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = projectTitle.ifEmpty { "Untitled Studio Canvas" },
            style = typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun StudioTopAppBarActions(
    viewModel: StudioViewModel,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFitToScreen: () -> Unit,
    onShowImport: () -> Unit,
    userMode: com.artstudio.ui.preferences.StudioUserMode = com.artstudio.ui.preferences.StudioUserMode.FULL,
    onUnlockAdvanced: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null
) {
    val spacing = StudioTheme.spacing

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        if (userMode == com.artstudio.ui.preferences.StudioUserMode.JUST_DRAW && onUnlockAdvanced != null) {
            com.artstudio.ui.components.StudioButton(
                text = "Show Advanced Tools",
                onClick = onUnlockAdvanced,
                variant = com.artstudio.ui.components.StudioButtonVariant.Secondary
            )
        }
        StudioIconButton(
            contentDescription = stringResource(R.string.action_undo),
            enabled = canUndo,
            disabledExplanation = stringResource(R.string.cd_undo_disabled),
            onClick = onUndo,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Undo,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.action_redo),
            enabled = canRedo,
            disabledExplanation = stringResource(R.string.cd_redo_disabled),
            onClick = onRedo,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Redo,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.action_zoom_fit),
            onClick = onFitToScreen,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.ZoomFit,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.action_import),
            onClick = onShowImport,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Import,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.action_export),
            active = viewModel.showExportDialog,
            onClick = { viewModel.showExportDialog = true },
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Export,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.tool_drawing_aids),
            active = viewModel.activePanel == ActivePanel.DrawingAids,
            onClick = { viewModel.togglePanel(ActivePanel.DrawingAids) },
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Aids,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        StudioIconButton(
            contentDescription = stringResource(R.string.tool_comic),
            active = viewModel.activePanel == ActivePanel.Comic,
            onClick = { viewModel.togglePanel(ActivePanel.Comic) },
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            androidx.compose.material3.Icon(
                imageVector = StudioIcons.Comic,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        if (com.artstudio.core.FeatureFlags.isAutoDrawV1Enabled && userMode != com.artstudio.ui.preferences.StudioUserMode.JUST_DRAW) {
            StudioIconButton(
                contentDescription = stringResource(R.string.tool_autodraw),
                active = viewModel.activePanel == ActivePanel.AutoDraw,
                onClick = { viewModel.togglePanel(ActivePanel.AutoDraw) },
                modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
            ) { tint ->
                androidx.compose.material3.Icon(
                    imageVector = StudioIcons.AutoDraw,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (onOpenHelp != null) {
            StudioIconButton(
                contentDescription = "Help & Manual",
                onClick = onOpenHelp,
                modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
            ) { tint ->
                androidx.compose.material3.Icon(
                    imageVector = StudioIcons.Help,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
