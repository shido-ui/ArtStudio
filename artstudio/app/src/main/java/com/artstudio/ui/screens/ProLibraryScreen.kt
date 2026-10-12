package com.artstudio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.artstudio.assets.download.AssetDownloadManager
import com.artstudio.assets.download.AssetDownloadState
import com.artstudio.assets.importer.LocalAssetImporter
import com.artstudio.assets.importer.LocalImportResult
import com.artstudio.assets.registry.AssetCategory
import com.artstudio.assets.registry.AssetRegistry
import com.artstudio.assets.registry.PublicAssetPack
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.comic.ComicAssetRepository
import com.artstudio.texture.TextureRepository
import com.artstudio.ui.components.*
import com.artstudio.ui.theme.StudioTheme
import java.io.File

/**
 * Serverless Pro Library Screen.
 * Provides on-demand downloading from public CC0 repositories, storage preflight,
 * truthful download lifecycle feedback, and local zero-network SAF file imports.
 */
@Composable
fun ProLibraryScreen(
    downloadManager: AssetDownloadManager,
    onNavigateBack: () -> Unit,
    brushRepo: BrushPresetRepository? = null,
    textureRepo: TextureRepository? = null,
    comicRepo: ComicAssetRepository? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf<AssetCategory?>(AssetCategory.BRUSH) }
    var searchQuery by remember { mutableStateOf("") }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // SAF File Picker
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cat = selectedCategory ?: AssetCategory.BRUSH
            when (val res = LocalAssetImporter.importFromUri(context, uri, cat, brushRepo, textureRepo, comicRepo)) {
                is LocalImportResult.Success -> {
                    statusMessage = "Imported ${res.displayName} successfully! ${res.details}"
                    refreshKey++
                }
                is LocalImportResult.Failure -> {
                    statusMessage = "Import failed: ${res.reason}"
                }
            }
        }
    }

    val packs = remember(selectedCategory, searchQuery, refreshKey) {
        if (searchQuery.isNotBlank()) {
            AssetRegistry.searchPacks(searchQuery, selectedCategory)
        } else if (selectedCategory != null) {
            AssetRegistry.getPacksByCategory(selectedCategory!!)
        } else {
            AssetRegistry.getAllPacks()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        ProLibraryHeader(
            onBack = onNavigateBack,
            onShowLicenses = { showLicensesDialog = true }
        )

        ProLibraryGlobalControls(
            downloadManager = downloadManager,
            onImportSaf = {
                safLauncher.launch(arrayOf("*/*"))
            },
            onRefresh = { refreshKey++ }
        )

        ProLibraryCategoryTabs(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it }
        )

        statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.accent.primary, RoundedCornerShape(8.dp))
                    .padding(spacing.s)
            ) {
                Text(text = msg, style = StudioTheme.typography.bodySmall, color = colors.textPrimary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            items(packs, key = { it.id }) { pack ->
                ProLibraryPackCard(
                    pack = pack,
                    downloadManager = downloadManager,
                    onStatusUpdate = {
                        statusMessage = it
                        refreshKey++
                    }
                )
            }
        }
    }

    if (showLicensesDialog) {
        StudioLicensesViewerDialog(onDismissRequest = { showLicensesDialog = false })
    }
}
