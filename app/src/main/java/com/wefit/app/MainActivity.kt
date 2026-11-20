package com.wefit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wefit.app.notifications.NotificationHelper
import com.wefit.app.ui.navigation.WeFitNavHost
import com.wefit.app.ui.theme.WeFitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value.loading }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val pending by viewModel.pendingDestination.collectAsStateWithLifecycle()
            WeFitTheme(themeMode = state.themeMode) {
                WeFitNavHost(
                    startRoute = state.startRoute,
                    pendingDestination = pending,
                    onDestinationHandled = viewModel::destinationHandled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        viewModel.openDestination(intent?.getStringExtra(NotificationHelper.EXTRA_DESTINATION))
    }
}
