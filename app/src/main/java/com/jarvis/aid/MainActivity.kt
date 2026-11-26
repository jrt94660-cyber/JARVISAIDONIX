package com.jarvis.aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val navy = Color(0xFF07111F)
    private val cyan = Color(0xFF35D8F2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                JarvisScreen(
                    onSpeak = { launchSpeechRecognition() },
                    onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                    onCommand = { command, respond -> handleCommand(command, respond) },
                    background = navy,
                    accent = cyan
                )
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.getDefault()
    }

    private fun say(message: String) {
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    private fun launchSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition is not available on this device.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }
        runCatching { startActivityForResult(intent, REQUEST_SPEECH) }
            .onFailure { say("I could not start speech recognition.") }
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            val phrase = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!phrase.isNullOrBlank()) {
                handleCommand(phrase) { response ->
                    say(response)
                    setContent {
                        MaterialTheme {
                            JarvisScreen(
                                onSpeak = { launchSpeechRecognition() },
                                onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                                onCommand = { command, respond -> handleCommand(command, respond) },
                                background = navy,
                                accent = cyan,
                                initialMessage = "You: $phrase\nJ.A.R.V.I.S: $response"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleCommand(raw: String, respond: (String) -> Unit) {
        val command = raw.lowercase(Locale.ROOT).trim()
        val result = when {
            command.contains("open settings") || command == "settings" -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); "Opening device settings."
            }
            command.contains("open wi-fi") || command.contains("open wifi") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); "Opening Wi-Fi settings."
            }
            command.contains("open bluetooth") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "Opening Bluetooth settings."
            }
            command.contains("turn on flashlight") || command.contains("flashlight on") -> setTorch(true)
            command.contains("turn off flashlight") || command.contains("flashlight off") -> setTorch(false)
            command.contains("what time") || command == "time" ->
                "The time is " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
            command.contains("hello") || command.contains("hey jarvis") ->
                "Hello. J.A.R.V.I.S is online. How can I assist?"
            command.contains("who are you") ->
                "I am J.A.R.V.I.S, your personal device assistant."
            else ->
                "I heard: $raw. That command is not available yet. Try opening settings, Wi-Fi, Bluetooth, or controlling the flashlight."
        }
        respond(result)
    }

    private fun setTorch(enabled: Boolean): String {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return "Camera permission is required before I can control the flashlight. Please grant it and try again."
        }
        return runCatching {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This device does not have a controllable flashlight."
            manager.setTorchMode(cameraId, enabled)
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        }.getOrElse { "I could not change the flashlight state. Check whether another app is using the camera." }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_SPEECH = 4101
        private const val REQUEST_CAMERA = 4102
    }
}

@Composable
private fun JarvisScreen(
    onSpeak: () -> Unit,
    onSettings: () -> Unit,
    onCommand: (String, (String) -> Unit) -> Unit,
    background: Color,
    accent: Color,
    initialMessage: String = "Systems ready. Tap the microphone and speak."
) {
    var message by remember { mutableStateOf(initialMessage) }
    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("J.A.R.V.I.S", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text("PERSONAL INTELLIGENCE SYSTEM", color = accent, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(36.dp))
            Surface(modifier = Modifier.size(190.dp), shape = CircleShape, color = Color(0xFF10283A)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("J", color = accent, fontSize = 76.sp, fontWeight = FontWeight.Light)
                    Text("ONLINE", color = accent, fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF102033)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(message, modifier = Modifier.padding(18.dp), color = Color(0xFFE6F4FA), fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSpeak,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Speak", tint = background)
                Text("  TALK TO J.A.R.V.I.S", color = background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                Text("  DEVICE SETTINGS", color = Color.White)
            }
            Spacer(Modifier.height(22.dp))
            Text("VOICE COMMANDS", color = accent, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("“Open Wi-Fi”  •  “Turn on flashlight”  •  “What time is it?”", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
