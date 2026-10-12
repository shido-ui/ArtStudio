package com.artstudio.project

/**
 * Supported sort orders for the project browser.
 */
enum class ProjectSortOrder {
    ModifiedDescending,
    ModifiedAscending,
    TitleAscending,
    TitleDescending,
    DimensionsDescending,
    StrokeCountDescending
}

/**
 * Filter parameters for searching and grouping projects.
 */
data class ProjectSearchFilter(
    val query: String = "",
    val folder: String? = null,
    val tag: String? = null
)

/**
 * Applies search query, folder filter, tag filter, and sort order to a list of projects.
 */
fun List<ProjectMetadata>.applyFilterAndSort(
    filter: ProjectSearchFilter = ProjectSearchFilter(),
    sortOrder: ProjectSortOrder = ProjectSortOrder.ModifiedDescending
): List<ProjectMetadata> {
    val filtered = this.filter { proj ->
        // Query filter matches title, folder, or tags
        val queryMatches = if (filter.query.isBlank()) {
            true
        } else {
            val q = filter.query.trim().lowercase()
            proj.title.lowercase().contains(q) ||
                (proj.folder?.lowercase()?.contains(q) == true) ||
                proj.tags.any { it.lowercase().contains(q) }
        }

        // Folder filter
        val folderMatches = if (filter.folder == null) {
            true
        } else {
            proj.folder.equals(filter.folder, ignoreCase = true)
        }

        // Tag filter
        val tagMatches = if (filter.tag == null) {
            true
        } else {
            proj.tags.any { it.equals(filter.tag, ignoreCase = true) }
        }

        queryMatches && folderMatches && tagMatches
    }

    return when (sortOrder) {
        ProjectSortOrder.ModifiedDescending -> filtered.sortedByDescending { it.updatedAt }
        ProjectSortOrder.ModifiedAscending -> filtered.sortedBy { it.updatedAt }
        ProjectSortOrder.TitleAscending -> filtered.sortedBy { it.title.lowercase() }
        ProjectSortOrder.TitleDescending -> filtered.sortedByDescending { it.title.lowercase() }
        ProjectSortOrder.DimensionsDescending -> filtered.sortedByDescending { it.width * it.height }
        ProjectSortOrder.StrokeCountDescending -> filtered.sortedByDescending { it.strokeCount }
    }
}
