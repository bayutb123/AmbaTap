package com.bayutb123.ambatap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PlayBlue,
    onPrimary = Color.White,
    primaryContainer = PlayBlueContainer,
    onPrimaryContainer = OnPlayBlueContainer,
    secondary = RecordOrange,
    onSecondary = Color.White,
    secondaryContainer = RecordOrangeContainer,
    onSecondaryContainer = OnRecordOrangeContainer,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = Muted,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PlayBlueDark,
    onPrimary = InkDark,
    primaryContainer = PlayBlueContainerDark,
    onPrimaryContainer = OnSurfaceDark,
    secondary = RecordOrangeDark,
    onSecondary = InkDark,
    secondaryContainer = RecordOrangeContainerDark,
    onSecondaryContainer = OnSurfaceDark,
    background = InkDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MutedDark,
    outline = OutlineDark,
    outlineVariant = SurfaceVariantDark,
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = InkDark,
    error = ErrorRedDark,
    onError = InkDark,
)

@Composable
fun AmbaTapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AmbaTapTypography,
        content = content,
    )
}
