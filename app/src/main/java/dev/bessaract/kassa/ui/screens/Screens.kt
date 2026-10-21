package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.core.calc.daysLeft
import dev.bessaract.kassa.core.formatRub
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.catIcon
import dev.bessaract.kassa.ui.components.Arrows
import dev.bessaract.kassa.ui.components.DaySegments
import dev.bessaract.kassa.ui.components.EmptyRow
import dev.bessaract.kassa.ui.components.Hatch
import dev.bessaract.kassa.ui.components.ListHeader
import dev.bessaract.kassa.ui.components.OpRow
import dev.bessaract.kassa.ui.components.Panel
import dev.bessaract.kassa.ui.components.PanelKind
import dev.bessaract.kassa.ui.components.PanelLabel
import dev.bessaract.kassa.ui.components.ScrambleText
import dev.bessaract.kassa.ui.components.reveal
import dev.bessaract.kassa.ui.dayTitle
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.ShapeA
import dev.bessaract.kassa.ui.theme.ShapeB
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** «Сегодня» в MVP: потрачено за день и за месяц, траты сегодня. */
@Composable
fun TodayScreen(s: SpendingState, onEdit: (Tx) -> Unit) {
    val sp = s.spending
    val days = YearMonth.from(s.today).lengthOfMonth()

    Panel(ShapeA, PanelKind.LIME, Modifier.reveal(2)) {
        Spacer(Modifier.height(8.dp))
        PanelLabel("Потрачено сегодня", "支出", KassaColors.Ink)
        Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(sp.today, symbol = false), KassaType.Big.copy(color = KassaColors.Ink))
            Text("₽", style = KassaType.Big.copy(fontSize = KassaType.Big.fontSize * 0.5f, color = KassaColors.Ink), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        Text("операций сегодня: ${sp.todayTx.size}", style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Hatch(KassaColors.Ink)
            Arrows(3, KassaColors.Ink)
            Spacer(Modifier.weight(1f))
            Text("SN 48-1508F", style = KassaType.Label.copy(fontSize = KassaType.Label.fontSize * 0.9f, color = KassaColors.Ink.copy(alpha = 0.6f)))
        }
    }

    Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(3)) {
        PanelLabel("Потрачено за месяц", "今月", KassaColors.Ink)
        Row(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(sp.month, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink))
            Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
        }
        DaySegments(days, s.today.dayOfMonth)
        Text(
            "день ${s.today.dayOfMonth} из $days · до конца месяца ${daysLeft(s.today)} дн.",
            style = KassaType.Small.copy(color = KassaColors.Ink.copy(alpha = 0.75f)),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
    }

    ListHeader("履歴", "Траты сегодня", "%02d шт".format(sp.todayTx.size), Modifier.reveal(4))
    if (sp.todayTx.isEmpty()) {
        EmptyRow("Пока пусто. Нажми «+», чтобы записать трату.", Modifier.reveal(5))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sp.todayTx.forEachIndexed { i, t -> key(t.id) { TxRow(t, s, Modifier.reveal(5 + i), onEdit) } }
        }
    }
}

/** «История» в MVP: траты текущего месяца по дням. */
@Composable
fun HistoryScreen(s: SpendingState, onEdit: (Tx) -> Unit) {
    val groups = s.spending.byDay
    Panel(ShapeB, PanelKind.PAPER, Modifier.reveal(2)) {
        PanelLabel("Потрачено за месяц", "今月", KassaColors.Ink)
        Row(Modifier.padding(top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
            ScrambleText(formatRub(s.spending.month, symbol = false), KassaType.Mid.copy(color = KassaColors.Ink))
            Text(" ₽", style = KassaType.Mid.copy(color = KassaColors.Ink))
        }
    }
    if (groups.isEmpty()) {
        EmptyRow("В этом месяце трат пока нет.", Modifier.reveal(3))
        return
    }
    var i = 3
    groups.forEach { g ->
        key(g.date) {
            ListHeader("日", dayTitle(g.date), formatRub(g.total), Modifier.reveal(i++))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.tx.forEach { t -> key(t.id) { TxRow(t, s, Modifier.reveal(i++), onEdit) } }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun TxRow(t: Tx, s: SpendingState, modifier: Modifier, onEdit: (Tx) -> Unit) {
    val cat = s.allCats[t.cat]
    val time = Instant.ofEpochMilli(t.ts).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
    val meta = listOf(t.who, t.note, time).filter { it.isNotBlank() }.joinToString(" · ")
    OpRow(
        icon = catIcon(cat),
        title = cat?.name ?: "Без категории",
        meta = meta,
        amount = "−" + formatRub(t.rub, symbol = false),
        modifier = modifier,
        onClick = { onEdit(t) },
    )
}

/** Вкладки, которых ещё нет в MVP. */
@Composable
fun PlaceholderScreen(text: String) {
    Panel(dev.bessaract.kassa.ui.theme.ShapeC, PanelKind.DARK, Modifier.reveal(2)) {
        PanelLabel("В разработке", "工事中", KassaColors.Grey)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(text, style = KassaType.Body)
        }
    }
}
