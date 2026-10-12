package com.artstudio.cv.regionprofile.instruction

import com.artstudio.cv.regionprofile.RegionAnalysisProfile
import com.artstudio.cv.regionprofile.RegionProfileOverride
import com.artstudio.cv.regionprofile.RegionProfileResolver
import java.util.Locale

/**
 * Deterministic Natural Language Instruction Translator for Illustration Analysis.
 *
 * Translates freeform artist text descriptions into a constrained schema of visible,
 * editable analysis parameters.
 *
 * Security Invariant:
 * Strictly rule-based and deterministic. Never executes arbitrary model text or scripts.
 * Malicious injections, shell commands, and unrecognized requests are safely intercepted,
 * classified, and explained.
 */
object InstructionTranslator {

    // Malicious and command injection patterns
    private val PROHIBITED_COMMAND_PATTERNS = listOf(
        "rm -rf", "sudo", "cat /etc", "bash", "sh -c", "curl ", "wget ", "chmod ",
        "format c:", "reboot", "shutdown", "<script>", "javascript:", "eval(", "exec(",
        "system(", "runtime.getruntime", "ignore previous instructions", "forget all prior",
        "system prompt", "you are now", "jailbreak", "dan mode"
    )

    // Vague / ambiguous terms that do not specify concrete parameters
    private val VAGUE_TERMS = listOf(
        "make it better", "make it good", "enhance", "look nice", "pretty", "cool",
        "do your best", "fix it", "perfect", "magic", "auto"
    )

