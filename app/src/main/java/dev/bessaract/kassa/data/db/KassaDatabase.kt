package dev.bessaract.kassa.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.bessaract.kassa.core.model.Defaults

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, PersonEntity::class, RateEntity::class,
        RecurringEntity::class, LoanEntity::class, GoalEntity::class, TxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KassaDatabase : RoomDatabase() {
    abstract fun dao(): KassaDao

    companion object {
        fun build(context: Context): KassaDatabase =
            Room.databaseBuilder(context, KassaDatabase::class.java, "kassa.db")
                .addCallback(SeedCallback)
                .build()
    }
}

/** При первом создании базы кладём стартовые категории и людей, как defaults() в kassa.html. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        Defaults.categories().map { it.toEntity(0) }.forEachIndexed { i, c ->
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", c.id); put("name", c.name); put("type", c.type)
                put("limit_amt", c.limit); put("position", i)
            })
        }
        Defaults.persons.forEachIndexed { i, name ->
            db.insert("persons", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("position", i); put("name", name)
            })
        }
    }
}
