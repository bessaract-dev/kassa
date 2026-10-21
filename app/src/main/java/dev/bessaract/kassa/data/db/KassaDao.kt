package dev.bessaract.kassa.data.db

import androidx.room.Dao
import androidx.room.Query
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
}