    /**
     * Translates a user instruction string into an [InstructionInterpretationRecord].
     *
     * @param instruction Freeform text description for the region.
     * @param regionId Identifier of the target reference region.
     * @param globalDefault Global baseline profile for inheritance.
     * @param modelMetadata Provenance audit record.
     */
    fun translate(
        instruction: String,
        regionId: String = "region_default",
        globalDefault: RegionAnalysisProfile = RegionAnalysisProfile.standard(),
        modelMetadata: ModelProviderMetadata = ModelProviderMetadata()
    ): InstructionInterpretationRecord {
        val trimmed = instruction.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Empty or Blank Check
        if (trimmed.isEmpty()) {
            return emptyInterpretationRecord(
                regionId = regionId,
                originalInstruction = instruction,
                globalDefault = globalDefault,
                modelMetadata = modelMetadata
            )
        }

        // 2. Security Screening: Check for Prohibited / Adversarial Commands
        val matchedProhibited = PROHIBITED_COMMAND_PATTERNS.filter { lower.contains(it) }
        if (matchedProhibited.isNotEmpty()) {
            return InstructionInterpretationRecord(
                regionId = regionId,
                originalInstruction = instruction,
                status = InterpretationStatus.MALICIOUS_OR_ADVERSARIAL,
                confidence = 0.0f,
                matchedIntents = emptyList(),
                unsupportedTokens = matchedProhibited,
                explanations = listOf(
                    "Security Alert: Prohibited command, script, or prompt injection pattern detected (${matchedProhibited.joinToString(", ")}). " +
                            "Arbitrary code execution is strictly prohibited. No analysis parameters were modified."
                ),
                conflictWarning = "Malicious or disallowed directives rejected for safety.",
                interpretedProfileOverride = RegionProfileOverride(),
                userOverrides = RegionProfileOverride(),
                effectiveResolvedProfile = RegionProfileResolver.resolve(globalDefault),
                modelMetadata = modelMetadata
            )
        }

        // 3. Match Supported Intent Clusters
        val matchedIntents = mutableListOf<MatchedIntentDetail>()
        val recognizedPhrases = mutableListOf<String>()

        // Intent: PRESERVE_TINY_FEATURES
        val tinyKeywords = listOf(
            "preserve tiny features", "tiny features", "tiny detail", "tiny details",
            "hairline", "hairlines", "eyelash", "eyelashes", "micro linework", "micro details",
            "small specks", "tiny dots", "delicate details", "delicate linework"
        )
        val matchedTiny = tinyKeywords.filter { lower.contains(it) }
        if (matchedTiny.isNotEmpty()) {
            recognizedPhrases.addAll(matchedTiny)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.PRESERVE_TINY_FEATURES,
                    matchedKeywords = matchedTiny,
                    confidence = 0.95f,
                    affectedParameters = mapOf(
                        "minFeatureSize" to "1.0 px",
                        "detailPreservation" to "0.90",
                        "edgeSensitivity" to "0.85"
                    ),
                    explanation = "Decreased minimum feature size to 1.0px and elevated edge sensitivity to retain fine hairlines and eyelashes."
                )
            )
        }

        // Intent: SEPARATE_NEIGHBOURING_CONTOURS
        val separateKeywords = listOf(
            "separate neighbouring contours", "separate neighboring contours", "separate contours",
            "separate borders", "separate colors", "split touching", "distinct boundaries",
            "do not merge lines", "isolated contours", "split adjacent"
        )
        val matchedSeparate = separateKeywords.filter { lower.contains(it) }
        if (matchedSeparate.isNotEmpty()) {
            recognizedPhrases.addAll(matchedSeparate)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.SEPARATE_NEIGHBOURING_CONTOURS,
                    matchedKeywords = matchedSeparate,
                    confidence = 0.92f,
                    affectedParameters = mapOf(
                        "boundaryMarginPx" to "6 px",
                        "gapClosingRadius" to "1 px",
                        "simplificationTolerance" to "0.8 px"
                    ),
                    explanation = "Expanded boundary transition margin and reduced gap bridging to keep adjacent color contours distinct."
                )
            )
        }

        // Intent: PRIORITISE_SMOOTH_CURVES
        val smoothKeywords = listOf(
            "prioritise smooth curves", "prioritize smooth curves", "smooth curves",
            "flowing curves", "organic curves", "fluid lines", "gentle strokes",
            "suppress jaggedness", "smooth outlines"
        )
        val matchedSmooth = smoothKeywords.filter { lower.contains(it) }
        if (matchedSmooth.isNotEmpty()) {
            recognizedPhrases.addAll(matchedSmooth)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.PRIORITISE_SMOOTH_CURVES,
                    matchedKeywords = matchedSmooth,
                    confidence = 0.90f,
                    affectedParameters = mapOf(
                        "curveFittingTolerance" to "3.0 px",
                        "simplificationTolerance" to "2.5 px",
                        "detailPreservation" to "0.30"
                    ),
                    explanation = "Increased curve fitting tolerance to 3.0px and RDP simplification to produce smooth flowing Bézier arcs."
                )
            )
        }

        // Intent: DETECT_FINE_TIPS
        val tipsKeywords = listOf(
            "detect fine tips", "fine tips", "sharp tips", "needle tips", "cusp corners",
            "angular corners", "sharp corners", "hair ends", "acute vertices", "pointed tips"
        )
        val matchedTips = tipsKeywords.filter { lower.contains(it) }
        if (matchedTips.isNotEmpty()) {
            recognizedPhrases.addAll(matchedTips)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.DETECT_FINE_TIPS,
                    matchedKeywords = matchedTips,
                    confidence = 0.94f,
                    affectedParameters = mapOf(
                        "detailPreservation" to "0.95",
                        "curveFittingTolerance" to "0.5 px",
                        "minFeatureSize" to "1.2 px"
                    ),
                    explanation = "Elevated detail preservation to 0.95 and lowered fitting error to preserve acute angular tips without roundoff."
                )
            )
        }

        // Intent: CLOSE_LINE_GAPS
        val gapsKeywords = listOf(
            "close line gaps", "close gaps", "bridge broken strokes", "bridge breaks",
            "heal micro gaps", "connect broken lines", "fill gaps", "close openings"
        )
        val matchedGaps = gapsKeywords.filter { lower.contains(it) }
        if (matchedGaps.isNotEmpty()) {
            recognizedPhrases.addAll(matchedGaps)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.CLOSE_LINE_GAPS,
                    matchedKeywords = matchedGaps,
                    confidence = 0.91f,
                    affectedParameters = mapOf(
                        "gapClosingRadius" to "4 px",
                        "reviewSensitivity" to "0.70"
                    ),
                    explanation = "Set gap closing radius to 4px to connect proximate stroke endpoints into enclosed contours."
                )
            )
        }

        // Intent: HIGH_DETAIL_OVERALL
        val highKeywords = listOf(
            "high detail", "maximum detail", "extra passes", "intricate analysis",
            "fine precision", "multi pass", "maximum precision"
        )
        val matchedHigh = highKeywords.filter { lower.contains(it) }
        if (matchedHigh.isNotEmpty()) {
            recognizedPhrases.addAll(matchedHigh)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.HIGH_DETAIL_OVERALL,
                    matchedKeywords = matchedHigh,
                    confidence = 0.88f,
                    affectedParameters = mapOf(
                        "additionalPasses" to "+2 passes",
                        "edgeSensitivity" to "0.90",
                        "minFeatureSize" to "1.5 px"
                    ),
                    explanation = "Enabled 2 additional refinement passes and boosted edge sensitivity to capture intricate focal details."
                )
            )
        }

        // Intent: CLEAN_NOISE_REMOVAL
        val noiseKeywords = listOf(
            "clean noise", "remove noise", "despeckle", "filter dust", "filter specks",
            "clean strokes", "suppress texture", "denoise"
        )
        val matchedNoise = noiseKeywords.filter { lower.contains(it) }
        if (matchedNoise.isNotEmpty()) {
            recognizedPhrases.addAll(matchedNoise)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.CLEAN_NOISE_REMOVAL,
                    matchedKeywords = matchedNoise,
                    confidence = 0.89f,
                    affectedParameters = mapOf(
                        "minFeatureSize" to "6.0 px",
                        "simplificationTolerance" to "2.0 px",
                        "edgeSensitivity" to "0.40"
                    ),
                    explanation = "Raised minimum feature size to 6.0px to filter background paper grain and stray dust specks."
                )
            )
        }

        // Intent: BROAD_FLAT_OUTLINES
        val broadKeywords = listOf(
            "broad flat outlines", "broad strokes", "flat regions", "comic flats",
            "thick outlines", "posterize", "cel flats"
        )
        val matchedBroad = broadKeywords.filter { lower.contains(it) }
        if (matchedBroad.isNotEmpty()) {
            recognizedPhrases.addAll(matchedBroad)
            matchedIntents.add(
                MatchedIntentDetail(
                    intent = UserAnalysisIntent.BROAD_FLAT_OUTLINES,
                    matchedKeywords = matchedBroad,
                    confidence = 0.87f,
                    affectedParameters = mapOf(
                        "minFeatureSize" to "10.0 px",
                        "curveFittingTolerance" to "3.5 px",
                        "simplificationTolerance" to "3.0 px"
                    ),
                    explanation = "Applied aggressive simplification and 10px minimum feature size for clean comic book flat fills."
                )
            )
        }

        // 4. Handle Vague or Empty Matches
        val matchedVague = VAGUE_TERMS.filter { lower.contains(it) }
        if (matchedIntents.isEmpty()) {
            val isVague = matchedVague.isNotEmpty()
            val explanation = if (isVague) {
                "Vague request: Phrases like '${matchedVague.joinToString(", ")}' do not specify concrete analysis parameters. " +
                        "Try specific directives such as 'preserve tiny features', 'prioritise smooth curves', or 'detect fine tips'."
            } else {
                "Unsupported request: No recognized illustration analysis directives found in text. " +
                        "Supported directives include: 'preserve tiny features', 'separate neighbouring contours', 'prioritise smooth curves', 'detect fine tips', 'close line gaps', 'high detail', etc."
            }

            return InstructionInterpretationRecord(
                regionId = regionId,
                originalInstruction = instruction,
                status = if (isVague) InterpretationStatus.AMBIGUOUS else InterpretationStatus.UNSUPPORTED,
                confidence = if (isVague) 0.35f else 0.0f,
                matchedIntents = emptyList(),
                unsupportedTokens = if (isVague) matchedVague else listOf(instruction),
                explanations = listOf(explanation),
                conflictWarning = if (isVague) "Vague phrasing requires manual parameter selection." else null,
                interpretedProfileOverride = RegionProfileOverride(),
                userOverrides = RegionProfileOverride(),
                effectiveResolvedProfile = RegionProfileResolver.resolve(globalDefault),
                modelMetadata = modelMetadata
            )
        }

        // 5. Detect Conflicting Directives & Ambiguity
        var conflictWarning: String? = null
        var isAmbiguous = false

        val hasSmooth = matchedIntents.any { it.intent == UserAnalysisIntent.PRIORITISE_SMOOTH_CURVES }
        val hasTips = matchedIntents.any { it.intent == UserAnalysisIntent.DETECT_FINE_TIPS }
        val hasBroad = matchedIntents.any { it.intent == UserAnalysisIntent.BROAD_FLAT_OUTLINES }
        val hasTiny = matchedIntents.any { it.intent == UserAnalysisIntent.PRESERVE_TINY_FEATURES }

        if (hasSmooth && hasTips) {
            isAmbiguous = true
            conflictWarning = "Ambiguity detected: 'Smooth curves' demands curve smoothing while 'Fine tips' demands sharp corner preservation. " +
                    "A balanced compromise was applied; adjust sliders below to prioritize."
        } else if (hasBroad && hasTiny) {
            isAmbiguous = true
            conflictWarning = "Ambiguity detected: 'Broad flat outlines' discards small features while 'Preserve tiny features' demands micro-detail retention. " +
                    "Review parameters below."
        } else if (matchedVague.isNotEmpty()) {
            isAmbiguous = true
            conflictWarning = "Partially ambiguous: Contained general vague phrasing alongside recognized directives."
        }

        // 6. Synthesize Overrides from Matched Intents
        var minFeatureSize: Float? = null
        var edgeSensitivity: Float? = null
        var curveFittingTolerance: Float? = null
        var simplificationTolerance: Float? = null
        var gapClosingRadius: Int? = null
        var detailPreservation: Float? = null
        var reviewSensitivity: Float? = null
        var boundaryMarginPx: Int? = null
        var additionalPasses: Int? = null

        for (m in matchedIntents) {
            when (m.intent) {
                UserAnalysisIntent.PRESERVE_TINY_FEATURES -> {
                    minFeatureSize = minOf(minFeatureSize ?: 1.0f, 1.0f)
                    detailPreservation = maxOf(detailPreservation ?: 0.90f, 0.90f)
                    edgeSensitivity = maxOf(edgeSensitivity ?: 0.85f, 0.85f)
                }
                UserAnalysisIntent.SEPARATE_NEIGHBOURING_CONTOURS -> {
                    boundaryMarginPx = maxOf(boundaryMarginPx ?: 6, 6)
                    gapClosingRadius = minOf(gapClosingRadius ?: 1, 1)
                    simplificationTolerance = minOf(simplificationTolerance ?: 0.8f, 0.8f)
                }
                UserAnalysisIntent.PRIORITISE_SMOOTH_CURVES -> {
                    curveFittingTolerance = maxOf(curveFittingTolerance ?: 3.0f, 3.0f)
                    simplificationTolerance = maxOf(simplificationTolerance ?: 2.5f, 2.5f)
                    detailPreservation = if (hasTips) 0.60f else minOf(detailPreservation ?: 0.30f, 0.30f)
                }
                UserAnalysisIntent.DETECT_FINE_TIPS -> {
                    detailPreservation = if (hasSmooth) 0.60f else maxOf(detailPreservation ?: 0.95f, 0.95f)
                    curveFittingTolerance = if (hasSmooth) 1.5f else minOf(curveFittingTolerance ?: 0.5f, 0.5f)
                    minFeatureSize = minOf(minFeatureSize ?: 1.2f, 1.2f)
                }
                UserAnalysisIntent.CLOSE_LINE_GAPS -> {
                    gapClosingRadius = maxOf(gapClosingRadius ?: 4, 4)
                    reviewSensitivity = maxOf(reviewSensitivity ?: 0.70f, 0.70f)
                }
                UserAnalysisIntent.HIGH_DETAIL_OVERALL -> {
                    additionalPasses = maxOf(additionalPasses ?: 2, 2)
                    edgeSensitivity = maxOf(edgeSensitivity ?: 0.90f, 0.90f)
                    minFeatureSize = minOf(minFeatureSize ?: 1.5f, 1.5f)
                }
                UserAnalysisIntent.CLEAN_NOISE_REMOVAL -> {
                    minFeatureSize = maxOf(minFeatureSize ?: 6.0f, 6.0f)
                    simplificationTolerance = maxOf(simplificationTolerance ?: 2.0f, 2.0f)
                    edgeSensitivity = minOf(edgeSensitivity ?: 0.40f, 0.40f)
                }
                UserAnalysisIntent.BROAD_FLAT_OUTLINES -> {
                    minFeatureSize = maxOf(minFeatureSize ?: 10.0f, 10.0f)
                    curveFittingTolerance = maxOf(curveFittingTolerance ?: 3.5f, 3.5f)
                    simplificationTolerance = maxOf(simplificationTolerance ?: 3.0f, 3.0f)
                }
            }
        }

        val interpretedOverride = RegionProfileOverride(
            minFeatureSize = minFeatureSize,
            edgeSensitivity = edgeSensitivity,
            curveFittingTolerance = curveFittingTolerance,
            simplificationTolerance = simplificationTolerance,
            gapClosingRadius = gapClosingRadius,
            detailPreservation = detailPreservation,
            reviewSensitivity = reviewSensitivity,
            boundaryMarginPx = boundaryMarginPx,
            additionalPasses = additionalPasses
        )

        val effectiveResolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            override = interpretedOverride
        )

        val avgConfidence = matchedIntents.map { it.confidence }.average().toFloat()
        val finalStatus = if (isAmbiguous) InterpretationStatus.AMBIGUOUS else InterpretationStatus.CONFIDENT

        return InstructionInterpretationRecord(
            regionId = regionId,
            originalInstruction = instruction,
            status = finalStatus,
            confidence = avgConfidence,
            matchedIntents = matchedIntents,
            unsupportedTokens = emptyList(),
            explanations = matchedIntents.map { it.explanation },
            conflictWarning = conflictWarning,
            interpretedProfileOverride = interpretedOverride,
            userOverrides = RegionProfileOverride(),
            effectiveResolvedProfile = effectiveResolved,
            modelMetadata = modelMetadata
        )
    }

    private fun emptyInterpretationRecord(
        regionId: String,
        originalInstruction: String,
        globalDefault: RegionAnalysisProfile,
        modelMetadata: ModelProviderMetadata
    ): InstructionInterpretationRecord {
        return InstructionInterpretationRecord(
            regionId = regionId,
            originalInstruction = originalInstruction,
            status = InterpretationStatus.UNSUPPORTED,
            confidence = 0.0f,
            matchedIntents = emptyList(),
            unsupportedTokens = listOf("<empty>"),
            explanations = listOf("Instruction text is empty. Using global baseline profile."),
            conflictWarning = null,
            interpretedProfileOverride = RegionProfileOverride(),
            userOverrides = RegionProfileOverride(),
            effectiveResolvedProfile = RegionProfileResolver.resolve(globalDefault),
            modelMetadata = modelMetadata
        )
    }
}
