package com.wefit.app.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.ui.components.GradientButton
import com.wefit.app.ui.theme.BrandColors
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val preferences: PreferencesRepository) : ViewModel() {
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}

data class OnboardingPage(val title: String, val body: String, val icon: ImageVector, val badges: List<ImageVector>)

val onboardingPages = listOf(
    OnboardingPage(
        "Track Your\nFitness Journey",
        "Monitor your weight, count your steps, and build healthier habits.",
        Icons.AutoMirrored.Filled.DirectionsRun,
        listOf(Icons.AutoMirrored.Filled.ShowChart, Icons.Filled.Favorite),
    ),
    OnboardingPage(
        "Set Goals &\nStay Motivated",
        "Set your target weight and daily step goals, and track your progress easily.",
        Icons.Filled.MonitorWeight,
        listOf(Icons.Filled.TrackChanges, Icons.Filled.EmojiEvents),
    ),
    OnboardingPage(
        "Build Healthy\nHabits",
        "Get reminders for workouts, weight tracking, and daily activity.",
        Icons.Filled.EventAvailable,
        listOf(Icons.Filled.NotificationsActive, Icons.Filled.Favorite),
    ),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingContent(onFinished = { viewModel.complete(onFinished) })
}

@Composable
fun OnboardingContent(onFinished: () -> Unit) {
    val pager = rememberPagerState { onboardingPages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == onboardingPages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinished, modifier = Modifier.testTag("onboarding_skip")) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = onboardingPages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingIllustration(page, Modifier.widthIn(max = 300.dp).fillMaxWidth())
                Spacer(Modifier.height(32.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { contentDescription = "Page ${pager.currentPage + 1} of ${onboardingPages.size}" },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(onboardingPages.size) { i ->
                val selected = i == pager.currentPage
                Box(
                    Modifier.padding(4.dp).animateContentSize().height(8.dp).size(width = if (selected) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        GradientButton(
            text = if (last) "Get Started" else "Next",
            onClick = { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            trailingIcon = if (last) null else Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag("onboarding_next"),
        )
    }
}

@Composable
private fun OnboardingIllustration(page: OnboardingPage, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize(0.92f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))),
        )
        Box(
            Modifier.fillMaxSize(0.56f).shadow(16.dp, CircleShape).clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandColors.PurpleLight, BrandColors.PurpleDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.55f)) }
        FloatingBadge(page.badges[0], WeFitTheme.colors.accentGradient, 64.dp, Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 28.dp))
        FloatingBadge(page.badges[1], Brush.linearGradient(listOf(BrandColors.Pink, BrandColors.Purple)), 52.dp,
            Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = (-28).dp))
    }
}

@Composable
private fun FloatingBadge(icon: ImageVector, brush: Brush, size: Dp, modifier: Modifier) {
    Box(
        modifier.size(size).shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(brush),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}
