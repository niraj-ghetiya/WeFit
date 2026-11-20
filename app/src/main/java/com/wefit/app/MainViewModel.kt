package com.wefit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.repository.PreferencesRepository
import com.wefit.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(
    val loading: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val startRoute: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(preferences: PreferencesRepository) : ViewModel() {

    val uiState: StateFlow<MainUiState> = preferences.preferences.map { p ->
        MainUiState(
            loading = false,
            themeMode = p.themeMode,
            startRoute = when {
                !p.onboardingCompleted -> Routes.ONBOARDING
                !p.setupCompleted -> Routes.SETUP
                else -> Routes.TAB_HOME
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val _pendingDestination = MutableStateFlow<String?>(null)
    val pendingDestination: StateFlow<String?> = _pendingDestination.asStateFlow()

    fun openDestination(route: String?) {
        if (route != null && Routes.isDeepLinkAllowed(route)) _pendingDestination.value = route
    }

    fun destinationHandled() { _pendingDestination.value = null }
}
