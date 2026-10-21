package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.ui.Tab
import dev.bessaract.kassa.ui.components.TerminalCard
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType

/** «Сегодня» до появления расчётов: стартовая карточка «С чего начать», как в kassa.html при пустой базе. */
@Composable
fun TodayScreen() {
    TerminalCard("С чего начать") {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}.", style = KassaType.Body.copy(color = KassaColors.Accent))
                Text(step, style = KassaType.Body)
            }
        }
    }
}

private val steps = listOf(
    "Добавь счета: карты, наличные, вклады.",
    "Во вкладке «План» внеси постоянные доходы и расходы.",
    "Во вкладке «Долги» внеси кредиты и цели.",
    "Траты за день вноси кнопкой «+».",
)

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
