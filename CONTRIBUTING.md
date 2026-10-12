# Contributing to Shadow306

Thank you for considering contributing to Shadow306!

## Development Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/mhameed716/ShadowVision.git
   cd ShadowVision
   ```

2. Open in Android Studio Hedgehog or newer.

3. Build:
   ```bash
   ./gradlew :app:assembleDebug
   ```

## Model Development

### Adding a new model

1. Export your model to ONNX (opset 14-17).
2. Quantize to FP16:
   ```bash
   python -m onnxruntime.tools.convert_onnx_models_to_ort --optimization_style Fixed --input model.onnx --output model_fp16.onnx
   ```
3. Compute SHA256 and size:
   ```bash
   sha256sum model_fp16.onnx
   ls -lh model_fp16.onnx
   ```
4. Add entry to `app/src/main/assets/models/manifest.json`.
5. Update `models/manifest.json` copy.
6. Run verification:
   ```bash
   python3 tools/verify_manifest.py app/src/main/assets/models/manifest.json
   ```

### Testing engines

Each engine has a fallback CPU path that works without models. Test both:

- Fallback: set engine mode to "fallback" in Settings.
- ONNX: install models via Setup screen.

## Code Style

- Kotlin official style.
- Use `kotlin.code.style=official` (already in gradle.properties).
- No wildcard imports.
- Max line length 120 (soft).

## Pull Requests

- Create feature branch from `main`.
- Ensure `./gradlew :app:testDebugUnitTest` passes.
- Ensure manifest verification passes.
- Add tests for new utils.
- Update docs if needed.

## Reporting Issues

Use GitHub Issues. Include:

- Device model and Android version
- App version
- Steps to reproduce
- Logcat if possible

## Privacy

Never add network calls that send user images. Shadow306 is offline-first. Any PR that adds cloud upload will be rejected.

## License

By contributing, you agree that your contributions will be licensed under MIT.
