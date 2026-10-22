package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.amountInput
import dev.bessaract.kassa.core.formatRub
import dev.bessaract.kassa.core.model.Goal
import dev.bessaract.kassa.core.parseAmount
import dev.bessaract.kassa.ui.GoalDraft
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.shortDate
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.distinctButtonVariants
import java.time.LocalDate

/** «30.12.2026» или «30.12.26» → дата; иначе null. */
private fun parseDate(text: String): LocalDate? {
    val p = text.trim().split('.', '/', '-')
    if (p.size != 3) return null
    val d = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    var y = p[2].toIntOrNull() ?: return null
    if (p[2].length == 2) y += 2000
    if (y !in 2000..2100) return null
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

/**
 * Лист цели: название, сумма, сколько уже накоплено и необязательная дата «накопить к».
 * original = null — новая цель.
 */
@Composable
fun GoalSheetContent(
    s: SpendingState,
    original: Goal?,
    onSave: (GoalDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val key = original?.id ?: "new-goal"
    var name by rememberSaveable(key) { mutableStateOf(original?.name.orEmpty()) }
    var target by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.target) } ?: "") }
    var saved by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.saved) } ?: "") }
    var by by rememberSaveable(key) {
        mutableStateOf(original?.by?.takeIf { it.isNotEmpty() }?.let { runCatching { shortDate(LocalDate.parse(it)) }.getOrNull() }.orEmpty())
    }
    var err by remember(key) { mutableStateOf("") }
    var confirmDelete by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        val t = parseAmount(target)
        val sv = if (saved.isBlank()) 0 else parseAmount(saved)
        val date = if (by.isBlank()) null else parseDate(by)
        when {
            name.isBlank() -> err = "Впиши, на что копишь"
            t <= 0 -> err = "Введи сумму цели больше нуля"
            sv < 0 -> err = "Накоплено — не меньше нуля"
            by.isNotBlank() && date == null -> err = "Дата в виде 30.12.2026"
            date != null && date < s.today && date.toString() != original?.by -> err = "Дата уже прошла"
            // накопленное не меняли — null: в базе могло уже прибавиться от «Отложить»
            else -> onSave(GoalDraft(original?.id, name.trim(), t, if (original != null && saved == amountInput(original.saved)) null else sv, date))
        }
    }

    LaunchedEffect(key) { if (original == null) focus.requestFocus() }

    Column {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text((if (original == null) "Новая цель" else "Правка цели").uppercase(), style = KassaType.SheetTitle, modifier = Modifier.weight(1f))
            Text("目标", style = KassaType.cjk(14).copy(color = KassaColors.Lime))
        }
        SheetLabel("Цель")
        SheetField(
            name, "Накопить 100к, путешествие в Китай…",
            onChange = { name = it.take(40); err = "" }, onDone = { save() },
            modifier = Modifier.focusRequester(focus),
        )
        SheetLabel("Сумма цели, ₽")
        SheetField(
            target, "100 000",
            onChange = { v -> target = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(12); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Decimal,
        )
        SheetLabel("Уже накоплено, ₽")
        SheetField(
            saved, "0",
            onChange = { v -> saved = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(12); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Decimal,
        )
        SheetLabel("Накопить к дате")
        SheetField(
            by, "30.12.2026 — необязательно",
            onChange = { v -> by = v.filter { it.isDigit() || it == '.' }.take(10); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Number,
        )
        if (err.isNotEmpty()) Text(err, style = KassaType.Small.copy(color = KassaColors.Bad), modifier = Modifier.padding(top = 8.dp))
        Text(
            "Без даты цель — копилка: на лимит влияет только то, что отложил. С датой приложение делит недостающее на месяцы до неё " +
                "и заранее вычитает взнос месяца из «Свободно до конца месяца», так что лимит на день уже учитывает цель.",
            style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(16.dp))
        val keys = listOfNotNull("Сохранить⇲⇲", if (original != null) "Удалить删除" else null, "Отмена取消")
        val v = remember(keys) { distinctButtonVariants(keys) }
        PrimaryButton("Сохранить", variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
        if (original != null) {
            // Взносы остаются в истории.
            GhostButton(if (confirmDelete) "Точно удалить?" else "Удалить", "删除", color = KassaColors.Bad, variant = v[1]) {
                if (confirmDelete) onDelete(original.id) else confirmDelete = true
            }
            Spacer(Modifier.height(8.dp))
        }
        GhostButton("Отмена", "取消", variant = v.last(), onClick = onCancel)
    }
}

/** Лист «Отложить»: сумма взноса в цель. */
@Composable
fun TopGoalSheetContent(goal: Goal, onSave: (Long) -> Unit, onCancel: () -> Unit) {
    var amount by rememberSaveable(goal.id) { mutableStateOf("") }
    var err by remember(goal.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun save() {
        val amt = parseAmount(amount)
        if (amt <= 0) err = "Введи сумму больше нуля" else onSave(amt)
    }

    LaunchedEffect(goal.id) { focus.requestFocus() }

    Column {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("ОТЛОЖИТЬ", style = KassaType.SheetTitle, modifier = Modifier.weight(1f))
            Text("存钱", style = KassaType.cjk(14).copy(color = KassaColors.Lime))
        }
        Text(
            "«${goal.name}»: накоплено ${formatRub(goal.saved, symbol = false)} из ${formatRub(goal.target, symbol = false)}, " +
                "осталось ${formatRub(maxOf(0, goal.target - goal.saved), symbol = false)}. Сумма вычтется из «Свободно до конца месяца».",
            style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (amount.isEmpty()) Text("0", style = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.89f, color = KassaColors.Line))
                BasicTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(12); err = "" },
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

        Spacer(Modifier.height(8.dp))
        val keys = listOf("Отложить⇲⇲", "Отмена取消")
        val v = remember { distinctButtonVariants(keys) }
        PrimaryButton("Отложить", variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
        GhostButton("Отмена", "取消", variant = v.last(), onClick = onCancel)
    }
}
