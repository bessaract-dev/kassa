package dev.bessaract.kassa.core.backup

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
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

// Резервная копия: один JSON-файл по образцу хранилища kassa.html — {cfg, months}, суммы в рублях (числа).
// В kassa.html months[k] — объект {id: tx}, у нас массив; при чтении принимаем оба вида.
// Сверху служебные поля app / format / exported. Восстановление — объединением: добавляется только то, чего нет.

const val BACKUP_FORMAT = 1

/** Всё, что лежит в копии. */
data class BackupData(
    val accounts: List<Account> = emptyList(),
    val cats: List<Category>,
    val persons: List<String>,
    val rec: List<Recurring>,
    val loans: List<Loan> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val tx: List<Tx>,
    /** Когда сделана копия (ISO-8601), если указано. */
    val exported: String? = null,
)

private fun rub(kopecks: Long): Double = kopecks / 100.0
/** Рубли → копейки. Бесконечность и суммы больше 100 млрд ₽ — признак порчи файла. */
private fun kop(v: Any?): Long {
    val x = (v as? Double) ?: throw JsonException("ожидалось число")
    if (!x.isFinite() || abs(x) >= 1e11) throw JsonException("сумма вне диапазона")
    return (x * 100).roundToLong()
}

/** Сумма операции или строки плана: не меньше нуля. */
private fun amount(v: Any?): Long = kop(v).also { if (it < 0) throw JsonException("отрицательная сумма") }

/** Копия → текст файла. exported — момент выгрузки в ISO-8601. */
fun encodeBackup(d: BackupData, exported: String): String {
    val months = d.tx.sortedWith(compareBy({ it.d }, { it.ts })).groupBy { it.d.substring(0, 7) }
    return Json.write(
        linkedMapOf(
            "app" to "kassa",
            "format" to BACKUP_FORMAT,
            "exported" to exported,
            "cfg" to linkedMapOf(
                "accounts" to d.accounts.map { linkedMapOf("id" to it.id, "name" to it.name, "cur" to it.cur, "init" to rub(it.init)) },
                "cats" to d.cats.map { linkedMapOf("id" to it.id, "name" to it.name, "type" to it.type.code, "limit" to rub(it.limit)) },
                "persons" to d.persons,
                "rates" to emptyMap<String, Any?>(),
                "rec" to d.rec.map {
                    linkedMapOf("id" to it.id, "type" to it.type.code, "name" to it.name, "amt" to rub(it.amt), "day" to it.day, "cat" to it.cat, "acc" to it.acc)
                },
                "loans" to d.loans.map {
                    linkedMapOf("id" to it.id, "name" to it.name, "total" to rub(it.total), "balance" to rub(it.balance), "pay" to rub(it.pay), "rate" to it.rate, "day" to it.day)
                },
                "goals" to d.goals.map {
                    linkedMapOf("id" to it.id, "name" to it.name, "target" to rub(it.target), "saved" to rub(it.saved), "monthly" to rub(it.monthly), "by" to it.by)
                },
            ),
            "months" to months.mapValues { (_, list) -> list.map(::txJson) },
        ),
    )
}

private fun txJson(t: Tx): Map<String, Any?> = linkedMapOf<String, Any?>(
    "id" to t.id, "ts" to t.ts, "t" to t.t.code, "amt" to rub(t.amt), "cur" to t.cur, "rub" to rub(t.rub),
    "cat" to t.cat, "acc" to t.acc, "to" to t.to, "who" to t.who, "d" to t.d, "note" to t.note,
).also { m ->
    t.ref?.let { r -> m["ref"] = linkedMapOf<String, Any?>("k" to r.kind.code, "id" to r.id).also { if (r.princ != null) it["princ"] = rub(r.princ) } }
}

