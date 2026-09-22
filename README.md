# 👁 VisionMate

> **An AI-Powered, Voice-First Accessibility Companion for Visually Impaired Users.**

VisionMate is an Android accessibility application designed to empower blind and visually impaired individuals in their daily lives. By integrating real-time multimodal AI (**Gemini 2.5 Flash**), 100% offline text recognition (**Google ML Kit OCR**), sensor-driven **fall & incident detection**, and an **audio-safeguarded voice interface**, VisionMate serves as an intuitive digital companion for spatial navigation, document reading, medication management, and personal safety.

---

## ✨ Key Features

### 1. 👁 AI Vision Mode (Gemini 2.5 Flash)
- **Spatial Scene Understanding**: Analyzes captured camera frames and provides detailed descriptions of surroundings using spatial orientation terms (*"to your left"*, *"directly in front of you"*).
- **Uncertainty-Aware Safety Prompting**: Uses cautious, non-prescriptive language (*"appears to be"*, *"looks like"*) to avoid making false claims about physical path safety.

### 2. 📖 Read Mode (100% Offline OCR)
- **Instant Document & Sign Reading**: Powered by Google ML Kit Text Recognition to scan and read aloud printed documents, street signs, food packaging, and menus completely offline.
- **Repeat & Silence Controls**: Allows users to repeat the last readout or silence audio output instantly with physical touch targets or voice commands.

### 3. 💊 Medication Assistant Prototype
- **Label & Dosage Parsing**: Automatically extracts medicine names and dosages (e.g., *Paracetamol 500 mg*) from scanned prescription bottles and package labels.
- **Safety Guardrails & Confirmation**: Displays mandatory medical disclaimers (*"VisionMate does not provide medical advice. Always verify with a pharmacist"*) and requires explicit user confirmation before scheduling reminders.

