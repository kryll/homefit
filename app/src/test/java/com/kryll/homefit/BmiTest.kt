package com.kryll.homefit

import com.kryll.homefit.domain.Bmi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BmiTest {
    @Test fun computesBmi() {
        assertEquals(22.86f, Bmi.compute(70f, 175f)!!, 0.01f)
    }

    @Test fun rejectsInvalidInput() {
        assertNull(Bmi.compute(0f, 175f))
        assertNull(Bmi.compute(70f, 0f))
    }
}
