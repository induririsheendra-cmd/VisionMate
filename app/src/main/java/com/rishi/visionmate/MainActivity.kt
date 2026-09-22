package com.rishi.visionmate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rishi.visionmate.ui.camera.CameraPreviewView
import com.rishi.visionmate.ui.medication.MedicationView
import com.rishi.visionmate.ui.safety.IncidentOverlay
import com.rishi.visionmate.ui.settings.SettingsDialog
import com.rishi.visionmate.ui.theme.VisionMateTheme
import com.rishi.visionmate.ui.voice.AppMode
import com.rishi.visionmate.ui.voice.VoiceViewModel

class MainActivity : ComponentActivity() {

    private val voiceViewModel: VoiceViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        if (cameraGranted) {
            voiceViewModel.toggleCamera(true)
        }
        if (recordGranted) {
            voiceViewModel.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-request initial permissions on startup
        checkAndRequestPermissions()

        setContent {
            VisionMateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OneUiHomeScreen(
                        viewModel = voiceViewModel,
                        onRequestAudioPermission = { checkAndStartListening() },
                        onRequestCameraPermission = { checkAndToggleCamera() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceViewModel.startListening()
        } else {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun checkAndToggleCamera() {
        if (voiceViewModel.uiState.value.isCameraActive) {
            voiceViewModel.toggleCamera(false)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                voiceViewModel.toggleCamera(true)
            } else {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }
}

@Composable
fun OneUiHomeScreen(
    viewModel: VoiceViewModel,
    onRequestAudioPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Color Palette - Samsung One UI Dark Theme
    val oneUiBg = Color(0xFF121215)
    val oneUiCardBg = Color(0xFF1E1E24)
    val oneUiAccentBlue = Color(0xFF007AFF)
    val oneUiActiveGreen = Color(0xFF2E7D32)
    val oneUiWarningOrange = Color(0xFFE65100)
    val oneUiErrorRed = Color(0xFFD32F2F)

    if (uiState.isIncidentAlertActive) {
        IncidentOverlay(
            countdownSeconds = uiState.incidentCountdownSeconds,
            onConfirmOkay = { viewModel.confirmUserIsOkay() },
            onRequestEmergencyHelp = { viewModel.sendEmergencyAlert() },
            modifier = modifier
        )
        return
    }

    if (uiState.isSettingsOpen) {
        SettingsDialog(
            isLocationSharingEnabled = uiState.isLocationSharingEnabled,
            isApiKeyConfigured = uiState.isApiKeyConfigured,
            onToggleLocationSharing = { enabled ->
                viewModel.toggleLocationSharing(enabled)
            },
            onDismiss = { viewModel.toggleSettings(false) }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = oneUiBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // One UI Header Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VisionMate",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Accessibility Assistant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.LightGray
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Settings Pill Button
                        Button(
                            onClick = { viewModel.toggleSettings(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = oneUiCardBg),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .semantics { contentDescription = "Open Settings and Privacy" }
                        ) {
                            Text(text = "⚙️ Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Safety Test Button
                        Button(
                            onClick = { viewModel.triggerIncidentAlert() },
                            colors = ButtonDefaults.buttonColors(containerColor = oneUiErrorRed),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .semantics { contentDescription = "Test safety incident simulation" }
                        ) {
                            Text(text = "🚨 Safety", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Offline Notice Banner
                if (!uiState.isOnline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(oneUiWarningOrange, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📡 Offline Mode: On-Device OCR Active",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // One UI Capsule Mode Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(oneUiCardBg, RoundedCornerShape(20.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.setMode(AppMode.VISION) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.activeMode == AppMode.VISION) oneUiAccentBlue else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "👁 Vision",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.activeMode == AppMode.VISION) Color.White else Color.Gray
                            )
                        }

                        Button(
                            onClick = { viewModel.setMode(AppMode.READ) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.activeMode == AppMode.READ) Color(0xFF1565C0) else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "📖 Read",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.activeMode == AppMode.READ) Color.White else Color.Gray
                            )
                        }

                        Button(
                            onClick = { viewModel.setMode(AppMode.MEDICATION) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.activeMode == AppMode.MEDICATION) oneUiWarningOrange else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "💊 Medicine",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.activeMode == AppMode.MEDICATION) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }

            // Viewing Deck - Samsung One UI Curved Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(oneUiCardBg)
            ) {
                if (uiState.isCameraActive) {
                    CameraPreviewView(
                        cameraManager = viewModel.cameraManager,
                        isTorchOn = uiState.isTorchOn,
                        onToggleTorch = { enable -> viewModel.toggleTorch(enable) },
                        onCaptureImage = { bitmap -> viewModel.handleCapturedImage(bitmap) },
                        onDarknessDetected = { viewModel.handleDarknessDetected() },
                        onError = { error -> viewModel.speakResponse(error) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Smooth Feedback & Output Deck Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (uiState.isCameraActive) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Status Badge
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.isAnalyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(oneUiAccentBlue, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Analyzing visual input...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else if (uiState.isListening) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(oneUiActiveGreen, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🎙 Microphone Listening...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Main Output Area / Scanned Medication
                    if (uiState.detectedMedication != null) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            MedicationView(
                                item = uiState.detectedMedication!!,
                                onConfirmReminder = { viewModel.confirmMedicationReminder() },
                                onCancel = { viewModel.clearMedication() }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.lastSpokenResponse,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                                lineHeight = 28.sp
                            )

                            if (uiState.lastRecognizedText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(oneUiAccentBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "You said: \"${uiState.lastRecognizedText}\"",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF90CAF9),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Quick Action Chips (One UI Reachability)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.captureAndAnalyze("What is in front of me?") },
                            colors = ButtonDefaults.buttonColors(containerColor = oneUiAccentBlue),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("👁 Analyze Scene", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onRequestCameraPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isCameraActive) oneUiErrorRed else Color(0xFF33333C)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                if (uiState.isCameraActive) "📷 Close Cam" else "📷 Open Cam",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Samsung One UI Reachability Control Deck (Bottom Zone)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Giant One UI Primary Voice Trigger Button
                Button(
                    onClick = {
                        if (uiState.isListening) {
                            viewModel.stopListening()
                        } else {
                            onRequestAudioPermission()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .semantics {
                            contentDescription = if (uiState.isListening) "Tap to stop listening" else "Tap to speak voice command"
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isListening) oneUiErrorRed else oneUiAccentBlue
                    ),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = if (uiState.isListening) "⏹ Stop Listening" else "🎤 Tap to Speak Command",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Control Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.repeatLastResponse() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Repeat last spoken feedback" },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "🔁 Repeat", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.stopSpeech() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Silence audio output" },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "🔇 Stop Audio", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
