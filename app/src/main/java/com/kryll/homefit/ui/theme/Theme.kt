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

// Paleta de marca HomeFit (semilla coral energético, esquema de alta fidelidad cromática):
// primario coral vivo, contenedores cálidos y terciario turquesa como contraste.
// Se usa siempre (dynamicColor = false por defecto) para que la app se vea colorida
// también con el fondo de pantalla neutro de Android 12+.
// Se definen TODOS los roles de superficie: los que no se indican toman los valores
// púrpura por defecto de Material 3 y rompen la coherencia cromática.
private val LightColors = lightColorScheme(
    primary = Color(0xFFB62500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFF704F),
    onPrimaryContainer = Color(0xFF250200),
    secondary = Color(0xFF9E412C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFF9B84),
    onSecondaryContainer = Color(0xFF560C00),
    tertiary = Color(0xFF006783),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF29ABD4),
    onTertiaryContainer = Color(0xFF00141C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF271814),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF271814),
    surfaceVariant = Color(0xFFFFDAD2),
    onSurfaceVariant = Color(0xFF5B403A),
    outline = Color(0xFF8F7069),
    outlineVariant = Color(0xFFE4BEB5),
    inverseSurface = Color(0xFF3E2C28),
    inverseOnSurface = Color(0xFFFFEDE9),
    inversePrimary = Color(0xFFFFB4A3),
    surfaceTint = Color(0xFFB62500),
    surfaceDim = Color(0xFFF1D4CD),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0ED),
    surfaceContainer = Color(0xFFFFE9E4),
    surfaceContainerHigh = Color(0xFFFFE2DB),
    surfaceContainerHighest = Color(0xFFFADCD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF630F00),
    primaryContainer = Color(0xFFD73A16),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFB4A3),
    onSecondary = Color(0xFF601404),
    secondaryContainer = Color(0xFF73210F),
    onSecondaryContainer = Color(0xFFFFC4B6),
    tertiary = Color(0xFF61D4FF),
    onTertiary = Color(0xFF003545),
    tertiaryContainer = Color(0xFF007FA1),
    onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1E100C),
    onBackground = Color(0xFFFADCD6),
    surface = Color(0xFF1E100C),
    onSurface = Color(0xFFFADCD6),
    surfaceVariant = Color(0xFF5B403A),
    onSurfaceVariant = Color(0xFFE4BEB5),
    outline = Color(0xFFAB8981),
    outlineVariant = Color(0xFF5B403A),
    inverseSurface = Color(0xFFFADCD6),
    inverseOnSurface = Color(0xFF3E2C28),
    inversePrimary = Color(0xFFB62500),
    surfaceTint = Color(0xFFFFB4A3),
    surfaceDim = Color(0xFF1E100C),
    surfaceBright = Color(0xFF473531),
    surfaceContainerLowest = Color(0xFF180B08),
    surfaceContainerLow = Color(0xFF271814),
    surfaceContainer = Color(0xFF2C1C18),
    surfaceContainerHigh = Color(0xFF372622),
    surfaceContainerHighest = Color(0xFF43302C),
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
