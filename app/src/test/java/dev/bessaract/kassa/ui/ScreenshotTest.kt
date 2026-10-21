package dev.bessaract.kassa.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.bessaract.kassa.core.calc.spending
import dev.bessaract.kassa.core.model.Defaults
import dev.bessaract.kassa.core.model.Tx
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
    private fun tx(id: String, d: LocalDate, h: Int, m: Int, rub: Long, cat: String, who: String = "Я", note: String = "") = Tx(
        id, d.atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        TxType.EXP, rub, "RUB", rub, cat(cat), "", "", who, d.toString(), note,
    )

    private fun state(withTx: Boolean): SpendingState {
        val list = if (!withTx) emptyList() else listOf(
            tx("a", today, 12, 40, 54000, "Продукты"),
            tx("b", today, 9, 15, 22000, "Кафе и рестораны", note = "кофе"),
            tx("c", today.minusDays(1), 19, 5, 185000, "Дом", who = "Мама"),
            tx("d", today.minusDays(3), 8, 30, 6400, "Транспорт"),
        )
        return SpendingState(
            today = today,
            spending = spending(list, today),
            expCats = cats.filter { it.type.code == "exp" && !it.isSystem },
            allCats = cats.associateBy { it.id },
            persons = Defaults.persons,
        )
    }

    private fun shot(name: String, s: SpendingState, tab: Tab = Tab.HOME, sheet: String? = null) {
        rule.setContent { KassaTheme { KassaContent(s, { _, _ -> }, {}, tab, sheet) } }
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
}
