package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artstudio.R
import com.artstudio.autodraw.AutoDrawV1Pipeline
import com.artstudio.autodraw.AutoDrawV1Result
import com.artstudio.core.DefaultDocumentState
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.job.AutoDrawJob
import com.artstudio.job.JobScheduler
import com.artstudio.job.JobState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Accessible Modal Dialog for AutoDraw v1 Classical Line Art Vectorization & Stroke Replay.
 * Complies with strict architectural constraints: background JobScheduler execution on WorkLane.ANALYSIS,
 * progress, cooperative cancellation, downscaling, and <= 150 lines per composable.
 */
@Composable
fun StudioAutoDrawDialog(
    drawingView: ArtStudioDrawingView?,
    onDismissRequest: () -> Unit,
    onStartPlayback: ((AutoDrawV1Result) -> Unit)? = null,
    jobScheduler: JobScheduler = remember { JobScheduler() },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentStage by remember { mutableStateOf("Ready to process line art") }
    var autoDrawResult by remember { mutableStateOf<AutoDrawV1Result?>(null) }
    var currentJobId by remember { mutableStateOf<String?>(null) }
    var observerJob by remember { mutableStateOf<Job?>(null) }

    Dialog(
        onDismissRequest = {
            currentJobId?.let { jobScheduler.cancelJob(it) }
            observerJob?.cancel()
            onDismissRequest()
        },
        properties = DialogProperties(dismissOnBackPress = !isProcessing, dismissOnClickOutside = !isProcessing)
    ) {
        AutoDrawDialogContent(
            isProcessing = isProcessing,
            progress = progress,
            currentStage = currentStage,
            result = autoDrawResult,
            onProcess = {
                val doc = drawingView?.document?.authoritativeDocument
                val selId = doc?.selectedLayerId
                val activeLayer = selId?.let { doc.findLayerById(it) } as? LayerNode.RasterLayer
                val w = activeLayer?.width ?: doc?.canvas?.width ?: 1024
                val h = activeLayer?.height ?: doc?.canvas?.height ?: 1024
                val ds = drawingView?.document as? DefaultDocumentState
                val pixels = if (selId != null) {
                    ds?.getOrRenderLayerPixels(selId) ?: IntArray(w * h)
                } else {
                    IntArray(w * h)
                }

                val job = AutoDrawJob(
                    pixels = pixels,
                    width = w,
                    height = h,
                    maxDimension = AutoDrawV1Pipeline.DEFAULT_MAX_DIMENSION,
                    normalizeIllumination = true
                )
                currentJobId = job.id
                isProcessing = true
                progress = 0f
                currentStage = "Queued in JobScheduler analysis lane"
                autoDrawResult = null

                jobScheduler.enqueue(job)

                observerJob?.cancel()
                observerJob = coroutineScope.launch {
                    job.progress.collect { prog ->
                        progress = prog.progress
                        if (prog.currentStep.isNotEmpty()) {
                            currentStage = prog.currentStep
                        }
                    }
                }
                coroutineScope.launch {
                    job.state.collect { state ->
                        when (state) {
                            JobState.SUCCEEDED -> {
                                autoDrawResult = job.result
                                isProcessing = false
                                currentStage = "AutoDraw linework extraction complete"
                            }
                            JobState.CANCELLED -> {
                                isProcessing = false
                                currentStage = "Processing cancelled"
                            }
                            JobState.FAILED -> {
                                isProcessing = false
                                currentStage = job.error.value?.message ?: "Processing failed"
                            }
                            JobState.RUNNING -> {
                                isProcessing = true
                            }
                            else -> {}
                        }
                    }
                }
            },
            onCancel = {
                currentJobId?.let { jobScheduler.cancelJob(it) }
                observerJob?.cancel()
                isProcessing = false
                currentStage = "Cancelled"
            },
            onReplay = {
                autoDrawResult?.let { onStartPlayback?.invoke(it) }
            },
            onCommit = {
                val doc = drawingView?.document?.authoritativeDocument
                val res = autoDrawResult
                if (doc != null && res != null) {
                    val intRes = AutoDrawV1Pipeline.integrateIntoDocument(doc, res)
                    val group = intRes.document.rootGroup.children.find { it.id == intRes.autoDrawGroupId }
                    if (group != null) {
                        drawingView.document.historyManager?.execute(
                            com.artstudio.history.AddLayerCommand(layer = group)
                        )
                    }
                    drawingView.invalidate()
                    onDismissRequest()
                }
            },
            onDismiss = {
                currentJobId?.let { jobScheduler.cancelJob(it) }
                observerJob?.cancel()
                onDismissRequest()
            },
            modifier = modifier
        )
    }
}

@Composable
private fun AutoDrawDialogContent(
    isProcessing: Boolean,
    progress: Float,
    currentStage: String,
    result: AutoDrawV1Result?,
    onProcess: () -> Unit,
    onCancel: () -> Unit,
    onReplay: () -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineStrong, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.xl)
            .semantics { contentDescription = "AutoDraw v1 Dialog" }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            Text(
                text = stringResource(R.string.tool_autodraw),
                style = typography.titleMedium,
                color = colors.textPrimary
            )

            Text(
                text = currentStage,
                style = typography.bodyMedium,
                color = colors.textSecondary
            )

            if (isProcessing) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = colors.accent.primary,
                    trackColor = colors.surfaceBase
                )
            }

            if (result != null) {
                Text(
                    text = "Generated ${result.vectorObjects.size} vector paths from ${result.contours.size} contours in ${result.executionTimeMs} ms",
                    style = typography.bodySmall,
                    color = colors.accent.primary
                )
            }

            AutoDrawButtonRow(
                isProcessing = isProcessing,
                hasResult = result != null,
                onProcess = onProcess,
                onCancel = onCancel,
                onReplay = onReplay,
                onCommit = onCommit,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AutoDrawButtonRow(
    isProcessing: Boolean,
    hasResult: Boolean,
    onProcess: () -> Unit,
    onCancel: () -> Unit,
    onReplay: () -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = StudioTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isProcessing) {
            StudioButton(
                text = stringResource(R.string.action_cancel),
                variant = StudioButtonVariant.Secondary,
                onClick = onCancel,
                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
            )
        } else if (!hasResult) {
            StudioButton(
                text = stringResource(R.string.action_cancel),
                variant = StudioButtonVariant.Secondary,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
            )
            StudioButton(
                text = "Trace Linework",
                variant = StudioButtonVariant.Primary,
                onClick = onProcess,
                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
            )
        } else {
            StudioButton(
                text = stringResource(R.string.action_replay_strokes),
                variant = StudioButtonVariant.Secondary,
                onClick = onReplay,
                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
            )
            StudioButton(
                text = stringResource(R.string.action_commit_layer),
                variant = StudioButtonVariant.Primary,
                onClick = onCommit,
                modifier = Modifier.heightIn(min = StudioAccessibility.MinTouchTarget)
            )
        }
    }
}
