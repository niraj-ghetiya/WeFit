package com.wefit.app.achievements

import android.content.Context
import com.wefit.app.di.ApplicationScope
import com.wefit.app.domain.calc.AchievementCatalog
import com.wefit.app.domain.calc.AchievementEvaluator
import com.wefit.app.domain.repository.AchievementRepository
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.notifications.NotificationHelper
import com.wefit.app.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Re-evaluates achievements whenever tracked data changes and persists new unlocks. */
@Singleton
class AchievementWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val steps: StepRepository,
    private val weights: WeightRepository,
    private val activities: ActivityRepository,
    private val goals: GoalRepository,
    private val profile: ProfileRepository,
    private val achievements: AchievementRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(steps.allDays, weights.entries, activities.all, goals.goals, profile.profile) { s, w, a, g, p ->
                AchievementEvaluator.buildStats(s, w, a, g, p?.heightCm)
            }
                .debounce(750)
                .collect { stats ->
                    val newly = runCatching {
                        achievements.unlock(AchievementEvaluator.earned(stats), System.currentTimeMillis())
                    }.getOrDefault(emptyList())
                    newly.forEach { id ->
                        val def = AchievementCatalog.byId(id) ?: return@forEach
                        NotificationHelper.show(
                            context, 3000 + AchievementCatalog.all.indexOf(def),
                            NotificationHelper.CHANNEL_ACHIEVEMENTS,
                            "Achievement unlocked: ${def.title}", def.description, Routes.ACHIEVEMENTS,
                        )
                    }
                }
        }
    }
}
