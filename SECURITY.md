# Security Policy

## Supported Versions

Security fixes target the current `main` branch unless a release branch is
explicitly announced.

## Reporting A Vulnerability

If the repository is public, report security issues through GitHub using the
least-sensitive public detail possible. If a report requires private details,
contact the maintainer through the GitHub profile associated with this
repository or use GitHub private vulnerability reporting if it is enabled.

Do not post real credentials, private screenshots, signing keys, or exploit
payloads in public issues.

## High-Risk Areas

Click intentionally uses powerful local automation capabilities:

- Accessibility gestures can tap and swipe other apps.
- Screen capture can read visible screen content while permission is active.
- Floating windows can appear above other apps.
- Imported Lua scripts can automate touches and run inside the app process.
- Optional `WRITE_SECURE_SETTINGS` is intended only for development or test
  devices where the user explicitly grants it through ADB.

Only install builds from sources you trust, and only import Lua scripts you have
reviewed or received from a trusted source.

## Signing Keys And Secrets

Release signing files such as `key` and signing passwords in `local.properties`
must never be committed, uploaded, printed, or shared. Forks should generate
their own release signing keys.

If a signing key, token, API key, password, or other secret is accidentally
published, revoke or rotate it before making the repository public.
