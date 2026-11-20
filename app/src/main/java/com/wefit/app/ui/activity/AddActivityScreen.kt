package com.wefit.app.ui.activity

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.ui.components.ConfirmDialog
import com.wefit.app.ui.components.DateField
import com.wefit.app.ui.components.GradientButton
import com.wefit.app.ui.components.NumberField
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SelectTile
import com.wefit.app.ui.components.TextInput
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.components.icon

@Composable
fun AddActivityScreen(onBack: () -> Unit, viewModel: AddActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    AddActivityContent(state, onBack, viewModel::update, { viewModel.save(onBack) }, { confirmDelete = true })
    if (confirmDelete) {
        ConfirmDialog("Delete activity?", "This activity will be permanently removed.", "Delete",
            onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}

@Composable
fun AddActivityContent(
    state: AddActivityState,
    onBack: () -> Unit,
    onUpdate: ((AddActivityState) -> AddActivityState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            WeFitTopBar(if (state.isEditing) "Edit Activity" else "Add Activity", onBack = onBack, actions = {
                if (state.isEditing) IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete activity") }
            })
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Activity type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityType.entries.forEach { t ->
                        SelectTile(t.icon(), t.label, state.type == t, { onUpdate { it.copy(type = t) } }, Modifier.weight(1f))
                    }
                }
                DateField("Date", state.date, { d -> onUpdate { it.copy(date = d) } })
                state.errors[ActivityField.DATE]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                NumberField(state.duration, { v -> onUpdate { it.copy(duration = v) } }, "Duration", suffix = "min",
                    decimal = false, error = state.errors[ActivityField.DURATION], modifier = Modifier.testTag("activity_duration"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { m ->
                        AssistChip(onClick = { onUpdate { it.copy(duration = m.toString()) } }, label = { Text("$m min") })
                    }
                }
                NumberField(state.distance, { v -> onUpdate { it.copy(distance = v) } }, "Distance (optional)",
                    suffix = UnitConverter.distanceUnit(state.units), error = state.errors[ActivityField.DISTANCE])
                val estimate = state.duration.toIntOrNull()?.let { FitnessCalculator.activityCalories(state.type, it, state.weightKg).toInt() }
                NumberField(
                    state.calories, { v -> onUpdate { it.copy(calories = v) } }, "Calories (optional)", suffix = "kcal",
                    decimal = false, error = state.errors[ActivityField.CALORIES],
                    supportingText = estimate?.let { "Leave blank to use an estimate of about $it kcal" },
                )
                TextInput(state.note, { v -> onUpdate { it.copy(note = v) } }, "Notes (optional)", singleLine = false)
                if (state.type.isStepBased) {
                    Text(
                        "Walking and running are also picked up by the step counter. To avoid double counting, WeFit uses the higher " +
                            "of your counted steps or this entry for that day's distance, calories and active time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                GradientButton("Save Activity", onSave, Modifier.fillMaxWidth().testTag("activity_save"), enabled = !state.saving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
