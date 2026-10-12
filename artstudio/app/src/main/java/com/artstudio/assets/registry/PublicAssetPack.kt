package com.artstudio.assets.registry

/**
 * Standard asset categories supported by Art Studio serverless Pro Asset architecture.
 */
enum class AssetCategory(val displayName: String) {
    BRUSH("Brushes"),
    TEXTURE("Textures"),
    FONT("Typography"),
    COMIC("Comic & Manga"),
    AI_MODEL("AI Models"),
    SAMPLE_PROJECT("Sample Projects")
}

/**
 * Supported container and asset binary formats.
 */
enum class AssetFormat(val extension: String) {
    ZIP(".zip"),
    KPP(".kpp"),
    MYB(".myb"),
    PNG(".png"),
    TTF(".ttf"),
    OTF(".otf"),
    TFLITE(".tflite"),
    ONNX(".onnx")
}

/**
 * Target domain specialization for optional AI neural network weights.
 */
enum class TargetDomain {
    LINE_ART,
    PHOTO,
    GENERAL
}

/**
 * Declarative description of a serverless, public downloadable asset pack.
 * Enforces zero proprietary server or Play Asset Delivery dependency.
 */
data class PublicAssetPack(
    val id: String,
    val displayName: String,
    val category: AssetCategory,
    val license: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val downloadUrls: List<String>,
    val fileSizeBytes: Long,
    val sha256: String,
    val format: AssetFormat,
    val targetDomain: TargetDomain? = null,
    val requiredRamBytes: Long = 0L,
    val previewUrl: String = "",
    val version: String = "1.0.0",
    val description: String = ""
) {
    init {
        require(id.isNotBlank()) { "Asset pack id cannot be blank" }
        require(displayName.isNotBlank()) { "Asset pack displayName cannot be blank" }
        require(downloadUrls.isNotEmpty()) { "Asset pack must declare at least one download mirror" }
        require(fileSizeBytes >= 0L) { "Asset pack size cannot be negative" }
    }
}
