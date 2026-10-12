package com.artstudio.core

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Gate tests proving canvases above memory limits are rejected cleanly.
 */
class CanvasMemoryBudgetValidatorTest {

    @Before
    fun setUp() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_2GB)
    }

    @After
    fun tearDown() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_4GB)
    }

    @Test
    fun testLowMemoryDevice_canvasAboveLimitIsRejectedCleanly() {
        // Under 2 GB device tier, max dimension is 2048 px
        val result = CanvasMemoryBudgetValidator.validateCanvas(4096, 4096, layerCount = 1)
        assertTrue(result is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)
        val rejected = result as CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected
        assertTrue(rejected.rejectionReason.contains("exceeds maximum supported dimension"))
    }

    @Test
    fun testLowMemoryDevice_validCanvasIsAccepted() {
        val result = CanvasMemoryBudgetValidator.validateCanvas(1024, 1024, layerCount = 2)
        assertTrue(result is CanvasMemoryBudgetValidator.CanvasValidationResult.Accepted)
    }

    @Test
    fun testLayerCountExceedingBudgetIsRejected() {
        // At 2048x2048 on 2 GB RAM, max layers is 4
        val result = CanvasMemoryBudgetValidator.validateCanvas(2048, 2048, layerCount = 16)
        assertTrue(result is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)
        val rejected = result as CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected
        assertTrue(rejected.rejectionReason.contains("exceeds device memory limit"))
    }

    @Test
    fun testHighEndDevice_allowsHigherResolutions() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_8GB_PLUS)
        val result = CanvasMemoryBudgetValidator.validateCanvas(4096, 4096, layerCount = 4)
        assertTrue(result is CanvasMemoryBudgetValidator.CanvasValidationResult.Accepted)
    }

    @Test
    fun testInvalidDimensions_rejected() {
        val resZero = CanvasMemoryBudgetValidator.validateCanvas(0, 100)
        assertTrue(resZero is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)

        val resNegative = CanvasMemoryBudgetValidator.validateCanvas(-500, -500)
        assertTrue(resNegative is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)
    }
}
