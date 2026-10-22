package dev.bessaract.kassa.core

import dev.bessaract.kassa.core.model.CatType
import dev.bessaract.kassa.core.model.Defaults
import dev.bessaract.kassa.core.model.SystemCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultsTest {
    @Test
    fun categoriesMatchWebVersion() {
        val cats = Defaults.categories()
        assertEquals(16, cats.size)
        assertEquals(13, cats.count { it.type == CatType.EXP })
        assertEquals(3, cats.count { it.type == CatType.INC })
        assertTrue(cats.any { it.id == SystemCategories.LOAN && it.isSystem })
        assertTrue(cats.any { it.id == SystemCategories.GOAL && it.isSystem })
        assertEquals(cats.size, cats.map { it.id }.toSet().size)
    }

    @Test
    fun personsDefaultFirstIsMe() {
        assertEquals(listOf("Я", "Варя", "Ира", "Мама"), Defaults.persons)
    }

    @Test
    fun toRubRoundsToKopeck() {
        // 10,01 $ по 92,35 ₽ = 924,4235 ₽ → 924,42 ₽
        assertEquals(92442L, toRub(1001L, 92.35))
    }

    @Test
    fun findExpCategoryIgnoresCaseAndSpaces() {
        val cats = Defaults.categories()
        assertEquals("Продукты", dev.bessaract.kassa.core.model.findExpCategory(cats, "  продукты ")?.name)
        assertEquals(null, dev.bessaract.kassa.core.model.findExpCategory(cats, "Зарплата"))
        assertEquals(null, dev.bessaract.kassa.core.model.findExpCategory(cats, "Кэшбэк"))
    }
}
