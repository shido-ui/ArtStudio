package com.artstudio.layer

import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.io.DocumentConversionAuditor
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerMask
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.LayerTransform
import com.artstudio.history.HistoryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Authoritative Test Suite for Prompt 18:
 * Editable Layer Masks, Clipping Relationships, and Alpha-Lock Controls.
 */
class LayerMaskAndClippingTest {

    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager
    private lateinit var rootLayerId: String

    @Before
    fun setUp() {
        val doc = ArtDocument()
        historyManager = HistoryManager(doc)
        layerManager = LayerManager(historyManager)
        rootLayerId = layerManager.currentDocument.activeLayerId
    }

    @Test
    fun testAddLayerMask_createsMaskAndRecordsCommand() {
        val mask = layerManager.addLayerMask(rootLayerId)
        assertNotNull("Mask should be created", mask)
        val layer = layerManager.currentDocument.findLayerById(rootLayerId)
        assertNotNull(layer?.mask)
        assertTrue(layer?.mask?.enabled == true)
        assertFalse(layer?.mask?.inverted == true)
        assertEquals(layerManager.currentDocument.canvas.width, layer?.mask?.width)
        assertEquals(layerManager.currentDocument.canvas.height, layer?.mask?.height)

        // Undo removes the mask
        assertTrue(historyManager.canUndo)
        historyManager.undo()
        val revertedLayer = layerManager.currentDocument.findLayerById(rootLayerId)
        assertNull("Undo must revert layer mask to null", revertedLayer?.mask)

        // Redo restores the mask
        assertTrue(historyManager.canRedo)
        historyManager.redo()
        val redoneLayer = layerManager.currentDocument.findLayerById(rootLayerId)
        assertNotNull("Redo must re-apply the layer mask", redoneLayer?.mask)
        assertEquals(mask?.id, redoneLayer?.mask?.id)
    }

    @Test
    fun testRemoveLayerMask_removesMaskAndIsReversible() {
        val mask = layerManager.addLayerMask(rootLayerId)
        assertNotNull(mask)

        // Remove mask
        val removed = layerManager.removeLayerMask(rootLayerId)
        assertTrue("removeLayerMask should return true", removed)
        assertNull(layerManager.currentDocument.findLayerById(rootLayerId)?.mask)

        // Undo restores original mask
        historyManager.undo()
        val restored = layerManager.currentDocument.findLayerById(rootLayerId)?.mask
        assertNotNull(restored)
        assertEquals(mask?.id, restored?.id)

        // Redo removes mask again
        historyManager.redo()
        assertNull(layerManager.currentDocument.findLayerById(rootLayerId)?.mask)
    }

    @Test
    fun testInvertMask_togglesInvertedAndIsReversible() {
        layerManager.addLayerMask(rootLayerId)
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.inverted)

