package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.MIGRATION_1_2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migración real 1 -> 2 sobre SQLite: crea el esquema v1 desde el JSON exportado (fuente de verdad del esquema
 * anterior), inserta filas representativas y abre con Room aplicando la migración. Verifica ALTERs, datos
 * retenidos (planJson null, itemIndex -1 en filas viejas) y reapertura válida.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    @Test fun v1ToV2MigratesRealSqliteKeepingRows(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        val schemaJson = javaClass.classLoader!!.getResourceAsStream("schemas/1.json")!!.bufferedReader().readText()
        val schema = JSONObject(schemaJson).getJSONObject("database")
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        // Filas representativas con el esquema v1 (sin planJson ni itemIndex)
        sqlite.execSQL("INSERT INTO workout_session (routineId, startMillis, endMillis, durationSeconds, notes) VALUES (5, 1000, 2000, 1, 'incompleto')")
        sqlite.execSQL("INSERT INTO set_log (sessionId, exerciseId, setIndex, reps) VALUES (1, 'sentadilla', 1, 10)")
        sqlite.close()

        val db = Room.databaseBuilder(context, HomeFitDatabase::class.java, "migration-test.db").addMigrations(MIGRATION_1_2).build()
        val sessions = db.sessionDao().observeWorkouts().first()
        assertEquals(1, sessions.size)
        assertNull(sessions[0].planJson)
        val sets = db.sessionDao().setsOf(1)
        assertEquals(1, sets.size)
        assertEquals(-1, sets[0].itemIndex)
        db.close()

        // Reapertura tras migrar: el esquema queda válido y los datos siguen
        val reopened = Room.databaseBuilder(context, HomeFitDatabase::class.java, "migration-test.db").addMigrations(MIGRATION_1_2).build()
        assertEquals(1, reopened.sessionDao().setsOf(1).size)
        reopened.close()
        dbFile.delete()
    }
}
