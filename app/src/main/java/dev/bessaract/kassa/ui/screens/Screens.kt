package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.bessaract.kassa.core.calc.goalPlan
import dev.bessaract.kassa.core.calc.loanForecast
import dev.bessaract.kassa.core.calc.loanPlan
import dev.bessaract.kassa.core.model.Loan
import dev.bessaract.kassa.ui.loanForecastText
import dev.bessaract.kassa.ui.rateText
import dev.bessaract.kassa.core.calc.recPayment
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.ui.components.Segmented
import dev.bessaract.kassa.ui.components.press
import dev.bessaract.kassa.ui.shortDate
import dev.bessaract.kassa.ui.theme.ShapeC
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.calc.RecStatus
import dev.bessaract.kassa.core.calc.byDay
import dev.bessaract.kassa.core.calc.recStatus
import dev.bessaract.kassa.core.formatRub
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.catIcon
import dev.bessaract.kassa.ui.components.AddRow
import dev.bessaract.kassa.ui.components.Arrows
import dev.bessaract.kassa.ui.components.DaySegments
import dev.bessaract.kassa.ui.components.EmptyRow
import dev.bessaract.kassa.ui.components.GlitchTarget
import dev.bessaract.kassa.ui.components.Hatch
import dev.bessaract.kassa.ui.components.LocalGlitch
import dev.bessaract.kassa.ui.components.ListHeader
import dev.bessaract.kassa.ui.components.MiniButton
import dev.bessaract.kassa.ui.components.OpRow
import dev.bessaract.kassa.ui.components.Panel
import dev.bessaract.kassa.ui.components.PanelKind
import dev.bessaract.kassa.ui.components.PanelLabel
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.components.ScrambleText
import dev.bessaract.kassa.ui.components.reveal
import dev.bessaract.kassa.ui.dayTitle
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.ShapeA
import dev.bessaract.kassa.ui.theme.ShapeB
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Действия со строкой плана: открыть на правку и «Оплатил» / «Получил». */
class PlanActions(val onEdit: (Recurring) -> Unit, val onPost: (Recurring) -> Unit)

/** Действия с кредитами и долгами. */
class LoanActions(val onEdit: (Loan) -> Unit, val onPay: (Loan) -> Unit, val onNew: () -> Unit)

/**
 * «Сегодня». Пока доходов нет — «Потрачено сегодня» и подсказка открыть План.
 * Когда есть — «Осталось на сегодня» (красное при перерасходе), «Свободно до конца месяца»
 * и строки плана, которые ещё ждут проведения. Формулы — budget() из kassa.html.
 */
