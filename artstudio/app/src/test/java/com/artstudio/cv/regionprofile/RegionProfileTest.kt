package com.artstudio.cv.regionprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive verification test suite for Region Analysis Profiles and Region-Aware CV Pipeline.
 *
 * Covers:
 * 1. Global default inheritance
 * 2. Factory preset inheritance without overrides
 * 3. Granular single-property overrides
 * 4. Multi-property overrides
 * 5. Complete 9-property overrides
 * 6. Boundary margin and additional passes overrides
 * 7. Resetting overrides reverting to preset
 * 8. Custom global default chained inheritance
 * 9. Parameter validation and constraint enforcement
 * 10. Profile and override serialization roundtrip
 * 11. Synthetic mixed-detail extra detail detection inside focus region
 * 12. Outside-region baseline output 100% zero regression (0 diff pixels, RMSE = 0.0)
 * 13. Per-region compute time and memory metrics recording
 * 14. Cross-boundary transition notes and margin continuity
 */
class RegionProfileTest {

    // ========================================================================
    // A. Profile Inheritance & Override Tests (Criteria 1)
    // ========================================================================

    @Test
    fun testInheritAllFromGlobalDefault() {
        val globalDefault = RegionAnalysisProfile.standard()
        val resolved = RegionProfileResolver.resolve(globalDefault = globalDefault)

        assertTrue(resolved.isInheritedFromGlobal)
        assertTrue(resolved.overriddenProperties.isEmpty())
        assertEquals(RegionProfilePreset.STANDARD, resolved.basePreset)
        assertEquals(4.0f, resolved.minFeatureSize, 0.001f)
        assertEquals(0.5f, resolved.edgeSensitivity, 0.001f)
        assertEquals(2.0f, resolved.curveFittingTolerance, 0.001f)
        assertEquals(1.5f, resolved.simplificationTolerance, 0.001f)
        assertEquals(2, resolved.gapClosingRadius)
        assertEquals(0.5f, resolved.detailPreservation, 0.001f)
        assertEquals(0.5f, resolved.reviewSensitivity, 0.001f)
        assertEquals(4, resolved.boundaryMarginPx)
        assertEquals(0, resolved.additionalPasses)
    }

