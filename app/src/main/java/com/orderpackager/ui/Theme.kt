package com.orderpackager.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ─── Режимы темы ──────────────────────────────────────────────────────────────
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary          = Color(0xFF1565C0),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary        = Color(0xFF0288D1),
    background       = Color(0xFFF5F5F5),
    surface          = Color.White,
    error            = Color(0xFFB71C1C)
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF90CAF9),
    onPrimary        = Color(0xFF003064),
    primaryContainer = Color(0xFF004494),
    secondary        = Color(0xFF81D4FA),
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
    error            = Color(0xFFEF9A9A)
)

// ─── Глобальное состояние темы ─────────────────────────────────────────────────
val LocalThemeMode = compositionLocalOf { mutableStateOf(ThemeMode.SYSTEM) }

fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences("packager_prefs", Context.MODE_PRIVATE)
    return when (prefs.getString("theme_mode", "SYSTEM")) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK"  -> ThemeMode.DARK
        else    -> ThemeMode.SYSTEM
    }
}

fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences("packager_prefs", Context.MODE_PRIVATE)
        .edit().putString("theme_mode", mode.name).apply()
}

@Composable
fun PackagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography  = Typography(),
        content     = content
    )
}
