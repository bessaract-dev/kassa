package dev.bessaract.kassa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KassaDao {
    @Query("SELECT * FROM accounts ORDER BY position")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY position")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM persons ORDER BY position")
    fun persons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM rates ORDER BY cur")
    fun rates(): Flow<List<RateEntity>>

    @Query("SELECT * FROM recurring ORDER BY position")
    fun recurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM loans ORDER BY position")
    fun loans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM goals ORDER BY position")
    fun goals(): Flow<List<GoalEntity>>

    /** Операции месяца: month = 'ГГГГ-ММ'. Диапазон, а не LIKE, — чтобы работал индекс по d. */
    @Query("SELECT * FROM tx WHERE d BETWEEN :month || '-01' AND :month || '-31' ORDER BY d DESC, ts DESC")
    fun monthTx(month: String): Flow<List<TxEntity>>

    /** Движение денег по счетам за всё время: суммы по виду операции и паре счетов. */
    @Query("SELECT t, acc, to_acc, SUM(rub) AS rub FROM tx WHERE acc != '' OR to_acc != '' GROUP BY t, acc, to_acc")
    fun accMoves(): Flow<List<AccMoveRow>>

    @Query("SELECT t, acc, to_acc, SUM(rub) AS rub FROM tx WHERE acc != '' OR to_acc != '' GROUP BY t, acc, to_acc")
    suspend fun accMovesNow(): List<AccMoveRow>

    @Upsert
    suspend fun upsertAccount(acc: AccountEntity)

    @Query("SELECT position FROM accounts WHERE id = :id")
    suspend fun accountPosition(id: String): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM accounts")
    suspend fun maxAccountPosition(): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("SELECT COUNT(*) FROM tx")
    fun txCount(): Flow<Int>

    @androidx.room.Insert
    suspend fun insertCategory(cat: CategoryEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxCategoryPosition(): Int

    /** Новая операция или правка существующей (по id). */
    @Upsert
    suspend fun upsertTx(tx: TxEntity)

    @Query("DELETE FROM tx WHERE id = :id")
    suspend fun deleteTx(id: String)

    @Upsert
    suspend fun upsertRecurring(rec: RecurringEntity)

    @Query("SELECT position FROM recurring WHERE id = :id")
    suspend fun recurringPosition(id: String): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM recurring")
    suspend fun maxRecurringPosition(): Int

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteRecurring(id: String)

    @Upsert
    suspend fun upsertGoal(goal: GoalEntity)

    @Query("SELECT position FROM goals WHERE id = :id")
    suspend fun goalPosition(id: String): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM goals")
    suspend fun maxGoalPosition(): Int

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    /** Изменить накопленное; меньше нуля не опускаем. */
    @Query("UPDATE goals SET saved = MAX(0, saved + :delta) WHERE id = :id")
    suspend fun addGoalSaved(id: String, delta: Long)

    @Upsert
    suspend fun upsertLoan(loan: LoanEntity)

    @Query("SELECT position FROM loans WHERE id = :id")
    suspend fun loanPosition(id: String): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM loans")
    suspend fun maxLoanPosition(): Int

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: String)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun loanById(id: String): LoanEntity?

    /** Изменить остаток кредита; меньше нуля не опускаем. */
    @Query("UPDATE loans SET balance = MAX(0, balance + :delta) WHERE id = :id")
    suspend fun addLoanBalance(id: String, delta: Long)

    /** Правка кредита без остатка: платёж, внесённый пока открыта форма, не затрётся. */
    @Query("UPDATE loans SET name = :name, total = MAX(:total, balance), pay = :pay, rate = :rate, day = :day WHERE id = :id")
    suspend fun updateLoanInfo(id: String, name: String, total: Long, pay: Long, rate: Double, day: Int)

    /**
     * Платёж по кредиту: погашенная часть считается от остатка в базе (а не на экране) — сначала проценты,
     * остальное в долг (kassa.html). Возвращает погашенное или null, если кредита уже нет.
     */
    @Transaction
    suspend fun payLoan(tx: TxEntity, loanId: String, principal: (LoanEntity) -> Long): Long? {
        val l = loanById(loanId) ?: return null
        val princ = principal(l)
        insertTx(listOf(tx.copy(refPrinc = princ)))
        addLoanBalance(loanId, -princ)
        return princ
    }

    @Query("SELECT * FROM tx WHERE id = :id")
    suspend fun txById(id: String): TxEntity?

    /** Взнос в цель: операция и накопленное меняются вместе. */
    @Transaction
    suspend fun contribute(tx: TxEntity, goalId: String) {
        insertTx(listOf(tx)); addGoalSaved(goalId, tx.rub)
    }

    /** Удаление операции; если это взнос в цель — накопленное уменьшается (как в kassa.html). */
    @Transaction
    suspend fun deleteTxAndUndo(id: String) {
        val t = txById(id) ?: return
        if (t.refKind == "goal" && t.refId != null) addGoalSaved(t.refId, -t.rub)
        // платёж по кредиту: погашенная часть возвращается в остаток
        if (t.refKind == "loan" && t.refId != null) addLoanBalance(t.refId, t.refPrinc ?: 0L)
        deleteTx(id)
    }

    // --- резервная копия: снимок всех данных и добавление недостающего ---

    @Query("SELECT * FROM accounts ORDER BY position") suspend fun allAccounts(): List<AccountEntity>
    @Query("SELECT * FROM categories ORDER BY position") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM persons ORDER BY position") suspend fun allPersons(): List<PersonEntity>
    @Query("SELECT * FROM recurring ORDER BY position") suspend fun allRecurring(): List<RecurringEntity>
    @Query("SELECT * FROM loans ORDER BY position") suspend fun allLoans(): List<LoanEntity>
    @Query("SELECT * FROM goals ORDER BY position") suspend fun allGoals(): List<GoalEntity>
    @Query("SELECT * FROM tx ORDER BY d, ts") suspend fun allTx(): List<TxEntity>
    @Query("SELECT id FROM tx") suspend fun allTxIds(): List<String>
    @Query("SELECT * FROM tx WHERE ref_kind = 'loan'") suspend fun loanTx(): List<TxEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM persons")
    suspend fun maxPersonPosition(): Int

    // IGNORE: если запись с таким id уже есть, её не трогаем (восстановление — объединением).
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPersons(list: List<PersonEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRecurring(list: List<RecurringEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTx(list: List<TxEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGoals(list: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLoans(list: List<LoanEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccounts(list: List<AccountEntity>)

    /** Добавить всё из копии одной транзакцией: либо всё, либо ничего. */
    @Transaction
    suspend fun insertBackup(
        cats: List<CategoryEntity>, persons: List<PersonEntity>, rec: List<RecurringEntity>, tx: List<TxEntity>,
        goals: List<GoalEntity>, goalTopUps: Map<String, Long>,
        loans: List<LoanEntity> = emptyList(), loanPayoffs: Map<String, Long> = emptyMap(),
        accounts: List<AccountEntity> = emptyList(),
    ) {
        insertAccounts(accounts)
        insertCategories(cats); insertPersons(persons); insertRecurring(rec); insertGoals(goals); insertLoans(loans); insertTx(tx)
        goalTopUps.forEach { (id, delta) -> addGoalSaved(id, delta) }
        loanPayoffs.forEach { (id, princ) -> addLoanBalance(id, -princ) }
    }

    /** Правка цели без накопленного: взнос, сделанный пока открыта форма, не затрётся. */
    @Query("UPDATE goals SET name = :name, target = :target, by_date = :by WHERE id = :id")
    suspend fun updateGoalInfo(id: String, name: String, target: Long, by: String)
}
