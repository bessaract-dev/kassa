package dev.bessaract.kassa.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.bessaract.kassa.core.calc.budget
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Defaults
import dev.bessaract.kassa.core.model.Recurring
import dev.bessaract.kassa.core.model.RefKind
import dev.bessaract.kassa.core.model.Tx
import dev.bessaract.kassa.core.model.TxRef
import dev.bessaract.kassa.core.model.TxType
import dev.bessaract.kassa.ui.theme.KassaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Снимки экранов для проверки вёрстки без эмулятора: PNG-файлы в app/build/screenshots.
 * Запуск: ./gradlew :app:testDebugUnitTest --tests dev.bessaract.kassa.ui.ScreenshotTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 21)
    private val cats = Defaults.categories()
    private fun cat(name: String) = cats.first { it.name == name }.id
    private fun tx(
        id: String, d: LocalDate, h: Int, m: Int, rub: Long, cat: String, who: String = "Я", note: String = "",
        t: TxType = TxType.EXP, ref: TxRef? = null,
    ) = Tx(
        id, d.atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        t, rub, "RUB", rub, cat(cat), "", "", if (t == TxType.EXP) who else "", d.toString(), note, ref,
    )

    private fun rec(id: String, type: CatType, name: String, rub: Long, day: Int, cat: String) =
        Recurring(id, type, name, rub * 100, day, cat(cat), "")

    private val plan = listOf(
        rec("avans", CatType.INC, "Аванс", 45_000, 10, "Зарплата"),
        rec("zp", CatType.INC, "Зарплата", 65_000, 25, "Зарплата"),
        rec("gym", CatType.EXP, "Спортзал", 3_500, 1, "Здоровье"),
        rec("rent", CatType.EXP, "Аренда квартиры", 38_000, 5, "Дом"),
        rec("net", CatType.EXP, "Интернет", 900, 12, "Связь и подписки"),
        rec("komm", CatType.EXP, "Коммуналка", 6_200, 15, "Дом"),
        rec("mob", CatType.EXP, "Мобильная связь", 650, 28, "Связь и подписки"),
    )

    private fun state(withTx: Boolean, withPlan: Boolean = false, overspend: Boolean = false): SpendingState {
        val list = if (!withTx) emptyList() else listOfNotNull(
            tx("a", today, 12, 40, 54000, "Продукты"),
            tx("b", today, 9, 15, 22000, "Кафе и рестораны", note = "кофе"),
            if (overspend) tx("big", today, 13, 0, 900000, "Одежда", note = "куртка") else null,
            tx("c", today.minusDays(1), 19, 5, 185000, "Дом", who = "Мама"),
            tx("d", today.minusDays(3), 8, 30, 6400, "Транспорт"),
            tx("i", today.minusDays(2), 18, 0, 150000, "Подработка", note = "перевод за макет", t = TxType.INC),
        ) + if (!withPlan) emptyList() else listOf(
            tx("p1", today.withDayOfMonth(10), 11, 0, 4_500_000, "Зарплата", note = "Аванс", t = TxType.INC, ref = TxRef(RefKind.REC, "avans")),
            tx("p2", today.withDayOfMonth(1), 10, 0, 350_000, "Здоровье", note = "Спортзал", ref = TxRef(RefKind.REC, "gym")),
            tx("p3", today.withDayOfMonth(5), 10, 0, 3_800_000, "Дом", note = "Аренда квартиры", ref = TxRef(RefKind.REC, "rent")),
            tx("p4", today.withDayOfMonth(12), 10, 0, 90_000, "Связь и подписки", note = "Интернет", ref = TxRef(RefKind.REC, "net")),
        )
        val rec = if (withPlan) plan else emptyList()
        return SpendingState(
            today = today,
            spending = spending(list, today),
            expCats = cats.filter { it.type == CatType.EXP && !it.isSystem },
            incCats = cats.filter { it.type == CatType.INC },
            allCats = cats.associateBy { it.id },
            persons = Defaults.persons,
            rec = rec,
            monthTx = list,
            budget = budget(rec, emptyList(), emptyList(), list, today),
        )
    }

    private fun shot(name: String, s: SpendingState, tab: Tab = Tab.HOME, sheet: String? = null) {
        rule.setContent { KassaTheme { KassaContent(s, KassaActions(), tab, sheet) } }
        rule.mainClock.advanceTimeBy(3_000)
        // Рисуем окно в Bitmap сами: captureToImage под Robolectric зависает на ожидании кадра.
        val bmp = rule.runOnIdle {
            val v = rule.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun todayEmpty() = shot("today-empty", state(false))
    @Test fun todayFull() = shot("today", state(true))
    @Test fun history() = shot("history", state(true), Tab.HIST)
    @Test fun sheetNew() = shot("sheet-new", state(true), sheet = NEW_TX)
    @Test fun sheetEdit() = shot("sheet-edit", state(true), sheet = "b")
    @Test fun todayPlan() = shot("today-plan", state(true, withPlan = true))
    @Test fun todayOver() = shot("today-over", state(true, withPlan = true, overspend = true))
    @Test fun planTab() = shot("plan", state(true, withPlan = true), Tab.PLAN)
    @Test fun historyPlan() = shot("history-plan", state(true, withPlan = true), Tab.HIST)
    @Test fun sheetRecNew() = shot("sheet-rec-new", state(true, withPlan = true), Tab.PLAN, NEW_REC_INC)
    @Test fun sheetRecEdit() = shot("sheet-rec-edit", state(true, withPlan = true), Tab.PLAN, REC_PREFIX + "rent")
    @Test fun sheetPlanTx() = shot("sheet-plan-tx", state(true, withPlan = true), Tab.HIST, "p3")
}