@Composable
fun TodayScreen(
    s: SpendingState, onEdit: (Tx) -> Unit, plan: PlanActions, onOpenPlan: () -> Unit, onOpenHistory: () -> Unit = {},
    loans: LoanActions = LoanActions({}, {}, {}),
) {
    val b = s.budget
    val sp = s.spending
    // Главная цифра есть только на этом экране — пока он открыт, глитч может выбрать её.
    val glitch = LocalGlitch.current
    DisposableEffect(glitch) {
        glitch.heroOnScreen = true
        onDispose { glitch.heroOnScreen = false }
    }

    if (b.noIncome) {
        Panel(ShapeA, PanelKind.LIME, Modifier.reveal(2), onClick = onOpenHistory) {
            Spacer(Modifier.height(8.dp))
            PanelLabel("Потрачено сегодня", "支出", KassaColors.Ink)
            HeroAmount(sp.today)
            Text("операций сегодня: ${sp.todayTx.size}", style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)))
            HeroDecor()
        }
        Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(3), onClick = onOpenPlan) {
            PanelLabel("Лимит на день", "限额", KassaColors.Ink)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("计划", style = KassaType.cjk(22).copy(color = KassaColors.Ink), modifier = Modifier.padding(end = 12.dp))
                Text(
                    "Добавь доходы и обязательные платежи во вкладке «План» — тогда здесь будет видно, сколько можно тратить в день.",
                    style = KassaType.Body.copy(color = KassaColors.Ink),
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Открыть план", onClick = onOpenPlan)
            Spacer(Modifier.height(6.dp))
        }
    } else {
        val over = b.leftToday < 0
        Panel(ShapeA, if (over) PanelKind.BAD else PanelKind.LIME, Modifier.reveal(2), onClick = onOpenHistory) {
            Spacer(Modifier.height(8.dp))
            PanelLabel(if (over) "Перерасход за сегодня" else "Осталось на сегодня", if (over) "超支" else "余额", KassaColors.Ink)
            HeroAmount(b.leftToday)
            Text(
                "лимит ${formatRub(b.dayLimit, symbol = false)} · потрачено ${formatRub(b.spentToday, symbol = false)}",
                style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)),
            )
            HeroDecor()
        }
        Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(3), onClick = onOpenPlan) {
            PanelLabel("Свободно до конца месяца", "月底", KassaColors.Ink)
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                ScrambleText(formatRub(b.free, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink), Modifier.weight(1f, fill = false))
                Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
            }
            DaySegments(b.days, s.today.dayOfMonth)
            Text(
                "дней осталось: ${b.left} из ${b.days} · обязательные ${formatRub(b.fixed, symbol = false)}",
                style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
        }
        // ждут оплаты: строки плана и платежи по кредитам, по числам
        val dueRec = s.rec.filter { recStatus(it, s.monthTx, s.today) !is RecStatus.Done }.map { it.day to (it as Any) }
        val dueLoans = s.loans.filter { loanPlan(it, s.monthTx, s.today).reserve > 0 }.map { it.day to (it as Any) }
        val due = (dueRec + dueLoans).sortedBy { it.first }.map { it.second }
        if (due.isNotEmpty()) {
            ListHeader("待付", "Ждут в этом месяце", "%02d шт".format(due.size), Modifier.reveal(4))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                due.forEachIndexed { i, x ->
                    when (x) {
                        is Recurring -> key(x.id) { RecRow(x, s, Modifier.reveal(5 + i), plan) }
                        is Loan -> key(x.id) { LoanRow(x, s, Modifier.reveal(5 + i), loans) }
                    }
                }
            }
        }
    }

    ListHeader("记录", "Операции сегодня", "%02d шт".format(sp.todayTx.size), Modifier.reveal(5))
    if (sp.todayTx.isEmpty()) {
        EmptyRow("Пока пусто. Нажми «+», чтобы записать трату.", Modifier.reveal(6))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sp.todayTx.forEachIndexed { i, t -> key(t.id) { TxRow(t, s, Modifier.reveal(6 + i), onEdit) } }
        }
    }
}

/** Главная цифра на лаймовой (или красной) панели, с глитчем. */
@Composable
private fun HeroAmount(kopecks: Long) {
    Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        ScrambleText(formatRub(kopecks, symbol = false), KassaType.Big.copy(color = KassaColors.Ink), Modifier.weight(1f, fill = false), glitch = GlitchTarget.HERO)
        Text("₽", style = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.5f, color = KassaColors.Ink), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}

@Composable
private fun HeroDecor() {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Hatch(KassaColors.Ink)
        Arrows(3, KassaColors.Ink)
        Spacer(Modifier.weight(1f))
        Text("SN 48-1508F", style = KassaType.Label.copy(fontSize = KassaType.Label.fontSize * 0.9f, color = KassaColors.Ink.copy(alpha = 0.6f)))
    }
}

/** «История»: операции текущего месяца по дням; сверху — расходы и доходы месяца. */
@Composable
fun HistoryScreen(s: SpendingState, onEdit: (Tx) -> Unit) {
    val groups = s.spending.byDay
    val b = s.budget
    // плашка: остаток (= «Свободно до конца месяца») и из чего он складывается
    val due = s.rec.filter { it.type == CatType.EXP && recStatus(it, s.monthTx, s.today) !is RecStatus.Done }.sumOf { it.amt } + b.loansReserve
    Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(2)) {
        PanelLabel("Остаток на месяц", "本月", KassaColors.Ink)
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(b.free, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink), Modifier.weight(1f, fill = false))
            Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
        }
        Text("свободно до конца месяца", style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)))
        Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
            SplitRow("Доходы", "+" + formatRub(b.planInc + b.extraInc, symbol = false))
            SplitRow("Потрачено", "−" + formatRub(s.spending.month, symbol = false))
            SplitRow("Предстоит: обязательные", "−" + formatRub(due, symbol = false))
            if (b.goalsReserve > 0) SplitRow("Предстоит: на цели", "−" + formatRub(b.goalsReserve, symbol = false))
        }
    }
    if (groups.isEmpty()) {
        EmptyRow("В этом месяце операций пока нет.", Modifier.reveal(3))
        return
    }
    var i = 3
    groups.forEach { g ->
        key(g.date) {
            ListHeader("日", dayTitle(g.date), if (g.total > 0) formatRub(-g.total, symbol = false) else "", Modifier.reveal(i++))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.tx.forEach { t -> key(t.id) { TxRow(t, s, Modifier.reveal(i++), onEdit) } }
            }
        }
    }
}

