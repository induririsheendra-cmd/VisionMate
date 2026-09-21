package com.rishi.visionmate.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rishi.visionmate.services.speech.SpeechToTextManager
import com.rishi.visionmate.services.speech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceUiState(
    val isListening: Boolean = false,
    val lastRecognizedText: String = "",
    val lastSpokenResponse: String = "Welcome to VisionMate. Tap the voice button to speak.",
    val errorMessage: String? = null
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val ttsManager = TextToSpeechManager(application)
    private var sttManager: SpeechToTextManager? = null

    init {
        sttManager = SpeechToTextManager(
            context = application,
            onResult = ::handleRecognizedSpeech,
            onError = ::handleSpeechError,
            onListeningStateChanged = { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        )
        // Initial greeting
        speakResponse("Welcome to VisionMate. Tap or activate speech to begin.")
    }

    fun startListening() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        sttManager?.startListening()
    }

    fun stopListening() {
        sttManager?.stopListening()
    }

    fun stopSpeech() {
        ttsManager.stop()
    }

    fun speakResponse(text: String, flush: Boolean = true) {
        _uiState.value = _uiState.value.copy(lastSpokenResponse = text)
        ttsManager.speak(text, flush)
    }

    private fun handleRecognizedSpeech(recognizedText: String) {
        _uiState.value = _uiState.value.copy(lastRecognizedText = recognizedText)

        val cleanText = recognizedText.lowercase().trim()
        val response = when {
            cleanText.contains("hello") || cleanText.contains("hi visionmate") -> {
                "Hello! I am VisionMate, your accessibility companion. How can I help you today?"
            }
            cleanText.contains("read") -> {
                "Read Mode requested. Point your camera at any document or text."
            }
            cleanText.contains("around me") || cleanText.contains("what is in front") || cleanText.contains("vision") -> {
                "Vision Mode requested. Point your camera at your surroundings."
            }
            cleanText.contains("help") -> {
                "You can say: 'What is around me', 'Read this', 'Medication', or 'Help'."
            }
            cleanText.contains("stop") -> {
                ttsManager.stop()
                "Stopped audio playback."
            }
            else -> {
                "I heard: '$recognizedText'. I am ready to help you."
            }
        }

        speakResponse(response)
    }

    private fun handleSpeechError(errorMsg: String) {
        _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
        speakResponse(errorMsg)
    }

    override fun onCleared() {
        super.onCleared()
        sttManager?.destroy()
        ttsManager.shutdown()
    }
}
