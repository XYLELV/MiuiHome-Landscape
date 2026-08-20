@echo off
setlocal
cd /d "%~dp0"

rem MiuiHome Landscape V5 reproducible Windows build (AGP 8.10.1 / Gradle 8.13 / Java 17).
if exist "%USERPROFILE%\.jdks\jbr-17.0.14\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\jbr-17.0.14"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
) else if not defined JAVA_HOME (
    echo [ERROR] Java 17 was not found. Install Android Studio or set JAVA_HOME.
    exit /b 1
)

if not defined ANDROID_SDK_ROOT (
    if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
)
if not defined ANDROID_SDK_ROOT (
    if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-33\android.jar" (
        set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
    )
)
if not defined ANDROID_SDK_ROOT (
    echo [ERROR] Android SDK was not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.
    exit /b 1
)
if not defined ANDROID_HOME set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

if not exist "%ANDROID_SDK_ROOT%\platforms\android-33\android.jar" (
    echo [ERROR] Android SDK platform 33 was not found.
    exit /b 1
)

call ".\gradlew.bat" :app:assembleDebug --no-daemon
if errorlevel 1 exit /b 1

echo [OK] %CD%\app\build\outputs\apk\debug\app-debug.apk
endlocal
