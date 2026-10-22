package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.amountInput
import dev.bessaract.kassa.core.calc.accountBalance
import dev.bessaract.kassa.core.model.Account
import dev.bessaract.kassa.core.parseAmount
import dev.bessaract.kassa.ui.AccountDraft
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.distinctButtonVariants

/**
 * Лист счёта: название и сколько на нём сейчас. Хранится остаток на старте (init = баланс − операции),
 * дальше баланс меняют операции. original = null — новый счёт.
 */
@Composable
fun AccountSheetContent(
    s: SpendingState,
    original: Account?,
    onSave: (AccountDraft) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val key = original?.id ?: "new-acc"
    // баланс на момент открытия: если его не трогали, в базе он мог измениться от новых операций
    val opened = remember(key) { original?.let { amountInput(accountBalance(it, s.accMoves)) } ?: "" }
    var name by rememberSaveable(key) { mutableStateOf(original?.name.orEmpty()) }
    var balance by rememberSaveable(key) { mutableStateOf(opened) }
    var err by remember(key) { mutableStateOf("") }
    var confirmDelete by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        when {
            name.isBlank() -> err = "Впиши название"
            else -> onSave(
                AccountDraft(
                    original?.id, name.trim(),
                    if (original != null && balance == opened) null else if (balance.isBlank()) 0 else parseAmount(balance),
                ),
            )
        }
    }

    LaunchedEffect(key) { if (original == null) focus.requestFocus() }

    Column {
        SheetHeader(if (original == null) "Новый счёт" else "Правка счёта", "账户", onCancel)
        SheetLabel("Название")
        SheetField(
            name, "Карта, наличные, вклад…",
            onChange = { name = it.take(40); err = "" }, onDone = { save() },
            modifier = Modifier.focusRequester(focus),
        )
        SheetLabel("Сколько на счёте сейчас, ₽")
        SheetField(
            balance, "0",
            // минус — для кредитки, ушедшей в долг
            onChange = { v -> balance = v.filterIndexed { i, c -> c.isDigit() || c == ',' || c == '.' || c == ' ' || (i == 0 && c == '-') }.take(13); err = "" },
            onDone = { save() }, keyboardType = KeyboardType.Decimal,
        )
        if (err.isNotEmpty()) Text(err, style = KassaType.Small.copy(color = KassaColors.Bad), modifier = Modifier.padding(top = 8.dp))
        Text(
            "Дальше баланс меняют операции с этим счётом. Разошлось с банком — впиши здесь, сколько на самом деле. " +
                "Первый счёт подставляется в новые траты и в «Оплатил».",
            style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f),
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(16.dp))
        val keys = listOfNotNull("Сохранить⇲⇲", if (original != null) "Удалить删除" else null)
        val v = remember(keys) { distinctButtonVariants(keys) }
        PrimaryButton("Сохранить", variant = v.first()) { save() }
        Spacer(Modifier.height(8.dp))
        if (original != null) {
            // Операции остаются в истории с пометкой «счёт удалён».
            GhostButton(if (confirmDelete) "Точно удалить?" else "Удалить", "删除", color = KassaColors.Bad, variant = v[1]) {
                if (confirmDelete) onDelete(original.id) else confirmDelete = true
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
