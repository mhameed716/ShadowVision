# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability, please email the maintainers privately instead of opening a public issue.

### What to include:

- Description of vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix if any

### Response time:

- Initial response: within 48 hours
- Fix timeline: depends on severity, critical within 7 days

## Security Considerations

### Offline-First

Shadow306 is designed to work 100% offline after setup. This is a security feature:

- No user images leave the device
- No cloud processing
- Models verified by SHA-256

### Model Integrity

- Each model file is verified by SHA-256 before use
- Corrupted files are deleted automatically
- Manifest is bundled in APK and verified copy in noBackupFilesDir

### Permissions

- INTERNET only for first-run model download
- Users can enable Air-gapped lock to block all network
- No READ_EXTERNAL_STORAGE on Android 13+ (Photo Picker)
- FileProvider for secure sharing

### Backup

- Models and user media excluded from cloud backup
- Only small preferences backed up

### Proguard

- ONNX Runtime classes kept
- Native methods kept
- Source file names obfuscated but line numbers kept for crash reports
