package dev.bessaract.kassa.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.bessaract.kassa.ui.theme.KassaColors
import kotlinx.coroutines.delay
import kotlin.random.Random

// Эффекты v3: фоновые трассы с бегущим «током» и редкие глитчи (docs/STYLE.md, «Анимации»).

/**
 * Системная настройка «Убрать анимации» (масштаб длительности 0) отключает трассы и глитчи.
 * Перечитывается при каждом возврате в приложение: настройку могли поменять, пока оно было свёрнуто.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var enabled by remember(resolver) { mutableStateOf(read()) }
    LifecycleResumeEffect(resolver) {
        enabled = read()
        onPauseOrDispose { }
    }
    return enabled
}

/**
 * Контурные трассы на фоне (viewBox 370×800, растянуты на экран) и лаймовый «ток» по ним:
 * штрих 60 / пропуск 800, круг 7 с, вторая трасса сдвинута на 3 с — как .traces в прототипе.
 */
@Composable
fun Traces(modifier: Modifier, animate: Boolean) {
    val flow = if (animate) {
        rememberInfiniteTransition(label = "traces").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)), label = "flow",
        )
    } else null
    // Свой слой: «ток» перерисовывает только трассы, а не весь экран.
    Canvas(modifier.graphicsLayer()) {
        val sx = size.width / 370f; val sy = size.height / 800f
        fun path(vararg pts: Float) = Path().apply {
            moveTo(pts[0] * sx, pts[1] * sy)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * sx, pts[i + 1] * sy); i += 2 }
        }
        val p1 = path(370f, 150f, 300f, 150f, 270f, 180f, 270f, 330f, 240f, 360f, 190f, 360f)
        val p2 = path(0f, 540f, 40f, 540f, 70f, 570f, 70f, 690f, 100f, 720f, 170f, 720f)
        val thin = Stroke(1.dp.toPx())
        drawPath(p1, KassaColors.Line, style = thin)
        drawPath(p2, KassaColors.Line, style = thin)
        if (flow != null) {
            val k = (sx + sy) / 2
            val period = 860f * k
            fun hot(p: Path, t: Float) = drawPath(
                p, KassaColors.Lime,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f * k, 800f * k), period * (1f - t))),
            )
            hot(p1, flow.value)
            hot(p2, (flow.value + 3f / 7f) % 1f)
        }
    }
}

enum class GlitchTarget { TITLE, HERO }

/** Текущий глитч: что «дёргается», шаг 0–4 (по 45 мс) и положение полосы через экран (null — без полосы). */
@Stable
class GlitchState {
    var target by mutableStateOf<GlitchTarget?>(null)
    var step by mutableIntStateOf(0)
    var tearY by mutableStateOf<Float?>(null)
    var tearStep by mutableIntStateOf(0)

    /** Главная цифра есть только на «Сегодня»; на других вкладках глитчит заголовок. */
    var heroOnScreen by mutableStateOf(false)

    suspend fun fire(target: GlitchTarget, tear: Boolean) {
        try {
            if (tear) tearY = Random.nextFloat() * 0.8f + 0.1f
            this.target = target
            for (s in 1..4) { step = s; tearStep = s.coerceAtMost(3); delay(45) }
        } finally {
            // и при отмене посреди глитча копии и полоса не должны остаться на экране
            this.target = null; step = 0; tearY = null; tearStep = 0
        }
    }
}

private val NoGlitch = GlitchState()

val LocalGlitch = staticCompositionLocalOf { NoGlitch }

/**
 * Раз в 15–40 с — глитч заголовка или главной цифры на 180 мс, в 40% случаев ещё и полоса через экран.
 * Никогда при открытом листе ввода (paused).
 */
@Composable
fun GlitchLoop(state: GlitchState, enabled: Boolean, paused: Boolean) {
    val isPaused by rememberUpdatedState(paused)
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(15_000, 40_000))
            if (isPaused) continue
            val target = if (state.heroOnScreen && Random.nextBoolean()) GlitchTarget.HERO else GlitchTarget.TITLE
            state.fire(target, tear = Random.nextFloat() < 0.4f)
        }
    }
}

/** Сдвиги по шагам глитча (dp): как @keyframes gl в прототипе. */
private val jitter = listOf(0f to 0f, -2f to 1f, 2f to -1f, -1f to 0f, 0f to 0f)

/**
 * Текст, который умеет «глитчить»: сдвиг и цветное расщепление (красная копия сверху, голубая снизу).
 * [text] рисуется функцией [draw], чтобы подходил и обычный Text, и подсчёт цифр.
 */
@Composable
fun Glitchable(target: GlitchTarget, modifier: Modifier = Modifier, draw: @Composable (Modifier, TextStyle?) -> Unit) {
    val g = LocalGlitch.current
    val active = g.target == target
    val d = LocalDensity.current
    val (jx, jy) = jitter[if (active) g.step else 0]
    Box(modifier.graphicsLayer { translationX = with(d) { jx.dp.toPx() }; translationY = with(d) { jy.dp.toPx() } }) {
        draw(Modifier, null)
        if (active) {
            draw(
                Modifier.offset(x = (-3).dp).drawWithContent {
                    clipRect(top = size.height * 0.10f, bottom = size.height * 0.45f) { this@drawWithContent.drawContent() }
                },
                TextStyle(color = KassaColors.Bad.copy(alpha = 0.85f)),
            )
            draw(
                Modifier.offset(x = 3.dp).drawWithContent {
                    clipRect(top = size.height * 0.60f, bottom = size.height * 0.92f) { this@drawWithContent.drawContent() }
                },
                TextStyle(color = GlitchCyan.copy(alpha = 0.85f)),
            )
        }
    }
}

private val GlitchCyan = Color(0xFF00E5FF)

/** Полоса через весь экран (14 dp) на время глитча, дёргается вбок в 3 шага. */
@Composable
fun GlitchTear(modifier: Modifier) {
    val g = LocalGlitch.current
    val y = g.tearY ?: return
    val shift = listOf(0f, -12f, 10f, 0f)[g.tearStep]
    Canvas(modifier) {
        val h = 14.dp.toPx()
        val top = size.height * y
        val dx = shift.dp.toPx()
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to KassaColors.Lime.copy(alpha = 0.2f),
                0.5f to Color.White.copy(alpha = 0.13f),
                0.8f to KassaColors.Bad.copy(alpha = 0.2f),
                1f to Color.Transparent,
                startX = dx, endX = size.width + dx,
            ),
            topLeft = Offset(dx, top), size = Size(size.width, h),
        )
    }
}

/** Обычный текст с глитчем: стиль [style], при расщеплении — только цвет копии. */
@Composable
fun GlitchText(text: String, style: TextStyle, target: GlitchTarget, modifier: Modifier = Modifier) {
    Glitchable(target, modifier) { m, over ->
        // Длинный заголовок при крупном системном шрифте уменьшается, а не обрезается.
        BasicText(
            text, style = if (over != null) style.merge(over) else style, modifier = m, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.5f, maxFontSize = style.fontSize),
        )
    }
}
