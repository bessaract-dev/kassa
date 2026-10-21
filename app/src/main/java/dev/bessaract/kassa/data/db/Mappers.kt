package dev.bessaract.kassa.data.db

import dev.bessaract.kassa.core.model.Account
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.Loan
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxRef
import dev.bessaract.kassa.core.model.TxType

// Перевод между строками базы и моделью из core. Коды типов — те же строки, что в kassa.html.

private fun catType(code: String) = CatType.entries.first { it.code == code }
private fun txType(code: String) = TxType.entries.first { it.code == code }
private fun refKind(code: String) = RefKind.entries.first { it.code == code }

fun AccountEntity.toModel() = Account(id, name, cur, init)
fun Account.toEntity(position: Int) = AccountEntity(id, name, cur, init, position)

fun CategoryEntity.toModel() = Category(id, name, catType(type), limit)
fun Category.toEntity(position: Int) = CategoryEntity(id, name, type.code, limit, position)

fun RecurringEntity.toModel() = Recurring(id, catType(type), name, amt, day, cat, acc)
fun Recurring.toEntity(position: Int) = RecurringEntity(id, type.code, name, amt, day, cat, acc, position)

fun LoanEntity.toModel() = Loan(id, name, total, balance, pay, rate, day)
fun Loan.toEntity(position: Int) = LoanEntity(id, name, total, balance, pay, rate, day, position)

fun GoalEntity.toModel() = Goal(id, name, target, saved, monthly)
fun Goal.toEntity(position: Int) = GoalEntity(id, name, target, saved, monthly, position)

fun TxEntity.toModel() = Tx(
    id, ts, txType(t), amt, cur, rub, cat, acc, to, who, d, note,
    ref = refKind?.let { TxRef(refKind(it), refId.orEmpty(), refPrinc) },
)

fun Tx.toEntity() = TxEntity(
    id, ts, t.code, amt, cur, rub, cat, acc, to, who, d, note,
    refKind = ref?.kind?.code, refId = ref?.id, refPrinc = ref?.princ,
)
