package com.kryll.homefit.domain

import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit

enum class MeasureField(val min: Float, val max: Float) {
    WEIGHT(20f, 400f), WAIST(10f, 300f), CHEST(10f, 300f), ARM(10f, 100f), THIGH(10f, 200f), HIP(10f, 300f), FAT(2f, 70f),
}

sealed interface MeasureResult {
    data class Ok(val measurement: BodyMeasurement) : MeasureResult
    data class Invalid(val field: MeasureField) : MeasureResult
    data object Empty : MeasureResult
}

object MeasureInput {
    private fun isLength(f: MeasureField) = f != MeasureField.WEIGHT && f != MeasureField.FAT

    /** Valor guardado (kg/cm/%) de un campo, o null. */
    fun stored(m: BodyMeasurement, f: MeasureField): Float? = when (f) {
        MeasureField.WEIGHT -> m.weightKg; MeasureField.WAIST -> m.waistCm; MeasureField.CHEST -> m.chestCm
        MeasureField.ARM -> m.armCm; MeasureField.THIGH -> m.thighCm; MeasureField.HIP -> m.hipCm; MeasureField.FAT -> m.bodyFatPct
    }

    /** Textos con los que se rellena el formulario al editar, en las unidades del usuario. */
    fun prefill(m: BodyMeasurement, wu: WeightUnit = WeightUnit.KG, lu: LengthUnit = LengthUnit.CM): Map<MeasureField, String> =
        MeasureField.entries.mapNotNull { f ->
            stored(m, f)?.let { v ->
                val shown = when {
                    f == MeasureField.WEIGHT -> Units.weightToDisplay(v, wu)
                    isLength(f) -> Units.lengthToDisplay(v, lu)
                    else -> v
                }
                f to Units.format(shown)
            }
        }.toMap()

    /**
     * Texto vacío = sin dato. Acepta coma o punto decimal. Los límites se aplican al valor ya convertido a kg/cm.
     * Al editar ([base] con su [prefill]), un campo cuyo texto no ha cambiado conserva el valor guardado exacto
     * (así convertir a lb/in y volver no desplaza el dato). Se conservan id y fecha de [base].
     */
    fun build(
        epochDay: Long, texts: Map<MeasureField, String>,
        wu: WeightUnit = WeightUnit.KG, lu: LengthUnit = LengthUnit.CM,
        base: BodyMeasurement? = null, prefill: Map<MeasureField, String> = emptyMap(),
    ): MeasureResult {
        val values = mutableMapOf<MeasureField, Float?>()
        for (f in MeasureField.entries) {
            val raw = texts[f].orEmpty().trim()
            if (raw.isEmpty()) { values[f] = null; continue }
            if (base != null && raw == prefill[f]?.trim()) { values[f] = stored(base, f); continue }
            val typed = raw.replace(',', '.').toFloatOrNull()
            if (typed == null || typed.isNaN() || typed.isInfinite()) return MeasureResult.Invalid(f)
            val v = when {
                f == MeasureField.WEIGHT -> Units.weightFromDisplay(typed, wu)
                isLength(f) -> Units.lengthFromDisplay(typed, lu)
                else -> typed
            }
            if (v < f.min || v > f.max) return MeasureResult.Invalid(f)
            values[f] = v
        }
        if (values.values.all { it == null }) return MeasureResult.Empty
        return MeasureResult.Ok(
            BodyMeasurement(
                id = base?.id ?: 0,
                epochDay = base?.epochDay ?: epochDay,
                weightKg = values[MeasureField.WEIGHT], waistCm = values[MeasureField.WAIST],
                chestCm = values[MeasureField.CHEST], armCm = values[MeasureField.ARM],
                thighCm = values[MeasureField.THIGH], hipCm = values[MeasureField.HIP],
                bodyFatPct = values[MeasureField.FAT],
            ),
        )
    }
}
