package com.artstudio.filter

import com.artstudio.history.ApplyFilterCommand
import com.artstudio.layer.LayerManager
import kotlinx.coroutines.*

/**
 * Interactive filter session manager for previewing, cancelling, and committing
 * image filter operations with bounded preview resolution and cooperative cancellation.
 */
class FilterSession(
    val layerId: String,
    val layerName: String,
    val sourcePixels: IntArray,
    val width: Int,
    val height: Int,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    init {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(sourcePixels.size == width * height) { "Source pixel size mismatch" }
    }

    var activeConfig: FilterConfig = FilterConfig.GaussianBlurConfig()
        private set

    var previewEnabled: Boolean = true

    // Downscaled preview buffer (bounded to max 512x512)
    val previewDownscaled = ImageFilterEngine.downscaleForPreview(sourcePixels, width, height, maxDim = 512)
    val previewSource: IntArray = previewDownscaled.first
    val previewWidth: Int = previewDownscaled.second
    val previewHeight: Int = previewDownscaled.third

    private var currentJob: Job? = null
    private var currentToken: FilterCancellationToken? = null

    var currentPreviewState: FilterPreviewState = FilterPreviewState(
        isProcessing = false,
        previewPixels = previewSource.clone(),
        previewWidth = previewWidth,
        previewHeight = previewHeight
    )
        private set

    /**
     * Updates the active filter configuration and schedules a debounced background preview calculation.
     */
    fun updateConfig(
        newConfig: FilterConfig,
        onPreviewUpdated: ((FilterPreviewState) -> Unit)? = null
    ) {
        activeConfig = newConfig

        if (!previewEnabled) {
            currentPreviewState = currentPreviewState.copy(
                isProcessing = false,
                previewPixels = previewSource.clone()
            )
            onPreviewUpdated?.invoke(currentPreviewState)
            return
        }

        // Cancel previous ongoing calculation
        currentToken?.cancel()
        currentJob?.cancel()

        val token = FilterCancellationToken()
        currentToken = token

        currentPreviewState = currentPreviewState.copy(isProcessing = true)
        onPreviewUpdated?.invoke(currentPreviewState)

        currentJob = scope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val result = try {
                ImageFilterEngine.applyFilter(
                    src = previewSource,
                    width = previewWidth,
                    height = previewHeight,
                    config = newConfig,
                    token = token
                )
            } catch (e: Exception) {
                null
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (!token.isCancelled && isActive) {
                currentPreviewState = if (result != null) {
                    FilterPreviewState(
                        isProcessing = false,
                        previewPixels = result,
                        previewWidth = previewWidth,
                        previewHeight = previewHeight,
                        processingTimeMs = elapsed
                    )
                } else {
                    FilterPreviewState(
                        isProcessing = false,
                        previewPixels = previewSource.clone(),
                        previewWidth = previewWidth,
                        previewHeight = previewHeight,
                        errorMessage = "Filter calculation failed or cancelled"
                    )
                }
                withContext(Dispatchers.Main) {
                    onPreviewUpdated?.invoke(currentPreviewState)
                }
            }
        }
    }

    /**
     * Synchronously evaluates the preview (used for unit testing and deterministic checks).
     */
    fun evaluatePreviewSync(config: FilterConfig): FilterPreviewState {
        activeConfig = config
        val startTime = System.currentTimeMillis()
        val result = ImageFilterEngine.applyFilter(
            src = previewSource,
            width = previewWidth,
            height = previewHeight,
            config = config
        )
        val elapsed = System.currentTimeMillis() - startTime
        val state = FilterPreviewState(
            isProcessing = false,
            previewPixels = result,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            processingTimeMs = elapsed
        )
        currentPreviewState = state
        return state
    }

    /**
     * Cancels the active filter session without altering the authoritative layer buffer.
     */
    fun cancel() {
        currentToken?.cancel()
        currentJob?.cancel()
        currentPreviewState = FilterPreviewState(
            isProcessing = false,
            previewPixels = previewSource.clone(),
            previewWidth = previewWidth,
            previewHeight = previewHeight
        )
    }

    /**
     * Commits the active filter to the authoritative layer, generating a reversible [ApplyFilterCommand].
     *
     * @param layerManager The target layer manager to execute the command into.
     * @param token Optional cancellation token for long full-resolution processing.
     * @return The created and executed [ApplyFilterCommand], or null if cancelled.
     */
    fun commit(
        layerManager: LayerManager,
        token: FilterCancellationToken? = null
    ): ApplyFilterCommand? {
        currentToken?.cancel()
        currentJob?.cancel()

        val fullResult = ImageFilterEngine.applyFilter(
            src = sourcePixels,
            width = width,
            height = height,
            config = activeConfig,
            token = token
        )

        if (token?.isCancelled == true) {
            return null
        }

        val tileDeltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(
            prePixels = sourcePixels,
            postPixels = fullResult,
            canvasWidth = width,
            canvasHeight = height
        )

        val cmd = ApplyFilterCommand(
            layerId = layerId,
            filterName = activeConfig.type.displayName,
            width = width,
            height = height,
            tileDeltas = tileDeltas
        )

        layerManager.applyFilterCommand(cmd, sourcePixels)
        return cmd
    }
}
