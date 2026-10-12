# Changelog

All notable changes to Shadow306 will be documented in this file.

## [1.0.0] - 2026-10-12

### Added
- Initial release of Shadow306
- 5 main tools: Try-On, Face Swap, Video Generation, Templates, Garment Removal
- Offline-first architecture with ONNX Runtime Mobile
- Model download manager with:
  - Resumable downloads (Range requests)
  - Mirror fallback
  - SHA-256 verification
  - Foreground service with live notification
  - Sideload scanner for offline import
  - Air-gapped lock
- Bilingual support (Arabic RTL + English) - 170 strings
- File-based template store (no Room/KSP)
- DataStore preferences
- Export manager with MediaStore integration
- Video encoder with MediaCodec + fallback
- WorkManager for periodic verification
- Comprehensive docs: ARCHITECTURE, MODELS, PRIVACY, BUILD
- CI workflow, manifest verification tools
- Unit tests for core utils

### Technical
- AGP 9.2.0, Kotlin 2.4.21, Compose BOM 2026.09.00
- ONNX Runtime Android 1.31.0
- Min SDK 26, Target SDK 37
- Dark-first OLED theme
- Proguard optimized

### Privacy
- 100% offline after setup
- No tracking SDKs
- Models excluded from backup
- Network security config bans cleartext

## [Unreleased]

### Planned
- Real ONNX models (currently placeholders)
- RIFE frame interpolation
- Hilt migration (optional)
- UI tests
- Benchmark tools
