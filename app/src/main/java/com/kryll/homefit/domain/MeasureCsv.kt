package com.kryll.homefit.domain

import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit
import java.time.LocalDate
import java.util.Locale

/** Exporta las medidas a CSV (UTF-8, coma, punto decimal, fecha ISO), de la más antigua a la más reciente, en las unidades del usuario. */
object MeasureCsv {
    fun build(items: List<BodyMeasurement>, wu: WeightUnit = WeightUnit.KG, lu: LengthUnit = LengthUnit.CM): String {
        val w = Units.weightLabel(wu)
        val l = Units.lengthLabel(lu)
        val sb = StringBuilder("fecha,peso_$w,cintura_$l,pecho_$l,brazo_$l,muslo_$l,cadera_$l,grasa_pct\n")
        fun cell(v: Float?) = v?.let { String.format(Locale.ROOT, "%.1f", it) } ?: ""
        items.sortedWith(compareBy({ it.epochDay }, { it.id })).forEach { m ->
            sb.append(LocalDate.ofEpochDay(m.epochDay)).append(',')
                .append(cell(m.weightKg?.let { Units.weightToDisplay(it, wu) })).append(',')
                .append(cell(m.waistCm?.let { Units.lengthToDisplay(it, lu) })).append(',')
                .append(cell(m.chestCm?.let { Units.lengthToDisplay(it, lu) })).append(',')
                .append(cell(m.armCm?.let { Units.lengthToDisplay(it, lu) })).append(',')
                .append(cell(m.thighCm?.let { Units.lengthToDisplay(it, lu) })).append(',')
                .append(cell(m.hipCm?.let { Units.lengthToDisplay(it, lu) })).append(',')
                .append(cell(m.bodyFatPct)).append('\n')
        }
        return sb.toString()
    }
}
