package dev.bessaract.kassa.ui.theme

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
// Размеры — таблица «Шрифты» из docs/STYLE.md.

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Tektur — заголовки, цифры, кнопки. */
val Tektur = FontFamily(
    variable(R.font.tektur, 600),
    variable(R.font.tektur, 700),
    variable(R.font.tektur, 800),
)

/** JetBrains Mono — текст и ярлыки. */
val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 600),
    variable(R.font.jetbrains_mono, 700),
)

/** Иероглифы — системный CJK-шрифт (Noto CJK на Android). */
val Cjk = FontFamily.SansSerif

/** Табличные цифры одинаковой ширины: суммы не «прыгают». */
private const val TNUM = "tnum"

object KassaType {
    /** Текст: JetBrains Mono 400, 13. */
    val Body = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 19.sp, color = KassaColors.Text)

    /** Служебные строки и подписи. */
    val Small = Body.copy(fontSize = 11.sp, lineHeight = 15.sp, color = KassaColors.Grey)

    /** Ярлык: JetBrains Mono 600, капс, разрядка 0.12em, 10. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.W600, fontSize = 10.sp, letterSpacing = 0.12.em)

    /** Главная цифра: Tektur 800, 54. */
    val Big = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W800, fontSize = 54.sp, lineHeight = 56.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM)

    /** Вторая цифра: Tektur 700, 30. */
    val Mid = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W700, fontSize = 30.sp, lineHeight = 32.sp, fontFeatureSettings = TNUM)

    /** Сумма в строке операции. */
    val RowAmount = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W700, fontSize = 16.sp, color = KassaColors.Text, fontFeatureSettings = TNUM)

    /** Заголовок экрана: Tektur 800, капс, 34. */
    val ScreenTitle = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W800, fontSize = 34.sp, lineHeight = 32.sp, letterSpacing = 0.01.em, color = KassaColors.Text)

    /** Заголовок нижнего листа. */
    val SheetTitle = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W800, fontSize = 20.sp, color = KassaColors.Text)

    /** Кнопки: Tektur 800, капс, 14, без переноса (формы v3.1). */
    val Button = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W800, fontSize = 14.sp, letterSpacing = 0.02.em)

    /** Стрелки-декор ⇲⇲. */
    val Arrows = TextStyle(fontFamily = Tektur, fontWeight = FontWeight.W800, fontSize = 14.sp, letterSpacing = (-0.1).em)

    /** Подпись вкладки. */
    val Nav = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.08.em)

    /** Иероглифы: 700–800, 13–22. */
    fun cjk(size: Int) = TextStyle(fontFamily = Cjk, fontWeight = FontWeight.W800, fontSize = size.sp, lineHeight = size.sp)
}
