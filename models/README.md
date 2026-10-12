# Shadow306 Model Pack

This folder mirrors `app/src/main/assets/models/manifest.json` for tooling.

## Production checklist

1. Replace placeholder URLs (`https://models.shadow306.app/...`) with your real CDN.
2. Compute real SHA-256 for each `.onnx` file:
   ```bash
   sha256sum *.onnx
   ```
3. Update `sizeBytes` and `sha256` in `manifest.json`.
4. Host `manifest.json` itself at `https://models.shadow306.app/manifest.json` so the app can check for updates (optional).

## Offline distribution (no internet)

Copy the entire model folder to device:

```
/sdcard/Download/Shadow306/models/
  manifest.json
  tryon_warp_fp16.onnx
  tryon_synth_fp16.onnx
  ...
```

On first launch, Shadow306's SideloadScanner will detect it and offer import.

## Size budget

Current pack: ~1.1 GB (8 models). Target for Play Store: keep base APK < 50 MB, models downloaded once via Wi-Fi.

## ONNX Runtime Mobile

Models must be exported with `onnxruntime/tools/mobile_helpers` and quantized to FP16 where possible.

Recommended opsets: 14-17, avoid control flow ops for mobile compatibility.
