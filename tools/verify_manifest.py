#!/usr/bin/env python3
"""
Verifies Shadow306 model manifest.json:
- schemaVersion, packVersion, totalBytes present
- each model has id, fileName, sizeBytes, sha256 (64 hex chars), url, features
- totalBytes == sum(sizeBytes) (warn if not)
- sha256 format valid (64 hex)
- no duplicate ids or fileNames
"""
import json, sys, re, pathlib

SHA_RE = re.compile(r'^[0-9a-fA-F]{64}$')

def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)

def warn(msg):
    print(f"WARN: {msg}", file=sys.stderr)

def main():
    path = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/models/manifest.json")
    if not path.exists():
        fail(f"Manifest not found: {path}")

    data = json.loads(path.read_text(encoding='utf-8'))

    for key in ("schemaVersion", "packVersion", "totalBytes", "models"):
        if key not in data:
            fail(f"Missing key: {key}")

    models = data["models"]
    if not isinstance(models, list) or len(models) == 0:
        fail("models must be non-empty list")

    seen_ids = set()
    seen_files = set()
    total = 0

    for m in models:
        for k in ("id", "fileName", "sizeBytes", "sha256", "url", "features"):
            if k not in m:
                fail(f"Model {m.get('id','?')} missing key: {k}")

        mid = m["id"]
        if mid in seen_ids:
            fail(f"Duplicate id: {mid}")
        seen_ids.add(mid)

        fname = m["fileName"]
        if fname in seen_files:
            fail(f"Duplicate fileName: {fname}")
        seen_files.add(fname)

        if not SHA_RE.match(m["sha256"]):
            # Allow placeholder aaaa... but warn
            if m["sha256"] in ("a"*64, "b"*64, "c"*64, "d"*64, "e"*64, "f"*64, "1"*64, "2"*64):
                warn(f"Model {mid} uses placeholder sha256")
            else:
                fail(f"Model {mid} has invalid sha256: {m['sha256']}")

        if not m["fileName"].endswith(".onnx"):
            fail(f"Model {mid} fileName must end with .onnx")

        if not isinstance(m["features"], list) or len(m["features"]) == 0:
            fail(f"Model {mid} features must be non-empty list")

        total += int(m["sizeBytes"])

    declared_total = int(data["totalBytes"])
    if declared_total != total:
        warn(f"totalBytes {declared_total} != sum sizes {total}")

    print(f"OK: {len(models)} models, {total/1024/1024:.1f} MB total")
    # Also check root models/manifest.json matches
    root_manifest = pathlib.Path("models/manifest.json")
    if root_manifest.exists() and root_manifest.resolve().absolute() != path.resolve().absolute():
        root_data = json.loads(root_manifest.read_text(encoding='utf-8'))
        if root_data != data:
            warn("models/manifest.json differs from app/src/main/assets/models/manifest.json")

if __name__ == "__main__":
    main()
