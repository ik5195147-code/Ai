# NUSU Starter (Android, Kotlin)

Voice assistant: Gemini Live (direct WebSocket, no backend) + AccessibilityService phone control.

## Setup
1. Android Studio -> New Project -> "Empty Activity" (Compose), package `com.nusu.assistant`, minSdk 26.
2. Delete the template MainActivity.kt. Copy `app/src/main/**` from this folder over yours
   (merge AndroidManifest.xml; keep the template's build files).
3. Add to app/build.gradle.kts dependencies:
       implementation("com.squareup.okhttp3:okhttp:4.12.0")
       implementation("androidx.security:security-crypto:1.1.0-alpha06")
4. Run on a real phone. In the app: paste your Gemini API key -> Save -> enable Accessibility -> Start.

## Files
- GeminiLive.kt            WebSocket client (setup, audio in/out, tool calls)
- AudioIO.kt               mic 16 kHz in, speaker 24 kHz out
- PhoneTools.kt            tool list + what each tool does  <- add new abilities here
- NusuAccessibilityService.kt   read screen / tap / type / scroll / back / home
- AssistantService.kt      foreground service that keeps it running
- SecureStore.kt           API key encrypted on device
