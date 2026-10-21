package dev.bessaract.kassa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.ui.theme.CutShape
import dev.bessaract.kassa.ui.theme.CutShapeSmall
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType

/** Строчки развёртки поверх всего экрана: тёмная линия 1dp через каждые 3dp (body::after). */
fun Modifier.scanlines(): Modifier = drawWithCache {
    val step = 3.dp.toPx()
    val brush = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.22f),
        1f / 3 to Color.Black.copy(alpha = 0.22f),
        1f / 3 to Color.Transparent,
        1f to Color.Transparent,
        startY = 0f,
        endY = step,
        tileMode = TileMode.Repeated,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush)
    }
}

/** Фон с зелёным свечением сверху. */
fun Modifier.terminalBackground(): Modifier = background(KassaColors.Bg).drawWithContent {
    drawRect(
        Brush.radialGradient(
            colors = listOf(KassaColors.BgGlow, Color.Transparent),
            center = Offset(size.width / 2, 0f),
            radius = size.width * 0.9f,
        ),
    )
    drawContent()
}

/** .card: поверхность, линия, срезанные углы, заголовок «> ТЕКСТ». */
@Composable
fun TerminalCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CutShape)
            .background(KassaColors.Surface)
            .border(1.dp, KassaColors.Line, CutShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text("> " + title.uppercase(), style = KassaType.CardTitle)
        content()
    }
}

/** .hero: главная цифра с неоновым свечением и цветной «расфокусировкой». */
@Composable
fun HeroCard(label: String, value: String, sub: String, over: Boolean = false) {
    val color = if (over) KassaColors.Bad else KassaColors.Accent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CutShape)
            .background(KassaColors.Surface)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.08f), Color.Transparent)))
            .border(1.dp, color, CutShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("[ ${label.uppercase()} ]", style = KassaType.HeroLabel.copy(color = color))
        Box {
            val big = KassaType.HeroBig.copy(color = color)
            Text(value, style = big.copy(color = KassaColors.Bad.copy(alpha = 0.5f), shadow = null), modifier = Modifier.offset(x = (-2).dp))
            Text(value, style = big.copy(color = KassaColors.Cyan.copy(alpha = 0.5f), shadow = null), modifier = Modifier.offset(x = 2.dp))
            Text(value, style = big)
        }
        Text(sub, style = KassaType.Body.copy(fontSize = KassaType.Small.fontSize))
    }
}

/** .stat: небольшая плитка с цифрой и подписью. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = KassaColors.Ink) {
    Column(
        modifier
            .clip(CutShapeSmall)
            .background(KassaColors.Surface)
            .border(1.dp, KassaColors.Line, CutShapeSmall)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = KassaType.Stat.copy(color = valueColor))
        Text(label, style = KassaType.Small)
    }
}
