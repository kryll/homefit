package com.kryll.homefit

import com.kryll.homefit.domain.Cue
import com.kryll.homefit.domain.Phase
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRunnerTest {
    private var t = 0L
    private val items = listOf(WorkItem("a", "Plancha", 1, null, 10, 30))
    private fun runner() = WorkoutRunner(items, now = { t })
    private fun phase(r: WorkoutRunner) = r.snapshot.value.state.phase

    @Test fun avanzaPorRelojAunqueNoHayaSyncCadaSegundo() {
        val r = runner()
        t = 5000; r.sync()
        assertEquals(Phase.WORK, phase(r))
    }

    @Test fun recuperaSegundosPerdidosDeUnaVez() {
        val r = runner()
        t = 15000; r.sync() // 5 s de espera + 10 s de plancha en una sola llamada
        assertEquals(Phase.RATE, phase(r))
    }

    @Test fun mediosSegundosNoSumanTicksFalsos() {
        val r = runner()
        repeat(40) { t += 250; r.sync() } // 10 s exactos
        assertEquals(Phase.WORK, phase(r)) // 5 s espera + 5 s de 10
        assertEquals(5, r.snapshot.value.state.remaining)
    }

    @Test fun pausaNoAcumulaTiempoNiSalta() {
        val r = runner()
        t = 1000; r.pause()
        assertEquals(4, r.snapshot.value.state.remaining)
        t = 61000; r.resume()
        assertEquals(4, r.snapshot.value.state.remaining)
        t = 65000; r.sync()
        assertEquals(Phase.WORK, phase(r))
    }

    @Test fun avisosAtrasadosSeDescartanYLosActualesSeEntreganUnaVez() {
        val r = runner()
        val got = mutableListOf<Cue>()
        r.cueListener = { got += it }
        t = 15000; r.sync()
        assertEquals(0, got.count { it is Cue.Countdown })
        val n = got.size
        r.sync() // sin tiempo nuevo: no repite
        assertEquals(n, got.size)
    }

    @Test fun salirSoloDevuelveResultadosUnaVez() {
        val r = runner()
        t = 15000; r.sync()
        r.rate(7)
        val first = r.exit()
        assertNotNull(first)
        assertEquals(7, first!!.first.single().rpe)
        assertNull(r.exit())
        t = 99000; r.sync()
        assertFalse(r.snapshot.value.state.phase == Phase.GET_READY)
    }

    @Test fun cerradoNoReaccionaNiEmiteAvisos() {
        val r = runner()
        val got = mutableListOf<Cue>()
        r.cueListener = { got += it }
        r.exit()
        t = 20000; r.sync(); r.done(); r.skip()
        assertEquals(0, got.size)
    }
}
