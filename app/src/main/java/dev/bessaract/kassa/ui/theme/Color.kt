package dev.bessaract.kassa.ui.theme

import androidx.compose.ui.graphics.Color

// Палитра дизайна v3 «мех-HUD» (docs/STYLE.md, :root в prototype/style.html).
object KassaColors {
    /** Угольный фон экрана. */
    val Bg = Color(0xFF0E0F0F)
    /** Тёмные панели, навигация, нижний лист. */
    val Surface = Color(0xFF17191A)
    /** Иконки, чипы, нажатое. */
    val Raised = Color(0xFF212425)
    /** Рамки, линии. */
    val Line = Color(0xFF34383A)
    /** Приглушённый текст, служебные строки. */
    val Grey = Color(0xFF8A8F91)
    /** Основной текст на тёмном. */
    val Text = Color(0xFFE9EAE4)
    /** Белые информационные панели. */
    val Paper = Color(0xFFEFEFEA)
    /** Текст на белом и лайме. */
    val Ink = Color(0xFF0B0C0C)
    /** Акцент. */
    val Lime = Color(0xFFC8FF00)
    /** Перерасход, удаление. */
    val Bad = Color(0xFFFF3D2E)
    val Warn = Color(0xFFFF9F0A)
}
