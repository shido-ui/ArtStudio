package com.artstudio.layer

import com.artstudio.core.BrushConfig
import com.artstudio.core.BrushStroke
import com.artstudio.core.StrokePoint
import com.artstudio.core.DefaultDocumentState
import com.artstudio.document.model.*
import com.artstudio.history.HistoryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LayerGroupAndMergeTest {

    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager

    @Before
    fun setUp() {
        val initialDoc = ArtDocument()
        historyManager = HistoryManager(initialDoc)
        layerManager = LayerManager(historyManager)
    }

    @Test
    fun testCreateEmptyGroupAtRootLevel() {
        val initialCount = layerManager.getLayers().size
        val group = layerManager.createGroup(name = "Character Group")

        assertEquals("Character Group", group.name)
        assertTrue(group.children.isEmpty())
        assertEquals(initialCount + 1, layerManager.getLayers().size)
        assertEquals(group.id, layerManager.getSelectedLayerId())
        assertTrue(layerManager.uiState.isGroupExpanded(group.id))

        // Verify Undo restores pre-group state
        assertTrue(historyManager.undo() != null)
        assertNull(layerManager.currentDocument.findLayerById(group.id))
        assertEquals(initialCount, layerManager.getLayers().size)

        // Verify Redo reapplies group
        assertTrue(historyManager.redo() != null)
        assertNotNull(layerManager.currentDocument.findLayerById(group.id))
        assertEquals(initialCount + 1, layerManager.getLayers().size)
    }

    @Test
    fun testCreateGroupWithSelectedLayers() {
        val layer1 = layerManager.createLayer(name = "Lineart")
        val layer2 = layerManager.createLayer(name = "Colors")

        val group = layerManager.createGroup(
            name = "Inks & Flats",
            layerIdsToInclude = listOf(layer1.id, layer2.id)
        )

        assertEquals(2, group.children.size)
        assertEquals(listOf(layer1.id, layer2.id), group.children.map { it.id })

        // Source layers must be moved inside the group and no longer direct children of root
        assertFalse(layerManager.getLayers().any { it.id == layer1.id })
        assertFalse(layerManager.getLayers().any { it.id == layer2.id })

        // Undo must restore them to root
        historyManager.undo()
        assertTrue(layerManager.getLayers().any { it.id == layer1.id })
        assertTrue(layerManager.getLayers().any { it.id == layer2.id })
        assertNull(layerManager.currentDocument.findLayerById(group.id))
    }

    @Test
    fun testEnforceMaxGroupDepthLimit() {
        assertEquals(5, LayerHierarchyConstants.MAX_GROUP_DEPTH)

        // Depth 1
        val g1 = layerManager.createGroup(name = "Level 1")
        // Depth 2
        val g2 = layerManager.createGroup(name = "Level 2", parentGroupId = g1.id)
        // Depth 3
        val g3 = layerManager.createGroup(name = "Level 3", parentGroupId = g2.id)
        // Depth 4
        val g4 = layerManager.createGroup(name = "Level 4", parentGroupId = g3.id)
        // Depth 5 (Maximum allowed depth)
        val g5 = layerManager.createGroup(name = "Level 5", parentGroupId = g4.id)

        assertNotNull(g5)
        assertEquals(5, layerManager.currentDocument.computeGroupDepth(g5.id))

        // Depth 6 must be strictly rejected
        assertThrows(MaxGroupDepthExceededException::class.java) {
            layerManager.createGroup(name = "Level 6 (Exceeded)", parentGroupId = g5.id)
        }
    }

    @Test
    fun testPreventCyclicGroupNesting() {
        val parentGroup = layerManager.createGroup(name = "Parent")
        val childGroup = layerManager.createGroup(name = "Child", parentGroupId = parentGroup.id)
        val grandChildGroup = layerManager.createGroup(name = "Grandchild", parentGroupId = childGroup.id)

        // Attempting to move parentGroup into its own childGroup or grandChildGroup must be rejected
        val moveIntoChildResult = layerManager.moveLayerIntoGroup(parentGroup.id, childGroup.id)
        assertFalse("Parent group cannot be moved into its child", moveIntoChildResult)

        val moveIntoGrandChildResult = layerManager.moveLayerIntoGroup(parentGroup.id, grandChildGroup.id)
        assertFalse("Parent group cannot be moved into its grandchild", moveIntoGrandChildResult)
    }

    @Test
    fun testMoveLayerIntoAndOutOfGroup() {
        val layer = layerManager.createLayer(name = "Floating Sketch")
        val group = layerManager.createGroup(name = "Folder")

        // Move into group
        val movedIn = layerManager.moveLayerIntoGroup(layer.id, group.id)
        assertTrue(movedIn)

        val updatedGroup = layerManager.currentDocument.findLayerById(group.id) as LayerNode.LayerGroup
        assertEquals(1, updatedGroup.children.size)
        assertEquals(layer.id, updatedGroup.children[0].id)
        assertEquals(group.id, layerManager.currentDocument.findParentGroupId(layer.id))

        // Move out of group (back to root)
        val movedOut = layerManager.moveLayerOutOfGroup(layer.id)
        assertTrue(movedOut)
        assertEquals(layerManager.currentDocument.rootGroup.id, layerManager.currentDocument.findParentGroupId(layer.id))

        // Undo move out
        historyManager.undo()
        assertEquals(group.id, layerManager.currentDocument.findParentGroupId(layer.id))

        // Undo move in
        historyManager.undo()
        assertEquals(layerManager.currentDocument.rootGroup.id, layerManager.currentDocument.findParentGroupId(layer.id))
    }

    @Test
    fun testUngroupOperationPreservesChildrenAndOrder() {
        val layer1 = layerManager.createLayer(name = "Layer A")
        val layer2 = layerManager.createLayer(name = "Layer B")
        val group = layerManager.createGroup(name = "ToUngroup", layerIdsToInclude = listOf(layer1.id, layer2.id))

        assertTrue(layerManager.ungroup(group.id))

        // Group node should no longer exist
        assertNull(layerManager.currentDocument.findLayerById(group.id))

        // Children must now be in root group
        val rootChildren = layerManager.getLayers()
        assertTrue(rootChildren.any { it.id == layer1.id })
        assertTrue(rootChildren.any { it.id == layer2.id })

        // Undo ungroup
        historyManager.undo()
        assertNotNull(layerManager.currentDocument.findLayerById(group.id))
        val restoredGroup = layerManager.currentDocument.findLayerById(group.id) as LayerNode.LayerGroup
        assertEquals(2, restoredGroup.children.size)
    }

    @Test
    fun testUiExpansionStateDecoupledFromDocument() {
        val group = layerManager.createGroup(name = "Foldable Group")
        val initialTimestamp = layerManager.currentDocument.updatedAt
        val initialHistoryCount = historyManager.undoCount

        // Toggle UI expansion
        layerManager.uiState.collapseGroup(group.id)
        assertFalse(layerManager.uiState.isGroupExpanded(group.id))

        layerManager.uiState.expandGroup(group.id)
        assertTrue(layerManager.uiState.isGroupExpanded(group.id))

        // Invariant: UI folding must NOT mutate document timestamp or history stack
        assertEquals(initialTimestamp, layerManager.currentDocument.updatedAt)
        assertEquals(initialHistoryCount, historyManager.undoCount)
    }

    @Test
    fun testMultiSelectionState() {
        val l1 = layerManager.createLayer(name = "Item 1")
        val l2 = layerManager.createLayer(name = "Item 2")
        val l3 = layerManager.createLayer(name = "Item 3")

        layerManager.setMultiSelection(listOf(l1.id, l2.id))
        assertEquals(setOf(l1.id, l2.id), layerManager.getSelectedLayerIds())

        layerManager.toggleMultiSelect(l3.id)
        assertEquals(setOf(l1.id, l2.id, l3.id), layerManager.getSelectedLayerIds())

        layerManager.toggleMultiSelect(l1.id)
        assertEquals(setOf(l2.id, l3.id), layerManager.getSelectedLayerIds())

        layerManager.clearMultiSelection()
        assertTrue(layerManager.getSelectedLayerIds().isEmpty())
    }

    @Test
    fun testMergeSelectedLayersReversible() {
        val l1 = layerManager.createLayer(name = "Highlights")
        val l2 = layerManager.createLayer(name = "Shadows")

        val merged = layerManager.mergeSelectedLayers(listOf(l1.id, l2.id))
        assertNotNull(merged)
        assertTrue(merged!!.name.contains("Merged"))

        // Original layers removed, merged layer present
        assertNull(layerManager.currentDocument.findLayerById(l1.id))
        assertNull(layerManager.currentDocument.findLayerById(l2.id))
        assertNotNull(layerManager.currentDocument.findLayerById(merged.id))
        assertEquals(merged.id, layerManager.getSelectedLayerId())

        // Undo merge restores original layers with identities intact
        historyManager.undo()
        assertNotNull(layerManager.currentDocument.findLayerById(l1.id))
        assertNotNull(layerManager.currentDocument.findLayerById(l2.id))
        assertNull(layerManager.currentDocument.findLayerById(merged.id))
    }

    @Test
    fun testMergeSelectedRequiresAtLeastTwoLayers() {
        val singleLayer = layerManager.createLayer(name = "Only Layer")
        val result = layerManager.mergeSelectedLayers(listOf(singleLayer.id))

        assertNull("Merge requires at least 2 layers", result)
        assertNotNull(layerManager.currentDocument.findLayerById(singleLayer.id))
    }

    @Test
    fun testMergeVisiblePreservesHiddenLayers() {
        val visible1 = layerManager.createLayer(name = "Visible 1")
        val visible2 = layerManager.createLayer(name = "Visible 2")
        val hiddenLayer = layerManager.createLayer(name = "Hidden Reference")
        layerManager.setLayerVisibility(hiddenLayer.id, false)

        val mergedVisible = layerManager.mergeVisibleLayers()
        assertNotNull(mergedVisible)

        // Hidden layer must be preserved!
        val preservedHidden = layerManager.currentDocument.findLayerById(hiddenLayer.id)
        assertNotNull("Hidden layer must be preserved", preservedHidden)
        assertFalse(preservedHidden!!.visible)

        // Visible layers merged into 1
        assertNull(layerManager.currentDocument.findLayerById(visible1.id))
        assertNull(layerManager.currentDocument.findLayerById(visible2.id))
        assertNotNull(layerManager.currentDocument.findLayerById(mergedVisible!!.id))

        // Undo restores full tree
        historyManager.undo()
        assertNotNull(layerManager.currentDocument.findLayerById(visible1.id))
        assertNotNull(layerManager.currentDocument.findLayerById(visible2.id))
        assertNotNull(layerManager.currentDocument.findLayerById(hiddenLayer.id))
    }

    @Test
    fun testFlattenRequiresExplicitConfirmation() {
        layerManager.createLayer(name = "L1")
        layerManager.createLayer(name = "L2")

        // Invariant: Unconfirmed flatten must throw IllegalStateException
        assertThrows(IllegalStateException::class.java) {
            layerManager.flattenDocument(confirmed = false)
        }
    }

    @Test
    fun testFlattenDocumentSucceedsWithConfirmationAndUndo() {
        val g = layerManager.createGroup(name = "Folder")
        val l1 = layerManager.createLayer(name = "Sub 1", parentGroupId = g.id)
        val l2 = layerManager.createLayer(name = "Top Layer")

        val flattened = layerManager.flattenDocument(confirmed = true)
        assertNotNull(flattened)
        assertEquals("Background", flattened.name)

        // All previous groups and layers collapsed down to exactly 1 layer
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(flattened.id, layerManager.getLayers()[0].id)
        assertNull(layerManager.currentDocument.findLayerById(g.id))

        // Full Undo completely restores previous multi-level structure
        historyManager.undo()
        assertNotNull(layerManager.currentDocument.findLayerById(g.id))
        assertNotNull(layerManager.currentDocument.findLayerById(l1.id))
        assertNotNull(layerManager.currentDocument.findLayerById(l2.id))
        assertNull(layerManager.currentDocument.findLayerById(flattened.id))
    }

    @Test
    fun testStrokeRemappingOnMergeAndUndo() {
        val docState = DefaultDocumentState()
        val lm = docState.layerManager

        val l1 = lm.createLayer(name = "Layer 1")
        val l2 = lm.createLayer(name = "Layer 2")

        val stroke1 = BrushStroke(
            id = "stroke-test-1",
            config = BrushConfig(size = 10f),
            points = mutableListOf(StrokePoint(10f, 10f, 1f, 0f, 0L)),
            layerId = l1.id
        )
        val stroke2 = BrushStroke(
            id = "stroke-test-2",
            config = BrushConfig(size = 20f),
            points = mutableListOf(StrokePoint(20f, 20f, 1f, 0f, 1L)),
            layerId = l2.id
        )

        docState.addCommittedStroke(stroke1)
        docState.addCommittedStroke(stroke2)

        assertEquals(2, docState.committedStrokes.size)
        assertEquals(l1.id, docState.committedStrokes[0].layerId)
        assertEquals(l2.id, docState.committedStrokes[1].layerId)

        // Merge L1 and L2
        val merged = lm.mergeSelectedLayers(listOf(l1.id, l2.id))
        assertNotNull(merged)

        // All strokes now associated with merged layer
        assertEquals(2, docState.committedStrokes.size)
        assertEquals(merged!!.id, docState.committedStrokes[0].layerId)
        assertEquals(merged.id, docState.committedStrokes[1].layerId)

        // Undo merge restores original stroke layer IDs
        docState.undo()
        assertEquals(2, docState.committedStrokes.size)
        assertEquals(l1.id, docState.committedStrokes[0].layerId)
        assertEquals(l2.id, docState.committedStrokes[1].layerId)

        // Redo merge re-associates strokes with merged layer
        docState.redo()
        assertEquals(2, docState.committedStrokes.size)
        assertEquals(merged.id, docState.committedStrokes[0].layerId)
        assertEquals(merged.id, docState.committedStrokes[1].layerId)
    }
}