/** Что показывать в «Плане»: доходы, расходы или всё вместе по числам. */
enum class PlanFilter(val label: String) { INC("Доходы"), EXP("Расходы"), ALL("Все") }

/** Действия с целями в «Плане». */
class GoalActions(val onEdit: (Goal) -> Unit, val onTop: (Goal) -> Unit, val onNew: () -> Unit)

/**
 * «План»: плашка «Остаток на месяц», переключатель Доходы / Расходы / Все и цели накоплений.
 * «Оплатил» / «Получил» записывает операцию на сегодня со ссылкой на строку плана, «Отложить» — взнос в цель.
 */
@Composable
fun PlanScreen(s: SpendingState, plan: PlanActions, goals: GoalActions, onOpenExpenses: () -> Unit, onAddIncome: () -> Unit) {
    val b = s.budget
    val inc = s.rec.filter { it.type == CatType.INC }.byDay()
    val exp = s.rec.filter { it.type == CatType.EXP }.byDay()
    // платежи месяца целиком (с уже внесённым), чтобы остаток не менялся от отметок «Оплатил»
    val activeLoans = s.loans.filter { it.balance > 0 && it.pay > 0 }
    val recExp = exp.sumOf { it.amt } + s.loans.sumOf { loanPlan(it, s.monthTx, s.today).plan }
    val rest = b.planInc - recExp - b.goalsReserve
    val paid = exp.count { recStatus(it, s.monthTx, s.today) is RecStatus.Done }
    var filter by rememberSaveable { mutableStateOf(PlanFilter.ALL) }
    Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(2)) {
        PanelLabel("Остаток на месяц", "结余", KassaColors.Ink)
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(rest, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink), Modifier.weight(1f, fill = false))
            Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
        }
        Text(
            if (rest < 0) "не хватает: уменьши цель или сдвинь дату" else "столько остаётся на ежедневные траты",
            style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)),
        )
        Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
            SplitRow("Доходы", "+" + formatRub(b.planInc, symbol = false))
            // платежи плана и кредиты, без целей
            SplitRow("Обязательные платежи", "−" + formatRub(recExp, symbol = false))
            if (b.goalsReserve > 0) SplitRow("На цели", "−" + formatRub(b.goalsReserve, symbol = false))
        }
    }
    Segmented(PlanFilter.entries.map { it to it.label }, filter, Modifier.reveal(3)) { filter = it }
    var i = 4
    val addInc = "Добавить доход (зарплата, аванс…)"
    when (filter) {
        PlanFilter.INC -> ListHeader("收入", "Постоянные доходы", "+" + formatRub(b.planInc, symbol = false), Modifier.reveal(i++))
        PlanFilter.EXP -> ListHeader("付款", "Обязательные платежи", "$paid/${exp.size} оплачено", Modifier.reveal(i++))
        PlanFilter.ALL -> ListHeader(
            "日历", "По числам",
            "+${formatRub(b.planInc, symbol = false)} / −${formatRub(recExp, symbol = false)}", Modifier.reveal(i++),
        )
    }
    val recRows = when (filter) {
        PlanFilter.INC -> inc
        PlanFilter.EXP -> exp
        PlanFilter.ALL -> s.rec
    }
    // кредиты с платежом — вместе с обязательными, по числам
    val rows = (recRows.map { it.day to (it as Any) } + (if (filter == PlanFilter.INC) emptyList() else activeLoans.map { it.day to (it as Any) }))
        .sortedBy { it.first }.map { it.second }
    val noLoanActions = LoanActions({ onOpenExpenses() }, {}, {})
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // обязательные платежи здесь только для обзора: добавляются, правятся и оплачиваются во вкладке «Расходы»
        rows.forEach { x ->
            when (x) {
                is Recurring -> key(x.id) {
                    if (x.type == CatType.INC) RecRow(x, s, Modifier.reveal(i++), plan)
                    else RecRow(x, s, Modifier.reveal(i++), plan, withButton = false, onClick = onOpenExpenses)
                }
                is Loan -> key(x.id) { LoanRow(x, s, Modifier.reveal(i++), noLoanActions, withButton = false) }
            }
        }
        if (filter != PlanFilter.EXP) AddRow(addInc, Modifier.reveal(i++), onClick = onAddIncome)
        if (filter != PlanFilter.INC) AddRow("Платежи меняются во вкладке «Расходы»", Modifier.reveal(i++), mark = "→", onClick = onOpenExpenses)
    }
    val active = s.goals.count { it.saved < it.target }
    ListHeader("目标", "Цели", if (s.goals.isEmpty()) "" else "$active в работе", Modifier.reveal(i++))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.goals.forEach { g -> key(g.id) { GoalRow(g, s, Modifier.reveal(i++), goals) } }
        AddRow("Добавить цель (накопить 100к, поездка…)", Modifier.reveal(i++), onClick = goals.onNew)
    }
    Text(
        "«Получил» записывает доход на сегодня с суммой из плана. «Отложить» добавляет взнос в цель. Ошибся — удали операцию в Истории, отметка снимется.",
        style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp).reveal(i),
    )
}

