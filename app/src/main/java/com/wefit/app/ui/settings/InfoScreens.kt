package com.wefit.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wefit.app.BuildConfig
import com.wefit.app.R
import com.wefit.app.ui.components.SectionHeader
import com.wefit.app.ui.components.WeFitCard
import com.wefit.app.ui.components.WeFitLogo
import com.wefit.app.ui.components.WeFitTopBar

@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { WeFitTopBar(title, onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    WeFitCard(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    InfoScaffold("About WeFit", onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WeFitLogo(markSize = 56.dp, fontSize = MaterialTheme.typography.displaySmall.fontSize)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoSection("What WeFit does", "WeFit counts your daily steps with your phone's step sensor, tracks your body weight, logs walks, runs, rides and workouts, and turns it all into goals, streaks, insights and achievements.")
        InfoSection("No account, no ads, no subscription", "There's nothing to sign up for. WeFit works fully offline and every feature is free.")
        InfoSection("About the numbers", "Distance, calories and active time are estimates based on your steps, height and weight using standard formulas (stride ≈ 41.4% of height; MET values for logged activities). They're meant for tracking trends, not medical use.")
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold("Privacy", onBack) {
        InfoSection("Your data stays on your device", "Your profile, weights, steps, activities, goals and achievements are stored only in WeFit's private storage on this phone. WeFit has no servers, no analytics and no advertising SDKs, and it doesn't use the internet.")
        InfoSection("Permissions", "• Physical activity: to read the step counter sensor.\n• Notifications: to show the reminders you switch on.\n• Foreground service (health): only if you turn on background step tracking.\n• Run at startup: to resume step tracking after a restart.")
        InfoSection("Export, backup and deletion", "Exports and backups are saved only where you choose. Android cloud backup is disabled for WeFit, so nothing is copied off the device without your action. \"Reset application data\" in Settings permanently deletes everything, and uninstalling WeFit removes all its data.")
    }
}
