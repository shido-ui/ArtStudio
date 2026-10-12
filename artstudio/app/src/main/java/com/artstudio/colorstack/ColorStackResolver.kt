package com.artstudio.colorstack

/**
 * Severity ranking for Colour Stack recipe validation messages.
 */
enum class MessageSeverity {
    /** Informational note regarding resolved behaviors (e.g. No Colour bypass, Monochrome+Neon semantics) */
    INFO,

    /** Advisory warning where combination may produce unintended visual artifacts (e.g. Neon before Monochrome) */
    WARNING,

    /** Critical error preventing execution (e.g. invalid configuration) */
    ERROR
}

/**
 * Structured diagnostic message returned by [ColorStackResolver].
 */
data class ResolutionMessage(
    val severity: MessageSeverity,
    val code: String,
    val title: String,
    val description: String,
    val affectedStageId: String? = null,
    val suggestedFix: String? = null
)

/**
 * Evaluation outcome returned by [ColorStackResolver.resolve].
 */
data class ColorStackResolverResult(
    val isValid: Boolean,
    val resolvedStages: List<ColorStageConfig>,
    val activeStages: List<ColorStageConfig>,
    val bypassedStages: List<ColorStageConfig>,
    val messages: List<ResolutionMessage>,
    val isNoColourActive: Boolean = false,
    val hasMonochromeNeonSemantic: Boolean = false
) {
    val hasWarnings: Boolean get() = messages.any { it.severity == MessageSeverity.WARNING }
    val hasErrors: Boolean get() = messages.any { it.severity == MessageSeverity.ERROR }
    val hasConflicts: Boolean get() = hasWarnings || hasErrors
}

/**
 * Deterministic Colour Stack Resolver and Conflict Evaluator.
 *
 * Enforces canonical ordering, evaluates style combinations, flags invalid or conflicting
 * configurations with user-facing diagnostic messages, and handles special semantic cases
 * such as No Colour baseline bypass and Monochrome + Neon ordered composition.
 */
object ColorStackResolver {

    /**
     * Canonical standard execution order for stage types.
     */
    val CANONICAL_STAGE_ORDER = listOf(
        ColorStageType.BASE_FILL,
        ColorStageType.CEL_SHADOW,
        ColorStageType.GRADIENT,
        ColorStageType.HIGHLIGHT,
        ColorStageType.MEDIUM_TEXTURE,
        ColorStageType.PALETTE_TRANSFORM,
        ColorStageType.FINISHING_EFFECT
    )

