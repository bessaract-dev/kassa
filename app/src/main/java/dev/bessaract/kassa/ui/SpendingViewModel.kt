package dev.bessaract.kassa.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bessaract.kassa.KassaApp
import dev.bessaract.kassa.core.backup.BackupData
import dev.bessaract.kassa.core.backup.BackupParse
import dev.bessaract.kassa.core.backup.MergePlan
import dev.bessaract.kassa.core.backup.decodeBackup
import dev.bessaract.kassa.core.backup.encodeBackup
import dev.bessaract.kassa.core.calc.Budget
import dev.bessaract.kassa.core.calc.Spending
import dev.bessaract.kassa.core.calc.budget
import dev.bessaract.kassa.core.calc.monthKey
import dev.bessaract.kassa.core.calc.recPayment
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.BASE_CURRENCY
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.model.SystemCategories
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxRef
import dev.bessaract.kassa.core.model.TxType
import dev.bessaract.kassa.core.newId
import dev.bessaract.kassa.data.TxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Всё, что нужно экранам: дата, операции месяца и сводки по ним, план, справочники для форм. */
data class SpendingState(
    val today: LocalDate,
    val spending: Spending,
    /** Категории расходов для выбора в форме: без системных «Кредиты» и «Накопления». */
    val expCats: List<Category>,
    /** Категории доходов. */
    val incCats: List<Category>,
    val allCats: Map<String, Category>,
    val persons: List<String>,
    /** Строки «Плана»: постоянные доходы и обязательные платежи. */
    val rec: List<Recurring> = emptyList(),
    /** Цели накоплений. */
    val goals: List<Goal> = emptyList(),
    /** Операции текущего месяца (для статусов плана и поиска операции при правке). */
    val monthTx: List<Tx> = emptyList(),
    val budget: Budget,
    /** Всего операций в базе (за все месяцы). */
    val txTotal: Int = 0,
) {
    fun cats(type: CatType) = if (type == CatType.EXP) expCats else incCats
}

/** Черновик операции из нижнего листа. id = null — новая. */
data class TxDraft(
    val id: String?,
    /** Трата или доход. */
    val t: TxType,
    val amt: Long,
    /** id выбранной категории; пусто, если название вписано руками. */
    val cat: String,
    /** Категория, вписанная руками; пусто, если выбран чип. */
    val newCat: String,
    val who: String,
    val d: LocalDate,
    val note: String,
)

/** Состояние листа «Настройки» → резервная копия. */
sealed interface BackupUi {
    data object Idle : BackupUi
    data object Busy : BackupUi
    /** Копия сохранена в файл. */
    data class Saved(val count: Int) : BackupUi
    /** Файл прочитан: что добавится. when — когда сделана копия, по-человечески. */
    data class Ready(val plan: MergePlan, val total: Int, val whenText: String) : BackupUi
    data class Added(val tx: Int, val rec: Int, val goals: Int = 0) : BackupUi
    data class Error(val message: String) : BackupUi
}

/** Черновик цели. id = null — новая. saved = null — накопленное в форме не меняли. by — дата «накопить к» или null. */
data class GoalDraft(
    val id: String?,
    val name: String,
    val target: Long,
    val saved: Long?,
    val by: LocalDate?,
)

