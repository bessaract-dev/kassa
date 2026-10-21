package dev.bessaract.kassa.core.calc

import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth

// Расчёты MVP «учёт ежедневных расходов». Суммы — в рублях по курсу (поле rub), как в kassa.html.

/** Ключ месяца «ГГГГ-ММ», как t.d.slice(0,7) в kassa.html. */
fun monthKey(date: LocalDate): String = date.toString().substring(0, 7)

/** Дней до конца месяца, включая сегодня (left в budget()). */
fun daysLeft(today: LocalDate): Int = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1

data class Spending(
    /** Потрачено сегодня, копейки. */
    val today: Long,
    /** Потрачено за месяц (b.exp в kassa.html — все расходы месяца), копейки. */
    val month: Long,
    /** Траты сегодня: новые сверху (sort по ts, как в vHome). */
    val todayTx: List<Tx>,
    /** Траты месяца по дням: дни от новых к старым, внутри дня новые сверху. */
    val byDay: List<DayGroup>,
)

data class DayGroup(val date: LocalDate, val total: Long, val tx: List<Tx>)

/** Сводка трат за месяц, в котором лежит today. tx — операции любого периода, лишние отбрасываются. */
fun spending(tx: List<Tx>, today: LocalDate): Spending {
    val month = monthKey(today)
    val td = today.toString()
    val exp = tx.filter { it.t == TxType.EXP && it.d.startsWith(month) }
    val todayTx = exp.filter { it.d == td }.sortedByDescending { it.ts }
    val byDay = exp.groupBy { it.d }
        .toSortedMap(reverseOrder())
        .map { (d, list) -> DayGroup(LocalDate.parse(d), list.sumOf { it.rub }, list.sortedByDescending { it.ts }) }
    return Spending(
        today = todayTx.sumOf { it.rub },
        month = exp.sumOf { it.rub },
        todayTx = todayTx,
        byDay = byDay,
    )
}
