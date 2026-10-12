package com.artstudio.ai.model

import java.io.File

/**
 * Authoritative registry managing the optional AI model catalog, on-demand downloads,
 * memory-bounded runtime adapters, and local storage accounting.
 *
 * In strict alignment with ADR-049 and ADR-050:
 * - Supplements, but NEVER replaces, the classical CV pipeline.
 * - Every model is optional; no model is ever hardcoded as required.
 * - Enforces zero bundled neural weights in the APK.
 * - Provides immediate fallback to [SemanticHeuristicAssist] when no neural model is downloaded.
 */
class AiModelRegistry(
    val downloader: AiModelDownloader
) {
    /**
     * Map of registered model descriptors. Initialized from curated catalog.
     */
    private val modelCatalog = mutableMapOf<String, AiModelDescriptor>()

    /**
     * Active loaded adapter in memory, or null if no neural model is resident.
     */
    @Volatile
    private var activeAdapter: AiModelAdapter? = null

    /**
     * Pure-Kotlin heuristic fallback adapter, always available and resident (0 bytes weights).
     */
    val heuristicAssist = SemanticHeuristicAssist()

    init {
        // Populate catalog from Curated Model Catalog
        AiModelCatalog.CURATED_CATALOG.forEach { descriptor ->
            modelCatalog[descriptor.id] = descriptor
        }
    }

    /**
     * Returns all model entries in the catalog.
     */
    fun getAllModels(): List<AiModelDescriptor> = modelCatalog.values.toList()

    /**
     * Retrieves a model descriptor by its unique ID.
     */
    fun getModel(id: String): AiModelDescriptor? = modelCatalog[id]

    /**
     * Filters catalog by functional category.
     */
    fun getModelsByCategory(category: ModelCategory): List<AiModelDescriptor> =
        modelCatalog.values.filter { it.category == category }

    /**
     * Returns list of models whose weights files are downloaded and verified on disk.
     */
    fun getDownloadedModels(): List<AiModelDescriptor> =
        modelCatalog.values.filter { isModelDownloaded(it.id) }

    /**
     * Checks if a model's weights file is present and validated on disk.
     */
    fun isModelDownloaded(id: String): Boolean {
        val descriptor = modelCatalog[id] ?: return false
        return downloader.isDownloaded(descriptor)
    }

    /**
     * Checks if a specific model is currently loaded in memory.
     */
    fun isModelLoaded(id: String): Boolean {
        return activeAdapter?.descriptor?.id == id && (activeAdapter?.isLoaded() == true)
    }

    /**
     * Returns true if at least one neural model (requiring download) is present on disk.
     */
    fun hasAnyDownloadedNeuralModel(): Boolean {
        return modelCatalog.values.any { it.runtimeType != ModelRuntimeType.CUSTOM_HEURISTIC && downloader.isDownloaded(it) }
    }

    /**
     * Returns true if an external neural model is currently loaded in memory.
     */
    fun hasAnyNeuralModelLoaded(): Boolean {
        val adapter = activeAdapter ?: return false
        return adapter.descriptor.runtimeType != ModelRuntimeType.CUSTOM_HEURISTIC && adapter.isLoaded()
    }

    /**
     * Gets the active adapter. If no neural model is loaded, returns the built-in [heuristicAssist].
     */
    fun getActiveAdapter(): AiModelAdapter {
        val adapter = activeAdapter
        if (adapter != null && adapter.isLoaded()) {
            return adapter
        }
        return heuristicAssist
    }

    /**
     * Loads a downloaded model into memory, unloading any previously active model.
     */
    suspend fun loadModel(id: String): LoadResult {
        if (!com.artstudio.core.FeatureFlags.isAiEnabled) {
            return LoadResult.UnsupportedRuntime("AI models are disabled: FeatureFlags.isAiEnabled is false")
        }
        val descriptor = modelCatalog[id] ?: return LoadResult.ModelFileNotFound("Model $id not found in catalog")

        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
            activeAdapter = heuristicAssist
            return LoadResult.Success
        }

        if (!downloader.isDownloaded(descriptor)) {
            return LoadResult.ModelFileNotFound("Model $id weights not downloaded on disk")
        }

        // Unload previous active adapter to protect memory budgets
        activeAdapter?.unload()

        val weightsFile = downloader.getModelFile(descriptor)
        val adapter: AiModelAdapter = when (descriptor.runtimeType) {
            ModelRuntimeType.TFLITE -> TfliteModelAdapter(descriptor, weightsFile)
            ModelRuntimeType.ONNX -> OnnxRuntimeModelAdapter(descriptor, weightsFile)
            ModelRuntimeType.CUSTOM_HEURISTIC -> heuristicAssist
        }

        val loadResult = adapter.load(weightsFile)
        if (loadResult is LoadResult.Success) {
            activeAdapter = adapter
        }
        return loadResult
    }

    /**
     * Unloads any currently active neural model to immediately reclaim heap RAM.
     */
    fun unloadActiveModel() {
        activeAdapter?.unload()
        activeAdapter = null
    }

    /**
     * Deletes a downloaded model weights file and unloads it if active.
     */
    fun deleteModel(id: String): Boolean {
        val descriptor = modelCatalog[id] ?: return false
        if (isModelLoaded(id)) {
            unloadActiveModel()
        }
        return downloader.deleteModel(descriptor)
    }

    /**
     * Clears all downloaded model files and unloads any active model.
     */
    fun clearAllDownloadedModels(): Long {
        unloadActiveModel()
        return downloader.clearAllModelStorage()
    }

    /**
     * Returns total disk space consumed by downloaded AI model weights.
     */
    fun getTotalStorageUsedBytes(): Long = downloader.getTotalStorageConsumedBytes()

    /**
     * Checks if active model has been idle for >= 60 seconds, unloading it if so.
     */
    fun checkIdleUnload(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val adapter = activeAdapter ?: return false
        if (adapter.descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) return false

        val unloaded = adapter.checkAndUnloadIfIdle(60_000L, currentTimeMs)
        if (unloaded) {
            activeAdapter = null
        }
        return unloaded
    }
}
