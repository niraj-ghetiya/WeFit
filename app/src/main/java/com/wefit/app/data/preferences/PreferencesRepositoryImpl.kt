package com.wefit.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wefit.app.domain.model.ThemeMode
import com.wefit.app.domain.model.UnitSystem
import com.wefit.app.domain.model.UserPreferences
import com.wefit.app.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wefit_prefs")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferencesRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val UNITS = stringPreferencesKey("unit_system")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val SETUP = booleanPreferencesKey("setup_completed")
        val BACKGROUND = booleanPreferencesKey("background_tracking")
    }

    override val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                unitSystem = p[Keys.UNITS]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
                onboardingCompleted = p[Keys.ONBOARDING] ?: false,
                setupCompleted = p[Keys.SETUP] ?: false,
                backgroundTracking = p[Keys.BACKGROUND] ?: false,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) { context.dataStore.edit { it[Keys.THEME] = mode.name } }
    override suspend fun setUnitSystem(units: UnitSystem) { context.dataStore.edit { it[Keys.UNITS] = units.name } }
    override suspend fun setOnboardingCompleted(done: Boolean) { context.dataStore.edit { it[Keys.ONBOARDING] = done } }
    override suspend fun setSetupCompleted(done: Boolean) { context.dataStore.edit { it[Keys.SETUP] = done } }
    override suspend fun setBackgroundTracking(enabled: Boolean) { context.dataStore.edit { it[Keys.BACKGROUND] = enabled } }
    override suspend fun clear() { context.dataStore.edit { it.clear() } }
}
