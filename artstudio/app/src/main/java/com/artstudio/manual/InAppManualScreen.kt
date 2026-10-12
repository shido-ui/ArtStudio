package com.artstudio.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.components.StudioIconButton
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioIcons
import com.artstudio.ui.theme.StudioTheme

/**
 * Searchable, context-aware In-App Manual screen.
 * Displays offline user documentation for all 11 studio chapters.
 * All composables guaranteed strictly <= 150 lines.
 */
@Composable
fun InAppManualScreen(
    initialChapterId: String? = null,
    onNavigateBack: () -> Unit,
    onTryItNow: ((sectionId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing

    val manualBook = remember {
        try {
            context.assets.open("manual/manual.json").use { ManualBook.loadFromStream(it) }
        } catch (_: Exception) {
            ManualBook("1.0", "Art Studio", emptyList())
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedChapterId by remember { mutableStateOf(initialChapterId ?: manualBook.chapters.firstOrNull()?.id ?: "") }

    val filteredChapters = remember(manualBook, searchQuery) {
        manualBook.search(searchQuery)
    }

    val activeChapter = remember(filteredChapters, selectedChapterId) {
        filteredChapters.firstOrNull { it.id == selectedChapterId } ?: filteredChapters.firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.m)
            .semantics { contentDescription = "In-App User Manual" }
    ) {
        ManualTopBar(
            onNavigateBack = onNavigateBack,
            searchQuery = searchQuery,
            onSearchChanged = { searchQuery = it }
        )

        Spacer(modifier = Modifier.height(spacing.s))

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ManualChapterDrawer(
                chapters = filteredChapters,
                selectedChapterId = activeChapter?.id,
                onSelectChapter = { selectedChapterId = it },
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(spacing.m))

            if (activeChapter != null) {
                ManualChapterView(
                    chapter = activeChapter,
                    onTryItNow = onTryItNow,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            } else {
                ManualEmptyState(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ManualTopBar(
    onNavigateBack: () -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        StudioIconButton(
            contentDescription = "Back",
            onClick = onNavigateBack,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Icon(StudioIcons.ArrowBack, contentDescription = null, tint = tint)
        }

        Text(
            text = "User Manual",
            style = typography.titleLarge,
            color = colors.textPrimary
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search tools, layers, brushes...") },
            modifier = Modifier.weight(1f).heightIn(max = 52.dp),
            singleLine = true
        )
    }
}

@Composable
private fun ManualChapterDrawer(
    chapters: List<ManualChapter>,
    selectedChapterId: String?,
    onSelectChapter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.s))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
            .padding(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        items(chapters, key = { it.id }) { chapter ->
            val isSelected = chapter.id == selectedChapterId
            val bg = if (isSelected) colors.accent.primary.copy(alpha = 0.15f) else colors.surfaceBase
            val textColor = if (isSelected) colors.accent.primary else colors.textPrimary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(cornerRadius.xs))
                    .background(bg)
                    .clickable { onSelectChapter(chapter.id) }
                    .padding(horizontal = spacing.s, vertical = spacing.xs)
            ) {
                Text(
                    text = chapter.title,
                    style = typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

@Composable
private fun ManualChapterView(
    chapter: ManualChapter,
    onTryItNow: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    LazyColumn(
        modifier = modifier.padding(horizontal = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        item {
            Text(text = chapter.title, style = typography.headlineLarge, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(text = chapter.intro, style = typography.bodyMedium, color = colors.textSecondary)
        }

        items(chapter.sections, key = { it.id }) { section ->
            ManualSectionItem(section = section, onTryItNow = onTryItNow)
        }
    }
}

@Composable
private fun ManualSectionItem(
    section: ManualSection,
    onTryItNow: ((String) -> Unit)?
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(text = section.title, style = typography.titleMedium, color = colors.textPrimary)
        Text(text = section.body, style = typography.bodySmall, color = colors.textSecondary)

        if (onTryItNow != null) {
            Spacer(modifier = Modifier.height(spacing.xs))
            StudioButton(
                text = "Try It Now",
                onClick = { onTryItNow(section.id) },
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ManualEmptyState(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No results. Try 'brush' or 'layers'.",
            style = typography.bodyMedium,
            color = colors.textTertiary
        )
    }
}
