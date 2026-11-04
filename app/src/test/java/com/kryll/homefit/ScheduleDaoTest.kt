package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.ScheduledSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScheduleDaoTest {
    @Test fun deleteBetweenKeepsOtherDays() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.sessionDao()
        listOf(100L, 200L, 300L).forEach { dao.insertScheduled(ScheduledSession(routineId = null, startMillis = it, status = "SCHEDULED")) }
        dao.deleteScheduledBetween(150, 250)
        assertEquals(listOf(100L, 300L), dao.scheduledBetween(0, 1000).map { it.startMillis })
        db.close()
    }
}
