package com.artstudio.ui.navigation

/**
 * Strongly typed navigation routes with arguments for Art Studio.
 */
sealed class StudioNavRoute(val routeKey: String) {
    object Onboarding : StudioNavRoute("onboarding")
    object HomeProjects : StudioNavRoute("home_projects")
    
    data class ManualStudio(
        val projectId: String,
        val projectTitle: String,
        val isReadOnlySample: Boolean = false
    ) : StudioNavRoute("manual_studio")
    
    data class InAppManual(
        val chapterId: String? = null
    ) : StudioNavRoute("in_app_manual")

    object AutoDraw : StudioNavRoute("auto_draw")
    object ColourLab : StudioNavRoute("colour_lab")
    object AssetsBrushes : StudioNavRoute("assets_brushes")
    object Settings : StudioNavRoute("settings")
    object ComponentReference : StudioNavRoute("component_reference")
    object InputDiagnostics : StudioNavRoute("input_diagnostics")
    object ProLibrary : StudioNavRoute("pro_library")

    /**
     * Serializes route for state restoration.
     */
    fun serialize(): String {
        return when (this) {
            is Onboarding -> "onboarding"
            is HomeProjects -> "home_projects"
            is ManualStudio -> "manual_studio|$projectId|$projectTitle|$isReadOnlySample"
            is InAppManual -> if (chapterId != null) "in_app_manual|$chapterId" else "in_app_manual"
            is AutoDraw -> "auto_draw"
            is ColourLab -> "colour_lab"
            is AssetsBrushes -> "assets_brushes"
            is Settings -> "settings"
            is ComponentReference -> "component_reference"
            is InputDiagnostics -> "input_diagnostics"
            is ProLibrary -> "pro_library"
        }
    }

    companion object {
        /**
         * Restores route from serialized string representation.
         * Falls back safely to HomeProjects on corrupt or unknown tokens.
         */
        fun deserialize(serialized: String): StudioNavRoute {
            val parts = serialized.split("|")
            return when (parts.firstOrNull()) {
                "onboarding" -> Onboarding
                "home_projects" -> HomeProjects
                "manual_studio" -> {
                    val id = parts.getOrNull(1) ?: return HomeProjects
                    val title = parts.getOrNull(2) ?: "Untitled Project"
                    val isSample = parts.getOrNull(3)?.toBoolean() ?: false
                    ManualStudio(id, title, isSample)
                }
                "in_app_manual" -> InAppManual(parts.getOrNull(1))
                "auto_draw" -> AutoDraw
                "colour_lab" -> ColourLab
                "assets_brushes" -> AssetsBrushes
                "settings" -> Settings
                "component_reference" -> ComponentReference
                "input_diagnostics" -> InputDiagnostics
                "pro_library" -> ProLibrary
                else -> HomeProjects
            }
        }
    }
}
