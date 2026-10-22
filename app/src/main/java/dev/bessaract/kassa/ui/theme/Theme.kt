package dev.bessaract.kassa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val scheme = darkColorScheme(
    primary = KassaColors.Lime,
    onPrimary = KassaColors.Ink,
    secondary = KassaColors.Paper,
    onSecondary = KassaColors.Ink,
    background = KassaColors.Bg,
    onBackground = KassaColors.Text,
    surface = KassaColors.Surface,
    onSurface = KassaColors.Text,
    surfaceVariant = KassaColors.Raised,
    onSurfaceVariant = KassaColors.Grey,
    outline = KassaColors.Line,
    error = KassaColors.Bad,
)

/** Масштаб шрифта приложения поверх системного: на телефоне 100% оказался мелким, утверждено 130% (2026-10-22). */
const val APP_FONT_SCALE = 1.3f

/** Только тёмная тема — системная настройка не учитывается намеренно. */
@Composable
fun KassaTheme(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * APP_FONT_SCALE)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = KassaType.Body, bodyMedium = KassaType.Body, bodySmall = KassaType.Small),
            content = content,
        )
    }
}
