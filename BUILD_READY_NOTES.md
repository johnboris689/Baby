# Baby build-ready source

This source tree is prepared for the repository's GitHub Actions build, which installs Gradle 8.13 directly and therefore does not require a Gradle wrapper JAR.

## Fix included

`BabyAssistantService.kt` had a Kotlin scope error where the passive `AudioRecord` was declared inside `try` but referenced from `finally`. The recorder is now retained in a nullable release reference that is visible to `finally`, while preserving the existing generation/cancellation and same-worker release design.

## Build command used by CI

`gradle :app:assembleDebug --no-daemon --stacktrace`

The Android workflow installs JDK 21, Android SDK 36/build-tools 36.0.0, and Gradle 8.13.
