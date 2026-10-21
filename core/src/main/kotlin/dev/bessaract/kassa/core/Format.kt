package dev.bessaract.kassa.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

private val NUMBER_PREFIX = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)")

private const val NBSP = ' '

/**
 * Сумма из поля ввода → копейки. Как num() в kassa.html: пробелы убираются, запятая = точка.
 * Пустая или нечисловая строка → 0. Дробь округляется до копейки (половина — от нуля).
 */
fun parseAmount(input: String): Long {
    // Как parseFloat: берём самое длинное число в начале строки, хвост отбрасываем («1,5,» → 1,5).
    val s = input.filterNot { it.isWhitespace() }.replaceFirst(',', '.')
    val n = NUMBER_PREFIX.find(s)?.value?.toBigDecimalOrNull() ?: return 0
    return n.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** Копейки → «1 234 ₽», «−860 ₽». Как fmt() в kassa.html для рубля: целые рубли, разряды через пробел. */
fun formatRub(kopecks: Long, symbol: Boolean = true): String {
    val rub = (abs(kopecks) + 50) / 100
    val digits = rub.toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    val sign = if (kopecks < 0 && rub != 0L) "−" else ""
    return sign + digits + if (symbol) "$NBSP₽" else ""
}

/** Копейки → строка для поля ввода: «1234», «1234,5», «0,05». Без разрядов, чтобы её можно было править. */
fun amountInput(kopecks: Long): String =
    BigDecimal.valueOf(kopecks, 2).stripTrailingZeros().toPlainString().replace('.', ',')
