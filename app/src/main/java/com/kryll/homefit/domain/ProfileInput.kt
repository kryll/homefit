package com.kryll.homefit.domain

/** Validación del formulario de perfil. Devuelve los campos con error (vacío = válido). */
object ProfileInput {
    enum class Field { NAME, HEIGHT, WEIGHT, AGE, DAYS, MINUTES }

    fun validate(
        name: String, heightCm: Float?, weightKg: Float?, age: Int?, daysMask: Int, minutes: Int?,
    ): Set<Field> = buildSet {
        if (name.isBlank()) add(Field.NAME)
        if (heightCm == null || heightCm !in 100f..250f) add(Field.HEIGHT)
        if (weightKg == null || weightKg !in 30f..300f) add(Field.WEIGHT)
        if (age == null || age !in 14..100) add(Field.AGE)
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
