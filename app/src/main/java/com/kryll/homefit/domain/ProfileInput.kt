package com.kryll.homefit.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Validación del formulario de perfil. Devuelve los campos con error (vacío = válido). */
object ProfileInput {
    enum class Field { NAME, HEIGHT, WEIGHT, BIRTH, DAYS, MINUTES }

    /** Convierte día/mes/año a epochDay; null si la fecha no existe. */
    fun birthEpochDay(day: Int?, month: Int?, year: Int?): Long? {
        if (day == null || month == null || year == null) return null
        return try { LocalDate.of(year, month, day).toEpochDay() } catch (e: DateTimeException) { null }
    }

    /** Edad en años cumplidos a [today]. Se calcula siempre desde la fecha guardada. */
    fun ageYears(birthEpochDay: Long, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.YEARS.between(LocalDate.ofEpochDay(birthEpochDay), today).toInt()

    fun validate(
        name: String, heightCm: Float?, weightKg: Float?, birthEpochDay: Long?, daysMask: Int, minutes: Int?,
        today: LocalDate = LocalDate.now(),
    ): Set<Field> = buildSet {
        if (name.isBlank()) add(Field.NAME)
        if (heightCm == null || heightCm !in 100f..250f) add(Field.HEIGHT)
        if (weightKg == null || weightKg !in 30f..300f) add(Field.WEIGHT)
        if (birthEpochDay == null || ageYears(birthEpochDay, today) !in 14..100) add(Field.BIRTH)
        if (daysMask and 0x7F == 0) add(Field.DAYS)
        if (minutes == null || minutes !in 10..180) add(Field.MINUTES)
    }

    fun bmiCategory(bmi: Float): String = when {
        bmi < 18.5f -> "bajo"
        bmi < 25f -> "normal"
        bmi < 30f -> "alto"
        else -> "muy alto"
    }
}
