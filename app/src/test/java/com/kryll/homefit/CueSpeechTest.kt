package com.kryll.homefit

import com.kryll.homefit.domain.Beep
import com.kryll.homefit.domain.Cue
import com.kryll.homefit.domain.toSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueSpeechTest {
    @Test fun countdownOnlyBeeps() {
        val s = Cue.Countdown(2).toSound()
        assertNull(s.speech); assertEquals(Beep.TICK, s.beep)
    }

    @Test fun goSaysRepsOrSeconds() {
        assertEquals("¡Ya! 12 repeticiones", Cue.Go("Sentadilla", 1, 3, 12, null).toSound().speech)
        assertEquals("¡Ya! 30 segundos", Cue.Go("Plancha", 1, 2, null, 30).toSound().speech)
        assertEquals(Beep.GO, Cue.Go("Plancha", 1, 2, null, 30).toSound().beep)
    }

    @Test fun getReadyNamesExerciseAndSet() {
        assertEquals("Prepárate: Plancha, serie 2 de 3", Cue.GetReady("Plancha", 2, 3).toSound().speech)
    }

    @Test fun restAnnouncesNextOnlyWhenKnown() {
        assertEquals("Descanso, 45 segundos. Siguiente: Plancha", Cue.Rest(45, "Plancha").toSound().speech)
        assertEquals("Descanso, 30 segundos", Cue.Rest(30, null).toSound().speech)
    }

    @Test fun finishAndSetDoneBeepDone() {
        assertEquals(Beep.DONE, Cue.Finished.toSound().beep)
        assertEquals("Serie hecha", Cue.SetDone("X").toSound().speech)
        assertEquals("Mitad", Cue.Halfway.toSound().speech)
        assertEquals("Quedan 10 segundos", Cue.TenSecondsLeft.toSound().speech)
        assertEquals("Cambio de lado", Cue.SwitchSides.toSound().speech)
    }

    @Test fun getReadyAnnouncesScheme() {
        assertEquals("Prepárate: Sentadilla, 3 series de 12 repeticiones", Cue.GetReady("Sentadilla", 1, 3, 12, null).toSound().speech)
        assertEquals("Prepárate: Plancha, 2 series de 30 segundos", Cue.GetReady("Plancha", 1, 2, null, 30).toSound().speech)
    }

    @Test fun lastSetGoIsAnnounced() {
        assertEquals("Última serie. ¡Ya! 12 repeticiones", Cue.Go("Sentadilla", 3, 3, 12, null).toSound().speech)
        assertEquals("¡Ya! 12 repeticiones", Cue.Go("Sentadilla", 1, 3, 12, null).toSound().speech)
    }

    @Test fun restAnnouncesNextExerciseScheme() {
        assertEquals(
            "Descanso, 45 segundos. Siguiente: Plancha, 2 series de 30 segundos",
            Cue.Rest(45, "Plancha", 2, null, 30).toSound().speech,
        )
        assertEquals("Descanso, 45 segundos. Siguiente: Sentadilla", Cue.Rest(45, "Sentadilla").toSound().speech)
    }
}
