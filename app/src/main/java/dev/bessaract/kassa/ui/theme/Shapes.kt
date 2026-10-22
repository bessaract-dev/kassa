package dev.bessaract.kassa.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Формы панелей v3: clip-path из prototype/style.html. Скруглений нет нигде.

private fun shape(build: Path.(Size, Density) -> Unit) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply { build(size, density) })
}

private fun Density.px(dp: Int) = dp.dp.toPx()

/**
 * Панель A (главная), формы v3.1: срез 22 dp слева сверху, ступенька 8 dp в верхней грани, срез справа сверху,
 * срезы снизу, вырез 8 dp по левому краю ниже 58% высоты. Оторванные куски рисует [panelDecor].
 */
val ShapeA = shape { s, d ->
    val w = s.width; val h = s.height
    fun p(v: Int) = d.px(v)
    val y = h * 0.58f
    moveTo(p(22), 0f); lineTo(w - p(104), 0f); lineTo(w - p(96), p(8)); lineTo(w - p(34), p(8)); lineTo(w, p(42))
    lineTo(w, h - p(28)); lineTo(w - p(28), h); lineTo(p(26), h); lineTo(p(8), h - p(18)); lineTo(p(8), y)
    lineTo(0f, y - p(8)); lineTo(0f, p(22)); close()
}

/** Панель B (белая): срез 30 dp справа сверху, вырез 8 dp по правому краю ниже 38%, «язычок» снизу. */
val ShapeB = shape { s, d ->
    val w = s.width; val h = s.height
    fun p(v: Int) = d.px(v)
    val y = h * 0.38f; val x = w * 0.42f
    moveTo(0f, 0f); lineTo(w - p(30), 0f); lineTo(w, p(30)); lineTo(w, y); lineTo(w - p(8), y + p(8))
    lineTo(w - p(8), h - p(22)); lineTo(w - p(30), h); lineTo(x, h); lineTo(x - p(8), h - p(8))
    lineTo(p(24), h - p(8)); lineTo(0f, h - p(32)); close()
}

/** Срезы по диагонали: левый верхний и правый нижний. Панель C (12 dp), «+» (12), чип (6). */
fun diagonalCut(cutDp: Int) = shape { s, d ->
    val c = d.px(cutDp)
    moveTo(c, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height - c); lineTo(s.width - c, s.height)
    lineTo(0f, s.height); lineTo(0f, c); close()
}

/** Вариант формы длинной кнопки: чёлка от/до (% ширины), её высота и срезы углов, dp. */
class ButtonVariant(val tabFrom: Float, val tabTo: Float, val h: Int, val tl: Int, val tr: Int, val br: Int, val bl: Int)

/** 4 варианта одного «языка» — как BTN_VARIANTS в prototype/style.html. */
val ButtonVariants = listOf(
    ButtonVariant(0.24f, 0.66f, 6, 12, 18, 10, 26),
    ButtonVariant(0.38f, 0.84f, 6, 20, 10, 24, 10),
    ButtonVariant(0.12f, 0.50f, 7, 10, 22, 12, 20),
    ButtonVariant(0.30f, 0.62f, 5, 16, 14, 20, 14),
)

/** Длинная кнопка: цельная форма, верхняя грань «прямо → вверх (чёлка) → прямо → вниз → прямо». */
fun buttonShape(v: ButtonVariant) = shape { s, d ->
    val w = s.width; val ht = s.height
    fun p(x: Int) = d.px(x)
    val h = p(v.h)
    val a = w * v.tabFrom; val b = w * v.tabTo
    moveTo(0f, p(v.tl) + h); lineTo(p(v.tl), h); lineTo(a, h); lineTo(a + h, 0f); lineTo(b - h, 0f); lineTo(b, h)
    lineTo(w - p(v.tr), h); lineTo(w, p(v.tr) + h); lineTo(w, ht - p(v.br)); lineTo(w - p(v.br), ht)
    lineTo(p(v.bl), ht); lineTo(0f, ht - p(v.bl)); close()
}

/** Хэш как в прототипе (a*31 + код символа, 32 бита без знака): вариант стабилен для текста кнопки. */
fun buttonVariantIndex(key: String): Int {
    var a = 7L
    for (c in key) a = (a * 31 + c.code) and 0xFFFFFFFFL
    return (a % ButtonVariants.size).toInt()
}

/** Варианты для кнопок одного экрана: по хэшу, но без повторов (как shapeButtons() в прототипе). */
fun distinctButtonVariants(keys: List<String>): List<Int> {
    val used = mutableSetOf<Int>()
    return keys.map { k ->
        var i = buttonVariantIndex(k)
        while (i in used && used.size < ButtonVariants.size) i = (i + 1) % ButtonVariants.size
        used += i; i
    }
}

val ShapeC = diagonalCut(12)
val ShapeFab = diagonalCut(12)
val ShapeChip = diagonalCut(6)

/** Высота «уступа» нижнего листа. */
const val SHEET_NOTCH_DP = 18

/** Нижний лист: уступ в верхней грани на 30% ширины. */
val ShapeSheet = shape { s, d ->
    val n = d.px(SHEET_NOTCH_DP); val x = s.width * 0.3f
    moveTo(0f, n); lineTo(x, n); lineTo(x + n, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height); close()
}
