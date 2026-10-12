package com.artstudio.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.artstudio.core.*
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.components.StudioSegmentedSelector
import com.artstudio.ui.theme.StudioTheme

/**
 * Developer Diagnostics Screen for real-time touch, stylus, pressure,
 * gesture arbitration, and coordinate transformation auditing.
 */
@Composable
fun InputDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var diagnosticsState by remember { mutableStateOf(InputDiagnosticsState()) }
    var latestSample by remember { mutableStateOf<PointerDiagnosticInfo?>(null) }
    var selectedInteractionMode by remember { mutableStateOf(TouchInteractionMode.DRAW_AND_NAVIGATE) }
    var selectedPressureMode by remember { mutableStateOf(PressureCalibrationMode.AUTO_DETECT) }
    var palmSuppressionEnabled by remember { mutableStateOf(true) }

    var drawingViewRef by remember { mutableStateOf<ArtStudioDrawingView?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Input & Stylus Diagnostics",
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )
                Text(
                    text = "Developer-only real-time digitizer and pointer telemetry",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            StudioButton(
                text = "Back",
                onClick = onNavigateBack,
                variant = StudioButtonVariant.Secondary
            )
        }

        // Live Telemetry Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.m)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tool: ${latestSample?.toolType ?: ToolType.FINGER}",
                        style = typography.labelMedium,
                        color = colors.accent.primary
                    )
                    Text(
                        text = "Gesture: ${diagnosticsState.arbiterState}",
                        style = typography.labelMedium,
                        color = if (diagnosticsState.arbiterState == TouchGestureState.DRAWING) colors.accent.primary else colors.textSecondary
                    )
                    Text(
                        text = "Latency: ${String.format("%.2f ms", diagnosticsState.lastEventLatencyMs)}",
                        style = typography.labelMedium,
                        color = colors.textSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val rawP = latestSample?.rawPressure ?: 0f
                    val effP = latestSample?.effectivePressure ?: 0f
                    val isFallback = latestSample?.isPressureFallback == true
                    Text(
                        text = "Pressure: Raw ${String.format("%.2f", rawP)} -> Eff ${String.format("%.2f", effP)}${if (isFallback) " (Simulated)" else ""}",
                        style = typography.bodySmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Tilt: ${String.format("%.1f°", latestSample?.tiltDegrees ?: 0f)} | Orient: ${String.format("%.1f°", latestSample?.orientationDegrees ?: 0f)}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sx = latestSample?.rawScreenX ?: 0f
                    val sy = latestSample?.rawScreenY ?: 0f
                    val dx = latestSample?.documentX ?: 0f
                    val dy = latestSample?.documentY ?: 0f
                    Text(
                        text = "Coords: Screen (${String.format("%.0f", sx)}, ${String.format("%.0f", sy)}) -> Doc (${String.format("%.1f", dx)}, ${String.format("%.1f", dy)})",
                        style = typography.bodySmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Events: ${diagnosticsState.totalEventsCount} | Batched: ${diagnosticsState.totalBatchedPointsCount} | Missed: ${diagnosticsState.missedPointerEventsCount}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Mode Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Gesture Routing", style = typography.labelSmall, color = colors.textSecondary)
                StudioSegmentedSelector(
                    options = TouchInteractionMode.entries,
                    selectedOption = selectedInteractionMode,
                    onOptionSelected = {
                        selectedInteractionMode = it
                        drawingViewRef?.inputPipeline?.arbiter?.interactionMode = it
                    },
                    optionLabel = {
                        when (it) {
                            TouchInteractionMode.DRAW_AND_NAVIGATE -> "Draw + Nav"
                            TouchInteractionMode.STYLUS_ONLY_DRAWING -> "Stylus Only"
                            TouchInteractionMode.NAVIGATION_ONLY -> "Nav Only"
                        }
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Pressure Calibration", style = typography.labelSmall, color = colors.textSecondary)
                StudioSegmentedSelector(
                    options = PressureCalibrationMode.entries,
                    selectedOption = selectedPressureMode,
                    onOptionSelected = {
                        selectedPressureMode = it
                        drawingViewRef?.inputPipeline?.pressureResolver?.mode = it
                    },
                    optionLabel = {
                        when (it) {
                            PressureCalibrationMode.AUTO_DETECT -> "Auto"
                            PressureCalibrationMode.FORCE_VELOCITY_FALLBACK -> "Velocity"
                            PressureCalibrationMode.HARDWARE_ONLY -> "Hardware"
                            PressureCalibrationMode.FIXED_FALLBACK -> "Fixed"
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioButton(
                text = if (palmSuppressionEnabled) "Palm Rejection: ON" else "Palm Rejection: OFF",
                onClick = {
                    palmSuppressionEnabled = !palmSuppressionEnabled
                    drawingViewRef?.inputPipeline?.palmSuppressionEnabled = palmSuppressionEnabled
                },
                variant = if (palmSuppressionEnabled) StudioButtonVariant.Primary else StudioButtonVariant.Secondary
            )

            StudioButton(
                text = "Clear Test Canvas",
                onClick = { drawingViewRef?.clearStrokes() },
                variant = StudioButtonVariant.Secondary
            )
        }

        // Live Interactive Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(cornerRadius.m))
                .border(2.dp, colors.accent.primary, RoundedCornerShape(cornerRadius.m))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ArtStudioDrawingView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        drawingViewRef = this
                        inputPipeline.palmSuppressionEnabled = palmSuppressionEnabled
                        inputPipeline.arbiter.interactionMode = selectedInteractionMode
                        inputPipeline.pressureResolver.mode = selectedPressureMode

                        // Attach diagnostics listener
                        val originalListener = inputPipeline.listener
                        inputPipeline.listener = object : PointerInputListener {
                            override fun onStrokeStart(stroke: BrushStroke) {
                                originalListener?.onStrokeStart(stroke)
                            }

                            override fun onStrokeUpdate(stroke: BrushStroke, newPoints: List<StrokePoint>) {
                                originalListener?.onStrokeUpdate(stroke, newPoints)
                            }

                            override fun onStrokeEnd(stroke: BrushStroke) {
                                originalListener?.onStrokeEnd(stroke)
                            }

                            override fun onStrokeCancelled(strokeId: String) {
                                originalListener?.onStrokeCancelled(strokeId)
                            }

                            override fun onViewportPanZoom(dx: Float, dy: Float, scaleFactor: Float, focalX: Float, focalY: Float) {
                                originalListener?.onViewportPanZoom(dx, dy, scaleFactor, focalX, focalY)
                            }

                            override fun onDiagnosticsUpdated(state: InputDiagnosticsState) {
                                diagnosticsState = state
                                latestSample = state.recentDiagnostics.lastOrNull()
                                originalListener?.onDiagnosticsUpdated(state)
                            }
                        }
                    }
                }
            )
        }
    }
}
