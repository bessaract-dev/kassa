package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.amountInput
import dev.bessaract.kassa.core.calc.loanForecast
import dev.bessaract.kassa.core.calc.loanInterest
import dev.bessaract.kassa.core.calc.loanPlan
import dev.bessaract.kassa.core.formatRub
import dev.bessaract.kassa.core.model.Loan
import dev.bessaract.kassa.core.parseAmount
import dev.bessaract.kassa.ui.LoanDraft
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.components.Chip
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.components.Segmented
import dev.bessaract.kassa.ui.components.press
import dev.bessaract.kassa.ui.formDay
import dev.bessaract.kassa.ui.loanForecastText
import dev.bessaract.kassa.ui.rateText
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.distinctButtonVariants
import java.time.LocalDate

private fun amountFilter(v: String) = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(12)

/** Ставка «14,9» → 14.9; пусто или мусор — 0. */
private fun parseRate(text: String): Double = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0

private fun rateInput(rate: Double): String =
    if (rate <= 0) "" else if (rate % 1.0 == 0.0) rate.toLong().toString() else rate.toString().replace('.', ',')

/**
 * Лист кредита или долга (editLoan в kassa.html). «С процентами» — банковский кредит: платёж и ставка обязательны.
 * «Без процентов» — долг человеку или рассрочка: ставка 0, платёж в месяц и число — по желанию.
 * original = null — новый.
 */
