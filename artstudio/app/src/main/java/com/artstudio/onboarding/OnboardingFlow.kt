package com.artstudio.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.preferences.StudioPreferences
import com.artstudio.ui.preferences.StudioUserMode
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioIcons
import com.artstudio.ui.theme.StudioTheme

/**
 * First-launch 3-step onboarding flow.
 * Lets users choose their path (Just Draw, Learn the Studio, Full Power)
 * with progressive disclosure of advanced studio features.
 * All composables guaranteed strictly <= 150 lines.
 */
@Composable
fun OnboardingFlow(
    preferences: StudioPreferences,
    onStartJustDraw: () -> Unit,
    onStartTutorial: () -> Unit,
    onStartFullPower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing

    var selectedPath by remember { mutableStateOf(StudioUserMode.JUST_DRAW) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.l)
            .semantics { contentDescription = "Onboarding Welcome Screen" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.l)
        ) {
            OnboardingHeader()

            OnboardingPathCard(
                title = "Just Draw",
                description = "Jump straight to a clean canvas. Clean and clutter-free, ideal for sketching.",
                icon = StudioIcons.Brush,
                isSelected = selectedPath == StudioUserMode.JUST_DRAW,
                onSelect = { selectedPath = StudioUserMode.JUST_DRAW }
            )

            OnboardingPathCard(
                title = "Learn the Studio",
                description = "60-second guided walkthrough covering essential tools, undo, layers, and saving.",
                icon = StudioIcons.Aids,
                isSelected = selectedPath == StudioUserMode.LEARN,
                onSelect = { selectedPath = StudioUserMode.LEARN }
            )

            OnboardingPathCard(
                title = "Full Power",
                description = "All professional tools enabled immediately: deformation, Colour Lab, and comics.",
                icon = StudioIcons.AutoDraw,
                isSelected = selectedPath == StudioUserMode.FULL,
                onSelect = { selectedPath = StudioUserMode.FULL }
            )

            OnboardingActionButtons(
                selectedPath = selectedPath,
                onConfirm = {
                    preferences.userMode = selectedPath
                    preferences.hasCompletedOnboarding = true
                    when (selectedPath) {
                        StudioUserMode.JUST_DRAW -> onStartJustDraw()
                        StudioUserMode.LEARN -> onStartTutorial()
                        StudioUserMode.FULL -> onStartFullPower()
                    }
                },
                onSkip = {
                    preferences.userMode = StudioUserMode.FULL
                    preferences.hasCompletedOnboarding = true
                    onStartFullPower()
                }
            )
        }
    }
}

@Composable
private fun OnboardingHeader() {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = "Welcome to Art Studio",
            style = typography.headlineLarge,
            color = colors.textPrimary
        )
        Text(
            text = "Choose how you would like to begin your creative journey.",
            style = typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun OnboardingPathCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    val borderColor = if (isSelected) colors.accent.primary else colors.outlineSubtle
    val bgColor = if (isSelected) colors.surfaceElevated else colors.surfaceBase

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(cornerRadius.m))
            .clickable(onClick = onSelect)
            .padding(spacing.m)
            .semantics {
                role = Role.RadioButton
                contentDescription = "$title option. $description"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) colors.accent.primary else colors.textSecondary,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = description,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun OnboardingActionButtons(
    selectedPath: StudioUserMode,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    val spacing = StudioTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioButton(
            text = "Skip",
            onClick = onSkip,
            variant = StudioButtonVariant.Ghost,
            modifier = Modifier.weight(1f)
        )
        StudioButton(
            text = when (selectedPath) {
                StudioUserMode.JUST_DRAW -> "Start Drawing"
                StudioUserMode.LEARN -> "Begin Tutorial"
                StudioUserMode.FULL -> "Open Studio"
            },
            onClick = onConfirm,
            variant = StudioButtonVariant.Primary,
            modifier = Modifier.weight(2f)
        )
    }
}
