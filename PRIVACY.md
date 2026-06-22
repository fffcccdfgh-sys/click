# Privacy Notes

Click is designed as an on-device Android automation tool. The app does not
request the Android internet permission, and it does not intentionally upload
scripts, screenshots, OCR results, or calibration data.

## Data Processed Locally

The app may process the following data on the device:

- Screen frames captured through Android MediaProjection for OCR and color
  matching.
- Accessibility gesture state used to dispatch taps and swipes.
- Saved ordinary scripts and PVZ2 scripts.
- PVZ2 calibration points and areas.
- USB sync files placed in the app-specific external files directory.
- OCR debug crop images, only if a developer build explicitly enables debug
  image saving.

## Local Storage

Saved scripts and calibration data are stored in the app's private Android app
data. USB sync files and OCR debug images may be stored under the app-specific
external files directory, for example:

```text
Android/data/com.fffcccdfgh.androidclicker/files/
```

OCR debug crop saving is disabled by default. If a developer build enables it,
debug crops are stored under a local `Pictures/ocr-debug` directory inside the
app-specific external files area. These images may contain parts of the screen
you were automating.

## Permissions

The app asks for powerful Android capabilities:

- Accessibility service: dispatches tap and swipe gestures.
- Floating window permission: displays control panels above other apps.
- Screen capture permission: reads screen frames for OCR and color matching.
- Foreground service permission: keeps capture and floating controls active.
- Optional `WRITE_SECURE_SETTINGS`: useful on development/test devices for
  restoring accessibility state when granted through ADB.

Only grant these permissions if you understand what the app does and trust the
build you installed.

## Deleting Local Data

To remove local data, clear app storage from Android system settings or
uninstall the app. If OCR debug images or USB sync files were written to the
app-specific external files area, deleting the app should remove that directory
on normal Android devices.

## Third-Party SDKs

The app includes bundled PaddleOCR/Paddle Lite/OpenCV components and depends on
Google ML Kit for OCR fallback. Review `THIRD_PARTY_NOTICES.md` for component
details.
