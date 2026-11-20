package com.wefit.app.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wefit.app.core.util.Formatters
import com.wefit.app.domain.calc.AchievementCatalog
import com.wefit.app.domain.calc.AchievementDefinition
import com.wefit.app.domain.calc.AchievementEvaluator
import com.wefit.app.domain.calc.AchievementIcon
import com.wefit.app.domain.repository.AchievementRepository
import com.wefit.app.domain.repository.ActivityRepository
import com.wefit.app.domain.repository.GoalRepository
import com.wefit.app.domain.repository.ProfileRepository
import com.wefit.app.domain.repository.StepRepository
import com.wefit.app.domain.repository.WeightRepository
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.theme.BrandColors
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class AchievementItem(val def: AchievementDefinition, val unlockedAt: Long?, val progress: Float)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    steps: StepRepository,
    weights: WeightRepository,
    activities: ActivityRepository,
    goals: GoalRepository,
    profile: ProfileRepository,
    achievements: AchievementRepository,
) : ViewModel() {
    val items: StateFlow<List<AchievementItem>?> = combine(
        combine(steps.allDays, weights.entries, activities.all, goals.goals, profile.profile) { s, w, a, g, p ->
            AchievementEvaluator.buildStats(s, w, a, g, p?.heightCm)
        },
        achievements.unlocked,
    ) { stats, unlocked ->
        val byId = unlocked.associateBy { it.id }
        AchievementCatalog.all.map { def ->
            val at = byId[def.id]?.unlockedAt
            AchievementItem(def, at, if (at != null) 1f else AchievementEvaluator.progress(def, stats))
        }.sortedWith(compareBy({ it.unlockedAt == null }, { -(it.unlockedAt ?: 0L) }, { -it.progress }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun AchievementIcon.vector(): ImageVector = when (this) {
    AchievementIcon.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    AchievementIcon.STREAK -> Icons.Filled.Whatshot
    AchievementIcon.WEIGHT -> Icons.Filled.MonitorWeight
    AchievementIcon.CALENDAR -> Icons.Filled.CalendarMonth
    AchievementIcon.TROPHY -> Icons.Filled.EmojiEvents
    AchievementIcon.ACTIVITY -> Icons.Filled.FitnessCenter
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val unlockedCount = items?.count { it.unlockedAt != null } ?: 0

    Scaffold(topBar = { WeFitTopBar("Achievements", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(WeFitTheme.colors.accentGradient),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (unlockedCount > 0) "Well Done!" else "Your first milestone awaits",
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "You've achieved $unlockedCount of ${AchievementCatalog.all.size} milestones in your fitness journey.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
            items(items.orEmpty(), key = { it.def.id }) { item -> AchievementRow(item) }
        }
    }
}

@Composable
private fun AchievementRow(item: AchievementItem) {
    val unlocked = item.unlockedAt != null
    WeFitCard(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "${item.def.title}, ${if (unlocked) "unlocked" else "locked, ${(item.progress * 100).toInt()} percent"}"
        },
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(
                        if (unlocked) Brush.linearGradient(listOf(BrandColors.PurpleLight, BrandColors.PurpleDeep))
                        else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (unlocked) item.def.icon.vector() else Icons.Filled.Lock, null,
                    tint = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).alpha(if (unlocked) 1f else 0.85f)) {
                Text(item.def.title, style = MaterialTheme.typography.titleSmall)
                Text(item.def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unlocked) {
                    val date = Instant.ofEpochMilli(item.unlockedAt!!).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("Unlocked ${Formatters.mediumDate(date)}", style = MaterialTheme.typography.labelSmall, color = WeFitTheme.colors.success)
                } else {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = WeFitTheme.colors.chartTrack,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
