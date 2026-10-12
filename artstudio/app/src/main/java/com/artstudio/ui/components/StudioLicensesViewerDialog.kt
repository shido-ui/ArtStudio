package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible viewer dialog presenting full license provenance and SPDX declarations
 * for all bundled and downloadable serverless assets.
 */
@Composable
fun StudioLicensesViewerDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val licenseText = """
        ART STUDIO — OPEN SOURCE & ASSET PROVENANCE
        
        1. BUNDLED BRUSHES & TEXTURES:
           • Licenses: Creative Commons Zero 1.0 Universal (CC0-1.0)
           • Authors: Art Studio Team, David Revoy, Krita Foundation, MyPaint Community
           • Terms: Public domain dedication. Unrestricted commercial and personal use.
        
        2. BUNDLED & DOWNLOADABLE TYPOGRAPHY:
           • Carrois Gothic SC (Jan Fromm) — SIL Open Font License 1.1
           • Cutive Mono (Vernon Adams) — SIL Open Font License 1.1
           • Bubble Sans (Abay Emes) — SIL Open Font License 1.1
           • Tomson Talks (Frank Bruder) — SIL Open Font License 1.1
           • Terms: Permission granted to use, study, copy, merge, embed, and redistribute.
        
        3. COMIC & MANGA ASSETS:
           • Halftone Screentone Patterns — CC0-1.0 Universal
           • Speech Balloon & Tail Vectors — CC0-1.0 Universal
           • Panel Grids & Layout Templates — CC0-1.0 Universal
        
        4. OPTIONAL AI MODELS & RUNTIMES:
           • MediaPipe Selfie Segmenter — Apache-2.0
           • AnimeGANv2 (akhaliq) — MIT License
           • Real-ESRGAN (xinntao) — BSD-3-Clause
           • U2Net Rembg (Daniel Gatis) — Apache-2.0
           • TensorFlow Lite Runtime — Apache-2.0
        
        100% AUDITABLE PROVENANCE: Zero viral licenses. No proprietary telemetry or tracking.
    """.trimIndent()

    StudioDialog(
        title = "Asset Licenses & Attributions",
        body = "Complete provenance for all bundled and downloadable Pro Assets.",
        confirmText = "Close",
        cancelText = null,
        onConfirm = onDismissRequest,
        onDismiss = onDismissRequest,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceBase)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .padding(spacing.m)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = licenseText,
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    )
}
