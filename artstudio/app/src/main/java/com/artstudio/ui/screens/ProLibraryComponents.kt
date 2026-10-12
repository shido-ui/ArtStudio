package com.artstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.assets.download.AssetDownloadManager
import com.artstudio.assets.download.AssetDownloadState
import com.artstudio.assets.registry.AssetCategory
import com.artstudio.assets.registry.PublicAssetPack
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.theme.StudioTheme

@Composable
fun ProLibraryHeader(
    onBack: () -> Unit,
    onShowLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Pro Asset Library", style = typography.titleLarge, color = colors.textPrimary)
            Text(text = "Serverless CC0 Brushes, Textures, Fonts & Comics", style = typography.bodySmall, color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioButton(
                text = "Attributions",
                onClick = onShowLicenses,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.height(36.dp)
            )
            StudioButton(
                text = "Close",
                onClick = onBack,
                variant = StudioButtonVariant.Primary,
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
fun ProLibraryGlobalControls(
    downloadManager: AssetDownloadManager,
    onImportSaf: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(8.dp))
            .padding(spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Wi-Fi Only:", style = typography.labelMedium, color = colors.textPrimary)
            StudioButton(
                text = if (downloadManager.allowCellular) "Off (Allow Cellular)" else "On (Wi-Fi Only)",
                onClick = {
                    downloadManager.allowCellular = !downloadManager.allowCellular
                    onRefresh()
                },
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.height(32.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioButton(
                text = "Import from Files",
                onClick = onImportSaf,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.height(32.dp)
            )
            StudioButton(
                text = "Clear Cache",
                onClick = {
                    val cleared = downloadManager.clearCache()
                    onRefresh()
                },
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
fun ProLibraryCategoryTabs(
    selectedCategory: AssetCategory?,
    onSelectCategory: (AssetCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val isAll = selectedCategory == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isAll) colors.accent.primary else colors.surfaceElevated)
                    .clickable { onSelectCategory(null) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "All Packs", style = typography.labelMedium, color = if (isAll) colors.accent.onPrimary else colors.textPrimary)
            }
        }

        items(AssetCategory.entries) { cat ->
            val isSelected = selectedCategory == cat
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                    .clickable { onSelectCategory(cat) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = cat.displayName, style = typography.labelMedium, color = if (isSelected) colors.accent.onPrimary else colors.textPrimary)
            }
        }
    }
}

@Composable
fun ProLibraryPackCard(
    pack: PublicAssetPack,
    downloadManager: AssetDownloadManager,
    onStatusUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing

    var currentState by remember(pack) { mutableStateOf(downloadManager.getDownloadState(pack)) }

    DisposableEffect(pack) {
        val listener = { packId: String, state: AssetDownloadState ->
            if (packId == pack.id) {
                currentState = state
            }
        }
        downloadManager.addListener(listener)
        onDispose { downloadManager.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(8.dp))
            .padding(spacing.m)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = pack.displayName, style = typography.titleSmall, color = colors.textPrimary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceBase)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = pack.license, style = typography.labelSmall, color = colors.accent.primary)
                }
            }

            Text(text = pack.description, style = typography.bodySmall, color = colors.textSecondary)

            val sizeMb = pack.fileSizeBytes.toFloat() / (1024f * 1024f)
            Text(
                text = "${pack.category.displayName} • ${"%.1f".format(sizeMb)} MB • ${pack.format.extension}",
                style = typography.labelSmall,
                color = colors.textSecondary
            )

            // State & Actions
            when (val state = currentState) {
                is AssetDownloadState.NotDownloaded -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioButton(
                            text = "Download",
                            onClick = {
                                downloadManager.startDownload(pack)
                            },
                            variant = StudioButtonVariant.Primary,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                is AssetDownloadState.Queued -> {
                    Text(text = "Queued for download...", style = typography.bodySmall, color = colors.warning)
                }
                is AssetDownloadState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = colors.accent.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloading: ${(state.progress * 100).toInt()}% (${state.bytesDownloaded / 1024} KB)",
                                style = typography.labelSmall,
                                color = colors.textSecondary
                            )
                            StudioButton(
                                text = "Cancel",
                                onClick = { downloadManager.cancelDownload(pack.id) },
                                variant = StudioButtonVariant.Secondary,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
                is AssetDownloadState.Verifying -> {
                    Text(text = "Verifying cryptographic SHA-256...", style = typography.bodySmall, color = colors.accent.primary)
                }
                is AssetDownloadState.Installed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✓ Installed & Ready", style = typography.labelSmall, color = colors.success)
                        StudioButton(
                            text = "Delete",
                            onClick = {
                                downloadManager.deletePack(pack)
                                onStatusUpdate("Deleted ${pack.displayName}")
                            },
                            variant = StudioButtonVariant.Secondary,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                is AssetDownloadState.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "Error: ${state.reason}", style = typography.bodySmall, color = colors.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioButton(
                                text = "Retry",
                                onClick = { downloadManager.startDownload(pack) },
                                variant = StudioButtonVariant.Primary,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
