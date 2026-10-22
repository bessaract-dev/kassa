package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.backup.BackupData
import dev.bessaract.kassa.core.backup.BackupParse
import dev.bessaract.kassa.core.backup.Json
import dev.bessaract.kassa.core.backup.decodeBackup
import dev.bessaract.kassa.core.backup.encodeBackup
import dev.bessaract.kassa.core.backup.mergePlan
import dev.bessaract.kassa.core.model.Account
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
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

class BackupTest {
    private val cats = listOf(Category("c1", "Продукты", CatType.EXP), Category("i1", "Зарплата", CatType.INC))
    private val rec = listOf(Recurring("r1", CatType.EXP, "Аренда", 38_000_00, 5, "c1", ""))
    private val tx = listOf(
        Tx("t1", 1_761_120_000_123, TxType.EXP, 540_50, "RUB", 540_50, "c1", "", "", "Я", "2026-10-22", "кофе \"с собой\"\n"),
        Tx("t2", 1_761_000_000_000, TxType.EXP, 38_000_00, "RUB", 38_000_00, "c1", "", "", "Я", "2026-10-05", "Аренда", TxRef(RefKind.REC, "r1")),
        Tx("t3", 1_758_000_000_000, TxType.INC, 1_500_00, "RUB", 1_500_00, "i1", "", "", "", "2026-09-12", ""),
    )
    private val data = BackupData(cats = cats, persons = listOf("Я", "Варя"), rec = rec, tx = tx)

    @Test
    fun roundTripKeepsEverything() {
        val text = encodeBackup(data, "2026-10-22T12:00:00Z")
        val back = (decodeBackup(text) as BackupParse.Ok).data
        assertEquals(cats, back.cats)
        assertEquals(rec, back.rec)
        assertEquals(listOf("Я", "Варя"), back.persons)
        assertEquals(tx.sortedBy { it.d }, back.tx.sortedBy { it.d })
        assertEquals("2026-10-22T12:00:00Z", back.exported)
        // формат хранилища kassa.html: месяцы ключами, суммы в рублях
        val months = (Json.parse(text) as Map<*, *>)["months"] as Map<*, *>
        assertEquals(setOf("2026-09", "2026-10"), months.keys)
        assertTrue(text.contains("\"amt\":540.5"))
    }

    @Test
    fun readsWebPrototypeFile() {
        val web = """{"app":"kassa","format":1,"exported":"2026-10-22T12:12:00.000Z","cfg":{"accounts":[],"cats":[{"id":"a","name":"Продукты","type":"exp"}],
            "persons":["Я"],"rates":{},"rec":[{"id":"r","type":"inc","name":"Аванс","amt":45000,"day":10,"cat":"a","acc":""}],"loans":[],"goals":[]},
            "months":{"2026-10":[{"id":"x","ts":1761120000000,"t":"exp","amt":220,"cur":"RUB","rub":220,"cat":"a","acc":"","to":"","who":"Я","d":"2026-10-22","note":""}]}}"""
        val d = (decodeBackup(web) as BackupParse.Ok).data
        assertEquals(45_000_00L, d.rec[0].amt)
        assertEquals(22_000L, d.tx[0].rub)
        assertEquals(0L, d.cats[0].limit)
    }

    @Test
    fun rejectsForeignAndBrokenFiles() {
        assertTrue(decodeBackup("не json") is BackupParse.Error)
        assertTrue(decodeBackup("""{"x":1}""") is BackupParse.Error)
        assertTrue(decodeBackup("""{"format":99,"cfg":{"cats":[]},"months":{}}""") is BackupParse.Error)
        val broken = """{"cfg":{"cats":[]},"months":{"2026-10":[{"id":"x","t":"exp","amt":1,"rub":1,"d":"22.10.2026"}]}}"""
        assertEquals(BackupParse.Error("В файле 1 повреждённых операций — восстановление отменено."), decodeBackup(broken))
        // несуществующая дата, бесконечная и отрицательная сумма — тоже порча
        listOf("\"d\":\"2026-11-31\",\"amt\":1,\"rub\":1", "\"d\":\"2026-11-01\",\"amt\":1e400,\"rub\":1", "\"d\":\"2026-11-01\",\"amt\":-5,\"rub\":-5").forEach { f ->
            val t = """{"cfg":{"cats":[]},"months":{"2026-11":[{"id":"x","t":"exp",$f}]}}"""
            assertTrue(f, decodeBackup(t) is BackupParse.Error)
        }
        // «[[[[…» не роняет стек, а даёт ошибку
        assertTrue(decodeBackup("[".repeat(100_000)) is BackupParse.Error)
        // months в виде объекта {id: tx}, как в kassa.html
        val obj = """{"cfg":{"cats":[]},"months":{"2026-11":{"x":{"id":"x","t":"exp","amt":1,"rub":1,"d":"2026-11-01","ref":{"k":"zzz","id":"q"}}}}}"""
        val ok = decodeBackup(obj) as BackupParse.Ok
        assertEquals(null, ok.data.tx[0].ref)
    }

    @Test
    fun mergeAddsOnlyMissingAndGluesSameNamedCategories() {
        // в телефоне: та же «Продукты», но с другим id; t1 и строка плана уже есть
        val phoneCats = listOf(Category("p1", "продукты", CatType.EXP))
        val plan = mergePlan(data, phoneCats, listOf("Я", "Мама"), rec, setOf("t1"))
        assertEquals(listOf("Зарплата"), plan.cats.map { it.name })
        assertEquals(listOf("Варя"), plan.persons)
        assertEquals(emptyList<Recurring>(), plan.rec)
        assertEquals(listOf("t2", "t3"), plan.tx.map { it.id })
        assertEquals("p1", plan.tx[0].cat)
        assertEquals("i1", plan.tx[1].cat)
        assertEquals(2, plan.skipped)
        // повторное восстановление той же копии ничего не добавляет
        val again = mergePlan(data, phoneCats + plan.cats, listOf("Я", "Мама", "Варя"), rec, setOf("t1", "t2", "t3"))
        assertTrue(again.isEmpty)
    }

