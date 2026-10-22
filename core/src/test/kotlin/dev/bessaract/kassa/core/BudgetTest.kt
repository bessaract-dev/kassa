package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.calc.RecStatus
import dev.bessaract.kassa.core.calc.budget
import dev.bessaract.kassa.core.calc.goalPlan
import dev.bessaract.kassa.core.calc.LoanForecast
import dev.bessaract.kassa.core.calc.loanForecast
import dev.bessaract.kassa.core.calc.loanPlan
import dev.bessaract.kassa.core.calc.loanPrincipal
import java.time.YearMonth
import dev.bessaract.kassa.core.calc.recStatus
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.Loan
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxRef
import dev.bessaract.kassa.core.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private val today = LocalDate.of(2026, 10, 22) // в октябре 31 день → left = 10

    private fun rec(id: String, type: CatType, rub: Long, day: Int) = Recurring(id, type, id, rub * 100, day, "c", "")
    private fun tx(d: String, rub: Long, t: TxType = TxType.EXP, ref: TxRef? = null) =
        Tx("x", 0, t, rub * 100, "RUB", rub * 100, "c", "", "", "Я", d, "", ref)

    private val plan = listOf(
        rec("avans", CatType.INC, 45_000, 10),
        rec("zp", CatType.INC, 65_000, 25),
        rec("rent", CatType.EXP, 38_000, 5),
        rec("net", CatType.EXP, 900, 28),
    )

    @Test
    fun formulasMatchKassaHtml() {
        val list = listOf(
            tx("2026-10-22", 760),                      // обычная трата сегодня
            tx("2026-10-20", 4_240),                    // обычная трата в этом месяце
            tx("2026-10-05", 38_000, ref = TxRef(RefKind.REC, "rent")), // платёж по плану — не обычная трата
            tx("2026-10-10", 45_000, TxType.INC, TxRef(RefKind.REC, "avans")), // доход по плану — уже в planInc
            tx("2026-10-12", 1_500, TxType.INC),         // разовый доход
            tx("2026-09-30", 9_999),                    // прошлый месяц
        )
        val loans = listOf(Loan("l", "Кредит", 0, 100_000_00, 5_000_00, 12.0, 15), Loan("closed", "Закрыт", 0, 0, 7_000_00, 0.0, 1))
        val goals = listOf(Goal("g", "Отпуск", 100_000_00, 10_000_00, 2_000_00), Goal("done", "Готово", 1_00, 1_00, 9_00))
        val b = budget(plan, loans, goals, list, today)
        assertEquals(10, b.left)
        assertEquals(110_000_00L, b.planInc)
        // цели без даты — копилки: monthly из kassa.html не резервируется (решение 2026-10-22)
        assertEquals((38_000 + 900 + 5_000) * 100L, b.fixed)
        assertEquals(5_000_00L, b.varSpent)
        assertEquals(760_00L, b.spentToday)
        assertEquals(1_500_00L, b.extraInc)
        val free = (110_000 + 1_500 - 43_900 - 5_000) * 100L
        assertEquals(free, b.free)
        assertEquals((free + 760_00) / 10, b.dayLimit)
        assertEquals((free + 760_00) / 10 - 760_00, b.leftToday)
    }

    @Test
    fun dayLimitRoundsToKopeck() {
        // 30.10: left = 2; free + spentToday = 200,99 ₽ → 100,495 ₽ → 100,50 ₽ (как Math.round(n*100)/100 в fmt)
        val b = budget(listOf(rec("x", CatType.INC, 0, 1).copy(amt = 200_99)), emptyList(), emptyList(), emptyList(), LocalDate.of(2026, 10, 30))
        assertEquals(2, b.left)
        assertEquals(100_50L, b.dayLimit)
    }

    @Test
    fun noIncomeAndOverspend() {
        val empty = budget(emptyList(), emptyList(), emptyList(), listOf(tx("2026-10-22", 500)), today)
        assertTrue(empty.noIncome)
        assertTrue(empty.leftToday < 0)
    }

    @Test
    fun recStatusThisMonthOnly() {
        val paid = listOf(tx("2026-10-05", 38_000, ref = TxRef(RefKind.REC, "rent")))
        assertEquals(RecStatus.Done(5), recStatus(plan[2], paid, today))
        // проведено в прошлом месяце — в этом снова ждёт / просрочено
        val old = listOf(tx("2026-09-05", 38_000, ref = TxRef(RefKind.REC, "rent")))
        assertEquals(RecStatus.Late(5), recStatus(plan[2], old, today))
        assertEquals(RecStatus.Waiting(28), recStatus(plan[3], emptyList(), today))
        assertEquals(RecStatus.Waiting(22), recStatus(plan[3].copy(day = 22), emptyList(), today))
    }

    @Test
    fun piggybankContributionCutsFreeButNotToday() {
        val g = Goal("g", "Копилка", 100_000_00, 15_000_00)
        val list = listOf(tx("2026-10-22", 760), tx("2026-10-22", 5_000, ref = TxRef(RefKind.GOAL, "g")))
        val b = budget(plan, emptyList(), listOf(g), list, today)
        assertEquals(0L, b.goalsReserve)
        assertEquals(38_900_00L, b.fixed)
        assertEquals(5_760_00L, b.varSpent)
        assertEquals(760_00L, b.spentToday)
        assertEquals((110_000 - 38_900 - 5_760) * 100L, b.free)
    }

    @Test
    fun datedGoalReservesMonthlyShare() {
        // 1 000 000 к 30.12: октябрь, ноябрь, декабрь — 3 месяца → 333 334 в месяц (вверх до рубля)
        val g = Goal("china", "Китай", 1_000_000_00, 0, by = "2026-12-30")
        val p0 = goalPlan(g, emptyList(), today)
        assertEquals(3, p0.months)
        assertEquals(333_334_00L, p0.plan)
        assertEquals(333_334_00L, p0.reserve)
        val b0 = budget(plan, emptyList(), listOf(g), emptyList(), today)
        assertEquals((38_900 + 333_334) * 100L, b0.fixed)
        assertTrue(b0.dayLimit < 0)

        // отложил 100 000 в этом месяце: план месяца не меняется, резерв — остаток плана
        val paid = listOf(tx("2026-10-21", 100_000, ref = TxRef(RefKind.GOAL, "china")))
        val g1 = g.copy(saved = 100_000_00)
        val p1 = goalPlan(g1, paid, today)
        assertEquals(333_334_00L, p1.plan)
        assertEquals(233_334_00L, p1.reserve)
        // fixed + varSpent уменьшают free ровно на план, а не на план + взнос
        val b1 = budget(plan, emptyList(), listOf(g1), paid, today)
        assertEquals(b0.free, b1.free)

        // отложил больше плана — резерв ноль, лишнее тоже вычитается
        val big = listOf(tx("2026-10-21", 400_000, ref = TxRef(RefKind.GOAL, "china")))
        assertEquals(0L, goalPlan(g.copy(saved = 400_000_00), big, today).reserve)

        // в ноябре недостающее 900 000 делится на 2 месяца
        assertEquals(450_000_00L, goalPlan(g.copy(saved = 100_000_00), paid, LocalDate.of(2026, 11, 1)).plan)
        // дата прошла — всё недостающее в этом месяце; цель достигнута — ничего
        assertEquals(900_000_00L, goalPlan(g.copy(saved = 100_000_00), emptyList(), LocalDate.of(2027, 1, 5)).plan)
        assertEquals(0L, goalPlan(g.copy(saved = 1_000_000_00), emptyList(), today).plan)
        // без даты или с кривой датой — копилка
        assertEquals(0L, goalPlan(g.copy(by = ""), emptyList(), today).plan)
        assertEquals(0L, goalPlan(g.copy(by = "2026-13-40"), emptyList(), today).plan)
    }

    @Test
    fun loanPaymentMovesFromReserveToSpent() {
        val car = Loan("car", "Кредит", 450_000_00, 100_000_00, 5_000_00, 12.0, 15)
        val before = budget(plan, listOf(car), emptyList(), emptyList(), today)
        assertEquals(5_000_00L, before.loansReserve)
        // платёж ровно pay: резерв уходит, сумма — в траты месяца; «свободно» то же, «потрачено сегодня» не меняется
        val paid = listOf(tx("2026-10-15", 5_000, ref = TxRef(RefKind.LOAN, "car", 4_000_00)))
        val after = budget(plan, listOf(car), emptyList(), paid, today)
        assertEquals(0L, after.loansReserve)
        assertEquals(5_000_00L, after.varSpent)
        assertEquals(before.free, after.free)
        // досрочно сверх платежа — «свободно» уменьшается на лишнее
        val extra = paid + tx("2026-10-22", 2_000, ref = TxRef(RefKind.LOAN, "car", 2_000_00))
        val b = budget(plan, listOf(car), emptyList(), extra, today)
        assertEquals(before.free - 2_000_00, b.free)
        assertEquals(0L, b.spentToday)
    }

    @Test
    fun debtWithoutScheduleCountsOnlyWhenReturned() {
        val mom = Loan("mom", "Долг маме", 30_000_00, 21_000_00, 0, 0.0, 1)
        val none = budget(plan, listOf(mom), emptyList(), emptyList(), today)
        assertEquals(0L, none.loansReserve)
        val back = budget(plan, listOf(mom), emptyList(), listOf(tx("2026-10-03", 3_000, ref = TxRef(RefKind.LOAN, "mom", 3_000_00))), today)
        assertEquals(none.free - 3_000_00, back.free)
    }

    @Test
    fun loanPlanCappedByWhatIsLeft() {
        // остаток 2 000 ₽ под 12%: проценты 20 ₽ → платёж месяца 2 020, а не 5 000
        val l = Loan("x", "x", 10_000_00, 2_000_00, 5_000_00, 12.0, 15)
        assertEquals(2_020_00L, loanPlan(l, emptyList(), today).plan)
        val closed = l.copy(balance = 0)
        assertEquals(0L, loanPlan(closed, emptyList(), today).reserve)
    }

    @Test
    fun loanPrincipalInterestFirst() {
        // как payLoan в kassa.html: 412 000 × 14,9% / 12 = 5 115,67 ₽ процентов, остальное — в долг
        val l = Loan("x", "x", 600_000_00, 412_000_00, 18_500_00, 14.9, 28)
        assertEquals(13_384_33L, loanPrincipal(l, 18_500_00))
        // платёж меньше процентов — долг не уменьшается; больше остатка — гасится только остаток
        assertEquals(0L, loanPrincipal(l, 5_000_00))
        assertEquals(1_000_00L, loanPrincipal(l.copy(balance = 1_000_00, rate = 0.0), 5_000_00))
    }

    @Test
    fun loanForecastLikeKassaHtml() {
        assertEquals(LoanForecast.In(4, YearMonth.of(2027, 2)), loanForecast(Loan("x", "x", 0, 10_000_00, 3_000_00, 0.0, 1), today))
        assertEquals(LoanForecast.NotCovering, loanForecast(Loan("x", "x", 0, 1_000_000_00, 5_000_00, 12.0, 1), today))
        assertEquals(LoanForecast.NoSchedule, loanForecast(Loan("x", "x", 0, 1_000_00, 0, 0.0, 1), today))
        assertEquals(LoanForecast.Closed, loanForecast(Loan("x", "x", 0, 0, 1_000_00, 0.0, 1), today))
    }
}