        // Invert mask
        val inverted = layerManager.invertMask(rootLayerId)
        assertTrue(inverted)
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.inverted)

        // Undo restores un-inverted
        historyManager.undo()
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.inverted)

        // Redo inverts again
        historyManager.redo()
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.inverted)
    }

    @Test
    fun testToggleMaskEnabled_togglesEnabledAndIsReversible() {
        layerManager.addLayerMask(rootLayerId)
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.enabled)

        // Toggle mask disabled
        layerManager.toggleMaskEnabled(rootLayerId)
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.enabled)

        // Undo enables mask
        historyManager.undo()
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.enabled)

        // Redo disables mask
        historyManager.redo()
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.enabled)
    }

    @Test
    fun testTransformMask_updatesTransformAndIsReversible() {
        layerManager.addLayerMask(rootLayerId)
        val initialTransform = layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.transform
        assertTrue(initialTransform.isIdentity)

        val newTransform = LayerTransform(
            translationX = 50f,
            translationY = -25f,
            scaleX = 1.5f,
            scaleY = 1.5f,
            rotationDegrees = 45f
        )
        layerManager.transformMask(rootLayerId, newTransform)
        val transformed = layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.transform
        assertEquals(50f, transformed.translationX, 0.001f)
        assertEquals(1.5f, transformed.scaleX, 0.001f)
        assertEquals(45f, transformed.rotationDegrees, 0.001f)

        // Undo restores identity transform
        historyManager.undo()
        val reverted = layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.transform
        assertTrue(reverted.isIdentity)

        // Redo reapplies custom transform
        historyManager.redo()
        val redone = layerManager.currentDocument.findLayerById(rootLayerId)!!.mask!!.transform
        assertEquals(50f, redone.translationX, 0.001f)
    }

    @Test
    fun testToggleClippingMask_updatesClippingStateAndIsReversible() {
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.isClippingMask)

        // Toggle clipping mask
        layerManager.toggleLayerClippingMask(rootLayerId)
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.isClippingMask)

        // Undo restores unclipped state
        historyManager.undo()
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.isClippingMask)

        // Redo restores clipping mask state
        historyManager.redo()
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.isClippingMask)
    }

    @Test
    fun testToggleAlphaLock_updatesAlphaLockAndIsReversible() {
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.isAlphaLocked)

        // Toggle alpha lock
        layerManager.toggleLayerAlphaLock(rootLayerId)
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.isAlphaLocked)

        // Undo restores unlocked state
        historyManager.undo()
        assertFalse(layerManager.currentDocument.findLayerById(rootLayerId)!!.isAlphaLocked)

        // Redo restores alpha locked state
        historyManager.redo()
        assertTrue(layerManager.currentDocument.findLayerById(rootLayerId)!!.isAlphaLocked)
    }

    @Test
    fun testLayerTreeUiState_editTargetSwitchingAndSnapshot() {
        assertEquals(LayerEditTarget.CONTENT, layerManager.uiState.activeEditTarget)
        assertFalse(layerManager.isEditingMask())

        layerManager.setEditTarget(LayerEditTarget.MASK)
        assertEquals(LayerEditTarget.MASK, layerManager.uiState.activeEditTarget)
        assertTrue(layerManager.isEditingMask())

        // Test snapshot deep copying edit target
        val snap = layerManager.uiState.snapshot()
        assertEquals(LayerEditTarget.MASK, snap.activeEditTarget)

        // Removing mask resets edit target to CONTENT
        layerManager.addLayerMask(rootLayerId)
        layerManager.removeLayerMask(rootLayerId)
        assertEquals(LayerEditTarget.CONTENT, layerManager.uiState.activeEditTarget)
        assertFalse(layerManager.isEditingMask())
    }

    @Test
    fun testLayerMaskBufferCompositing_modulatesAlphaCorrectly() {
        val opaqueRed = (255 shl 24) or (255 shl 16) or (0 shl 8) or 0
        val whiteMaskPixel = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255
        val blackMaskPixel = (255 shl 24) or (0 shl 16) or (0 shl 8) or 0
        val midGrayMaskPixel = (255 shl 24) or (128 shl 16) or (128 shl 8) or 128

        // Test 1: White mask preserves full alpha
        val pixels1 = intArrayOf(opaqueRed)
        val maskPixels1 = intArrayOf(whiteMaskPixel)
        LayerBlendCompositor.applyMaskToBuffer(pixels1, maskPixels1, inverted = false, enabled = true)
        assertEquals(255, (pixels1[0] ushr 24) and 0xFF)
        assertEquals(255, (pixels1[0] ushr 16) and 0xFF)

        // Test 2: Black mask makes pixel completely transparent
        val pixels2 = intArrayOf(opaqueRed)
        val maskPixels2 = intArrayOf(blackMaskPixel)
        LayerBlendCompositor.applyMaskToBuffer(pixels2, maskPixels2, inverted = false, enabled = true)
        assertEquals(0, (pixels2[0] ushr 24) and 0xFF)

        // Test 3: 50% Gray mask halves alpha
        val pixels3 = intArrayOf(opaqueRed)
        val maskPixels3 = intArrayOf(midGrayMaskPixel)
        LayerBlendCompositor.applyMaskToBuffer(pixels3, maskPixels3, inverted = false, enabled = true)
        val a3 = (pixels3[0] ushr 24) and 0xFF
        assertTrue("Alpha should be ~128, got $a3", a3 in 127..129)

        // Test 4: Inverted white mask makes pixel transparent
        val pixels4 = intArrayOf(opaqueRed)
        LayerBlendCompositor.applyMaskToBuffer(pixels4, maskPixels1, inverted = true, enabled = true)
        assertEquals(0, (pixels4[0] ushr 24) and 0xFF)

        // Test 5: Inverted black mask preserves full alpha
        val pixels5 = intArrayOf(opaqueRed)
        LayerBlendCompositor.applyMaskToBuffer(pixels5, maskPixels2, inverted = true, enabled = true)
        assertEquals(255, (pixels5[0] ushr 24) and 0xFF)

        // Test 6: Disabled mask makes no changes
        val pixels6 = intArrayOf(opaqueRed)
        LayerBlendCompositor.applyMaskToBuffer(pixels6, maskPixels2, inverted = false, enabled = false)
        assertEquals(255, (pixels6[0] ushr 24) and 0xFF)
    }

    @Test
    fun testClippingMaskBufferCompositing_clipsToParentAlpha() {
        val clipBlue = (255 shl 24) or (0 shl 16) or (0 shl 8) or 255
        val fullyOpaqueBase = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255
        val transparentBase = 0
        val halfAlphaBase = (128 shl 24) or (100 shl 16) or (100 shl 8) or 100

        // Pixels where base is fully opaque -> 100% clip alpha preserved
        val clip1 = intArrayOf(clipBlue)
        val base1 = intArrayOf(fullyOpaqueBase)
        LayerBlendCompositor.applyClippingMaskToBuffer(clip1, base1)
        assertEquals(255, (clip1[0] ushr 24) and 0xFF)
        assertEquals(255, clip1[0] and 0xFF) // Blue preserved

        // Pixels where base is transparent -> clip layer alpha becomes 0
        val clip2 = intArrayOf(clipBlue)
        val base2 = intArrayOf(transparentBase)
        LayerBlendCompositor.applyClippingMaskToBuffer(clip2, base2)
        assertEquals(0, (clip2[0] ushr 24) and 0xFF)

        // Pixels where base is 50% alpha -> clip layer alpha becomes 50%
        val clip3 = intArrayOf(clipBlue)
        val base3 = intArrayOf(halfAlphaBase)
        LayerBlendCompositor.applyClippingMaskToBuffer(clip3, base3)
        val a3 = (clip3[0] ushr 24) and 0xFF
        assertTrue("Alpha should be ~128, got $a3", a3 in 127..129)
    }

    @Test
    fun testAlphaLockBufferCompositing_strictlyPreservesTransparency() {
        val transparentDst = 0
        val opaqueRedDst = (255 shl 24) or (255 shl 16) or 0
        val semiTransparentDst = (128 shl 24) or (0 shl 16) or (255 shl 8) or 0 // Semi-green

        val strokeBlue = (255 shl 24) or 255 // Opaque Blue

        val baseBuffer = intArrayOf(transparentDst, opaqueRedDst, semiTransparentDst)
        val strokeBuffer = intArrayOf(strokeBlue, strokeBlue, strokeBlue)

        LayerBlendCompositor.applyAlphaLockStroke(baseBuffer, strokeBuffer)

        // 1. Transparent pixel: MUST remain strictly 0 alpha and 0 RGB (zero bleeding outside silhouette)
        assertEquals("Transparent pixel must not be modified by alpha lock stroke", 0, baseBuffer[0])

        // 2. Opaque red pixel: Replaced by stroke blue via SRC_ATOP while retaining alpha = 255
        assertEquals(255, (baseBuffer[1] ushr 24) and 0xFF)
        assertEquals(0, (baseBuffer[1] ushr 16) and 0xFF) // Red replaced
        assertEquals(255, baseBuffer[1] and 0xFF) // Blue drawn

        // 3. Semi-transparent green pixel: Alpha must be strictly preserved at 128
        val semiAlpha = (baseBuffer[2] ushr 24) and 0xFF
        assertEquals("Destination alpha must be strictly preserved", 128, semiAlpha)
        assertEquals(255, baseBuffer[2] and 0xFF) // Blue blended
    }

    @Test
    fun testDocumentConversionAuditor_surfacesAllUnsupportedFeatures() {
        // Build document with mask, clipping mask, and alpha lock
        layerManager.addLayerMask(rootLayerId)
        val layer2 = layerManager.createLayer("Clipped Layer")
        layerManager.setLayerClippingMask(layer2.id, true)
        layerManager.setLayerAlphaLock(layer2.id, true)

        val doc = layerManager.currentDocument

        // Audit for FLAT_RASTER (PNG/JPEG)
        val flatReport = DocumentConversionAuditor.auditExport(doc, "FLAT_RASTER")
        assertTrue(flatReport.hasLossyConversions)
        val features = flatReport.warnings.map { it.feature }
        assertTrue("Must report LAYER_MASK warning", features.contains("LAYER_MASK"))
        assertTrue("Must report CLIPPING_MASK warning", features.contains("CLIPPING_MASK"))
        assertTrue("Must report ALPHA_LOCK warning", features.contains("ALPHA_LOCK"))

        // Audit for OPENRASTER (ORA)
        val oraReport = DocumentConversionAuditor.auditExport(doc, "OPENRASTER")
        assertTrue(oraReport.hasLossyConversions)
        val oraFeatures = oraReport.warnings.map { it.feature }
        assertTrue("Must report LAYER_MASK for ORA", oraFeatures.contains("LAYER_MASK"))
        assertTrue("Must report ALPHA_LOCK for ORA", oraFeatures.contains("ALPHA_LOCK"))

        // Audit for LEGACY_V1
        val v1Report = DocumentConversionAuditor.auditExport(doc, "LEGACY_V1")
        assertTrue(v1Report.hasLossyConversions)
        assertTrue(v1Report.warnings.any { it.feature == "LAYER_MASK" })
    }

    @Test
    fun testDocumentPersistence_roundtripsMaskClippingAndAlphaLock() {
        val maskTransform = LayerTransform(translationX = 10f, translationY = 20f, scaleX = 1.2f, scaleY = 1.2f, rotationDegrees = 15f)
        val customMask = LayerMask(
            id = UUID.randomUUID().toString(),
            enabled = false,
            inverted = true,
            assetPath = "masks/mask_01.png",
            width = 1024,
            height = 1024,
            transform = maskTransform,
            linkTransformWithLayer = false
        )
        layerManager.addLayerMask(rootLayerId, customMask)
        layerManager.setLayerClippingMask(rootLayerId, true)
        layerManager.setLayerAlphaLock(rootLayerId, true)

        val originalDoc = layerManager.currentDocument
        val docMap = DocumentSerializer.toMap(originalDoc)
        val restoredDoc = DocumentSerializer.fromMap(docMap)

        val restoredLayer = restoredDoc.findLayerById(rootLayerId)
        assertNotNull(restoredLayer)
        assertTrue(restoredLayer!!.isClippingMask)
        assertTrue(restoredLayer.isAlphaLocked)

        val restoredMask = restoredLayer.mask
        assertNotNull(restoredMask)
        assertEquals(customMask.id, restoredMask!!.id)
        assertFalse(restoredMask.enabled)
        assertTrue(restoredMask.inverted)
        assertEquals("masks/mask_01.png", restoredMask.assetPath)
        assertEquals(1024, restoredMask.width)
        assertEquals(1024, restoredMask.height)
        assertFalse(restoredMask.linkTransformWithLayer)
        assertEquals(10f, restoredMask.transform.translationX, 0.001f)
        assertEquals(1.2f, restoredMask.transform.scaleX, 0.001f)
        assertEquals(15f, restoredMask.transform.rotationDegrees, 0.001f)
    }
}
