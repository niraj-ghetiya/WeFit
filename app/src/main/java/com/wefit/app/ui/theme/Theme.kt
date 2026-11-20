package com.wefit.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.wefit.app.domain.model.ThemeMode

object BrandColors {
    val Purple = Color(0xFF7C3AED)
    val PurpleLight = Color(0xFF9F67FF)
    val PurpleDeep = Color(0xFF5B21B6)
    val PurpleNight = Color(0xFF2A1060)
    val Orange = Color(0xFFFF8A00)
    val OrangeLight = Color(0xFFFFA53D)
    val Pink = Color(0xFFFF3EA5)
    val Background = Color(0xFFFAF8FF)
    val DarkBackground = Color(0xFF160D2B)
    val Success = Color(0xFF16A34A)
    val Danger = Color(0xFFDC2626)
}

@Immutable
data class WeFitExtraColors(
    val success: Color,
    val danger: Color,
    val accent: Color,
    val chartTrack: Color,
    val cardBorder: Color,
    val iconContainer: Color,
    val accentContainer: Color,
    val buttonGradient: Brush,
    val accentGradient: Brush,
    val splashGradient: Brush,
)

private val LightColors = lightColorScheme(
    primary = BrandColors.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2E0F6B),
    secondary = BrandColors.Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBD3),
    onSecondaryContainer = Color(0xFF4A2600),
    tertiary = BrandColors.Pink,
    background = BrandColors.Background,
    onBackground = Color(0xFF1C1433),
    surface = Color.White,
    onSurface = Color(0xFF1C1433),
    surfaceVariant = Color(0xFFF3EEFC),
    onSurfaceVariant = Color(0xFF6B6385),
    outline = Color(0xFFD9D0EC),
    outlineVariant = Color(0xFFEAE4F5),
    error = BrandColors.Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1E0B47),
    primaryContainer = Color(0xFF3B1F7A),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = BrandColors.OrangeLight,
    onSecondary = Color(0xFF3A1E00),
    secondaryContainer = Color(0xFF5A3200),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFFFF7CC0),
    background = BrandColors.DarkBackground,
    onBackground = Color(0xFFF1EBFF),
    surface = Color(0xFF211640),
    onSurface = Color(0xFFF1EBFF),
    surfaceVariant = Color(0xFF2D2152),
    onSurfaceVariant = Color(0xFFBDB2DA),
    outline = Color(0xFF4A3C75),
    outlineVariant = Color(0xFF362A5E),
    error = Color(0xFFF87171),
)

private val LightExtra = WeFitExtraColors(
    success = BrandColors.Success,
    danger = BrandColors.Danger,
    accent = BrandColors.Orange,
    chartTrack = Color(0xFFEFE8FB),
    cardBorder = Color(0xFFEDE7F7),
    iconContainer = Color(0xFFF1EAFF),
    accentContainer = Color(0xFFFFF1E0),
    buttonGradient = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
    accentGradient = Brush.linearGradient(listOf(Color(0xFFFFA53D), Color(0xFFFF7A00))),
    splashGradient = Brush.verticalGradient(listOf(Color(0xFF5B21B6), BrandColors.PurpleNight, Color(0xFF1A0940))),
)

private val DarkExtra = WeFitExtraColors(
    success = Color(0xFF4ADE80),
    danger = Color(0xFFF87171),
    accent = BrandColors.OrangeLight,
    chartTrack = Color(0xFF33285A),
    cardBorder = Color(0xFF33285A),
    iconContainer = Color(0xFF34265E),
    accentContainer = Color(0xFF4A2D12),
    buttonGradient = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
    accentGradient = Brush.linearGradient(listOf(Color(0xFFFFA53D), Color(0xFFFF7A00))),
    splashGradient = Brush.verticalGradient(listOf(Color(0xFF5B21B6), BrandColors.PurpleNight, Color(0xFF1A0940))),
)

val LocalWeFitColors = staticCompositionLocalOf { LightExtra }

private val WeFitTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val Typography.statValue: TextStyle get() = headlineSmall.copy(fontWeight = FontWeight.ExtraBold)

object WeFitTheme {
    val colors: WeFitExtraColors @Composable get() = LocalWeFitColors.current
}

@Composable
fun WeFitTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalWeFitColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = WeFitTypography,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
