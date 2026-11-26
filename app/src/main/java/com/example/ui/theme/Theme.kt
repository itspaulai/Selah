package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.AppThemeMode

private val SelahLightScheme = lightColorScheme(
    primary = SelahLightPrimary,
    onPrimary = SelahLightOnPrimary,
    primaryContainer = SelahLightPrimaryContainer,
    onPrimaryContainer = SelahLightOnPrimaryContainer,
    secondary = SelahLightSecondary,
    onSecondary = SelahLightOnSecondary,
    secondaryContainer = SelahLightSecondaryContainer,
    onSecondaryContainer = SelahLightOnSecondaryContainer,
    tertiary = SelahLightTertiary,
    onTertiary = SelahLightOnTertiary,
    tertiaryContainer = SelahLightTertiaryContainer,
    onTertiaryContainer = SelahLightOnTertiaryContainer,
    background = SelahLightBackground,
    onBackground = SelahLightOnBackground,
    surface = SelahLightSurface,
    onSurface = SelahLightOnSurface,
    surfaceVariant = SelahLightSurfaceVariant,
    onSurfaceVariant = SelahLightOnSurfaceVariant,
    surfaceContainerLowest = SelahLightSurfaceContainerLowest,
    surfaceContainerLow = SelahLightSurfaceContainerLow,
    surfaceContainer = SelahLightSurfaceContainer,
    surfaceContainerHigh = SelahLightSurfaceContainerHigh,
    surfaceContainerHighest = SelahLightSurfaceContainerHighest,
    outline = SelahLightOutline,
    outlineVariant = SelahLightOutlineVariant
)

private val SelahDarkScheme = darkColorScheme(
    primary = SelahDarkPrimary,
    onPrimary = SelahDarkOnPrimary,
    primaryContainer = SelahDarkPrimaryContainer,
    onPrimaryContainer = SelahDarkOnPrimaryContainer,
    secondary = SelahDarkSecondary,
    onSecondary = SelahDarkOnSecondary,
    secondaryContainer = SelahDarkSecondaryContainer,
    onSecondaryContainer = SelahDarkOnSecondaryContainer,
    tertiary = SelahDarkTertiary,
    onTertiary = SelahDarkOnTertiary,
    tertiaryContainer = SelahDarkTertiaryContainer,
    onTertiaryContainer = SelahDarkOnTertiaryContainer,
    background = SelahDarkBackground,
    onBackground = SelahDarkOnBackground,
    surface = SelahDarkSurface,
    onSurface = SelahDarkOnSurface,
    surfaceVariant = SelahDarkSurfaceVariant,
    onSurfaceVariant = SelahDarkOnSurfaceVariant,
    surfaceContainerLowest = SelahDarkSurfaceContainerLowest,
    surfaceContainerLow = SelahDarkSurfaceContainerLow,
    surfaceContainer = SelahDarkSurfaceContainer,
    surfaceContainerHigh = SelahDarkSurfaceContainerHigh,
    surfaceContainerHighest = SelahDarkSurfaceContainerHighest,
    outline = SelahDarkOutline,
    outlineVariant = SelahDarkOutlineVariant
)

val SelahShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun SelahTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> SelahDarkScheme
        else -> SelahLightScheme
    }

    val typography = remember(language) { selahTypographyFor(language) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = SelahShapes,
        content = content
    )
}
