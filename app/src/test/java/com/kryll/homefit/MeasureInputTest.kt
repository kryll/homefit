package com.kryll.homefit

import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.domain.MeasureField
import com.kryll.homefit.domain.MeasureInput
import com.kryll.homefit.domain.MeasureResult
import com.kryll.homefit.ui.chartX
import com.kryll.homefit.ui.weightPoints
import com.kryll.homefit.ui.weightSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureInputTest {
    @Test fun commaDecimalAndBlanks() {
        val r = MeasureInput.build(10, mapOf(MeasureField.WEIGHT to "72,5", MeasureField.WAIST to " ", MeasureField.FAT to "18.2"))
        val m = (r as MeasureResult.Ok).measurement
        assertEquals(72.5f, m.weightKg!!, 0.001f)
        assertEquals(null, m.waistCm)
        assertEquals(18.2f, m.bodyFatPct!!, 0.001f)
        assertEquals(10L, m.epochDay)
    }
    @Test fun allBlankIsEmpty() {
        assertEquals(MeasureResult.Empty, MeasureInput.build(1, MeasureField.entries.associateWith { "" }))
    }
    @Test fun outOfRangeAndGarbageAreInvalid() {
        assertEquals(MeasureResult.Invalid(MeasureField.WEIGHT), MeasureInput.build(1, mapOf(MeasureField.WEIGHT to "5")))
        assertEquals(MeasureResult.Invalid(MeasureField.ARM), MeasureInput.build(1, mapOf(MeasureField.ARM to "abc")))
        assertEquals(MeasureResult.Invalid(MeasureField.FAT), MeasureInput.build(1, mapOf(MeasureField.WEIGHT to "70", MeasureField.FAT to "90")))
        assertEquals(MeasureResult.Invalid(MeasureField.HIP), MeasureInput.build(1, mapOf(MeasureField.HIP to "NaN")))
    }
    @Test fun weightSeriesIsChronologicalAndSkipsMissing() {
        val items = listOf(
            BodyMeasurement(id = 3, epochDay = 30, weightKg = 70f),
            BodyMeasurement(id = 2, epochDay = 20, waistCm = 80f),
            BodyMeasurement(id = 1, epochDay = 10, weightKg = 72f),
        )
        assertEquals(listOf(72f, 70f), weightSeries(items))
        assertTrue(weightSeries(emptyList()).isEmpty())
    }

    @Test fun chartPositionsFollowRealDates() {
        val items = listOf(
            BodyMeasurement(id = 1, epochDay = 100, weightKg = 72f),
            BodyMeasurement(id = 2, epochDay = 101, weightKg = 71f),
            BodyMeasurement(id = 3, epochDay = 110, weightKg = 70f),
        )
        val pts = weightPoints(items)
        assertEquals(listOf(0f, 0.1f, 1f), pts.map { chartX(it.first, 100, 110) })
        assertEquals(0.5f, chartX(5, 5, 5), 0f)
    }
}