    /**
     * Evaluates a recipe, validates stage order, resolves conflicts, and generates
     * actionable diagnostic messages.
     */
    fun resolve(recipe: ColorStackRecipe): ColorStackResolverResult {
        val messages = mutableListOf<ResolutionMessage>()
        val stages = recipe.stages

        // Check 1: No Colour Baseline semantics
        val isNoColour = recipe.isNoColour || recipe.styleId == ColorStyleId.NO_COLOUR
        if (isNoColour) {
            messages.add(
                ResolutionMessage(
                    severity = MessageSeverity.INFO,
                    code = "NO_COLOUR_BYPASS",
                    title = "No Colour Baseline Active",
                    description = "Skipping automatic colour/fill stages (base fills, cel shadows, gradients, highlights, textures, finishing effects) while retaining full reconstruction/linework.",
                    suggestedFix = "Disable No Colour baseline if color fills are desired."
                )
            )
            return ColorStackResolverResult(
                isValid = true,
                resolvedStages = stages,
                activeStages = emptyList(), // All color stages bypassed
                bypassedStages = stages.filter { it.enabled },
                messages = messages,
                isNoColourActive = true,
                hasMonochromeNeonSemantic = false
            )
        }

        // Check 2: Filter enabled vs disabled stages
        val enabledStages = stages.filter { it.enabled }
        if (enabledStages.isEmpty()) {
            messages.add(
                ResolutionMessage(
                    severity = MessageSeverity.INFO,
                    code = "EMPTY_RECIPE",
                    title = "No Active Stages",
                    description = "Recipe has no active colour stages; output will render linework only.",
                    suggestedFix = "Enable at least one colour stage to generate artwork colors."
                )
            )
            return ColorStackResolverResult(
                isValid = true,
                resolvedStages = stages,
                activeStages = emptyList(),
                bypassedStages = emptyList(),
                messages = messages,
                isNoColourActive = false,
                hasMonochromeNeonSemantic = false
            )
        }

        // Check 3: Missing Base Fill dependency for Shading / Highlights
        val hasBaseFill = enabledStages.any { it.stageType == ColorStageType.BASE_FILL }
        val hasShadingOrHighlights = enabledStages.any {
            it.stageType == ColorStageType.CEL_SHADOW ||
                    it.stageType == ColorStageType.GRADIENT ||
                    it.stageType == ColorStageType.HIGHLIGHT
        }
        if (!hasBaseFill && hasShadingOrHighlights) {
            messages.add(
                ResolutionMessage(
                    severity = MessageSeverity.WARNING,
                    code = "MISSING_BASE_FILL",
                    title = "Shading Without Base Fill",
                    description = "Cel shadows, gradients, or highlights are active without an enabled Base Fill stage; shading will be composited over transparent canvas.",
                    suggestedFix = "Enable a Base Fill stage to provide an opaque chromatic base."
                )
            )
        }

        // Check 4: Duplicate Base Fills
        val baseFillStages = enabledStages.filter { it.stageType == ColorStageType.BASE_FILL }
        if (baseFillStages.size > 1) {
            val allHaveRegions = baseFillStages.all { !it.targetRegionIds.isNullOrEmpty() }
            if (!allHaveRegions) {
                messages.add(
                    ResolutionMessage(
                        severity = MessageSeverity.WARNING,
                        code = "DUPLICATE_BASE_FILL",
                        title = "Multiple Base Fill Stages",
                        description = "Multiple Base Fill stages are active without mutually exclusive region masks; lower base fill may be occluded by upper base fill.",
                        suggestedFix = "Assign specific non-overlapping region masks to each base fill, or keep only one base fill."
                    )
                )
            }
        }

        // Check 5: Monochrome + Neon Ordered Semantics
        var hasMonochromeNeon = false
        val monoIdx = enabledStages.indexOfFirst {
            it is PaletteTransformStageConfig && it.transformType == PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE
        }
        val neonIdx = enabledStages.indexOfFirst {
            it is FinishingEffectStageConfig && it.effectType == FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM
        }

        if (monoIdx != -1 && neonIdx != -1) {
            hasMonochromeNeon = true
            if (monoIdx < neonIdx) {
                // Monochrome precedes Neon: Desaturates base first, adds vibrant glowing neon on top
                messages.add(
                    ResolutionMessage(
                        severity = MessageSeverity.INFO,
                        code = "MONOCHROME_THEN_NEON_ORDERED",
                        title = "Monochrome -> Neon Ordered Semantics Active",
                        description = "Monochrome transform desaturates base fills and shading to grayscale first, and Neon finishing effect applies vibrant glowing bloom on top (Cyberpunk Noir aesthetic).",
                        suggestedFix = null
                    )
                )
            } else {
                // Neon precedes Monochrome: Monochrome wipes out neon color
                messages.add(
                    ResolutionMessage(
                        severity = MessageSeverity.WARNING,
                        code = "NEON_THEN_MONOCHROME_CONFLICT",
                        title = "Neon Precedes Monochrome Transform",
                        description = "Monochrome palette transform will desaturate the vibrant colors of the preceding Neon bloom stage. Reorder Monochrome before Neon to preserve glowing colors.",
                        affectedStageId = enabledStages[neonIdx].stageId,
                        suggestedFix = "Move Monochrome stage before Neon stage."
                    )
                )
            }
        }

        // Check 6: Conflicting Medium Textures
        val textureStages = enabledStages.filterIsInstance<MediumTextureStageConfig>()
        if (textureStages.size > 1) {
            val hasScreentone = textureStages.any { it.mediumType == MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS }
            val hasOil = textureStages.any { it.mediumType == MediumTextureStageConfig.MediumTextureType.OIL_IMPASTO }
            if (hasScreentone && hasOil) {
                messages.add(
                    ResolutionMessage(
                        severity = MessageSeverity.WARNING,
                        code = "CONFLICTING_MEDIUM_TEXTURES",
                        title = "Conflicting Medium Textures",
                        description = "Combining Manga Screentone dots with heavy Oil Impasto ridges may produce visual clutter and clashing texture artifacts.",
                        suggestedFix = "Select a single primary medium texture."
                    )
                )
            }
        }

        // Check 7: Stage Ordering vs Canonical
        var isCanonical = true
        var lastRank = -1
        for (stage in enabledStages) {
            val rank = CANONICAL_STAGE_ORDER.indexOf(stage.stageType)
            if (rank != -1) {
                if (rank < lastRank) {
                    isCanonical = false
                }
                lastRank = rank
            }
        }
        if (!isCanonical && monoIdx == -1) { // If not already flagged by special rule
            messages.add(
                ResolutionMessage(
                    severity = MessageSeverity.INFO,
                    code = "NON_CANONICAL_ORDER",
                    title = "Custom Stage Ordering Active",
                    description = "Stages are arranged in a custom execution order deviating from canonical (Base -> Shadow -> Gradient -> Highlight -> Texture -> Transform -> Effects).",
                    suggestedFix = "Use 'Auto-Order' if standard layering is desired."
                )
            )
        }

        return ColorStackResolverResult(
            isValid = true,
            resolvedStages = stages,
            activeStages = enabledStages,
            bypassedStages = stages.filter { !it.enabled },
            messages = messages,
            isNoColourActive = false,
            hasMonochromeNeonSemantic = hasMonochromeNeon
        )
    }

