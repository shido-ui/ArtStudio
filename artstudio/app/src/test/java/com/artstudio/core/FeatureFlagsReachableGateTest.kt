package com.artstudio.core

import com.artstudio.ai.model.*
import com.artstudio.color.ColorData
import com.artstudio.colorstack.ColorStackPipeline
import com.artstudio.colorstack.ColorStackRecipe
import com.artstudio.deformation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Authoritative verification test proving that hidden features are strictly unreachable
 * when their corresponding FeatureFlags are false, and that released features are enabled by default.
 */
class FeatureFlagsReachableGateTest {

    @Test
    fun testDefaultReleaseFlagValues() {
        assertTrue("AutoDraw v1 is shipped", FeatureFlags.isAutoDrawV1Enabled)
        assertTrue("PSD is shipped", FeatureFlags.isPsdEnabled)
        assertTrue("SVG is shipped", FeatureFlags.isSvgEnabled)
        assertTrue("Animation is shipped", FeatureFlags.isAnimationEnabled)
        assertTrue("ColorStack is shipped in release", FeatureFlags.isColorStackEnabled)
        assertTrue("Deformation is shipped in release", FeatureFlags.isDeformationEnabled)
        assertFalse("AI Neural Models are disabled in v1 release", FeatureFlags.isAiEnabled)
    }

    @Test
    fun testColorStackUnreachableWhenDisabled() {
        val orig = FeatureFlags.isColorStackEnabled
        try {
            FeatureFlags.isColorStackEnabled = false

            // ColorStackPipeline.execute throws
            assertThrows(IllegalStateException::class.java) {
                ColorStackPipeline.execute(
                    recipe = ColorStackRecipe(name = "Test"),
                    linework = null,
                    regionMasks = emptyMap<String, ByteArray>(),
                    regionColors = emptyMap<String, ColorData>(),
                    width = 16,
                    height = 16
                )
            }
        } finally {
            FeatureFlags.isColorStackEnabled = orig
        }
    }

    @Test
    fun testDeformationUnreachableWhenDisabled() {
        val orig = FeatureFlags.isDeformationEnabled
        try {
            FeatureFlags.isDeformationEnabled = false

            // DeformationSession creation throws
            assertThrows(IllegalStateException::class.java) {
                DeformationSession(
                    layerId = "layer_1",
                    originalWidth = 64,
                    originalHeight = 64,
                    originalPixels = IntArray(64 * 64)
                )
            }

            // DeformationEngine warpWithDisplacement throws
            assertThrows(IllegalStateException::class.java) {
                DeformationEngine.warpWithDisplacement(
                    srcPixels = IntArray(16),
                    width = 4,
                    height = 4,
                    destPixels = IntArray(16),
                    displacement = DisplacementField(4, 4),
                    edgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
                    interpolation = DeformInterpolation.BILINEAR
                )
            }

            // DeformationEngine warpWithMesh throws
            assertThrows(IllegalStateException::class.java) {
                DeformationEngine.warpWithMesh(
                    srcPixels = IntArray(16),
                    width = 4,
                    height = 4,
                    destPixels = IntArray(16),
                    mesh = WarpMesh.createUniformGrid(4f, 4f, 2, 2)
                )
            }
        } finally {
            FeatureFlags.isDeformationEnabled = orig
        }
    }

    @Test
    fun testAiModelsUnreachableWhenDisabled() = runBlocking {
        val orig = FeatureFlags.isAiEnabled
        try {
            FeatureFlags.isAiEnabled = false

            val dummyFile = File.createTempFile("ai_flag_test", ".tflite")
            dummyFile.deleteOnExit()
            try {
                val descriptor = AiModelCatalog.CURATED_CATALOG.first()
                val adapter = TfliteModelAdapter(descriptor, dummyFile)

                // Load returns UnsupportedRuntime
                val loadRes = adapter.load(dummyFile)
                assertTrue("Load must be UnsupportedRuntime", loadRes is LoadResult.UnsupportedRuntime)

                // Downloader returns Failure
                val downloader = AiModelDownloader(dummyFile.parentFile ?: File("."))
                val dlRes = downloader.downloadModelStreaming(descriptor, { ByteArrayInputStream(ByteArray(0)) })
                assertTrue("Downloader must fail", dlRes.isFailure)

                // Registry returns UnsupportedRuntime
                val registry = AiModelRegistry(downloader)
                val regRes = registry.loadModel(descriptor.id)
                assertTrue("Registry must be UnsupportedRuntime", regRes is LoadResult.UnsupportedRuntime)

                // Inference returns Error and never fake masks
                val inferRes = adapter.infer(IntArray(16), 4, 4, InferenceParams())
                assertTrue("Inference must be Error", inferRes is InferenceResult.Error)
            } finally {
                dummyFile.delete()
            }
        } finally {
            FeatureFlags.isAiEnabled = orig
        }
    }
}
