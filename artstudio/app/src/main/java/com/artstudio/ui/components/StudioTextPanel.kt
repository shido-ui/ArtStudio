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
import com.artstudio.text.*
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Compose Bottom Sheet Panel for typography, text editing, and font controls.
 */
@Composable
fun StudioTextPanel(
    activeTextObject: TextObject?,
    onApplyText: (TextObject) -> Unit,
    onRasterizeText: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var textContent by remember(activeTextObject) { mutableStateOf(activeTextObject?.text ?: "Hello Art Studio") }
    var selectedFamily by remember(activeTextObject) { mutableStateOf(activeTextObject?.fontFamily ?: "sans-serif") }
    var fontSize by remember(activeTextObject) { mutableFloatStateOf(activeTextObject?.fontSize ?: 36f) }
    var selectedStyle by remember(activeTextObject) { mutableStateOf(activeTextObject?.fontStyle ?: TextFontStyle.NORMAL) }
    var selectedAlign by remember(activeTextObject) { mutableStateOf(activeTextObject?.alignment ?: TextAlignment.LEFT) }
    var selectedColor by remember(activeTextObject) { mutableStateOf(activeTextObject?.textColor ?: 0xFF000000L) }
    var isUnderline by remember(activeTextObject) { mutableStateOf(activeTextObject?.isUnderline ?: false) }
    var isStrikeThrough by remember(activeTextObject) { mutableStateOf(activeTextObject?.isStrikeThrough ?: false) }

    val allFamilies = remember { FontEngine.getAllAvailableFamilies() }

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
                text = "Typography & Text",
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

        // Text input
        OutlinedTextField(
            value = textContent,
            onValueChange = { textContent = it },
            label = { Text("Text Content") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accent.primary,
                unfocusedBorderColor = colors.outlineSubtle
            )
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Font Family Selector
        Text("Font Family", style = typography.labelMedium, color = colors.textSecondary)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allFamilies, key = { it }) { family ->
                val isSelected = family.equals(selectedFamily, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                        .border(1.dp, if (isSelected) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                        .clickable { selectedFamily = family }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = family,
                        color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                        style = typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Font Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Size: ${fontSize.toInt()} pt", style = typography.bodySmall, color = colors.textPrimary)
        }
        Slider(
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 12f..120f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent.primary,
                activeTrackColor = colors.accent.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Style and Alignment row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Style chips
            Column {
                Text("Style", style = typography.labelMedium, color = colors.textSecondary)
                Spacer(modifier = Modifier.height(spacing.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    TextFontStyle.entries.forEach { style ->
                        val isSel = selectedStyle == style
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius.xs))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .clickable { selectedStyle = style }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = style.name.take(4),
                                fontSize = 11.sp,
                                color = if (isSel) colors.accent.onPrimary else colors.textPrimary
                            )
                        }
                    }
                }
            }

            // Alignment chips
            Column {
                Text("Align", style = typography.labelMedium, color = colors.textSecondary)
                Spacer(modifier = Modifier.height(spacing.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    TextAlignment.entries.forEach { align ->
                        val isSel = selectedAlign == align
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius.xs))
                                .background(if (isSel) colors.accent.primary else colors.surfaceBase)
                                .clickable { selectedAlign = align }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when (align) {
                                    TextAlignment.LEFT -> "⇤"
                                    TextAlignment.CENTER -> "⇔"
                                    TextAlignment.RIGHT -> "⇥"
                                },
                                fontSize = 12.sp,
                                color = if (isSel) colors.accent.onPrimary else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Button(
                onClick = {
                    val updated = (activeTextObject ?: TextObject()).copy(
                        text = textContent,
                        fontFamily = selectedFamily,
                        fontSize = fontSize,
                        fontStyle = selectedStyle,
                        alignment = selectedAlign,
                        textColor = selectedColor,
                        isUnderline = isUnderline,
                        isStrikeThrough = isStrikeThrough
                    )
                    onApplyText(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent.primary),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (activeTextObject != null) "Update Text" else "Create Text Layer", color = colors.accent.onPrimary)
            }

            if (onRasterizeText != null && activeTextObject != null) {
                OutlinedButton(
                    onClick = onRasterizeText,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rasterize", color = colors.textPrimary)
                }
            }
        }
    }
}
