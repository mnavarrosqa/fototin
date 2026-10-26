package app.fototin.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext as LocalContextCompose
import app.fototin.data.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Paleta cálida tipo álbum de fotos: terracota, crema y durazno.
private val Light = lightColorScheme(
    primary = Color(0xFFB4483A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF8A5A2B),
    secondaryContainer = Color(0xFFFFE3C7),
    onSecondaryContainer = Color(0xFF2E1600),
    tertiary = Color(0xFF5B6B3A),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF241A17),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF241A17),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLow = Color(0xFFFFF0EB),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF7E4DE),
    outlineVariant = Color(0xFFD8C2BC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    onPrimary = Color(0xFF690004),
    primaryContainer = Color(0xFF8F3125),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFFFB878),
    secondaryContainer = Color(0xFF6C4316),
    onSecondaryContainer = Color(0xFFFFE3C7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    outlineVariant = Color(0xFF53433F),
)

private val Base = Typography()

private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * @param dynamicColor usar los colores del fondo de pantalla (Android 12+). Por defecto, lo que
 * eligió el usuario en Ajustes.
 */
@Composable
fun FototinTheme(
    dynamicColor: Boolean = remember { Settings(LocalContextCompose.current).dynamicColor },
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContextCompose.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
