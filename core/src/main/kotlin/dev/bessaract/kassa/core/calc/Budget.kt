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
    /** Обязательные: Σ платежей плана + платежи по открытым кредитам + ещё не отложенные взносы на цели с датой. */
    val fixed: Long,
    /** Часть fixed: сколько в этом месяце ещё нужно отложить на цели с датой. */
    val goalsReserve: Long,
    /** Обычные траты месяца (без ref) + взносы в цели. */
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
    val fixed = rec.filter { it.type == CatType.EXP }.sumOf { it.amt } +
        loans.filter { it.balance > 0 }.sumOf { it.pay } +
        goalsReserve
    val toGoal = { x: Tx -> x.ref?.kind == RefKind.GOAL }
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