/** Черновик строки плана. id = null — новая. */
data class RecDraft(
    val id: String?,
    val type: CatType,
    val name: String,
    val amt: Long,
    val day: Int,
    val cat: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TxRepository((app as KassaApp).db.dao())

    /** Сегодняшняя дата; раз в 30 секунд сверяемся, чтобы после полуночи экран сам перешёл на новый день. */
    private val today = flow {
        while (true) { emit(LocalDate.now()); delay(30_000) }
    }.distinctUntilChanged()

    val state: StateFlow<SpendingState?> = combine(
        today.flatMapLatest { d -> repo.monthTx(monthKey(d)).map { d to it } },
        repo.categories,
        repo.persons,
        combine(repo.recurring, repo.goals, ::Pair),
        repo.txCount,
    ) { (d, tx), cats, persons, (rec, goals), total ->
        SpendingState(
            today = d,
            spending = spending(tx, d),
            expCats = cats.filter { it.type == CatType.EXP && !it.isSystem },
            incCats = cats.filter { it.type == CatType.INC },
            allCats = cats.associateBy { it.id },
            persons = persons,
            rec = rec,
            goals = goals,
            monthTx = tx,
            // кредитов ещё нет — в обязательные входят план и взносы на цели с датой
            budget = budget(rec, emptyList(), goals, tx, d),
            txTotal = total,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Записать трату или доход. При правке сохраняем время внесения, счёт и связь ref исходной операции. */
    fun save(draft: TxDraft, original: Tx?) {
        viewModelScope.launch {
            val type = if (draft.t == TxType.INC) CatType.INC else CatType.EXP
            val cat = if (draft.newCat.isBlank()) draft.cat
            else repo.categoryId(draft.newCat, type, state.value?.allCats?.values?.toList().orEmpty())
            val tx = Tx(
                id = original?.id ?: newId(),
                ts = original?.ts ?: System.currentTimeMillis(),
                t = draft.t,
                amt = draft.amt,
                cur = BASE_CURRENCY,
                rub = draft.amt,
                cat = cat,
                acc = original?.acc.orEmpty(),
                to = "",
                // «на кого» — только у трат, как в kassa.html
                who = if (draft.t == TxType.EXP) draft.who else "",
                d = draft.d.toString(),
                note = draft.note.trim(),
                ref = original?.ref,
            )
            repo.save(tx)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    /**
     * «Оплатил» / «Получил»: операция на сегодня с суммой из плана и ссылкой на строку (postRec в kassa.html).
     * Если строка уже проведена в этом месяце (двойное нажатие), второй раз не записываем.
     */
    fun postRec(r: Recurring) {
        // Пока первая запись не дошла до state, второе нажатие не видит её — отсекаем по id строки.
        if (!posting.add(r.id)) return
        viewModelScope.launch {
            try {
            val s = state.value ?: return@launch
            // Дата — на момент нажатия, а не из тикера: в полночь на смене месяца тикер может отставать до 30 с.
            val now = LocalDate.now()
            if (recPayment(r.id, s.monthTx, now) != null) return@launch
            val inc = r.type == CatType.INC
            repo.save(
                Tx(
                    id = newId(), ts = System.currentTimeMillis(), t = if (inc) TxType.INC else TxType.EXP,
                    amt = r.amt, cur = BASE_CURRENCY, rub = r.amt, cat = r.cat, acc = r.acc, to = "",
                    who = if (inc) "" else s.persons.firstOrNull().orEmpty(),
                    d = now.toString(), note = r.name, ref = TxRef(RefKind.REC, r.id),
                ),
            )
            } finally {
                posting.remove(r.id)
            }
        }
    }

    private val posting = mutableSetOf<String>()

    // --- резервная копия (0.5): файл выбирает пользователь, приложение в сеть не ходит ---

    private val _backup = MutableStateFlow<BackupUi>(BackupUi.Idle)

    /** 20 МБ — с большим запасом: год ежедневных трат — около 1 МБ. */
    private val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    val backup = _backup.asStateFlow()

    private val resolver get() = getApplication<Application>().contentResolver

    /** Имя файла по умолчанию: kassa-ГГГГ-ММ-ДД.json. */
    fun backupFileName(): String = "kassa-${LocalDate.now()}.json"

    fun exportTo(uri: Uri) {
        if (_backup.value == BackupUi.Busy) return
        _backup.value = BackupUi.Busy
        viewModelScope.launch {
            _backup.value = try {
                val data = repo.snapshot()
                val text = withContext(Dispatchers.Default) { encodeBackup(data, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()) }
                withContext(Dispatchers.IO) {
                    // CreateDocument всегда создаёт новый файл, поэтому хватает "w" (режим "wt" поддерживают не все хранилища)
                    (resolver.openOutputStream(uri, "w") ?: error("нет потока")).use { it.write(text.toByteArray()) }
                }
                BackupUi.Saved(data.tx.size)
            } catch (e: Exception) {
                BackupUi.Error("Не удалось сохранить файл. Попробуй выбрать другое место.")
            }
        }
    }

    fun readBackup(uri: Uri) {
        if (_backup.value == BackupUi.Busy) return
        _backup.value = BackupUi.Busy
        viewModelScope.launch {
            _backup.value = try {
                val text = withContext(Dispatchers.IO) {
                    (resolver.openInputStream(uri) ?: error("нет потока")).use { input ->
                        // читаем не больше MAX_BACKUP_BYTES: видео или архив, выбранный по ошибке, не должен съесть память
                        val bytes = input.readNBytesCompat(MAX_BACKUP_BYTES + 1)
                        if (bytes.size > MAX_BACKUP_BYTES) null else bytes.decodeToString()
                    }
                } ?: return@launch run { _backup.value = BackupUi.Error("Файл слишком большой для копии «Кассы».") }
                when (val p = withContext(Dispatchers.Default) { decodeBackup(text) }) {
                    is BackupParse.Error -> BackupUi.Error(p.message)
                    is BackupParse.Ok -> BackupUi.Ready(repo.planMerge(p.data), p.data.tx.size + p.data.rec.size, whenText(p.data))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // в т.ч. OutOfMemoryError / StackOverflowError — сообщение вместо падения
                BackupUi.Error("Не удалось прочитать файл.")
            }
        }
    }

    fun applyBackup() {
        val ready = _backup.value as? BackupUi.Ready ?: return
        _backup.value = BackupUi.Busy
        viewModelScope.launch {
            _backup.value = try {
                repo.applyMerge(ready.plan)
                BackupUi.Added(ready.plan.tx.size, ready.plan.rec.size, ready.plan.goals.size)
            } catch (e: Exception) {
                BackupUi.Error("Не удалось добавить данные — ничего не изменилось.")
            }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun resetBackup() { if (_backup.value != BackupUi.Busy) _backup.value = BackupUi.Idle }

    private fun whenText(d: BackupData): String = d.exported?.let {
        runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))) }.getOrNull()
    } ?: "неизвестной даты"

    fun saveRec(d: RecDraft) {
        viewModelScope.launch {
            val old = state.value?.rec?.find { it.id == d.id }
            repo.saveRecurring(Recurring(d.id ?: newId(), d.type, d.name.trim(), d.amt, d.day, d.cat, old?.acc.orEmpty()))
        }
    }

    fun deleteRec(id: String) {
        viewModelScope.launch { repo.deleteRecurring(id) }
    }

    /** При правке накопленное берётся из формы: так можно поправить его руками. */
    fun saveGoal(d: GoalDraft) {
        viewModelScope.launch {
            val old = state.value?.goals?.find { it.id == d.id }
            val g = Goal(d.id ?: newId(), d.name.trim(), d.target, d.saved ?: old?.saved ?: 0, old?.monthly ?: 0, d.by?.toString().orEmpty())
            // накопленное не правили — его не пишем: «Отложить» мог успеть изменить его в базе
            if (old != null && d.saved == null) repo.updateGoalInfo(g) else repo.saveGoal(g)
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { repo.deleteGoal(id) }
    }

    /** «Отложить»: расход в системной категории «Накопления» со ссылкой на цель (topGoal в kassa.html). */
    fun contribute(goalId: String, amt: Long) {
        viewModelScope.launch {
            val s = state.value ?: return@launch
            val g = s.goals.find { it.id == goalId } ?: return@launch
            repo.contribute(
                Tx(
                    id = newId(), ts = System.currentTimeMillis(), t = TxType.EXP, amt = amt, cur = BASE_CURRENCY, rub = amt,
                    cat = SystemCategories.GOAL, acc = "", to = "", who = s.persons.firstOrNull().orEmpty(),
                    d = LocalDate.now().toString(), note = g.name, ref = TxRef(RefKind.GOAL, g.id),
                ),
                g.id,
            )
        }
    }
}
