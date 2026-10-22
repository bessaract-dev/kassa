package dev.bessaract.kassa.ui

import dev.bessaract.kassa.core.model.Category
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Подписи для экранов: иероглифы категорий (docs/STYLE.md) и даты по-русски.

private val ru = Locale.forLanguageTag("ru")

/** Иконка категории — один иероглиф. Первые шесть — из STYLE.md, остальные подобраны по смыслу. */
private val catIcons = mapOf(
    "Продукты" to "食", "Кафе и рестораны" to "茶", "Транспорт" to "車", "Дом" to "家",
    "Развлечения" to "楽", "Связь и подписки" to "信", "Здоровье" to "医", "Одежда" to "服",
    "Дети" to "子", "Подарки" to "贈", "Прочее" to "他", "Кредиты" to "債", "Накопления" to "貯",
    "Зарплата" to "給", "Подработка" to "副", "Прочий доход" to "入",
)

fun catIcon(cat: Category?): String =
    cat?.let { catIcons[it.name] ?: if (it.type == dev.bessaract.kassa.core.model.CatType.INC) "入" else null } ?: "支"

/** Короткое название для чипа: «Кафе и рестораны» → «Кафе». */
fun catShort(cat: Category): String = when (cat.name) {
    "Кафе и рестораны" -> "Кафе"
    "Связь и подписки" -> "Связь"
    "Прочий доход" -> "Прочее"
    else -> cat.name
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMMM, EE", ru)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", ru)
private val sysFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", ru)
private val wdFmt = DateTimeFormatter.ofPattern("EE", ru)
private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", ru)

/** «21 октября, ср» — заголовок дня в истории. */
fun dayTitle(d: LocalDate): String = d.format(dayFmt)

/** Дата в форме ввода: «Сегодня», «Вчера», иначе «19 окт.». */
fun formDay(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    today.minusDays(2) -> "Позавчера"
    else -> d.format(shortFmt)
}

/** Служебная строка: «21.10.2026 // СР». */
fun sysDate(d: LocalDate): String = d.format(sysFmt) + " // " + d.format(wdFmt).uppercase(ru)

/** «Октябрь 2026». */
fun monthTitle(d: LocalDate): String = d.format(monthFmt).replaceFirstChar { it.titlecase(ru) }