    /**
     * Reorders a list of stages into canonical execution order, ensuring that if both
     * Monochrome and Neon are present, Monochrome strictly precedes Neon.
     */
    fun autoReorderToCanonical(stages: List<ColorStageConfig>): List<ColorStageConfig> {
        return stages.sortedWith(Comparator { s1, s2 ->
            val rank1 = CANONICAL_STAGE_ORDER.indexOf(s1.stageType).let { if (it == -1) 999 else it }
            val rank2 = CANONICAL_STAGE_ORDER.indexOf(s2.stageType).let { if (it == -1) 999 else it }
            if (rank1 != rank2) {
                rank1.compareTo(rank2)
            } else {
                // Within same stage type, keep original order
                0
            }
        })
    }

    /**
     * Specifically detects if Neon precedes Monochrome and automatically reorders
     * them so Monochrome strictly precedes Neon, preserving vibrant neon glow over
     * desaturated grayscale base.
     */
    fun autoReorderMonochromeNeon(stages: List<ColorStageConfig>): List<ColorStageConfig> {
        val monoIdx = stages.indexOfFirst {
            it is PaletteTransformStageConfig && it.transformType == PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE
        }
        val neonIdx = stages.indexOfFirst {
            it is FinishingEffectStageConfig && it.effectType == FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM
        }

        if (monoIdx != -1 && neonIdx != -1 && neonIdx < monoIdx) {
            val mutable = stages.toMutableList()
            val neonStage = mutable.removeAt(neonIdx)
            val newMonoIdx = mutable.indexOfFirst {
                it is PaletteTransformStageConfig && it.transformType == PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE
            }
            mutable.add(newMonoIdx + 1, neonStage)
            return mutable
        }
        return stages
    }
}
