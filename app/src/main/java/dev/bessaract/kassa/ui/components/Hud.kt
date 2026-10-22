package dev.bessaract.kassa.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.Motion
import dev.bessaract.kassa.ui.theme.ButtonVariants
import dev.bessaract.kassa.ui.theme.ShapeA
import dev.bessaract.kassa.ui.theme.ShapeB
import dev.bessaract.kassa.ui.theme.buttonShape
import dev.bessaract.kassa.ui.theme.buttonVariantIndex
import dev.bessaract.kassa.ui.theme.ShapeC
import dev.bessaract.kassa.ui.theme.ShapeChip
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Фон: почти невидимая сетка 24 dp (белый 2%). */
fun Modifier.gridBackground(): Modifier = background(KassaColors.Bg).drawBehind {
    val step = 24.dp.toPx()
    val c = Color.White.copy(alpha = 0.024f)
    var x = 0f
    while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

/**
 * Нажатие: проседание на 2 dp и масштаб 0.98 за 120 мс, короткая вибрация.
 * Мгновенная инверсия цветов из прототипа пока не перенесена.
 */
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, tween(Motion.FAST, easing = Motion.EaseOut), label = "press")
    val haptic = LocalHapticFeedback.current
    val shift = with(LocalDensity.current) { 2.dp.toPx() }
    this
        .graphicsLayer { translationY = shift * p; scaleX = 1f - 0.02f * p; scaleY = 1f - 0.02f * p }
        .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        }
}

/** Появление панели: сдвиг на 8 dp вверх и проявление, по очереди с шагом 45 мс (не дольше 8 шагов). */
fun Modifier.reveal(index: Int): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * Motion.STAGGER)
        p.animateTo(1f, tween(Motion.BASE, easing = Motion.EaseOut))
    }
    val shift = with(LocalDensity.current) { 8.dp.toPx() }
    graphicsLayer { alpha = p.value; translationY = shift * (1f - p.value) }
}

enum class PanelKind(val bg: Color, val fg: Color) {
    LIME(KassaColors.Lime, KassaColors.Ink),
    BAD(KassaColors.Bad, KassaColors.Ink),
    PAPER(KassaColors.Paper, KassaColors.Ink),
    DARK(KassaColors.Surface, KassaColors.Text),
}

/**
 * Панель v3. Тёмная — с рамкой цвета line; [highlight] — заполненная строка списка: рамка лайм 45% и отсвет слева.
 * Светлые панели A и B — «стекло» (2026-10-22, макет Figma): полупрозрачная заливка, зерно, свечение цвета панели,
 * светлая кромка. Размытия под панелью в Android нет, поэтому заливка плотнее, чем в прототипе (62% / 88%).
 * [onClick] у стеклянной панели — нажатие с утапливанием и наклоном к пальцу. При перерасходе LIME → BAD плавно.
 */
@Composable
fun Panel(
    shape: Shape,
    kind: PanelKind,
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg by animateColorAsState(kind.bg, tween(Motion.BASE, easing = Motion.EaseOut), label = "panel")
    val glass = kind != PanelKind.DARK && (shape === ShapeA || shape === ShapeB)
    var m = modifier.fillMaxWidth()
    if (glass) {
        val fill = bg.copy(alpha = if (kind == PanelKind.PAPER) GLASS_PAPER else GLASS_LIME)
        val grain = rememberGrain()
        if (onClick != null) m = m.glassPress(onClick)
        m = m.glassGlow(shape) { bg }
            .panelDecor(shape) { bg.copy(alpha = 0.65f) }
            .clip(shape)
            .background(fill)
            .drawBehind {
                // зерно: чёрный шум ~18%, умножение
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.18f, blendMode = BlendMode.Multiply)
                // светлая кромка: ярче сверху слева, гаснет к середине, чуть светлее снизу справа; видна внутренняя половина штриха
                val outline = shape.createOutline(size, layoutDirection, this)
                if (outline is Outline.Generic) drawPath(
                    outline.path,
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.75f), 0.45f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.35f),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    ),
                    style = Stroke(2.4.dp.toPx()),
                )
            }
    } else {
        m = m.panelDecor(shape) { if (kind == PanelKind.DARK) KassaColors.Line else bg }.clip(shape).background(bg)
        if (highlight) m = m.drawBehind {
            drawRect(Brush.horizontalGradient(0f to KassaColors.Lime.copy(alpha = 0.08f), 0.55f to Color.Transparent))
        }
        if (kind == PanelKind.DARK) m = m.border(1.dp, if (highlight) KassaColors.Lime.copy(alpha = 0.45f) else KassaColors.Line, shape)
    }
    Box(m) {
        Column(Modifier.padding(padding)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides kind.fg,
            ) { content() }
        }
    }
}

