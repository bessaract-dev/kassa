package dev.bessaract.kassa.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Формы панелей v3: многоугольники из prototype/style.html. С 2026-10-22 вершины скруглены
// (roundPath в прототипе): стеклянные панели A/B — 3 dp; кнопки, чипы, поля — 4 dp; строки списков (C) и «+» — чистые срезы.

private fun shape(build: Path.(Size, Density) -> Unit) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply { build(size, density) })
}

private fun Density.px(dp: Int) = dp.dp.toPx()

/**
 * Замкнутый многоугольник со скруглёнными вершинами: у каждой вершины отступаем по рёбрам на r
 * (не больше половины ребра) и соединяем квадратичной кривой через вершину — как roundPath() в прототипе.
 */
fun Path.roundedPolygon(pts: List<Offset>, r: Float) {
    val n = pts.size
    for (i in 0 until n) {
        val p = pts[i]; val a = pts[(i - 1 + n) % n]; val b = pts[(i + 1) % n]
        val la = (a - p).getDistance(); val lb = (b - p).getDistance()
        val rr = minOf(r, la / 2, lb / 2)
        val p1 = p + (a - p) / la * rr; val p2 = p + (b - p) / lb * rr
        if (i == 0) moveTo(p1.x, p1.y) else lineTo(p1.x, p1.y)
        quadraticTo(p.x, p.y, p2.x, p2.y)
    }
    close()
}

/** Открытая ломаная со скруглёнными изгибами (рельс и уголок у панелей) — roundOpen() в прототипе. */
fun Path.roundedPolyline(pts: List<Offset>, r: Float) {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size - 1) {
        val p = pts[i]; val a = pts[i - 1]; val b = pts[i + 1]
        val la = (a - p).getDistance(); val lb = (b - p).getDistance()
        val rr = minOf(r, la / 2, lb / 2)
        val p1 = p + (a - p) / la * rr; val p2 = p + (b - p) / lb * rr
        lineTo(p1.x, p1.y); quadraticTo(p.x, p.y, p2.x, p2.y)
    }
    lineTo(pts.last().x, pts.last().y)
}

/** Фигура из точек (в dp-пикселях) со скруглением [radiusDp]. */
private fun rounded(radiusDp: Int, pts: Density.(Size) -> List<Offset>) = shape { s, d -> roundedPolygon(d.pts(s), d.px(radiusDp)) }

/**
 * Панель A (главная), формы v3.1: срез 22 dp слева сверху, ступенька 8 dp в верхней грани, срез справа сверху,
 * срезы снизу, вырез 8 dp по левому краю ниже 58% высоты. Оторванные куски рисует [panelDecor].
 */
val ShapeA = rounded(3) { s ->
    val w = s.width; val h = s.height
    fun p(v: Int) = px(v)
    val y = h * 0.58f
    listOf(
        Offset(p(22), 0f), Offset(w - p(104), 0f), Offset(w - p(96), p(8)), Offset(w - p(34), p(8)), Offset(w, p(42)),
        Offset(w, h - p(28)), Offset(w - p(28), h), Offset(p(26), h), Offset(p(8), h - p(18)), Offset(p(8), y),
        Offset(0f, y - p(8)), Offset(0f, p(22)),
    )
}

/** Панель B (белая): срез 30 dp справа сверху, вырез 8 dp по правому краю ниже 38%, «язычок» снизу. */
val ShapeB = rounded(3) { s ->
    val w = s.width; val h = s.height
    fun p(v: Int) = px(v)
    val y = h * 0.38f; val x = w * 0.42f
    listOf(
        Offset(0f, 0f), Offset(w - p(30), 0f), Offset(w, p(30)), Offset(w, y), Offset(w - p(8), y + p(8)),
        Offset(w - p(8), h - p(22)), Offset(w - p(30), h), Offset(x, h), Offset(x - p(8), h - p(8)),
        Offset(p(24), h - p(8)), Offset(0f, h - p(32)),
    )
}

/** Срезы по диагонали: левый верхний и правый нижний. Панель C (12 dp), «+» (12), чип (6). Вершины скруглены. */
fun diagonalCut(cutDp: Int, radiusDp: Int = 4) = rounded(radiusDp) { s ->
    val c = px(cutDp)
    listOf(Offset(c, 0f), Offset(s.width, 0f), Offset(s.width, s.height - c), Offset(s.width - c, s.height), Offset(0f, s.height), Offset(0f, c))
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
fun buttonShape(v: ButtonVariant) = rounded(4) { s ->
    val w = s.width; val ht = s.height
    fun p(x: Int) = px(x)
    val h = p(v.h)
    val a = w * v.tabFrom; val b = w * v.tabTo
    listOf(
        Offset(0f, p(v.tl) + h), Offset(p(v.tl), h), Offset(a, h), Offset(a + h, 0f), Offset(b - h, 0f), Offset(b, h),
        Offset(w - p(v.tr), h), Offset(w, p(v.tr) + h), Offset(w, ht - p(v.br)), Offset(w - p(v.br), ht),
        Offset(p(v.bl), ht), Offset(0f, ht - p(v.bl)),
    )
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

val ShapeC = diagonalCut(12, radiusDp = 0)
val ShapeFab = diagonalCut(12, radiusDp = 0)
val ShapeChip = diagonalCut(6)

/** Высота «уступа» нижнего листа. */
const val SHEET_NOTCH_DP = 18

/** Нижний лист: уступ в верхней грани на 30% ширины. */
val ShapeSheet = shape { s, d ->
    val n = d.px(SHEET_NOTCH_DP); val x = s.width * 0.3f
    moveTo(0f, n); lineTo(x, n); lineTo(x + n, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height); close()
}
