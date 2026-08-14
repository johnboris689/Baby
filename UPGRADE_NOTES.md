# Baby — Final Glass / Voice / Build Stability Upgrade

This package is based on the latest Baby main source (commit bcc28cd5e16fd4f135b480c2ebf880fdfe203021) and keeps its glass UI, attachment processing, memory/companion features, and voice functionality.

## Build fix

- AGP 8.13.2
- Gradle 8.13 installed by GitHub Actions (no checked-in Gradle wrapper)
- Kotlin 2.3.20
- KSP 2.3.6
- JDK 21
- Android SDK 36
- Modern `kotlin.compilerOptions` JVM target configuration
- No `kotlin.sourceSets` or legacy `kotlinOptions` configuration
- Root plugin aliases are declared once with `apply false` and applied in the app module.
- KSP 2.3.6 is used with the Kotlin 2.3.20 toolchain; KSP 2.3.6 also includes fixes around built-in-Kotlin detection.

## Voice fixes

- Manual microphone recording pauses passive background listening.
- Passive and foreground microphone sessions cannot overwrite each other's generations.
- On-device speech recognition is preferred when supported, with Android recognizer fallback.
- Partial results are enabled.
- Speech timeout/no-match/busy/client/server errors retry automatically.
- Manual wake phrases Hey Baby / Hi Baby / Hello Baby are recognized.
- Background VAD is less aggressive and prevents duplicate passive jobs.
- Passive microphone jobs are cancelled when explicitly stopped.
- TTS completion resumes background voice listening safely.
- The service waits briefly for TTS initialization before the wake acknowledgement.

## UI and companion features

- Futuristic glassmorphism Home, Chat, Settings and Memory surfaces.
- Animated voice orb and explicit Listening / Thinking / Speaking states.
- Faster Gemini path with bounded wait and offline companion fallback.
- Persistent local memory and mood-aware companion behavior.
- ZIP/document/image/video/audio attachment processing.

## CI

GitHub Actions installs JDK 21, Android API 36/build-tools 35.0.0 and Gradle 8.13, runs `gradle :app:assembleDebug --no-daemon --stacktrace`, verifies the APK, and uploads `baby-app-debug-apk`.
