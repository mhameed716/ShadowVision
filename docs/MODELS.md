# Shadow306 Model Pack

## Current placeholder manifest

`app/src/main/assets/models/manifest.json` contains 8 models (~1.1 GB):

| id | file | size | feature |
|---|---|---|---|
| tryon_warp | tryon_warp_fp16.onnx | 120 MB | TRY_ON |
| tryon_synth | tryon_synth_fp16.onnx | 180 MB | TRY_ON |
| cloth_parse | cloth_parse_fp16.onnx | 45 MB | CLOTH_PARSE |
| inpaint_body | inpaint_body_fp16.onnx | 150 MB | INPAINT |
| face_det | scrfd_500m_fp16.onnx | 20 MB | FACE_SWAP |
| face_swap | inswapper_128_fp16.onnx | 180 MB | FACE_SWAP |
| video_gen | video_gen_fp16.onnx | 350 MB | VIDEO_GEN |
| translate_ar_en | translate_ar_en_fp16.onnx | 60 MB | TRANSLATE (optional) |

All sha256 are placeholders (`aaaa...`). Replace with real hashes.

## How to produce real models

### Try-On

- Use VITON-HD or HR-VITON, export to ONNX, quantize to FP16.
- Warp model: TPS or flow.
- Synthesis: inpaint person + warped garment.

### Cloth Parsing

- ATR / LIP dataset, DeepLabv3+ or SCHP, export to ONNX.
- Classes: background, hat, hair, glove, sunglasses, upper-clothes, dress, coat, socks, pants, jumpsuits, scarf, skirt, face, left-arm, right-arm, left-leg, right-leg, left-shoe, right-shoe.

### Face Swap

- SCRFD for detection (500M variant is mobile-friendly).
- Inswapper 128 (InsightFace) for swapping.

### Video

- AnimateDiff or Stable Video Diffusion, distilled to 4 sec.
- Export to ONNX with dynamic batch.

### Translation

- Helsinki-NLP/opus-mt-ar-en distilled + quantized.

## ONNX Runtime Mobile

Convert with:

```bash
python -m onnxruntime.tools.convert_onnx_models_to_ort --optimization_style Fixed
```

Test compatibility:

```bash
python -m onnxruntime.tools.check_onnx_model_mobile_usability --model model.onnx
```

## CDN

Host at `https://models.shadow306.app/` with:

- Range support (for resume)
- CORS not needed (native)
- TLS only (network_security_config enforces)

Mirrors: `https://cdn.shadow306.app/models/`

## Update flow (future)

- App checks `https://models.shadow306.app/manifest.json` for `packVersion` bump.
- If newer, show "Update available" in Settings.
- Download only changed models (by sha256 diff).
