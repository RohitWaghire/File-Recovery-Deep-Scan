# File Recovery & Deep Scan

[![Android CI](https://github.com/RohitWaghire/File-Recovery-Deep-Scan/actions/workflows/android.yml/badge.svg)](https://github.com/RohitWaghire/File-Recovery-Deep-Scan/actions/workflows/android.yml)

An Android app that scans accessible storage for photos, videos, audio, and
documents, classifies them by file signature, and copies selected files into a
dedicated recovery folder. It also keeps a vault of recovered files and a
history of past scans, and can securely shred files it has recovered.

> **Scope note:** This app works on files the OS makes readable to it — its own
> cache/files directories and, with the user's permission, the shared media
> directories (DCIM, Pictures, Download). It classifies and copies those files;
> it does **not** perform raw disk/sector carving of truly deleted data. The
> debug build also seeds a few sample files into the app cache so a scan is
> never empty during testing.

## Features

- **Deep scan** of app storage and (with permission) shared media folders
- **Signature-based detection** — files are typed by magic-header bytes first,
  falling back to extension (handles mismatched or missing extensions)
- **Category scans** — Photos, Videos, Audio, Documents, or a full scan
- **Recovery** — copy selected files into `RecoveredFiles/` with automatic,
  collision-safe unique naming
- **Secure shred** — multi-pass (zero-fill + secure-random) overwrite before delete
- **Vault & history** — recovered files and past scans persisted with Room
- **Cancellable scans** with live progress

## Architecture

MVVM with a single-activity Jetpack Compose UI:

```
UI (Compose)            ui/screens/MainDashboard.kt
   │  collects StateFlow
ViewModel               viewmodel/RecoveryViewModel.kt   (scan + recovery logic)
   │
Repository              data/RecoveryRepository.kt
   │
Room DAO / Database     data/RecoveryDao.kt, RecoveryDatabase.kt
   └─ Entities          data/RecoveryEntities.kt         (ScanHistory, RecoveredFile)
```

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2024.09.00), Material 3 |
| Async | Kotlin Coroutines + StateFlow |
| Persistence | Room 2.7.0 (KSP) |
| Build | Android Gradle Plugin 9.1.1, Gradle 9.5.1 |
| Tests | JUnit4, Robolectric 4.16.1, Roborazzi 1.59.0 |
| Min / Target / Compile SDK | 24 / 36 / 36.1 |

## Prerequisites

- **JDK 17** (e.g. Eclipse Temurin)
- **Android Studio** (build 261 / 2025.x or newer — required for Gradle ≥ 9.3.1), or
  a standalone Gradle ≥ 9.3.1
- **Android SDK** with platform `android-36` and matching build-tools

## Getting started

1. **Clone**

   ```bash
   git clone https://github.com/RohitWaghire/File-Recovery-Deep-Scan.git
   cd File-Recovery-Deep-Scan
   ```

2. **Open in Android Studio** and let the Gradle sync finish. The project uses
   the committed Gradle wrapper (9.5.1), so no separate Gradle install is needed.

3. **Generate a debug keystore.** The debug build is signed with a keystore at
   the repo root (`debug.keystore`), which is gitignored. Create it once:

   ```bash
   keytool -genkeypair -v -keystore debug.keystore \
     -storepass android -keypass android \
     -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```

4. **Run** on an emulator or device (▶ in Android Studio, or `./gradlew installDebug`).

> **Optional:** the Secrets Gradle plugin is wired to read a `GEMINI_API_KEY`
> from a `.env` file (see `.env.example`), but the current app code does not call
> any Gemini API, so this is not required to build or run.

## Build & test

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run the Robolectric unit tests
```

Unit tests are configured with Robolectric `@Config(sdk = 34)` so they run on
JDK 17 (SDK 36 in Robolectric would require JDK 21).

## Continuous integration

Every push and pull request to `main` runs [`.github/workflows/android.yml`](.github/workflows/android.yml),
which builds the debug APK and runs the unit tests on a fresh runner.

## Permissions

Declared in `AndroidManifest.xml`:

- `READ_EXTERNAL_STORAGE` (≤ API 32)
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (API 33+)
- `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+ partial photo/video access)

If permissions are denied, the app falls back to scanning only its own
sandboxed storage.
