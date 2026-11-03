package com.kryll.homefit

import com.kryll.homefit.domain.ProfileInput
import com.kryll.homefit.domain.ProfileInput.Field
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInputTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val birth = LocalDate.of(1994, 12, 23).toEpochDay()

    @Test fun validProfileHasNoErrors() =
        assertTrue(ProfileInput.validate("Ana", 170f, 65f, birth, 0b101, 30, today).isEmpty())

    @Test fun reportsEachInvalidField() {
        val e = ProfileInput.validate(" ", 50f, null, null, 0, 5, today)
        assertEquals(setOf(Field.NAME, Field.HEIGHT, Field.WEIGHT, Field.BIRTH, Field.DAYS, Field.MINUTES), e)
    }

    @Test fun birthDateMustExist() {
        assertNull(ProfileInput.birthEpochDay(31, 2, 2000))
        assertEquals(LocalDate.of(2000, 2, 29).toEpochDay(), ProfileInput.birthEpochDay(29, 2, 2000))
    }

    @Test fun ageIsComputedFromStoredDate() {
        assertEquals(31, ProfileInput.ageYears(birth, today))
        assertEquals(32, ProfileInput.ageYears(birth, LocalDate.of(2026, 12, 23)))
    }

    @Test fun rejectsTooYoung() =
        assertTrue(Field.BIRTH in ProfileInput.validate("Ana", 170f, 65f, LocalDate.of(2020, 1, 1).toEpochDay(), 1, 30, today))

    @Test fun bmiCategories() {
        assertEquals("bajo", ProfileInput.bmiCategory(17f))
        assertEquals("normal", ProfileInput.bmiCategory(22f))
        assertEquals("alto", ProfileInput.bmiCategory(27f))
        assertEquals("muy alto", ProfileInput.bmiCategory(31f))
    }
}
