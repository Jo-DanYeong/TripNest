# Build-time server configuration

TripNest Android now reads the backend URL from Gradle build configuration.
The app no longer asks users to type the server address inside the app.

## Option 1: local.properties

Add these values to the project root `local.properties` file:

```properties
BACKEND_BASE_URL=https://your-backend.example.com
BACKEND_FALLBACK_URL=https://your-backend.example.com
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

Then build normally:

```powershell
.\gradlew.bat assembleDebug
```

## Option 2: Gradle properties

Pass values only for this build:

```powershell
.\gradlew.bat assembleDebug -PBACKEND_BASE_URL=https://your-backend.example.com -PBACKEND_FALLBACK_URL=https://your-backend.example.com
```

## Option 3: Environment variables

Set environment variables before building:

```powershell
$env:BACKEND_BASE_URL="https://your-backend.example.com"
$env:BACKEND_FALLBACK_URL="https://your-backend.example.com"
.\gradlew.bat assembleDebug
```

Resolution order is:

1. Gradle `-P` property
2. Environment variable
3. `local.properties`
4. Hardcoded development default
