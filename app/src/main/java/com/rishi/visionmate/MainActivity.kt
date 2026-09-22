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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    HomeScreen(
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
fun HomeScreen(
    viewModel: VoiceViewModel,
    onRequestAudioPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VisionMate",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row {
                        OutlinedButton(
                            onClick = { viewModel.toggleSettings(true) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .semantics { contentDescription = "Open Settings and Privacy" }
                        ) {
                            Text(text = "⚙️ Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = { viewModel.triggerIncidentAlert() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .semantics { contentDescription = "Test safety incident simulation" }
                        ) {
                            Text(text = "🚨 Safety", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Offline Notice Banner
                if (!uiState.isOnline) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE65100), RoundedCornerShape(6.dp))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📡 Offline Mode: Using Local Text Reader",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Mode Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { viewModel.setMode(AppMode.VISION) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.activeMode == AppMode.VISION) MaterialTheme.colorScheme.primary else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .semantics { contentDescription = "Switch to Vision Mode for scene description" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "👁 Vision", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { viewModel.setMode(AppMode.READ) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.activeMode == AppMode.READ) Color(0xFF1565C0) else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .semantics { contentDescription = "Switch to Read Mode for document reading" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "📖 Read", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { viewModel.setMode(AppMode.MEDICATION) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.activeMode == AppMode.MEDICATION) Color(0xFFE65100) else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .semantics { contentDescription = "Switch to Medication Assistant mode" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "💊 Medicine", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Central Area: Live Camera Preview OR Active Output & Conversation History
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 6.dp)
            ) {
                if (uiState.isCameraActive) {
                    CameraPreviewView(
                        cameraManager = viewModel.cameraManager,
                        isTorchOn = uiState.isTorchOn,
                        onToggleTorch = { enable -> viewModel.toggleTorch(enable) },
                        onCaptureImage = { bitmap -> viewModel.handleCapturedImage(bitmap) },
                        onError = { error -> viewModel.speakResponse(error) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Spoken Feedback Card Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (uiState.isCameraActive) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Status / Listening Bar
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.isAnalyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(16.dp).width(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing image...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else if (uiState.isListening) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2E7D32), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("🎙 Listening... Speak command", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Main Output Text or Scanned Medication View
                    if (uiState.detectedMedication != null) {
                        MedicationView(
                            item = uiState.detectedMedication!!,
                            onConfirmReminder = { viewModel.confirmMedicationReminder() },
                            onCancel = { viewModel.clearMedication() }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            item {
                                Text(
                                    text = uiState.lastSpokenResponse,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = Color.White
                                )

                                if (uiState.lastRecognizedText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "You Said: \"${uiState.lastRecognizedText}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFEB3B),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Floating Action Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.captureAndAnalyze("What is in front of me?") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("👁 What is in front?", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRequestCameraPermission() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isCameraActive) Color(0xFFC62828) else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(if (uiState.isCameraActive) "📷 Close Cam" else "📷 Open Cam", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // High Contrast Voice Touch Targets
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                        .height(72.dp)
                        .semantics {
                            contentDescription = if (uiState.isListening) "Tap to stop listening" else "Tap to speak voice command"
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isListening) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (uiState.isListening) "⏹ Stop Listening" else "🎤 Tap to Speak Command",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.repeatLastResponse() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Repeat last text readout" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "🔁 Repeat", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = { viewModel.stopSpeech() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Silence audio output" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "🔇 Stop", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
