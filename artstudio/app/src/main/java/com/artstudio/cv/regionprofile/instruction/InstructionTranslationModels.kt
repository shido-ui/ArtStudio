package com.artstudio.cv.regionprofile.instruction

import com.artstudio.cv.regionprofile.RegionAnalysisProfile
import com.artstudio.cv.regionprofile.RegionProfileOverride
import com.artstudio.cv.regionprofile.RegionProfileResolver
import com.artstudio.cv.regionprofile.ResolvedRegionProfile
import java.util.UUID

/**
 * Standard semantic analysis intents recognized from user text instructions.
 */
enum class UserAnalysisIntent(val displayName: String, val description: String) {
    PRESERVE_TINY_FEATURES(
        "Preserve Tiny Features",
        "Retains micro-linework, eyelashes, tiny dots, and high-frequency details by reducing minimum feature size"
    ),
    SEPARATE_NEIGHBOURING_CONTOURS(
        "Separate Neighbouring Contours",
        "Prevents adjacent color regions or touching strokes from merging into a single contour"
    ),
    PRIORITISE_SMOOTH_CURVES(
        "Prioritise Smooth Curves",
        "Fits fluid, continuous Bézier arcs with higher curve tolerance and angular smoothing"
    ),
    DETECT_FINE_TIPS(
        "Detect Fine Tips",
        "Preserves sharp acute vertices, needle tips, and corner cusps without roundoff"
    ),
    CLOSE_LINE_GAPS(
        "Close Line Gaps",
        "Bridges micro-breaks and stroke discontinuities to form enclosed contours"
    ),
    HIGH_DETAIL_OVERALL(
        "High Detail Multi-Pass",
        "Executes multi-scale refinement passes with heightened edge sensitivity across the region"
    ),
    CLEAN_NOISE_REMOVAL(
        "Clean Noise & Despeckle",
        "Filters dust, paper grain, and stray pixel specks using increased feature thresholds"
    ),
    BROAD_FLAT_OUTLINES(
        "Broad Flat Outlines",
        "Simplifies geometry into broad, clean strokes suitable for comic flats and cel fills"
    )
}

/**
 * Classification status for an instruction translation pass.
 */
enum class InterpretationStatus(val displayName: String) {
    /** Clear mapping with high confidence and no irreconcilable conflicts */
    CONFIDENT("Confident Interpretation"),

    /** Text contains conflicting directives or vague phrasing requiring user alignment */
    AMBIGUOUS("Ambiguous Directives"),

    /** Text does not contain recognized illustration analysis terminology */
    UNSUPPORTED("Unsupported Request"),

    /** Input attempted command execution, script injection, or prompt override */
    MALICIOUS_OR_ADVERSARIAL("Rejected (Adversarial / Prohibited)")
}

/**
 * Detailed breakdown of an individual matched intent within an instruction.
 */
data class MatchedIntentDetail(
    val intent: UserAnalysisIntent,
    val matchedKeywords: List<String>,
    val confidence: Float,
    val affectedParameters: Map<String, String>,
    val explanation: String
)

/**
 * Audit provenance metadata recording the translator or model engine.
 */
