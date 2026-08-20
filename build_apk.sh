#!/usr/bin/env sh
set -eu

# MiuiHome Landscape V5 build (AGP 8.10.1 / Gradle 8.13 / Java 17).
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$PROJECT_DIR"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "[ERROR] Set JAVA_HOME to a Java 17 installation." >&2
    exit 1
fi
if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    echo "[ERROR] Set ANDROID_SDK_ROOT to an SDK containing platform 33." >&2
    exit 1
fi
if [ ! -f "$ANDROID_SDK_ROOT/platforms/android-33/android.jar" ]; then
    echo "[ERROR] Android SDK platform 33 was not found." >&2
    exit 1
fi

chmod +x ./gradlew
./gradlew :app:assembleDebug --no-daemon
echo "[OK] $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
