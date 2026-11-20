package com.wefit.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wefit.app.ui.steps.AdjustStepsDialog
import com.wefit.app.ui.theme.WeFitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flow on a device with the real database. Run on a freshly cleared install:
 *   adb shell pm clear com.wefit.app.debug && ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CriticalFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 10_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun onboardingSetupDashboardWeightAndActivity() {
        // Splash -> onboarding (first launch only)
        waitForText("Track Your")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Get Started").performClick()

        // Profile setup
        waitForText("Set Up Your Profile")
        compose.onNodeWithTag("field_name").performTextInput("Tester")
        compose.onNodeWithTag("field_weight").performScrollTo().performTextInput("72.5")
        compose.onNodeWithTag("field_step_goal").performScrollTo().performTextReplacement("8000")
        compose.onNodeWithTag("setup_save").performScrollTo().performClick()

        // Home dashboard reflects saved data
        waitForText("Hello, Tester")
        compose.onNodeWithText("72.5 kg").assertIsDisplayed()
        compose.onNodeWithText("Goal: 8,000").assertIsDisplayed()

        // Add a second weight entry from the dashboard
        compose.onNodeWithText("Add Weight").performClick()
        waitForText("Enter Your Weight")
        compose.onNodeWithTag("weight_input").performTextReplacement("71.9")
        compose.onNodeWithTag("weight_save").performClick()
        waitForText("Hello, Tester")
        waitForText("71.9 kg")

        // Log an activity from the dashboard
        compose.onNodeWithText("Add Activity").performClick()
        waitForText("Activity type")
        compose.onNodeWithText("Cycling").performClick()
        compose.onNodeWithTag("activity_duration").performTextReplacement("40")
        compose.onNodeWithTag("activity_save").performScrollTo().performClick()

        // Logged activity appears on the dashboard
        waitForText("Hello, Tester")
        // The section sits further down the lazily composed dashboard list.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Logged today"))
        compose.onNodeWithText("Logged today").assertIsDisplayed()
        compose.onNodeWithText("Cycling").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class AdjustStepsDialogDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun enteringAmountEnablesSave() {
        var delta: Int? = null
        compose.setContent { WeFitTheme { AdjustStepsDialog("Today", onConfirm = { delta = it }, onDismiss = {}) } }
        compose.onNodeWithTag("adjust_amount").performTextInput("1500")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertThat(delta).isEqualTo(1500)
    }
}
