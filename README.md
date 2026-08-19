# Baby — Android AI Assistant

This is the corrected Android version of Baby. Unlike the earlier `Baby-source.zip`, this repository is an actual Android/Gradle project and produces an APK through GitHub Actions.

## What works in the APK
- Baby operator chat UI.
- Claude/Anthropic connection using an API key entered locally in Settings.
- Offline/demo mode when no API key is configured; it never pretends an external action happened.
- Android speech recognition with British English preference.
- Android text-to-speech with UK voice preference when the device provides one.
- Local settings storage for the AI endpoint/model/key.
- Responsive dark operator interface.

## GitHub Actions
Every push to `main` or `master` automatically runs `.github/workflows/build-apk.yml`, builds `app-debug.apk`, and uploads it as the `Baby-debug-apk` workflow artifact. The workflow can also be started manually.

## Important
The previous web/Next.js source was not an APK project. Server-only Next.js API routes cannot simply become an Android APK. This project moves the core Baby experience into a native Android application so GitHub Actions can actually compile and package it.

## GitHub Actions APK build

Every push to `main` or `master` automatically runs `.github/workflows/build-apk.yml` and uploads the debug APK as the `Baby-debug-apk` artifact. The Android Java and Kotlin compiler targets are both pinned to JVM 17. GitHub Actions uses JDK 17 and Gradle 8.9, matching the Android Gradle Plugin configuration and avoiding inconsistent JVM-target errors.