/**
 * Оторванные куски панелей A и B (формы v3.1) — того же цвета, что панель, рисуются за её границами.
 * A: планка слева снаружи, штриховка в нижнем вырезе слева, рельс сверху справа параллельно грани и срезу (зазор 6 dp).
 * B: планка в правом вырезе, оторванный уголок снизу слева, штриховка слева снаружи.
 */
private fun Modifier.panelDecor(shape: Shape, color: () -> Color): Modifier =
    if (shape !== ShapeA && shape !== ShapeB) this else drawBehind {
        val c = color(); val w = size.width; val h = size.height
        fun p(v: Float) = v.dp.toPx()
        fun poly(vararg pts: Float) = Path().apply {
            moveTo(pts[0], pts[1]); var i = 2
            while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
            close()
        }
        if (shape === ShapeA) {
            val y = h * 0.58f
            val top = p(34f); val bottom = y - p(18f)
            if (bottom > top + p(10f)) drawPath(poly(-p(9f), top + p(5f), -p(4f), top, -p(4f), bottom, -p(9f), bottom - p(5f)), c)
            hatchRect(c, 0f, y + p(6f), p(5f), h - p(28f))
            val rail = Path().apply {
                moveTo(w - p(90f), p(2f)); lineTo(w - p(31.5f), p(2f)); lineTo(w + p(6f), p(39.5f)); lineTo(w + p(6f), p(82f))
            }
            drawPath(rail, c, style = Stroke(p(2f)))
        } else {
            val y = h * 0.38f
            drawRect(c, Offset(w - p(4f), y + p(18f)), androidx.compose.ui.geometry.Size(p(4f), (h - p(28f)) - (y + p(18f))))
            val ox = -p(8f); val oy = h + p(1f) - p(30f)
            drawPath(poly(ox, oy, ox + p(4f), oy, ox + p(4f), oy + p(4f), ox + p(30f), oy + p(30f), ox + p(26f), oy + p(30f), ox, oy + p(4f)), c)
            hatchRect(c, -p(9f), p(20f), -p(4f), p(20f) + h * 0.3f)
        }
    }

private const val GLASS_LIME = 0.74f
private const val GLASS_PAPER = 0.93f

/** Зерно стекла: 140×140 серого шума, один раз на всё приложение. */
private var grainBitmap: ImageBitmap? = null

@Composable
private fun rememberGrain(): ImageBitmap = remember {
    grainBitmap ?: run {
        val n = 140
        val rnd = java.util.Random(48_1508)
        val px = IntArray(n * n) { val v = 128 + rnd.nextInt(128); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap().also { grainBitmap = it }
    }
}

/**
 * Свечение цвета панели: та же форма, размытие ~30, сдвиг 4 dp вниз, 70%.
 * Внутри формы не рисуется — не просвечивает сквозь полупрозрачное стекло.
 */
private fun Modifier.glassGlow(shape: Shape, color: () -> Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this) as? Outline.Generic ?: return@drawBehind
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color().copy(alpha = 0.7f).toArgb()
        maskFilter = android.graphics.BlurMaskFilter(18.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.clipOutPathCompat(outline.path.asAndroidPath())
        nc.translate(0f, 4.dp.toPx())
        nc.drawPath(outline.path.asAndroidPath(), paint)
        nc.restore()
    }
}

private fun android.graphics.Canvas.clipOutPathCompat(p: android.graphics.Path) {
    if (android.os.Build.VERSION.SDK_INT >= 26) clipOutPath(p)
    else @Suppress("DEPRECATION") clipPath(p, android.graphics.Region.Op.DIFFERENCE)
}

/**
 * Нажатие стеклянной панели: утапливается и наклоняется к пальцу до 7°, 120 мс; возврат 220 мс.
 * При выключенных анимациях — просто нажатие.
 */
private fun Modifier.glassPress(onClick: () -> Unit): Modifier = composed {
    val motion = rememberMotionEnabled()
    var down by remember { mutableStateOf(false) }
    var tilt by remember { mutableStateOf(Offset.Zero) }
    val p by animateFloatAsState(
        if (down && motion) 1f else 0f,
        tween(if (down) Motion.FAST else 220, easing = Motion.EaseOut), label = "glass",
    )
    val haptic = LocalHapticFeedback.current
    this
        .graphicsLayer {
            cameraDistance = 12f * density
            // палец снизу — нижний край уходит от нас, справа — правый
            rotationX = tilt.y * 7f * p
            rotationY = tilt.x * 7f * p
            scaleX = 1f - 0.03f * p; scaleY = 1f - 0.03f * p
            translationY = 1.dp.toPx() * p
        }
        .semantics { role = Role.Button }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = { o ->
                    tilt = Offset(o.x / size.width * 2f - 1f, o.y / size.height * 2f - 1f)
                    down = true
                    tryAwaitRelease()
                    down = false
                },
                onTap = { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey); onClick() },
            )
        }
}

