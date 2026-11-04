package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.ExerciseSeeder
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.MIGRATION_1_2
import com.kryll.homefit.data.MIGRATION_2_3
import com.kryll.homefit.domain.ExerciseCatalog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actualización idempotente del catálogo: instalación nueva (siembra completa con variantes) y
 * datos anteriores (migración 1->3 + refresh): el contenido nuevo llega a filas viejas, las rutinas,
 * la planificación y el historial sobreviven, y repetir no duplica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogRefreshTest {
    private val json = File("src/main/assets/exercises.json").readText()
    private val catalog = ExerciseCatalog.parse(json)

    @Test fun refreshOnEmptyDatabaseSeedsCatalogWithVariants() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java).allowMainThreadQueries().build()
        ExerciseSeeder.refreshFromJson(json, db)
        val dao = db.exerciseDao()
        assertEquals(catalog.exercises.size, dao.count())
        catalog.exercises.forEach {
            val row = dao.byId(it.id)!!
            assertTrue("${it.id} sin variante fácil", row.easierVariantId != null || !row.easierText.isNullOrBlank())
            assertTrue("${it.id} sin variante difícil", row.harderVariantId != null || !row.harderText.isNullOrBlank())
        }
        assertTrue(db.exerciseDao().observeEquipment().first().isNotEmpty())
        db.close()
    }

    @Test fun migrationThenRefreshUpdatesOldRowsKeepsUserDataNoDuplicates(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = context.getDatabasePath("refresh-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // BD v1 con datos anteriores: ejercicio sin campos de texto + rutina/plan/historial que lo referencian
        val schemaJson = javaClass.classLoader!!.getResourceAsStream("schemas/1.json")!!.bufferedReader().readText()
        val schema = JSONObject(schemaJson).getJSONObject("database")
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                sqlite.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL("PRAGMA user_version = 1")
        sqlite.execSQL("INSERT INTO exercise (id, name, primaryMuscle, difficulty, steps, commonMistakes, safetyTips, images, source) VALUES ('superman', 'Superman viejo', 'espalda', 'principiante', 'a\nb', 'm', 's', '', 'x')")
        sqlite.execSQL("INSERT INTO routine (name, goal, level, type, predefined, notes) VALUES ('Mi rutina', 't', 'p', 'personalizada', 0, '')")
        sqlite.execSQL("INSERT INTO routine_item (routineId, position, exerciseId, sets, restSeconds, alternateSides) VALUES (1, 0, 'superman', 3, 30, 0)")
        sqlite.execSQL("INSERT INTO workout_session (routineId, startMillis, endMillis, durationSeconds, notes) VALUES (1, 1000, 2000, 1, 'ok')")
        sqlite.execSQL("INSERT INTO set_log (sessionId, exerciseId, setIndex, reps) VALUES (1, 'superman', 0, 12)")
        sqlite.execSQL("INSERT INTO scheduled_session (routineId, startMillis, status, reminderMinutesBefore) VALUES (1, 5000, 'SCHEDULED', 0)")
        sqlite.close()

        // Migración 1->3: la fila vieja queda con los campos de texto a null
        val db = Room.databaseBuilder(context, HomeFitDatabase::class.java, "refresh-test.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        assertNull(db.exerciseDao().byId("superman")!!.easierText)

        // Refresh de arranque: el contenido nuevo llega a la fila vieja
        ExerciseSeeder.refreshFromJson(json, db)
        val dao = db.exerciseDao()
        val sup = dao.byId("superman")!!
        assertEquals("Superman", sup.name)
        assertNotNull(sup.easierText)
        assertNotNull(sup.harderText)
        assertEquals(catalog.exercises.size, dao.count())
        assertTrue(dao.observeEquipment().first().isNotEmpty())

        // Rutinas, planificación e historial intactos
        assertEquals(1, db.routineDao().observeAll().first().size)
        assertEquals(1, db.routineDao().observeAllItems().first().size)
        assertEquals(1, db.sessionDao().workoutsBetween(0, 10000).size)
        assertEquals(1, db.sessionDao().setsOf(1).size)
        assertEquals(1, db.sessionDao().scheduledBetween(0, 10000).size)

        // Segunda pasada: idempotente, sin duplicados
        ExerciseSeeder.refreshFromJson(json, db)
        assertEquals(catalog.exercises.size, dao.count())
        assertEquals("Superman", dao.byId("superman")!!.name)
        assertEquals(1, db.routineDao().observeAllItems().first().size)
        db.close()
        dbFile.delete()
    }
}
