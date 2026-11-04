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

    /** Resultado de leer un CSV: filas válidas y errores por línea. */
    data class ParseResult(val rows: List<BodyMeasurement>, val errors: List<String>)

    /**
     * Lee un CSV con el formato de [build] (cabecera fecha,peso_...). Acepta punto o coma decimal.
     * Las filas sin ninguna medida se ignoran; fecha o número inválido se anota en errors y no detiene el resto.
     */
    fun parse(text: String, wu: WeightUnit = WeightUnit.KG, lu: LengthUnit = LengthUnit.CM): ParseResult {
        val lines = text.lines().filter { it.isNotBlank() }
        val errors = mutableListOf<String>()
        val rows = mutableListOf<BodyMeasurement>()
        if (lines.isEmpty() || !lines.first().trimStart().lowercase(Locale.ROOT).startsWith("fecha,")) {
            return ParseResult(emptyList(), listOf("cabecera"))
        }
        fun num(cell: String, line: Int): Float? {
            val t = cell.trim()
            if (t.isEmpty()) return null
            val v = t.replace(',', '.').toFloatOrNull()
            if (v == null) errors += "línea $line: número inválido"
            return v
        }
        lines.drop(1).forEachIndexed { i, line ->
            val n = i + 2
            val cells = line.split(',')
            if (cells.size != 8) { errors += if (cells.size < 8) "línea $n: columnas incompletas" else "línea $n: columnas de más (usa punto decimal)"; return@forEachIndexed }
            val date = runCatching { LocalDate.parse(cells[0].trim()) }.getOrNull()
            if (date == null) { errors += "línea $n: fecha inválida"; return@forEachIndexed }
            val m = BodyMeasurement(
                epochDay = date.toEpochDay(),
                weightKg = num(cells[1], n)?.let { Units.weightFromDisplay(it, wu) },
                waistCm = num(cells[2], n)?.let { Units.lengthFromDisplay(it, lu) },
                chestCm = num(cells[3], n)?.let { Units.lengthFromDisplay(it, lu) },
                armCm = num(cells[4], n)?.let { Units.lengthFromDisplay(it, lu) },
                thighCm = num(cells[5], n)?.let { Units.lengthFromDisplay(it, lu) },
                hipCm = num(cells[6], n)?.let { Units.lengthFromDisplay(it, lu) },
                bodyFatPct = num(cells[7], n),
            )
            if (listOf(m.weightKg, m.waistCm, m.chestCm, m.armCm, m.thighCm, m.hipCm, m.bodyFatPct).any { it != null }) rows += m
        }
        return ParseResult(rows, errors)
    }
}
