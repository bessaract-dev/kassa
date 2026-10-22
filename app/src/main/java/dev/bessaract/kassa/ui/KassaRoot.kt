package dev.bessaract.kassa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bessaract.kassa.core.calc.daysLeft
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.ui.components.Arrows
import dev.bessaract.kassa.ui.components.GlitchLoop
import dev.bessaract.kassa.ui.components.GlitchState
import dev.bessaract.kassa.ui.components.GlitchTarget
import dev.bessaract.kassa.ui.components.GlitchTear
import dev.bessaract.kassa.ui.components.GlitchText
import dev.bessaract.kassa.ui.components.LocalGlitch
import dev.bessaract.kassa.ui.components.Traces
import dev.bessaract.kassa.ui.components.rememberMotionEnabled
import dev.bessaract.kassa.ui.components.gridBackground
import dev.bessaract.kassa.ui.components.press
import dev.bessaract.kassa.ui.components.reveal
import dev.bessaract.kassa.ui.screens.HistoryScreen
import dev.bessaract.kassa.ui.screens.PlaceholderScreen
import dev.bessaract.kassa.ui.screens.TodayScreen
import dev.bessaract.kassa.ui.screens.TxSheetContent
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.Motion
import dev.bessaract.kassa.ui.theme.SHEET_NOTCH_DP
import dev.bessaract.kassa.ui.theme.ShapeFab
import dev.bessaract.kassa.ui.theme.ShapeSheet
import dev.bessaract.kassa.ui.theme.Tektur

/** Вкладки нижней навигации, как в prototype/style.html: две слева от «+», две справа. */
enum class Tab(val label: String, val cjk: String) {
    HOME("Сегодня", "今日"),
    HIST("История", "履歴"),
    PLAN("План", "計画"),
    DEBT("Долги", "負債"),
}

/** Что открыто в нижнем листе: NEW_TX — новая трата, иначе id операции для правки. */
const val NEW_TX = "new"

@Composable
fun KassaRoot(vm: SpendingViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    KassaContent(state, onSave = vm::save, onDelete = vm::delete)
}

/** Весь интерфейс без ViewModel: так его можно отрисовать в тесте со скриншотами. */
@Composable
fun KassaContent(
    s: SpendingState?,
    onSave: (TxDraft, Tx?) -> Unit,
    onDelete: (String) -> Unit,
    initialTab: Tab = Tab.HOME,
    initialSheet: String? = null,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    val focus = LocalFocusManager.current

    // Операция для правки ищется в текущем месяце; если её удалили или она ушла в другой месяц — лист закрывается.
    val editing: Tx? = sheet?.takeIf { it != NEW_TX }?.let { id -> s?.spending?.byDay?.flatMap { it.tx }?.find { it.id == id } }
    val sheetOpen = sheet != null && s != null && (sheet == NEW_TX || editing != null)
    fun closeSheet() { focus.clearFocus(); sheet = null }

    BackHandler(enabled = sheetOpen) { closeSheet() }

    val motion = rememberMotionEnabled()
    val glitch = remember { GlitchState() }
    GlitchLoop(glitch, enabled = motion, paused = sheetOpen)

    CompositionLocalProvider(LocalGlitch provides glitch) {
    Box(Modifier.fillMaxSize().gridBackground()) {
        Traces(Modifier.fillMaxSize(), animate = motion)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Column(
                Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (s != null) {
                    SysLine(s)
                    Head(tab)
                    // key: при смене вкладки панели заново «вытираются» по очереди.
                    androidx.compose.runtime.key(tab) {
                        when (tab) {
                            Tab.HOME -> TodayScreen(s) { sheet = it.id }
                            Tab.HIST -> HistoryScreen(s) { sheet = it.id }
                            Tab.PLAN -> PlaceholderScreen("Постоянные доходы и расходы, лимит на день. Появится в следующих версиях.")
                            Tab.DEBT -> PlaceholderScreen("Кредиты с прогнозом закрытия и цели накоплений. Появится в следующих версиях.")
                        }
                    }
                }
            }
            BottomNav(tab, onSelect = { tab = it }, onAdd = { if (s != null) sheet = NEW_TX })
        }

        // Полоса глитча — поверх экрана, но под листом ввода
        GlitchTear(Modifier.fillMaxSize())

        // Затемнение под листом
        AnimatedVisibility(
            visible = sheetOpen,
            enter = fadeIn(tween(Motion.SLOW, easing = Motion.EaseOut)),
            exit = fadeOut(tween(Motion.exit(Motion.SLOW), easing = Motion.EaseIn)),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.67f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { closeSheet() },
            )
        }

        // Нижний лист: выезжает за 360 мс, затем вдоль верхней грани пробегает лаймовая линия.
        AnimatedVisibility(
            visible = sheetOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(Motion.SLOW, easing = Motion.EaseOut)) { it },
            exit = slideOutVertically(tween(Motion.exit(Motion.SLOW), easing = Motion.EaseIn)) { it },
        ) {
            if (s != null) {
                // Запоминаем, что открыли: пока лист уезжает, sheet уже null, и форма не должна превратиться в «новую».
                val opened = remember { editing }
                val line = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(Motion.SLOW + 120L)
                    line.animateTo(1f, tween(Motion.SLOW, easing = Motion.EaseOut))
                }
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp).imePadding()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(ShapeSheet)
                            .background(KassaColors.Surface)
                            .drawWithContent {
                                drawContent()
                                val y = SHEET_NOTCH_DP.dp.toPx()
                                drawRect(KassaColors.Lime, Offset(0f, y), Size(size.width * line.value, 2.dp.toPx()))
                            }
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 22.dp + SHEET_NOTCH_DP.dp, bottom = 22.dp),
                    ) {
                        TxSheetContent(
                            s = s,
                            original = opened,
                            onSave = { d -> onSave(d, opened); closeSheet() },
                            onDelete = { id -> onDelete(id); closeSheet() },
                            onCancel = { closeSheet() },
                        )
                    }
                }
            }
        }
    }
    }
}

