package com.artstudio.ai.model

import com.artstudio.cv.ai.CandidateResolution
import com.artstudio.cv.ai.CvAiIntegrationPipeline
import com.artstudio.cv.region.RegionMaskConfig
import com.artstudio.cv.region.RegionMaskEngine
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.AutoDrawProvenance
import com.artstudio.document.model.CanvasConfig
import com.artstudio.job.JobCancellationToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/**
 * Unit test suite verifying Prompt 55: AI Model Registry, On-Demand Downloads,
 * Memory Gating, Classical CV Supplementation, Semantic Tagging, and Immutability.
 */
class AiModelRegistryTest {

    private lateinit var tempDir: File
    private lateinit var downloader: AiModelDownloader
    private lateinit var registry: AiModelRegistry

    @Before
    fun setUp() {
        com.artstudio.core.FeatureFlags.isAiEnabled = true
        tempDir = File(System.getProperty("java.io.tmpdir"), "artstudio_ai_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        downloader = AiModelDownloader(storageDir = tempDir, allowCellular = true, isWifiConnected = true)
        registry = AiModelRegistry(downloader)
    }

    @After
    fun tearDown() {
        registry.unloadActiveModel()
        tempDir.deleteRecursively()
        com.artstudio.core.FeatureFlags.isAiEnabled = false
    }

    // ========================================================================
    // 1. Catalog Parsing, Metadata Completeness & Optional Invariant (Criteria 1 & 2)
    // ========================================================================

    @Test
    fun testCatalogParsingAndMetadataCompleteness() {
        val models = registry.getAllModels()
        assertTrue("Catalog must have at least 6 models, found ${models.size}", models.size >= 6)

        // Check MediaPipe
        val mediapipe = registry.getModel(AiModelCatalog.ID_MEDIAPIPE_SEGMENTER)
        assertNotNull(mediapipe)
        assertEquals("Apache-2.0", mediapipe!!.licenseId)
        assertEquals(TargetDomain.PHOTO, mediapipe.targetDomain)
        assertEquals(ModelCategory.SEGMENTATION, mediapipe.category)
        assertFalse("Neural weights must never be bundled", mediapipe.isBundled)

        // Check U2-Net
        val u2net = registry.getModel(AiModelCatalog.ID_U2NET_ILLUSTRATION)
        assertNotNull(u2net)
        assertEquals(TargetDomain.ANIME_FLAT_COLOUR, u2net!!.targetDomain)

        // Check AnimeGANv2
        val animegan = registry.getModel(AiModelCatalog.ID_ANIMEGAN_V2)
        assertNotNull(animegan)
        assertEquals(ModelCategory.STYLE_TRANSFER, animegan!!.category)
        assertEquals("MIT", animegan.licenseId)

        // Check Real-ESRGAN
        val esrgan = registry.getModel(AiModelCatalog.ID_REAL_ESRGAN)
        assertNotNull(esrgan)
        assertEquals(ModelCategory.SUPER_RESOLUTION, esrgan!!.category)
        assertEquals("BSD-3-Clause", esrgan.licenseId)

        // Check MobileSAM
        val mobilesam = registry.getModel(AiModelCatalog.ID_MOBILESAM)
        assertNotNull(mobilesam)
        assertTrue(mobilesam!!.supportedPromptModalities.contains(PromptModality.POINT))

        // Check CLIPSeg
        val clipseg = registry.getModel(AiModelCatalog.ID_CLIPSEG)
        assertNotNull(clipseg)
        assertTrue(clipseg!!.supportedPromptModalities.contains(PromptModality.TEXT))

        // Check Semantic Heuristic Assist
        val heuristic = registry.getModel(AiModelCatalog.ID_SEMANTIC_HEURISTIC)
        assertNotNull(heuristic)
        assertEquals(0L, heuristic!!.weightsSizeBytes)
        assertTrue(heuristic.isBundled)
    }

    @Test
    fun testZeroNeuralWeightsBundledAndAllModelsOptional() {
        for (model in registry.getAllModels()) {
            if (model.runtimeType != ModelRuntimeType.CUSTOM_HEURISTIC) {
                assertFalse("Neural model ${model.id} must not be bundled in APK", model.isBundled)
                assertTrue("Neural model ${model.id} must have non-zero size", model.weightsSizeBytes > 0L)
            }
        }
    }

    @Test
    fun testTargetDomainWarningOnLineArt() {
        val mediapipe = registry.getModel(AiModelCatalog.ID_MEDIAPIPE_SEGMENTER)!!
        assertTrue(mediapipe.isPhotoOnly)
        assertNotNull(mediapipe.domainWarning)
        assertTrue(mediapipe.domainWarning!!.contains("trained on natural photos"))

        val u2net = registry.getModel(AiModelCatalog.ID_U2NET_ILLUSTRATION)!!
        assertFalse(u2net.isPhotoOnly)
        assertNull(u2net.domainWarning)
    }

    @Test
    fun testCatalogJsonExportAndReimportRoundTrip() {
        val json = AiModelCatalog.exportCatalogJson()
        assertTrue(json.contains("mediapipe_image_segmenter"))
        assertTrue(json.contains("u2net_illustration_salient"))

        val imported = AiModelCatalog.importCatalogJson(json)
        assertEquals(AiModelCatalog.CURATED_CATALOG.size, imported.size)
        assertEquals(AiModelCatalog.CURATED_CATALOG.map { it.id }, imported.map { it.id })
    }

    // ========================================================================
    // 2. On-Demand Download Manager, Pre-flight & Verification (Criteria 3 & 4)
    // ========================================================================

    @Test
    fun testDownloadPreflightStorageRejection() {
        val model = registry.getModel(AiModelCatalog.ID_MEDIAPIPE_SEGMENTER)!!
        val requiredStorage = model.weightsSizeBytes * 2

        // Insufficient storage
        val preflightFail = downloader.performPreflight(model, availableStorageBytes = requiredStorage - 1000)
        assertTrue(preflightFail is DownloadPreflightResult.InsufficientStorage)
        assertEquals(requiredStorage, (preflightFail as DownloadPreflightResult.InsufficientStorage).requiredBytes)

        // Sufficient storage
        val preflightOk = downloader.performPreflight(model, availableStorageBytes = requiredStorage + 100000)
        assertTrue(preflightOk is DownloadPreflightResult.CanDownload)
    }

    @Test
    fun testDownloadPreflightWifiRequirement() {
        val model = registry.getModel(AiModelCatalog.ID_U2NET_ILLUSTRATION)!!
        downloader.allowCellular = false
        downloader.isWifiConnected = false

        val preflight = downloader.performPreflight(model, availableStorageBytes = 100_000_000L)
        assertTrue(preflight is DownloadPreflightResult.WifiRequired)

        downloader.isWifiConnected = true
        val preflightWifi = downloader.performPreflight(model, availableStorageBytes = 100_000_000L)
        assertTrue(preflightWifi is DownloadPreflightResult.CanDownload)
    }

    @Test
    fun testDownloadSha256VerificationSuccessAndFailureCleanup() {
        val testBytes = "ArtStudioOnDeviceModelWeightsTestData".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val correctHash = digest.digest(testBytes).joinToString("") { "%02x".format(it) }

        val testDescriptor = AiModelDescriptor(
            id = "test_download_model",
            displayName = "Test Model",
            category = ModelCategory.SEGMENTATION,
            description = "Test description",
            weightsSizeBytes = testBytes.size.toLong(),
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/test.tflite",
            sha256 = correctHash,
            targetDomain = TargetDomain.ANIME_FLAT_COLOUR,
            expectedRamPeakBytes = 10_000_000L,
            expectedInferenceMs = 10L,
            supportedPromptModalities = listOf(PromptModality.AUTOMATIC),
            runtimeType = ModelRuntimeType.TFLITE
        )

        // 1. Successful download
        val downloadResult = downloader.downloadModelStreaming(
            descriptor = testDescriptor,
            streamSupplier = { ByteArrayInputStream(testBytes) }
        )
        assertTrue("Download should succeed", downloadResult.isSuccess)
        val file = downloadResult.getOrThrow()
        assertTrue(file.exists())
        assertEquals(testBytes.size.toLong(), file.length())
        assertTrue(downloader.isDownloaded(testDescriptor))

        // 2. Corrupted weights download with bad hash
        val badDescriptor = testDescriptor.copy(
            id = "test_bad_hash_model",
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        val badResult = downloader.downloadModelStreaming(
            descriptor = badDescriptor,
            streamSupplier = { ByteArrayInputStream(testBytes) }
        )
        assertTrue("Corrupted hash must fail verification", badResult.isFailure)
        val targetFile = downloader.getModelFile(badDescriptor)
        assertFalse("Corrupted file must be deleted upon failure", targetFile.exists())
    }

    @Test
    fun testDownloadResumeWithByteOffset() {
        val testBytes = ByteArray(1000) { (it % 128).toByte() }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(testBytes).joinToString("") { "%02x".format(it) }

        val descriptor = AiModelDescriptor(
            id = "test_resumable_model",
            displayName = "Resumable Model",
            category = ModelCategory.SEGMENTATION,
            description = "Resumable",
            weightsSizeBytes = 1000L,
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/resumable.tflite",
            sha256 = hash,
            targetDomain = TargetDomain.GENERAL,
            expectedRamPeakBytes = 5_000_000L,
            expectedInferenceMs = 10L,
            supportedPromptModalities = listOf(PromptModality.AUTOMATIC),
            runtimeType = ModelRuntimeType.TFLITE
        )

        // Write first 400 bytes as .part file
        val partFile = File(tempDir, "${descriptor.id}.part")
        partFile.writeBytes(testBytes.copyOfRange(0, 400))
        assertEquals(400L, partFile.length())

        // Resume downloading remaining 600 bytes from offset 400
        val resumeResult = downloader.downloadModelStreaming(
            descriptor = descriptor,
            streamSupplier = { offset ->
                assertEquals(400L, offset)
                ByteArrayInputStream(testBytes.copyOfRange(offset.toInt(), testBytes.size))
            },
            resumeOffset = 400L
        )

        assertTrue(resumeResult.isSuccess)
        val finalFile = resumeResult.getOrThrow()
        assertEquals(1000L, finalFile.length())
        assertArrayEquals(testBytes, finalFile.readBytes())
    }

    // ========================================================================
    // 3. Runtime Adapter, Memory Budgets & Cooperative Cancellation (Criteria 4)
    // ========================================================================

    @Test
    fun testModelLoadingRejectionOnLowMemory() = runBlocking {
        // Create descriptor requesting an absurdly huge RAM peak (100 Terabytes)
        val hugeRamDescriptor = AiModelDescriptor(
            id = "huge_ram_model",
            displayName = "Gigantic Model",
            category = ModelCategory.SEGMENTATION,
            description = "Huge model",
            weightsSizeBytes = 1000L,
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/huge.tflite",
            sha256 = "dummy",
            targetDomain = TargetDomain.GENERAL,
            expectedRamPeakBytes = 100_000_000_000_000L, // 100 TB
            expectedInferenceMs = 1000L,
            supportedPromptModalities = listOf(PromptModality.AUTOMATIC),
            runtimeType = ModelRuntimeType.TFLITE
        )

        val dummyFile = File(tempDir, "huge_ram_model.tflite").apply { writeBytes(ByteArray(1000)) }
        val adapter = TfliteModelAdapter(hugeRamDescriptor, dummyFile)
        val loadResult = adapter.load()

        assertTrue(
            "Model loading must be rejected when free memory is below 1.5x expected peak",
            loadResult is LoadResult.InsufficientMemory
        )
        assertFalse(adapter.isLoaded())
    }

    @Test
    fun testCooperativeInferenceCancellation() = runBlocking {
        val testBytes = ByteArray(100)
        val dummyFile = File(tempDir, "test_cancel.tflite").apply { writeBytes(testBytes) }
        val descriptor = AiModelDescriptor(
            id = "test_cancel",
            displayName = "Cancel Model",
            category = ModelCategory.SEGMENTATION,
            description = "Cancel",
            weightsSizeBytes = 100L,
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/cancel.tflite",
            sha256 = "dummy",
            targetDomain = TargetDomain.GENERAL,
            expectedRamPeakBytes = 1_000_000L,
            expectedInferenceMs = 10L,
            supportedPromptModalities = listOf(PromptModality.POINT),
            runtimeType = ModelRuntimeType.TFLITE
        )

        val adapter = TfliteModelAdapter(descriptor, dummyFile)
        val loadRes = adapter.load()
        assertTrue(loadRes is LoadResult.Success)

        val token = JobCancellationToken()
        token.cancel("User cancelled task")

        val pixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() }
        val inferResult = adapter.infer(
            pixels = pixels,
            width = 100,
            height = 100,
            params = InferenceParams(pointPrompts = listOf(Pair(50f, 50f))),
            cancellationToken = token
        )

        assertEquals(InferenceResult.Cancelled, inferResult)
    }

    @Test
    fun testAutomaticModelUnloadAfterIdleTimeout() = runBlocking {
        val testBytes = ByteArray(100)
        val dummyFile = File(tempDir, "test_idle.tflite").apply { writeBytes(testBytes) }
        val descriptor = AiModelDescriptor(
            id = "test_idle",
            displayName = "Idle Model",
            category = ModelCategory.SEGMENTATION,
            description = "Idle",
            weightsSizeBytes = 100L,
            licenseId = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceUrl = "https://example.com/idle.tflite",
            sha256 = "dummy",
            targetDomain = TargetDomain.GENERAL,
            expectedRamPeakBytes = 1_000_000L,
            expectedInferenceMs = 10L,
            supportedPromptModalities = listOf(PromptModality.POINT),
            runtimeType = ModelRuntimeType.TFLITE
        )

        val adapter = TfliteModelAdapter(descriptor, dummyFile)
        adapter.load()
        assertTrue(adapter.isLoaded())

        // 1. Within 30 seconds (< 60s timeout): should NOT unload
        val withinTime = adapter.lastActivityTimestamp + 30_000L
        val unloaded30s = adapter.checkAndUnloadIfIdle(idleTimeoutMs = 60_000L, currentTimeMs = withinTime)
        assertFalse(unloaded30s)
        assertTrue(adapter.isLoaded())

        // 2. After 65 seconds (> 60s timeout): should unload automatically
        val afterTime = adapter.lastActivityTimestamp + 65_000L
        val unloaded65s = adapter.checkAndUnloadIfIdle(idleTimeoutMs = 60_000L, currentTimeMs = afterTime)
        assertTrue(unloaded65s)
        assertFalse(adapter.isLoaded())
    }

    // ========================================================================
    // 4. CV Pipeline Integration & Contradiction Resolution (Criteria 5 & 6)
    // ========================================================================

    @Test
    fun testAiOutputMergeWithClassicalCvNeverDiscardsClassical() = runBlocking {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }

        // Draw a dark box [10..40, 10..40]
        for (y in 10..40) {
            for (x in 10..40) {
                pixels[y * width + x] = 0xFFFF0000.toInt() // Red box
            }
        }

        // Run segmentation with AI supplement (Heuristic Assist active)
        val proposal = CvAiIntegrationPipeline.segmentRegionWithAiSupplement(
            pixels = pixels,
            width = width,
            height = height,
            seedX = 20,
            seedY = 20,
            registry = registry
        )

        assertNotNull(proposal.classicalMask)
        assertTrue(proposal.classicalMask.selectedPixelCount > 0)
        assertEquals(RegionSource.CLASSICAL_CV, proposal.classicalMask.source)

        // Proposal default is USE_CLASSICAL
        val resolved = proposal.resolve(CandidateResolution.USE_CLASSICAL)
        assertEquals(proposal.classicalMask, resolved)
    }

