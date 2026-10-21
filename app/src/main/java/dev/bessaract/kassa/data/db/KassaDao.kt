package dev.bessaract.kassa.data.db

import androidx.room.Dao
import androidx.room.Query
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
}