data class ModelProviderMetadata(
    val providerName: String = "ArtStudio-DeterministicLexicon",
    val modelIdentifier: String = "rule-lexicon-v1",
    val modelVersion: String = "1.0.0",
    val isOfflineDeterministic: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, String> = mapOf(
        "providerName" to providerName,
        "modelIdentifier" to modelIdentifier,
        "modelVersion" to modelVersion,
        "isOfflineDeterministic" to isOfflineDeterministic.toString(),
        "timestamp" to timestamp.toString()
    )

    companion object {
        fun fromMap(map: Map<String, String>): ModelProviderMetadata = ModelProviderMetadata(
            providerName = map["providerName"] ?: "ArtStudio-DeterministicLexicon",
            modelIdentifier = map["modelIdentifier"] ?: "rule-lexicon-v1",
            modelVersion = map["modelVersion"] ?: "1.0.0",
            isOfflineDeterministic = map["isOfflineDeterministic"]?.toBooleanStrictOrNull() ?: true,
            timestamp = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }
}

/**
 * Comprehensive, reproducible record of an instruction translation lifecycle.
 *
 * Persists the raw user text, matched intents, interpreted settings, user overrides,
 * effective resolved profile, and provider provenance.
 */
data class InstructionInterpretationRecord(
    val recordId: String = UUID.randomUUID().toString(),
    val regionId: String,
    val originalInstruction: String,
    val status: InterpretationStatus,
    val confidence: Float,
    val matchedIntents: List<MatchedIntentDetail>,
    val unsupportedTokens: List<String>,
    val explanations: List<String>,
    val conflictWarning: String? = null,
    val interpretedProfileOverride: RegionProfileOverride,
    val userOverrides: RegionProfileOverride = RegionProfileOverride(),
    val effectiveResolvedProfile: ResolvedRegionProfile,
    val modelMetadata: ModelProviderMetadata = ModelProviderMetadata(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Re-computes the effective resolved profile after user modifies manual overrides.
     */
    fun withUserOverrides(
        newOverrides: RegionProfileOverride,
        globalDefault: RegionAnalysisProfile = RegionAnalysisProfile.standard()
    ): InstructionInterpretationRecord {
        // Merge interpreted overrides with user manual overrides (user overrides take precedence)
        val combined = RegionProfileOverride(
            minFeatureSize = newOverrides.minFeatureSize ?: interpretedProfileOverride.minFeatureSize,
            edgeSensitivity = newOverrides.edgeSensitivity ?: interpretedProfileOverride.edgeSensitivity,
            curveFittingTolerance = newOverrides.curveFittingTolerance ?: interpretedProfileOverride.curveFittingTolerance,
            simplificationTolerance = newOverrides.simplificationTolerance ?: interpretedProfileOverride.simplificationTolerance,
            gapClosingRadius = newOverrides.gapClosingRadius ?: interpretedProfileOverride.gapClosingRadius,
            detailPreservation = newOverrides.detailPreservation ?: interpretedProfileOverride.detailPreservation,
            reviewSensitivity = newOverrides.reviewSensitivity ?: interpretedProfileOverride.reviewSensitivity,
            boundaryMarginPx = newOverrides.boundaryMarginPx ?: interpretedProfileOverride.boundaryMarginPx,
            additionalPasses = newOverrides.additionalPasses ?: interpretedProfileOverride.additionalPasses
        )

        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            override = combined
        )

        return copy(
            userOverrides = newOverrides,
            effectiveResolvedProfile = resolved,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["recordId"] = recordId
        map["regionId"] = regionId
        map["originalInstruction"] = originalInstruction
        map["status"] = status.name
        map["confidence"] = confidence.toString()
        map["conflictWarning"] = conflictWarning ?: ""
        map["createdAt"] = createdAt.toString()
        map["updatedAt"] = updatedAt.toString()

        // Serialize overrides
        interpretedProfileOverride.toMap().forEach { (k, v) ->
            map["interpreted_$k"] = v
        }
        userOverrides.toMap().forEach { (k, v) ->
            map["user_$k"] = v
        }

        // Serialize model metadata
        modelMetadata.toMap().forEach { (k, v) ->
            map["meta_$k"] = v
        }

        // Comma-separated summaries
        map["matchedIntentNames"] = matchedIntents.joinToString(",") { it.intent.name }
        map["unsupportedTokens"] = unsupportedTokens.joinToString(",")
        map["explanations"] = explanations.joinToString(" || ")

        return map
    }

    companion object {
        fun fromMap(
            map: Map<String, String>,
            globalDefault: RegionAnalysisProfile = RegionAnalysisProfile.standard()
        ): InstructionInterpretationRecord {
            val recordId = map["recordId"] ?: UUID.randomUUID().toString()
            val regionId = map["regionId"] ?: ""
            val originalInstruction = map["originalInstruction"] ?: ""
            val status = runCatching { InterpretationStatus.valueOf(map["status"] ?: "") }
                .getOrDefault(InterpretationStatus.UNSUPPORTED)
            val confidence = map["confidence"]?.toFloatOrNull() ?: 0.0f
            val conflictWarning = map["conflictWarning"]?.takeIf { it.isNotBlank() }
            val createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            val updatedAt = map["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()

            // Reconstruct overrides
            val interpretedMap = map.filterKeys { it.startsWith("interpreted_") }
                .mapKeys { it.key.removePrefix("interpreted_") }
            val interpretedOverride = RegionProfileOverride.fromMap(interpretedMap)

            val userMap = map.filterKeys { it.startsWith("user_") }
                .mapKeys { it.key.removePrefix("user_") }
            val userOverride = RegionProfileOverride.fromMap(userMap)

            // Reconstruct metadata
            val metaMap = map.filterKeys { it.startsWith("meta_") }
                .mapKeys { it.key.removePrefix("meta_") }
            val modelMeta = ModelProviderMetadata.fromMap(metaMap)

            // Reconstruct lists
            val matchedNames = map["matchedIntentNames"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val matchedIntents = matchedNames.mapNotNull { name ->
                runCatching { UserAnalysisIntent.valueOf(name) }.getOrNull()?.let { intent ->
                    MatchedIntentDetail(
                        intent = intent,
                        matchedKeywords = emptyList(),
                        confidence = confidence,
                        affectedParameters = emptyMap(),
                        explanation = intent.description
                    )
                }
            }

            val unsupported = map["unsupportedTokens"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val explanations = map["explanations"]?.split(" || ")?.filter { it.isNotBlank() } ?: emptyList()

            // Resolve effective profile
            val combined = RegionProfileOverride(
                minFeatureSize = userOverride.minFeatureSize ?: interpretedOverride.minFeatureSize,
                edgeSensitivity = userOverride.edgeSensitivity ?: interpretedOverride.edgeSensitivity,
                curveFittingTolerance = userOverride.curveFittingTolerance ?: interpretedOverride.curveFittingTolerance,
                simplificationTolerance = userOverride.simplificationTolerance ?: interpretedOverride.simplificationTolerance,
                gapClosingRadius = userOverride.gapClosingRadius ?: interpretedOverride.gapClosingRadius,
                detailPreservation = userOverride.detailPreservation ?: interpretedOverride.detailPreservation,
                reviewSensitivity = userOverride.reviewSensitivity ?: interpretedOverride.reviewSensitivity,
                boundaryMarginPx = userOverride.boundaryMarginPx ?: interpretedOverride.boundaryMarginPx,
                additionalPasses = userOverride.additionalPasses ?: interpretedOverride.additionalPasses
            )

            val effective = RegionProfileResolver.resolve(globalDefault, override = combined)

            return InstructionInterpretationRecord(
                recordId = recordId,
                regionId = regionId,
                originalInstruction = originalInstruction,
                status = status,
                confidence = confidence,
                matchedIntents = matchedIntents,
                unsupportedTokens = unsupported,
                explanations = explanations,
                conflictWarning = conflictWarning,
                interpretedProfileOverride = interpretedOverride,
                userOverrides = userOverride,
                effectiveResolvedProfile = effective,
                modelMetadata = modelMeta,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