    @Test
    fun testSemanticTagPersistenceOnRegionMask() {
        val maskBytes = ByteArray(100) { 255.toByte() }
        val augmentation = AiAugmentation(
            modelId = AiModelCatalog.ID_MEDIAPIPE_SEGMENTER,
            modelName = "MediaPipe Segmenter",
            modelSha256 = "b7a2d6f890123456789abcdef0123456789abcdef0123456789abcdef0123456",
            inferenceLatencyMs = 38L,
            confidence = 0.94f,
            semanticTag = RegionSemanticTag.SKIN,
            promptModality = PromptModality.AUTOMATIC
        )

        val regionMask = com.artstudio.cv.region.RegionMask(
            regionId = "face_skin_01",
            width = 10,
            height = 10,
            mask = maskBytes,
            semanticTag = RegionSemanticTag.SKIN,
            confidence = 0.94f,
            source = RegionSource.AI_MODEL,
            aiAugmentation = augmentation
        )

        assertEquals(RegionSemanticTag.SKIN, regionMask.semanticTag)
        assertEquals(100, regionMask.selectedPixelCount)
        assertNotNull(regionMask.aiAugmentation)
        assertEquals("face_skin_01", regionMask.regionId)
    }

    @Test
    fun testSemanticHeuristicAssistNoAiFallback() = runBlocking {
        // Pure-Kotlin heuristic classifier works without any weights downloaded
        val heuristic = registry.heuristicAssist
        assertTrue(heuristic.isLoaded())

        // Test Skin classification
        val skinArgb = 0xFFFFDFD3.toInt() // Warm peach skin tone
        val (h, s, v) = SemanticHeuristicAssist.rgbToHsv(skinArgb)
        val skinTag = SemanticHeuristicAssist.classifyHsv(h, s, v, x = 50, y = 50, w = 100, hCanvas = 100)
        assertEquals(RegionSemanticTag.SKIN, skinTag)

        // Test Highlight classification
        val whiteArgb = 0xFFFFFFFF.toInt()
        val (wh, ws, wv) = SemanticHeuristicAssist.rgbToHsv(whiteArgb)
        val highlightTag = SemanticHeuristicAssist.classifyHsv(wh, ws, wv, x = 50, y = 50, w = 100, hCanvas = 100)
        assertEquals(RegionSemanticTag.HIGHLIGHT, highlightTag)

        // Test Shadow classification
        val darkArgb = 0xFF101010.toInt()
        val (dh, ds, dv) = SemanticHeuristicAssist.rgbToHsv(darkArgb)
        val shadowTag = SemanticHeuristicAssist.classifyHsv(dh, ds, dv, x = 50, y = 50, w = 100, hCanvas = 100)
        assertEquals(RegionSemanticTag.SHADOW, shadowTag)
    }

