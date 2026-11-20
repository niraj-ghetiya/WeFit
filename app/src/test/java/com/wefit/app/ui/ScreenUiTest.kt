package com.wefit.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wefit.app.domain.model.ActivityType
import com.wefit.app.ui.activity.AddActivityContent
import com.wefit.app.ui.activity.AddActivityState
import com.wefit.app.ui.onboarding.OnboardingContent
import com.wefit.app.ui.setup.ProfileField
import com.wefit.app.ui.setup.ProfileFormState
import com.wefit.app.ui.setup.ProfileSetupContent
import com.wefit.app.ui.steps.AdjustStepsForm
import com.wefit.app.ui.steps.parseStepAdjustment
import com.wefit.app.ui.theme.WeFitTheme
import com.wefit.app.ui.weight.AddWeightContent
import com.wefit.app.ui.weight.AddWeightState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onboardingNextNextGetStartedFinishes() {
        var finished = 0
        compose.setContent { WeFitTheme { OnboardingContent(onFinished = { finished++ }) } }
        compose.onNodeWithText("Track Your\nFitness Journey").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Set Goals &\nStay Motivated").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Get Started").performClick()
        assertThat(finished).isEqualTo(1)
    }

    @Test fun onboardingSkipFinishesImmediately() {
        var finished = false
        compose.setContent { WeFitTheme { OnboardingContent(onFinished = { finished = true }) } }
        compose.onNodeWithTag("onboarding_skip").performClick()
        assertThat(finished).isTrue()
    }

    @Test fun profileSetupShowsValidationErrorsAndSkip() {
        var skipped = false
        var saves = 0
        compose.setContent {
            WeFitTheme {
                ProfileSetupContent(
                    state = ProfileFormState(loading = false, errors = mapOf(ProfileField.STEP_GOAL to "Enter a step goal between 500 and 100,000")),
                    isEditing = false, onBack = null, onUpdate = {}, onUnitsChange = {},
                    onSave = { saves++ }, onSkip = { skipped = true },
                )
            }
        }
        compose.onNodeWithText("Enter a step goal between 500 and 100,000").assertIsDisplayed()
        compose.onNodeWithTag("setup_save").performScrollTo().performClick()
        compose.onNodeWithTag("setup_skip").performScrollTo().performClick()
        assertThat(saves).isEqualTo(1)
        assertThat(skipped).isTrue()
    }

    @Test fun addWeightUpdatesStateAndSaves() {
        var state by mutableStateOf(AddWeightState(loading = false))
        var saved = false
        compose.setContent {
            WeFitTheme {
                AddWeightContent(state, onBack = {}, onUpdate = { state = it(state) }, onSave = { saved = true }, onDelete = {})
            }
        }
        compose.onNodeWithTag("weight_input").performTextInput("72,5")
        assertThat(state.weight).isEqualTo("72.5")   // comma normalised to dot
        compose.onNodeWithTag("weight_save").performClick()
        assertThat(saved).isTrue()
    }

    @Test fun addWeightShowsError() {
        compose.setContent {
            WeFitTheme {
                AddWeightContent(AddWeightState(loading = false, error = "Enter your weight"), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Enter your weight").assertIsDisplayed()
    }

    @Test fun addActivitySelectTypeAndQuickDuration() {
        var state by mutableStateOf(AddActivityState(loading = false))
        compose.setContent {
            WeFitTheme { AddActivityContent(state, {}, { state = it(state) }, {}, {}) }
        }
        compose.onNodeWithText("Cycling").performClick()
        compose.onNodeWithText("45 min").performClick()
        assertThat(state.type).isEqualTo(ActivityType.CYCLING)
        assertThat(state.duration).isEqualTo("45")
        compose.onNodeWithTag("activity_duration").performTextReplacement("90")
        assertThat(state.duration).isEqualTo("90")
    }

    // The dialog window itself is verified on-device (AdjustStepsDialogDeviceTest); a focused text field
    // inside a Dialog window never idles under Robolectric, so the form content is tested here.
    @Test fun adjustStepsFormValidatesAndSwitchesMode() {
        var amount by mutableStateOf("")
        var add by mutableStateOf(true)
        compose.setContent { WeFitTheme { AdjustStepsForm("Today", amount, { amount = it }, add, { add = it }) } }
        compose.onNodeWithTag("adjust_amount").performTextInput("abc200")
        assertThat(amount).isEqualTo("200")
        compose.onNodeWithText("Remove steps").performClick()
        assertThat(add).isFalse()
        assertThat(parseStepAdjustment(amount, add)).isEqualTo(-200)
        compose.onNodeWithTag("adjust_amount").performTextReplacement("0")
        compose.onNodeWithText("Enter 1 to 100,000 steps").assertIsDisplayed()
    }

    @Test fun stepAdjustmentParsing() {
        assertThat(parseStepAdjustment("1500", add = true)).isEqualTo(1500)
        assertThat(parseStepAdjustment("1500", add = false)).isEqualTo(-1500)
        assertThat(parseStepAdjustment("", add = true)).isNull()
        assertThat(parseStepAdjustment("100001", add = true)).isNull()
    }
}
