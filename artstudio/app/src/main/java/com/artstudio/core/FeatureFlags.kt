package com.artstudio.core

/**
 * Authoritative system feature flags.
 * Governs which features are shipped in production vs hidden behind feature flags.
 *
 * In accordance with AutoDraw v1 & Subsystem Architecture:
 * - Shipped:
 *   1. AutoDraw v1 (Classical CV line-art contour detection, downscaling, background jobs, progress/cancel, stroke replay)
 *   2. PSD (Hardened streaming PSD parser, importer, and exporter)
 *   3. SVG (Safe vector XML parser, importer, and exporter)
 *   4. Animation (Timeline manager, playback controller, onion skins, GIF exporter)
 * - Hidden:
 *   1. AI Models (No TFLite runtime or on-device model weights; adapters return fake masks)
 *   2. ColorStack (Procedural multi-stage coloring requiring region segmentation)
 *   3. Deformation (Free-form 2D mesh displacement UI pending)
 */
object FeatureFlags {
    /**
     * Classical Computer Vision line-art vectorization and stroke replay (SHIPPED).
     */
    var isAutoDrawV1Enabled: Boolean = true

    /**
     * Hardened stream-based PSD import and export (SHIPPED).
     */
    var isPsdEnabled: Boolean = true

    /**
     * Safe SVG vector import and export (SHIPPED).
     */
    var isSvgEnabled: Boolean = true

    /**
     * Frame-by-frame animation timeline, playback, and GIF export (SHIPPED).
     */
    var isAnimationEnabled: Boolean = true

    /**
     * On-device AI models / neural networks (HIDDEN).
     * Disabled because runtime lacks TFLite dependencies and real model weights.
     */
    var isAiEnabled: Boolean = false

    /**
     * Whether optional native TFLite runtime has been downloaded on-demand (Option B).
     */
    var isTfliteRuntimeAvailable: Boolean = false

    /**
     * Multi-stage procedural ColorStack engine (SHIPPED).
     * Fully wired end-to-end with AutoDraw v1, region segmentation, palette extraction,
     * and non-destructive layer baking.
     */
    var isColorStackEnabled: Boolean = true

    /**
     * Free-form 2D mesh deformation and liquify engine (SHIPPED).
     * Interactive canvas handles, duplicate preview surface, memory bounded.
     */
    var isDeformationEnabled: Boolean = true
}
