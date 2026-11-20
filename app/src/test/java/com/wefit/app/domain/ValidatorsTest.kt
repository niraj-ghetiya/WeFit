package com.wefit.app.domain

import com.google.common.truth.Truth.assertThat
import com.wefit.app.core.util.Csv
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.WeightEntry
import com.wefit.app.ui.activity.ActivityField
import com.wefit.app.ui.activity.ActivityValidator
import com.wefit.app.ui.activity.AddActivityState
import com.wefit.app.ui.home.weightComparison
import com.wefit.app.ui.setup.ProfileField
import com.wefit.app.ui.setup.ProfileFormState
import com.wefit.app.ui.setup.ProfileValidator
import com.wefit.app.ui.weight.WeightFilter
import com.wefit.app.ui.weight.WeightInputValidator
import com.wefit.app.ui.weight.filterWeights
import org.junit.Test
import java.time.LocalDate

class ValidatorsTest {
    private val loaded = ProfileFormState(loading = false)

    @Test fun profileWithOnlyStepGoalIsValid() {
        val (parsed, errors) = ProfileValidator.validate(loaded)
        assertThat(errors).isEmpty()
        assertThat(parsed!!.stepGoal).isEqualTo(10_000)
        assertThat(parsed.heightCm).isNull()
        assertThat(parsed.weightKg).isNull()
    }

    @Test fun profileRejectsOutOfRangeValues() {
        val (parsed, errors) = ProfileValidator.validate(
            loaded.copy(age = "200", heightCm = "20", currentWeight = "5", stepGoal = "10"),
        )
        assertThat(parsed).isNull()
        assertThat(errors.keys).containsExactly(ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT, ProfileField.STEP_GOAL)
    }

    @Test fun imperialProfileConvertsToMetric() {
        val (parsed, errors) = ProfileValidator.validate(
            loaded.copy(units = UnitSystem.IMPERIAL, heightFt = "5", heightIn = "11", currentWeight = "160", targetWeight = "150"),
        )
        assertThat(errors).isEmpty()
        assertThat(parsed!!.heightCm).isWithin(0.01).of(180.34)
        assertThat(parsed.weightKg).isWithin(0.01).of(72.57)
        assertThat(parsed.targetKg).isWithin(0.01).of(68.04)
    }

    @Test fun weightInputValidation() {
        assertThat(WeightInputValidator.parse("72.5", UnitSystem.METRIC).getOrThrow()).isEqualTo(72.5)
        assertThat(WeightInputValidator.parse("", UnitSystem.METRIC).isFailure).isTrue()
        assertThat(WeightInputValidator.parse("1000", UnitSystem.METRIC).isFailure).isTrue()
        assertThat(WeightInputValidator.parse("160", UnitSystem.IMPERIAL).getOrThrow()).isWithin(0.01).of(72.57)
    }

    @Test fun activityValidation() {
        val ok = ActivityValidator.validate(AddActivityState(loading = false, duration = "30", distance = "2.5", calories = ""))
        assertThat(ok.second).isEmpty()
        assertThat(ok.first!!.distanceKm).isEqualTo(2.5)
        assertThat(ok.first!!.calories).isNull()

        val bad = ActivityValidator.validate(
            AddActivityState(loading = false, duration = "0", distance = "-1", calories = "99999", date = LocalDate.now().plusDays(1)),
        )
        assertThat(bad.first).isNull()
        assertThat(bad.second.keys).containsExactly(ActivityField.DURATION, ActivityField.DISTANCE, ActivityField.CALORIES, ActivityField.DATE)
    }

    @Test fun imperialActivityDistanceConverted() {
        val ok = ActivityValidator.validate(AddActivityState(loading = false, units = UnitSystem.IMPERIAL, duration = "20", distance = "1"))
        assertThat(ok.first!!.distanceKm).isWithin(1e-6).of(1.609344)
    }

    @Test fun weightComparisonPrefersMonthAgoEntry() {
        val today = LocalDate.of(2026, 11, 20)
        val entries = listOf(
            WeightEntry(3, 72.5, today), WeightEntry(2, 73.2, today.minusDays(10)),
            WeightEntry(1, 74.0, today.minusDays(31)), WeightEntry(0, 76.0, today.minusDays(90)),
        )
        val c = weightComparison(entries)!!
        assertThat(c.changeKg).isWithin(1e-9).of(-1.5)
        assertThat(c.sinceStart).isFalse()
        assertThat(weightComparison(entries.take(2))!!.sinceStart).isTrue()
        assertThat(weightComparison(entries.take(1))).isNull()
    }

    @Test fun weightFilterWindow() {
        val today = LocalDate.of(2026, 11, 20)
        val entries = listOf(WeightEntry(2, 72.0, today), WeightEntry(1, 73.0, today.minusDays(6)), WeightEntry(0, 75.0, today.minusDays(40)))
        assertThat(filterWeights(entries, WeightFilter.WEEK, today).map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(filterWeights(entries, WeightFilter.ALL, today)).hasSize(3)
    }

    @Test fun csvEscaping() {
        assertThat(Csv.row("a", "b,c", "say \"hi\"", null)).isEqualTo("a,\"b,c\",\"say \"\"hi\"\"\",")
    }

    @Test fun formatting() {
        assertThat(Formatters.weightChange(-1.5, UnitSystem.METRIC)).isEqualTo("-1.5 kg")
        assertThat(Formatters.weightChange(0.01, UnitSystem.METRIC)).isEqualTo("0.0 kg")
        assertThat(Formatters.duration(78)).isEqualTo("1 h 18 min")
        assertThat(Formatters.time(18, 5)).isEqualTo("6:05 PM")
    }
}
