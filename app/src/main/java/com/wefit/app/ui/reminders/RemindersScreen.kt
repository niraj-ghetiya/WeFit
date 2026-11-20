package com.wefit.app.ui.reminders

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.model.ReminderSetting
import com.wefit.app.domain.model.ReminderType
import com.wefit.app.domain.repository.ReminderRepository
import com.wefit.app.notifications.NotificationHelper
import com.wefit.app.reminders.ReminderScheduler
import com.wefit.app.ui.components.IconBadge
import com.wefit.app.ui.components.TimePickerDialog
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val reminders: StateFlow<List<ReminderSetting>> =
        repository.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(setting: ReminderSetting) {
        viewModelScope.launch {
            repository.save(setting)
            scheduler.schedule(setting)
        }
    }
}

private fun ReminderType.icon(): ImageVector = when (this) {
    ReminderType.WEIGHT -> Icons.Filled.MonitorWeight
    ReminderType.STEP_GOAL -> Icons.AutoMirrored.Filled.DirectionsWalk
    ReminderType.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
    ReminderType.WEEKLY_SUMMARY -> Icons.Filled.Insights
}

@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingEnable by remember { mutableStateOf<ReminderSetting?>(null) }
    var editingTime by remember { mutableStateOf<ReminderSetting?>(null) }
    var notificationsAllowed by remember { mutableStateOf(NotificationHelper.canPostNotifications(context)) }

    // Re-check when returning from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { notificationsAllowed = NotificationHelper.canPostNotifications(context) }
    }

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = NotificationHelper.canPostNotifications(context)
        val setting = pendingEnable
        pendingEnable = null
        if (granted && setting != null) {
            viewModel.save(setting.copy(enabled = true))
        } else if (!granted) {
            scope.launch {
                val result = snackbar.showSnackbar("Notifications are blocked, so reminders can't be shown.", "Settings", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) openNotificationSettings()
            }
        }
    }

    fun toggle(setting: ReminderSetting, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.canPostNotifications(context)) {
            pendingEnable = setting
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.save(setting.copy(enabled = enabled))
        }
    }

    Scaffold(topBar = { WeFitTopBar("Reminders", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notificationsAllowed && reminders.any { it.enabled }) {
                item {
                    WeFitCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text("Notifications are turned off for WeFit.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = ::openNotificationSettings) { Text("Turn on") }
                        }
                    }
                }
            }
            items(reminders, key = { it.type.name }) { setting ->
                WeFitCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(setting.type.icon())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(setting.type.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                (if (setting.type.weekly) "Every ${setting.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} at " else "Daily at ") +
                                    Formatters.time(setting.hour, setting.minute),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = setting.enabled, onCheckedChange = { toggle(setting, it) })
                    }
                    Text(setting.type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editingTime = setting }) { Text("Change time") }
                    }
                    if (setting.type.weekly) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                FilterChip(
                                    selected = setting.dayOfWeek == day,
                                    onClick = { viewModel.save(setting.copy(dayOfWeek = day)) },
                                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth(), color = WeFitTheme.colors.accentContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.BatteryAlert, null, tint = WeFitTheme.colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("About delivery times", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Reminders are scheduled with Android's WorkManager and respect battery saving, so they may arrive a few minutes late. " +
                                    "If reminders don't appear, allow WeFit to run without battery restrictions.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }) { Text("Battery settings") }
                        }
                    }
                }
            }
        }
    }

    editingTime?.let { setting ->
        TimePickerDialog(
            hour = setting.hour, minute = setting.minute,
            onConfirm = { h, m -> viewModel.save(setting.copy(hour = h, minute = m)); editingTime = null },
            onDismiss = { editingTime = null },
        )
    }
}
