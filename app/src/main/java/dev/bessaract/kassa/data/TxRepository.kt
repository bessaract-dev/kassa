package dev.bessaract.kassa.data

import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.findCategory
import dev.bessaract.kassa.core.newId
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.data.db.KassaDao
import dev.bessaract.kassa.data.db.toEntity
import dev.bessaract.kassa.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Слой данных: справочники, план и операции месяца в виде модели из core. */
class TxRepository(private val dao: KassaDao) {
    val categories: Flow<List<Category>> = dao.categories().map { list -> list.map { it.toModel() } }
    val persons: Flow<List<String>> = dao.persons().map { list -> list.map { it.name } }
    val recurring: Flow<List<Recurring>> = dao.recurring().map { list -> list.map { it.toModel() } }

    /** month = 'ГГГГ-ММ'. */
    fun monthTx(month: String): Flow<List<Tx>> = dao.monthTx(month).map { list -> list.map { it.toModel() } }

    suspend fun save(tx: Tx) = dao.upsertTx(tx.toEntity())

    /**
     * Категория, вписанная руками: если такая уже есть (без учёта регистра) — её id,
     * иначе создаём новую в конце списка. Так она появится среди чипов в следующий раз.
     */
    suspend fun categoryId(name: String, type: CatType, existing: List<Category>): String {
        findCategory(existing, name, type)?.let { return it.id }
        val cat = Category(newId(), name.trim(), type)
        dao.insertCategory(cat.toEntity(dao.maxCategoryPosition() + 1))
        return cat.id
    }

    suspend fun delete(id: String) = dao.deleteTx(id)

    /** Новая строка плана — в конец списка, правка — на прежнее место. */
    suspend fun saveRecurring(r: Recurring) {
        val pos = dao.recurringPosition(r.id) ?: (dao.maxRecurringPosition() + 1)
        dao.upsertRecurring(r.toEntity(pos))
    }

    /** Уже проведённые операции остаются в истории, как в kassa.html. */
    suspend fun deleteRecurring(id: String) = dao.deleteRecurring(id)
}
