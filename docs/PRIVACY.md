# Privacy - Shadow306

## Principle

> All images, prompts and results stay on this device.

## What needs INTERNET?

- Only first-run model download (500 MB - 1.2 GB).
- After that, zero sockets.

Users can enable **Air-gapped lock** in Settings to block even the first download.

## What is backed up?

- Only small preferences (language, engine mode, setupDone) via DataStore.
- Models, templates, exports are in `noBackupFilesDir` and `files/` which are excluded from cloud backup and device-to-device transfer (except templates are included in device transfer for convenience).

See `res/xml/backup_rules.xml` and `data_extraction_rules.xml`.

## No analytics

No Firebase, no Crashlytics, no tracking SDKs.

## Permissions

- INTERNET, ACCESS_NETWORK_STATE: for model download only.
- POST_NOTIFICATIONS: for download progress.
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC: for download service.
- READ_EXTERNAL_STORAGE (maxSdk 32): legacy gallery reads. Photo Picker needs no permission on Android 13+.

## FileProvider

Exports are shared via `FileProvider` with `grantUriPermissions=true`, no world-readable files.

## Model integrity

Each model file is verified by SHA-256 before use. Corrupted files are deleted and re-downloaded.
