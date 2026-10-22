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
    /** Обязательные: Σ платежей плана + платежи по открытым кредитам + взносы на незакрытые цели. */
    val fixed: Long,
    /** Обычные траты месяца (без ref). */
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
    val fixed = rec.filter { it.type == CatType.EXP }.sumOf { it.amt } +
        loans.filter { it.balance > 0 }.sumOf { it.pay } +
        goals.filter { it.saved < it.target }.sumOf { it.monthly }
    val varSpent = mtx.filter { it.t == TxType.EXP && free0(it) }.sumOf { it.rub }
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
        varSpent = varSpent,
        spentToday = spentToday,
        extraInc = extraInc,
        free = free,
        dayLimit = dayLimit,
        leftToday = dayLimit - spentToday,
    )
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
