package dev.bessaract.kassa.core.calc

import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.Loan
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth

// Бюджет месяца — 1:1 с budget() в kassa.html. Суммы в копейках (rub).

data class Budget(
    /** Дней до конца месяца, включая сегодня. */
    val left: Int,
    /** Дней в месяце. */
    val days: Int,
    /** Σ постоянных доходов из плана. */
    val planInc: Long,
    /** Обязательные: Σ платежей плана + ещё не внесённые платежи месяца по кредитам + ещё не отложенные взносы на цели с датой. */
    val fixed: Long,
    /** Часть fixed: сколько в этом месяце ещё нужно внести по кредитам и долгам. */
    val loansReserve: Long,
    /** Часть fixed: сколько в этом месяце ещё нужно отложить на цели с датой. */
    val goalsReserve: Long,
    /** Обычные траты месяца (без ref) + взносы в цели + платежи по кредитам. */
    val varSpent: Long,
    /** Обычные траты сегодня (без ref). */
    val spentToday: Long,
    /** Разовые доходы месяца (без ref). */
    val extraInc: Long,
    /** Свободно до конца месяца. */
    val free: Long,
    /** Лимит на сегодня: (free + spentToday) / left. */
    val dayLimit: Long,
    /** Осталось на сегодня: dayLimit − spentToday; меньше нуля — перерасход. */
    val leftToday: Long,
) {
    /** Доходов нет ни в плане, ни разовых — лимит на день не имеет смысла. */
    val noIncome: Boolean get() = planInc + extraInc == 0L
}

/** tx — операции любого периода; учитываются только месяц, в котором лежит today. */
fun budget(rec: List<Recurring>, loans: List<Loan>, goals: List<Goal>, tx: List<Tx>, today: LocalDate): Budget {
    val month = monthKey(today)
    val td = today.toString()
    val mtx = tx.filter { it.d.startsWith(month) }
    val free0 = { x: Tx -> x.ref == null }
    val planInc = rec.filter { it.type == CatType.INC }.sumOf { it.amt }
    // Отличие от kassa.html (решение 2026-10-22): цель без даты — копилка, в fixed не входит;
    // у цели с датой в fixed резервируется ещё не отложенная часть взноса месяца (goalPlan).
    // Сами взносы уменьшают «свободно» (varSpent), но не «потрачено сегодня».
    val goalsReserve = goals.sumOf { goalPlan(it, tx, today).reserve }
    // Кредиты (решение 0.7, 2026-10-22) — так же, как цели: в fixed ещё не внесённая часть платежа месяца,
    // а внесённое — в varSpent. Так досрочный платёж и возврат долга без графика тоже уменьшают «свободно».
    // В kassa.html в fixed шёл весь pay, а платежи в траты не входили; при платеже ровно pay итог тот же.
    val loansReserve = loans.sumOf { loanPlan(it, tx, today).reserve }
    val fixed = rec.filter { it.type == CatType.EXP }.sumOf { it.amt } + loansReserve + goalsReserve
    val toGoal = { x: Tx -> x.ref?.kind == RefKind.GOAL || x.ref?.kind == RefKind.LOAN }
    val varSpent = mtx.filter { it.t == TxType.EXP && (free0(it) || toGoal(it)) }.sumOf { it.rub }
    val spentToday = mtx.filter { it.t == TxType.EXP && free0(it) && it.d == td }.sumOf { it.rub }
    val extraInc = mtx.filter { it.t == TxType.INC && free0(it) }.sumOf { it.rub }
    val free = planInc + extraInc - fixed - varSpent
    val left = daysLeft(today)
    // В JS деление дробное; у нас копейки — округляем до копейки к ближайшему, как Math.round при показе.
    val dayLimit = Math.round((free + spentToday).toDouble() / left)
    return Budget(
        left = left,
        days = YearMonth.from(today).lengthOfMonth(),
        planInc = planInc,
        fixed = fixed,
        loansReserve = loansReserve,
        goalsReserve = goalsReserve,
        varSpent = varSpent,
        spentToday = spentToday,
        extraInc = extraInc,
        free = free,
        dayLimit = dayLimit,
        leftToday = dayLimit - spentToday,
    )
}

/**
 * Цель на этот месяц. month — отложено в этом месяце; plan — сколько нужно отложить в этом месяце, чтобы успеть к дате;
 * reserve — сколько из plan ещё не отложено; months — месяцев до даты, включая текущий. Без даты всё, кроме month, — ноль.
 */
data class GoalPlan(val month: Long, val plan: Long, val reserve: Long, val months: Int)

