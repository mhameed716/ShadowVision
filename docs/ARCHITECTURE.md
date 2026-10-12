# Shadow306 Architecture

## Offline-First Principle

The app MUST work with no internet after setup. This drives every decision:

- No Firebase, no analytics SDKs, no crash reporting that needs network.
- All ML via ONNX Runtime Mobile (CPU + NNAPI/GPU optional).
- Models stored in noBackupFilesDir, never backed up.
- NetworkSecurityConfig bans cleartext and whitelists only model CDN hosts.

## Model Download Manager

### Manifest

`assets/models/manifest.json` is the source of truth. It contains:

- `id`, `fileName`, `sizeBytes`, `sha256`, `url`, `mirrors`, `features`, `minRamMb`, `required`.

The downloader:

1. Loads bundled manifest from assets.
2. Checks installed files via ModelVerifier (size + sha256).
3. For each missing/corrupted model:
   - Checks free space (FileIo.freeBytes) + safety margin.
   - Checks airgap + metered + Wi-Fi (ModelRepository.canUseNetwork).
   - Downloads with Range resume to `.tmp`, then atomic move.
   - Verifies sha256, retries with exponential backoff (RetryPolicy).
   - Falls back to mirrors on failure.
4. Persists manifest copy to noBackupFilesDir/.manifest.json.

### Foreground Service

`ModelDownloadService` shows live notification (percent, speed, ETA). It survives backgrounding because `stopWithTask=false` and is observed by WorkManager (future).

### Sideload

`SideloadScanner` scans:

- `getExternalFilesDir()/Shadow306/models`
- `/sdcard/Download/Shadow306/models`

If manifest.json + .onnx files found, it verifies checksums and copies to internal storage. This enables offline distribution via USB.

## Engine Abstraction

All engines have two paths:

- **ONNX path**: loads ORT via reflection (OnnxEnv). Reflection avoids hard dependency in CI where .so is missing.
- **Fallback path**: pure CPU, fast, approximated. Lets UI work in Preview mode.

`EngineMode` = AUTO | ONNX | FALLBACK, stored in DataStore and selectable in Settings.

### Try-On

- ONNX: warp (TPS) + synthesis (inpaint).
- Fallback: torso heuristic + alpha blend.

### Face Swap

- ONNX: SCRFD + ArcFace + inswapper.
- Fallback: centered face copy.

### Video

- ONNX: video diffusion (4 sec master).
- Fallback: Ken Burns zoom/pan.
- Then FrameInterpolator (RIFE in future) + VideoLooper (cross-fade) to 60 sec.
- Encoder: MediaCodec AVC.

### Removal

- ONNX: cloth parsing (pixel-perfect mask) + inpaint.
- Fallback: color heuristic segmentation + average skin fill + blur feather.

### Translation

- Optional ONNX NMT model `translate_ar_en_fp16.onnx`.
- Fallback lexicon in `assets/lexicon/ar_en.json` (800 entries) + phrase replacement.

## Data Layer

- `AppPreferences` via DataStore (coroutine-safe).
- `TemplateStore` file-based (no Room). Each template is a folder with image.jpg, thumb.jpg, meta.json. Index file for fast startup.
- Exports go to `files/exports/` (private) and can be shared via FileProvider.

## UI

- Compose Material3, dark-first OLED.
- Navigation Compose, single Activity.
- Home shows pack status + 5 tools.
- Setup shows download progress + sideload offer.
- Each tool screen: pick image(s), tune params, run engine, save result.

## Locale

- `values/strings.xml` + `values-ar/strings.xml` (170 keys, verified).
- `LocaleManager` uses AppCompatDelegate per-app locales (Android 13+) and supports RTL.
- `android:supportsRtl=true` + `localeConfig`.

## Security

- No INTERNET after setup (user can enable airgap lock).
- Backup rules exclude models and user media.
- FileProvider for sharing.
- Proguard keeps ORT classes.