### 4. 🚨 Safety Incident System
- **Accelerometer Motion Detection**: Continuously monitors motion sensors for sudden G-force anomalies (>2.5G) representing potential falls or impacts.
- **15-Second Verification Overlay**: Triggers an urgent countdown screen with high-contrast buttons (**🟢 I'M OKAY** / **🚨 SEND ALERT NOW**) and voice cancellation.
- **Emergency Location Payload**: Retrieves GPS coordinates (when opt-in location sharing is enabled) for emergency alerts.
- **Interactive Simulation**: Includes a dedicated header button (**🚨 Safety**) for safe, non-hazardous live demonstrations.

### 5. 🔊 Voice-First Accessibility & Volume Safeguard
- **Hands-Free Control**: Complete functionality accessible via voice commands or high-contrast, large touch targets.
- **Automatic Volume Safeguard**: `TextToSpeechManager` automatically inspects and unmutes Android media audio volume if silenced or set too low, ensuring critical speech feedback is always audible.

### 6. 🔦 Low-Light Flashlight / Torch Safeguard
- **Dark Environment Support**: Integrated into CameraX to toggle the device flashlight on/off via voice command (*"Turn on light"*) or high-contrast button.

### 7. 📡 Network Resilience & Offline Fallback
- **Live Connectivity Monitoring**: Real-time network detection via `ConnectivityManager`. When internet drops, Vision Mode gracefully falls back to local OCR reading and notifies the user.

---

## 🛠 Tech Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material3 high-contrast accessibility layout)
- **Architecture**: Clean Architecture (MVVM + StateFlow + Coroutines)
- **Camera Engine**: AndroidX CameraX (Preview, ImageCapture, Torch Control)
- **AI / Multimodal**: Google Generative AI Android SDK (`gemini-2.5-flash`)
- **Offline ML**: Google ML Kit Text Recognition
- **Location Services**: Google Play Services Location (`FusedLocationProviderClient`)
- **Audio Engine**: Native Android `TextToSpeech` & `SpeechRecognizer`
- **Min SDK**: 24 (Android 7.0) | **Target SDK**: 37 (Android 15)

---

## 📂 Project Structure

```
com.rishi.visionmate/
├── core/
│   └── common/
│       └── NetworkMonitor.kt           # Real-time internet connectivity listener
├── domain/
│   ├── model/
│   │   └── MedicationItem.kt          # Structured prescription data model
│   └── usecase/
│       ├── AnalyzeSceneUseCase.kt     # Gemini AI vision analysis
│       ├── ReadTextUseCase.kt         # ML Kit offline OCR extraction
│       ├── ExtractMedicationUseCase.kt# Regex dosage & prescription parser
│       └── DetectIncidentUseCase.kt   # Safety sensor anomaly listener
├── services/
│   ├── camera/
│   │   └── CameraManager.kt           # CameraX lifecycle, capture & torch
│   ├── location/
│   │   └── LocationProvider.kt         # Fused location coordinate provider
│   ├── ocr/
│   │   └── MlKitOcrService.kt         # Google ML Kit text recognizer
│   ├── safety/
│   │   └── IncidentDetector.kt        # Accelerometer threshold monitor
│   ├── speech/
│   │   ├── TextToSpeechManager.kt     # TTS engine with volume unmute protection
│   │   └── SpeechToTextManager.kt     # Hands-free speech recognition
│   └── vision/
│       └── GeminiVisionService.kt     # Gemini 2.5 Flash SDK client
└── ui/
    ├── camera/
    │   └── CameraPreviewView.kt       # Compose CameraPreview & controls
    ├── medication/
    │   └── MedicationView.kt          # Prescription confirmation card
    ├── safety/
    │   └── IncidentOverlay.kt         # Full-screen emergency countdown
    ├── settings/
    │   └── SettingsDialog.kt          # Privacy & location opt-in dialog
    └── voice/
        └── VoiceViewModel.kt          # Central ViewModel orchestrator
```

---

## 🗣 Voice Commands Reference

| Command Phrase | Action Triggered |
| :--- | :--- |
| **"Hello"** / **"Hi VisionMate"** | Spoken introductory greeting and assistance guide. |
| **"What is around me"** / **"Describe"** | Opens camera, captures photo, and provides AI scene analysis. |
| **"Read"** / **"Read this"** | Switches to Read Mode and scans text via offline ML Kit OCR. |
| **"Medication"** / **"Pill"** | Switches to Medication Assistant mode to scan prescription label. |
| **"Confirm medication"** | Confirms scanned medicine dosage and schedules a reminder. |
| **"Turn on light"** / **"Torch on"** | Activates camera flashlight for dark environments. |
| **"Turn off light"** / **"Torch off"** | Deactivates camera flashlight. |
| **"I'm okay"** / **"Cancel"** | Cancels active 15-second safety incident emergency alert. |
| **"Repeat"** / **"Say again"** | Re-reads the last spoken AI description or text output. |
| **"Stop"** | Immediately halts ongoing speech audio playback. |
| **"Test safety"** | Simulates a safety incident alert for demonstration purposes. |
| **"Settings"** | Opens the Settings & Privacy dialog. |
| **"Help"** | Lists available voice commands out loud. |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug / 2024.2+ recommended.
- **Android Device**: Physical device recommended (Camera, Microphone, Accelerometer required).
- **Gemini API Key**: Obtain a free API key from [Google AI Studio](https://aistudio.google.com/).

### Setup Instructions

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/induririsheendra-cmd/VisionMate.git
   cd VisionMate
   ```

2. **Configure API Key (`local.properties`)**:
   Open or create `local.properties` in the project root directory and add your Gemini API key:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```
   > 🔒 *Security Note: `local.properties` is listed in `.gitignore` and is never committed to Git.*

3. **Build & Deploy**:
   - Open the project in **Android Studio**.
   - Sync Gradle project.
   - Connect your Android device via USB / Wireless ADB.
   - Click **Run (`Shift + F10`)**.

---

## 🛡 Security & Privacy

- **No Hardcoded Keys**: API keys are dynamically loaded via `BuildConfig` from `local.properties`.
- **Privacy First**: Camera frames are processed ephemerally and never saved to persistent user storage.
- **Opt-In Location**: GPS coordinates are retrieved only during unconfirmed safety incidents when emergency location sharing is enabled.

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for more information.

---

## 🤝 Acknowledgments

- **Google Generative AI**: Gemini 2.5 Flash SDK.
- **Google ML Kit**: On-Device Text Recognition.
- **Android Jetpack**: Compose, CameraX, ViewModel, StateFlow.
