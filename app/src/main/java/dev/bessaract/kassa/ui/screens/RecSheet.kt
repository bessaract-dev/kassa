package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.amountInput
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.parseAmount
import dev.bessaract.kassa.ui.RecDraft
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.catIcon
import dev.bessaract.kassa.ui.catShort
import dev.bessaract.kassa.ui.components.Chip
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.distinctButtonVariants

/**
 * Лист строки «Плана»: постоянный доход или обязательный платёж — название, сумма в месяц, число, категория.
 * original = null — новая строка типа [type].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecSheetContent(
    s: SpendingState,
    original: Recurring?,
    type: CatType,
    onSave: (RecDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val key = original?.id ?: "new-${type.code}"
    val inc = type == CatType.INC
    val cats = s.cats(type)
    var name by rememberSaveable(key) { mutableStateOf(original?.name.orEmpty()) }
    var amount by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.amt) } ?: "") }
    var day by rememberSaveable(key) { mutableStateOf(original?.day?.toString().orEmpty()) }
    // Новый платёж — по умолчанию «Дом» (аренда, коммуналка), доход — первая категория («Зарплата»).
    var cat by rememberSaveable(key) {
        mutableStateOf(original?.cat ?: (if (inc) cats.firstOrNull() else cats.find { it.name == "Дом" } ?: cats.firstOrNull())?.id.orEmpty())
    }
    var err by remember(key) { mutableStateOf("") }
    var confirmDelete by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        val amt = parseAmount(amount)
        val d = day.trim().toIntOrNull()
        when {
            name.isBlank() -> err = "Впиши название"
            amt <= 0 -> err = "Введи сумму больше нуля"
            d == null || d !in 1..31 -> err = "Число месяца — от 1 до 31"
            cat.isEmpty() -> err = "Выбери категорию"
            else -> onSave(RecDraft(original?.id, type, name.trim(), amt, d, cat))
        }
    }

    LaunchedEffect(key) { if (original == null) focus.requestFocus() }

    Column {
        val title = (if (original == null) "Новый " else "Правка: ") + if (inc) "доход" else "платёж"
        SheetHeader(title, if (inc) "收入" else "付款", onCancel)

        SheetLabel("Название")
        SheetField(
            name, if (inc) "Зарплата, аванс…" else "Аренда, интернет…",
            onChange = { name = it.take(40); err = "" }, onDone = { save() },
            modifier = Modifier.focusRequester(focus),
        )
        SheetLabel("Сумма в месяц, ₽")
        SheetField(
            amount, "0",
            onChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(12); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Decimal,
        )
        SheetLabel("Какого числа")
        SheetField(
            day, "1–31",
            onChange = { v -> day = v.filter { it.isDigit() }.take(2); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Number,
        )
        if (err.isNotEmpty()) Text(err, style = KassaType.Small.copy(color = KassaColors.Bad), modifier = Modifier.padding(top = 8.dp))

        SheetLabel("Категория")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> Chip("${catIcon(c)} ${catShort(c)}", c.id == cat) { cat = c.id; err = "" } }
        }
        if (inc) {
            Text(
                "Зарплата два раза в месяц — это два дохода: например «Аванс» 10-го и «Зарплата» 25-го.",
                style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        val keys = listOfNotNull("Сохранить⇲⇲", if (original != null) "Удалить删除" else null)
        val v = remember(keys) { distinctButtonVariants(keys) }
        PrimaryButton("Сохранить", variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
        if (original != null) {
            // Уже проведённые операции остаются в истории.
            GhostButton(if (confirmDelete) "Точно удалить?" else "Удалить", "删除", color = KassaColors.Bad, variant = v[1]) {
                if (confirmDelete) onDelete(original.id) else confirmDelete = true
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
