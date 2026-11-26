# J.A.R.V.I.S

Android-first personal voice assistant.

## Current foundation
- Kotlin + Jetpack Compose interface
- Speech input through Android's speech-recognition service
- Spoken responses through Android Text-to-Speech
- Explicit, foreground user-triggered device actions: open Settings, Wi-Fi settings, Bluetooth settings, and toggle the flashlight (with Android permission)
- No hidden background recording or silent remote-control capability

## Build
Requirements: Android Studio, JDK 17, Android SDK 35.

1. Open this repository in Android Studio.
2. Allow Gradle sync to finish.
3. Run the `:app` configuration on an Android 8.0+ device.

## Remote access roadmap
Remote access is intentionally not enabled in this starter. A secure implementation requires:
- A separately deployed authenticated relay/signaling service.
- Pairing initiated on the Android device and approved by the user.
- Short-lived session tokens, TLS, device revocation, and audit logs.
- A persistent foreground notification while a remote session is active.
- Android MediaProjection consent for screen sharing and explicit Accessibility-service enablement for permitted UI automation.
- No unattended microphone/camera capture, stealth mode, lock-screen bypass, or security-setting bypass.

Never put API keys, relay secrets, or private signing keys in the Android app. Keep server credentials in the backend environment.
