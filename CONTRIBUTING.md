# Contributing

Thanks for taking an interest in Click. This is maintained as a practical
Android automation tool, so changes should keep the app buildable and usable on
real devices.

## Development Setup

1. Clone the repository.
2. Open it in Android Studio.
3. Let Android Studio create `local.properties` with your local Android SDK
   path.
4. Build a debug APK:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

5. Run unit tests:

   ```powershell
   .\gradlew.bat test
   ```

## Contribution Guidelines

- Do not commit `local.properties`, release keystores, signing passwords,
  device-specific files, debug screenshots, or generated build outputs.
- Keep permission-sensitive behavior explicit in code and documentation.
- Do not add network upload, analytics, or telemetry without a clear privacy
  discussion and documentation update.
- When changing vendored native libraries, OCR models, or third-party code,
  update `THIRD_PARTY_NOTICES.md`.
- Test changes with at least the relevant unit tests and, for UI/automation
  behavior, a manual device run.

## Scripts

Lua scripts can automate touches and may interact with other apps. Contributions
that add example scripts should avoid private data, account automation, payment
flows, or behavior that clearly violates another service's rules.
