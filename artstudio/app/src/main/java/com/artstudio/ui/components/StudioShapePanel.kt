package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.shape.GeometricShapeType
import com.artstudio.shape.ShapeCreationParams
import com.artstudio.shape.ShapeTargetMode
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Compose Bottom Sheet Panel for creating filled and outlined geometric shapes.
 */
@Composable
fun StudioShapePanel(
    onCreateShape: (ShapeCreationParams) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var selectedType by remember { mutableStateOf(GeometricShapeType.RECTANGLE) }
    var isFilled by remember { mutableStateOf(true) }
    var isOutlined by remember { mutableStateOf(true) }
    var strokeWidth by remember { mutableFloatStateOf(4.0f) }
    var polygonSides by remember { mutableIntStateOf(5) }
    var starPoints by remember { mutableIntStateOf(5) }
    var targetMode by remember { mutableStateOf(ShapeTargetMode.NEW_VECTOR_LAYER) }

    Column(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated.copy(alpha = 0.95f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Geometric Shapes",
                style = typography.titleSmall,
                color = colors.textPrimary
            )
            StudioButton(
                text = "Done",
                onClick = onDismissRequest,
                modifier = Modifier.height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Shape Type Chips
        Text("Shape Type", style = typography.labelMedium, color = colors.textSecondary)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(GeometricShapeType.entries.toList(), key = { it.name }) { type ->
                val isSel = selectedType == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                        .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                        .clickable { selectedType = type }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (type) {
                            GeometricShapeType.RECTANGLE -> "Rectangle"
                            GeometricShapeType.ROUNDED_RECTANGLE -> "Round Rect"
                            GeometricShapeType.ELLIPSE -> "Ellipse"
                            GeometricShapeType.CIRCLE -> "Circle"
                            GeometricShapeType.LINE -> "Line"
                            GeometricShapeType.POLYGON -> "Polygon"
                            GeometricShapeType.STAR -> "Star"
                        },
                        color = if (isSel) colors.accent.onPrimary else colors.textPrimary,
                        style = typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Style Toggles: Filled / Outlined
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isFilled,
                    onCheckedChange = { isFilled = it },
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent.primary)
                )
                Text("Fill", style = typography.bodySmall, color = colors.textPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isOutlined,
                    onCheckedChange = { isOutlined = it },
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent.primary)
                )
                Text("Outline", style = typography.bodySmall, color = colors.textPrimary)
            }
        }

        // Stroke Width Slider (if outlined)
        if (isOutlined) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stroke: ${strokeWidth.toInt()} px", style = typography.bodySmall, color = colors.textPrimary)
            }
            Slider(
                value = strokeWidth,
                onValueChange = { strokeWidth = it },
                valueRange = 1f..32f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent.primary,
                    activeTrackColor = colors.accent.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Polygon / Star specific controls
        if (selectedType == GeometricShapeType.POLYGON) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sides: $polygonSides", style = typography.bodySmall, color = colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    listOf(3, 4, 5, 6, 8).forEach { s ->
                        OutlinedButton(
                            onClick = { polygonSides = s },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("$s", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (selectedType == GeometricShapeType.STAR) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Points: $starPoints", style = typography.bodySmall, color = colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    listOf(4, 5, 6, 8).forEach { p ->
                        OutlinedButton(
                            onClick = { starPoints = p },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("$p", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Destination mode selector
        Text("Layer Destination", style = typography.labelMedium, color = colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            listOf(
                ShapeTargetMode.NEW_VECTOR_LAYER to "New Vector",
                ShapeTargetMode.ACTIVE_VECTOR_LAYER to "Active Vector",
                ShapeTargetMode.STAMP_TO_RASTER_LAYER to "Stamp Raster"
            ).forEach { (mode, label) ->
                val isSel = targetMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                        .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                        .clickable { targetMode = mode }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSel) colors.accent.onPrimary else colors.textPrimary,
                        style = typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Action Button
        Button(
            onClick = {
                val params = ShapeCreationParams(
                    type = selectedType,
                    x = 100f,
                    y = 100f,
                    width = 300f,
                    height = 300f,
                    rx = 20f,
                    ry = 20f,
                    polygonSides = polygonSides,
                    starPoints = starPoints,
                    isFilled = isFilled,
                    isOutlined = isOutlined,
                    strokeWidth = strokeWidth,
                    targetMode = targetMode
                )
                onCreateShape(params)
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Shape", color = colors.accent.onPrimary)
        }
    }
}
