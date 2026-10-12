package com.artstudio.ai.model

import com.artstudio.core.FeatureFlags
import com.artstudio.cv.ai.CandidateResolution
import com.artstudio.cv.ai.CvAiIntegrationPipeline
import com.artstudio.cv.region.RegionMaskConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Gate test suite verifying Option A truthfulness:
 * - FeatureFlags.isAiEnabled is permanently false.
 * - No fake or simulated masks are returned by TFLite or ONNX model adapters.
 * - Classical CV remains the sole authoritative AutoDraw engine.
 */
class AiModelTruthfulnessTest {

    private lateinit var tempDir: File
    private lateinit var downloader: AiModelDownloader
    private lateinit var registry: AiModelRegistry

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "artstudio_ai_truth_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        downloader = AiModelDownloader(storageDir = tempDir, allowCellular = true, isWifiConnected = true)
        registry = AiModelRegistry(downloader)
    }

    @After
    fun tearDown() {
        registry.unloadActiveModel()
        tempDir.deleteRecursively()
        FeatureFlags.isAiEnabled = false
    }

    @Test
    fun testFeatureFlagsAiIsDisabledPermanently() {
        assertFalse(
            "FeatureFlags.isAiEnabled must be false in accordance with ADR-050 Option A",
            FeatureFlags.isAiEnabled
        )
    }

    @Test
    fun testTfliteAdapterNeverReturnsFakeMasks() = runBlocking {
        val testBytes = ByteArray(100)
        val dummyFile = File(tempDir, "dummy_model.tflite").apply { writeBytes(testBytes) }
        val descriptor = AiModelDescriptor(
            id = "test_tflite_model",
            displayName = "Test TFLite Model",
            category = ModelCategory.SEGMENTATION,
            description = "Test Model",
            weightsSizeBytes = 100L,
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/model.tflite",
            sha256 = "dummy_sha256",
            targetDomain = TargetDomain.PHOTO,
            expectedRamPeakBytes = 1_000_000L,
            expectedInferenceMs = 25L,
            supportedPromptModalities = listOf(PromptModality.AUTOMATIC),
            runtimeType = ModelRuntimeType.TFLITE,
            isBundled = false
        )

        val adapter = TfliteModelAdapter(descriptor, dummyFile)
        val pixels = IntArray(128 * 128) { 0xFFFFFFFF.toInt() }

        // When AI is disabled
        FeatureFlags.isAiEnabled = false
        val disabledResult = adapter.infer(
            pixels = pixels,
            width = 128,
            height = 128,
            params = InferenceParams()
        )
        assertTrue("Disabled inference must return Error", disabledResult is InferenceResult.Error)
        assertFalse("Must never return fake Segmentation mask", disabledResult is InferenceResult.Segmentation)

        // Even if temporarily enabled for test, must return truthful error
        FeatureFlags.isAiEnabled = true
        adapter.load(dummyFile)
        val result = adapter.infer(
            pixels = pixels,
            width = 128,
            height = 128,
            params = InferenceParams()
        )
        assertTrue("Must return honest error explaining on-device inference is not implemented in v1", result is InferenceResult.Error)
        val errMsg = (result as InferenceResult.Error).message
        assertTrue(errMsg.contains("Download Only") || errMsg.contains("Not Implemented in v1"))
        assertFalse("Must never return fake Segmentation mask", result is InferenceResult.Segmentation)
    }

    @Test
    fun testOnnxAdapterNeverReturnsFakeMasks() = runBlocking {
        val testBytes = ByteArray(100)
        val dummyFile = File(tempDir, "dummy_model.onnx").apply { writeBytes(testBytes) }
        val descriptor = AiModelDescriptor(
            id = "test_onnx_model",
            displayName = "Test ONNX Model",
            category = ModelCategory.SEGMENTATION,
            description = "Test Model",
            weightsSizeBytes = 100L,
            licenseId = "MIT",
            licenseUrl = "https://opensource.org/licenses/MIT",
            sourceUrl = "https://example.com/model.onnx",
            sha256 = "dummy_sha256",
            targetDomain = TargetDomain.ANIME_FLAT_COLOUR,
            expectedRamPeakBytes = 1_000_000L,
            expectedInferenceMs = 30L,
            supportedPromptModalities = listOf(PromptModality.AUTOMATIC),
            runtimeType = ModelRuntimeType.ONNX,
            isBundled = false
        )

        val adapter = OnnxRuntimeModelAdapter(descriptor, dummyFile)
        val pixels = IntArray(128 * 128) { 0xFFFFFFFF.toInt() }

        FeatureFlags.isAiEnabled = false
        val disabledResult = adapter.infer(
            pixels = pixels,
            width = 128,
            height = 128,
            params = InferenceParams()
        )
        assertTrue("Disabled inference must return Error", disabledResult is InferenceResult.Error)
        assertFalse("Must never return fake Segmentation mask", disabledResult is InferenceResult.Segmentation)

        FeatureFlags.isAiEnabled = true
        adapter.load(dummyFile)
        val result = adapter.infer(
            pixels = pixels,
            width = 128,
            height = 128,
            params = InferenceParams()
        )
        assertTrue("Must return honest error", result is InferenceResult.Error)
        assertFalse("Must never return fake Segmentation mask", result is InferenceResult.Segmentation)
    }

    @Test
    fun testCvAiIntegrationPipelineFallsBackToClassicalWithoutFakeProposals() = runBlocking {
        FeatureFlags.isAiEnabled = false
        val pixels = IntArray(64 * 64) { 0xFFFFFFFF.toInt() }
        val proposal = CvAiIntegrationPipeline.segmentRegionWithAiSupplement(
            pixels = pixels,
            width = 64,
            height = 64,
            seedX = 32,
            seedY = 32,
            registry = registry,
            config = RegionMaskConfig()
        )

        assertNotNull(proposal.classicalMask)
        assertEquals(CandidateResolution.USE_CLASSICAL, proposal.resolution)
        val resolved = proposal.resolve()
        assertEquals(proposal.classicalMask, resolved)
    }
}
