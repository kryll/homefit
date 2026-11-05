package com.kryll.homefit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Paleta de marca HomeFit (semilla turquesa #40C8C0, esquema de alta fidelidad cromática),
// alineada con la referencia visual que pidió José (estilo MyFitCoach: fondo oscuro,
// acento turquesa). Se define TODOS los roles de superficie: los que no se indican toman
// los valores púrpura por defecto de Material 3 y rompen la coherencia cromática.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006A65),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4DD2CA),
    onPrimaryContainer = Color(0xFF003735),
    secondary = Color(0xFF3A6662),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC2F0EB),
    onSecondaryContainer = Color(0xFF25524F),
    tertiary = Color(0xFF95491B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFAA7E),
    onTertiaryContainer = Color(0xFF552100),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF5FBF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFD7E5E3),
    onSurfaceVariant = Color(0xFF3C4948),
    outline = Color(0xFF6C7A78),
    outlineVariant = Color(0xFFBBC9C7),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F0),
    inversePrimary = Color(0xFF57DAD2),
    surfaceTint = Color(0xFF006A65),
    surfaceDim = Color(0xFFD5DBDA),
    surfaceBright = Color(0xFFF5FBF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3E9E8),
    surfaceContainerHighest = Color(0xFFDEE4E2),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6BEBE3),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF36C1B9),
    onPrimaryContainer = Color(0xFF002926),
    secondary = Color(0xFFA2CFCB),
    onSecondary = Color(0xFF033734),
    secondaryContainer = Color(0xFF164441),
    onSecondaryContainer = Color(0xFFACDAD5),
    tertiary = Color(0xFFFFCFB8),
    onTertiary = Color(0xFF552100),
    tertiaryContainer = Color(0xFFF99863),
    onTertiaryContainer = Color(0xFF421800),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3C4948),
    onSurfaceVariant = Color(0xFFBBC9C7),
    outline = Color(0xFF869392),
    outlineVariant = Color(0xFF3C4948),
    inverseSurface = Color(0xFFDEE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    inversePrimary = Color(0xFF006A65),
    surfaceTint = Color(0xFF57DAD2),
    surfaceDim = Color(0xFF0E1514),
    surfaceBright = Color(0xFF343A3A),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

@Composable
fun HomeFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // La app usa su paleta de marca por defecto; el color dinámico del sistema queda
    // disponible como opción (p. ej. un futuro ajuste en pantalla).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
