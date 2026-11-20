package com.wefit.app.ui.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.calc.InsightsRange
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.ui.components.ActivityListItem
import com.wefit.app.ui.components.BarChart
import com.wefit.app.ui.components.ChoiceChips
import com.wefit.app.ui.components.ConfirmDialog
import com.wefit.app.ui.components.EmptyState
import com.wefit.app.ui.components.InfoRow
import com.wefit.app.ui.components.NumberField
import com.wefit.app.ui.components.PeriodNavigator
import com.wefit.app.ui.components.ProgressRing
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SegmentedSelector
import com.wefit.app.ui.components.StatTile
import com.wefit.app.ui.components.StepTrackingStatusCard
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.theme.WeFitTheme

private val ranges = listOf(InsightsRange.DAY, InsightsRange.WEEK, InsightsRange.MONTH)

@Composable
fun StepTrackerScreen(onNavigate: (String) -> Unit, viewModel: StepTrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdjust by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ActivityEntry?>(null) }
    var explainerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            WeFitTopBar("Activity", actions = {
                IconButton(onClick = { onNavigate(Routes.addActivity(date = state.anchor.takeIf { state.range == InsightsRange.DAY })) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add activity")
                }
            })
        },
    ) { padding ->
        val data = state.data
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SegmentedSelector(ranges.map { it.label }, ranges.indexOf(state.range), { viewModel.setRange(ranges[it]) })
            }
            item {
                PeriodNavigator(data?.title ?: "", { viewModel.shift(-1) }, { viewModel.shift(1) }, state.canGoForward)
            }
            item { StepTrackingStatusCard(state.tracking, onPermissionResult = viewModel::onPermissionResult) }
            if (data != null) {
                if (state.range == InsightsRange.DAY) {
                    item {
                        val total = state.day?.total ?: 0
                        val goal = state.day?.goal ?: state.goals.dailyStepGoal
                        WeFitCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRing(FitnessCalculator.goalProgress(total, goal), size = 120.dp, brush = WeFitTheme.colors.buttonGradient) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(if (goal > 0) "${total * 100 / goal}%" else "—", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Formatters.number(total), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("day_steps"))
                                    Text("of ${Formatters.number(goal)} steps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val manual = state.day?.manualSteps ?: 0
                                    if (manual != 0) {
                                        Text(
                                            "Sensor ${Formatters.number(state.day?.sensorSteps ?: 0)} · Manual ${if (manual > 0) "+" else ""}${Formatters.number(manual)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            state.daySummary?.let { s ->
                                InfoRow(Icons.Filled.Place, "Distance", Formatters.distance(s.distanceKm, state.units))
                                InfoRow(Icons.Filled.Timer, "Walking & active time", Formatters.duration(s.activeMinutes))
                                InfoRow(Icons.Filled.LocalFireDepartment, "Calories (est.)", "${s.caloriesKcal} kcal", tint = WeFitTheme.colors.accent)
                            }
                            OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.fillMaxWidth().testTag("adjust_steps")) {
                                Icon(Icons.Filled.EditNote, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust steps manually")
                            }
                        }
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(Icons.AutoMirrored.Filled.DirectionsWalk, Formatters.number(data.totalSteps), "Total steps", Modifier.weight(1f))
                            StatTile(Icons.Filled.Timer, Formatters.number(data.avgDailySteps), "Daily average", Modifier.weight(1f))
                            StatTile(Icons.Filled.Whatshot, "${data.goalDaysMet}/${data.goalDaysCounted}", "Goal days", Modifier.weight(1f), tint = WeFitTheme.colors.accent)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(Icons.Filled.Place, Formatters.distance(data.totalDistanceKm, state.units), "Distance", Modifier.weight(1f))
                            StatTile(Icons.Filled.Timer, Formatters.duration(data.totalActiveMinutes), "Active time", Modifier.weight(1f))
                            StatTile(Icons.Filled.LocalFireDepartment, Formatters.number(data.totalCalories), "kcal (est.)", Modifier.weight(1f), tint = WeFitTheme.colors.accent)
                        }
                    }
                }
                item {
                    WeFitCard(Modifier.fillMaxWidth()) {
                        SectionHeader(if (state.range == InsightsRange.DAY) "Hourly steps" else "Daily steps")
                        Spacer(Modifier.height(8.dp))
                        BarChart(
                            bars = data.bars,
                            goalValue = if (state.range == InsightsRange.DAY) null else state.goals.dailyStepGoal.toFloat(),
                            labelEvery = when (state.range) { InsightsRange.DAY -> 6; InsightsRange.MONTH -> 5; else -> 1 },
                            description = "Steps chart for ${data.title}",
                        )
                        if (state.range == InsightsRange.DAY && (state.day?.manualSteps ?: 0) != 0) {
                            Text("Hourly chart shows sensor steps only; manual adjustments are added to the daily total.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    WeFitCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Whatshot, null, tint = WeFitTheme.colors.accent)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Step goal streak", style = MaterialTheme.typography.titleSmall)
                                Text("Consecutive days you hit your step goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${data.currentStreak} days", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                Text("Best ${data.bestStreak}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    if (state.range == InsightsRange.DAY) "Logged activities" else "Activities this ${state.range.label.lowercase()}",
                    action = "Add",
                    onAction = { onNavigate(Routes.addActivity(date = state.anchor.takeIf { state.range == InsightsRange.DAY })) },
                )
            }
            if (state.activities.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.DirectionsWalk, "No activities logged",
                        "Log walks, runs, rides or workouts to see them here.",
                    )
                }
            } else {
                items(state.activities, key = { it.id }) { entry ->
                    ActivityListItem(
                        entry, state.units, state.weightKg,
                        onClick = { onNavigate(Routes.addActivity(entry.id)) },
                        onDelete = { pendingDelete = entry },
                        showDate = state.range != InsightsRange.DAY,
                    )
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth(), onClick = { explainerOpen = !explainerOpen }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("How step counting works", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (explainerOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                    AnimatedVisibility(explainerOpen) {
                        Text(
                            "WeFit reads your phone's built-in step counter sensor. The phone keeps counting while WeFit is closed, " +
                                "and WeFit adds those steps the next time it syncs (live while the app is open, and about every 15 minutes " +
                                "in the background — Android may delay this to save battery).\n\n" +
                                "• Steps can't be counted while your phone is switched off.\n" +
                                "• After a restart, steps taken before WeFit last synced are kept, but steps between that sync and the restart may be missed.\n" +
                                "• Some devices don't have a step sensor; use manual adjustments instead.\n" +
                                "• For more reliable background counting, turn on Background step tracking in Profile & Settings.\n\n" +
                                "Distance, active time and calories are estimates based on your height and weight.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAdjust) {
        AdjustStepsDialog(
            dateLabel = Formatters.fullDate(state.anchor),
            onConfirm = { delta -> viewModel.adjustSteps(state.anchor, delta); showAdjust = false },
            onDismiss = { showAdjust = false },
        )
    }
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete activity?",
            message = "This ${entry.type.label.lowercase()} on ${Formatters.mediumDate(entry.date)} will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteActivity(entry.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Returns the signed step delta, or null when the amount is missing or invalid. */
fun parseStepAdjustment(amount: String, add: Boolean): Int? {
    val value = amount.toIntOrNull()?.takeIf { it in 1..100_000 } ?: return null
    return if (add) value else -value
}

@Composable
fun AdjustStepsForm(dateLabel: String, amount: String, onAmountChange: (String) -> Unit, add: Boolean, onAddChange: (Boolean) -> Unit) {
    val error = if (amount.isNotEmpty() && parseStepAdjustment(amount, add) == null) "Enter 1 to 100,000 steps" else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Manual adjustments for $dateLabel are labelled separately from sensor steps.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChips(listOf(true, false), add, onAddChange, { if (it) "Add steps" else "Remove steps" })
        NumberField(amount, onAmountChange, "Steps", decimal = false, error = error, modifier = Modifier.testTag("adjust_amount"))
    }
}

@Composable
fun AdjustStepsDialog(dateLabel: String, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(true) }
    val delta = parseStepAdjustment(amount, add)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust steps") },
        text = { AdjustStepsForm(dateLabel, amount, { amount = it }, add, { add = it }) },
        confirmButton = { TextButton(onClick = { delta?.let(onConfirm) }, enabled = delta != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
