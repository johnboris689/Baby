package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val BabyDarkScheme = darkColorScheme(
    primary = BabyBlue,
    onPrimary = BabyText,
    secondary = BabyViolet,
    tertiary = BabyCyan,
    background = BabyBackground,
    surface = BabySurface,
    onBackground = BabyText,
    onSurface = BabyText,
    surfaceVariant = BabyGlass,
    onSurfaceVariant = BabyMuted
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else BabyDarkScheme

    MaterialTheme(
        colorScheme = BabyDarkScheme,
        typography = Typography(),
        content = content
    )
}
