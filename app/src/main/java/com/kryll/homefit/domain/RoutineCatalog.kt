package com.kryll.homefit.domain

import org.json.JSONArray

data class RoutineSeedItem(val exerciseId: String, val sets: Int, val reps: Int?, val seconds: Int?, val restSeconds: Int)
data class RoutineSeed(val name: String, val goal: String, val level: String, val type: String, val notes: String, val items: List<RoutineSeedItem>)

/** Rutinas predefinidas (assets/routines.json). */
object RoutineCatalog {
    fun parse(json: String): List<RoutineSeed> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val its = o.getJSONArray("items")
            RoutineSeed(
                o.getString("name"), o.getString("goal"), o.getString("level"), o.getString("type"), o.getString("notes"),
                (0 until its.length()).map { j ->
                    val it = its.getJSONObject(j)
                    RoutineSeedItem(
                        it.getString("exerciseId"), it.getInt("sets"),
                        if (it.isNull("reps")) null else it.getInt("reps"),
                        if (it.isNull("seconds")) null else it.getInt("seconds"),
                        it.getInt("restSeconds"),
                    )
                },
            )
        }
    }
}
