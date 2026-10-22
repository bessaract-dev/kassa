package dev.bessaract.kassa.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

// Движение v3: механическое и быстрое — резкий старт, мягкая остановка, без «пружин» (docs/STYLE.md).
// Системная настройка «Убрать анимации» действует на все анимации Compose сама.
object Motion {
    const val FAST = 120
    const val BASE = 220
    const val SLOW = 360
    const val STAGGER = 45L
    /** Подсчёт цифр. */
    const val SCRAMBLE = 440

    /** Всё появляющееся. */
    val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Всё исчезающее, ×0.7 времени. */
    val EaseIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun exit(ms: Int) = (ms * 0.7f).toInt()
}
