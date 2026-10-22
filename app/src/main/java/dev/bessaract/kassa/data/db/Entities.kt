package dev.bessaract.kassa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Таблицы Room по модели из CLAUDE.md. Суммы — копейки (INTEGER), коды типов — текстом, как в kassa.html.
// position хранит порядок списка (в вебе это порядок элементов массива).

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cur: String,
    val init: Long,
    val position: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 'exp' | 'inc' */
    val type: String,
    @ColumnInfo(name = "limit_amt") val limit: Long,
    val position: Int,
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Порядок в списке; первый — по умолчанию. */
    val position: Int,
)

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val cur: String,
    /** Рублей за единицу валюты. */
    val rate: Double,
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: String,
    /** 'exp' | 'inc' */
    val type: String,
    val name: String,
    val amt: Long,
    val day: Int,
    val cat: String,
    val acc: String,
    val position: Int,
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val total: Long,
    val balance: Long,
    val pay: Long,
    val rate: Double,
    val day: Int,
    val position: Int,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val target: Long,
    val saved: Long,
    val monthly: Long,
    val position: Int,
    /** Накопить к дате ГГГГ-ММ-ДД, пусто — без срока. Появилось в версии базы 2. */
    @ColumnInfo(name = "by_date", defaultValue = "") val by: String = "",
)

/** Сумма операций одного вида по паре счетов (для балансов счетов). */
data class AccMoveRow(
    val t: String,
    val acc: String,
    @ColumnInfo(name = "to_acc") val to: String,
    val rub: Long,
)

/** Операция. ref хранится тремя полями: ref_kind ('rec'|'loan'|'goal' или null), ref_id, ref_princ. */
@Entity(
    tableName = "tx",
    indices = [Index("d"), Index("ref_kind", "ref_id")],
)
data class TxEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    /** 'exp' | 'inc' | 'tr' */
    val t: String,
    val amt: Long,
    val cur: String,
    val rub: Long,
    val cat: String,
    val acc: String,
    @ColumnInfo(name = "to_acc") val to: String,
    val who: String,
    /** ГГГГ-ММ-ДД */
    val d: String,
    val note: String,
    @ColumnInfo(name = "ref_kind") val refKind: String?,
    @ColumnInfo(name = "ref_id") val refId: String?,
    @ColumnInfo(name = "ref_princ") val refPrinc: Long?,
)
