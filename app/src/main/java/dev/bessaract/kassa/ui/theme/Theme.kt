package dev.bessaract.kassa.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Срезанные углы: правый верхний и левый нижний (--cut и --cut-s в kassa.html).
val CutShape = CutCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 14.dp)
val CutShapeSmall = CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)

private val scheme = darkColorScheme(
    primary = KassaColors.Accent,
    onPrimary = KassaColors.OnAccent,
    secondary = KassaColors.Cyan,
    background = KassaColors.Bg,
    onBackground = KassaColors.Ink,
    surface = KassaColors.Surface,
    onSurface = KassaColors.Ink,
    surfaceVariant = KassaColors.Sunk,
    onSurfaceVariant = KassaColors.Muted,
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
