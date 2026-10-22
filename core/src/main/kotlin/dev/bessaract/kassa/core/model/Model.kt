package dev.bessaract.kassa.core.model

// Модель данных 1:1 с kassa.html (config + операции).
// Все суммы — в копейках (Long): 1 ₽ = 100. Для валютных счетов — в сотых долях валюты.

const val BASE_CURRENCY = "RUB"

enum class CatType(val code: String) { EXP("exp"), INC("inc") }

enum class TxType(val code: String) { EXP("exp"), INC("inc"), TR("tr") }

enum class RefKind(val code: String) { REC("rec"), LOAN("loan"), GOAL("goal") }

data class Account(
    val id: String,
    val name: String,
    val cur: String,
    /** Начальный остаток, в копейках валюты счёта. */
    val init: Long,
)

data class Category(
    val id: String,
    val name: String,
    val type: CatType,
    /** Лимит в месяц, копейки; 0 = без лимита. */
    val limit: Long = 0,
) {
    val isSystem: Boolean get() = id == SystemCategories.LOAN || id == SystemCategories.GOAL
}

/** Постоянный доход или расход из «Плана». */
data class Recurring(
    val id: String,
    val type: CatType,
    val name: String,
    val amt: Long,
    /** Число месяца, 1–31. */
    val day: Int,
    val cat: String,
    val acc: String,
)

data class Loan(
    val id: String,
    val name: String,
    val total: Long,
    val balance: Long,
    val pay: Long,
    /** Ставка, % годовых. */
    val rate: Double,
    val day: Int,
)

data class Goal(
    val id: String,
    val name: String,
    val target: Long,
    val saved: Long,
    /** Взнос в месяц из kassa.html; в расчётах не участвует (решение 2026-10-22: взнос считается от даты [by]). */
    val monthly: Long = 0,
    /** Накопить к дате ГГГГ-ММ-ДД; пусто — цель-копилка без срока. */
    val by: String = "",
)

/** Связь операции с планом, кредитом или целью. princ — погашенный основной долг (только для кредита). */
data class TxRef(
    val kind: RefKind,
    val id: String,
    val princ: Long? = null,
)

data class Tx(
    val id: String,
    /** Время внесения, мс с 1970 (для сортировки внутри дня). */
    val ts: Long,
    val t: TxType,
    /** Сумма в валюте операции, копейки. */
    val amt: Long,
    val cur: String,
    /** Сумма в рублях по курсу на день внесения, копейки. */
    val rub: Long,
    val cat: String,
    val acc: String,
    /** Счёт назначения для перевода, иначе пусто. */
    val to: String,
    /** «На кого» — только для расходов, иначе пусто. */
    val who: String,
    /** Дата ГГГГ-ММ-ДД. */
    val d: String,
    val note: String,
    val ref: TxRef? = null,
)

data class Config(
    val accounts: List<Account>,
    val cats: List<Category>,
    val persons: List<String>,
    /** Курс: сколько рублей за единицу валюты. */
    val rates: Map<String, Double>,
    val rec: List<Recurring>,
    val loans: List<Loan>,
    val goals: List<Goal>,
)
