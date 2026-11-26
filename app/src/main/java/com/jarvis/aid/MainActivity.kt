package com.jarvis.aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listeningEnabled = false
    private var commandMode = false
    private var restarting = false
    private var statusText by mutableStateOf("Opening assistant…")
    private var transcript by mutableStateOf("Keep J.A.R.V.I.S open and the screen awake. Say “Hey Jarvis” to activate.")
    private val navy = Color(0xFF07111F)
    private val cyan = Color(0xFF35D8F2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                JarvisScreen(
                    status = statusText,
                    message = transcript,
                    onToggle = {
                        if (listeningEnabled) stopWakeListening() else enableWakeListening()
                    },
                    onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                    background = navy,
                    accent = cyan
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Wake-word listening is foreground-only and is not kept alive after the app is backgrounded.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            enableWakeListening()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    override fun onPause() {
        stopRecognizer()
        super.onPause()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.getDefault()
    }

    private fun say(message: String) {
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    private fun enableWakeListening() {
        if (isFinishing || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText = "Microphone permission required"
            return
        }
        listeningEnabled = true
        commandMode = false
        statusText = "LISTENING FOR “HEY JARVIS”"
        startRecognition()
    }

    private fun stopWakeListening() {
        listeningEnabled = false
        commandMode = false
        restarting = false
        handler.removeCallbacksAndMessages(null)
        stopRecognizer()
        statusText = "OFF — SAY “HEY JARVIS” AFTER TAPPING ACTIVATE"
        transcript = "Wake-word listening and the active local voice session are stopped."
        say("J.A.R.V.I.S offline.")
    }

    private fun stopRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun startRecognition() {
        if (!listeningEnabled || isFinishing || isDestroyed || restarting) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText = "Speech recognition unavailable on this device"
            listeningEnabled = false
            return
        }
        stopRecognizer()
        val speech = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText = if (commandMode) "READY — SPEAK YOUR COMMAND" else "LISTENING FOR “HEY JARVIS”"
            }
            override fun onBeginningOfSpeech() { statusText = if (commandMode) "HEARING COMMAND…" else "HEARING…" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText = "PROCESSING…" }
            override fun onError(error: Int) { scheduleRecognitionRestart() }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (phrase.isNotBlank()) {
                    processRecognizedPhrase(phrase)
                } else {
                    scheduleRecognitionRestart()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val normalized = normalize(phrase)
                if (commandMode && containsOffPhrase(normalized)) {
                    stopWakeListening()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        runCatching { speech.startListening(intent) }
            .onFailure { scheduleRecognitionRestart() }
    }

    private fun scheduleRecognitionRestart() {
        if (!listeningEnabled || restarting) return
        restarting = true
        handler.postDelayed({
            restarting = false
            if (listeningEnabled && !isFinishing && !isDestroyed) startRecognition()
        }, 450L)
    }

    private fun processRecognizedPhrase(raw: String) {
        val normalized = normalize(raw)
        if (containsOffPhrase(normalized)) {
            stopWakeListening()
            return
        }
        if (!commandMode) {
            if (containsWakePhrase(normalized)) {
                commandMode = true
                val command = normalized.substringAfter("hey jarvis", "").trim()
                    .ifBlank { normalized.substringAfter("jarvis", "").trim() }
                if (command.isNotBlank()) executeVoiceCommand(command)
                else {
                    transcript = "Wake phrase detected. Listening for your command."
                    say("I'm listening.")
                    scheduleRecognitionRestart()
                }
            } else {
                scheduleRecognitionRestart()
            }
            return
        }
        executeVoiceCommand(raw)
    }

    private fun executeVoiceCommand(command: String) {
        if (containsOffPhrase(normalize(command))) {
            stopWakeListening()
            return
        }
        handleCommand(command) { response ->
            transcript = "You: $command\nJ.A.R.V.I.S: $response"
            say(response)
            commandMode = false
            statusText = "LISTENING FOR “HEY JARVIS”"
            scheduleRecognitionRestart()
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun containsWakePhrase(value: String): Boolean =
        value.contains("hey jarvis") || value == "jarvis" || value.startsWith("jarvis ")

    private fun containsOffPhrase(value: String): Boolean =
        value == "jarvis off" || value.endsWith(" jarvis off") || value == "turn jarvis off"

    private fun handleCommand(raw: String, respond: (String) -> Unit) {
        val command = normalize(raw)
        val result = when {
            command.contains("open settings") || command == "settings" -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); "Opening device settings."
            }
            command.contains("open wi fi") || command.contains("open wifi") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); "Opening Wi-Fi settings."
            }
            command.contains("open bluetooth") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "Opening Bluetooth settings."
            }
            command.contains("turn on flashlight") || command.contains("flashlight on") -> setTorch(true)
            command.contains("turn off flashlight") || command.contains("flashlight off") -> setTorch(false)
            command.contains("what time") || command == "time" ->
                "The time is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            command.contains("hello") ->
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
            return "Camera permission is required before I can control the flashlight. Grant it, then repeat the command."
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

    @Deprecated("Deprecated by Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            enableWakeListening()
        } else if (requestCode == REQUEST_AUDIO) {
            statusText = "Microphone permission denied"
            transcript = "Allow microphone access to use the Hey Jarvis wake phrase."
        }
    }

    override fun onDestroy() {
        listeningEnabled = false
        handler.removeCallbacksAndMessages(null)
        stopRecognizer()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 4103
        private const val REQUEST_CAMERA = 4102
    }
}

@androidx.compose.runtime.Composable
private fun JarvisScreen(
    status: String,
    message: String,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    background: Color,
    accent: Color
) {
    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("J.A.R.V.I.S", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text("PERSONAL INTELLIGENCE SYSTEM", color = accent, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(30.dp))
            Surface(modifier = Modifier.size(180.dp), shape = CircleShape, color = Color(0xFF10283A)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("J", color = accent, fontSize = 76.sp, fontWeight = FontWeight.Light)
                    Text(if (status.contains("OFF")) "STANDBY" else "ACTIVE", color = accent, fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(status, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF102033)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(message, modifier = Modifier.padding(18.dp), color = Color(0xFFE6F4FA), fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Microphone", tint = background)
                Text("  ACTIVATE / STOP LISTENING", color = background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                Text("  DEVICE SETTINGS", color = Color.White)
            }
            Spacer(Modifier.height(18.dp))
            Text("SAY “HEY JARVIS” TO WAKE", color = accent, fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Say “Jarvis off” to stop. Keep this app open and the screen awake.", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
