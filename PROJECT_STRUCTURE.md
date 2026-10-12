# Shadow306 - هيكل المشروع الكامل

```
Shadow306/
├── .github/
│   ├── workflows/
│   │   ├── build.yml          # ⭐ الأساسي: يبني APK في السحابة (JDK 17 + SDK 35)
│   │   ├── android.yml        # سريع: اختبارات + Lint فقط
│   │   └── README.md          # دليل الـ Workflows
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── pull_request_template.md
│
├── app/
│   ├── build.gradle.kts       # إعدادات البناء (compileSdk 37, min 26, target 37)
│   ├── proguard-rules.pro     # قواعد Proguard لـ ONNX + Compose
│   ├── lint.xml               # إعدادات Lint
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/shadow306/app/
│       │   │   ├── MainActivity.kt              # نقطة الدخول + Splash
│       │   │   ├── Shadow306App.kt              # Application + تهيئة
│       │   │   ├── core/
│       │   │   │   ├── util/
│       │   │   │   │   ├── Format.kt            # تنسيق الأحجام والسرعات
│       │   │   │   │   ├── Checksums.kt         # SHA-256 للنماذج
│       │   │   │   │   ├── FileIo.kt            # عمليات الملفات الآمنة
│       │   │   │   │   └── Retry.kt             # Exponential backoff
│       │   │   │   ├── modeldownload/           # ⭐ نظام التحميل
│       │   │   │   │   ├── ModelSpec.kt         # وصف النموذج + Manifest
│       │   │   │   │   ├── DownloadState.kt     # حالة التحميل
│       │   │   │   │   ├── ModelPaths.kt        # مسارات التخزين
│       │   │   │   │   ├── ModelRepository.kt   # مصدر الحقيقة
│       │   │   │   │   ├── ModelDownloader.kt   # تحميل resumable + mirrors
│       │   │   │   │   ├── ModelDownloadService.kt # Foreground service
│       │   │   │   │   ├── ModelVerifier.kt     # فحص SHA-256
│       │   │   │   │   └── SideloadScanner.kt   # استيراد بدون إنترنت
│       │   │   │   ├── data/
│       │   │   │   │   ├── prefs/AppPreferences.kt # DataStore
│       │   │   │   │   └── db/TemplateStore.kt     # قوالب محلية
│       │   │   │   ├── engine/
│       │   │   │   │   ├── onnx/
│       │   │   │   │   │   ├── OnnxEnv.kt       # تحميل ORT عبر reflection
│       │   │   │   │   │   └── OnnxSession.kt   # جلسات ONNX
│       │   │   │   │   ├── imaging/
│       │   │   │   │   │   ├── BitmapUtils.kt   # معالجة الصور CPU
│       │   │   │   │   │   └── InpaintingUtils.kt
│       │   │   │   │   ├── body/
│       │   │   │   │   │   ├── ClothParser.kt   # تجزئة الملابس
│       │   │   │   │   │   ├── TryOnEngine.kt   # تغيير الملابس
│       │   │   │   │   │   └── RemovalEngine.kt # إزالة الملابس
│       │   │   │   │   ├── face/FaceEngine.kt   # تبديل الوجوه
│       │   │   │   │   ├── video/
│       │   │   │   │   │   ├── VideoEngine.kt   # توليد الفيديو 4s→60s
│       │   │   │   │   │   └── VideoEncoderImpl.kt # MediaCodec
│       │   │   │   │   └── translate/LocalTranslator.kt # عربي→إنجليزي
│       │   │   │   ├── export/
│       │   │   │   │   ├── ExportManager.kt     # حفظ + MediaStore
│       │   │   │   │   └── PermissionHandler.kt
│       │   │   │   ├── work/
│       │   │   │   │   ├── ModelVerificationWorker.kt
│       │   │   │   │   └── WorkScheduler.kt
│       │   │   │   ├── locale/LocaleManager.kt  # عربي/إنجليزي RTL
│       │   │   │   └── usecase/UseCases.kt
│       │   │   ├── di/ServiceLocator.kt         # DI يدوي (بدون Hilt)
│       │   │   └── ui/
│       │   │       ├── theme/                   # OLED dark-first
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Type.kt
│       │   │       │   └── Theme.kt
│       │   │       ├── nav/NavGraph.kt          # 7 routes
│       │   │       ├── common/
│       │   │       │   ├── Components.kt        # Cards, Chips, Banners
│       │   │       │   └── ImagePicker.kt       # Photo Picker
│       │   │       ├── home/
│       │   │       │   ├── HomeScreen.kt        # الرئيسية + 5 أدوات
│       │   │       │   └── HomeViewModel.kt
│       │   │       ├── setup/SetupScreen.kt     # تحميل النماذج + sideload
│       │   │       ├── tryon/
│       │   │       │   ├── TryOnScreen.kt
│       │   │       │   └── TryOnViewModel.kt
│       │   │       ├── faceswap/FaceSwapScreen.kt
│       │   │       ├── video/VideoScreen.kt
│       │   │       ├── templates/TemplatesScreen.kt
│       │   │       ├── removal/RemovalScreen.kt
│       │   │       └── settings/SettingsScreen.kt
│       │   ├── res/
│       │   │   ├── values/
│       │   │   │   ├── strings.xml      # 170 مفتاح EN
│       │   │   │   ├── colors.xml
│       │   │   │   ├── dimens.xml
│       │   │   │   ├── themes.xml
│       │   │   │   └── ic_launcher_background.xml
│       │   │   ├── values-ar/
│       │   │   │   └── strings.xml      # 170 مفتاح AR RTL
│       │   │   ├── values-night/colors.xml
│       │   │   ├── drawable/
│       │   │   │   ├── ic_launcher_foreground.xml
│       │   │   │   ├── ic_launcher_legacy.xml
│       │   │   │   ├── bg_gradient.xml
│       │   │   │   ├── ic_placeholder.xml
│       │   │   │   └── ic_download.xml
│       │   │   ├── mipmap-anydpi-v26/
│       │   │   │   ├── ic_launcher.xml
│       │   │   │   └── ic_launcher_round.xml
│       │   │   └── xml/
│       │   │       ├── backup_rules.xml
│       │   │       ├── data_extraction_rules.xml
│       │   │       ├── file_paths.xml
│       │   │       ├── locales_config.xml
│       │   │       └── network_security_config.xml
│       │   └── assets/
│       │       ├── models/manifest.json # ⭐ 8 نماذج، روابط، SHA256
│       │       └── lexicon/ar_en.json   # قاموس عربي→إنجليزي
│       └── test/
│           └── kotlin/com/shadow306/app/
│               ├── core/util/
│               │   ├── RetryTest.kt
│               │   ├── FormatTest.kt
│               │   ├── ChecksumsTest.kt
│               │   └── FileIoTest.kt
│               └── core/modeldownload/ManifestTest.kt
│
├── gradle/
│   ├── libs.versions.toml       # إصدارات: AGP 9.2, Kotlin 2.4.21, Compose BOM 2026.09
│   └── wrapper/
│       ├── gradle-wrapper.jar   # 48KB - يعمل بدون إنترنت
│       └── gradle-wrapper.properties # Gradle 9.4.1
│
├── models/
│   ├── manifest.json            # نسخة طبق الأصل من assets للتوثيق
│   └── README.md                # دليل النماذج + offline sideload
│
├── tools/
│   ├── verify_manifest.py       # فحص manifest (يُستخدم في CI)
│   ├── compute_sha.py           # حساب SHA256 للنماذج الحقيقية
│   └── README.md
│
├── docs/
│   ├── ARCHITECTURE.md          # البنية + Offline-first
│   ├── MODELS.md                # النماذج + ONNX Mobile
│   ├── PRIVACY.md               # الخصوصية + backup rules
│   └── BUILD.md                 # دليل البناء
│
├── build.gradle.kts             # Root config
├── settings.gradle.kts          # include :app
├── gradle.properties            # JVM + AndroidX + Kotlin
├── gradlew                      # Wrapper script (Unix)
├── gradlew.bat                  # Wrapper script (Windows)
├── .gitignore                   # يستبعد .onnx, build/, .idea/
├── LICENSE                      # MIT
├── README.md                    # دليل EN
├── README_AR.md                 # دليل AR
├── CHANGELOG.md
├── CONTRIBUTING.md
├── SECURITY.md
└── PROJECT_STRUCTURE.md         # هذا الملف
```

## 🔑 النقاط الأساسية

### للبناء في السحابة (GitHub Actions):
- **Workflow**: `.github/workflows/build.yml`
- **البيئة**: JDK 17 + Android SDK 35 (يُثبت تلقائياً)
- **النتيجة**: APK جاهز في Actions → Artifacts
- **الحجم**: <50MB base APK

### للبناء محلياً:
```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### النماذج:
- **المكان**: `app/src/main/assets/models/manifest.json`
- **الحجم**: 1.1GB (8 نماذج)
- **التحقق**: `python3 tools/verify_manifest.py`
- **التحديث**: `python3 tools/compute_sha.py models/ manifest.json`

### الخصوصية:
- 100% offline بعد الإعداد
- الصور لا تخرج من الجهاز
- `network_security_config.xml` يمنع cleartext
- `backup_rules.xml` تستبعد النماذج

## 📦 التسليم

المشروع منظم بالكامل وجاهز للبناء في السحابة عبر GitHub Actions.