fun goalPlan(g: Goal, tx: List<Tx>, today: LocalDate): GoalPlan {
    val key = monthKey(today)
    val month = tx.filter { it.d.startsWith(key) && it.ref?.kind == RefKind.GOAL && it.ref.id == g.id }.sumOf { it.rub }
    val by = g.by.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return GoalPlan(month, 0, 0, 0)
    // недостающее на начало месяца делим на месяцы до даты; прошедшая дата — всё недостающее в этом месяце
    val need = maxOf(0L, g.target - (g.saved - month))
    val months = maxOf(1, (by.year * 12 + by.monthValue) - (today.year * 12 + today.monthValue) + 1)
    // вверх до целого рубля, как Math.ceil в прототипе
    val perRub = 100L * months
    val plan = (need + perRub - 1) / perRub * 100
    return GoalPlan(month, plan, maxOf(0L, plan - month), months)
}

/** Проценты за месяц, копейки (дробные): balance × rate / 1200, как в kassa.html. */
fun loanInterest(l: Loan): Double = l.balance * l.rate / 1200

/** Платёж amt по кредиту: сколько из него уменьшит долг. Сначала проценты, остальное — в долг, не больше остатка. */
fun loanPrincipal(l: Loan, amt: Long): Long =
    Math.round(minOf(l.balance.toDouble(), maxOf(0.0, amt - loanInterest(l)))).coerceIn(0L, maxOf(0L, l.balance))

/**
 * Кредит в этом месяце. month — внесено в этом месяце; plan — платёж месяца (pay, но не больше, чем осталось
 * с процентами, вверх до рубля); reserve — сколько из plan ещё не внесено. Без платежа (долг без графика) plan = 0.
 */
data class LoanPlan(val month: Long, val plan: Long, val reserve: Long)

fun loanPlan(l: Loan, tx: List<Tx>, today: LocalDate): LoanPlan {
    val key = monthKey(today)
    val month = tx.filter { it.d.startsWith(key) && it.ref?.kind == RefKind.LOAN && it.ref.id == l.id }.sumOf { it.rub }
    if (l.pay <= 0) return LoanPlan(month, 0, 0)
    val rest = Math.ceil((maxOf(0L, l.balance) + loanInterest(l)) / 100).toLong() * 100
    val plan = minOf(l.pay, month + rest)
    return LoanPlan(month, plan, maxOf(0L, plan - month))
}

/** Прогноз закрытия — loanForecast в kassa.html. */
sealed interface LoanForecast {
    data object Closed : LoanForecast
    /** Платежа в месяц нет — возвращается когда получится. */
    data object NoSchedule : LoanForecast
    data object NotCovering : LoanForecast
    data object TooLong : LoanForecast
    /** Закроется через [months] месяцев, в месяце [at]. */
    data class In(val months: Int, val at: YearMonth) : LoanForecast
}

fun loanForecast(l: Loan, today: LocalDate): LoanForecast {
    var b = l.balance.toDouble()
    if (b <= 0) return LoanForecast.Closed
    if (l.pay <= 0) return LoanForecast.NoSchedule
    var m = 0
    while (b > 0 && m < 600) {
        val i = b * l.rate / 1200
        if (l.pay <= i) return LoanForecast.NotCovering
        b -= l.pay - i; m++
    }
    if (m >= 600) return LoanForecast.TooLong
    return LoanForecast.In(m, YearMonth.from(today).plusMonths(m.toLong()))
}

/** Операция, которой строка плана проведена в месяце today (paidThisMonth в kassa.html), или null. */
fun recPayment(recId: String, tx: List<Tx>, today: LocalDate): Tx? {
    val month = monthKey(today)
    return tx.firstOrNull { it.d.startsWith(month) && it.ref?.kind == RefKind.REC && it.ref.id == recId }
}

/** Статус строки плана в этом месяце: проведена, срок прошёл или ждёт своего числа. */
sealed interface RecStatus {
    data class Done(val day: Int) : RecStatus
    data class Late(val day: Int) : RecStatus
    data class Waiting(val day: Int) : RecStatus
}

fun recStatus(r: Recurring, tx: List<Tx>, today: LocalDate): RecStatus {
    recPayment(r.id, tx, today)?.let { return RecStatus.Done(LocalDate.parse(it.d).dayOfMonth) }
    return if (r.day < today.dayOfMonth) RecStatus.Late(r.day) else RecStatus.Waiting(r.day)
}

/** Строки плана по числу месяца, как в прототипе. */
fun List<Recurring>.byDay(): List<Recurring> = sortedBy { it.day }
