package com.wefit.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.ui.components.GradientButton
import com.wefit.app.ui.components.NumberField
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SegmentedSelector
import com.wefit.app.ui.components.TextInput
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitLogo
import com.wefit.app.ui.components.WeFitTopBar

@Composable
fun ProfileSetupScreen(
    isEditing: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileSetupContent(
        state = state,
        isEditing = isEditing,
        onBack = onBack,
        onUpdate = viewModel::update,
        onUnitsChange = viewModel::setUnits,
        onSave = { viewModel.save(isEditing, onDone) },
        onSkip = { viewModel.skip(onDone) },
    )
}

@Composable
fun ProfileSetupContent(
    state: ProfileFormState,
    isEditing: Boolean,
    onBack: (() -> Unit)?,
    onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit,
    onUnitsChange: (UnitSystem) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(topBar = { WeFitTopBar(if (isEditing) "Edit Profile" else "Set Up Your Profile", onBack = onBack) }) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val weightUnit = UnitConverter.weightUnit(state.units)
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isEditing) {
                    WeFitLogo(Modifier.align(Alignment.CenterHorizontally), markSize = 36.dp)
                    Text(
                        "Tell us a little about yourself so WeFit can estimate distance and calories. Everything is optional except your step goal, and stays on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionHeader("Measurement units")
                SegmentedSelector(
                    options = listOf("Metric (kg, cm, km)", "Imperial (lb, ft, mi)"),
                    selectedIndex = state.units.ordinal,
                    onSelect = { onUnitsChange(UnitSystem.entries[it]) },
                )
                WeFitCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader("About you")
                        TextInput(state.name, { v -> onUpdate { it.copy(name = v) } }, "Name (optional)",
                            error = state.errors[ProfileField.NAME], maxLength = 40, modifier = Modifier.testTag("field_name"))
                        NumberField(state.age, { v -> onUpdate { it.copy(age = v) } }, "Age (optional)", decimal = false,
                            suffix = "years", error = state.errors[ProfileField.AGE])
                        if (state.units == UnitSystem.METRIC) {
                            NumberField(state.heightCm, { v -> onUpdate { it.copy(heightCm = v) } }, "Height (optional)",
                                suffix = "cm", error = state.errors[ProfileField.HEIGHT], decimal = false)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(state.heightFt, { v -> onUpdate { it.copy(heightFt = v) } }, "Height ft",
                                    suffix = "ft", decimal = false, error = state.errors[ProfileField.HEIGHT], modifier = Modifier.weight(1f))
                                NumberField(state.heightIn, { v -> onUpdate { it.copy(heightIn = v) } }, "in",
                                    suffix = "in", decimal = false, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                WeFitCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader("Goals")
                        if (!isEditing) {
                            NumberField(state.currentWeight, { v -> onUpdate { it.copy(currentWeight = v) } }, "Current weight (optional)",
                                suffix = weightUnit, error = state.errors[ProfileField.WEIGHT], modifier = Modifier.testTag("field_weight"))
                        }
                        NumberField(state.targetWeight, { v -> onUpdate { it.copy(targetWeight = v) } }, "Target weight (optional)",
                            suffix = weightUnit, error = state.errors[ProfileField.TARGET])
                        NumberField(state.stepGoal, { v -> onUpdate { it.copy(stepGoal = v) } }, "Daily step goal",
                            suffix = "steps", decimal = false, error = state.errors[ProfileField.STEP_GOAL], modifier = Modifier.testTag("field_step_goal"))
                    }
                }
                state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(4.dp))
                GradientButton(
                    text = if (isEditing) "Save" else "Save & Continue",
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("setup_save"),
                )
                if (!isEditing) {
                    TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("setup_skip")) {
                        Text("Skip for now — I'll add details later")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
