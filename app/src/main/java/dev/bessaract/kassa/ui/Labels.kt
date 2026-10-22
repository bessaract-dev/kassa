package dev.bessaract.kassa.ui

import dev.bessaract.kassa.core.model.Category
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Подписи для экранов: иероглифы категорий (docs/STYLE.md) и даты по-русски.

private val ru = Locale.forLanguageTag("ru")

/** Иконка категории — один иероглиф. Первые шесть — из STYLE.md, остальные подобраны по смыслу. */
private val catIcons = mapOf(
    "Продукты" to "食", "Кафе и рестораны" to "茶", "Транспорт" to "车", "Дом" to "家",
    "Развлечения" to "乐", "Связь и подписки" to "讯", "Здоровье" to "医", "Одежда" to "服",
    "Дети" to "童", "Подарки" to "礼", "Прочее" to "他", "Кредиты" to "债", "Накопления" to "存",
    "Зарплата" to "薪", "Подработка" to "副", "Прочий доход" to "收",
)

fun catIcon(cat: Category?): String =
    cat?.let { catIcons[it.name] ?: if (it.type == dev.bessaract.kassa.core.model.CatType.INC) "收" else null } ?: "支"

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

/** «30.12.2026» — срок цели. */
fun shortDate(d: LocalDate): String = d.format(sysFmt)

/** Служебная строка: «21.10.2026 // СР». */
fun sysDate(d: LocalDate): String = d.format(sysFmt) + " // " + d.format(wdFmt).uppercase(ru)

/** «Октябрь 2026». */
fun monthTitle(d: LocalDate): String = d.format(monthFmt).replaceFirstChar { it.titlecase(ru) }

private val monthShortFmt = DateTimeFormatter.ofPattern("LLL yyyy", ru)
private val monthLongFmt = DateTimeFormatter.ofPattern("LLLL yyyy", ru)

/** Прогноз закрытия кредита словами. short — для строки списка («до янв. 2029»), без графика — пусто. */
fun loanForecastText(f: dev.bessaract.kassa.core.calc.LoanForecast, short: Boolean): String = when (f) {
    dev.bessaract.kassa.core.calc.LoanForecast.Closed -> if (short) "" else "закрыт"
    dev.bessaract.kassa.core.calc.LoanForecast.NoSchedule -> if (short) "" else "без графика"
    dev.bessaract.kassa.core.calc.LoanForecast.NotCovering -> if (short) "не покрывает %" else "платёж не покрывает проценты"
    dev.bessaract.kassa.core.calc.LoanForecast.TooLong -> "больше 50 лет"
    is dev.bessaract.kassa.core.calc.LoanForecast.In ->
        if (short) "до " + f.at.format(monthShortFmt) else "закроется " + f.at.format(monthLongFmt) + " (${f.months} мес.)"
}

/** Ставка по-русски: 14,9%; без процентов — «без %». */
fun rateText(rate: Double): String =
    if (rate <= 0) "без\u00A0%" else (if (rate % 1.0 == 0.0) rate.toLong().toString() else rate.toString().replace('.', ',')) + "%"
