package com.kryll.homefit

import com.kryll.homefit.domain.MeasureDate
import com.kryll.homefit.domain.MeasureInput
import com.kryll.homefit.domain.MeasureField
import com.kryll.homefit.domain.MeasureResult
import com.kryll.homefit.data.BodyMeasurement
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MeasureDateTest {
    private val today = LocalDate.of(2026, 11, 3).toEpochDay()
    @Test fun strictDatesAndFutureRejected() {
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), MeasureDate.parse("29/02/2024", today))
        listOf("29/02/2025", "31/04/2026", "04/11/2026", "", "2026-11-03").forEach { assertNull(it, MeasureDate.parse(it, today)) }
        assertEquals("03/11/2026", MeasureDate.text(today))
    }
    @Test fun explicitDateChangesDateButKeepsIdAndExactMeasurements() {
        val base = BodyMeasurement(id = 5, epochDay = today, weightKg = 70.123f)
        val prefill = MeasureInput.prefill(base)
        val result = MeasureInput.build(today, prefill, base = base, prefill = prefill, selectedDay = today - 2) as MeasureResult.Ok
        assertEquals(5L, result.measurement.id)
        assertEquals(today - 2, result.measurement.epochDay)
        assertEquals(base.weightKg, result.measurement.weightKg)
    }
}
