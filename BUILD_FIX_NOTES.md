# Baby Voice Crash / Build Fix Notes

This source revision fixes the microphone and wake-word stability problems found in the previous revision.

## Changes

- Removed the invalid service-level `isActive` coroutine reference and replaced it with an explicit service lifecycle flag.
- Kept `isActive` only inside the passive AudioRecord coroutine where it is valid.
- Removed nullable `Runnable` usage from wake-candidate Handler callbacks.
- Added a safe delay before reacquiring the microphone after `SpeechRecognizer` teardown, reducing AudioRecord/SpeechRecognizer native-audio races.
- Pausing foreground/manual voice now cancels, destroys, and releases the background recognizer/microphone owner.
- Background wake-word resume recreates the recognizer when necessary and waits for the native audio stack to settle.
- Added explicit microphone foreground-service type when calling `startForeground()` on Android 10+.
- Added the Android speech-recognition `<queries>` entry required for package visibility on Android 11+.
- Enabled `baby` as a default wake phrase alongside `hey baby`, `hi baby`, and `hello baby`.
- Made the service coroutine scope use `SupervisorJob` and cancel cleanly on service destruction.
- Wake-word candidate detection remains gated by sustained speech energy, debounce, and confidence matching; arbitrary speech/noise is rejected instead of activating the command path.

## Build configuration

- JDK: 21
- Gradle in GitHub Actions: 8.13
- Android SDK: 36
- Compile SDK: 36
- Target SDK: 35
- Min SDK: 26
- AGP: 8.13.2
- Kotlin: 2.3.20

## Verification performed in this environment

- Confirmed the invalid `isActive` usage is gone from service-level lifecycle logic.
- Confirmed no nullable `Runnable` is passed to Handler `removeCallbacks()`/`postDelayed()` in the wake-word service.
- Confirmed the microphone FGS type is explicit.
- Confirmed the SpeechRecognizer package-visibility query is present.
- Confirmed the `baby` wake phrase defaults to enabled.
- Confirmed the source tree contains the complete Android project and the existing GitHub Actions build workflow.

A full Android APK compilation cannot be executed in this container because it does not have the Android SDK/Gradle installation. The GitHub Actions workflow in this project is the build environment and should be used for the authoritative APK build.
