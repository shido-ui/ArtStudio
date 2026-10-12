package com.artstudio.ai.model

/**
 * Functional categories for optional on-device AI models.
 */
enum class ModelCategory(val displayName: String) {
    SEGMENTATION("Region Segmentation"),
    STYLE_TRANSFER("Style Transfer"),
    SUPER_RESOLUTION("Super-Resolution"),
    CLASSIFICATION("Classification"),
    SEMANTIC_UNDERSTANDING("Semantic Understanding")
}

/**
 * Quantization formats for model neural weights.
 */
enum class QuantizationType(val displayName: String) {
    FP32("32-bit Float"),
    FP16("16-bit Half-Float"),
    INT8("8-bit Integer"),
    NONE("No Weights (Heuristic)")
}

/**
 * Target domain data distribution that the model was trained on.
 */
enum class TargetDomain(val displayName: String) {
    ANIME_LINE_ART("Anime & Illustration Line Art"),
    ANIME_FLAT_COLOUR("Anime & Illustration Flat Colour"),
    PHOTO("Natural Photography & Real-World Scenes"),
    GENERAL("General Multi-Domain")
}

/**
 * Interactive and automatic prompting modalities supported by the model.
 */
enum class PromptModality(val displayName: String) {
    AUTOMATIC("Automatic Full-Canvas"),
    POINT("Interactive Point Tap"),
    BOX("Interactive Bounding Box"),
    TEXT("Natural Language Text Prompt"),
    MASK("Binary Input Mask Refinement")
}

/**
 * Execution runtime environment required by the model weights.
 */
enum class ModelRuntimeType(val displayName: String) {
    TFLITE("TensorFlow Lite"),
    ONNX("ONNX Runtime Mobile"),
    CUSTOM_HEURISTIC("First-Party Pure-Kotlin Heuristic")
}

/**
 * High-level semantic role tags assigned to segmented illustration regions.
 */
enum class RegionSemanticTag(val displayName: String, val defaultColorHex: String) {
    SKIN("Skin", "#FFDFD3"),
    HAIR("Hair", "#7D5260"),
    EYES("Eyes", "#00E5FF"),
    CLOTHING("Clothing", "#4A6572"),
    BACKGROUND("Background", "#E0E0E0"),
    HIGHLIGHT("Highlight", "#FFFFFF"),
    SHADOW("Shadow", "#232F34"),
    UNKNOWN("Unassigned / Generic", "#808080")
}

/**
 * Provenance source indicating how a region mask or path was derived.
 */
enum class RegionSource {
    CLASSICAL_CV,
    AI_MODEL,
    USER_MANUAL,
    HYBRID
}

/**
 * Complete immutable metadata descriptor for an optional on-device AI model candidate.
 *
 * In strict alignment with ADR-004 and ADR-050:
 * - No model weights may ever be bundled in the APK.
 * - Every model is strictly optional; zero models are required.
 * - Contains comprehensive licensing, download sizing, memory budgets, and domain warnings.
 */
