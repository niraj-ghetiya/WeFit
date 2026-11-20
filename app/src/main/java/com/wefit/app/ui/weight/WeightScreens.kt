package com.wefit.app.ui.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.Formatters
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.ui.components.ConfirmDialog
import com.wefit.app.ui.components.DateField
import com.wefit.app.ui.components.EmptyState
import com.wefit.app.ui.components.GradientButton
import com.wefit.app.ui.components.LineChart
import com.wefit.app.ui.components.NumberField
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SegmentedSelector
import com.wefit.app.ui.components.TextInput
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.theme.BrandColors
import com.wefit.app.ui.theme.WeFitTheme

@Composable
fun WeightScreen(onNavigate: (String) -> Unit, viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<WeightEntry?>(null) }
    val units = state.units

    Scaffold(topBar = { WeFitTopBar("Weight") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.loading && state.entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.MonitorWeight, "No weight entries yet",
                        "Log your weight regularly to see your progress chart and trends.",
                        actionLabel = "Add Weight", onAction = { onNavigate(Routes.addWeight()) },
                    )
                }
                return@LazyColumn
            }
            item {
                SegmentedSelector(WeightFilter.entries.map { it.label }, state.filter.ordinal, { viewModel.setFilter(WeightFilter.entries[it]) })
            }
            item {
                WeFitCard(Modifier.fillMaxWidth()) {
                    SectionHeader("Progress")
                    if (state.chartEntries.isEmpty()) {
                        Text("No entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    } else {
                        LineChart(
                            values = state.chartEntries.map { UnitConverter.weightToDisplay(it.weightKg, units).toFloat() },
                            labels = state.chartEntries.map { Formatters.shortDate(it.date) },
                            targetValue = state.goals.targetWeightKg?.let { UnitConverter.weightToDisplay(it, units).toFloat() },
                            description = "Weight chart, ${state.chartEntries.size} entries",
                        )
                        if (state.goals.targetWeightKg != null) {
                            Text("Dashed line: target weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile("Start", state.start?.let { Formatters.weight(it.weightKg, units) } ?: "—", Modifier.weight(1f))
                    SummaryTile("Current", state.current?.let { Formatters.weight(it.weightKg, units) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile("Target", state.goals.targetWeightKg?.let { Formatters.weight(it, units) } ?: "Not set", Modifier.weight(1f),
                        onClick = { onNavigate(Routes.TAB_GOALS) })
                    val change = state.totalChangeKg
                    SummaryTile(
                        "Total change", change?.let { Formatters.weightChange(it, units) } ?: "—", Modifier.weight(1f),
                        valueColor = when {
                            change == null -> null
                            state.goals.targetWeightKg != null && state.start != null &&
                                ((state.goals.targetWeightKg!! < state.start!!.weightKg) == (change < 0)) -> WeFitTheme.colors.success
                            else -> null
                        },
                    )
                }
            }
            item { GradientButton("Add Weight", { onNavigate(Routes.addWeight()) }, Modifier.fillMaxWidth(), leadingIcon = Icons.Filled.Add) }
            item { SectionHeader("History") }
            items(state.entries, key = { it.id }) { entry ->
                val index = state.entries.indexOf(entry)
                val previous = state.entries.getOrNull(index + 1)
                WeFitCard(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.addWeight(entry.id)) }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Formatters.weight(entry.weightKg, units), style = MaterialTheme.typography.titleMedium)
                            Text(Formatters.fullDate(entry.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        }
                        previous?.let {
                            Text(Formatters.weightChange(entry.weightKg - it.weightKg, units), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pendingDelete = entry }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete entry from ${Formatters.mediumDate(entry.date)}")
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete weight entry?",
            message = "${Formatters.weight(entry.weightKg, units)} on ${Formatters.mediumDate(entry.date)} will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(entry.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null, onClick: (() -> Unit)? = null) {
    WeFitCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
fun AddWeightScreen(onBack: () -> Unit, viewModel: AddWeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    AddWeightContent(
        state = state,
        onBack = onBack,
        onUpdate = viewModel::update,
        onSave = { viewModel.save(onBack) },
        onDelete = { confirmDelete = true },
    )
    if (confirmDelete) {
        ConfirmDialog("Delete weight entry?", "This entry will be permanently removed.", "Delete",
            onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}

@Composable
fun AddWeightContent(
    state: AddWeightState,
    onBack: () -> Unit,
    onUpdate: ((AddWeightState) -> AddWeightState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            WeFitTopBar(if (state.isEditing) "Edit Weight" else "Add Weight", onBack = onBack, actions = {
                if (state.isEditing) IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete entry") }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BrandColors.PurpleLight, BrandColors.PurpleDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MonitorWeight, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
            Text("Enter Your Weight", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    state.weight, { v -> onUpdate { it.copy(weight = v) } }, "Weight",
                    suffix = UnitConverter.weightUnit(state.units), error = state.error,
                    modifier = Modifier.testTag("weight_input"),
                )
                DateField("Measurement date", state.date, { d -> onUpdate { it.copy(date = d) } })
                TextInput(state.note, { v -> onUpdate { it.copy(note = v) } }, "Notes (optional)", placeholder = "e.g. Morning weight", singleLine = false)
                Spacer(Modifier.height(4.dp))
                GradientButton("Save", onSave, Modifier.fillMaxWidth().testTag("weight_save"), enabled = !state.saving)
                Spacer(Modifier.width(1.dp).height(24.dp))
            }
        }
    }
}
