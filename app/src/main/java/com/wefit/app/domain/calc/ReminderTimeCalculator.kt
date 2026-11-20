package com.wefit.app.domain.calc

import com.wefit.app.domain.model.ReminderSetting
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object ReminderTimeCalculator {
    /** Next time strictly after [now] at which [setting] should fire. */
    fun nextTrigger(setting: ReminderSetting, now: ZonedDateTime): ZonedDateTime {
        var t = now.with(LocalTime.of(setting.hour, setting.minute))
        if (setting.type.weekly) t = t.with(TemporalAdjusters.nextOrSame(setting.dayOfWeek))
        if (!t.isAfter(now)) t = if (setting.type.weekly) t.plusWeeks(1) else t.plusDays(1)
        return t
    }
}
