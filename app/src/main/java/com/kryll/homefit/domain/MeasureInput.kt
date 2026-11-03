package com.kryll.homefit.domain

import com.kryll.homefit.data.BodyMeasurement

enum class MeasureField(val min: Float, val max: Float) {
    WEIGHT(20f, 400f), WAIST(10f, 300f), CHEST(10f, 300f), ARM(10f, 100f), THIGH(10f, 200f), HIP(10f, 300f), FAT(2f, 70f),
}

sealed interface MeasureResult {
    data class Ok(val measurement: BodyMeasurement) : MeasureResult
    data class Invalid(val field: MeasureField) : MeasureResult
    data object Empty : MeasureResult
}

object MeasureInput {
    /** Texto vacío = sin dato. Acepta coma o punto decimal. Fuera de rango = inválido. */
    fun build(epochDay: Long, texts: Map<MeasureField, String>): MeasureResult {
        val values = mutableMapOf<MeasureField, Float?>()
        for (f in MeasureField.entries) {
            val raw = texts[f].orEmpty().trim()
            if (raw.isEmpty()) { values[f] = null; continue }
            val v = raw.replace(',', '.').toFloatOrNull()
            if (v == null || v.isNaN() || v.isInfinite() || v < f.min || v > f.max) return MeasureResult.Invalid(f)
            values[f] = v
        }
        if (values.values.all { it == null }) return MeasureResult.Empty
        return MeasureResult.Ok(
            BodyMeasurement(
                epochDay = epochDay,
                weightKg = values[MeasureField.WEIGHT], waistCm = values[MeasureField.WAIST],
                chestCm = values[MeasureField.CHEST], armCm = values[MeasureField.ARM],
                thighCm = values[MeasureField.THIGH], hipCm = values[MeasureField.HIP],
                bodyFatPct = values[MeasureField.FAT],
            ),
        )
    }
}
