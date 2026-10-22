package dev.bessaract.kassa.core.calc

import dev.bessaract.kassa.core.model.Account
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType

// Счета (0.8, только рубли): баланс = остаток на старте (init) ± операции этого счёта, как accBalance в kassa.html.
// Перевод уменьшает счёт acc и увеличивает счёт to; в бюджет переводы не входят.

/** Сумма операций одного вида по паре счетов — так база отдаёт движение денег, не загружая все операции. */
data class AccMove(val t: TxType, val acc: String, val to: String, val rub: Long)

/** Движение по каждому счёту: сколько прибавилось за всё время (может быть меньше нуля). */
fun accountMoves(moves: List<AccMove>): Map<String, Long> {
    val m = HashMap<String, Long>()
    fun add(id: String, v: Long) { if (id.isNotEmpty()) m[id] = (m[id] ?: 0L) + v }
    moves.forEach { x ->
        when (x.t) {
            TxType.INC -> add(x.acc, x.rub)
            TxType.EXP -> add(x.acc, -x.rub)
            TxType.TR -> { add(x.acc, -x.rub); add(x.to, x.rub) }
        }
    }
    return m
}

fun List<Tx>.toMoves(): List<AccMove> = map { AccMove(it.t, it.acc, it.to, it.rub) }

/** Баланс счёта. */
fun accountBalance(a: Account, moves: Map<String, Long>): Long = a.init + (moves[a.id] ?: 0L)

/** Остаток на старте, чтобы баланс сейчас был равен [balance] (в форме вводят, сколько на счёте сейчас). */
fun accountInit(accId: String, balance: Long, moves: Map<String, Long>): Long = balance - (moves[accId] ?: 0L)

/** За месяц по счёту: пришло и ушло (переводы тоже). monthTx — операции месяца. */
data class AccMonth(val inflow: Long, val outflow: Long)

fun accountMonth(accId: String, monthTx: List<Tx>): AccMonth {
    var i = 0L; var o = 0L
    monthTx.forEach { t ->
        when (t.t) {
            TxType.TR -> { if (t.acc == accId) o += t.rub; if (t.to == accId) i += t.rub }
            TxType.INC -> if (t.acc == accId) i += t.rub
            TxType.EXP -> if (t.acc == accId) o += t.rub
        }
    }
    return AccMonth(i, o)
}
