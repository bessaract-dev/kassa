package dev.bessaract.kassa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bessaract.kassa.KassaApp
import dev.bessaract.kassa.core.calc.Spending
import dev.bessaract.kassa.core.calc.monthKey
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.BASE_CURRENCY
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Category
import dev.bessaract.kassa.core.model.Tx
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

/** Всё, что нужно экранам MVP: дата, сводка трат за месяц и справочники для формы. */
data class SpendingState(
    val today: LocalDate,
    val spending: Spending,
    /** Категории расходов для выбора в форме: без системных «Кредиты» и «Накопления». */
    val expCats: List<Category>,
    val allCats: Map<String, Category>,
    val persons: List<String>,
)

/** Черновик траты из нижнего листа. id = null — новая трата. */
data class TxDraft(
    val id: String?,
    val amt: Long,
    /** id выбранной категории; пусто, если название вписано руками. */
    val cat: String,
    /** Категория, вписанная руками; пусто, если выбран чип. */
    val newCat: String,
    val who: String,
    val d: LocalDate,
    val note: String,
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
    ) { (d, tx), cats, persons ->
        SpendingState(
            today = d,
            spending = spending(tx, d),
            expCats = cats.filter { it.type == CatType.EXP && !it.isSystem },
            allCats = cats.associateBy { it.id },
            persons = persons,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Записать трату. При правке сохраняем время внесения, счёт и связь ref исходной операции. */
    fun save(draft: TxDraft, original: Tx?) {
        viewModelScope.launch {
            val cat = if (draft.newCat.isBlank()) draft.cat
            else repo.expCategoryId(draft.newCat, state.value?.allCats?.values?.toList().orEmpty())
            val tx = Tx(
                id = original?.id ?: newId(),
                ts = original?.ts ?: System.currentTimeMillis(),
                t = TxType.EXP,
                amt = draft.amt,
                cur = BASE_CURRENCY,
                rub = draft.amt,
                cat = cat,
                acc = original?.acc.orEmpty(),
                to = "",
                who = draft.who,
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
}
