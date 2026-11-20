package com.wefit.app.ui.navigation

import java.time.LocalDate

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SETUP = "setup"

    const val TAB_HOME = "home"
    const val TAB_ACTIVITY = "activity"
    const val TAB_WEIGHT = "weight"
    const val TAB_GOALS = "goals"
    const val TAB_MORE = "more"

    const val ADD_WEIGHT = "add_weight?id={id}"
    const val ADD_ACTIVITY = "add_activity?id={id}&date={date}"
    const val INSIGHTS = "insights"
    const val REMINDERS = "reminders"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"

    val topLevel = listOf(TAB_HOME, TAB_ACTIVITY, TAB_WEIGHT, TAB_GOALS, TAB_MORE)

    fun addWeight(id: Long? = null) = "add_weight?id=${id ?: -1L}"
    fun addActivity(id: Long? = null, date: LocalDate? = null) =
        "add_activity?id=${id ?: -1L}&date=${date?.toEpochDay() ?: Long.MIN_VALUE}"

    /** Destinations that notifications are allowed to open. */
    fun isDeepLinkAllowed(route: String): Boolean =
        route.startsWith("add_weight") || route.startsWith("add_activity") ||
            route in setOf(TAB_ACTIVITY, INSIGHTS, ACHIEVEMENTS, REMINDERS)
}
