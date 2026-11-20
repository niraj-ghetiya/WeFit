package com.wefit.app.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.model.ActivityEntry
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

enum class ActivityField { DURATION, DISTANCE, CALORIES, DATE }

data class AddActivityState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val weightKg: Double? = null,
    val type: ActivityType = ActivityType.WALKING,
    val date: LocalDate = LocalDate.now(),
    val duration: String = "30",
    val distance: String = "",
    val calories: String = "",
    val note: String = "",
    val errors: Map<ActivityField, String> = emptyMap(),
    val saving: Boolean = false,
)

object ActivityValidator {
    data class Parsed(val durationMinutes: Int, val distanceKm: Double?, val calories: Int?)

    fun validate(s: AddActivityState, today: LocalDate = LocalDate.now()): Pair<Parsed?, Map<ActivityField, String>> {
        val errors = mutableMapOf<ActivityField, String>()
        val duration = s.duration.toIntOrNull()
        if (duration == null || duration !in 1..1440) errors[ActivityField.DURATION] = "Enter a duration between 1 and 1440 minutes"
        val distanceKm = s.distance.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.let { UnitConverter.distanceFromDisplay(it, s.units) }
        if (s.distance.isNotBlank() && (distanceKm == null || distanceKm <= 0.0 || distanceKm > 500.0)) {
            errors[ActivityField.DISTANCE] = "Enter a distance greater than 0 and up to ${if (s.units == UnitSystem.IMPERIAL) "310 mi" else "500 km"}"
        }
        val calories = s.calories.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (s.calories.isNotBlank() && (calories == null || calories !in 1..10_000)) errors[ActivityField.CALORIES] = "Enter calories between 1 and 10,000"
        if (s.date.isAfter(today)) errors[ActivityField.DATE] = "Date can't be in the future"
        if (errors.isNotEmpty()) return null to errors
        return Parsed(duration!!, distanceKm, calories) to emptyMap()
    }
}

@HiltViewModel
class AddActivityViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val preferences: PreferencesRepository,
    private val activities: ActivityRepository,
    private val weights: WeightRepository,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id")?.takeIf { it > 0 } ?: -1L
    private val initialDate: LocalDate? = savedState.get<Long>("date")?.takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) }
    private var existing: ActivityEntry? = null

    private val _state = MutableStateFlow(AddActivityState())
    val state: StateFlow<AddActivityState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val units = preferences.preferences.first().unitSystem
            val weightKg = weights.getAll().firstOrNull()?.weightKg
            existing = if (id > 0) activities.get(id) else null
            val e = existing
            _state.value = if (e != null) AddActivityState(
                loading = false, isEditing = true, units = units, weightKg = weightKg, type = e.type, date = e.date,
                duration = e.durationMinutes.toString(),
                distance = e.distanceKm?.let { String.format(Locale.US, "%.2f", UnitConverter.distanceToDisplay(it, units)) }.orEmpty(),
                calories = e.caloriesKcal?.toString().orEmpty(),
                note = e.note.orEmpty(),
            ) else AddActivityState(
                loading = false, units = units, weightKg = weightKg,
                date = initialDate?.takeIf { !it.isAfter(LocalDate.now()) } ?: LocalDate.now(),
            )
        }
    }

    fun update(transform: (AddActivityState) -> AddActivityState) = _state.update { transform(it).copy(errors = emptyMap()) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val (parsed, errors) = ActivityValidator.validate(s)
        if (parsed == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val entry = ActivityEntry(
                id = existing?.id ?: 0,
                type = s.type,
                date = s.date,
                durationMinutes = parsed.durationMinutes,
                distanceKm = parsed.distanceKm,
                caloriesKcal = parsed.calories,
                note = s.note,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            runCatching { if (existing != null) activities.update(entry) else activities.add(entry) }
                .onSuccess { onDone() }
                .onFailure { _state.update { it.copy(saving = false) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val e = existing ?: return
        viewModelScope.launch { activities.delete(e.id); onDone() }
    }
}
