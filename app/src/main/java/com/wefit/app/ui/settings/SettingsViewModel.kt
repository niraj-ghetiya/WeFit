package com.wefit.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.data.backup.DataTransferManager
import com.wefit.app.data.backup.InvalidBackupException
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.UserPreferences
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.step.StepTracker
import com.wefit.app.step.StepTrackingService
import com.wefit.app.step.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val profile: UserProfile? = null,
    val goals: FitnessGoals = FitnessGoals(),
    val tracking: TrackingStatus = TrackingStatus(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    profiles: ProfileRepository,
    goals: GoalRepository,
    private val tracker: StepTracker,
    private val transfer: DataTransferManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(preferences.preferences, profiles.profile, goals.goals, tracker.status) { p, pr, g, t ->
        SettingsUiState(p, pr, g, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()
    fun messageShown() { _messages.value = null }

    fun setUnits(units: UnitSystem) { viewModelScope.launch { preferences.setUnitSystem(units) } }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { preferences.setThemeMode(mode) } }

    fun setBackgroundTracking(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBackgroundTracking(enabled)
            if (enabled) StepTrackingService.start(context) else StepTrackingService.stop(context)
        }
    }

    fun onPermissionResult() = tracker.onPermissionResult()
    fun refreshTracking() = tracker.refreshStatus()

    private fun run(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _messages.value = try {
                block()
                success
            } catch (e: InvalidBackupException) {
                e.message
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportCsv(uri: Uri) = run("Data exported as CSV") { transfer.exportCsv(uri) }
    fun backup(uri: Uri) = run("Backup saved") { transfer.exportBackup(uri) }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _messages.value = try {
                val summary = transfer.restoreBackup(uri)
                "Restored ${summary.weights} weight entries, ${summary.days} days of steps and ${summary.activities} activities"
            } catch (e: InvalidBackupException) {
                e.message
            } catch (e: Exception) {
                "Could not restore backup: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun reset(onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { transfer.resetAll() }
            _busy.value = false
            onDone()
        }
    }
}
