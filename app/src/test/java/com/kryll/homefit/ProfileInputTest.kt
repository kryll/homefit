package com.kryll.homefit

import com.kryll.homefit.domain.ProfileInput
import com.kryll.homefit.domain.ProfileInput.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInputTest {
    @Test fun validProfileHasNoErrors() =
        assertTrue(ProfileInput.validate("Ana", 170f, 65f, 30, 0b101, 30).isEmpty())

    @Test fun reportsEachInvalidField() {
        val e = ProfileInput.validate(" ", 50f, null, 5, 0, 5)
        assertEquals(setOf(Field.NAME, Field.HEIGHT, Field.WEIGHT, Field.AGE, Field.DAYS, Field.MINUTES), e)
    }

    @Test fun bmiCategories() {
        assertEquals("bajo", ProfileInput.bmiCategory(17f))
        assertEquals("normal", ProfileInput.bmiCategory(22f))
        assertEquals("alto", ProfileInput.bmiCategory(27f))
        assertEquals("muy alto", ProfileInput.bmiCategory(31f))
    }
}
