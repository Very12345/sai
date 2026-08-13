# Building

The checked-in Gradle wrapper is pinned to 9.4.1. The project uses AGP 9.2 built-in Kotlin support, Kotlin serialization/Compose 2.3.21, compile SDK 37, target SDK 36 and min SDK 29.

On this workstation all large components are intentionally stored below `D:\Code`:

- Android Studio Quail 2 2026.1.2: `D:\Code\Android Studio`
- Android SDK and NDK 28.2: `D:\Code\Android\Sdk`
- Temurin JDK 17: `D:\Code\Java\jdk-17.0.20+8`
- Gradle user home: `D:\Code\GradleHome`
- AVDs: `D:\Code\Android\Avd`
- IDE caches and plugins: `D:\Code\Android\StudioData`

Run:

```powershell
.\scripts\build.ps1
. .\scripts\dev-env.ps1
.\gradlew.bat lintDebug cyclonedxBom
```

Debug APKs are written below `app\build\outputs\apk\debug`. Release signing values must be supplied outside the repository; API keys and signing passwords must never be committed.

Normal debug and instrumentation builds use the isolated `com.phoneagent.app.debug` package so connected tests cannot modify a user's installed app. Only an intentional signed device build should pass `-PphoneAgentDebugPackageSuffix=false`.

Use `scripts\start-android-studio.ps1` to start the IDE with its caches redirected to D:.
