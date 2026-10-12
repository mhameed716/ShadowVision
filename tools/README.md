# Tools

## verify_manifest.py

Validates `manifest.json`:

```bash
python3 tools/verify_manifest.py app/src/main/assets/models/manifest.json
```

Checks:
- schemaVersion, packVersion, totalBytes present
- each model has id, fileName, sizeBytes, sha256 (64 hex), url, features
- totalBytes == sum(sizeBytes) (warn if not)
- sha256 format valid
- no duplicate ids or fileNames

## compute_sha.py

Computes real SHA256 and size for .onnx files and updates manifest:

```bash
# Place your real .onnx files in models/
python3 tools/compute_sha.py models/ app/src/main/assets/models/manifest.json
```

## Future tools (planned)

- `convert_to_ort.py` - batch convert ONNX to ORT format
- `quantize_fp16.py` - FP16 quantization
- `benchmark.py` - benchmark inference on device
