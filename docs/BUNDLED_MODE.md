# وضع النماذج المدمجة - يعمل بدون إنترنت من أول تشغيل

## المشكلة

الوضع الحالي:
- APK خفيف <50MB
- النماذج 1.1GB تُنزّل عند أول فتح عبر Wi-Fi
- يحتاج إنترنت مرة واحدة

المطلوب:
- APK يعمل 100% بدون إنترنت من أول تشغيل
- بدون أي تحميل بعد التثبيت

## الحل: نكهة Bundled

أنشأنا نكهتين (flavors):

### 1. Standard (الافتراضي - للـ Play Store)
```bash
./gradlew :app:assembleStandardDebug
# الحجم: <50MB
# النماذج: تُنزّل عند أول فتح
# مناسب لـ: Play Store
```

### 2. Bundled (النماذج مدمجة - للتوزيع المباشر)
```bash
./gradlew :app:assembleBundledDebug
# الحجم: ~1.1GB
# النماذج: مدمجة في APK
# يعمل: 100% بدون إنترنت من أول تشغيل
# مناسب لـ: مشاركة APK مباشرة، مناطق بدون إنترنت
```

## كيف تدمج النماذج؟

### الخطوة 1: حمّل النماذج الحقيقية (محلياً - خارج الساندبوكس)

بيئة الساندبوكس محدودة (فقط github.com مسموح)، لذا يجب التحميل محلياً:

```bash
# شغّل السكريبت المساعد
./tools/download_and_bundle_models.sh
```

أو حمّل يدوياً من:

**Face Detection & Swap:**
- https://github.com/deepinsight/insightface/releases
- حمّل `buffalo_l.zip` (500MB)
- فك الضغط:
  - `scrfd_10g_bnkps.onnx` → `app/src/main/assets/models/scrfd_500m_fp16.onnx`
  - `inswapper_128.onnx` → `app/src/main/assets/models/inswapper_128_fp16.onnx`

**Cloth Parsing:**
- https://github.com/GoGoDuck912/Self-Correction-Human-Parsing
- أو https://huggingface.co/spaces/levihsu/OOTDiffusion

**Try-On:**
- https://github.com/shadow2496/VITON-HD
- https://github.com/sangyun884/HR-VITON

**Video:**
- https://github.com/guoyww/AnimateDiff

### الخطوة 2: ضع النماذج في المجلد الصحيح

```bash
app/src/main/assets/models/
├── manifest.json
├── tryon_warp_fp16.onnx
├── tryon_synth_fp16.onnx
├── cloth_parse_fp16.onnx
├── inpaint_body_fp16.onnx
├── scrfd_500m_fp16.onnx
├── inswapper_128_fp16.onnx
├── video_gen_fp16.onnx
└── translate_ar_en_fp16.onnx (اختياري)
```

### الخطوة 3: احسب SHA256 وحدّث manifest

```bash
python3 tools/compute_sha.py app/src/main/assets/models/ app/src/main/assets/models/manifest.json
python3 tools/verify_manifest.py app/src/main/assets/models/manifest.json
```

### الخطوة 4: ابنِ APK المدمج

```bash
./gradlew :app:assembleBundledDebug
# الناتج: app/build/outputs/apk/bundled/debug/app-bundled-debug.apk (~1.1GB)

./gradlew :app:assembleBundledRelease
# الناتج: app/build/outputs/apk/bundled/release/app-bundled-release.apk
```

### الخطوة 5: ثبّت واختبر

```bash
adb install app/build/outputs/apk/bundled/debug/app-bundled-debug.apk
# افتح التطبيق - سيعمل مباشرة بدون أي تحميل!
# في Logcat: "Bundled models installed: 8 copied, 0 skipped..."
```

## كيف يعمل الكود؟

`BundledModelInstaller.kt`:

1. عند أول تشغيل، يفحص `assets/models/` هل فيها `.onnx`
2. إذا وجدها، ينسخها إلى `noBackupFilesDir/models/`
3. يتحقق من SHA256
4. يضع `setupDone = true` → يتخطى شاشة التحميل

`Shadow306App.kt`:
```kotlin
val installer = BundledModelInstaller(this)
if (installer.isBundled()) {
    installer.installIfNeeded() // ينسخ النماذج
    prefs.setSetupDone(true)    // يتخطى التحميل
}
```

## المقارنة

| الميزة | Standard | Bundled |
|--------|----------|---------|
| حجم APK | <50MB | ~1.1GB |
| يحتاج إنترنت؟ | مرة واحدة عند أول فتح | لا، أبداً |
| Play Store؟ | ✅ نعم | ❌ لا (الحد 150MB) |
| توزيع مباشر؟ | يحتاج تحميل بعد التثبيت | ✅ يعمل فوراً |
| مناسب لـ | عامة المستخدمين | مناطق بدون إنترنت، متاجر، توزيع USB |

## التوصية

- **للنشر العام**: استخدم Standard (Play Store)
- **للتوزيع الخاص**: استخدم Bundled (شارك APK مباشرة)

يمكنك بناء الاثنين:
```bash
./gradlew :app:assembleStandardDebug :app:assembleBundledDebug
```

## GitHub Actions

الـ Workflow في `.github/workflows/build.yml` يدعم الاثنين:

- إذا لم توجد نماذج في `assets/models/` → يبني Standard فقط
- إذا وجدت نماذج → يبني Standard + Bundled (عند اختيار bundled/both)

لكن في CI، لا نستطيع تحميل النماذج الحقيقية بسبب قيود الشبكة، لذا Bundled يُبنى فقط محلياً.

## الأسئلة الشائعة

**س: هل يمكن رفع APK 1.1GB لـ GitHub؟**
ج: GitHub يحدد 100MB لكل ملف و 2GB لكل repo. APK 1.1GB كبير جداً. استخدم توزيع مباشر (Google Drive, Telegram) أو Play Asset Delivery.

**س: هل يمكن ضغط النماذج؟**
ج: نعم، ONNX يدعم FP16 (نصف الحجم) و INT8 (ربع الحجم) لكن بجودة أقل. النماذج الحالية FP16 بالفعل.

**س: ماذا عن Play Asset Delivery؟**
ج: يمكن استخدام Play Asset Delivery لتوصيل النماذج كـ asset packs (حتى 4GB)، لكن يحتاج إعداد إضافي. هذا مستقبلي.

**س: لماذا لا نستطيع التحميل في الساندبوكس؟**
ج: الساندبوكس يسمح فقط بـ github.com, codeload.github.com, api.github.com. نماذج InsightFace تُستضاف على objects.githubusercontent.com (محظور) و HuggingFace (محظور). لذا يجب التحميل محلياً.