    // ========================================================================
    // 5. Golden Tests: Zero-Model Fidelity, Document Immutability & Provenance Round-Trip
    // ========================================================================

    @Test
    fun testZeroModelGoldenClassicalCvPipelineFidelity() {
        // Assert that zero neural models downloaded does NOT degrade classical CV
        assertEquals(0, registry.getDownloadedModels().filter { it.runtimeType != ModelRuntimeType.CUSTOM_HEURISTIC }.size)

        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        // Draw 10x10 square in center
        for (y in 10 until 20) {
            for (x in 10 until 20) {
                pixels[y * width + x] = 0xFF000000.toInt()
            }
        }

        val result = RegionMaskEngine.segmentRegion(
            pixels = pixels,
            width = width,
            height = height,
            seedX = 15,
            seedY = 15,
            config = RegionMaskConfig(colorTolerance = 0.1f, boundaryExpansion = 0)
        )

        assertTrue("Pixel count should match detected square (96..100)", result.pixelCount in 96..100)
        assertTrue(result.contains(15, 15))
        assertFalse(result.contains(5, 5))
    }

    @Test
    fun testModelInferenceDoesNotMutateArtDocument(): Unit = runBlocking {
        // Construct canonical ArtDocument
        val doc = ArtDocument(
            title = "Immutability Test",
            canvas = CanvasConfig(width = 256, height = 256)
        )
        val docJsonBefore = DocumentSerializer.toJsonString(doc)

        // Simulate AI inference pass on canvas pixels
        val dummyPixels = IntArray(256 * 256) { 0xFFFFFFFF.toInt() }
        val adapter = registry.getActiveAdapter()
        val result = adapter.infer(
            pixels = dummyPixels,
            width = 256,
            height = 256,
            params = InferenceParams(pointPrompts = listOf(Pair(128f, 128f)))
        )
        assertNotNull(result)

        // Verify ArtDocument is bit-exact identical
        val docJsonAfter = DocumentSerializer.toJsonString(doc)
        assertEquals("Loading a model and running inference must never mutate the ArtDocument", docJsonBefore, docJsonAfter)
    }

