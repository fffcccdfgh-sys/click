# Release And Public Repository Checklist

Use this checklist before making the repository public or publishing an APK.

## Repository Visibility

- Confirm the local `origin` remote points to the intended repository.
- Confirm the target repository is still private while preparing the release.
- Review all uncommitted changes with `git status --short` and `git diff`.
- Do not change repository visibility until the checks below are complete.

## Secrets And Private Files

- Confirm these files are not tracked:
  - `key`
  - `local.properties`
  - `.env` and `.env.*`
  - signing keys, certificates, passwords, tokens, logs, APKs, and AABs
- Run:

  ```powershell
  git ls-files | Where-Object { $_ -match '(?i)(^|/)(key|local\.properties|.*\.env.*|.*token.*|.*secret.*|.*password.*|.*credential.*|auth\.json|credentials\.json)$' }
  ```

- Expected result: no output.
- If a real secret was ever committed, rotate or replace it before publishing.

## Build Verification

- Run unit tests:

  ```powershell
  .\gradlew.bat test
  ```

- Build a debug APK:

  ```powershell
  .\gradlew.bat assembleDebug
  ```

- For a release APK, use the existing release signing setup from
  `local.properties`; never commit or print signing passwords.

## Documentation

- `README.md` explains the project, permissions, build steps, and script docs.
- `LICENSE` is present.
- `NOTICE` is present.
- `CHANGELOG.md` has an `Unreleased` section.
- `THIRD_PARTY_NOTICES.md` lists bundled native libraries, model files, and
  major dependencies.
- `PRIVACY.md` explains local screen capture, OCR, scripts, storage, and debug
  images.
- GitHub issue templates are present.

## Manual Smoke Test

- Install the debug APK on a test device.
- Open the app.
- Grant accessibility, floating window, and screen capture permissions.
- Start the floating panel.
- Add a tap or wait action.
- Run and stop the script.
- Open script list and verify import/export still works.
- If testing PVZ2 flows, verify calibration screens open and saved values load.

## Publishing Notes

- Public repositories can be viewed and forked by anyone.
- If the repository is made private again later, existing public forks or local
  clones may still exist.
- Do not publish a release APK until the same checklist has passed on the exact
  commit used for the APK.
