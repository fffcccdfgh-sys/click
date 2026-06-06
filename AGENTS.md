## Android release signing

When building a release APK, use the existing release signing configuration.

- The release keystore is the project-root file `key`.
- The release key alias is `key0`.
- Signing values are read from `local.properties`:
  - `RELEASE_STORE_FILE=key`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS=key0`
  - `RELEASE_KEY_PASSWORD`
- Do not regenerate, replace, move, commit, print, or upload the keystore.
- Do not hard-code or reveal signing passwords. Keep password values in `local.properties`.
- Build release packages through the Gradle release variant so `app/build.gradle.kts` applies `signingConfigs.release`.

The current release certificate public fingerprint is:

`SHA-256: e6f49dea9378a959ad85be36a9cb0514d1db9feb4ae2c58713300854fadc83fd`