sealed interface BackupParse {
    data class Ok(val data: BackupData) : BackupParse
    /** Текст ошибки — для пользователя. */
    data class Error(val message: String) : BackupParse
}

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Разбор файла копии с проверкой; при любой порче — понятная ошибка, ничего не применяется. */
fun decodeBackup(text: String): BackupParse {
    val root = try { Json.parse(text.removePrefix("﻿")) } catch (e: JsonException) { return BackupParse.Error("Файл не читается: это не JSON.") }
    val o = root as? Map<*, *> ?: return BackupParse.Error("Это не похоже на копию «Кассы».")
    val cfg = o["cfg"] as? Map<*, *>
    val months = o["months"] as? Map<*, *>
    if (cfg == null || cfg["cats"] !is List<*> || months == null) return BackupParse.Error("Это не похоже на копию «Кассы».")
    val format = (o["format"] as? Double)?.toInt() ?: 1
    if (format > BACKUP_FORMAT) return BackupParse.Error("Копия из более новой версии приложения — сначала обнови приложение.")
    return try {
        val rawTx = months.values.flatMap { (it as? List<*>) ?: (it as? Map<*, *>)?.values ?: throw JsonException("months") }
        var bad = 0
        val tx = rawTx.mapNotNull { r -> runCatching { tx(r as Map<*, *>) }.getOrNull().also { if (it == null) bad++ } }
        if (bad > 0) return BackupParse.Error("В файле $bad повреждённых операций — восстановление отменено.")
        BackupParse.Ok(
            BackupData(
                accounts = list(cfg["accounts"]) { Account(str(it, "id"), str(it, "name"), str(it, "cur", "RUB"), kop(it["init"] ?: 0.0)) },
                cats = list(cfg["cats"]) { Category(str(it, "id"), str(it, "name", ""), catType(it["type"]), kop(it["limit"] ?: 0.0)) },
                persons = (cfg["persons"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                rec = list(cfg["rec"]) {
                    Recurring(str(it, "id"), catType(it["type"]), str(it, "name", ""), amount(it["amt"]), int(it["day"]).coerceIn(1, 31), str(it, "cat", ""), str(it, "acc", ""))
                },
                loans = list(cfg["loans"]) {
                    Loan(str(it, "id"), str(it, "name"), kop(it["total"] ?: 0.0), kop(it["balance"]), kop(it["pay"]), (it["rate"] as? Double) ?: 0.0, int(it["day"] ?: 1.0))
                },
                goals = list(cfg["goals"]) {
                    // дата цели — только настоящая; кривая превращает цель в копилку, а не портит весь файл
                    val by = (it["by"] as? String)?.takeIf { d -> DATE.matches(d) && runCatching { LocalDate.parse(d) }.isSuccess }.orEmpty()
                    Goal(str(it, "id"), str(it, "name", ""), amount(it["target"]), amount(it["saved"] ?: 0.0), kop(it["monthly"] ?: 0.0), by)
                },
                tx = tx,
                exported = o["exported"] as? String,
            ),
        )
    } catch (e: Exception) {
        BackupParse.Error("Файл копии повреждён — восстановление отменено.")
    }
}

private fun <T> list(v: Any?, f: (Map<*, *>) -> T): List<T> = (v as? List<*>).orEmpty().map { f(it as? Map<*, *> ?: throw JsonException("элемент")) }
private fun str(m: Map<*, *>, k: String, def: String? = null): String = (m[k] as? String) ?: def ?: throw JsonException(k)
private fun int(v: Any?): Int = ((v as? Double) ?: throw JsonException("число")).toInt()
private fun catType(v: Any?) = CatType.entries.firstOrNull { it.code == v } ?: throw JsonException("type")

private fun tx(m: Map<*, *>): Tx {
    val d = str(m, "d")
    // не только вид ГГГГ-ММ-ДД, но и настоящая дата: «2026-11-31» потом уронила бы экраны
    require(DATE.matches(d) && runCatching { LocalDate.parse(d) }.isSuccess)
    // неизвестный вид связи — операция становится обычной, а не портит весь файл
    val ref = (m["ref"] as? Map<*, *>)?.let { r ->
        RefKind.entries.firstOrNull { it.code == r["k"] }?.let { k -> TxRef(k, str(r, "id"), r["princ"]?.let(::kop)) }
    }
    return Tx(
        id = str(m, "id"),
        ts = ((m["ts"] as? Double) ?: 0.0).toLong(),
        t = TxType.entries.first { it.code == m["t"] },
        amt = amount(m["amt"]),
        cur = str(m, "cur", "RUB"),
        rub = amount(m["rub"]),
        cat = str(m, "cat", ""),
        acc = str(m, "acc", ""),
        to = str(m, "to", ""),
        who = str(m, "who", ""),
        d = d,
        note = str(m, "note", ""),
        ref = ref,
    )
}

/** Что добавится из копии. skipped — сколько операций и строк плана уже есть. */
data class MergePlan(
    val cats: List<Category>,
    val persons: List<String>,
    val rec: List<Recurring>,
    val tx: List<Tx>,
    val skipped: Int,
    val goals: List<Goal> = emptyList(),
) {
    val isEmpty: Boolean get() = cats.isEmpty() && persons.isEmpty() && rec.isEmpty() && tx.isEmpty() && goals.isEmpty()
}

/**
 * Объединение (решение 2026-10-22): добавляем то, чего нет, по id; существующее не меняется.
 * Категория из файла с тем же типом и названием (без регистра), что уже есть, склеивается с существующей:
 * операции и строки плана переводятся на её id.
 */
fun mergePlan(
    data: BackupData,
    cats: List<Category>,
    persons: List<String>,
    rec: List<Recurring>,
    txIds: Set<String>,
    goals: List<Goal> = emptyList(),
): MergePlan {
    val catMap = HashMap<String, String>()
    val newCats = ArrayList<Category>()
    data.cats.forEach { c ->
        val same = cats.find { it.id == c.id } ?: newCats.find { it.id == c.id }
            ?: cats.find { it.type == c.type && it.name.trim().equals(c.name.trim(), ignoreCase = true) }
        if (same != null) catMap[c.id] = same.id else { newCats += c; catMap[c.id] = c.id }
    }
    val recIds = rec.mapTo(HashSet()) { it.id }
    val newRec = data.rec.filter { it.id !in recIds }.distinctBy { it.id }.map { it.copy(cat = catMap[it.cat] ?: it.cat) }
    val goalIds = goals.mapTo(HashSet()) { it.id }
    // накопленное в цели (saved) берётся из файла как есть: взносы-операции его не пересчитывают
    val newGoals = data.goals.filter { it.id !in goalIds }.distinctBy { it.id }
    val newTx = data.tx.filter { it.id !in txIds }.distinctBy { it.id }.map { it.copy(cat = catMap[it.cat] ?: it.cat) }
    return MergePlan(
        cats = newCats,
        persons = data.persons.filter { it.isNotBlank() && it !in persons }.distinct(),
        rec = newRec,
        tx = newTx,
        skipped = (data.rec.size - newRec.size) + (data.tx.size - newTx.size) + (data.goals.size - newGoals.size),
        goals = newGoals,
    )
}
