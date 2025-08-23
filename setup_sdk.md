# Android SDK Setup Instructions

This project targets Android SDK 35 and requires JDK 17. Follow the steps below to resolve common "SDK not found" or "Failed to find target with hash string 'android-35'" issues.

## Prerequisites
- Android Studio (Giraffe/Flamingo or newer) OR IntelliJ IDEA with Android plugin
- Android SDK Platform 35 and Build-Tools 35.x installed
- JDK 17 available to Gradle

## 1) Locate your Android SDK
Common locations:
- Windows: `C:\\Users\\{username}\\AppData\\Local\\Android\\Sdk`
- macOS: `/Users/{username}/Library/Android/sdk`
- Linux: `/home/{username}/Android/Sdk`
- IntelliJ IDEA: File > Project Structure > SDKs (check "SDK Home Path")

## 2) Point the project to your SDK (choose one)
A. local.properties (recommended)
Create or edit `local.properties` in the project root and set:
```properties
sdk.dir=C:\\Users\\{username}\\AppData\\Local\\Android\\Sdk   # Windows example
# sdk.dir=/Users/{username}/Library/Android/sdk                        # macOS example
# sdk.dir=/home/{username}/Android/Sdk                                 # Linux example
```
Note: Use double backslashes in Windows paths when editing with some editors. Android Studio handles single backslashes fine.

B. Environment variables (alternative)
Set one of these environment variables to your SDK path:
- ANDROID_SDK_ROOT
- ANDROID_HOME (legacy, still supported)

On Windows PowerShell (example):
```powershell
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', 'C:\\Users\\{env:USERNAME}\\AppData\\Local\\Android\\Sdk', 'User')
```
Restart your IDE/terminal after setting environment variables.

## 3) Install required SDK components
If the build fails with missing platform or build-tools, install via Android Studio (SDK Manager) or the command line SDK Manager.

Command line examples (replace sdkmanager path as needed):
```powershell
# Windows PowerShell
"$env:ANDROID_SDK_ROOT\\cmdline-tools\\latest\\bin\\sdkmanager.bat" "platforms;android-35" "platform-tools" "build-tools;35.0.0"
```
```bash
# macOS/Linux
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "platform-tools" "build-tools;35.0.0"
```

Required components for this project:
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0 (or newer 35.x)
- Android SDK Platform-Tools

## 4) Ensure JDK 17 is used by Gradle
This project is configured for Java 17 (see build.gradle.kts). In Android Studio:
- File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK: select a JDK 17.

Command line check:
```bash
./gradlew -version
```
Look for "JVM: 17" in the output. If not, configure `JAVA_HOME` or set the Gradle JDK in your IDE.

## 5) Verify the setup
From the project root, run:
```bash
./gradlew tasks
```
On Windows PowerShell:
```powershell
./gradlew.bat tasks
```
You should see the task list without errors.

## Troubleshooting
- Error: Failed to find target with hash string 'android-35'
  - Install "Android SDK Platform 35" and set `sdk.dir` or ANDROID_SDK_ROOT.
- Error: No installed build tools found. Install the Android SDK Build-Tools.
  - Install "build-tools;35.0.0" (or latest 35.x).
- Error about AGP/Kotlin versions
  - Ensure Android Gradle Plugin 8.2.x+ and Kotlin 1.9.x are used (this repo is configured for AGP 8.2.1 and Kotlin 1.9.21).
- Windows path issues
  - Use backslashes in `sdk.dir`, e.g., `C:\\Users\\Tyler\\AppData\\Local\\Android\\Sdk`.
- Still stuck?
  - Delete `.gradle` and `.idea` directories, then re-import the project.