@Composable
fun LoanSheetContent(
    s: SpendingState,
    original: Loan?,
    onSave: (LoanDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val key = original?.id ?: "new-loan"
    var bank by rememberSaveable(key) { mutableStateOf(original?.let { it.rate > 0 } ?: true) }
    var name by rememberSaveable(key) { mutableStateOf(original?.name.orEmpty()) }
    var total by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.total) } ?: "") }
    var balance by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.balance) } ?: "") }
    var pay by rememberSaveable(key) { mutableStateOf(original?.takeIf { it.pay > 0 }?.let { amountInput(it.pay) } ?: "") }
    var rate by rememberSaveable(key) { mutableStateOf(original?.let { rateInput(it.rate) } ?: "") }
    var day by rememberSaveable(key) { mutableStateOf(original?.day?.toString() ?: "") }
    var err by remember(key) { mutableStateOf("") }
    var confirmDelete by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        // проверки как editLoan в kassa.html; у долга без процентов платёж и число необязательны
        val t = parseAmount(total)
        val b = if (balance.isBlank()) t else parseAmount(balance)
        val p = if (pay.isBlank()) 0 else parseAmount(pay)
        val r = if (bank) parseRate(rate) else 0.0
        val d = day.trim().toIntOrNull()
        when {
            name.isBlank() -> err = "Впиши название"
            original == null && b <= 0 -> err = "Введи, сколько осталось выплатить"
            b < 0 || p < 0 -> err = "Суммы — не меньше нуля"
            bank && p <= 0 -> err = "Введи платёж в месяц"
            bank && r <= 0 -> err = "Введи ставку или выбери «Без процентов»"
            r > 100 -> err = "Ставка — не больше 100% годовых"
            (p > 0 || bank) && (d == null || d !in 1..31) -> err = "Число месяца — от 1 до 31"
            else -> onSave(
                LoanDraft(
                    id = original?.id, name = name.trim(), total = t,
                    // остаток не меняли — null: в базе мог уже уменьшиться от платежа
                    balance = if (original != null && balance == amountInput(original.balance)) null else b,
                    pay = p, rate = r, day = d?.takeIf { it in 1..31 } ?: 1,
                ),
            )
        }
    }

    LaunchedEffect(key) { if (original == null) focus.requestFocus() }

    Column {
        SheetHeader(if (original == null) "Новый долг" else "Правка долга", if (bank) "债" else "借", onCancel)
        Spacer(Modifier.height(12.dp))
        Segmented(listOf(true to "С процентами", false to "Без процентов"), bank) { bank = it; err = "" }
        SheetLabel("Название")
        SheetField(
            name, if (bank) "Кредит на машину, кредитка…" else "Долг маме, рассрочка…",
            onChange = { name = it.take(40); err = "" }, onDone = { save() },
            modifier = Modifier.focusRequester(focus),
        )
        SheetLabel("Сколько взял всего, ₽")
        SheetField(total, "0", onChange = { total = amountFilter(it); err = "" }, onDone = { save() }, keyboardType = KeyboardType.Decimal)
        SheetLabel("Сколько осталось сейчас, ₽")
        SheetField(balance, "как всего", onChange = { balance = amountFilter(it); err = "" }, onDone = { save() }, keyboardType = KeyboardType.Decimal)
        SheetLabel("Платёж в месяц, ₽")
        SheetField(pay, if (bank) "0" else "необязательно", onChange = { pay = amountFilter(it); err = "" }, onDone = { save() }, keyboardType = KeyboardType.Decimal)
        if (bank) {
            SheetLabel("Ставка, % годовых")
            SheetField(rate, "14,9", onChange = { v -> rate = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6); err = "" }, onDone = { save() }, keyboardType = KeyboardType.Decimal)
        }
        SheetLabel("Какого числа платёж")
        SheetField(day, "1–31", onChange = { v -> day = v.filter { it.isDigit() }.take(2); err = "" }, onDone = { save() }, keyboardType = KeyboardType.Number)
        if (original != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("ПРОГНОЗ", style = KassaType.Label.copy(color = KassaColors.Grey), modifier = Modifier.weight(1f))
                Text(loanForecastText(loanForecast(original, s.today), short = false), style = KassaType.Small)
            }
        }
        if (err.isNotEmpty()) Text(err, style = KassaType.Small.copy(color = KassaColors.Bad), modifier = Modifier.padding(top = 8.dp))
        Text(
            if (bank) "Платёж каждый месяц заранее вычитается из «Свободно до конца месяца». Из платежа сначала гасятся проценты за месяц, остальное уменьшает долг."
            else "Без процентов: всё внесённое уменьшает долг. Если указать платёж в месяц, он вычитается из «Свободно» заранее, как обязательный. Без платежа — возвращай, когда сможешь.",
            style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(16.dp))
        val keys = listOfNotNull("Сохранить⇲⇲", if (original != null) "Удалить删除" else null)
        val v = remember(keys) { distinctButtonVariants(keys) }
        PrimaryButton("Сохранить", variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
        if (original != null) {
            // Платежи остаются в истории.
            GhostButton(if (confirmDelete) "Точно удалить?" else "Удалить", "删除", color = KassaColors.Bad, variant = v[1]) {
                if (confirmDelete) onDelete(original.id) else confirmDelete = true
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Лист «Внести» / «Вернуть»: сумма платежа (по умолчанию — что осталось внести в этом месяце) и дата. */
@Composable
fun PayLoanSheetContent(s: SpendingState, loan: Loan, onSave: (Long, LocalDate) -> Unit, onCancel: () -> Unit) {
    val bank = loan.rate > 0
    val reserve = loanPlan(loan, s.monthTx, s.today).reserve
    var amount by rememberSaveable(loan.id) { mutableStateOf(if (reserve > 0) amountInput(reserve) else "") }
    var day by rememberSaveable(loan.id) { mutableStateOf(s.today.toString()) }
    val date = LocalDate.parse(day)
    var err by remember(loan.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun save() {
        val amt = parseAmount(amount)
        if (amt <= 0) err = "Введи сумму больше нуля" else onSave(amt, date)
    }

    LaunchedEffect(loan.id) { focus.requestFocus() }

    Column {
        SheetHeader(if (bank) "Платёж" else "Вернуть", "还款", onCancel)
        Text(
            "«${loan.name}»: осталось ${formatRub(loan.balance, symbol = false)}." +
                (if (bank) " Проценты за месяц ${formatRub(Math.round(loanInterest(loan)), symbol = false)} (${rateText(loan.rate)} годовых) — они гасятся первыми, остальное уменьшает долг." else "") +
                " Сумма вычтется из «Свободно до конца месяца».",
            style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (amount.isEmpty()) Text("0", style = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.89f, color = KassaColors.Line))
                BasicTextField(
                    value = amount,
                    onValueChange = { amount = amountFilter(it); err = "" },
                    textStyle = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.89f, color = KassaColors.Text),
                    cursorBrush = SolidColor(KassaColors.Lime),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Text("₽", style = KassaType.Mid.copy(color = KassaColors.Grey))
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).height(2.dp).background(KassaColors.Line))
        if (err.isNotEmpty()) Text(err, style = KassaType.Small.copy(color = KassaColors.Bad), modifier = Modifier.padding(bottom = 8.dp))

        SheetLabel("Дата")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // только этот месяц: платёж задним числом в прошлый месяц не увидеть в Истории и не отменить
            if (date.dayOfMonth > 1) Chip("‹", false) { day = date.minusDays(1).toString() }
            Text(
                formDay(date, s.today),
                style = KassaType.Body.copy(color = if (date == s.today) KassaColors.Lime else KassaColors.Text),
                modifier = Modifier.width(120.dp).press { day = s.today.toString() },
                textAlign = TextAlign.Center,
            )
            if (date < s.today) Chip("›", false) { day = date.plusDays(1).toString() }
        }

        Spacer(Modifier.height(16.dp))
        val label = if (bank) "Внести" else "Вернуть"
        val v = remember(label) { distinctButtonVariants(listOf("$label⇲⇲")) }
        PrimaryButton(label, variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
    }
}
