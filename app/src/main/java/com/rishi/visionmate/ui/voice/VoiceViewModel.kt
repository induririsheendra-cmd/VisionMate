package com.rishi.visionmate.ui.voice

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rishi.visionmate.domain.usecase.AnalyzeSceneUseCase
import com.rishi.visionmate.domain.usecase.ReadTextUseCase
import com.rishi.visionmate.services.camera.CameraManager
import com.rishi.visionmate.services.speech.SpeechToTextManager
import com.rishi.visionmate.services.speech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppMode {
    VISION,
    READ
}

data class VoiceUiState(
    val isListening: Boolean = false,
    val isCameraActive: Boolean = false,
    val isTorchOn: Boolean = false,
    val isAnalyzing: Boolean = false,
    val activeMode: AppMode = AppMode.VISION,
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
    private val readTextUseCase = ReadTextUseCase()

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

    fun setMode(mode: AppMode) {
        _uiState.value = _uiState.value.copy(activeMode = mode)
        if (mode == AppMode.READ) {
            speakResponse("Read Mode selected. Point camera at document or text and tap Capture.")
        } else {
            speakResponse("Vision Mode selected. Point camera at your surroundings and tap Capture.")
        }
    }

    fun toggleCamera(active: Boolean) {
        _uiState.value = _uiState.value.copy(isCameraActive = active)
        if (active) {
            val modeName = if (_uiState.value.activeMode == AppMode.READ) "Read Mode" else "Vision Mode"
            speakResponse("Camera activated in $modeName. Point your camera and tap Capture.")
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

        if (_uiState.value.activeMode == AppMode.READ) {
            speakResponse("Photo captured. Reading text from document, please wait...")
            viewModelScope.launch {
                val result = readTextUseCase(bitmap)
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
                result.onSuccess { text ->
                    speakResponse(text)
                }.onFailure { error ->
                    val errorMsg = "Unable to read document: ${error.localizedMessage}"
                    _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
                    speakResponse(errorMsg)
                }
            }
        } else {
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
    }

    fun repeatLastResponse() {
        val currentResponse = _uiState.value.lastSpokenResponse
        speakResponse(currentResponse)
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
            cleanText.contains("repeat") || cleanText.contains("say again") || cleanText.contains("read again") -> {
                repeatLastResponse()
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
            cleanText.contains("read") || cleanText.contains("read this") || cleanText.contains("document") -> {
                setMode(AppMode.READ)
                if (!_uiState.value.isCameraActive) {
                    toggleCamera(true)
                }
            }
            cleanText.contains("around me") || cleanText.contains("what is in front") || cleanText.contains("describe") || cleanText.contains("vision") -> {
                setMode(AppMode.VISION)
                if (!_uiState.value.isCameraActive) {
                    toggleCamera(true)
                }
                speakResponse("Capturing scene for analysis.")
                cameraManager.takePicture(
                    onImageCaptured = { bitmap -> handleCapturedImage(bitmap) },
                    onError = { err -> speakResponse(err) }
                )
            }
            cleanText.contains("close camera") || cleanText.contains("hide camera") -> {
                toggleCamera(false)
            }
            cleanText.contains("help") -> {
                speakResponse("You can say: 'Read this', 'What is around me', 'Repeat', 'Turn on light', or 'Help'.")
            }
            cleanText.contains("stop") -> {
                ttsManager.stop()
                speakResponse("Stopped audio playback.")
            }
            else -> {
                speakResponse("I heard: '$recognizedText'. Say 'Read this', 'What is around me', or 'Help' for options.")
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
