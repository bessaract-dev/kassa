package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.amountInput
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxType
import dev.bessaract.kassa.core.parseAmount
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.TxDraft
import dev.bessaract.kassa.ui.catIcon
import dev.bessaract.kassa.ui.catShort
import dev.bessaract.kassa.ui.components.Chip
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.components.Segmented
import dev.bessaract.kassa.ui.theme.distinctButtonVariants
import dev.bessaract.kassa.ui.components.press
import dev.bessaract.kassa.ui.formDay
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import java.time.LocalDate

/**
 * Содержимое нижнего листа «Новая трата / доход» и правки операции.
 * original = null — новая операция: сразу фокус на сумме, чтобы открылась клавиатура; сверху переключатель Трата / Доход.
 * Операцию по плану (ref) можно только удалить — как в kassa.html: отметка в Плане снимется.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxSheetContent(
    s: SpendingState,
    original: Tx?,
    onSave: (TxDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val key = original?.id ?: "new"
    var amount by rememberSaveable(key) { mutableStateOf(original?.let { amountInput(it.amt) } ?: "") }
    var type by rememberSaveable(key) { mutableStateOf(original?.t ?: TxType.EXP) }
    val inc = type == TxType.INC
    val cats = s.cats(if (inc) CatType.INC else CatType.EXP)
    var cat by rememberSaveable(key) { mutableStateOf(original?.cat ?: s.expCats.firstOrNull()?.id.orEmpty()) }
    var customCat by rememberSaveable(key) { mutableStateOf("") }
    var who by rememberSaveable(key) { mutableStateOf(original?.who ?: s.persons.firstOrNull().orEmpty()) }
    var day by rememberSaveable(key) { mutableStateOf(original?.d ?: s.today.toString()) }
    var note by rememberSaveable(key) { mutableStateOf(original?.note.orEmpty()) }
    var err by remember(key) { mutableStateOf("") }
    var confirmDelete by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val date = LocalDate.parse(day)

    fun save() {
        val amt = parseAmount(amount)
        when {
            amt <= 0 -> err = "Введи сумму больше нуля"
            cat.isEmpty() && customCat.isBlank() -> err = "Выбери или впиши категорию"
            else -> onSave(TxDraft(original?.id, type, amt, if (customCat.isBlank()) cat else "", customCat.trim(), who, date, note))
        }
    }

    LaunchedEffect(key) { if (original == null) focus.requestFocus() }
    val locked = original?.ref != null

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        val title = when {
            original == null -> if (inc) "Новый доход" else "Новая трата"
            else -> if (inc) "Правка дохода" else "Правка траты"
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(title.uppercase(), style = KassaType.SheetTitle, modifier = Modifier.weight(1f))
            Text(if (inc) "収入" else "支出", style = KassaType.cjk(14).copy(color = KassaColors.Lime))
        }
        if (original == null) {
            Segmented(listOf(TxType.EXP to "Трата", TxType.INC to "Доход"), type, Modifier.padding(top = 12.dp)) { t ->
                if (t != type) {
                    type = t
                    cat = s.cats(if (t == TxType.INC) CatType.INC else CatType.EXP).firstOrNull()?.id.orEmpty()
                    customCat = ""; err = ""
                }
            }
        }
        if (locked) {
            Text(
                if (original?.ref?.kind == RefKind.GOAL) "Взнос в цель «${original.note}». Его можно только удалить — накопленное уменьшится."
                else "Операция по плану: «${original?.note}». Её можно только удалить — отметка в Плане снимется.",
                style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // Сумма: крупно, с лаймовым курсором
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (amount.isEmpty()) Text("0", style = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.89f, color = KassaColors.Line))
                BasicTextField(
                    value = amount,
                    enabled = !locked,
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

        if (!locked) {
        SheetLabel("Категория")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> Chip("${catIcon(c)} ${catShort(c)}", c.id == cat) { cat = c.id; customCat = ""; err = "" } }
            // Категория, которую уже удалили или сделали системной, остаётся видна при правке.
            if (cat.isNotEmpty() && cats.none { it.id == cat }) {
                Chip("${catIcon(s.allCats[cat])} ${s.allCats[cat]?.name ?: "Без категории"}", true) {}
            }
        }

        // Своя категория руками: выбор чипа снимается; новая категория создаётся при записи.
        SheetField(
            value = customCat,
            placeholder = "или впиши свою категорию",
            onChange = { v -> customCat = v.take(40); if (v.isNotBlank()) cat = ""; err = "" },
            onDone = { save() },
            modifier = Modifier.padding(top = 8.dp),
        )

        // «На кого» — только у трат
        if (!inc) {
            SheetLabel("На кого")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.persons.forEach { p -> Chip(p, p == who) { who = p } }
            }
        }

        SheetLabel("Дата")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("‹", false) { day = date.minusDays(1).toString() }
            Text(
                formDay(date, s.today),
                style = KassaType.Body.copy(color = if (date == s.today) KassaColors.Lime else KassaColors.Text),
                modifier = Modifier.width(120.dp).press { day = s.today.toString() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // В будущее не листаем: операция — это то, что уже случилось.
            if (date < s.today) Chip("›", false) { day = date.plusDays(1).toString() }
        }

        SheetLabel("Заметка")
        SheetField(note, "необязательно", onChange = { note = it.take(200) }, onDone = { save() })
        }

        Spacer(Modifier.height(16.dp))
        // Кнопки одного листа — разные варианты формы (ключи — как текст кнопки в прототипе).
        val keys = listOfNotNull(if (locked) null else "Записать⇲⇲", if (original != null) "Удалить削除" else null, "Отмена取消")
        val v = remember(keys) { distinctButtonVariants(keys) }
        if (!locked) {
            PrimaryButton("Записать", variant = v.first()) { save() }
            Spacer(Modifier.height(8.dp))
        }
        if (original != null) {
            GhostButton(if (confirmDelete) "Точно удалить?" else "Удалить", "削除", color = KassaColors.Bad, variant = v[keys.indexOf("Удалить削除")]) {
                if (confirmDelete) onDelete(original.id) else confirmDelete = true
            }
            Spacer(Modifier.height(8.dp))
        }
        GhostButton("Отмена", "取消", variant = v.last(), onClick = onCancel)
    }
}

@Composable
internal fun SheetLabel(text: String) {
    Text(text.uppercase(), style = KassaType.Label.copy(color = KassaColors.Grey), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** Однострочное поле на утопленном фоне, с лаймовым курсором. Активное (непустое) — с лаймовой чертой слева. */
@Composable
internal fun SheetField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(modifier.fillMaxWidth().background(KassaColors.Raised)) {
        if (value.isNotEmpty()) Box(Modifier.width(2.dp).height(37.dp).background(KassaColors.Lime))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 9.dp)) {
            if (value.isEmpty()) Text(placeholder, style = KassaType.Body.copy(color = KassaColors.Grey))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = KassaType.Body,
                cursorBrush = SolidColor(KassaColors.Lime),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
