package com.kryll.homefit.domain

import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Exporta el historial de entrenos a CSV (UTF-8, coma, punto decimal, fechas ISO), una fila por serie registrada, de la más antigua a la más reciente. */
object WorkoutCsv {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun build(
        sessions: List<Pair<WorkoutSession, List<SetLog>>>,
        routineNames: Map<Long, String> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val sb = StringBuilder("fecha,hora,rutina,duracion_min,ejercicio,serie,repeticiones,peso_kg,segundos,rpe,notas\n")
        sessions.sortedBy { it.first.startMillis }.forEach { (s, sets) ->
            val start = Instant.ofEpochMilli(s.startMillis).atZone(zone)
            val day = DateTimeFormatter.ISO_LOCAL_DATE.format(start)
            val time = TIME.format(start)
            val routine = cell(s.routineId?.let { routineNames[it] } ?: "")
            val durMin = String.format(Locale.ROOT, "%.1f", s.durationSeconds / 60f)
            val notes = cell(s.notes)
            sets.sortedBy { it.id }.forEach { set ->
                sb.append(day).append(',').append(time).append(',').append(routine).append(',')
                    .append(durMin).append(',').append(set.exerciseId).append(',')
                    .append(set.setIndex + 1).append(',')
                    .append(set.reps?.toString() ?: "").append(',')
                    .append(set.weightKg?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "").append(',')
                    .append(set.seconds?.toString() ?: "").append(',')
                    .append(set.rpe?.toString() ?: "").append(',')
                    .append(notes).append('\n')
            }
        }
        return sb.toString()
    }

    /** Envuelve en comillas si el texto lleva coma, comilla o salto de línea; duplica las comillas internas. */
    private fun cell(raw: String): String =
        if (raw.any { it == ',' || it == '"' || it == '\n' }) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
}
