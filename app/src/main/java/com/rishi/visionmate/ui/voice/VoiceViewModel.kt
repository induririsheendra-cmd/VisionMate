package com.rishi.visionmate.ui.voice

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.rishi.visionmate.services.camera.CameraManager
import com.rishi.visionmate.services.speech.SpeechToTextManager
import com.rishi.visionmate.services.speech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceUiState(
    val isListening: Boolean = false,
    val isCameraActive: Boolean = false,
    val lastRecognizedText: String = "",
    val lastSpokenResponse: String = "Welcome to VisionMate. Tap the voice button or camera button to begin.",
    val errorMessage: String? = null,
    val capturedBitmap: Bitmap? = null
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val ttsManager = TextToSpeechManager(application)
    private var sttManager: SpeechToTextManager? = null
    val cameraManager: CameraManager = CameraManager(application)

    init {
        sttManager = SpeechToTextManager(
            context = application,
            onResult = ::handleRecognizedSpeech,
            onError = ::handleSpeechError,
            onListeningStateChanged = { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        )
        // Initial spoken welcome
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

    fun toggleCamera(active: Boolean) {
        _uiState.value = _uiState.value.copy(isCameraActive = active)
        if (active) {
            speakResponse("Camera activated. Point your camera at your surroundings or a document, then tap Capture.")
        } else {
            speakResponse("Camera closed.")
        }
    }

    fun handleCapturedImage(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(capturedBitmap = bitmap)
        speakResponse("Photo captured. Ready for AI vision analysis.")
    }

    fun speakResponse(text: String, flush: Boolean = true) {
        _uiState.value = _uiState.value.copy(lastSpokenResponse = text)
        ttsManager.speak(text, flush)
    }

    private fun handleRecognizedSpeech(recognizedText: String) {
        _uiState.value = _uiState.value.copy(lastRecognizedText = recognizedText)

        val cleanText = recognizedText.lowercase().trim()
        when {
            cleanText.contains("hello") || cleanText.contains("hi visionmate") -> {
                speakResponse("Hello! I am VisionMate, your accessibility companion. How can I help you today?")
            }
            cleanText.contains("read") || cleanText.contains("camera") || cleanText.contains("around me") || cleanText.contains("vision") -> {
                toggleCamera(true)
            }
            cleanText.contains("close camera") || cleanText.contains("hide camera") -> {
                toggleCamera(false)
            }
            cleanText.contains("help") -> {
                speakResponse("You can say: 'Open camera', 'What is around me', 'Read this', or 'Help'.")
            }
            cleanText.contains("stop") -> {
                ttsManager.stop()
                speakResponse("Stopped audio playback.")
            }
            else -> {
                speakResponse("I heard: '$recognizedText'. Say 'Open camera' or 'Help' for options.")
            }
        }
    }

    private fun handleSpeechError(errorMsg: String) {
        _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
        speakResponse(errorMsg)
    }

    override fun onCleared() {
        super.onCleared()
        sttManager?.destroy()
        ttsManager.shutdown()
        cameraManager.unbind()
    }
}