data class AiModelDescriptor(
    val id: String,
    val displayName: String,
    val category: ModelCategory,
    val description: String,
    val weightsSizeBytes: Long,
    val runtimeLibSizeBytes: Long = 0L,
    val licenseId: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val sha256: String,
    val inputResolution: String = "512x512",
    val quantization: QuantizationType = QuantizationType.INT8,
    val targetDomain: TargetDomain,
    val expectedRamPeakBytes: Long,
    val expectedInferenceMs: Long,
    val supportedPromptModalities: List<PromptModality>,
    val minAndroidApi: Int = 26,
    val requiredAbi: String = "all",
    val runtimeType: ModelRuntimeType = ModelRuntimeType.TFLITE,
    val isBundled: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Model ID must not be blank" }
        require(displayName.isNotBlank()) { "Model display name must not be blank" }
        require(licenseId.isNotBlank()) { "License ID must not be blank" }
        require(sha256.isNotBlank()) { "SHA-256 must not be blank" }
        require(weightsSizeBytes >= 0L) { "weightsSizeBytes must be non-negative" }
        require(expectedRamPeakBytes >= 0L) { "expectedRamPeakBytes must be non-negative" }
        require(expectedInferenceMs >= 0L) { "expectedInferenceMs must be non-negative" }
        require(minAndroidApi >= 21) { "minAndroidApi must be at least 21" }

        // Security & Architectural Invariant: Never allow neural models to be marked as bundled
        if (runtimeType != ModelRuntimeType.CUSTOM_HEURISTIC) {
            require(!isBundled) {
                "Model $id: Neural weights must NEVER be bundled in the APK per ADR-050."
            }
        }
    }

    /**
     * True if the model was trained exclusively on natural photography and may produce
     * bleeding or fragmentation artifacts when applied to fine anime line art.
     */
    val isPhotoOnly: Boolean
        get() = targetDomain == TargetDomain.PHOTO

    /**
     * Warning message generated if this model is selected for line art illustration.
     */
    val domainWarning: String?
        get() = if (isPhotoOnly) {
            "Warning: '$displayName' was trained on natural photos. It may bleed across thin hand-drawn line art and fracture hair strands."
        } else null

    /**
     * Formatted string of weights download size in megabytes.
     */
    val formattedWeightsSize: String
        get() = if (weightsSizeBytes == 0L) {
            "0 MB (Heuristic, Built-in)"
        } else {
            String.format("%.1f MB", weightsSizeBytes / (1024.0 * 1024.0))
        }

    /**
     * Checks compatibility with the host runtime environment.
     */
    fun isCompatibleWithDevice(api: Int, currentAbi: String): Boolean {
        if (api < minAndroidApi) return false
        if (requiredAbi == "all") return true
        return requiredAbi.equals(currentAbi, ignoreCase = true)
    }

    /**
     * Serializes descriptor metadata to a key-value dictionary for JSON serialization.
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["displayName"] = displayName
        map["category"] = category.name
        map["description"] = description
        map["weightsSizeBytes"] = weightsSizeBytes
        map["runtimeLibSizeBytes"] = runtimeLibSizeBytes
        map["licenseId"] = licenseId
        map["licenseUrl"] = licenseUrl
        map["sourceUrl"] = sourceUrl
        map["sha256"] = sha256
        map["inputResolution"] = inputResolution
        map["quantization"] = quantization.name
        map["targetDomain"] = targetDomain.name
        map["expectedRamPeakBytes"] = expectedRamPeakBytes
        map["expectedInferenceMs"] = expectedInferenceMs
        map["supportedPromptModalities"] = supportedPromptModalities.map { it.name }
        map["minAndroidApi"] = minAndroidApi
        map["requiredAbi"] = requiredAbi
        map["runtimeType"] = runtimeType.name
        map["isBundled"] = isBundled
        return map
    }

    companion object {
        /**
         * Reconstructs an [AiModelDescriptor] from a serialized dictionary.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): AiModelDescriptor {
            val modalities = (map["supportedPromptModalities"] as? List<*>)?.mapNotNull {
                runCatching { PromptModality.valueOf(it.toString()) }.getOrNull()
            } ?: listOf(PromptModality.AUTOMATIC)

            return AiModelDescriptor(
                id = map["id"]?.toString() ?: "",
                displayName = map["displayName"]?.toString() ?: "",
                category = runCatching { ModelCategory.valueOf(map["category"]?.toString() ?: "") }
                    .getOrDefault(ModelCategory.SEGMENTATION),
                description = map["description"]?.toString() ?: "",
                weightsSizeBytes = (map["weightsSizeBytes"] as? Number)?.toLong() ?: 0L,
                runtimeLibSizeBytes = (map["runtimeLibSizeBytes"] as? Number)?.toLong() ?: 0L,
                licenseId = map["licenseId"]?.toString() ?: "Apache-2.0",
                licenseUrl = map["licenseUrl"]?.toString() ?: "",
                sourceUrl = map["sourceUrl"]?.toString() ?: "",
                sha256 = map["sha256"]?.toString() ?: "",
                inputResolution = map["inputResolution"]?.toString() ?: "512x512",
                quantization = runCatching { QuantizationType.valueOf(map["quantization"]?.toString() ?: "") }
                    .getOrDefault(QuantizationType.INT8),
                targetDomain = runCatching { TargetDomain.valueOf(map["targetDomain"]?.toString() ?: "") }
                    .getOrDefault(TargetDomain.GENERAL),
                expectedRamPeakBytes = (map["expectedRamPeakBytes"] as? Number)?.toLong() ?: 50_000_000L,
                expectedInferenceMs = (map["expectedInferenceMs"] as? Number)?.toLong() ?: 50L,
                supportedPromptModalities = modalities,
                minAndroidApi = (map["minAndroidApi"] as? Number)?.toInt() ?: 26,
                requiredAbi = map["requiredAbi"]?.toString() ?: "all",
                runtimeType = runCatching { ModelRuntimeType.valueOf(map["runtimeType"]?.toString() ?: "") }
                    .getOrDefault(ModelRuntimeType.TFLITE),
                isBundled = (map["isBundled"] as? Boolean) ?: false
            )
        }
    }
}
