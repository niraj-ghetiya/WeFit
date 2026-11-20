package com.wefit.app.ui.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.ui.setup.ProfileValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class WeightFilter(val label: String, val days: Long?) { WEEK("Week", 7), MONTH("Month", 30), YEAR("Year", 365), ALL("All", null) }

data class WeightUiState(
    val loading: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
    val entries: List<WeightEntry> = emptyList(),
    val chartEntries: List<WeightEntry> = emptyList(),
    val filter: WeightFilter = WeightFilter.MONTH,
    val goals: FitnessGoals = FitnessGoals(),
) {
    val start: WeightEntry? get() = entries.lastOrNull()
    val current: WeightEntry? get() = entries.firstOrNull()
    val totalChangeKg: Double? get() = if (entries.size >= 2) current!!.weightKg - start!!.weightKg else null
}

/** Entries (oldest first) within the filter window ending today. */
fun filterWeights(entriesNewestFirst: List<WeightEntry>, filter: WeightFilter, today: LocalDate): List<WeightEntry> {
    val from = filter.days?.let { today.minusDays(it - 1) }
    return entriesNewestFirst.filter { from == null || !it.date.isBefore(from) }.reversed()
}

@HiltViewModel
class WeightViewModel @Inject constructor(
    preferences: PreferencesRepository,
    goals: GoalRepository,
    private val weights: WeightRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(WeightFilter.MONTH)

    val state: StateFlow<WeightUiState> = combine(preferences.preferences, goals.goals, weights.entries, filter) { p, g, e, f ->
        WeightUiState(false, p.unitSystem, e, filterWeights(e, f, LocalDate.now()), f, g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun setFilter(f: WeightFilter) { filter.value = f }
    fun delete(id: Long) { viewModelScope.launch { weights.delete(id) } }
}

data class AddWeightState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val weight: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

object WeightInputValidator {
    /** Returns weight in kg, or an error message. */
    fun parse(text: String, units: UnitSystem): Result<Double> {
        val v = text.toDoubleOrNull() ?: return Result.failure(IllegalArgumentException("Enter your weight"))
        val kg = UnitConverter.weightFromDisplay(v, units)
        if (kg !in ProfileValidator.MIN_WEIGHT_KG..ProfileValidator.MAX_WEIGHT_KG) {
            val lo = UnitConverter.weightToDisplay(ProfileValidator.MIN_WEIGHT_KG, units).roundToInt()
            val hi = UnitConverter.weightToDisplay(ProfileValidator.MAX_WEIGHT_KG, units).roundToInt()
            return Result.failure(IllegalArgumentException("Enter a weight between $lo and $hi ${UnitConverter.weightUnit(units)}"))
        }
        return Result.success(kg)
    }
}

@HiltViewModel
class AddWeightViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val preferences: PreferencesRepository,
    private val weights: WeightRepository,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id")?.takeIf { it > 0 } ?: -1L
    private var existing: WeightEntry? = null

    private val _state = MutableStateFlow(AddWeightState())
    val state: StateFlow<AddWeightState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val units = preferences.preferences.first().unitSystem
            existing = if (id > 0) weights.get(id) else null
            val e = existing
            _state.value = if (e != null) {
                AddWeightState(false, true, units, String.format(Locale.US, "%.1f", UnitConverter.weightToDisplay(e.weightKg, units)), e.date, e.note.orEmpty())
            } else {
                // Pre-fill with the last logged weight to make daily logging quick.
                val last = weights.getAll().firstOrNull()
                AddWeightState(false, false, units, last?.let { String.format(Locale.US, "%.1f", UnitConverter.weightToDisplay(it.weightKg, units)) }.orEmpty())
            }
        }
    }

    fun update(transform: (AddWeightState) -> AddWeightState) = _state.update { transform(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val kg = WeightInputValidator.parse(s.weight, s.units).getOrElse { e ->
            _state.update { it.copy(error = e.message) }
            return
        }
        if (s.date.isAfter(LocalDate.now())) {
            _state.update { it.copy(error = "Date can't be in the future") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                val e = existing
                if (e != null) weights.update(e.copy(weightKg = kg, date = s.date, note = s.note))
                else weights.add(WeightEntry(weightKg = kg, date = s.date, note = s.note))
            }.onSuccess { onDone() }
                .onFailure { err -> _state.update { it.copy(saving = false, error = "Could not save: ${err.message}") } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val e = existing ?: return
        viewModelScope.launch { weights.delete(e.id); onDone() }
    }
}
