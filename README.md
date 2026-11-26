# J.A.R.V.I.S

Android-first personal voice assistant and opt-in game accessibility prototype.

## Voice activation
- Keep the app open in the foreground and keep the phone screen awake.
- Grant microphone permission when Android asks.
- Say **“Hey Jarvis”** to enter command mode.
- Say **“Jarvis off”** to stop listening.
- Listening stops when the app is sent to the background.

## Game assistance prototype
- Say **“enable game control”** to open Android Accessibility settings. Manually enable **J.A.R.V.I.S Game Control**.
- Say **“analyze game”** to read visible accessibility text and labels exposed by the current app.
- Say **“tap X Y”** to send a touch gesture at screen pixel coordinates, for example “tap 420 900”.
- Gestures only work while the user-enabled accessibility service is active.

Important limitations:
- This prototype does not visually understand every game. Many games render their entire interface as graphics/canvas and expose no accessibility text; those screens return no readable UI.
- Visual screen analysis using MediaProjection plus OCR/vision has not been implemented yet.
- Game-specific strategy analysis, automatic gameplay, controller input, and remote game control have not been implemented.
- Some games block overlays or accessibility gestures, and app behavior varies by device.
- Do not use this for cheating, botting, bypassing anti-cheat, or violating a game's rules.

## Current foundation
- Kotlin + Jetpack Compose interface
- Foreground speech recognition and Android Text-to-Speech
- Settings, Wi-Fi, Bluetooth and flashlight commands
- Opt-in Android Accessibility Service for visible UI text inspection and user-requested tap gestures

## Build
Requirements: Android Studio, JDK 17, Android SDK 35.

1. Open this repository in Android Studio.
2. Allow Gradle sync to finish.
3. Run the `:app` configuration on an Android 8.0+ device.

## Remote access roadmap
Remote access is not implemented. It requires a separately deployed authenticated relay, user-approved pairing, TLS, short-lived tokens, revocation, audit logs, a visible foreground notification, and Android's explicit MediaProjection/Accessibility approvals for the relevant functions. No stealth control, unattended microphone/camera capture, lock-screen bypass, or security-setting bypass.

Never place API keys, relay secrets, or private signing keys in the Android app.
