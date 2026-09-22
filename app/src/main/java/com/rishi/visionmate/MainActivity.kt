package com.rishi.visionmate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.rishi.visionmate.ui.theme.VisionMateTheme
import com.rishi.visionmate.ui.voice.AppMode
import com.rishi.visionmate.ui.voice.VoiceViewModel

class MainActivity : ComponentActivity() {

    private val voiceViewModel: VoiceViewModel by viewModels()

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            voiceViewModel.startListening()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_LONG).show()
            voiceViewModel.speakResponse("Microphone permission is required to listen to your commands.")
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            voiceViewModel.toggleCamera(true)
        } else {
            Toast.makeText(this, "Camera permission is required for vision, read, and medication modes", Toast.LENGTH_LONG).show()
            voiceViewModel.speakResponse("Camera permission is required to use Vision, Read, and Medication modes.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    private fun checkAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceViewModel.startListening()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkAndToggleCamera() {
        if (voiceViewModel.uiState.value.isCameraActive) {
            voiceViewModel.toggleCamera(false)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                voiceViewModel.toggleCamera(true)
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
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
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { viewModel.triggerIncidentAlert() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .semantics { contentDescription = "Test safety incident simulation" }
                    ) {
                        Text(text = "🚨 Test Safety", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Mode Selector Bar (Vision / Read / Medication)
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
                            .height(44.dp)
                            .semantics { contentDescription = "Switch to Vision Mode for scene description" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "👁 Vision", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { viewModel.setMode(AppMode.READ) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.activeMode == AppMode.READ) Color(0xFF1565C0) else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics { contentDescription = "Switch to Read Mode for document reading" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "📖 Read", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { viewModel.setMode(AppMode.MEDICATION) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.activeMode == AppMode.MEDICATION) Color(0xFFE65100) else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics { contentDescription = "Switch to Medication Assistant mode" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "💊 Medicine", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Central Area: Live Camera Preview OR Spoken Output Display / Medication View
            if (uiState.isCameraActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    CameraPreviewView(
                        cameraManager = viewModel.cameraManager,
                        isTorchOn = uiState.isTorchOn,
                        onToggleTorch = { enable ->
                            viewModel.toggleTorch(enable)
                        },
                        onCaptureImage = { bitmap ->
                            viewModel.handleCapturedImage(bitmap)
                        },
                        onError = { error ->
                            viewModel.speakResponse(error)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = "Live camera view"
                            }
                    )
                }
            } else if (uiState.detectedMedication != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    MedicationView(
                        item = uiState.detectedMedication!!,
                        onConfirmReminder = { viewModel.confirmMedicationReminder() },
                        onCancel = { viewModel.clearMedication() }
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .semantics {
                            contentDescription = "Spoken feedback card: ${uiState.lastSpokenResponse}"
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isListening) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎙 Listening... Speak command",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        val modeTitle = when (uiState.activeMode) {
                            AppMode.READ -> "Read Mode Output:"
                            AppMode.MEDICATION -> "Medication Output:"
                            AppMode.VISION -> "Vision Mode Output:"
                        }

                        Text(
                            text = modeTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.lastSpokenResponse,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.lastRecognizedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "You Said: \"${uiState.lastRecognizedText}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        uiState.errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Controls Section (High Contrast Touch Targets)
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onRequestCameraPermission,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics {
                                contentDescription = if (uiState.isCameraActive) "Tap to close camera" else "Tap to open camera"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isCameraActive) Color(0xFFC62828) else MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.isCameraActive) "📷 Close Camera" else "📷 Open Camera",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = { viewModel.repeatLastResponse() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Repeat last text readout" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "🔁 Repeat", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = { viewModel.stopSpeech() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Silence audio output" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "🔇 Stop", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
