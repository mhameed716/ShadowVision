#!/usr/bin/env python3
"""
Computes SHA256 and size for all .onnx files in a directory and updates manifest.json

Usage:
  python3 tools/compute_sha.py models/ app/src/main/assets/models/manifest.json

It will:
- Scan directory for .onnx files
- Compute sha256 and size
- Update manifest.json in place
- Print summary
"""
import hashlib, pathlib, json, sys

def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <models_dir> <manifest.json>")
        sys.exit(1)

    models_dir = pathlib.Path(sys.argv[1])
    manifest_path = pathlib.Path(sys.argv[2])

    if not models_dir.exists():
        print(f"Models dir not found: {models_dir}")
        sys.exit(1)

    if not manifest_path.exists():
        print(f"Manifest not found: {manifest_path}")
        sys.exit(1)

    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))

    onnx_files = {p.name: p for p in models_dir.glob("*.onnx")}

    total = 0
    for model in manifest["models"]:
        fname = model["fileName"]
        if fname in onnx_files:
            fpath = onnx_files[fname]
            sha = sha256_file(fpath)
            size = fpath.stat().st_size
            print(f"{fname}: {size} bytes, sha256={sha[:16]}...")
            model["sha256"] = sha
            model["sizeBytes"] = size
            total += size
        else:
            print(f"WARN: {fname} not found in {models_dir}, keeping placeholder")

    manifest["totalBytes"] = total

    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f"\nUpdated {manifest_path} - total {total/1024/1024:.1f} MB")

    # Also update root copy if exists
    root_manifest = pathlib.Path("models/manifest.json")
    if root_manifest.exists() and root_manifest.resolve() != manifest_path.resolve():
        root_manifest.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
        print(f"Also updated {root_manifest}")

if __name__ == "__main__":
    main()