    @Test
    fun goalsRoundTripAndMergeById() {
        val goals = listOf(Goal("g1", "Китай", 1_000_000_00, 50_000_00, by = "2026-12-30"), Goal("g2", "Копилка", 100_000_00, 0))
        val d = data.copy(goals = goals)
        val back = (decodeBackup(encodeBackup(d, "2026-10-22T12:00:00Z")) as BackupParse.Ok).data
        assertEquals(goals, back.goals)
        // кривая дата не портит файл — цель становится копилкой
        val bad = """{"cfg":{"cats":[],"goals":[{"id":"g","name":"x","target":10,"saved":0,"monthly":0,"by":"2026-02-30"}]},"months":{}}"""
        assertEquals("", (decodeBackup(bad) as BackupParse.Ok).data.goals[0].by)
        val plan = mergePlan(d, cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), listOf(goals[0]))
        assertEquals(listOf("g2"), plan.goals.map { it.id })
        // 1 строка плана + 3 операции + 1 цель уже есть
        assertEquals(5, plan.skipped)
        // новый взнос из копии в цель, что уже есть в телефоне, прибавится к её накопленному
        val top = Tx("t9", 1, TxType.EXP, 700_00, "RUB", 700_00, "c_goal", "", "", "Я", "2026-10-20", "Китай", TxRef(RefKind.GOAL, "g1"))
        val p2 = mergePlan(d.copy(tx = tx + top), cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), listOf(goals[0]))
        assertEquals(mapOf("g1" to 700_00L), p2.goalTopUps)
    }

    @Test
    fun loansRoundTripAndMergeById() {
        val loans = listOf(Loan("l1", "Кредит на машину", 450_000_00, 286_000_00, 9_800_00, 14.9, 26), Loan("l2", "Долг маме", 30_000_00, 21_000_00, 0, 0.0, 1))
        val d = data.copy(loans = loans)
        val back = (decodeBackup(encodeBackup(d, "2026-10-22T12:00:00Z")) as BackupParse.Ok).data
        assertEquals(loans, back.loans)
        val plan = mergePlan(d, cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), loans = listOf(loans[0]))
        assertEquals(listOf("l2"), plan.loans.map { it.id })
        // 1 строка плана + 3 операции + 1 кредит уже есть
        assertEquals(5, plan.skipped)
        // новый платёж из копии по кредиту, что уже есть в телефоне, уменьшит его остаток на погашенную часть
        val pay = Tx("t9", 1, TxType.EXP, 9_800_00, "RUB", 9_800_00, "c_loan", "", "", "Я", "2026-10-20", "Кредит", TxRef(RefKind.LOAN, "l1", 6_248_83))
        val p2 = mergePlan(d.copy(tx = tx + pay), cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), loans = listOf(loans[0]))
        assertEquals(mapOf("l1" to 6_248_83L), p2.loanPayoffs)
        // кредит удалили в телефоне и вернули из копии: платёж, сделанный после копии (его в файле нет), вычитается;
        // платёж, что есть и в файле, — нет (остаток в копии его уже учёл)
        val phone = listOf(Triple("after", "l2", 2_000_00L), Triple(tx[0].id, "l2", 500_00L))
        val p3 = mergePlan(d, cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), loans = listOf(loans[0]), phoneLoanPayments = phone)
        assertEquals(mapOf("l2" to 2_000_00L), p3.loanPayoffs)
        // кривые кредиты в файле: отрицательная ставка — без процентов, число месяца — в 1..31
        val bad = """{"cfg":{"cats":[],"loans":[{"id":"x","name":"x","total":10,"balance":5,"pay":1,"rate":-5,"day":40}]},"months":{}}"""
        val l = (decodeBackup(bad) as BackupParse.Ok).data.loans[0]
        assertEquals(0.0, l.rate, 0.0)
        assertEquals(31, l.day)
    }

    @Test
    fun accountsAndTransfersRoundTripAndMergeById() {
        val accs = listOf(Account("a1", "Карта", "RUB", 12_345_67), Account("a2", "Наличные", "RUB", -500_00))
        val tr = Tx("tr1", 5, TxType.TR, 3_000_00, "RUB", 3_000_00, "", "a1", "a2", "", "2026-10-21", "")
        val d = data.copy(accounts = accs, tx = tx + tr)
        val back = (decodeBackup(encodeBackup(d, "2026-10-22T12:00:00Z")) as BackupParse.Ok).data
        assertEquals(accs, back.accounts)
        assertEquals(tr, back.tx.single { it.id == "tr1" })
        // счёт, что уже есть в телефоне, не трогаем; пустой id пропускаем
        // и валютный счёт из веб-версии: счета только в рублях
        val withBlank = d.copy(accounts = accs + Account("", "x", "RUB", 0) + Account("usd", "Доллары", "USD", 100_00))
        val plan = mergePlan(withBlank, cats, listOf("Я", "Варя"), rec, tx.map { it.id }.toSet(), accounts = listOf(accs[0]))
        assertEquals(listOf("a2"), plan.accounts.map { it.id })
        assertEquals(listOf("tr1"), plan.tx.map { it.id })
        // 1 строка плана + 3 операции + 1 счёт уже есть
        assertEquals(5, plan.skipped)
    }
}
