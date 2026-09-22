package com.rishi.visionmate.ui.voice

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rishi.visionmate.domain.usecase.AnalyzeSceneUseCase
import com.rishi.visionmate.services.camera.CameraManager
import com.rishi.visionmate.services.speech.SpeechToTextManager
import com.rishi.visionmate.services.speech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VoiceUiState(
    val isListening: Boolean = false,
    val isCameraActive: Boolean = false,
    val isTorchOn: Boolean = false,
    val isAnalyzing: Boolean = false,
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
    private val analyzeSceneUseCase = AnalyzeSceneUseCase()

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
            toggleTorch(false)
            speakResponse("Camera closed.")
        }
    }

    fun toggleTorch(enable: Boolean) {
        cameraManager.setTorch(enable) { error ->
            speakResponse(error)
        }
        val newState = cameraManager.isTorchOn
        _uiState.value = _uiState.value.copy(isTorchOn = newState)
        if (newState) {
            speakResponse("Torch turned on.")
        } else if (!enable) {
            speakResponse("Torch turned off.")
        }
    }

    fun handleCapturedImage(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            capturedBitmap = bitmap,
            isAnalyzing = true,
            errorMessage = null
        )
        speakResponse("Photo captured. Analyzing your surroundings, please wait...")

        viewModelScope.launch {
            val result = analyzeSceneUseCase(bitmap)
            _uiState.value = _uiState.value.copy(isAnalyzing = false)

            result.onSuccess { description ->
                speakResponse(description)
            }.onFailure { error ->
                val userMsg = if (error.message?.contains("API key") == true) {
                    "API key is not configured in local.properties. Please add GEMINI_API_KEY."
                } else {
                    "Unable to analyze image. Please check your internet connection."
                }
                _uiState.value = _uiState.value.copy(errorMessage = userMsg)
                speakResponse(userMsg)
            }
        }
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
            cleanText.contains("torch on") || cleanText.contains("flashlight on") || cleanText.contains("turn on light") || cleanText.contains("light on") -> {
                if (!_uiState.value.isCameraActive) {
                    toggleCamera(true)
                }
                toggleTorch(true)
            }
            cleanText.contains("torch off") || cleanText.contains("flashlight off") || cleanText.contains("turn off light") || cleanText.contains("light off") -> {
                toggleTorch(false)
            }
            cleanText.contains("around me") || cleanText.contains("what is in front") || cleanText.contains("describe") || cleanText.contains("vision") -> {
                if (!_uiState.value.isCameraActive) {
                    toggleCamera(true)
                }
                speakResponse("Capturing scene for analysis.")
                cameraManager.takePicture(
                    onImageCaptured = { bitmap -> handleCapturedImage(bitmap) },
                    onError = { err -> speakResponse(err) }
                )
            }
            cleanText.contains("read") -> {
                toggleCamera(true)
                speakResponse("Read mode active. Point your camera at text and tap Capture.")
            }
            cleanText.contains("close camera") || cleanText.contains("hide camera") -> {
                toggleCamera(false)
            }
            cleanText.contains("help") -> {
                speakResponse("You can say: 'What is around me', 'Turn on light', 'Read this', or 'Help'.")
            }
            cleanText.contains("stop") -> {
                ttsManager.stop()
                speakResponse("Stopped audio playback.")
            }
            else -> {
                speakResponse("I heard: '$recognizedText'. Say 'What is around me' or 'Help' for options.")
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
