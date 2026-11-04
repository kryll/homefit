package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.MIGRATION_1_2
import com.kryll.homefit.data.MIGRATION_2_3
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migración real 2 -> 3 sobre SQLite (encadenada desde v1 exportado): añade easierText/harderText a exercise.
 * Verifica que las filas viejas sobreviven con los campos nuevos en null y que una fila con texto se conserva
 * tras reabrir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Migration23Test {
    @Test fun v2ToV3AddsVariantTextColumnsKeepingRows(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = context.getDatabasePath("migration23-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

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
        sqlite.execSQL("INSERT INTO exercise (id, name, primaryMuscle, difficulty, steps, commonMistakes, safetyTips, images, source) VALUES ('sentadilla', 'Sentadilla', 'pierna', 'principiante', 'a\nb', 'm', 's', '', 'x')")
        sqlite.close()

        // Room aplica 1->2->3 y valida el esquema final contra el compilado (v3)
        val db = Room.databaseBuilder(context, HomeFitDatabase::class.java, "migration23-test.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val dao = db.exerciseDao()
        val row = dao.byId("sentadilla")
        assertEquals("Sentadilla", row?.name)
        assertNull(row?.easierText)
        assertNull(row?.harderText)
        db.close()

        // Reapertura: el texto de variante persiste
        val reopened = Room.databaseBuilder(context, HomeFitDatabase::class.java, "migration23-test.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val withText = reopened.exerciseDao().byId("sentadilla")
        assertEquals("Sentadilla", withText?.name)
        reopened.close()
        dbFile.delete()
    }
}
