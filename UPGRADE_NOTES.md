# Baby — Glass UI / Voice Stability Upgrade

## Included

- Futuristic glassmorphism redesign for Home, Chat, Settings and Memory surfaces.
- Manual speech input now owns the microphone exclusively and pauses the background listener.
- SpeechRecognizer no longer closes the UI immediately at `onEndOfSpeech`; final results are allowed to arrive.
- Speech timeout/no-match/busy errors retry automatically before showing an error.
- `Hey Baby`, `Hi Baby`, and `Hello Baby` work without a custom phrase. Saying only a wake phrase manually produces an immediate acknowledgement.
- Background wake-word VAD is less aggressive about microphone cycling and uses partial recognition to reduce wake latency.
- Old background microphone generations cannot release a newer recorder.
- Gemini calls have shorter network limits and a bounded 18-second online wait before offline companion fallback.
- Artificial response typing latency remains removed.
- Gradle wrapper JAR removed; CI installs Gradle 9.3.1 directly and disables wrapper validation because no wrapper is checked in.
- AGP 9 built-in Kotlin + KSP2 compatibility is retained.
- Gemini API key can be provided with the `GEMINI_API_KEY` Gradle property or environment variable, or entered in Baby settings.
- ZIP/document/media attachment processing from the previous upgrade remains included.

## CI

GitHub Actions installs JDK 21, Android API 36/build-tools 36.0.0 and Gradle 9.3.1, then runs `gradle assembleDebug` and uploads `baby-app-debug-apk`.


## Build-stability correction (latest)

- Rebased the build toolchain to AGP 8.13.2 + Gradle 8.13 + Kotlin 2.3.20 + KSP 2.3.10.
- Removed the AGP 9 built-in-Kotlin temporary `android.disallowKotlinSourceSets=false` workaround.
- Restored the standard Kotlin Android plugin so Kotlin 2.3.20 and the Compose compiler plugin use one consistent compiler toolchain.
- Migrated the JVM target to the Kotlin `compilerOptions` DSL.
- Updated Room to 2.8.4 and Compose BOM to 2026.06.00.
- CI no longer checks in or executes a Gradle wrapper JAR.
- Passive voice now cancels the previous passive job before starting another recorder.
- Wake VAD is less aggressive and uses a lower speech threshold.
- On-device speech recognition is preferred where available, with the normal Android recognizer as fallback.
