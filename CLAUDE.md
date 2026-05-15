# Claude Code Instructions

## Build Commands

This project is on Windows, but Claude Code's `Bash(...)` tool runs commands through a POSIX-style shell.

Use these commands from Bash:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew installDebug
```

Use these commands only from PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat clean assembleDebug
```

Do not run `.\gradlew.bat ...` or `.\\gradlew.bat ...` from `Bash(...)`. Bash parses that Windows path as `.gradlew.bat`, which causes exit code 127 with `command not found`.
