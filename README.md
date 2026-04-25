# Priyatra Guide

Android app (Kotlin, Jetpack Compose). Build with Android Studio or Gradle.

## Setup

1. Clone the repository.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to your Android SDK path and add `GROQ_API_KEY` (see [Groq console](https://console.groq.com/keys)).
4. Build: `./gradlew assembleDebug` or use **Build → Make Project** in Android Studio.

`local.properties` is not committed; do not commit API keys.