/** Строка раскладки на бумажной плашке: подпись слева, сумма справа, линия сверху. */
@Composable
private fun SplitRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(KassaColors.Ink.copy(alpha = 0.14f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }.padding(vertical = 5.dp),
    ) {
        Text(label, style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.7f)), modifier = Modifier.weight(1f))
        Text(value, style = KassaType.Small.copy(color = KassaColors.Ink))
    }
}

/** Цель: накоплено из суммы, процент, срок и взнос этого месяца, полоса прогресса и «Отложить». */
@Composable
private fun GoalRow(g: Goal, s: SpendingState, modifier: Modifier, goals: GoalActions) {
    val done = g.saved >= g.target
    val p = if (g.target > 0) (g.saved.toDouble() / g.target).coerceIn(0.0, 1.0) else 0.0
    val gp = goalPlan(g, s.monthTx, s.today)
    val by = g.by.takeIf { it.isNotEmpty() }?.let { runCatching { shortDate(LocalDate.parse(it)) }.getOrNull() }
    val meta = when {
        done -> "✓ цель достигнута"
        by != null -> "${formatRub(g.saved, symbol = false)} из ${formatRub(g.target, symbol = false)} · ${(p * 100).toInt()}%\n" +
            "к $by · за месяц ${formatRub(gp.month, symbol = false)} из ${formatRub(gp.plan, symbol = false)}"
        else -> "${formatRub(g.saved, symbol = false)} из ${formatRub(g.target, symbol = false)} · ${(p * 100).toInt()}%" +
            if (gp.month > 0) " · в этом месяце +${formatRub(gp.month, symbol = false)}" else ""
    }
    Panel(ShapeC, PanelKind.DARK, modifier.press { goals.onEdit(g) }, padding = 0.dp, highlight = true) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(34.dp).background(KassaColors.Raised), contentAlignment = Alignment.Center) {
                Text("存", style = KassaType.cjk(13).copy(color = KassaColors.Lime))
            }
            Column(Modifier.weight(1f)) {
                Text(g.name, style = KassaType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    meta,
                    style = KassaType.Small.copy(fontSize = KassaType.Label.fontSize, color = if (done) KassaColors.Lime else KassaColors.Grey),
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(KassaColors.Raised)) {
                    Box(Modifier.fillMaxWidth(p.toFloat()).height(4.dp).background(KassaColors.Lime))
                }
            }
            if (!done) MiniButton("Отложить") { goals.onTop(g) }
        }
    }
}

