package com.kryll.homefit.domain

/** Cálculo de IMC. Solo informativo: no es un diagnóstico. */
object Bmi {
    fun compute(weightKg: Float, heightCm: Float): Float? {
        if (weightKg <= 0f || heightCm <= 0f) return null
        val m = heightCm / 100f
        return weightKg / (m * m)
    }
}