/** Служебная строка: KS-01 // дата // день недели, справа — сколько дней до конца месяца. */
@Composable
private fun SysLine(s: SpendingState) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp, end = 4.dp).reveal(0), verticalAlignment = Alignment.CenterVertically) {
        Text("KS-01 // " + sysDate(s.today), style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null, letterSpacing = KassaType.Nav.letterSpacing), modifier = Modifier.weight(1f))
        Text("残 ${daysLeft(s.today)} ДН ", style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null))
        Arrows(2, KassaColors.Grey)
    }
}

/** Шапка экрана: заголовок капсом, индекс экранов 01–04, вертикальный ярлык из двух иероглифов. */
@Composable
private fun Head(tab: Tab) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).reveal(1), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            GlitchText(tab.label.uppercase(), KassaType.ScreenTitle, GlitchTarget.TITLE)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Text(
                        "%02d".format(t.ordinal + 1),
                        style = KassaType.Label.copy(color = if (on) KassaColors.Lime else KassaColors.Grey, fontWeight = null),
                        modifier = if (on) Modifier.drawBehind {
                            drawRect(KassaColors.Lime, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
                        } else Modifier,
                    )
                }
            }
        }
        Row(
            Modifier.drawBehind { drawRect(KassaColors.Lime, Offset.Zero, Size(2.dp.toPx(), size.height)) }.padding(start = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tab.cjk.forEach { Text(it.toString(), style = KassaType.cjk(22).copy(color = KassaColors.Text)) }
            }
        }
    }
}

/** Навигация: поверхность, линия сверху, активная вкладка — лайм с полосой; «+» по центру приподнят. */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(KassaColors.Surface)
            .drawBehind { drawLine(KassaColors.Line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(78.dp).padding(start = 6.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(Tab.HOME, current, onSelect)
            NavItem(Tab.HIST, current, onSelect)
            Box(Modifier.width(82.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-18).dp)
                        .size(62.dp)
                        .press(onClick = onAdd)
                        .clip(ShapeFab)
                        .background(KassaColors.Lime)
                        .semantics { contentDescription = "Добавить трату" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = KassaType.Body.copy(fontFamily = Tektur, fontSize = 34.sp, lineHeight = 34.sp, color = KassaColors.Ink))
                }
            }
            NavItem(Tab.PLAN, current, onSelect)
            NavItem(Tab.DEBT, current, onSelect)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(t: Tab, current: Tab, onSelect: (Tab) -> Unit) {
    val on = t == current
    val color = if (on) KassaColors.Lime else KassaColors.Grey
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .press { onSelect(t) }
            .drawBehind {
                if (on) drawRect(KassaColors.Lime, Offset(size.width * 0.22f, 0f), Size(size.width * 0.56f, 3.dp.toPx()))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(t.cjk, style = KassaType.cjk(15).copy(color = color))
        Spacer(Modifier.height(3.dp))
        Text(t.label.uppercase(), style = KassaType.Nav.copy(color = color), maxLines = 1)
    }
}
