package com.artstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artstudio.ai.model.*
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.components.StudioDisabledExplanation
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * AI Models tab in Studio Settings.
 *
 * In strict alignment with ADR-004 and ADR-050:
 * - Transparently shows that ZERO neural weights are bundled in the base APK.
 * - Displays exact download weights size, RAM peak requirements, license, and target domain.
 * - Warns artists when natural photo models may bleed across thin line art.
 * - Provides immediate controls to download, load, unload, and delete weights.
 */
@Composable
fun StudioAiModelSettingsScreen(
    registry: AiModelRegistry,
    onDownloadModel: (AiModelDescriptor) -> Unit,
    onLoadModel: (AiModelDescriptor) -> Unit,
    onUnloadModel: (AiModelDescriptor) -> Unit,
    onDeleteModel: (AiModelDescriptor) -> Unit,
    onClearAllStorage: () -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val allModels = remember { registry.getAllModels() }
    val totalStorageBytes = registry.getTotalStorageUsedBytes()
    var wifiOnly by remember { mutableStateOf(!registry.downloader.allowCellular) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        contentPadding = PaddingValues(vertical = spacing.l)
    ) {
        // 1. Header & Architectural Privacy Guarantee Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.s)
            ) {
                Text(
                    text = "Optional On-Device AI Models",
                    style = typography.headlineSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s)),
                    color = colors.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(spacing.m)) {
                        Text(
                            text = "Download Only — On-Device Inference Not Implemented in v1.",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Classical Computer Vision is the authoritative AutoDraw engine in Art Studio v1. No AI models or neural inference runtimes (such as TFLite or ONNX Runtime) are bundled, preserving the strict < 15 MB APK footprint. Model downloads are available for preview and future inference staging only; no fake masks or synthetic predictions are generated.",
                            style = typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // 2. Storage Accounting & Wi-Fi Management Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m)),
                color = colors.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(spacing.m)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local AI Model Storage",
                                style = typography.titleSmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            val storageMb = totalStorageBytes / (1024.0 * 1024.0)
                            Text(
                                text = String.format("%.2f MB used on device", storageMb),
                                style = typography.bodySmall,
                                color = colors.accent.primary
                            )
                        }

                        if (totalStorageBytes > 0L) {
                            StudioButton(
                                text = "Clear All",
                                onClick = onClearAllStorage,
                                variant = StudioButtonVariant.Destructive,
                                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.m))
                    HorizontalDivider(color = colors.outlineSubtle)
                    Spacer(modifier = Modifier.height(spacing.m))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                wifiOnly = !wifiOnly
                                onToggleWifiOnly(wifiOnly)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi Only Downloads",
                                style = typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Prevent downloads over metered cellular networks",
                                style = typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                onToggleWifiOnly(it)
                            }
                        )
                    }
                }
            }
        }

        // 3. Section Title
        item {
            Text(
                text = "Available Model Catalog (${allModels.size})",
                style = typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = spacing.s)
            )
        }

        // 4. Model Catalog Entries
        items(allModels, key = { it.id }) { model ->
            AiModelCatalogCard(
                model = model,
                isDownloaded = registry.isModelDownloaded(model.id),
                isLoaded = registry.isModelLoaded(model.id),
                onDownload = { onDownloadModel(model) },
                onLoad = { onLoadModel(model) },
                onUnload = { onUnloadModel(model) },
                onDelete = { onDeleteModel(model) }
            )
        }
    }
}

/**
 * Individual Model Catalog Card.
 */
@Composable
fun AiModelCatalogCard(
    model: AiModelDescriptor,
    isDownloaded: Boolean,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.m))
            .border(
                1.dp,
                if (isLoaded) colors.accent.primary else colors.outlineSubtle,
                RoundedCornerShape(cornerRadius.m)
            ),
        color = colors.surfaceElevated
    ) {
        Column(modifier = Modifier.padding(spacing.m)) {
            // Top Row: Title, Category pill, License
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(cornerRadius.s),
                            color = colors.surfaceBase
                        ) {
                            Text(
                                text = model.category.displayName,
                                style = typography.labelSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(cornerRadius.s),
                            color = colors.surfaceBase
                        ) {
                            Text(
                                text = model.licenseId,
                                style = typography.labelSmall,
                                color = colors.accent.primary,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(cornerRadius.s),
                            color = colors.surfaceBase
                        ) {
                            Text(
                                text = model.formattedWeightsSize,
                                style = typography.labelSmall,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            Text(
                text = model.description,
                style = typography.bodySmall,
                color = colors.textSecondary
            )

            // Target Domain Warning for Photo-Trained Models on Line Art
            if (model.isPhotoOnly) {
                Spacer(modifier = Modifier.height(spacing.s))
                StudioDisabledExplanation(
                    reason = "Trained exclusively on natural photos. May blur across thin hand-drawn ink lines.",
                    suggestedAction = "For illustration line art, classical CV or U2-Net is recommended."
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
                    Text(
                        text = "✓ Always Active (Built-in)",
                        style = typography.labelMedium,
                        color = colors.accent.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (!isDownloaded) {
                    StudioButton(
                        text = "Download (${model.formattedWeightsSize})",
                        onClick = onDownload,
                        variant = StudioButtonVariant.Primary,
                        modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                    )
                } else {
                    // Model is downloaded
                    StudioButton(
                        text = "Delete",
                        onClick = onDelete,
                        variant = StudioButtonVariant.Secondary,
                        modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                    )

                    Spacer(modifier = Modifier.width(spacing.s))

                    if (isLoaded) {
                        StudioButton(
                            text = "Unload (Reclaim RAM)",
                            onClick = onUnload,
                            variant = StudioButtonVariant.Secondary,
                            modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                        )
                    } else {
                        StudioButton(
                            text = "Load Model",
                            onClick = onLoad,
                            variant = StudioButtonVariant.Primary,
                            modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Truthful AI Assist row for the Precision Region Editor.
 *
 * If no model is downloaded, shows a truthful explanation with direct navigation.
 */
@Composable
fun AiAssistToggleRow(
    isAiAssistEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    hasDownloadedModel: Boolean,
    onNavigateToRegistry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI-Assisted Segmentation",
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (hasDownloadedModel) {
                        "Propose candidate masks using downloaded on-device model"
                    } else {
                        "Requires optional downloaded model"
                    },
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
            }

            if (hasDownloadedModel) {
                Switch(
                    checked = isAiAssistEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle AI-assisted segmentation"
                    }
                )
            }
        }

        if (!hasDownloadedModel) {
            Spacer(modifier = Modifier.height(spacing.xs))
            StudioDisabledExplanation(
                reason = "No compatible AI models downloaded.",
                suggestedAction = "Open AI Models Registry to download an optional model"
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            StudioButton(
                text = "Download a model to enable AI assist",
                onClick = onNavigateToRegistry,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = StudioAccessibility.MinTouchTarget)
            )
        }
    }
}
