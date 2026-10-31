package com.mcqtracker.android

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper around Android's offline [TextToSpeech] engine.
 *
 * The engine is supplied by the device; the app itself requests no network
 * permission. If no engine is installed, [speak] silently does nothing.
 */
class SpeechController(context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching { engine?.language = Locale.getDefault() }
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        runCatching {
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mcq-${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    fun shutdown() {
        ready = false
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