/** Штриховка в прямоугольнике (как repeating-linear-gradient(-45deg, c 0 3px, transparent 3px 6px)). */
private fun DrawScope.hatchRect(c: Color, l: Float, t: Float, r: Float, b: Float) {
    if (b <= t) return
    clipRect(l, t, r, b) {
        val step = 8.5.dp.toPx(); val sw = 3.dp.toPx(); val span = r - l
        var y = t - span
        while (y < b + span) { drawLine(c, Offset(l, y + span), Offset(r, y), sw); y += step }
    }
}

/** .lbl: ярлык капсом слева и иероглиф справа. */
@Composable
fun PanelLabel(text: String, cjk: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = KassaType.Label.copy(color = color), modifier = Modifier.weight(1f))
        Text(cjk, style = KassaType.cjk(13).copy(color = color))
    }
}

/** Штриховка «////». */
@Composable
fun Hatch(color: Color, modifier: Modifier = Modifier.size(64.dp, 10.dp)) {
    Canvas(modifier.clip(androidx.compose.ui.graphics.RectangleShape)) {
        val gap = 7.dp.toPx(); val w = 3.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), w)
            x += gap
        }
    }
}

@Composable
fun Arrows(count: Int, color: Color) {
    Text("⇲".repeat(count), style = KassaType.Arrows.copy(color = color))
}

/**
 * Подсчёт цифр: при смене суммы разряды «перебираются» случайными цифрами
 * и встают на место слева направо за 440 мс. Не влезает по ширине — шрифт уменьшается.
 * [glitch] — эта цифра может «глитчить» (главная цифра «Сегодня»).
 */
@Composable
fun ScrambleText(target: String, style: TextStyle, modifier: Modifier = Modifier, glitch: GlitchTarget? = null) {
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val a = Animatable(0f)
        a.animateTo(1f, tween(Motion.SCRAMBLE, easing = LinearEasing)) {
            val settled = (value * target.length).toInt()
            shown = target.mapIndexed { k, ch ->
                if (k < settled || !ch.isDigit()) ch else ('0' + Random.nextInt(10))
            }.joinToString("")
        }
        shown = target
    }
    val draw: @Composable (Modifier, TextStyle?) -> Unit = { m, over ->
        BasicText(
            shown,
            style = if (over != null) style.merge(over) else style,
            modifier = m,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.4f, maxFontSize = style.fontSize),
        )
    }
    if (glitch != null) Glitchable(glitch, modifier, draw) else draw(modifier, null)
}

/** Отступы длинных кнопок v3.1: 26 dp по бокам, 24 сверху (с учётом чёлки), 20 снизу. */
private val ButtonPadding = Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 20.dp)