/** Строка плана: статус за этот месяц и кнопка «Оплатил» / «Получил», пока не проведена. */
@Composable
private fun RecRow(
    r: Recurring, s: SpendingState, modifier: Modifier, plan: PlanActions,
    withButton: Boolean = true, onClick: (() -> Unit)? = null,
) {
    val inc = r.type == CatType.INC
    val st = recStatus(r, s.monthTx, s.today)
    val (meta, color) = when (st) {
        is RecStatus.Done -> "✓ ${if (inc) "получено" else "оплачено"} ${st.day}-го".uppercase() to KassaColors.Lime
        is RecStatus.Late -> "срок был ${st.day}-го".uppercase() to KassaColors.Warn
        is RecStatus.Waiting -> "ждёт ${st.day}-го".uppercase() to KassaColors.Grey
    }
    OpRow(
        icon = catIcon(s.allCats[r.cat]),
        title = r.name,
        meta = meta,
        metaColor = color,
        amount = (if (inc) "+" else "−") + formatRub(r.amt, symbol = false),
        positive = inc,
        dimmed = st is RecStatus.Done,
        trailing = if (st is RecStatus.Done || !withButton) null else { { MiniButton(if (inc) "Получил" else "Оплатил") { plan.onPost(r) } } },
        modifier = modifier,
        onClick = onClick ?: { plan.onEdit(r) },
    )
}

/**
 * «Расходы»: обязательные платежи месяца — сколько всего, внесено и предстоит; добавить, поправить, «Оплатил».
 * Ниже кредиты и долги: остаток, прогноз, «Внести» / «Вернуть».
 */
@Composable
fun ExpensesScreen(s: SpendingState, plan: PlanActions, loans: LoanActions = LoanActions({}, {}, {}), onAdd: () -> Unit) {
    val exp = s.rec.filter { it.type == CatType.EXP }.byDay()
    val paid = exp.mapNotNull { r -> recPayment(r.id, s.monthTx, s.today) }
    val due = exp.filter { recPayment(it.id, s.monthTx, s.today) == null }
    val lp = s.loans.map { loanPlan(it, s.monthTx, s.today) }
    val debt = s.loans.sumOf { maxOf(0L, it.balance) }
    Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(2)) {
        PanelLabel("Обязательные в месяц", "付款", KassaColors.Ink)
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(exp.sumOf { it.amt } + lp.sumOf { it.plan }, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink), Modifier.weight(1f, fill = false))
            Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
        }
        Text("платежи и кредиты — каждый месяц", style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)))
        Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
            // по кредитам — в пределах платежа месяца, чтобы «Внесено» + «Предстоит» = итог
            SplitRow("Внесено", "−" + formatRub(paid.sumOf { it.rub } + lp.sumOf { minOf(it.month, it.plan) }, symbol = false))
            SplitRow("Предстоит", "−" + formatRub(due.sumOf { it.amt } + lp.sumOf { it.reserve }, symbol = false))
            if (s.loans.isNotEmpty()) SplitRow("Долгов осталось всего", formatRub(debt, symbol = false))
        }
    }
    var i = 3
    ListHeader("付款", "Обязательные платежи", "${paid.size}/${exp.size} оплачено", Modifier.reveal(i++))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        exp.forEach { r -> key(r.id) { RecRow(r, s, Modifier.reveal(i++), plan) } }
        AddRow("Добавить платёж (аренда, связь…)", Modifier.reveal(i++), onClick = onAdd)
    }
    ListHeader("债", "Кредиты и долги", if (debt > 0) "осталось " + formatRub(debt, symbol = false) else "", Modifier.reveal(i++))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // открытые сверху, выплаченные — в конце
        (s.loans.filter { it.balance > 0 } + s.loans.filter { it.balance <= 0 }).forEach { l -> key(l.id) { LoanRow(l, s, Modifier.reveal(i++), loans) } }
        AddRow("Добавить кредит или долг (маме, рассрочка…)", Modifier.reveal(i++), onClick = loans.onNew)
    }
    Text(
        "«Оплатил» записывает трату на сегодня с суммой платежа. «Внести» по кредиту: сначала гасятся проценты за месяц, " +
            "остальное уменьшает долг. Ошибся — удали операцию в Истории, отметка снимется и долг вернётся.",
        style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp).reveal(i),
    )
}

/**
 * Кредит или долг: статус платежа месяца, ставка, остаток из взятого, прогноз, полоса выплаченного,
 * платёж месяца и «Внести» (с процентами) / «Вернуть» (без), пока платёж месяца не внесён или графика нет.
 */
