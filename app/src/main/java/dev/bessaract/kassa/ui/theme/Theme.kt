package dev.bessaract.kassa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

/** Только тёмная тема — системная настройка не учитывается намеренно. */
@Composable
fun KassaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(bodyLarge = KassaType.Body, bodyMedium = KassaType.Body, bodySmall = KassaType.Small),
        content = content,
    )
}
