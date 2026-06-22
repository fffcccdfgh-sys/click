# Click

Click is an Android automation tool for recording, editing, importing, and
running tap/swipe scripts. It is built for local use on Android devices and
combines accessibility gestures, floating controls, screen capture, OCR, color
matching, and Lua scripting.

The app UI and scripting documentation are currently Chinese-first.

## Features

- Floating control panel for creating and running tap, swipe, wait, and program
  actions.
- Lua script import/export with percentage-based screen coordinates.
- Conditional execution with OCR text matching and color matching.
- On-device OCR using bundled PaddleOCR assets on `arm64-v8a`, with ML Kit
  fallback where available.
- Screen-capture based OCR and color detection.
- PVZ2-specific script list, calibration flow, and USB script sync helpers.

This project is not affiliated with Plants vs. Zombies 2, PopCap, EA, or any
other game publisher. Use automation responsibly and follow the rules of the
apps and services you automate.

## Repository Contents

- `app/src/main/java/` - Android app source code.
- `app/src/main/cpp/` - Native PaddleOCR bridge and vendored native headers.
- `app/src/main/assets/paddleocr/` - Bundled PaddleOCR model assets.
- `app/src/main/jniLibs/` - Runtime native libraries for `arm64-v8a`.
- `docs/normal-programming-guide.md` - General Lua scripting guide.
- `docs/pvz2-calibration-guide.md` - PVZ2 calibration walkthrough and optional
  screenshot examples.
- `docs/pvz2-calibration-variables.md` - PVZ2 calibration variable reference.

The repository intentionally keeps the native libraries and OCR models checked
in so a fresh clone is easier to build and run.

## Requirements

- Android Studio with a recent Android Gradle Plugin toolchain.
- JDK 17 or newer.
- Android SDK for compile SDK 36.
- CMake 3.22.1.
- An Android device or emulator running Android 8.0+ (`minSdk 26`).
- `arm64-v8a` device support for the bundled native OCR path.

Gradle Wrapper is included. Use the wrapper instead of a system Gradle install.

## Build

Clone the repository, open it in Android Studio, let Android Studio create your
local `local.properties`, then build from the IDE or terminal:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```sh
./gradlew assembleDebug
```

Run unit tests:

```powershell
.\gradlew.bat test
```

Release signing is maintainer-local. The release keystore file `key` and
password values in `local.properties` are intentionally ignored by Git and must
not be committed. Forks should create their own signing key.

## Basic Use

1. Install a debug or release APK on an Android device.
2. Open the app and grant the requested capabilities:
   - Accessibility service, used to dispatch gestures.
   - Floating window permission, used for the control panels.
   - Screen capture permission, used for OCR and color detection.
3. Start the floating control panel.
4. Record actions or import a Lua script.
5. Run the script and stop it from the floating control or notification.

For script syntax, see:

- [General scripting guide](docs/normal-programming-guide.md)
- [PVZ2 calibration guide](docs/pvz2-calibration-guide.md)
- [PVZ2 calibration variables](docs/pvz2-calibration-variables.md)

Only run scripts you trust. Imported Lua scripts can automate touches and may
run with a broad Lua runtime inside the app process.

## Permissions And Privacy

Click does not request the Android internet permission. Screen frames, OCR
results, scripts, and calibration data are processed locally on the device.

Because the app uses powerful Android capabilities, review the privacy notes
before installing or distributing builds:

- [Privacy notes](PRIVACY.md)

## Third-Party Software

This repository includes third-party source, headers, models, and native
libraries. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution
and license notes.

## Issues And Maintenance

- Use GitHub issues for bug reports and feature requests.
- Maintainers can use [docs/release-checklist.md](docs/release-checklist.md)
  before making the repository public or publishing an APK.

## License

Project code is licensed under the Apache License 2.0. Third-party components
remain under their own licenses.

Copyright 2026 fffcccdfgh-sys.