@Composable
private fun LoanRow(l: Loan, s: SpendingState, modifier: Modifier, loans: LoanActions, withButton: Boolean = true) {
    val lp = loanPlan(l, s.monthTx, s.today)
    val closed = l.balance <= 0
    val done = closed || (lp.plan > 0 && lp.reserve == 0L)
    val p = if (l.total > 0) ((l.total - l.balance).toDouble() / l.total).coerceIn(0.0, 1.0) else 0.0
    val (st, color) = when {
        closed -> "✓ выплачен" to KassaColors.Lime
        lp.plan > 0 && lp.reserve == 0L -> "✓ внесено ${formatRub(lp.month, symbol = false)}" to KassaColors.Lime
        lp.plan > 0 && l.day < s.today.dayOfMonth -> "срок был ${l.day}-го" to KassaColors.Warn
        lp.plan > 0 -> "ждёт ${l.day}-го" to KassaColors.Grey
        lp.month > 0 -> "в этом месяце ${formatRub(lp.month, symbol = false)}" to KassaColors.Lime
        else -> "без графика" to KassaColors.Grey
    }
    val fc = loanForecastText(loanForecast(l, s.today), short = true)
    val nb = { x: String -> x.replace(' ', '\u00A0') }
    val meta = "${nb(formatRub(maxOf(0L, l.balance), symbol = false))} из ${nb(formatRub(l.total, symbol = false))}" + if (fc.isNotEmpty()) " · ${nb(fc)}" else ""
    val showBtn = withButton && !closed && (lp.reserve > 0 || lp.plan == 0L)
    Panel(ShapeC, PanelKind.DARK, modifier.press { loans.onEdit(l) }.graphicsLayer { alpha = if (done) 0.62f else 1f }, padding = 0.dp, highlight = true) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(34.dp).background(KassaColors.Raised), contentAlignment = Alignment.Center) {
                Text(if (l.rate > 0) "债" else "借", style = KassaType.cjk(13).copy(color = KassaColors.Lime))
            }
            Column(Modifier.weight(1f)) {
                Text(l.name, style = KassaType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = color)) { append(st.uppercase()) }
                        append(" · " + rateText(l.rate) + "\n" + meta)
                    },
                    style = KassaType.Small.copy(fontSize = KassaType.Label.fontSize, color = KassaColors.Grey),
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(KassaColors.Raised)) {
                    Box(Modifier.fillMaxWidth(p.toFloat()).height(4.dp).background(KassaColors.Lime))
                }
            }
            if (lp.plan > 0) Text("−" + formatRub(lp.plan, symbol = false), style = KassaType.RowAmount, maxLines = 1, softWrap = false)
            if (showBtn) MiniButton(if (l.rate > 0) "Внести" else "Вернуть") { loans.onPay(l) }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun TxRow(t: Tx, s: SpendingState, modifier: Modifier, onEdit: (Tx) -> Unit) {
    val cat = s.allCats[t.cat]
    val inc = t.t == TxType.INC
    val time = Instant.ofEpochMilli(t.ts).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
    // Операция по плану называется как строка плана (note), а не как категория.
    val title = if (t.ref != null && t.note.isNotBlank()) t.note else cat?.name ?: "Без категории"
    val mark = when {
        t.ref == null -> ""
        t.ref?.kind == RefKind.GOAL -> "в цель"
        t.ref?.kind == RefKind.LOAN -> "платёж по долгу"
        inc -> "получено по плану"
        else -> "по плану"
    }
    val meta = listOf(if (inc) "" else t.who, mark, if (t.ref != null) "" else t.note, time).filter { it.isNotBlank() }.joinToString(" · ")
    OpRow(
        icon = catIcon(cat),
        title = title,
        meta = meta,
        amount = (if (inc) "+" else "−") + formatRub(t.rub, symbol = false),
        positive = inc,
        modifier = modifier,
        onClick = { onEdit(t) },
    )
}

/** Вкладки, которых ещё нет. */
@Composable
fun PlaceholderScreen(text: String) {
    Panel(dev.bessaract.kassa.ui.theme.ShapeC, PanelKind.DARK, Modifier.reveal(2)) {
        PanelLabel("В разработке", "开发中", KassaColors.Grey)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(text, style = KassaType.Body)
        }
    }
}
