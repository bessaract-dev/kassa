package dev.bessaract.kassa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bessaract.kassa.KassaApp
import dev.bessaract.kassa.core.calc.Budget
import dev.bessaract.kassa.core.calc.Spending
import dev.bessaract.kassa.core.calc.budget
import dev.bessaract.kassa.core.calc.monthKey
import dev.bessaract.kassa.core.calc.recPayment
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.BASE_CURRENCY
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxRef
import dev.bessaract.kassa.core.model.TxType
import dev.bessaract.kassa.core.newId
import dev.bessaract.kassa.data.TxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate

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
    /** Операции текущего месяца (для статусов плана и поиска операции при правке). */
    val monthTx: List<Tx> = emptyList(),
    val budget: Budget,
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
        repo.recurring,
    ) { (d, tx), cats, persons, rec ->
        SpendingState(
            today = d,
            spending = spending(tx, d),
            expCats = cats.filter { it.type == CatType.EXP && !it.isSystem },
            incCats = cats.filter { it.type == CatType.INC },
            allCats = cats.associateBy { it.id },
            persons = persons,
            rec = rec,
            monthTx = tx,
            // кредитов и целей ещё нет (0.6–0.7) — в обязательные пока входит только план
            budget = budget(rec, emptyList(), emptyList(), tx, d),
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

    fun saveRec(d: RecDraft) {
        viewModelScope.launch {
            val old = state.value?.rec?.find { it.id == d.id }
            repo.saveRecurring(Recurring(d.id ?: newId(), d.type, d.name.trim(), d.amt, d.day, d.cat, old?.acc.orEmpty()))
        }
    }

    fun deleteRec(id: String) {
        viewModelScope.launch { repo.deleteRecurring(id) }
    }
}
