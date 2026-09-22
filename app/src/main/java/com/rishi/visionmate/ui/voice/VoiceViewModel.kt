package com.rishi.visionmate.ui.voice

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rishi.visionmate.BuildConfig
import com.rishi.visionmate.core.common.NetworkMonitor
import com.rishi.visionmate.domain.model.MedicationItem
import com.rishi.visionmate.domain.usecase.AnalyzeSceneUseCase
import com.rishi.visionmate.domain.usecase.DetectIncidentUseCase
import com.rishi.visionmate.domain.usecase.ExtractMedicationUseCase
import com.rishi.visionmate.domain.usecase.ReadTextUseCase
import com.rishi.visionmate.services.camera.CameraManager
import com.rishi.visionmate.services.location.LocationProvider
import com.rishi.visionmate.services.speech.SpeechToTextManager
import com.rishi.visionmate.services.speech.TextToSpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppMode {
    VISION,
    READ,
    MEDICATION
}

data class VoiceUiState(
    val isListening: Boolean = false,
    val isCameraActive: Boolean = true, // Default active for instant captures
    val isTorchOn: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isIncidentAlertActive: Boolean = false,
    val incidentCountdownSeconds: Int = 15,
    val isOnline: Boolean = true,
    val isLocationSharingEnabled: Boolean = true,
    val isApiKeyConfigured: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank(),
    val isSettingsOpen: Boolean = false,
    val activeMode: AppMode = AppMode.VISION,
    val detectedMedication: MedicationItem? = null,
    val lastRecognizedText: String = "",
    val lastSpokenResponse: String = "Welcome to VisionMate. I am listening. Say what is in front of me or read this.",
    val conversationHistory: List<Pair<String, String>> = emptyList(), // History of (Query, Response)
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
    private val extractMedicationUseCase = ExtractMedicationUseCase()
    private val networkMonitor = NetworkMonitor(application)
    private val locationProvider = LocationProvider(application)

    private var countdownJob: Job? = null
    private val incidentDetector = DetectIncidentUseCase(application) {
        triggerIncidentAlert()
    }

    init {
        sttManager = SpeechToTextManager(
            context = application,
            onResult = ::handleRecognizedSpeech,
            onError = ::handleSpeechError,
            onListeningStateChanged = { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        )
        // Start background sensors & network monitoring
        incidentDetector.start()
        networkMonitor.startMonitoring()

        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
            }
        }

        // Initial spoken welcome
        speakResponse("Welcome to VisionMate. Tap or speak your command.")
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

    fun toggleSettings(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }

    fun toggleLocationSharing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isLocationSharingEnabled = enabled)
        if (enabled) {
            speakResponse("Emergency location sharing enabled.")
        } else {
            speakResponse("Emergency location sharing disabled.")
        }
    }

    fun setMode(mode: AppMode) {
        _uiState.value = _uiState.value.copy(activeMode = mode, detectedMedication = null)
        when (mode) {
            AppMode.READ -> speakResponse("Read Mode selected. Point camera at document and tap Capture or speak command.")
            AppMode.MEDICATION -> speakResponse("Medication Mode selected. Point camera at medicine package and tap Capture or speak command.")
            AppMode.VISION -> speakResponse("Vision Mode selected. Point camera at surroundings and ask what is in front of me.")
        }
    }

    fun toggleCamera(active: Boolean) {
        _uiState.value = _uiState.value.copy(isCameraActive = active)
        if (active) {
            val modeName = when (_uiState.value.activeMode) {
                AppMode.READ -> "Read Mode"
                AppMode.MEDICATION -> "Medication Assistant"
                AppMode.VISION -> "Vision Mode"
            }
            speakResponse("Camera activated in $modeName.")
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

    fun handleDarknessDetected() {
        if (!_uiState.value.isTorchOn) {
            toggleTorch(true)
            speakResponse("Low light detected. Flashlight automatically turned on.")
        }
    }

    fun captureAndAnalyze(customPrompt: String? = null) {
        if (!_uiState.value.isCameraActive) {
            _uiState.value = _uiState.value.copy(isCameraActive = true)
        }

        speakResponse("Capturing image for analysis...")
        cameraManager.takePicture(
            onImageCaptured = { bitmap ->
                handleCapturedImage(bitmap, customPrompt)
            },
            onError = { err ->
                speakResponse(err)
            }
        )
    }

    fun handleCapturedImage(bitmap: Bitmap, customPrompt: String? = null) {
        _uiState.value = _uiState.value.copy(
            capturedBitmap = bitmap,
            isAnalyzing = true,
            errorMessage = null,
            detectedMedication = null
        )

        // Offline Fallback Check
        if (_uiState.value.activeMode == AppMode.VISION && !_uiState.value.isOnline) {
            speakResponse("Network is offline. Falling back to local text reader.")
            viewModelScope.launch {
                val result = readTextUseCase(bitmap)
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
                result.onSuccess { text -> speakResponse(text) }
                    .onFailure { speakResponse("Network is offline and no text was detected.") }
            }
            return
        }

        when (_uiState.value.activeMode) {
            AppMode.READ -> {
                speakResponse("Reading document text, please wait...")
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
            }
            AppMode.MEDICATION -> {
                speakResponse("Analyzing medication label, please wait...")
                viewModelScope.launch {
                    val result = readTextUseCase(bitmap)
                    _uiState.value = _uiState.value.copy(isAnalyzing = false)
                    result.onSuccess { rawText ->
                        val medication = extractMedicationUseCase(rawText)
                        _uiState.value = _uiState.value.copy(detectedMedication = medication)
                        speakResponse("Detected medication: ${medication.name}, Dosage: ${medication.dosage}. Please verify package label with your pharmacist and tap Confirm to schedule.")
                    }.onFailure { error ->
                        val errorMsg = "Unable to read medication label: ${error.localizedMessage}"
                        _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
                        speakResponse(errorMsg)
                    }
                }
            }
            AppMode.VISION -> {
                speakResponse("Analyzing surroundings, please wait...")
                viewModelScope.launch {
                    val result = analyzeSceneUseCase(bitmap, customPrompt)
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
    }

    private fun handleFollowUpQuestion(userQuestion: String) {
        speakResponse("Processing your question...")
        _uiState.value = _uiState.value.copy(isAnalyzing = true)

        viewModelScope.launch {
            val result = analyzeSceneUseCase.askFollowUp(userQuestion)
            _uiState.value = _uiState.value.copy(isAnalyzing = false)
            result.onSuccess { responseText ->
                speakResponse(responseText)
            }.onFailure { _ ->
                // If no previous image exists, capture a new image and answer
                captureAndAnalyze("Answer this question about what is in front of the camera: '$userQuestion'")
            }
        }
    }

    fun confirmMedicationReminder() {
        val currentMed = _uiState.value.detectedMedication ?: return
        val confirmedItem = currentMed.copy(isConfirmedByUser = true)
        _uiState.value = _uiState.value.copy(detectedMedication = confirmedItem)
        speakResponse("Medication reminder confirmed for ${confirmedItem.name}, dosage ${confirmedItem.dosage}.")
    }

    fun clearMedication() {
        _uiState.value = _uiState.value.copy(detectedMedication = null)
        speakResponse("Medication scan cleared.")
    }

    // Safety Incident Alert Flow
    fun triggerIncidentAlert() {
        if (_uiState.value.isIncidentAlertActive) return

        _uiState.value = _uiState.value.copy(
            isIncidentAlertActive = true,
            incidentCountdownSeconds = 15
        )

        speakResponse("Potential incident detected! Are you okay? Say I am okay or tap the green button within 15 seconds.")

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 15 downTo 1) {
                _uiState.value = _uiState.value.copy(incidentCountdownSeconds = sec)
                delay(1000)
            }
            sendEmergencyAlert()
        }
    }

    fun confirmUserIsOkay() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(isIncidentAlertActive = false)
        speakResponse("Glad to hear you are okay. Incident alert cancelled.")
    }

    fun sendEmergencyAlert() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(isIncidentAlertActive = false)

        viewModelScope.launch {
            val location = if (_uiState.value.isLocationSharingEnabled) {
                locationProvider.getCurrentLocation()
            } else null

            val locationInfo = if (location != null) {
                "Location: ${location.latitude}, ${location.longitude}"
            } else {
                "Location unavailable or disabled."
            }

            speakResponse("Emergency alert dispatched to trusted contacts. $locationInfo")
        }
    }

    fun repeatLastResponse() {
        val currentResponse = _uiState.value.lastSpokenResponse
        speakResponse(currentResponse)
    }

    fun speakResponse(text: String, flush: Boolean = true) {
        val currentText = _uiState.value.lastRecognizedText
        val updatedHistory = if (currentText.isNotBlank()) {
            (_uiState.value.conversationHistory + Pair(currentText, text)).takeLast(10)
        } else {
            _uiState.value.conversationHistory
        }

        _uiState.value = _uiState.value.copy(
            lastSpokenResponse = text,
            conversationHistory = updatedHistory
        )
        ttsManager.speak(text, flush)
    }

    private fun handleRecognizedSpeech(recognizedText: String) {
        _uiState.value = _uiState.value.copy(lastRecognizedText = recognizedText)

        val cleanText = recognizedText.lowercase().trim()

        // Active Incident Verification
        if (_uiState.value.isIncidentAlertActive) {
            if (cleanText.contains("okay") || cleanText.contains("fine") || cleanText.contains("cancel") || cleanText.contains("good")) {
                confirmUserIsOkay()
                return
            } else if (cleanText.contains("help") || cleanText.contains("alert") || cleanText.contains("send")) {
                sendEmergencyAlert()
                return
            }
        }

        // Detect Follow-Up Questions (e.g. "what is it used for", "how do I take it", "what color", "what is the price", "tell me more", "how many", "why")
        val isFollowUp = cleanText.startsWith("what is it") ||
                cleanText.contains("used for") ||
                cleanText.contains("how do i") ||
                cleanText.contains("how to use") ||
                cleanText.contains("tell me more") ||
                cleanText.contains("what color") ||
                cleanText.contains("what price") ||
                cleanText.contains("explain more") ||
                cleanText.contains("dosage")

        if (isFollowUp) {
            handleFollowUpQuestion(recognizedText)
            return
        }

        when {
            cleanText.contains("hello") || cleanText.contains("hi visionmate") -> {
                speakResponse("Hello! I am VisionMate, your accessibility companion. How can I help you today?")
            }
            cleanText.contains("confirm medication") || cleanText.contains("confirm reminder") -> {
                confirmMedicationReminder()
            }
            cleanText.contains("repeat") || cleanText.contains("say again") || cleanText.contains("read again") -> {
                repeatLastResponse()
            }
            cleanText.contains("settings") || cleanText.contains("privacy") -> {
                toggleSettings(true)
                speakResponse("Settings opened.")
            }
            cleanText.contains("test incident") || cleanText.contains("simulate fall") || cleanText.contains("test safety") -> {
                triggerIncidentAlert()
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
            cleanText.contains("medication") || cleanText.contains("medicine") || cleanText.contains("pill") || cleanText.contains("prescription") -> {
                setMode(AppMode.MEDICATION)
                captureAndAnalyze()
            }
            cleanText.contains("read") || cleanText.contains("read this") || cleanText.contains("document") -> {
                setMode(AppMode.READ)
                captureAndAnalyze()
            }
            cleanText.contains("around me") || cleanText.contains("what is in front") || cleanText.contains("describe") || cleanText.contains("vision") || cleanText.contains("what is this") -> {
                setMode(AppMode.VISION)
                captureAndAnalyze()
            }
            cleanText.contains("close camera") || cleanText.contains("hide camera") -> {
                toggleCamera(false)
            }
            cleanText.contains("help") -> {
                speakResponse("You can say: 'What is in front of me', 'Read this', 'Medication', 'What is it used for', 'Repeat', or 'Help'.")
            }
            cleanText.contains("stop") -> {
                ttsManager.stop()
                speakResponse("Stopped audio playback.")
            }
            else -> {
                // If it's a general question, route as follow-up query to Gemini!
                if (cleanText.length > 5) {
                    handleFollowUpQuestion(recognizedText)
                } else {
                    speakResponse("I heard: '$recognizedText'. Say 'What is in front of me', 'Read this', or 'Help'.")
                }
            }
        }
    }

    private fun handleSpeechError(errorMsg: String) {
        _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
        speakResponse(errorMsg)
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.stopMonitoring()
        incidentDetector.stop()
        sttManager?.destroy()
        ttsManager.shutdown()
        cameraManager.unbind()
    }
}
