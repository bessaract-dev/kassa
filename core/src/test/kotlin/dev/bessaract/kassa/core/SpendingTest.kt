package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.calc.daysLeft
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpendingTest {
    private fun tx(id: String, d: String, rub: Long, t: TxType = TxType.EXP, ts: Long = 0) =
        Tx(id, ts, t, rub, "RUB", rub, "c", "", "", "Я", d, "")

    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun sumsOnlyExpensesButListsIncomes() {
        val list = listOf(
            tx("a", "2026-10-21", 54000, ts = 2),
            tx("b", "2026-10-21", 22000, ts = 5),
            tx("c", "2026-10-03", 100000),
            tx("inc", "2026-10-21", 15000, TxType.INC),
            tx("tr", "2026-10-21", 70000, TxType.TR),
            tx("old", "2026-09-30", 999900),
        )
        val s = spending(list, today)
        assertEquals(76000L, s.today)
        assertEquals(176000L, s.month)
        assertEquals(15000L, s.monthInc)
        assertEquals(listOf("b", "a", "inc"), s.todayTx.map { it.id })
        assertEquals(listOf("2026-10-21", "2026-10-03"), s.byDay.map { it.date.toString() })
        assertEquals(76000L, s.byDay[0].total)
    }

    @Test
    fun daysLeftIncludesToday() {
        assertEquals(11, daysLeft(today))
        assertEquals(1, daysLeft(LocalDate.of(2026, 10, 31)))
        assertEquals(29, daysLeft(LocalDate.of(2028, 2, 1)))
    }

    @Test
    fun parseAmountLikeWebNum() {
        assertEquals(123450L, parseAmount("1 234,5"))
        assertEquals(5L, parseAmount("0.05"))
        assertEquals(0L, parseAmount(""))
        assertEquals(0L, parseAmount("abc"))
        assertEquals(1L, parseAmount("0,005"))
        // хвост отбрасывается, как parseFloat в JS
        assertEquals(150L, parseAmount("1,5,"))
        assertEquals(120L, parseAmount("1.2.3"))
        assertEquals(50L, parseAmount(",5"))
    }

    @Test
    fun formatRubLikeWebFmt() {
        assertEquals("1 234 ₽", formatRub(123449))
        assertEquals("1 235 ₽", formatRub(123450))
        assertEquals("−860 ₽", formatRub(-86000))
        assertEquals("0 ₽", formatRub(-40))
        assertEquals("540", formatRub(54000, symbol = false))
    }

    @Test
    fun amountInputRoundTrips() {
        assertEquals("1234", amountInput(123400))
        assertEquals("1234,5", amountInput(123450))
        assertEquals("0,05", amountInput(5))
        assertEquals(123450L, parseAmount(amountInput(123450)))
    }
}