/**
 * Основная кнопка: лайм, цельная форма с чёлкой, текст капсом в одну строку и стрелки ⇲⇲.
 * [variant] — номер формы из [ButtonVariants]; по умолчанию по тексту, как в прототипе.
 * Если на экране несколько кнопок, передай варианты из distinctButtonVariants, чтобы они не повторялись.
 */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, variant: Int = buttonVariantIndex(text + "⇲⇲"), onClick: () -> Unit) {
    val shape = remember(variant) { buttonShape(ButtonVariants[variant]) }
    Row(
        modifier.fillMaxWidth().press(onClick = onClick).clip(shape).background(KassaColors.Lime).then(ButtonPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text.uppercase(), style = KassaType.Button.copy(color = KassaColors.Ink), modifier = Modifier.weight(1f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Arrows(2, KassaColors.Ink)
    }
}

/** Вторичная кнопка: та же форма контуром line, справа иероглиф. */
@Composable
fun GhostButton(
    text: String,
    cjk: String,
    modifier: Modifier = Modifier,
    color: Color = KassaColors.Text,
    variant: Int = buttonVariantIndex(text + cjk),
    onClick: () -> Unit,
) {
    val shape = remember(variant) { buttonShape(ButtonVariants[variant]) }
    Row(
        modifier.fillMaxWidth().press(onClick = onClick).clip(shape).background(KassaColors.Surface)
            .border(1.dp, KassaColors.Line, shape).then(ButtonPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text.uppercase(), style = KassaType.Button.copy(color = color), modifier = Modifier.weight(1f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(cjk, style = KassaType.cjk(12).copy(color = color))
    }
}

/** Чип выбора: включённый — лайм. */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) KassaColors.Lime else KassaColors.Raised, tween(Motion.FAST), label = "chip")
    Text(
        text,
        style = KassaType.Body.copy(
            fontSize = KassaType.Small.fontSize,
            color = if (on) KassaColors.Ink else KassaColors.Text,
            fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.W700 else null,
        ),
        modifier = Modifier.press(onClick = onClick).clip(ShapeChip).background(bg).padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/**
 * Строка операции или плана (.op): иконка-иероглиф, название и подробности, сумма.
 * [positive] — доход: иконка на лайме, сумма лаймом. [meta] можно раскрасить (статус плана) через [metaColor].
 * [dimmed] — проведённая строка плана, приглушена. [trailing] — кнопка справа («Оплатил»).
 */
@Composable
fun OpRow(
    icon: String,
    title: String,
    meta: String,
    amount: String,
    modifier: Modifier = Modifier,
    positive: Boolean = false,
    metaColor: Color = KassaColors.Grey,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Panel(ShapeC, PanelKind.DARK, modifier.press(onClick = onClick).graphicsLayer { alpha = if (dimmed) 0.62f else 1f }, padding = 0.dp, highlight = true) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(34.dp).background(if (positive) KassaColors.Lime else KassaColors.Raised), contentAlignment = Alignment.Center) {
                Text(icon, style = KassaType.cjk(13).copy(color = if (positive) KassaColors.Ink else KassaColors.Lime))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = KassaType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(
                    meta,
                    style = KassaType.Small.copy(fontSize = KassaType.Label.fontSize, color = metaColor),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(amount, style = KassaType.RowAmount.copy(color = if (positive) KassaColors.Lime else KassaColors.Text), maxLines = 1, softWrap = false)
            trailing?.invoke()
        }
    }
}

/** Маленькая лаймовая кнопка в строке (.mini): «Оплатил» / «Получил». */
@Composable
fun MiniButton(text: String, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = KassaType.Button.copy(fontSize = 11.sp, color = KassaColors.Ink),
        maxLines = 1, softWrap = false,
        modifier = Modifier.press(onClick = onClick).clip(ShapeChip).background(KassaColors.Lime).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Строка «+ Добавить …» с пунктирной рамкой (.add-row). [mark] — значок слева: «+» или «→» для перехода. */
@Composable
fun AddRow(text: String, modifier: Modifier = Modifier, mark: String = "+", onClick: () -> Unit) {
    Row(
        // фон — тёмное «стекло» (в прототипе 55% с размытием; размытия нет — плотнее)
        modifier.fillMaxWidth().press(onClick = onClick).background(KassaColors.Surface.copy(alpha = 0.85f)).drawBehind {
            val w = 1.dp.toPx()
            drawRect(
                KassaColors.Line, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(mark, style = KassaType.RowAmount.copy(fontSize = 18.sp, color = KassaColors.Lime))
        Text(text, style = KassaType.Small.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Переключатель из двух-трёх вариантов (.seg): «Трата / Доход». */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            val bg by animateColorAsState(if (on) KassaColors.Lime else KassaColors.Raised, tween(Motion.FAST), label = "seg")
            Box(
                Modifier.weight(1f).press { onSelect(v) }.clip(ShapeSeg).background(bg).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = KassaType.Button.copy(fontSize = 13.sp, color = if (on) KassaColors.Ink else KassaColors.Grey))
            }
        }
    }
}

private val ShapeSeg = dev.bessaract.kassa.ui.theme.diagonalCut(8)

/** Пустой список: тусклый иероглиф и подсказка. */
@Composable
fun EmptyRow(text: String, modifier: Modifier = Modifier) {
    Panel(ShapeC, PanelKind.DARK, modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("空", style = KassaType.cjk(20).copy(color = KassaColors.Line))
            Text(text, style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize.times(0.92f)))
        }
    }
}

/** Заголовок списка (.ops-h): иероглиф, подпись капсом и счётчик справа. */
@Composable
fun ListHeader(cjk: String, text: String, right: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(cjk, style = KassaType.cjk(12).copy(color = KassaColors.Text))
        Spacer(Modifier.size(8.dp))
        Text(text.uppercase(), style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null), modifier = Modifier.weight(1f))
        Text(right.uppercase(), style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null))
    }
}

/** Сегментная шкала дней месяца: прошедшие — ink, сегодня — лайм. */
@Composable
fun DaySegments(days: Int, today: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (d in 1..days) {
            val c = when {
                d == today -> KassaColors.Lime
                d < today -> KassaColors.Ink
                else -> KassaColors.Ink.copy(alpha = 0.13f)
            }
            var m = Modifier.weight(1f).height(8.dp).background(c)
            if (d == today) m = m.border(1.dp, KassaColors.Ink)
            Box(m)
        }
    }
}
