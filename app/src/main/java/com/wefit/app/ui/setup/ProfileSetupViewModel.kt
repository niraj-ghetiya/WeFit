package com.wefit.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.UnitConverter
import com.wefit.app.domain.model.FitnessGoals
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.UserProfile
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
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
import kotlin.math.roundToInt

enum class ProfileField { NAME, AGE, HEIGHT, WEIGHT, TARGET, STEP_GOAL }

data class ProfileFormState(
    val loading: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
    val name: String = "",
    val age: String = "",
    val heightCm: String = "",
    val heightFt: String = "",
    val heightIn: String = "",
    val currentWeight: String = "",
    val targetWeight: String = "",
    val stepGoal: String = FitnessGoals.DEFAULT_STEP_GOAL.toString(),
    val errors: Map<ProfileField, String> = emptyMap(),
    val saving: Boolean = false,
    val saveError: String? = null,
)

data class ParsedProfile(
    val name: String,
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val targetKg: Double?,
    val stepGoal: Int,
)

object ProfileValidator {
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0

    fun validate(s: ProfileFormState, requireWeightField: Boolean = false): Pair<ParsedProfile?, Map<ProfileField, String>> {
        val errors = mutableMapOf<ProfileField, String>()
        val name = s.name.trim()
        if (name.length > 40) errors[ProfileField.NAME] = "Name must be 40 characters or fewer"

        val age = s.age.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() }
        if (s.age.isNotBlank() && (age == null || age !in 5..120)) errors[ProfileField.AGE] = "Enter an age between 5 and 120"

        val heightCm: Double? = if (s.units == UnitSystem.METRIC) {
            s.heightCm.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                .also { if (s.heightCm.isNotBlank() && (it == null || it !in 50.0..272.0)) errors[ProfileField.HEIGHT] = "Enter a height between 50 and 272 cm" }
        } else if (s.heightFt.isBlank() && s.heightIn.isBlank()) null else {
            val ft = s.heightFt.ifBlank { "0" }.toIntOrNull()
            val inch = s.heightIn.ifBlank { "0" }.toIntOrNull()
            if (ft == null || inch == null || ft !in 1..8 || inch !in 0..11) {
                errors[ProfileField.HEIGHT] = "Enter feet (1–8) and inches (0–11)"
                null
            } else UnitConverter.feetInchesToCm(ft, inch).also {
                if (it !in 50.0..272.0) errors[ProfileField.HEIGHT] = "Enter a realistic height"
            }
        }

        fun weight(text: String, field: ProfileField): Double? {
            if (text.isBlank()) {
                if (field == ProfileField.WEIGHT && requireWeightField) errors[field] = "Enter your weight"
                return null
            }
            val kg = text.toDoubleOrNull()?.let { UnitConverter.weightFromDisplay(it, s.units) }
            if (kg == null || kg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
                val unit = UnitConverter.weightUnit(s.units)
                val lo = UnitConverter.weightToDisplay(MIN_WEIGHT_KG, s.units).roundToInt()
                val hi = UnitConverter.weightToDisplay(MAX_WEIGHT_KG, s.units).roundToInt()
                errors[field] = "Enter a weight between $lo and $hi $unit"
                return null
            }
            return kg
        }
        val weightKg = weight(s.currentWeight, ProfileField.WEIGHT)
        val targetKg = weight(s.targetWeight, ProfileField.TARGET)

        val goal = s.stepGoal.toIntOrNull()
        if (goal == null || goal !in 500..100_000) errors[ProfileField.STEP_GOAL] = "Enter a step goal between 500 and 100,000"

        if (errors.isNotEmpty()) return null to errors
        return ParsedProfile(name, age, heightCm, weightKg, targetKg, goal!!) to emptyMap()
    }
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val profiles: ProfileRepository,
    private val goals: GoalRepository,
    private val weights: WeightRepository,
    private val steps: StepRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val units = preferences.preferences.first().unitSystem
            val profile = profiles.get()
            val g = goals.get()
            val (ft, inch) = profile?.heightCm?.let { UnitConverter.cmToFeetInches(it) } ?: (null to null)
            _state.value = ProfileFormState(
                loading = false,
                units = units,
                name = profile?.name.orEmpty(),
                age = profile?.age?.toString().orEmpty(),
                heightCm = profile?.heightCm?.roundToInt()?.toString().orEmpty(),
                heightFt = ft?.toString().orEmpty(),
                heightIn = inch?.toString().orEmpty(),
                targetWeight = g.targetWeightKg?.let { fmt(UnitConverter.weightToDisplay(it, units)) }.orEmpty(),
                stepGoal = g.dailyStepGoal.toString(),
            )
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) = _state.update { transform(it).copy(saveError = null) }

    fun setUnits(units: UnitSystem) = _state.update { s ->
        if (s.units == units) return@update s
        fun convertWeight(text: String) = text.toDoubleOrNull()?.let {
            val kg = UnitConverter.weightFromDisplay(it, s.units)
            fmt(UnitConverter.weightToDisplay(kg, units))
        } ?: text
        val cm: Double? = if (s.units == UnitSystem.METRIC) s.heightCm.toDoubleOrNull() else {
            val ft = s.heightFt.toIntOrNull(); val inch = s.heightIn.toIntOrNull() ?: 0
            ft?.let { UnitConverter.feetInchesToCm(it, inch) }
        }
        val (ft, inch) = cm?.let { UnitConverter.cmToFeetInches(it) } ?: (null to null)
        s.copy(
            units = units,
            heightCm = cm?.roundToInt()?.toString() ?: s.heightCm.takeIf { units == UnitSystem.METRIC }.orEmpty(),
            heightFt = ft?.toString().orEmpty(),
            heightIn = inch?.toString().orEmpty(),
            currentWeight = convertWeight(s.currentWeight),
            targetWeight = convertWeight(s.targetWeight),
            errors = emptyMap(),
        )
    }

    fun save(isEditing: Boolean, onDone: () -> Unit) {
        val (parsed, errors) = ProfileValidator.validate(_state.value)
        if (parsed == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            runCatching {
                profiles.save(UserProfile(parsed.name, parsed.age, parsed.heightCm))
                val existing = goals.get()
                goals.save(existing.copy(dailyStepGoal = parsed.stepGoal, targetWeightKg = parsed.targetKg))
                steps.syncGoalForDay(LocalDate.now(), parsed.stepGoal)
                if (!isEditing && parsed.weightKg != null) {
                    weights.add(WeightEntry(weightKg = parsed.weightKg, date = LocalDate.now(), note = "Starting weight"))
                }
                preferences.setUnitSystem(_state.value.units)
                if (!isEditing) preferences.setSetupCompleted(true)
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                onDone()
            }.onFailure { e ->
                _state.update { it.copy(saving = false, saveError = "Could not save: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setUnitSystem(_state.value.units)
            preferences.setSetupCompleted(true)
            onDone()
        }
    }
}
