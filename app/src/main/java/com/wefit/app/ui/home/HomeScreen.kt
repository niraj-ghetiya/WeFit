package com.wefit.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.ui.components.ActivityListItem
import com.wefit.app.ui.components.GradientButton
import com.wefit.app.ui.components.ProgressRing
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.StatTile
import com.wefit.app.ui.components.StepTrackingStatusCard
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitLogo
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.theme.BrandColors
import com.wefit.app.ui.theme.WeFitTheme

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshTracking() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeFitLogo(markSize = 32.dp, fontSize = 24.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onNavigate(Routes.REMINDERS) }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Reminders")
                }
            }
        }
        item {
            Column {
                Text(
                    if (state.name.isBlank()) "Hello 👋" else "Hello, ${state.name} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(Formatters.fullDate(state.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val weight = state.currentWeight
                HighlightCard(
                    title = "Weight",
                    icon = Icons.Filled.MonitorWeight,
                    value = weight?.let { Formatters.weight(it.weightKg, state.units) } ?: "—",
                    caption = when {
                        weight == null -> "Tap to log your weight"
                        state.weightComparison != null -> {
                            val c = state.weightComparison!!
                            "${Formatters.weightChange(c.changeKg, state.units)} ${if (c.sinceStart) "since start" else "vs last month"}"
                        }
                        else -> "Logged ${Formatters.shortDate(weight.date)}"
                    },
                    brush = Brush.linearGradient(listOf(BrandColors.PurpleLight, BrandColors.PurpleDeep)),
                    progress = null,
                    onClick = { onNavigate(if (weight == null) Routes.addWeight() else Routes.TAB_WEIGHT) },
                    modifier = Modifier.weight(1f),
                )
                HighlightCard(
                    title = "Steps",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    value = Formatters.number(state.steps),
                    caption = "Goal: ${Formatters.number(state.goals.dailyStepGoal)}",
                    brush = WeFitTheme.colors.accentGradient,
                    progress = FitnessCalculator.goalProgress(state.steps, state.goals.dailyStepGoal),
                    onClick = { onNavigate(Routes.TAB_ACTIVITY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { StepTrackingStatusCard(state.tracking, onPermissionResult = viewModel::onPermissionResult) }
        item {
            WeFitCard(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.TAB_ACTIVITY) }) {
                SectionHeader("Today's Activity")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = FitnessCalculator.goalProgress(state.steps, state.goals.dailyStepGoal)
                    ProgressRing(progress = progress, size = 128.dp, stroke = 13.dp, brush = WeFitTheme.colors.buttonGradient) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Formatters.number(state.steps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text("steps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        val pct = if (state.goals.dailyStepGoal > 0) state.steps * 100 / state.goals.dailyStepGoal else 0
                        Text("$pct% of daily goal", style = MaterialTheme.typography.titleMedium)
                        val remaining = (state.goals.dailyStepGoal - state.steps).coerceAtLeast(0)
                        Text(
                            if (remaining == 0) "Goal reached — great job!" else "${Formatters.number(remaining)} steps to go",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.manualSteps != 0) {
                            Text(
                                "Includes ${if (state.manualSteps > 0) "+" else ""}${Formatters.number(state.manualSteps)} manually adjusted",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Filled.LocalFireDepartment, "${state.summary.caloriesKcal}", "kcal (est.)", Modifier.weight(1f), tint = WeFitTheme.colors.accent)
                StatTile(Icons.Filled.Timer, Formatters.duration(state.summary.activeMinutes), "Active time", Modifier.weight(1f))
                StatTile(Icons.Filled.Place, Formatters.distance(state.summary.distanceKm, state.units), "Distance", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton("Add Weight", { onNavigate(Routes.addWeight()) }, Modifier.weight(1f), leadingIcon = Icons.Filled.Add)
                OutlinedButton(
                    onClick = { onNavigate(Routes.addActivity()) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Activity", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.todayActivities.isNotEmpty()) {
            item { SectionHeader("Logged today") }
            items(state.todayActivities, key = { it.id }) { entry ->
                ActivityListItem(entry, state.units, state.currentWeight?.weightKg,
                    onClick = { onNavigate(Routes.addActivity(entry.id)) }, onDelete = null)
            }
        }
        if (state.summary.overlapResolved) {
            item {
                Note("Walking and running entries overlap with counted steps, so WeFit uses the higher value instead of adding both.")
            }
        }
        if (!state.loading && state.profileIncomplete) {
            item {
                Note("Distance and calories are estimates. Add your height and weight for more accurate numbers.",
                    onClick = { onNavigate(Routes.EDIT_PROFILE) })
            }
        }
    }
}

@Composable
private fun Note(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HighlightCard(
    title: String,
    icon: ImageVector,
    value: String,
    caption: String,
    brush: Brush,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(brush).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Column {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
            Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(caption, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
