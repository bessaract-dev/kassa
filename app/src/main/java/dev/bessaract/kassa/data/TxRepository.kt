package dev.bessaract.kassa.data

import dev.bessaract.kassa.core.backup.BackupData
import dev.bessaract.kassa.core.backup.MergePlan
import dev.bessaract.kassa.core.backup.mergePlan
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.findCategory
import dev.bessaract.kassa.core.newId
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.data.db.KassaDao
import dev.bessaract.kassa.data.db.PersonEntity
import dev.bessaract.kassa.data.db.toEntity
import dev.bessaract.kassa.data.db.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

    val txCount: Flow<Int> = dao.txCount()

    /** Все данные для резервной копии. */
    suspend fun snapshot(): BackupData = BackupData(
        accounts = dao.allAccounts().map { it.toModel() },
        cats = dao.allCategories().map { it.toModel() },
        persons = dao.allPersons().map { it.name },
        rec = dao.allRecurring().map { it.toModel() },
        loans = dao.allLoans().map { it.toModel() },
        goals = dao.allGoals().map { it.toModel() },
        tx = dao.allTx().map { it.toModel() },
    )

    /** Что добавится из копии в текущую базу. */
    suspend fun planMerge(data: BackupData): MergePlan {
        val cats = dao.allCategories().map { it.toModel() }
        val persons = dao.allPersons().map { it.name }
        val rec = dao.allRecurring().map { it.toModel() }
        val txIds = dao.allTxIds().toHashSet()
        return withContext(Dispatchers.Default) { mergePlan(data, cats, persons, rec, txIds) }
    }

    /** Добавить недостающее из копии — в конец списков. */
    suspend fun applyMerge(p: MergePlan) {
        val catPos = dao.maxCategoryPosition() + 1
        val personPos = dao.maxPersonPosition() + 1
        val recPos = dao.maxRecurringPosition() + 1
        dao.insertBackup(
            cats = p.cats.mapIndexed { i, c -> c.toEntity(catPos + i) },
            persons = p.persons.mapIndexed { i, n -> PersonEntity(name = n, position = personPos + i) },
            rec = p.rec.mapIndexed { i, r -> r.toEntity(recPos + i) },
            tx = p.tx.map { it.toEntity() },
        )
    }
}
