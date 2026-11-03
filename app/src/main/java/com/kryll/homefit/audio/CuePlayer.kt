package com.kryll.homefit.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kryll.homefit.domain.Beep
import com.kryll.homefit.domain.Cue
import com.kryll.homefit.domain.toSound
import java.util.Locale

/**
 * Reproduce los avisos del entreno: pitidos con ToneGenerator y voz en español con TextToSpeech.
 * Pide foco de audio con atenuación (ducking) mientras habla, así la música baja y vuelve.
 * Si el móvil no tiene voz en español, solo suenan los pitidos.
 */
class CuePlayer(context: Context, var speechEnabled: Boolean = true, var beepEnabled: Boolean = true) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    private var tonePercent = 80
    private var speechRate = 1.0f
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener(::onFocusChange)
        .build()

    private val abandon = Runnable { if (pending == 0) audio.abandonAudioFocusRequest(focus) }

    private fun onFocusChange(change: Int) {
        if (change < 0) {
            tts?.stop()
            tone?.stopTone()
            pending = 0
            handler.removeCallbacks(abandon)
        }
    }

    init {
        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val r = engine.setLanguage(Locale("es", "ES"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setAudioAttributes(attrs)
                engine.setSpeechRate(speechRate)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = finished()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finished()
                })
            }
        }
    }

    /** Aplica los ajustes del usuario (voz, volumen de pitidos y velocidad); se puede llamar con el entreno en marcha. */
    fun apply(s: com.kryll.homefit.domain.CueSettings) {
        speechEnabled = s.speech
        beepEnabled = s.beep
        speechRate = s.speechRate
        if (ttsReady) tts?.setSpeechRate(speechRate)
        if (s.beep && s.beepPercent != tonePercent) {
            tone?.release()
            tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, s.beepPercent) }.getOrNull()
            tonePercent = s.beepPercent
        }
    }

    private fun finished() {
        pending = (pending - 1).coerceAtLeast(0)
        if (pending == 0) audio.abandonAudioFocusRequest(focus)
    }

    fun play(cue: Cue) {
        val sound = cue.toSound()
        val willSpeak = speechEnabled && ttsReady && sound.speech != null
        val willBeep = beepEnabled && sound.beep != Beep.NONE
        if (!willSpeak && !willBeep) return
        // Una llamada o una denegación de foco nunca se sobreponen con los avisos.
        if (audio.mode != AudioManager.MODE_NORMAL) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        handler.removeCallbacks(abandon)
        if (beepEnabled) when (sound.beep) {
            Beep.TICK -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Beep.GO -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            Beep.DONE -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            Beep.NONE -> Unit
        }
        val text = sound.speech
        if (speechEnabled && ttsReady && text != null) {
            pending++
            val queue = if (cue is Cue.Go || cue is Cue.Rest) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (queue == TextToSpeech.QUEUE_FLUSH) pending = 1
            val result = tts?.speak(text, queue, null, "cue-${System.nanoTime()}")
            if (result != TextToSpeech.SUCCESS) finished()
        } else {
            handler.postDelayed(abandon, 400L)
        }
    }

    fun release() {
        handler.removeCallbacks(abandon)
        tts?.stop(); tts?.shutdown(); tts = null
        tone?.release()
        audio.abandonAudioFocusRequest(focus)
    }
}
