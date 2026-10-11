package com.mdkdw1.mypdf

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.KOREAN
            }
        }
    }

    fun speak(text: String, lang: String = "ko") {
        if (!ready) return
        tts?.language = when (lang) {
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            "zh" -> Locale.CHINESE
            else -> Locale.KOREAN
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mypdf_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    fun isReady(): Boolean = ready
}
