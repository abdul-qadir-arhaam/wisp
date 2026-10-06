package com.wisp.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Text-to-Speech manager for voice capture confirmation.
 * Rule: Spoken confirmation ("Got it, saved") is uttered ONLY when the input method was voice.
 * Reference: PRD.md Section 6.1.2
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    /**
     * Speaks the standard voice confirmation phrase.
     */
    fun speakConfirmation(message: String = "Got it, saved") {
        if (isInitialized) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "wisp_voice_confirm")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
