package com.kryll.homefit

import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.domain.MeasureCsv
import com.kryll.homefit.domain.MeasureField
import com.kryll.homefit.domain.MeasureInput
import com.kryll.homefit.domain.MeasureResult
import com.kryll.homefit.domain.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureUnitsTest {
    @Test fun conversionsRoundTrip() {
        assertEquals(176.37f, Units.weightToDisplay(80f, WeightUnit.LB), 0.01f)
        assertEquals(80f, Units.weightFromDisplay(Units.weightToDisplay(80f, WeightUnit.LB), WeightUnit.LB), 0.001f)
        assertEquals(39.37f, Units.lengthToDisplay(100f, LengthUnit.IN), 0.01f)
        assertEquals(100f, Units.lengthFromDisplay(Units.lengthToDisplay(100f, LengthUnit.IN), LengthUnit.IN), 0.001f)
        assertEquals(80f, Units.weightToDisplay(80f, WeightUnit.KG), 0f)
    }

    @Test fun poundsAndInchesAreStoredAsKgAndCm() {
        val r = MeasureInput.build(10, mapOf(MeasureField.WEIGHT to "176,4", MeasureField.WAIST to "32"), WeightUnit.LB, LengthUnit.IN) as MeasureResult.Ok
        assertEquals(80.0f, r.measurement.weightKg!!, 0.05f)
        assertEquals(81.28f, r.measurement.waistCm!!, 0.05f)
    }

    @Test fun rangeIsCheckedAfterConversion() {
        // 30 lb = 13,6 kg: por debajo del mínimo de 20 kg
        assertEquals(MeasureResult.Invalid(MeasureField.WEIGHT), MeasureInput.build(10, mapOf(MeasureField.WEIGHT to "30"), WeightUnit.LB))
        assertTrue(MeasureInput.build(10, mapOf(MeasureField.WEIGHT to "30"), WeightUnit.KG) is MeasureResult.Ok)
    }

    @Test fun editKeepsIdAndDateAndUntouchedFieldsExactly() {
        val base = BodyMeasurement(id = 7, epochDay = 50, weightKg = 80f, waistCm = 81.3f)
        val pre = MeasureInput.prefill(base, WeightUnit.LB, LengthUnit.IN)
        assertEquals("176,4", pre[MeasureField.WEIGHT])
        // solo cambio la cintura; el peso conserva los 80 kg exactos aunque se vea 176,4 lb
        val texts = pre + (MeasureField.WAIST to "33")
        val r = MeasureInput.build(999, texts, WeightUnit.LB, LengthUnit.IN, base, pre) as MeasureResult.Ok
        assertEquals(7L, r.measurement.id)
        assertEquals(50L, r.measurement.epochDay)
        assertEquals(80f, r.measurement.weightKg!!, 0f)
        assertEquals(83.82f, r.measurement.waistCm!!, 0.05f)
    }

    @Test fun editCanClearAField() {
        val base = BodyMeasurement(id = 1, epochDay = 5, weightKg = 70f, waistCm = 80f)
        val pre = MeasureInput.prefill(base)
        val r = MeasureInput.build(9, pre + (MeasureField.WAIST to ""), base = base, prefill = pre) as MeasureResult.Ok
        assertNull(r.measurement.waistCm)
        assertEquals(70f, r.measurement.weightKg!!, 0f)
        assertEquals(MeasureResult.Empty, MeasureInput.build(9, mapOf(MeasureField.WEIGHT to "", MeasureField.WAIST to ""), base = base, prefill = pre))
    }

    @Test fun csvHasHeaderOrderAndUnits() {
        val items = listOf(
            BodyMeasurement(id = 2, epochDay = 20401, weightKg = 79.5f, bodyFatPct = 18f),
            BodyMeasurement(id = 1, epochDay = 20400, weightKg = 80f, waistCm = 81f),
        )
        val kg = MeasureCsv.build(items).lines()
        assertEquals("fecha,peso_kg,cintura_cm,pecho_cm,brazo_cm,muslo_cm,cadera_cm,grasa_pct", kg[0])
        assertEquals("2025-11-08,80.0,81.0,,,,,", kg[1])
        assertEquals("2025-11-09,79.5,,,,,,18.0", kg[2])
        val lb = MeasureCsv.build(items, WeightUnit.LB, LengthUnit.IN).lines()
        assertEquals("fecha,peso_lb,cintura_in,pecho_in,brazo_in,muslo_in,cadera_in,grasa_pct", lb[0])
        assertEquals("2025-11-08,176.4,31.9,,,,,", lb[1])
    }
}
