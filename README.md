# Priyatra Guide

Android app (Kotlin, Jetpack Compose). Build with Android Studio or Gradle.

## Download and install the app (sideload)

**Android does not support “one tap from GitHub” the same way as the Play Store.** Do this on the phone (or after copying the file):

1. Open the [**Releases** page for this repo on GitHub](https://github.com/subhrajit2564/priyatra/releases).
2. Open the **Priyatra (latest from main)** (tag `dev-build`) prerelease, or any **v…** version you want.
3. Download **`priyatra-guide.apk`**.
4. On the device: allow **install from unknown sources** (or *Install unknown apps*) for the app you use to open the file (e.g. Chrome, Files, or GitHub in-app browser, depending on the device).
5. Open the downloaded APK and complete installation.

**Direct link to the current dev build asset** (after the first successful workflow on `main`):

- `https://github.com/subhrajit2564/priyatra/releases/download/dev-build/priyatra-guide.apk`

> Prerelease builds are **debug** APKs. For a named version, use a **v*.*.*** tag in Releases (e.g. `v1.0.0`).

**Alternative:** each workflow run also uploads the same file under the **Actions** → run → **Artifacts** section, but a **signed-in GitHub** account is often required. Releases are the better link to share with others.

## Develop locally

1. Clone the repository.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to your Android SDK path and add `GROQ_API_KEY` (see [Groq console](https://console.groq.com/keys)).
4. Build: `./gradlew assembleDebug` or use **Build → Make Project** in Android Studio.

`local.properties` is not committed; do not commit API keys.
