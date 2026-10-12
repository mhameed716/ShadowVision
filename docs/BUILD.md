# Build Guide - Shadow306

## Requirements

- JDK 17 (Temurin recommended)
- Android SDK:
  - compileSdk 37
  - build-tools 36.0.0
  - NDK 28.2.13676358 (optional, for ONNX Runtime custom builds)
- Gradle 9.4.1 via wrapper

## Quick Start

```bash
git clone https://github.com/mhameed716/ShadowVision.git
cd ShadowVision

# Verify manifest
python3 tools/verify_manifest.py app/src/main/assets/models/manifest.json

# Build debug APK (base, no models)
./gradlew :app:assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Model Pack

The APK does NOT contain models. On first launch:

1. App loads `assets/models/manifest.json`
2. Checks `noBackupFilesDir/models/` for existing files
3. If missing, shows SetupScreen asking for Wi-Fi
4. Downloads via ModelDownloadService (foreground, resumable)

### Replace placeholder URLs

Edit `app/src/main/assets/models/manifest.json`:

```json
{
  "url": "https://your-cdn.com/tryon_warp_fp16.onnx",
  "sha256": "real_sha256_here"
}
```

Compute sha256:

```bash
sha256sum *.onnx
```

### Offline Sideload

For devices without internet:

```bash
adb shell mkdir -p /sdcard/Download/Shadow306/models
adb push models/*.onnx /sdcard/Download/Shadow306/models/
adb push models/manifest.json /sdcard/Download/Shadow306/models/
```

Open app → "Found models on this device" → Import.

## Release Build

```bash
# Create keystore (once)
keytool -genkey -v -keystore shadow306.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shadow306

# Add to local.properties:
# storeFile=../shadow306.jks
# storePassword=...
# keyAlias=shadow306
# keyPassword=...

./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

## Troubleshooting

- `gradle-wrapper.jar` missing: run `gradle wrapper --gradle-version 9.4.1` if you have Gradle installed.
- `services.gradle.org` blocked in sandbox: build locally, not in restricted environment.
- ONNX Runtime .so not found: ensure `onnxruntime-android` dependency and ABI filters `arm64-v8a, armeabi-v7a`.
- Models not verifying: check sha256 and sizeBytes in manifest.

## CI

GitHub Actions workflow `.github/workflows/android.yml` builds debug APK and runs unit tests.
