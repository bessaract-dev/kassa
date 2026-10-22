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

    @Query("SELECT * FROM tx WHERE id = :id")
    suspend fun txById(id: String): TxEntity?

    /** Взнос в цель: операция и накопленное меняются вместе. */
    @Transaction
    suspend fun contribute(tx: TxEntity, goalId: String) {
        insertTx(listOf(tx)); addGoalSaved(goalId, tx.amt)
    }

    /** Удаление операции; если это взнос в цель — накопленное уменьшается (как в kassa.html). */
    @Transaction
    suspend fun deleteTxAndUndo(id: String) {
        val t = txById(id) ?: return
        if (t.refKind == "goal" && t.refId != null) addGoalSaved(t.refId, -t.amt)
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

    @Query("SELECT COALESCE(MAX(position), -1) FROM persons")
    suspend fun maxPersonPosition(): Int

    // IGNORE: если запись с таким id уже есть, её не трогаем (восстановление — объединением).
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPersons(list: List<PersonEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRecurring(list: List<RecurringEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTx(list: List<TxEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGoals(list: List<GoalEntity>)

    /** Добавить всё из копии одной транзакцией: либо всё, либо ничего. */
    @Transaction
    suspend fun insertBackup(cats: List<CategoryEntity>, persons: List<PersonEntity>, rec: List<RecurringEntity>, tx: List<TxEntity>, goals: List<GoalEntity>) {
        insertCategories(cats); insertPersons(persons); insertRecurring(rec); insertGoals(goals); insertTx(tx)
    }
}
