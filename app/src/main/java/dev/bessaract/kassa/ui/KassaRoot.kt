package dev.bessaract.kassa.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.Tx
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
import dev.bessaract.kassa.ui.screens.ExpensesScreen
import dev.bessaract.kassa.ui.screens.GoalActions
import dev.bessaract.kassa.ui.screens.GoalSheetContent
import dev.bessaract.kassa.ui.screens.HistoryScreen
import dev.bessaract.kassa.ui.screens.TopGoalSheetContent
import dev.bessaract.kassa.ui.screens.PlanActions
import dev.bessaract.kassa.ui.screens.PlanScreen
import dev.bessaract.kassa.ui.screens.RecSheetContent
import dev.bessaract.kassa.ui.screens.SettingsSheetContent
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
    HOME("Сегодня", "今天"),
    HIST("История", "记录"),
    PLAN("План", "计划"),
    EXP("Расходы", "支出"),
}

/**
 * Что открыто в нижнем листе (строка, чтобы пережить поворот экрана):
 * NEW_TX — новая операция, NEW_REC_EXP / NEW_REC_INC — новая строка плана,
 * REC_PREFIX + id — правка строки плана, иначе id операции для правки.
 */
const val NEW_TX = "new"
const val NEW_REC_EXP = "rec+exp"
const val NEW_REC_INC = "rec+inc"
const val REC_PREFIX = "rec:"
const val SETTINGS = "settings"
const val NEW_GOAL = "goal+"
const val GOAL_PREFIX = "goal:"
const val TOP_PREFIX = "top:"

/** Обработчики действий интерфейса — всё, что меняет данные. */
class KassaActions(
    val onSave: (TxDraft, Tx?) -> Unit = { _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onPostRec: (Recurring) -> Unit = {},
    val onSaveRec: (RecDraft) -> Unit = {},
    val onDeleteRec: (String) -> Unit = {},
    val onSaveGoal: (GoalDraft) -> Unit = {},
    val onDeleteGoal: (String) -> Unit = {},
    val onContribute: (String, Long) -> Unit = { _, _ -> },
    val backupFileName: () -> String = { "kassa.json" },
    val onExport: (Uri) -> Unit = {},
    val onReadBackup: (Uri) -> Unit = {},
    val onApplyBackup: () -> Unit = {},
    val onResetBackup: () -> Unit = {},
)

@Composable
fun KassaRoot(vm: SpendingViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        KassaActions(
            vm::save, vm::delete, vm::postRec, vm::saveRec, vm::deleteRec,
            vm::saveGoal, vm::deleteGoal, vm::contribute,
            vm::backupFileName, vm::exportTo, vm::readBackup, vm::applyBackup, vm::resetBackup,
        )
    }
    KassaContent(state, actions, backup = backup)
}

/** Что показать в листе: операция или строка плана; null — открывать нечего (удалили или ушла в другой месяц). */
private sealed interface SheetTarget {
    data class TxForm(val tx: Tx?) : SheetTarget
    data class RecForm(val rec: Recurring?, val type: CatType) : SheetTarget
    data object Settings : SheetTarget
    data class GoalForm(val goal: Goal?) : SheetTarget
    data class TopGoal(val goal: Goal) : SheetTarget
}

private fun resolveSheet(sheet: String?, s: SpendingState?): SheetTarget? {
    if (sheet == null || s == null) return null
    return when {
        sheet == NEW_TX -> SheetTarget.TxForm(null)
        sheet == SETTINGS -> SheetTarget.Settings
        sheet == NEW_REC_EXP -> SheetTarget.RecForm(null, CatType.EXP)
        sheet == NEW_REC_INC -> SheetTarget.RecForm(null, CatType.INC)
        sheet == NEW_GOAL -> SheetTarget.GoalForm(null)
        sheet.startsWith(GOAL_PREFIX) -> s.goals.find { it.id == sheet.removePrefix(GOAL_PREFIX) }?.let { SheetTarget.GoalForm(it) }
        sheet.startsWith(TOP_PREFIX) -> s.goals.find { it.id == sheet.removePrefix(TOP_PREFIX) }?.let { SheetTarget.TopGoal(it) }
        sheet.startsWith(REC_PREFIX) -> s.rec.find { it.id == sheet.removePrefix(REC_PREFIX) }?.let { SheetTarget.RecForm(it, it.type) }
        else -> s.monthTx.find { it.id == sheet }?.let { SheetTarget.TxForm(it) }
    }
}

