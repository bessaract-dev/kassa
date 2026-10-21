package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bessaract.kassa.KassaApp
import dev.bessaract.kassa.ui.Tab
import dev.bessaract.kassa.ui.components.HeroCard
import dev.bessaract.kassa.ui.components.StatTile
import dev.bessaract.kassa.ui.components.TerminalCard
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType

/** «Сегодня» на шаге 1: вид главной цифры без расчётов и проверка, что база создалась. */
@Composable
fun TodayScreen() {
    val dao = (LocalContext.current.applicationContext as KassaApp).db.dao()
    // remember: запрос к базе создаётся один раз, а не при каждой перерисовке.
    val cats by remember(dao) { dao.categories() }.collectAsStateWithLifecycle(emptyList())
    val persons by remember(dao) { dao.persons() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember(dao) { dao.accounts() }.collectAsStateWithLifecycle(emptyList())
    val txCount by remember(dao) { dao.txCount() }.collectAsStateWithLifecycle(0)

    HeroCard(label = "На сегодня осталось", value = "— ₽", sub = "Расчёт лимита появится на шаге 2")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("— ₽", "свободно до конца месяца", Modifier.weight(1f))
        StatTile("— ₽", "обязательные платежи в месяц", Modifier.weight(1f))
    }
    TerminalCard("База данных") {
        Line("Категорий", "${cats.size} (расход ${cats.count { it.type == "exp" }}, доход ${cats.count { it.type == "inc" }})")
        Line("На кого", persons.joinToString(", ") { it.name })
        Line("Счетов", accounts.size.toString())
        Line("Операций", txCount.toString())
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = KassaType.Small)
        Text(value, style = KassaType.Body.copy(color = KassaColors.Good))
    }
}

private val upcoming = mapOf(
    Tab.HIST to "Операции по дням, переключение месяцев.",
    Tab.PLAN to "Постоянные доходы и расходы, лимиты по категориям.",
    Tab.DEBT to "Кредиты с прогнозом закрытия, цели накоплений.",
    Tab.REP to "Расходы по категориям и людям, шесть месяцев.",
    Tab.SET to "Счета, валюты и курс, люди, категории, экспорт и импорт JSON.",
)

@Composable
fun PlaceholderScreen(tab: Tab) {
    TerminalCard("В разработке") {
        Text(upcoming[tab].orEmpty(), style = KassaType.Body)
        Text("Экран появится на шаге 4.", style = KassaType.Small.copy(color = KassaColors.Warn))
    }
}
