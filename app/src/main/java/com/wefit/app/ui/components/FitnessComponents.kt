package com.wefit.app.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.calc.FitnessCalculator
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.step.TrackingStatus

fun ActivityType.icon(): ImageVector = when (this) {
    ActivityType.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityType.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    ActivityType.OTHER -> Icons.Filled.FitnessCenter
}

/**
 * Explains the state of sensor step counting and offers the permission request when needed.
 * Renders nothing when tracking works.
 */
@Composable
fun StepTrackingStatusCard(status: TrackingStatus, onPermissionResult: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onPermissionResult()
    }
    when {
        !status.sensorAvailable -> WeFitCard(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Filled.SensorsOff)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Step sensor not available", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This device has no step-counter sensor, so steps can't be counted automatically. " +
                            "You can still add steps manually and log activities.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        !status.permissionGranted -> WeFitCard(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Filled.Sensors)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Turn on step counting", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "WeFit needs the Physical activity permission to read your phone's step counter. " +
                            "Steps stay on this device.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow") }
                        if (denied) {
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text("Open settings") }
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
fun ActivityListItem(
    entry: ActivityEntry,
    units: UnitSystem,
    weightKg: Double?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    showDate: Boolean = false,
) {
    val kcal = entry.caloriesKcal ?: FitnessCalculator.activityCalories(entry.type, entry.durationMinutes, weightKg).toInt()
    val details = buildList {
        if (showDate) add(Formatters.shortDate(entry.date))
        add(Formatters.duration(entry.durationMinutes))
        entry.distanceKm?.let { add(Formatters.distance(it, units)) }
        add("$kcal kcal${if (entry.caloriesKcal == null) " (est.)" else ""}")
    }.joinToString("  •  ")
    WeFitCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(entry.type.icon())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.type.label, style = MaterialTheme.typography.titleSmall)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${entry.type.label}") }
            }
        }
    }
}

/** Remembers a pending delete for confirmation dialogs. */
@Composable
fun <T> rememberPendingDelete() = remember { mutableStateOf<T?>(null) }
