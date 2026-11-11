package com.atelier.archive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HauteCoutureColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = ParchmentBase,
    primaryContainer = ParchmentBase,
    onPrimaryContainer = InkPrimary,
    secondary = ChampagneGold,
    onSecondary = InkPrimary,
    secondaryContainer = HairlineBorder,
    onSecondaryContainer = InkPrimary,
    tertiary = AntiqueBronze,
    background = ParchmentBase,
    onBackground = InkPrimary,
    surface = ParchmentCard,
    onSurface = InkPrimary,
    surfaceVariant = ParchmentBase,
    onSurfaceVariant = InkSecondary,
    outline = HairlineBorder,
    outlineVariant = DeepHairline
)

@Composable
fun AtelierArchiveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HauteCoutureColorScheme,
        typography = EditorialTypography,
        content = content
    )
}