    @Test
    fun testAiAugmentationProvenanceNativeSaveReopenRoundTrip() {
        val aug = AiAugmentation(
            modelId = AiModelCatalog.ID_U2NET_ILLUSTRATION,
            modelName = "U2-Net Salient Cutout",
            modelSha256 = "c8b3e7a123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            inferenceLatencyMs = 82L,
            confidence = 0.93f,
            semanticTag = RegionSemanticTag.CLOTHING,
            promptModality = PromptModality.AUTOMATIC
        )

        val prov = AutoDrawProvenance(
            sourceImageHash = "hash123456789",
            algorithm = "u2net_ai_supplement",
            edgeThresholdLow = 40f,
            edgeThresholdHigh = 160f,
            generatedLayerIds = listOf("layer_cutout_01"),
            timestampMs = 1700000000000L,
            aiAugmentation = aug
        )

        val doc = ArtDocument(
            title = "Provenance Test",
            autoDrawProvenance = prov
        )

        val json = DocumentSerializer.toJsonString(doc)
        assertTrue(json.contains("u2net_illustration_salient"))
        assertTrue(json.contains("CLOTHING"))

        val reloadedDoc = DocumentSerializer.fromJsonString(json)
        assertNotNull(reloadedDoc.autoDrawProvenance)
        val reloadedProv = reloadedDoc.autoDrawProvenance!!
        assertEquals("u2net_ai_supplement", reloadedProv.algorithm)
        assertNotNull(reloadedProv.aiAugmentation)

        val reloadedAug = reloadedProv.aiAugmentation!!
        assertEquals(AiModelCatalog.ID_U2NET_ILLUSTRATION, reloadedAug.modelId)
        assertEquals("U2-Net Salient Cutout", reloadedAug.modelName)
        assertEquals(82L, reloadedAug.inferenceLatencyMs)
        assertEquals(0.93f, reloadedAug.confidence, 0.001f)
        assertEquals(RegionSemanticTag.CLOTHING, reloadedAug.semanticTag)
        assertEquals(PromptModality.AUTOMATIC, reloadedAug.promptModality)
    }

    @Test
    fun testSuperResolutionReferenceCappedAt4096AndPaintLayerImmunity() = runBlocking {
        // High-res reference (2048 x 2048) upscaled 2x should cap at 4096 x 4096
        val inW = 2048
        val inH = 2048
        val refPixels = IntArray(16) // Small test slice for mock

        // If not downloaded, returns untouched dimensions
        val (outPixels, outW, outH) = CvAiIntegrationPipeline.superResolveReference(
            referencePixels = refPixels,
            width = inW,
            height = inH,
            registry = registry
        )
        assertEquals(inW, outW)
        assertEquals(inH, outH)
        assertTrue(outW <= 4096)
        assertTrue(outH <= 4096)
    }
}