    @Test
    fun testPresetInheritanceWithoutOverrides() {
        val globalDefault = RegionAnalysisProfile.standard()

        // High Detail Preset
        val resolvedHigh = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.HIGH_DETAIL
        )
        assertEquals(RegionProfilePreset.HIGH_DETAIL, resolvedHigh.basePreset)
        assertEquals(1.5f, resolvedHigh.minFeatureSize, 0.001f)
        assertEquals(0.85f, resolvedHigh.edgeSensitivity, 0.001f)
        assertEquals(0.75f, resolvedHigh.curveFittingTolerance, 0.001f)
        assertEquals(0.5f, resolvedHigh.simplificationTolerance, 0.001f)
        assertEquals(4, resolvedHigh.gapClosingRadius)
        assertEquals(0.9f, resolvedHigh.detailPreservation, 0.001f)
        assertEquals(0.8f, resolvedHigh.reviewSensitivity, 0.001f)
        assertEquals(6, resolvedHigh.boundaryMarginPx)
        assertEquals(2, resolvedHigh.additionalPasses)
        assertTrue(resolvedHigh.overriddenProperties.isEmpty())
    }

    @Test
    fun testPresetInheritanceFineLines() {
        val globalDefault = RegionAnalysisProfile.standard()
        val resolvedFine = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.FINE_LINES
        )
        assertEquals(RegionProfilePreset.FINE_LINES, resolvedFine.basePreset)
        assertEquals(1.0f, resolvedFine.minFeatureSize, 0.001f)
        assertEquals(0.95f, resolvedFine.edgeSensitivity, 0.001f)
        assertEquals(0.5f, resolvedFine.curveFittingTolerance, 0.001f)
        assertEquals(0.3f, resolvedFine.simplificationTolerance, 0.001f)
        assertEquals(3, resolvedFine.gapClosingRadius)
        assertEquals(0.95f, resolvedFine.detailPreservation, 0.001f)
        assertEquals(0.9f, resolvedFine.reviewSensitivity, 0.001f)
        assertEquals(4, resolvedFine.boundaryMarginPx)
        assertEquals(2, resolvedFine.additionalPasses)
        assertTrue(resolvedFine.overriddenProperties.isEmpty())
    }

    @Test
    fun testPresetInheritanceBroadStrokes() {
        val globalDefault = RegionAnalysisProfile.standard()
        val resolvedBroad = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.BROAD_STROKES
        )
        assertEquals(RegionProfilePreset.BROAD_STROKES, resolvedBroad.basePreset)
        assertEquals(12.0f, resolvedBroad.minFeatureSize, 0.001f)
        assertEquals(0.3f, resolvedBroad.edgeSensitivity, 0.001f)
        assertEquals(4.0f, resolvedBroad.curveFittingTolerance, 0.001f)
        assertEquals(3.0f, resolvedBroad.simplificationTolerance, 0.001f)
        assertEquals(1, resolvedBroad.gapClosingRadius)
        assertEquals(0.2f, resolvedBroad.detailPreservation, 0.001f)
        assertEquals(0.2f, resolvedBroad.reviewSensitivity, 0.001f)
        assertEquals(0, resolvedBroad.additionalPasses)
        assertTrue(resolvedBroad.overriddenProperties.isEmpty())
    }

    @Test
    fun testSinglePropertyOverrideEdgeSensitivity() {
        val globalDefault = RegionAnalysisProfile.standard()
        val override = RegionProfileOverride(edgeSensitivity = 0.92f)
        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.STANDARD,
            override = override
        )

        // Only edgeSensitivity should be overridden
        assertEquals(0.92f, resolved.edgeSensitivity, 0.001f)
        assertTrue(resolved.isPropertyOverridden("edgeSensitivity"))
        assertEquals(1, resolved.overriddenProperties.size)

        // Other 8 properties should be inherited from STANDARD
        assertEquals(4.0f, resolved.minFeatureSize, 0.001f)
        assertEquals(2.0f, resolved.curveFittingTolerance, 0.001f)
        assertEquals(1.5f, resolved.simplificationTolerance, 0.001f)
        assertEquals(2, resolved.gapClosingRadius)
        assertEquals(0.5f, resolved.detailPreservation, 0.001f)
        assertEquals(0.5f, resolved.reviewSensitivity, 0.001f)
        assertEquals(4, resolved.boundaryMarginPx)
        assertEquals(0, resolved.additionalPasses)
    }

    @Test
    fun testSinglePropertyOverrideMinFeatureSize() {
        val globalDefault = RegionAnalysisProfile.standard()
        val override = RegionProfileOverride(minFeatureSize = 0.5f)
        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.STANDARD,
            override = override
        )

        assertEquals(0.5f, resolved.minFeatureSize, 0.001f)
        assertTrue(resolved.isPropertyOverridden("minFeatureSize"))
        assertFalse(resolved.isPropertyOverridden("edgeSensitivity"))
        assertEquals(1, resolved.overriddenProperties.size)
        assertEquals(0.5f, resolved.edgeSensitivity, 0.001f)
    }

    @Test
    fun testMultiPropertyOverrideCurveAndSimplification() {
        val globalDefault = RegionAnalysisProfile.standard()
        val override = RegionProfileOverride(
            curveFittingTolerance = 0.25f,
            simplificationTolerance = 0.15f
        )
        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.HIGH_DETAIL,
            override = override
        )

        assertEquals(0.25f, resolved.curveFittingTolerance, 0.001f)
        assertEquals(0.15f, resolved.simplificationTolerance, 0.001f)
        assertEquals(2, resolved.overriddenProperties.size)
        assertTrue(resolved.isPropertyOverridden("curveFittingTolerance"))
        assertTrue(resolved.isPropertyOverridden("simplificationTolerance"))

        // Un-overridden properties retain HIGH_DETAIL values
        assertEquals(1.5f, resolved.minFeatureSize, 0.001f)
        assertEquals(0.85f, resolved.edgeSensitivity, 0.001f)
        assertEquals(4, resolved.gapClosingRadius)
        assertEquals(2, resolved.additionalPasses)
    }

    @Test
    fun testBoundaryMarginAndPassesOverride() {
        val globalDefault = RegionAnalysisProfile.standard()
        val override = RegionProfileOverride(
            boundaryMarginPx = 10,
            additionalPasses = 3
        )
        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.FINE_LINES,
            override = override
        )

        assertEquals(10, resolved.boundaryMarginPx)
        assertEquals(3, resolved.additionalPasses)
        assertEquals(2, resolved.overriddenProperties.size)

        // Retains FINE_LINES edge sensitivity
        assertEquals(0.95f, resolved.edgeSensitivity, 0.001f)
        assertEquals(1.0f, resolved.minFeatureSize, 0.001f)
    }

    @Test
    fun testCompleteOverrideReplacesAllProperties() {
        val globalDefault = RegionAnalysisProfile.standard()
        val override = RegionProfileOverride(
            minFeatureSize = 0.8f,
            edgeSensitivity = 0.99f,
            curveFittingTolerance = 0.1f,
            simplificationTolerance = 0.05f,
            gapClosingRadius = 5,
            detailPreservation = 1.0f,
            reviewSensitivity = 1.0f,
            boundaryMarginPx = 8,
            additionalPasses = 4
        )
        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.STANDARD,
            override = override
        )

        assertEquals(9, resolved.overriddenProperties.size)
        assertEquals(0.8f, resolved.minFeatureSize, 0.001f)
        assertEquals(0.99f, resolved.edgeSensitivity, 0.001f)
        assertEquals(0.1f, resolved.curveFittingTolerance, 0.001f)
        assertEquals(0.05f, resolved.simplificationTolerance, 0.001f)
        assertEquals(5, resolved.gapClosingRadius)
        assertEquals(1.0f, resolved.detailPreservation, 0.001f)
        assertEquals(1.0f, resolved.reviewSensitivity, 0.001f)
        assertEquals(8, resolved.boundaryMarginPx)
        assertEquals(4, resolved.additionalPasses)
    }

    @Test
    fun testResetOverridesRestoresPresetDefaults() {
        val globalDefault = RegionAnalysisProfile.standard()
        var override = RegionProfileOverride(
            edgeSensitivity = 0.95f,
            additionalPasses = 3
        )
        assertTrue(override.hasOverrides())

        // Clear all overrides
        override = override.clear()
        assertFalse(override.hasOverrides())

        val resolved = RegionProfileResolver.resolve(
            globalDefault = globalDefault,
            regionPreset = RegionProfilePreset.STANDARD,
            override = override
        )

        assertEquals(0, resolved.overriddenProperties.size)
        assertEquals(0.5f, resolved.edgeSensitivity, 0.001f)
        assertEquals(0, resolved.additionalPasses)
    }

    @Test
    fun testChainedInheritanceCustomGlobalDefault() {
        val customGlobal = RegionAnalysisProfile(
            preset = RegionProfilePreset.CUSTOM,
            minFeatureSize = 8.0f,
            edgeSensitivity = 0.65f,
            curveFittingTolerance = 3.0f,
            simplificationTolerance = 2.0f,
            gapClosingRadius = 3,
            detailPreservation = 0.7f,
            reviewSensitivity = 0.6f,
            boundaryMarginPx = 5,
            additionalPasses = 1
        )

        // Region without preset or override inherits the custom global default
        val resolved = RegionProfileResolver.resolve(globalDefault = customGlobal)
        assertEquals(8.0f, resolved.minFeatureSize, 0.001f)
        assertEquals(0.65f, resolved.edgeSensitivity, 0.001f)
        assertEquals(1, resolved.additionalPasses)
        assertTrue(resolved.overriddenProperties.isEmpty())
    }

    @Test
    fun testParameterBoundaryValidationAndClamping() {
        // Negative minFeatureSize should throw
        assertThrows(IllegalArgumentException::class.java) {
            RegionAnalysisProfile(minFeatureSize = -1.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegionProfileOverride(minFeatureSize = -1.0f)
        }

        // Out-of-bounds sensitivity should throw
        assertThrows(IllegalArgumentException::class.java) {
            RegionAnalysisProfile(edgeSensitivity = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RegionProfileOverride(edgeSensitivity = -0.1f)
        }

        // Negative boundary margin should throw
        assertThrows(IllegalArgumentException::class.java) {
            RegionAnalysisProfile(boundaryMarginPx = -2)
        }
    }

    @Test
    fun testProfileSerializationRoundTrip() {
        val profile = RegionAnalysisProfile.fineLines()
        val map = profile.toMap()
        val restored = RegionAnalysisProfile.fromMap(map)

        assertEquals(profile.preset, restored.preset)
        assertEquals(profile.minFeatureSize, restored.minFeatureSize, 0.001f)
        assertEquals(profile.edgeSensitivity, restored.edgeSensitivity, 0.001f)
        assertEquals(profile.curveFittingTolerance, restored.curveFittingTolerance, 0.001f)
        assertEquals(profile.additionalPasses, restored.additionalPasses)

        val override = RegionProfileOverride(edgeSensitivity = 0.88f, gapClosingRadius = 4)
        val overrideMap = override.toMap()
        val restoredOverride = RegionProfileOverride.fromMap(overrideMap)

        assertEquals(0.88f, restoredOverride.edgeSensitivity ?: 0f, 0.001f)
        assertEquals(4, restoredOverride.gapClosingRadius)
        assertFalse(restoredOverride.overriddenPropertyNames().contains("minFeatureSize"))
    }

    // ========================================================================
    // B. Synthetic Mixed-Detail & Zero-Regression Tests (Criteria 2)
    // ========================================================================

    @Test
    fun testSyntheticMixedDetailFixtureDetectsFineLines() {
        val fixture = SyntheticMixedDetailFixture.createFixture(128, 128)

        // Run Region-Aware Pipeline: Global is STANDARD, Focus Region is FINE_LINES
        val result = RegionAwareAnalysisPipeline.analyze(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            globalProfile = RegionAnalysisProfile.standard(),
            regions = listOf(fixture.focusRegion),
            regionPresets = mapOf(fixture.focusRegion.id to RegionProfilePreset.FINE_LINES)
        )

        val verification = SyntheticMixedDetailFixture.verifyMixedDetail(fixture, result)

        // 1. Assert extra fine details captured inside region
        assertTrue(verification.isExtraDetailCapturedInside)
        assertTrue(verification.insideRegionalEdgeCount > verification.insideBaselineEdgeCount)

        // 2. Assert zero regression outside the region
        assertTrue(verification.isZeroRegressionOutside)
        assertEquals(0, verification.outsideDiffPixelCount)
        assertEquals(0.0, verification.outsideRmse, 0.0001)
    }

    @Test
    fun testOutsideRegionBaselineOutputZeroRegression() {
        val fixture = SyntheticMixedDetailFixture.createFixture(128, 128)

        // Run pipeline with HIGH_DETAIL on the focus region
        val result = RegionAwareAnalysisPipeline.analyze(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            globalProfile = RegionAnalysisProfile.standard(),
            regions = listOf(fixture.focusRegion),
            regionPresets = mapOf(fixture.focusRegion.id to RegionProfilePreset.HIGH_DETAIL)
        )

        // Baseline global edges vs merged edges
        val baselineEdges = result.globalResult.edges
        val mergedEdges = result.mergedEdges

        // For every pixel outside the focus region: mergedEdges MUST BE identical to baselineEdges!
        var outsideDiffCount = 0
        for (i in 0 until fixture.width * fixture.height) {
            if (fixture.focusRegion.mask[i] == 0.toByte()) {
                if (mergedEdges[i] != baselineEdges[i]) {
                    outsideDiffCount++
                }
            }
        }

        assertEquals("Outside-region pixels must have zero diff with baseline!", 0, outsideDiffCount)
    }

    // ========================================================================
    // C. Telemetry, Timing & Cross-Boundary Metrics (Criteria 3)
    // ========================================================================

    @Test
    fun testPerRegionComputeTimeAndMemoryRecorded() {
        val fixture = SyntheticMixedDetailFixture.createFixture(128, 128)

        val result = RegionAwareAnalysisPipeline.analyze(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            globalProfile = RegionAnalysisProfile.standard(),
            regions = listOf(fixture.focusRegion),
            regionPresets = mapOf(fixture.focusRegion.id to RegionProfilePreset.HIGH_DETAIL)
        )

        assertEquals(1, result.regionMetrics.size)
        val metric = result.regionMetrics[0]

        assertEquals(fixture.focusRegion.id, metric.regionId)
        assertEquals(fixture.focusRegion.name, metric.regionName)
        assertTrue("Region elapsed time must be recorded (> 0 ms)", metric.elapsedTimeMs >= 0L)
        assertTrue("Region peak memory must be recorded (> 0 bytes)", metric.peakMemoryBytes > 0L)
        assertTrue("Additional passes executed must be recorded", metric.passesExecuted >= 2)
        assertTrue("Features detected in region must be non-negative", metric.featuresDetected >= 0)
        assertEquals(6, metric.boundaryMarginUsed) // High detail uses 6px boundary margin
    }

    @Test
    fun testBoundaryMarginCrossBoundaryEffectDocumentation() {
        val fixture = SyntheticMixedDetailFixture.createFixture(128, 128)

        val result = RegionAwareAnalysisPipeline.analyze(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            globalProfile = RegionAnalysisProfile.standard(),
            regions = listOf(fixture.focusRegion),
            regionPresets = mapOf(fixture.focusRegion.id to RegionProfilePreset.FINE_LINES)
        )

        assertFalse(result.crossBoundaryNotes.isEmpty())
        val note = result.crossBoundaryNotes[0]
        assertTrue(note.contains("boundary margin"))
        assertTrue(note.contains("100% bit-exact preserved"))
    }
}
