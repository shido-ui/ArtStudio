package com.artstudio.ui.navigation

/**
 * Top-level application navigation destinations.
 */
enum class StudioDestination(
    val route: String,
    val title: String,
    val iconSymbol: String,
    val description: String
) {
    HomeProjects(
        route = "home_projects",
        title = "Projects",
        iconSymbol = "📁",
        description = "Manage local projects, native .ffart documents, and imports."
    ),
    ManualStudio(
        route = "manual_studio",
        title = "Studio",
        iconSymbol = "🖌️",
        description = "Standalone professional manual painting studio with native Canvas/Skia surface."
    ),
    AutoDraw(
        route = "auto_draw",
        title = "AutoDraw",
        iconSymbol = "⚡",
        description = "Optional offline computer-vision reconstruction and vector line art analysis."
    ),
    ColourLab(
        route = "colour_lab",
        title = "Colour Lab",
        iconSymbol = "🌈",
        description = "15 composable artistic styles + No Colour baseline shader passes."
    ),
    AssetsBrushes(
        route = "assets_brushes",
        title = "Assets",
        iconSymbol = "🎨",
        description = "Brush presets, textures, palettes, and swatch libraries."
    ),
    Settings(
        route = "settings",
        title = "Settings",
        iconSymbol = "⚙️",
        description = "Themes, accessibility tokens, privacy consents, and storage budgets."
    ),
    ComponentReference(
        route = "component_reference",
        title = "UI System",
        iconSymbol = "📐",
        description = "Interactive design system reference catalog exercising all reusable controls."
    )
}
