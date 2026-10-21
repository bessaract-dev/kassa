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

/** Панель A (главная): ступенька в верхней грани на 58% ширины, срезы справа сверху и слева снизу 14 dp. */
val ShapeA = shape { s, d ->
    val cut = d.px(14); val step = d.px(10); val x = s.width * 0.58f
    moveTo(0f, 0f); lineTo(x, 0f); lineTo(x + step, step); lineTo(s.width - cut, step)
    lineTo(s.width, step + cut); lineTo(s.width, s.height); lineTo(cut, s.height); lineTo(0f, s.height - cut); close()
}

/** Панель B (белая): срез справа сверху 14 dp, «язычок» снизу справа. */
val ShapeB = shape { s, d ->
    val cut = d.px(14); val t = d.px(8); val x = s.width * 0.6f
    moveTo(0f, 0f); lineTo(s.width - cut, 0f); lineTo(s.width, cut); lineTo(s.width, s.height)
    lineTo(x, s.height); lineTo(x - t, s.height - t); lineTo(0f, s.height - t); close()
}

/** Срезы по диагонали: левый верхний и правый нижний. Панель C (8 dp), «+» (12), кнопка (14), чип (6). */
fun diagonalCut(cutDp: Int) = shape { s, d ->
    val c = d.px(cutDp)
    moveTo(c, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height - c); lineTo(s.width - c, s.height)
    lineTo(0f, s.height); lineTo(0f, c); close()
}

/** Основная кнопка: срезы справа сверху и слева снизу 14 dp. */
val ShapeButton = shape { s, d ->
    val c = d.px(14)
    moveTo(0f, 0f); lineTo(s.width - c, 0f); lineTo(s.width, c); lineTo(s.width, s.height)
    lineTo(c, s.height); lineTo(0f, s.height - c); close()
}

val ShapeC = diagonalCut(8)
val ShapeFab = diagonalCut(12)
val ShapeChip = diagonalCut(6)

/** Высота «уступа» нижнего листа. */
const val SHEET_NOTCH_DP = 18

/** Нижний лист: уступ в верхней грани на 30% ширины. */
val ShapeSheet = shape { s, d ->
    val n = d.px(SHEET_NOTCH_DP); val x = s.width * 0.3f
    moveTo(0f, n); lineTo(x, n); lineTo(x + n, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height); close()
}
