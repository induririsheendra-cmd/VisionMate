package com.rishi.visionmate.services.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val pendingSpeechQueue = mutableListOf<Pair<String, Boolean>>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }

            // Set TTS audio attributes for accessibility speech output
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            isInitialized = true
            Log.d("TextToSpeechManager", "TTS successfully initialized with accessibility audio attributes")

            ensureVolumeNotMuted()

            // Flush pending speech calls
            for ((text, flush) in pendingSpeechQueue) {
                speak(text, flush)
            }
            pendingSpeechQueue.clear()
        } else {
            Log.e("TextToSpeechManager", "TTS Initialization failed with status: $status")
        }
    }

    /**
     * Checks device media/accessibility stream volume and raises it to an audible level if muted.
     */
    fun ensureVolumeNotMuted() {
        try {
            audioManager?.let { am ->
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (currentVolume <= 1) {
                    val targetVolume = (maxVolume * 0.65).toInt().coerceAtLeast(3)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
                    Log.d("TextToSpeechManager", "Audio stream volume was low/muted. Raised volume to $targetVolume/$maxVolume")

                    // Haptic buzz warning to inform visually impaired user that volume was adjusted
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(200)
                }
            }
        } catch (e: Exception) {
            Log.e("TextToSpeechManager", "Error ensuring volume is unmuted", e)
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return

        ensureVolumeNotMuted()

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
