package dev.bessaract.kassa

import android.app.Application
import dev.bessaract.kassa.data.db.KassaDatabase

class KassaApp : Application() {
    val db: KassaDatabase by lazy { KassaDatabase.build(this) }
}
