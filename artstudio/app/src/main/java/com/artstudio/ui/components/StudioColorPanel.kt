package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.artstudio.color.ColorData
import com.artstudio.color.ColorInputMode
import com.artstudio.color.StudioColorState
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Authoritative Studio Color Picker Panel.
 * Provides Color Wheel, RGB/HSV Sliders, Hex Input, Eyedropper toggle,
 * Foreground/Background color chips with swap, and Color History strip.
 */
@Composable
fun StudioColorPanel(
    colorState: StudioColorState,
    onColorSelected: (ColorData) -> Unit,
    onEyedropperToggle: () -> Unit,
    isEyedropperActive: Boolean = false,
    onOpenPalettes: () -> Unit = {},
    onOpenGradients: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var activeMode by remember { mutableStateOf(colorState.activeInputMode) }
    var currentColor by remember { mutableStateOf(colorState.activeColor) }
    var isEditingForeground by remember { mutableStateOf(true) }
    var hexInputText by remember { mutableStateOf(currentColor.hexRgb) }
    var hexError by remember { mutableStateOf<String?>(null) }

    fun updateColor(newColor: ColorData) {
        currentColor = newColor
        hexInputText = newColor.hexRgb
        hexError = null
        colorState.selectColor(newColor, updateForeground = isEditingForeground)
        onColorSelected(newColor)
    }

    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // 1. Header: Foreground & Background Color Chips, Swap, Default & Eyedropper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dual Foreground / Background Color Indicator
            Box(modifier = Modifier.size(StudioAccessibility.MinTouchTarget)) {
                // Background Chip (offset)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(colorState.fgbg.background.argbInt))
                        .border(
                            if (!isEditingForeground) 2.dp else 1.dp,
                            if (!isEditingForeground) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(role = Role.RadioButton) {
                            isEditingForeground = false
                            currentColor = colorState.fgbg.background
                            hexInputText = currentColor.hexRgb
                        }
                        .semantics {
                            this.role = Role.RadioButton
                            this.contentDescription = "Background color: ${colorState.fgbg.background.hexRgb}"
                        }
                )

                // Foreground Chip (front)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(colorState.fgbg.foreground.argbInt))
                        .border(
                            if (isEditingForeground) 2.dp else 1.dp,
                            if (isEditingForeground) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(role = Role.RadioButton) {
                            isEditingForeground = true
                            currentColor = colorState.fgbg.foreground
                            hexInputText = currentColor.hexRgb
                        }
                        .semantics {
                            this.role = Role.RadioButton
                            this.contentDescription = "Foreground color: ${colorState.fgbg.foreground.hexRgb}"
                        }
                )
            }

            // Quick actions: Swap, Default Black/White, Eyedropper
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StudioIconButton(
                    contentDescription = "Swap foreground and background colors",
                    onClick = {
                        colorState.fgbg.swapColors()
                        currentColor = if (isEditingForeground) colorState.fgbg.foreground else colorState.fgbg.background
                        hexInputText = currentColor.hexRgb
                        onColorSelected(currentColor)
                    },
                    icon = { Text("⇄", style = typography.titleSmall) }
                )

                StudioIconButton(
                    contentDescription = "Reset to default Black and White",
                    onClick = {
                        colorState.fgbg.resetDefaults()
                        currentColor = if (isEditingForeground) colorState.fgbg.foreground else colorState.fgbg.background
                        hexInputText = currentColor.hexRgb
                        onColorSelected(currentColor)
                    },
                    icon = { Text("⬛", style = typography.titleSmall) }
                )

                StudioIconButton(
                    contentDescription = "Eyedropper pixel sampler",
                    active = isEyedropperActive,
                    onClick = onEyedropperToggle,
                    icon = { Text("💧", style = typography.titleSmall) }
                )
            }
        }

        // 2. Mode Selector Tabs: Wheel, Sliders, Hex
        StudioSegmentedSelector(
            options = listOf(ColorInputMode.WHEEL, ColorInputMode.SLIDERS_RGB, ColorInputMode.HEX),
            selectedOption = activeMode,
            onOptionSelected = { activeMode = it },
            optionLabel = { mode ->
                when (mode) {
                    ColorInputMode.WHEEL -> "Wheel"
                    ColorInputMode.SLIDERS_RGB, ColorInputMode.SLIDERS_HSV -> "Sliders"
                    ColorInputMode.HEX -> "Hex"
                }
            }
        )

        // 3. Main Input Area based on activeMode
        when (activeMode) {
            ColorInputMode.WHEEL -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    StudioColorWheel(
                        currentColor = currentColor,
                        onColorChanged = { updateColor(it) },
                        size = 180.dp
                    )
                }

                // Brightness / Value Slider
                StudioSlider(
                    value = currentColor.value,
                    onValueChange = { newV ->
                        updateColor(ColorData.fromHsv(currentColor.hue, currentColor.saturation, newV, currentColor.alpha))
                    },
                    label = "Brightness",
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )

                // Alpha Slider
                StudioSlider(
                    value = currentColor.alpha,
                    onValueChange = { newA ->
                        updateColor(currentColor.copy(a = newA))
                    },
                    label = "Alpha (Opacity)",
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )
            }

            ColorInputMode.SLIDERS_RGB, ColorInputMode.SLIDERS_HSV -> {
                // Red Slider
                StudioSlider(
                    value = currentColor.red,
                    onValueChange = { newR ->
                        updateColor(ColorData(newR, currentColor.green, currentColor.blue, currentColor.alpha))
                    },
                    label = "Red (R)",
                    valueFormatter = { (it * 255f).toInt().toString() }
                )

                // Green Slider
                StudioSlider(
                    value = currentColor.green,
                    onValueChange = { newG ->
                        updateColor(ColorData(currentColor.red, newG, currentColor.blue, currentColor.alpha))
                    },
                    label = "Green (G)",
                    valueFormatter = { (it * 255f).toInt().toString() }
                )

                // Blue Slider
                StudioSlider(
                    value = currentColor.blue,
                    onValueChange = { newB ->
                        updateColor(ColorData(currentColor.red, currentColor.green, newB, currentColor.alpha))
                    },
                    label = "Blue (B)",
                    valueFormatter = { (it * 255f).toInt().toString() }
                )

                // Alpha Slider
                StudioSlider(
                    value = currentColor.alpha,
                    onValueChange = { newA ->
                        updateColor(currentColor.copy(a = newA))
                    },
                    label = "Alpha (A)",
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )
            }

            ColorInputMode.HEX -> {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Hex Code (#RGB, #RRGGBB, #AARRGGBB)",
                        style = typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(colors.surfaceBase)
                            .border(1.dp, if (hexError != null) colors.error else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                            .padding(horizontal = spacing.m, vertical = spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = hexInputText,
                            onValueChange = { text ->
                                hexInputText = text
                                try {
                                    val parsed = ColorData.fromHex(text)
                                    hexError = null
                                    updateColor(parsed)
                                } catch (e: Exception) {
                                    hexError = "Invalid hex"
                                }
                            },
                            singleLine = true,
                            textStyle = typography.bodyMedium.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                try {
                                    val parsed = ColorData.fromHex(hexInputText)
                                    hexError = null
                                    updateColor(parsed)
                                } catch (e: Exception) {
                                    hexError = "Invalid hex"
                                }
                            }),
                            modifier = Modifier.weight(1f)
                        )

                        // Color swatch preview
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(currentColor.argbInt))
                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(4.dp))
                        )
                    }

                    if (hexError != null) {
                        Text(
                            text = hexError!!,
                            style = typography.labelSmall,
                            color = colors.error
                        )
                    }

                    // Alpha Slider
                    StudioSlider(
                        value = currentColor.alpha,
                        onValueChange = { newA ->
                            updateColor(currentColor.copy(a = newA))
                        },
                        label = "Alpha (Opacity)",
                        valueFormatter = { "${(it * 100).toInt()}%" }
                    )
                }
            }
        }

        // 4. Color History (Recents Strip)
        val historyItems = colorState.history.items
        if (historyItems.isNotEmpty()) {
            Text(
                text = "Color History",
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(historyItems, key = { it.hexArgb }) { histColor ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(histColor.argbInt))
                            .border(
                                if (histColor.deltaRgb(currentColor) < 0.01f) 2.dp else 1.dp,
                                if (histColor.deltaRgb(currentColor) < 0.01f) colors.accent.primary else colors.outlineSubtle,
                                CircleShape
                            )
                            .clickable(role = Role.Button) {
                                updateColor(histColor)
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "History color ${histColor.hexRgb}"
                            }
                    )
                }
            }
        }

        // 5. Navigation shortcuts to Palettes & Gradients
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StudioButton(
                text = "🎨 Palettes",
                onClick = onOpenPalettes,
                variant = StudioButtonVariant.Secondary
            )
            StudioButton(
                text = "🌈 Gradients",
                onClick = onOpenGradients,
                variant = StudioButtonVariant.Secondary
            )
        }
    }
}
