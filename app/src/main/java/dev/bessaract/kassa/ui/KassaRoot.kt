package dev.bessaract.kassa.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bessaract.kassa.ui.components.scanlines
import dev.bessaract.kassa.ui.components.terminalBackground
import dev.bessaract.kassa.ui.screens.PlaceholderScreen
import dev.bessaract.kassa.ui.screens.TodayScreen
import dev.bessaract.kassa.ui.theme.CutShapeSmall
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.Tektur
import dev.bessaract.kassa.ui.theme.glow

/** Экраны приложения. Первые пять — во вкладках снизу, настройки открываются шестерёнкой (как в kassa.html). */
enum class Tab(val label: String, val title: String) {
    HOME("Сегодня", "Сегодня"),
    HIST("История", "История"),
    PLAN("План", "План на месяц"),
    DEBT("Долги", "Долги и цели"),
    REP("Отчёт", "Отчёт"),
    SET("Настройки", "Настройки"),
}

private val bottomTabs = Tab.entries.filter { it != Tab.SET }

@Composable
fun KassaRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var back by rememberSaveable { mutableStateOf(Tab.HOME) }
    val context = LocalContext.current

    BackHandler(enabled = tab == Tab.SET) { tab = back }

    Box(Modifier.fillMaxSize().terminalBackground().scanlines()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Column(
                Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(tab.title, onSettings = { if (tab != Tab.SET) { back = tab; tab = Tab.SET } })
                when (tab) {
                    Tab.HOME -> TodayScreen()
                    else -> PlaceholderScreen(tab)
                }
            }
            BottomNav(current = tab, onSelect = { tab = it; back = it })
        }
        Fab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(end = 16.dp, bottom = 62.dp),
            onClick = { Toast.makeText(context, "Быстрый ввод — на шаге 3", Toast.LENGTH_SHORT).show() },
        )
    }
}

/** .top: «// ЗАГОЛОВОК» и кнопка настроек. */
@Composable
private fun TopBar(title: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = KassaColors.Cyan, shadow = null)) { append("// ") }
                append(title.uppercase())
            },
            style = KassaType.ScreenTitle,
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(CutShapeSmall)
                .background(KassaColors.Surface)
                .border(1.dp, KassaColors.Line, CutShapeSmall)
                .clickable(onClick = onSettings)
                .semantics { contentDescription = "Настройки" },
            contentAlignment = Alignment.Center,
        ) { SlidersIcon() }
    }
}

/** Иконка «ползунки» из kassa.html (viewBox 24×24). */
@Composable
private fun SlidersIcon() {
    Canvas(Modifier.size(20.dp)) {
        val u = size.width / 24f
        val stroke = 2 * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(KassaColors.Ink, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke, StrokeCap.Round)
        line(4f, 7f, 14f, 7f); line(18f, 7f, 20f, 7f); line(4f, 17f, 6f, 17f); line(10f, 17f, 20f, 17f)
        drawCircle(KassaColors.Ink, 2 * u, Offset(16 * u, 7 * u), style = Stroke(stroke))
        drawCircle(KassaColors.Ink, 2 * u, Offset(8 * u, 17 * u), style = Stroke(stroke))
    }
}

/** nav: пять вкладок, жёлтая линия сверху, активная — со свечением. */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(KassaColors.Bg)
            .drawBehind { drawLine(KassaColors.Accent, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        bottomTabs.forEach { t ->
            val active = t == current
            Text(
                t.label.uppercase(),
                style = KassaType.Nav.copy(
                    color = if (active) KassaColors.Accent else KassaColors.Muted,
                    shadow = if (active) glow(KassaColors.Accent.copy(alpha = 0.6f), 10f) else null,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(t) }
                    .padding(vertical = 12.dp, horizontal = 2.dp),
            )
        }
    }
}

/** #fab: жёлтая кнопка «+». */
@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(60.dp)
            .clip(CutShapeSmall)
            .background(KassaColors.Accent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Добавить операцию" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = KassaType.Body.copy(fontFamily = Tektur, fontSize = 32.sp, lineHeight = 32.sp, color = KassaColors.OnAccent))
    }
}
