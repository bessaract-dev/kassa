package dev.bessaract.kassa.data

import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.findExpCategory
import dev.bessaract.kassa.core.newId
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.data.db.KassaDao
import dev.bessaract.kassa.data.db.toEntity
import dev.bessaract.kassa.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Слой данных для MVP: справочники и операции месяца в виде модели из core. */
class TxRepository(private val dao: KassaDao) {
    val categories: Flow<List<Category>> = dao.categories().map { list -> list.map { it.toModel() } }
    val persons: Flow<List<String>> = dao.persons().map { list -> list.map { it.name } }

    /** month = 'ГГГГ-ММ'. */
    fun monthTx(month: String): Flow<List<Tx>> = dao.monthTx(month).map { list -> list.map { it.toModel() } }

    suspend fun save(tx: Tx) = dao.upsertTx(tx.toEntity())

    /**
     * Категория расхода, вписанная руками: если такая уже есть (без учёта регистра) — её id,
     * иначе создаём новую в конце списка. Так она появится среди чипов в следующий раз.
     */
    suspend fun expCategoryId(name: String, existing: List<Category>): String {
        findExpCategory(existing, name)?.let { return it.id }
        val cat = Category(newId(), name.trim(), CatType.EXP)
        dao.insertCategory(cat.toEntity(dao.maxCategoryPosition() + 1))
        return cat.id
    }

    suspend fun delete(id: String) = dao.deleteTx(id)
}
