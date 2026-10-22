package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.backup.BackupData
import dev.bessaract.kassa.core.backup.BackupParse
import dev.bessaract.kassa.core.backup.Json
import dev.bessaract.kassa.core.backup.decodeBackup
import dev.bessaract.kassa.core.backup.encodeBackup
import dev.bessaract.kassa.core.backup.mergePlan
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
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
}
