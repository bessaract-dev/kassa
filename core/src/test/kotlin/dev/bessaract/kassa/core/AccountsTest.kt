package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.calc.AccMonth
import dev.bessaract.kassa.core.calc.accountBalance
import dev.bessaract.kassa.core.calc.accountInit
import dev.bessaract.kassa.core.calc.accountMonth
import dev.bessaract.kassa.core.calc.accountMoves
import dev.bessaract.kassa.core.calc.budget
import dev.bessaract.kassa.core.calc.toMoves
import dev.bessaract.kassa.core.model.Account
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AccountsTest {
    private fun tx(id: String, t: TxType, rub: Long, acc: String, to: String = "", d: String = "2026-10-22") =
        Tx(id, 0, t, rub, "RUB", rub, if (t == TxType.TR) "" else "c", acc, to, "Я", d, "")

    private val card = Account("card", "Карта", "RUB", 10_000_00)
    private val cash = Account("cash", "Наличные", "RUB", 2_000_00)
    private val list = listOf(
        tx("e1", TxType.EXP, 1_500_00, "card"),
        tx("i1", TxType.INC, 50_000_00, "card", d = "2026-09-30"),
        tx("tr", TxType.TR, 3_000_00, "card", "cash"),
        tx("e2", TxType.EXP, 700_00, "cash"),
        // без счёта — ни на что не влияет
        tx("e3", TxType.EXP, 999_00, ""),
    )

    @Test
    fun balanceIsInitPlusMoves() {
        val m = accountMoves(list.toMoves())
        // карта: 10 000 − 1 500 + 50 000 − 3 000; наличные: 2 000 + 3 000 − 700
        assertEquals(55_500_00L, accountBalance(card, m))
        assertEquals(4_300_00L, accountBalance(cash, m))
        // перевод не меняет общую сумму
        assertEquals(10_000_00L + 2_000_00L - 1_500_00L + 50_000_00L - 700_00L, accountBalance(card, m) + accountBalance(cash, m))
    }

    @Test
    fun initFromCurrentBalance() {
        val m = accountMoves(list.toMoves())
        // ввели «сейчас на карте 60 000» — остаток на старте такой, чтобы баланс сошёлся
        val init = accountInit("card", 60_000_00, m)
        assertEquals(60_000_00L, accountBalance(card.copy(init = init), m))
        // новый счёт без операций: init = баланс
        assertEquals(1_000_00L, accountInit("new", 1_000_00, m))
    }

    @Test
    fun monthInAndOut() {
        val oct = list.filter { it.d.startsWith("2026-10") }
        assertEquals(AccMonth(0, 4_500_00), accountMonth("card", oct))
        assertEquals(AccMonth(3_000_00, 700_00), accountMonth("cash", oct))
    }

    @Test
    fun transfersStayOutOfBudget() {
        val d = LocalDate.of(2026, 10, 22)
        val withTr = list.filter { it.d.startsWith("2026-10") }
        val noTr = withTr.filter { it.t != TxType.TR }
        assertEquals(budget(emptyList(), emptyList(), emptyList(), noTr, d), budget(emptyList(), emptyList(), emptyList(), withTr, d))
    }
}
