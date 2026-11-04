package com.kryll.homefit

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryll.homefit.audio.CuePlayer
import com.kryll.homefit.domain.Cue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CueAudioFocusTest {
    @Test fun callModeDoesNotRequestAudioFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_IN_CALL
        val before = shadowOf(audio).lastAudioFocusRequest
        val player = CuePlayer(context)
        player.play(Cue.Countdown(3))
        assertEquals(before, shadowOf(audio).lastAudioFocusRequest)
        player.release()
        audio.mode = AudioManager.MODE_NORMAL
    }
}
