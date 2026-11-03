package com.kryll.homefit.domain

import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit
import java.util.Locale

/** La base de datos guarda siempre kg y cm; las unidades del usuario solo afectan a lo que se ve y se escribe. */
object Units {
    private const val LB_PER_KG = 2.2046226f
    private const val IN_PER_CM = 0.3937008f

    fun weightToDisplay(kg: Float, u: WeightUnit): Float = if (u == WeightUnit.LB) kg * LB_PER_KG else kg
    fun weightFromDisplay(v: Float, u: WeightUnit): Float = if (u == WeightUnit.LB) v / LB_PER_KG else v
    fun lengthToDisplay(cm: Float, u: LengthUnit): Float = if (u == LengthUnit.IN) cm * IN_PER_CM else cm
    fun lengthFromDisplay(v: Float, u: LengthUnit): Float = if (u == LengthUnit.IN) v / IN_PER_CM else v

    fun weightLabel(u: WeightUnit) = if (u == WeightUnit.LB) "lb" else "kg"
    fun lengthLabel(u: LengthUnit) = if (u == LengthUnit.IN) "in" else "cm"

    /** Una cifra decimal con coma, sin ",0" final. */
    fun format(v: Float): String = String.format(Locale("es", "ES"), "%.1f", v).removeSuffix(",0")
}
