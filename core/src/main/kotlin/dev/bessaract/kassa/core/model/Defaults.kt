package dev.bessaract.kassa.core.model

import dev.bessaract.kassa.core.newId

object SystemCategories {
    const val LOAN = "c_loan"
    const val GOAL = "c_goal"
}

/** Стартовые данные — как defaults() в kassa.html. */
object Defaults {
    val persons = listOf("Я", "Варя", "Ира", "Мама")

    private val expNames = listOf(
        "Продукты", "Кафе и рестораны", "Транспорт", "Дом", "Связь и подписки",
        "Здоровье", "Одежда", "Развлечения", "Дети", "Подарки", "Прочее",
    )
    private val incNames = listOf("Зарплата", "Подработка", "Прочий доход")

    fun categories(newId: () -> String = ::newId): List<Category> =
        expNames.map { Category(newId(), it, CatType.EXP) } +
            Category(SystemCategories.LOAN, "Кредиты", CatType.EXP) +
            Category(SystemCategories.GOAL, "Накопления", CatType.EXP) +
            incNames.map { Category(newId(), it, CatType.INC) }

    fun config(newId: () -> String = ::newId) = Config(
        accounts = emptyList(),
        cats = categories(newId),
        persons = persons,
        rates = emptyMap(),
        rec = emptyList(),
        loans = emptyList(),
        goals = emptyList(),
    )
}
