package dev.bessaract.kassa.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.bessaract.kassa.data.db.KassaDatabase
import dev.bessaract.kassa.data.db.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Обновление поверх 0.5: база версии 1 по schemas/1.json с целью и операцией
 * открывается версией 2 без потери данных, у старой цели даты нет.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @Test
    fun v1ToV2KeepsData() {
        val ctx = RuntimeEnvironment.getApplication()
        val file = ctx.getDatabasePath("migr.db").apply { parentFile?.mkdirs(); delete() }
        val schema = JSONObject(File("schemas/dev.bessaract.kassa.data.db.KassaDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { ix ->
                    for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO goals VALUES ('g', 'Отпуск', 10000000, 500000, 0, 0)")
            db.execSQL("INSERT INTO tx VALUES ('t', 1, 'exp', 54000, 'RUB', 54000, 'c', '', '', 'Я', '2026-10-22', '', NULL, NULL, NULL)")
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, KassaDatabase::class.java, "migr.db").addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        runBlocking {
            val goals = room.dao().allGoals()
            assertEquals(1, goals.size)
            assertEquals("", goals[0].by)
            assertEquals(500000L, goals[0].saved)
            assertEquals(listOf("t"), room.dao().allTxIds())
        }
        room.close()
    }
}
