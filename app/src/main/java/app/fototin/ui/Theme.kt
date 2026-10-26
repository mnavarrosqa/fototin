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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta de galería: superficies neutras para que el color lo pongan las fotos, un acento índigo
// para lo que se puede tocar y un ámbar cálido reservado para Recuerdos y favoritas.
private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF5B5B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6FA),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF121217),
    surface = Color(0xFFFBFBFD),
    onSurface = Color(0xFF121217),
    surfaceVariant = Color(0xFFECECF2),
    onSurfaceVariant = Color(0xFF5D5D6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F9),
    surfaceContainer = Color(0xFFF0F0F5),
    surfaceContainerHigh = Color(0xFFEAEAF0),
    surfaceContainerHighest = Color(0xFFE4E4EB),
    outline = Color(0xFF8E8EA0),
    outlineVariant = Color(0xFFDADAE3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFC4C4E0),
    onSecondary = Color(0xFF2A2A40),
    secondaryContainer = Color(0xFF2B2A4A),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF4A2E0A),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF0E0E12),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF24242C),
    onSurfaceVariant = Color(0xFFA9A9B8),
    surfaceContainerLowest = Color(0xFF09090C),
    surfaceContainerLow = Color(0xFF141418),
    surfaceContainer = Color(0xFF19191E),
    surfaceContainerHigh = Color(0xFF222228),
    surfaceContainerHighest = Color(0xFF2B2B32),
    outline = Color(0xFF6E6E80),
    outlineVariant = Color(0xFF2E2E36),
)

private val Base = Typography()

// Títulos sin serif, gruesos y un poco más juntos, como en las apps actuales.
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

// Esquinas generosas en tarjetas y diálogos.
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * @param dynamicColor usar los colores del fondo de pantalla (Android 12+). Por defecto, lo que
 * eligió el usuario en Ajustes.
 */
@Composable
fun FototinTheme(
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContextCompose.current
    val useDynamic = dynamicColor ?: remember(context) { Settings(context).dynamicColor }
    val colors = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
