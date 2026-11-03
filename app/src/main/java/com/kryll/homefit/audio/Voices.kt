package com.kryll.homefit.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Nombres de las voces TTS en español instaladas en el dispositivo.
 * Crea un motor efímero solo para consultarlas; devuelve lista vacía si el motor no responde.
 */
suspend fun listSpanishVoices(context: Context): List<String> = suspendCancellableCoroutine { cont ->
    var tts: TextToSpeech? = null
    tts = TextToSpeech(context) { status ->
        val engine = tts
        val names = if (status == TextToSpeech.SUCCESS && engine != null) {
            runCatching {
                engine.voices?.filter { it.locale.language == "es" }?.map { it.name }?.sorted()
            }.getOrNull().orEmpty()
        } else emptyList()
        engine?.shutdown()
        tts = null
        if (cont.isActive) cont.resume(names)
    }
    cont.invokeOnCancellation { tts?.shutdown() }
}
