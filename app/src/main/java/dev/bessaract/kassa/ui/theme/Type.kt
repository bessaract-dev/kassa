package dev.bessaract.kassa.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.bessaract.kassa.R

// Шрифты лежат в APK (res/font) — без загрузки из сети. Оба файла вариативные: толщина задаётся осью wght.

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Tektur — заголовки и цифры. */
val Tektur = FontFamily(
    variable(R.font.tektur, 500),
    variable(R.font.tektur, 700),
    variable(R.font.tektur, 900),
)

/** JetBrains Mono — основной текст. */
val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 600),
    variable(R.font.jetbrains_mono, 700),
)

/** Свечение текста (text-shadow в kassa.html). */
fun glow(color: Color, radius: Float) = Shadow(color = color, offset = Offset.Zero, blurRadius = radius)

object KassaType {
    val Body = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, lineHeight = 20.sp, color = KassaColors.Ink)
    val Small = Body.copy(fontSize = 12.sp, lineHeight = 16.sp, color = KassaColors.Muted)

    /** .top h1 */
    val ScreenTitle = TextStyle(
        fontFamily = Tektur, fontWeight = FontWeight.W700, fontSize = 16.sp, letterSpacing = 0.08.em,
        color = KassaColors.Accent, shadow = glow(KassaColors.Accent.copy(alpha = 0.45f), 10f),
    )

    /** .card h2 */
    val CardTitle = TextStyle(
        fontFamily = Tektur, fontWeight = FontWeight.W700, fontSize = 12.sp, letterSpacing = 0.14.em,
        color = KassaColors.Cyan,
    )

    /** .hero .lbl */
    val HeroLabel = Body.copy(fontSize = 12.sp, letterSpacing = 0.12.em, color = KassaColors.Accent)

    /** .hero .big */
    val HeroBig = TextStyle(
        fontFamily = Tektur, fontWeight = FontWeight.W900, fontSize = 40.sp, lineHeight = 44.sp,
        color = KassaColors.Accent, shadow = glow(KassaColors.Accent.copy(alpha = 0.55f), 18f),
    )

    /** .stat b */
    val Stat = TextStyle(
        fontFamily = Tektur, fontWeight = FontWeight.W700, fontSize = 17.sp,
        color = KassaColors.Ink, shadow = glow(KassaColors.Good.copy(alpha = 0.35f), 8f),
    )

    /** nav button */
    val Nav = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W500, fontSize = 11.sp, letterSpacing = 0.06.em)
}
