package com.kryll.homefit

import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.domain.CueSettings
import com.kryll.homefit.domain.SettingsRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    @Test fun volumeStepsByTenPercentAndClamps() {
        assertEquals(0.9f, SettingsRules.stepVolume(0.8f, 1), 0.0001f)
        assertEquals(1.0f, SettingsRules.stepVolume(1.0f, 1), 0.0001f)
        assertEquals(0.0f, SettingsRules.stepVolume(0.0f, -1), 0.0001f)
        assertEquals(0.7f, SettingsRules.stepVolume(0.8f, -1), 0.0001f)
    }

    @Test fun rateStepsByQuarterAndClamps() {
        assertEquals(1.25f, SettingsRules.stepRate(1.0f, 1), 0.0001f)
        assertEquals(0.75f, SettingsRules.stepRate(1.0f, -1), 0.0001f)
        assertEquals(2.0f, SettingsRules.stepRate(2.0f, 1), 0.0001f)
        assertEquals(0.5f, SettingsRules.stepRate(0.5f, -1), 0.0001f)
    }

    @Test fun cueSettingsFollowTheStoredSettings() {
        val c = CueSettings.from(AppSettings(beepVolume = 0.3f, voiceEnabled = false, ttsRate = 1.5f))
        assertFalse(c.speech)
        assertEquals(30, c.beepPercent)
        assertTrue(c.beep)
        assertEquals(1.5f, c.speechRate, 0.0001f)
    }

    @Test fun zeroVolumeMeansNoBeeps() = assertFalse(CueSettings.from(AppSettings(beepVolume = 0f)).beep)

    @Test fun outOfRangeStoredValuesAreClamped() {
        val c = CueSettings.from(AppSettings(beepVolume = 5f, ttsRate = 9f))
        assertEquals(100, c.beepPercent)
        assertEquals(2.0f, c.speechRate, 0.0001f)
    }

    @Test fun restStepsByFiveAndStaysInEditorRange() {
        assertEquals(50, SettingsRules.stepRest(45, 1))
        assertEquals(40, SettingsRules.stepRest(45, -1))
        assertEquals(0, SettingsRules.stepRest(0, -1))
        assertEquals(com.kryll.homefit.domain.RoutineDraft.MAX_REST, SettingsRules.stepRest(com.kryll.homefit.domain.RoutineDraft.MAX_REST, 1))
    }

    @Test fun countdownStepsByOneBetweenZeroAndFifteen() {
        assertEquals(6, SettingsRules.stepCountdown(5, 1))
        assertEquals(0, SettingsRules.stepCountdown(0, -1))
        assertEquals(15, SettingsRules.stepCountdown(15, 1))
    }
}
