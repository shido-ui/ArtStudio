package com.artstudio.comic

import com.artstudio.document.io.ArtJson
import java.io.File
import java.io.InputStream
import java.util.UUID

data class ComicPanelTemplate(
    val id: String,
    val name: String,
    val vertices: List<Point2D>,
    val description: String = ""
)

data class SpeechBubbleTemplate(
    val id: String,
    val name: String,
    val bubbleType: SpeechBubbleType,
    val tailType: BubbleTailType,
    val defaultText: String
)

data class ScreentoneAsset(
    val id: String,
    val name: String,
    val filePath: String? = null,
    val assetPath: String? = null,
    val isBuiltIn: Boolean = true,
    val author: String = "Art Studio Team",
    val license: String = "CC0-1.0",
    val provenance: String = "CC0 Comic Asset"
)

/**
 * Authoritative repository for comic and manga creation assets:
 * frame templates, speech balloon presets, and halftone screentone patterns.
 */
class ComicAssetRepository(
    val storageDir: File? = null,
    private val assetStreamOpener: ((String) -> InputStream?)? = null
) {
    private val panelTemplates = mutableListOf<ComicPanelTemplate>()
    private val bubbleTemplates = mutableListOf<SpeechBubbleTemplate>()
    private val screentoneAssets = mutableListOf<ScreentoneAsset>()

    init {
        loadBuiltIns()
        loadManifestAssets()
    }

    private fun loadBuiltIns() {
        // Standard panel layouts
        panelTemplates.add(
            ComicPanelTemplate(
                id = "panel_single_full",
                name = "Full Page Splash",
                vertices = listOf(Point2D(64f, 64f), Point2D(1984f, 64f), Point2D(1984f, 1984f), Point2D(64f, 1984f))
            )
        )
        panelTemplates.add(
            ComicPanelTemplate(
                id = "panel_4koma_top",
                name = "4-Koma Strip Panel",
                vertices = listOf(Point2D(128f, 64f), Point2D(1920f, 64f), Point2D(1920f, 480f), Point2D(128f, 480f))
            )
        )
        panelTemplates.add(
            ComicPanelTemplate(
                id = "panel_action_slanted",
                name = "Action Slash Panel",
                vertices = listOf(Point2D(64f, 64f), Point2D(1984f, 128f), Point2D(1800f, 1024f), Point2D(64f, 896f))
            )
        )

        // Bubble templates
        bubbleTemplates.add(
            SpeechBubbleTemplate(
                id = "bubble_dialogue_standard",
                name = "Standard Dialogue",
                bubbleType = SpeechBubbleType.SPEECH_OVAL,
                tailType = BubbleTailType.STRAIGHT_POINTER,
                defaultText = "..."
            )
        )
        bubbleTemplates.add(
            SpeechBubbleTemplate(
                id = "bubble_shout_burst",
                name = "Action Scream Burst",
                bubbleType = SpeechBubbleType.SHOUT_BURST,
                tailType = BubbleTailType.CURVED_POINTER,
                defaultText = "WHAT?!"
            )
        )
        bubbleTemplates.add(
            SpeechBubbleTemplate(
                id = "bubble_thought_cloud",
                name = "Internal Monologue",
                bubbleType = SpeechBubbleType.THOUGHT_CLOUD,
                tailType = BubbleTailType.THOUGHT_DOTS,
                defaultText = "Hmm..."
            )
        )
        bubbleTemplates.add(
            SpeechBubbleTemplate(
                id = "bubble_whisper_dashed",
                name = "Quiet Whisper",
                bubbleType = SpeechBubbleType.WHISPER,
                tailType = BubbleTailType.STRAIGHT_POINTER,
                defaultText = "Psst..."
            )
        )
    }

    private fun loadManifestAssets() {
        if (assetStreamOpener == null) return
        try {
            assetStreamOpener.invoke("comic/manifest.json")?.use { stream ->
                val text = stream.reader().readText()
                @Suppress("UNCHECKED_CAST")
                val map = ArtJson.parse(text) as? Map<String, Any?>
                val tones = map?.get("screentones") as? List<Map<String, Any?>> ?: return
                for (t in tones) {
                    val id = t["id"] as? String ?: continue
                    val name = t["name"] as? String ?: id
                    val file = t["file"] as? String ?: continue
                    screentoneAssets.add(
                        ScreentoneAsset(
                            id = id,
                            name = name,
                            assetPath = "comic/$file",
                            isBuiltIn = true,
                            author = t["author"] as? String ?: "Art Studio Team",
                            license = t["license"] as? String ?: "CC0-1.0",
                            provenance = "Bundled Screentone Asset"
                        )
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    fun getAvailablePanels(): List<ComicPanelTemplate> = panelTemplates.toList()
    fun getAvailableBubbles(): List<SpeechBubbleTemplate> = bubbleTemplates.toList()
    fun getAvailableScreentones(): List<ScreentoneAsset> = screentoneAssets.toList()

    /**
     * Registers assets from a downloaded comic pack directory.
     */
    fun registerPack(packId: String, packDir: File): Int {
        var count = 0
        if (!packDir.exists()) return count

        packDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val name = file.nameWithoutExtension
            if (file.name.endsWith(".png", ignoreCase = true) || file.name.endsWith(".svg", ignoreCase = true)) {
                screentoneAssets.add(
                    ScreentoneAsset(
                        id = "dl_${packId}_${name}",
                        name = name.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() },
                        filePath = file.absolutePath,
                        isBuiltIn = false,
                        author = "CC0 Comic Artist",
                        license = "CC0-1.0",
                        provenance = "Downloaded Pack ($packId)"
                    )
                )
                count++
            }
        }
        return count
    }

    fun removePack(packId: String): Int {
        val removed = screentoneAssets.removeAll { it.provenance.contains(packId) || it.id.startsWith("dl_${packId}") }
        return if (removed) 1 else 0
    }
}
