# J.A.R.V.I.S

Android-first personal voice assistant.

## Voice activation
- Keep the app open in the foreground and keep the phone screen awake.
- Grant microphone permission when Android asks.
- Say **“Hey Jarvis”** to enter command mode.
- Say **“Jarvis off”** to stop listening and end the current local voice session.
- The app stops microphone recognition when it is sent to the background or the screen/activity is no longer foregrounded. It does not run a hidden wake-word listener.

Wake-word detection currently uses Android's speech-recognition service. Recognition quality, partial-result behavior, language support, and availability depend on the device's installed speech service and network configuration. This is not a dedicated always-on low-power hardware hotword engine.

## Current foundation
- Kotlin + Jetpack Compose interface
- Foreground speech recognition and spoken responses through Android Text-to-Speech
- Explicit device actions: open Settings, Wi-Fi settings, Bluetooth settings, and toggle the flashlight (with Android permission)
- User-visible microphone permission and foreground-only listening

## Build
Requirements: Android Studio, JDK 17, Android SDK 35.

1. Open this repository in Android Studio.
2. Allow Gradle sync to finish.
3. Run the `:app` configuration on an Android 8.0+ device.

## Remote access roadmap
Remote access is not implemented yet. A secure implementation requires:
- A separately deployed authenticated relay/signaling service.
- Pairing initiated on the Android device and approved by the user.
- Short-lived session tokens, TLS, device revocation, and audit logs.
- A persistent foreground notification while a remote-control session is active.
- Android MediaProjection consent for screen sharing and explicit Accessibility-service enablement for permitted UI automation.
- No unattended microphone/camera capture, stealth mode, lock-screen bypass, or security-setting bypass.

Never put API keys, relay secrets, or private signing keys in the Android app. Keep server credentials in the backend environment.
