package com.wefit.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.ui.components.ConfirmDialog
import com.wefit.app.ui.components.IconBadge
import com.wefit.app.ui.components.NavRow
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.SegmentedSelector
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.theme.WeFitTheme
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onResetComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var enableAfterPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshTracking() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    val today = LocalDate.now()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::backup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult()
        if (granted && enableAfterPermission) viewModel.setBackgroundTracking(true)
        enableAfterPermission = false
    }

    Scaffold(topBar = { WeFitTopBar("Profile & Settings", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WeFitCard(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.EDIT_PROFILE) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                IconBadge(Icons.Filled.Person, size = 56.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                val p = state.profile
                                Text(p?.name?.ifBlank { null } ?: "Your profile", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(
                                        p?.age?.let { "$it years" },
                                        p?.heightCm?.let { Formatters.height(it, state.prefs.unitSystem) },
                                    ).joinToString(" · ").ifBlank { "Tap to add your details" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item { SectionHeader("Units") }
                item {
                    SegmentedSelector(listOf("Metric · kg, km", "Imperial · lb, mi"), state.prefs.unitSystem.ordinal,
                        { viewModel.setUnits(UnitSystem.entries[it]) })
                }
                item { SectionHeader("Theme") }
                item {
                    SegmentedSelector(listOf("System", "Light", "Dark"), state.prefs.themeMode.ordinal, { viewModel.setTheme(ThemeMode.entries[it]) })
                }
                item {
                    WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        NavRow(Icons.Filled.TrackChanges, "Fitness goals", "${Formatters.number(state.goals.dailyStepGoal)} steps/day") { onNavigate(Routes.TAB_GOALS) }
                        NavRow(Icons.Filled.Notifications, "Manage reminders") { onNavigate(Routes.REMINDERS) }
                    }
                }
                item { SectionHeader("Step tracking") }
                item {
                    WeFitCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Sensors)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Background step tracking", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        !state.tracking.sensorAvailable -> "Not available: this device has no step sensor."
                                        else -> "Keeps counting while WeFit is closed. Shows a persistent notification and uses a little more battery."
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.prefs.backgroundTracking && state.tracking.canTrack,
                                enabled = state.tracking.sensorAvailable,
                                onCheckedChange = { on ->
                                    if (on && !state.tracking.permissionGranted) {
                                        enableAfterPermission = true
                                        activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                    } else viewModel.setBackgroundTracking(on)
                                },
                            )
                        }
                    }
                }
                item { SectionHeader("Your data") }
                item {
                    WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        NavRow(Icons.Filled.Download, "Export data to CSV", "Weights, daily steps and activities (.zip)") {
                            csvLauncher.launch("wefit-export-$today.zip")
                        }
                        NavRow(Icons.Filled.Backup, "Back up data", "Save a backup file you can restore later") {
                            backupLauncher.launch("wefit-backup-$today.json")
                        }
                        NavRow(Icons.Filled.Restore, "Restore from backup", "Replace current data with a backup file") { confirmRestore = true }
                        NavRow(Icons.Filled.DeleteForever, "Reset application data", "Permanently erase everything", tint = WeFitTheme.colors.danger) {
                            confirmReset = true
                        }
                    }
                }
                item { SectionHeader("About") }
                item {
                    WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        NavRow(Icons.Filled.Info, "About WeFit") { onNavigate(Routes.ABOUT) }
                        NavRow(Icons.Filled.PrivacyTip, "Privacy information") { onNavigate(Routes.PRIVACY) }
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore from backup?",
            message = "All weight entries, steps, activities, goals and settings currently on this device will be replaced by the backup's contents.",
            confirmLabel = "Choose file",
            onConfirm = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { confirmRestore = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all data?",
            message = "This permanently deletes your profile, weight history, steps, activities, goals, reminders and achievements from this device. " +
                "Consider making a backup first. This can't be undone.",
            confirmLabel = "Erase everything",
            destructive = true,
            onConfirm = { viewModel.reset(onResetComplete) },
            onDismiss = { confirmReset = false },
        )
    }
}
