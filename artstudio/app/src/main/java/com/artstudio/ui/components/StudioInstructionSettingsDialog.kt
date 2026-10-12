package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.cv.regionprofile.RegionProfileOverride
import com.artstudio.cv.regionprofile.RegionProfilePreset
import com.artstudio.cv.regionprofile.instruction.InstructionInterpretationRecord
import com.artstudio.cv.regionprofile.instruction.InstructionTranslator
import com.artstudio.cv.regionprofile.instruction.InterpretationStatus
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible dialog / drawer presenting natural language instruction interpretations.
 *
 * Shows original prompt, status banner, matched intents, explanations, and allows
 * editing every mapped parameter directly via sliders before analysis.
 */
@Composable
fun StudioInstructionSettingsDialog(
    record: InstructionInterpretationRecord,
    onApplySettings: (InstructionInterpretationRecord) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    var instructionText by remember { mutableStateOf(record.originalInstruction) }
    var currentRecord by remember { mutableStateOf(record) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Instruction Translation Assistant",
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Map artist descriptions to visible, editable CV parameters",
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            Text(
                text = "✕",
                style = typography.titleMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .defaultMinSize(minWidth = StudioAccessibility.MinTouchTarget, minHeight = StudioAccessibility.MinTouchTarget)
                    .clickable { onDismissRequest() }
                    .padding(8.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Close dialog"
                    }
            )
        }

        Spacer(modifier = Modifier.height(spacing.m))

        // Instruction Text Input Field
        OutlinedTextField(
            value = instructionText,
            onValueChange = { instructionText = it },
            label = { Text("Region Text Prompt / Instruction") },
            placeholder = { Text("e.g. Preserve tiny eyelashes and sharp hair tips") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Translate / Re-interpret Button
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                .clip(RoundedCornerShape(cornerRadius.s))
                .background(colors.accent.primary)
                .clickable {
                    currentRecord = InstructionTranslator.translate(
                        instruction = instructionText,
                        regionId = currentRecord.regionId
                    )
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Translate text to settings"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Translate to Settings",
                style = typography.labelMedium,
                color = colors.accent.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(spacing.m))

        // Interpretation Status Banner
        val statusBg = when (currentRecord.status) {
            InterpretationStatus.CONFIDENT -> colors.success.copy(alpha = 0.15f)
            InterpretationStatus.AMBIGUOUS -> colors.warning.copy(alpha = 0.15f)
            InterpretationStatus.UNSUPPORTED -> colors.surfaceElevated
            InterpretationStatus.MALICIOUS_OR_ADVERSARIAL -> colors.error.copy(alpha = 0.15f)
        }

        val statusText = when (currentRecord.status) {
            InterpretationStatus.CONFIDENT -> "✓ ${currentRecord.status.displayName} (${(currentRecord.confidence * 100).toInt()}%)"
            InterpretationStatus.AMBIGUOUS -> "⚠️ ${currentRecord.status.displayName}"
            InterpretationStatus.UNSUPPORTED -> "ℹ️ ${currentRecord.status.displayName}"
            InterpretationStatus.MALICIOUS_OR_ADVERSARIAL -> "🛑 ${currentRecord.status.displayName}"
        }

        val statusTextColor = when (currentRecord.status) {
            InterpretationStatus.CONFIDENT -> colors.success
            InterpretationStatus.AMBIGUOUS -> colors.warning
            InterpretationStatus.UNSUPPORTED -> colors.textSecondary
            InterpretationStatus.MALICIOUS_OR_ADVERSARIAL -> colors.error
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(statusBg)
                .border(1.dp, statusTextColor.copy(alpha = 0.5f), RoundedCornerShape(cornerRadius.m))
                .padding(spacing.s)
        ) {
            Column {
                Text(
                    text = statusText,
                    style = typography.labelLarge,
                    color = statusTextColor,
                    fontWeight = FontWeight.Bold
                )
                if (currentRecord.conflictWarning != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentRecord.conflictWarning!!,
                        style = typography.bodySmall,
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Matched Intents Chips
        if (currentRecord.matchedIntents.isNotEmpty()) {
            Text(
                text = "Detected Analysis Directives",
                style = typography.labelMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currentRecord.matchedIntents.forEach { m ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.xs))
                            .background(colors.accent.container)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = m.intent.displayName,
                            fontSize = 11.sp,
                            color = colors.accent.onContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.s))
        }

        // Explanations List
        if (currentRecord.explanations.isNotEmpty()) {
            Text(
                text = "Parameter Adjustments Explanation",
                style = typography.labelMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            currentRecord.explanations.forEach { exp ->
                Text(
                    text = "• $exp",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.m))
        }

        // Embedded Parameter Controls: User can edit every mapped value!
        StudioRegionProfileEditor(
            resolvedProfile = currentRecord.effectiveResolvedProfile,
            activePreset = currentRecord.effectiveResolvedProfile.basePreset,
            activeOverride = currentRecord.userOverrides,
            onSelectPreset = { preset ->
                val base = com.artstudio.cv.regionprofile.RegionAnalysisProfile.fromPreset(preset)
                currentRecord = currentRecord.copy(
                    effectiveResolvedProfile = com.artstudio.cv.regionprofile.RegionProfileResolver.resolve(
                        globalDefault = base,
                        override = currentRecord.userOverrides
                    )
                )
            },
            onUpdateOverride = { newOverrides ->
                currentRecord = currentRecord.withUserOverrides(newOverrides)
            },
            onResetAllOverrides = {
                currentRecord = currentRecord.withUserOverrides(RegionProfileOverride())
            }
        )

        Spacer(modifier = Modifier.height(spacing.m))

        // Provenance & Model Audit Metadata Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.s))
                .background(colors.surfaceElevated)
                .padding(spacing.s)
        ) {
            Column {
                Text(
                    text = "Provenance & Audit Trail",
                    style = typography.labelSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Provider: ${currentRecord.modelMetadata.providerName} | Engine: ${currentRecord.modelMetadata.modelIdentifier} v${currentRecord.modelMetadata.modelVersion}",
                    style = typography.labelSmall,
                    color = colors.textTertiary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Deterministic Offline Engine (zero remote transmission)",
                    style = typography.labelSmall,
                    color = colors.textTertiary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.m))

        // Bottom Actions: Apply vs Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .clickable { onDismissRequest() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    style = typography.labelMedium,
                    color = colors.textPrimary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.accent.primary)
                    .clickable {
                        onApplySettings(currentRecord)
                        onDismissRequest()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Apply Settings",
                    style = typography.labelMedium,
                    color = colors.accent.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
