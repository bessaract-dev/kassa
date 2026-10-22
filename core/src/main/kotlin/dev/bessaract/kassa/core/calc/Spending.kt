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
    /** Доходы за месяц (все, включая полученные по плану), копейки. */
    val monthInc: Long,
    /** Операции сегодня (траты и доходы): новые сверху (sort по ts, как в vHome). */
    val todayTx: List<Tx>,
    /** Операции месяца по дням: дни от новых к старым, внутри дня новые сверху. */
    val byDay: List<DayGroup>,
)

/** total — только траты дня. */
data class DayGroup(val date: LocalDate, val total: Long, val tx: List<Tx>)

/** Сводка операций за месяц, в котором лежит today. tx — операции любого периода, лишние отбрасываются. */
fun spending(tx: List<Tx>, today: LocalDate): Spending {
    val month = monthKey(today)
    val td = today.toString()
    // в списках — траты, доходы и переводы между счетами; в суммы переводы не входят
    val ops = tx.filter { it.d.startsWith(month) }
    val exp = ops.filter { it.t == TxType.EXP }
    val todayTx = ops.filter { it.d == td }.sortedByDescending { it.ts }
    val byDay = ops.groupBy { it.d }
        .toSortedMap(reverseOrder())
        .map { (d, list) -> DayGroup(LocalDate.parse(d), list.filter { it.t == TxType.EXP }.sumOf { it.rub }, list.sortedByDescending { it.ts }) }
    return Spending(
        today = exp.filter { it.d == td }.sumOf { it.rub },
        month = exp.sumOf { it.rub },
        monthInc = ops.filter { it.t == TxType.INC }.sumOf { it.rub },
        todayTx = todayTx,
        byDay = byDay,
    )
}
