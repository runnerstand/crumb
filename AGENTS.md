# Crumb Android Project Rules

## Project Context

Crumb is a native Android university prototype.

- Language: Kotlin
- UI: Jetpack Compose
- IDE: Android Studio
- Backend: existing FastAPI server
- Emulator backend base URL: `http://10.0.2.2:8000`
- Database: MySQL is accessed only through FastAPI
- Authentication: do not implement yet
- User identity: use a temporary local user identity where required

## Current Android Setup

- Module: `:app`
- Package / namespace: `com.example.crumb`
- Application ID: `com.example.crumb`
- Minimum SDK: `31`
- Target SDK: `36`
- Compile SDK: `36`
- Gradle wrapper: `8.13`
- Android Gradle Plugin: `8.13.2`
- Kotlin: `2.0.21`
- Compose: enabled through the Kotlin Compose plugin and Compose BOM `2024.09.00`
- Java target: `11`

## Architecture Rules

- Keep the app simple and appropriate for a university prototype.
- The Android app must never connect directly to MySQL.
- All remote data access must go through the FastAPI backend.
- Do not add authentication until it is explicitly requested.
- Prefer small, focused changes over broad refactors.
- Do not replace working Gradle, Kotlin, Android Gradle Plugin, or Compose versions unnecessarily.
- Follow the existing Kotlin and Jetpack Compose style unless there is a clear reason to change it.
- Avoid large unrelated changes, generated churn, or IDE-only edits.

## Recommended Folder Structure

Use this structure as the app grows:

```text
app/src/main/java/com/example/crumb/
  MainActivity.kt
  CrumbApp.kt
  core/
    config/
    network/
    model/
  data/
    remote/
    repository/
  domain/
    model/
    repository/
    usecase/
  feature/
    home/
    pantry/
    recipe/
    shopping/
  ui/
    components/
    theme/
```

Suggested responsibilities:

- `core/config`: constants such as the backend base URL and temporary local user ID.
- `core/network`: Retrofit/Ktor client setup and shared API behavior.
- `core/model`: shared DTO or app-wide simple models when needed.
- `data/remote`: FastAPI endpoint interfaces and request/response DTOs.
- `data/repository`: repository implementations that call the backend.
- `domain`: optional lightweight business models and use cases if screens become complex.
- `feature`: screen-specific UI, state holders, and feature logic.
- `ui/components`: reusable Compose components.
- `ui/theme`: Material theme, colors, and typography.

## Build Command

```powershell
.\gradlew.bat build
```

If `JAVA_HOME` is not configured, Android Studio's bundled JBR can be used for the command:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

## Testing Commands

Run local unit tests:

```powershell
.\gradlew.bat test
```

Run Android instrumented tests when an emulator or device is available:

```powershell
.\gradlew.bat connectedAndroidTest
```

## Current Notes

- The project currently contains the default single-activity Compose starter screen.
- Existing tests are the default generated unit and instrumented test examples.
- `local.properties` should remain machine-local and should not be used for application secrets.
