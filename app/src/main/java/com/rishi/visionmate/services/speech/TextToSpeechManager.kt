package com.rishi.visionmate.services.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val pendingSpeechQueue = mutableListOf<Pair<String, Boolean>>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            isInitialized = true
            Log.d("TextToSpeechManager", "TTS successfully initialized")

            // Flush pending speech calls
            for ((text, flush) in pendingSpeechQueue) {
                speak(text, flush)
            }
            pendingSpeechQueue.clear()
        } else {
            Log.e("TextToSpeechManager", "TTS Initialization failed with status: $status")
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return

        if (!isInitialized) {
            pendingSpeechQueue.add(Pair(text, flush))
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "visionmate_tts_${System.currentTimeMillis()}"
        tts?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}
