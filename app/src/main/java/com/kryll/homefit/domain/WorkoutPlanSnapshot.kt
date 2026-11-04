package com.kryll.homefit.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializa el plan de una sesión para guardarlo con ella: la reanudación es fiel a lo planeado entonces,
 * aunque la rutina se edite después (series, reps, tiempos, orden o ejercicios repetidos incluidos).
 */
object WorkoutPlanSnapshot {
    fun toJson(plan: List<WorkItem>): String {
        val arr = JSONArray()
        plan.forEach { w ->
            val o = JSONObject()
            o.put("exerciseId", w.exerciseId)
            o.put("name", w.name)
            o.put("sets", w.sets)
            o.put("reps", w.reps ?: JSONObject.NULL)
            o.put("seconds", w.seconds ?: JSONObject.NULL)
            o.put("restSeconds", w.restSeconds)
            o.put("alternateSides", w.alternateSides)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<WorkItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WorkItem(
                o.getString("exerciseId"), o.getString("name"), o.getInt("sets"),
                if (o.isNull("reps")) null else o.getInt("reps"),
                if (o.isNull("seconds")) null else o.getInt("seconds"),
                o.getInt("restSeconds"), o.optBoolean("alternateSides"),
            )
        }
    }
}
