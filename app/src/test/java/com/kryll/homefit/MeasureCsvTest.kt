package com.kryll.homefit

import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.domain.MeasureCsv
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureCsvTest {
    private fun m(day: String, weight: Float? = null, waist: Float? = null, fat: Float? = null) =
        BodyMeasurement(epochDay = LocalDate.parse(day).toEpochDay(), weightKg = weight, waistCm = waist, bodyFatPct = fat)

    @Test fun roundTripKeepsData() {
        val items = listOf(m("2026-10-01", 80.5f, 90f, 21f), m("2026-11-01", 79f, null, null))
        val parsed = MeasureCsv.parse(MeasureCsv.build(items))
        assertEquals(2, parsed.rows.size)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(items[0].epochDay, parsed.rows[0].epochDay)
        assertEquals(80.5f, parsed.rows[0].weightKg!!, 0.01f)
        assertEquals(90f, parsed.rows[0].waistCm!!, 0.01f)
        assertEquals(21f, parsed.rows[0].bodyFatPct!!, 0.01f)
        assertEquals(null, parsed.rows[1].waistCm)
    }

    @Test fun parsesOtherUnits() {
        val csv = "fecha,peso_lb,cintura_in,pecho_in,brazo_in,muslo_in,cadera_in,grasa_pct\n2026-10-01,39.4,,,,,,20.5\n"
        val parsed = MeasureCsv.parse(csv, WeightUnit.LB, LengthUnit.IN)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(1, parsed.rows.size)
        assertEquals(39.4f / 2.20462f, parsed.rows[0].weightKg!!, 0.05f)
        assertEquals(20.5f, parsed.rows[0].bodyFatPct!!, 0.01f)
    }

    @Test fun extraColumnsGetClearError() {
        val csv = "fecha,peso_kg,cintura_cm,pecho_cm,brazo_cm,muslo_cm,cadera_cm,grasa_pct\n2026-10-01,39,4,,,,,,\n"
        val parsed = MeasureCsv.parse(csv)
        assertTrue(parsed.rows.isEmpty())
        assertEquals(listOf("línea 2: columnas de más (usa punto decimal)"), parsed.errors)
    }

    @Test fun skipsEmptyRowsAndReportsBadLines() {
        val csv = "fecha,peso_kg,cintura_cm,pecho_cm,brazo_cm,muslo_cm,cadera_cm,grasa_pct\n" +
            "2026-10-01,,,,,,,\n" +
            "no-fecha,80,,,,,,\n" +
            "2026-10-03,81,,,,,,\n"
        val parsed = MeasureCsv.parse(csv)
        assertEquals(1, parsed.rows.size)
        assertEquals(listOf("línea 3: fecha inválida"), parsed.errors)
    }

    @Test fun rejectsMissingHeader() {
        val parsed = MeasureCsv.parse("a,b,c\n1,2,3\n")
        assertTrue(parsed.rows.isEmpty())
        assertEquals(listOf("cabecera"), parsed.errors)
    }
}
