package com.wefit.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wefit.app.ui.components.NavRow
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitTopBar
import com.wefit.app.ui.navigation.Routes
import com.wefit.app.ui.theme.WeFitTheme

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Scaffold(topBar = { WeFitTopBar("More") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    NavRow(Icons.Filled.Insights, "Insights", "Trends, averages and progress summaries") { onNavigate(Routes.INSIGHTS) }
                    NavRow(Icons.Filled.Notifications, "Reminders", "Weight, steps, activity and weekly summary") { onNavigate(Routes.REMINDERS) }
                    NavRow(Icons.Filled.EmojiEvents, "Achievements", "Milestones you've unlocked", tint = WeFitTheme.colors.accent) { onNavigate(Routes.ACHIEVEMENTS) }
                }
            }
            item {
                WeFitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    NavRow(Icons.Filled.Person, "Profile & Settings", "Profile, units, theme, data export and backup") { onNavigate(Routes.SETTINGS) }
                    NavRow(Icons.Filled.PrivacyTip, "Privacy", "How WeFit handles your data") { onNavigate(Routes.PRIVACY) }
                    NavRow(Icons.Filled.Info, "About WeFit") { onNavigate(Routes.ABOUT) }
                }
            }
        }
    }
}
