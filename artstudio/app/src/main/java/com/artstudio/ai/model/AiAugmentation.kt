package com.artstudio.ai.model

/**
 * Provenance audit container capturing the specific on-device AI model invocation details
 * that produced or refined a region mask, style pass, or super-resolution reference.
 *
 * Persisted across native document save/reopen to ensure reproducible reanalysis.
 */
data class AiAugmentation(
    val modelId: String,
    val modelName: String,
    val modelSha256: String,
    val inferenceLatencyMs: Long,
    val confidence: Float,
    val semanticTag: RegionSemanticTag = RegionSemanticTag.UNKNOWN,
    val promptModality: PromptModality = PromptModality.AUTOMATIC,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Serializes augmentation metadata to a key-value dictionary for JSON serialization.
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["modelId"] = modelId
        map["modelName"] = modelName
        map["modelSha256"] = modelSha256
        map["inferenceLatencyMs"] = inferenceLatencyMs
        map["confidence"] = confidence
        map["semanticTag"] = semanticTag.name
        map["promptModality"] = promptModality.name
        map["timestampMs"] = timestampMs
        return map
    }

    companion object {
        /**
         * Reconstructs an [AiAugmentation] from a serialized dictionary.
         */
        fun fromMap(map: Map<String, Any?>): AiAugmentation {
            return AiAugmentation(
                modelId = map["modelId"]?.toString() ?: "",
                modelName = map["modelName"]?.toString() ?: "",
                modelSha256 = map["modelSha256"]?.toString() ?: "",
                inferenceLatencyMs = (map["inferenceLatencyMs"] as? Number)?.toLong() ?: 0L,
                confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.5f,
                semanticTag = runCatching { RegionSemanticTag.valueOf(map["semanticTag"]?.toString() ?: "") }
                    .getOrDefault(RegionSemanticTag.UNKNOWN),
                promptModality = runCatching { PromptModality.valueOf(map["promptModality"]?.toString() ?: "") }
                    .getOrDefault(PromptModality.AUTOMATIC),
                timestampMs = (map["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
