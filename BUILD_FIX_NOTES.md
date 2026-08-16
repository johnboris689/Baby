# Baby build stabilization notes

## Fix for GitHub Actions #47 and #49

The failing runner error was:

`The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version, so compatibility cannot be checked.`

The project no longer preloads the Kotlin Gradle plugins from the root `build.gradle.kts`. Kotlin Android, Compose compiler, and KAPT are resolved by the `app` module directly from the version catalog. This avoids the versioned plugin request colliding with a Kotlin plugin that has already entered a higher classloader.

KSP remains removed. Room uses KAPT.

Toolchain:
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.20
- JDK 21
- Android API 36


## Fix for GitHub Actions #50

The Android CI reached `:app:checkDebugAarMetadata` but stopped because the project did not explicitly enable AndroidX while its runtime classpath contains AndroidX dependencies. `gradle.properties` now contains `android.useAndroidX=true`.

The obsolete `design/` directory is absent from this source package. No design reference asset is included.