/** Весь интерфейс без ViewModel: так его можно отрисовать в тесте со скриншотами. */
@Composable
fun KassaContent(
    s: SpendingState?,
    actions: KassaActions,
    initialTab: Tab = Tab.HOME,
    initialSheet: String? = null,
    backup: BackupUi = BackupUi.Idle,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    val focus = LocalFocusManager.current

    // Операция или строка плана ищется в текущих данных; если её удалили или она ушла в другой месяц — лист закрывается.
    val target = resolveSheet(sheet, s)
    val sheetOpen = target != null
    fun closeSheet() { focus.clearFocus(); sheet = null }
    // Пока лист уезжает (360 мс), его кнопки ещё нажимаются: второе «Записать» не должно создать дубль.
    fun submit(action: () -> Unit) { if (sheet != null) { action(); closeSheet() } }

    BackHandler(enabled = sheetOpen) { closeSheet() }

    // Файл копии выбирает пользователь системным диалогом (Загрузки, Диск…): приложению не нужен доступ к памяти.
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions.onExport)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.onReadBackup)
    }

    val plan = remember(actions) { PlanActions(onEdit = { sheet = REC_PREFIX + it.id }, onPost = actions.onPostRec) }
    val goals = remember { GoalActions(onEdit = { sheet = GOAL_PREFIX + it.id }, onTop = { sheet = TOP_PREFIX + it.id }, onNew = { sheet = NEW_GOAL }) }

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
                    SysLine(s) { actions.onResetBackup(); sheet = SETTINGS }
                    Head(tab)
                    // key: при смене вкладки панели заново «вытираются» по очереди.
                    androidx.compose.runtime.key(tab) {
                        when (tab) {
                            Tab.HOME -> TodayScreen(s, onEdit = { sheet = it.id }, plan = plan, onOpenPlan = { tab = Tab.PLAN }, onOpenHistory = { tab = Tab.HIST })
                            Tab.HIST -> HistoryScreen(s) { sheet = it.id }
                            Tab.PLAN -> PlanScreen(s, plan, goals, onOpenExpenses = { tab = Tab.EXP }, onAddIncome = { sheet = NEW_REC_INC })
                            Tab.EXP -> ExpensesScreen(s, plan) { sheet = NEW_REC_EXP }
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
                val opened = remember { target }
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
                        when (opened) {
                            is SheetTarget.TxForm -> TxSheetContent(
                                s = s,
                                original = opened.tx,
                                onSave = { d -> submit { actions.onSave(d, opened.tx) } },
                                onDelete = { id -> submit { actions.onDelete(id) } },
                                onCancel = { closeSheet() },
                            )
                            is SheetTarget.RecForm -> RecSheetContent(
                                s = s,
                                original = opened.rec,
                                type = opened.type,
                                onSave = { d -> submit { actions.onSaveRec(d) } },
                                onDelete = { id -> submit { actions.onDeleteRec(id) } },
                                onCancel = { closeSheet() },
                            )
                            SheetTarget.Settings -> SettingsSheetContent(
                                s = s,
                                backup = backup,
                                onExport = { createFile.launch(actions.backupFileName()) },
                                // text/plain и octet-stream: на некоторых телефонах .json из Загрузок помечен так
                                onPick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                                onApply = actions.onApplyBackup,
                                onCancel = { closeSheet() },
                            )
                            is SheetTarget.GoalForm -> GoalSheetContent(
                                s = s,
                                original = opened.goal,
                                onSave = { d -> submit { actions.onSaveGoal(d) } },
                                onDelete = { id -> submit { actions.onDeleteGoal(id) } },
                                onCancel = { closeSheet() },
                            )
                            is SheetTarget.TopGoal -> TopGoalSheetContent(
                                goal = opened.goal,
                                onSave = { amt -> submit { actions.onContribute(opened.goal.id, amt) } },
                                onCancel = { closeSheet() },
                            )
                            null -> {}
                        }
                    }
                }
            }
        }
    }
    }
}

/** Служебная строка: KS-01 // дата // день недели, справа — сколько дней до конца месяца и ⚙ «Настройки». */
@Composable
private fun SysLine(s: SpendingState, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp).reveal(0), verticalAlignment = Alignment.CenterVertically) {
        Text("KS-01 // " + sysDate(s.today), style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null, letterSpacing = KassaType.Nav.letterSpacing), modifier = Modifier.weight(1f))
        Text("剩 ${daysLeft(s.today)} ДН", style = KassaType.Label.copy(color = KassaColors.Grey, fontWeight = null))
        // \uFE0E — текстовый вид значка, а не цветной эмодзи
        Text(
            "⚙\uFE0E",
            style = KassaType.Body.copy(color = KassaColors.Grey, fontSize = 15.sp),
            modifier = Modifier.press(onClick = onSettings).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics { contentDescription = "Настройки" },
        )
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
                        .semantics { contentDescription = "Добавить трату или доход" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = KassaType.Body.copy(fontFamily = Tektur, fontSize = 34.sp, lineHeight = 34.sp, color = KassaColors.Ink))
                }
            }
            NavItem(Tab.PLAN, current, onSelect)
            NavItem(Tab.EXP, current, onSelect)
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
        // иероглифы в навигации на 50%, как в прототипе
        Text(t.cjk, style = KassaType.cjk(15).copy(color = color.copy(alpha = 0.5f)))
        Spacer(Modifier.height(3.dp))
        Text(t.label.uppercase(), style = KassaType.Nav.copy(color = color), maxLines = 1)
    }
}
