package dev.local.fasting.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Brand: the same teal family the charts are stepped from, so chrome and data agree.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF16705E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9E7DA),
    onPrimaryContainer = Color(0xFF00251C),
    secondary = Color(0xFF4B635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DF),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF3F6375),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF101416),
    surface = Color(0xFFF7FAF9),
    onSurface = Color(0xFF101416),
    surfaceVariant = Color(0xFFDCE5E2),
    onSurfaceVariant = Color(0xFF404947),
    surfaceContainer = Color(0xFFFBFDFC),
    surfaceContainerHigh = Color(0xFFF0F4F3),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBFC9C6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7BE3C3),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF17332C),
    onPrimaryContainer = Color(0xFFB9E7DA),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFA6CBE0),
    onTertiary = Color(0xFF0A344A),
    background = Color(0xFF0C1012),
    onBackground = Color(0xFFECF3F3),
    surface = Color(0xFF0C1012),
    onSurface = Color(0xFFECF3F3),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBFC9C6),
    surfaceContainer = Color(0xFF101416),
    surfaceContainerHigh = Color(0xFF1A2124),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FastingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVizColors provides vizColors(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = FastingTypography,
            content = content,
        )
    }
}
