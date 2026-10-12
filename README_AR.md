# Shadow306 — استوديو ذكاء اصطناعي على الجهاز (100% بدون إنترنت)

> **تطبيق أندرويد متطور يعمل بنسبة 100% بدون إنترنت بعد التثبيت الأولي.**
> كل معالجة الصور تتم على عتاد الهاتف عبر ONNX Runtime Mobile.

## 📥 نظام التحميل الأولي للنماذج

- **الحجم الأساسي الخفيف**: APK < 50 MB بدون نماذج.
- **التنزيل لمرة واحدة**: عند أول فتح، شاشة إعداد تطلب Wi-Fi لتنزيل حزمة النماذج (500 MB – 1.2 GB).
- **الحفظ المحلي**: تُخزَّن في `noBackupFilesDir/models/` (لا تُرفع لـ Drive).
- **المانيفست**: `app/src/main/assets/models/manifest.json` يحتوي روابط التحميل، الأحجام، `sha256`.

### مثال المانيفست

```json
{
  "schemaVersion": 1,
  "packVersion": 1,
  "totalBytes": 1105000000,
  "models": [
    {
      "id": "tryon_warp",
      "fileName": "tryon_warp_fp16.onnx",
      "sizeBytes": 120000000,
      "sha256": "aaaa...",
      "url": "https://models.shadow306.app/tryon_warp_fp16.onnx",
      "features": ["TRY_ON"]
    }
  ]
}
```

## 📱 الواجهات الخمس

### 💠 تغيير الملابس (Virtual Try-On)
- يختار المستخدم صورة جسم كامل + قالب ملابس.
- ONNX: `tryon_warp_fp16.onnx` + `tryon_synth_fp16.onnx`.
- وضع المعاينة: دمج شفاف فوق منطقة الجذع.

### 👥 تبديل الأوجه (Face Swap)
- InsightFace مضغوط: `scrfd_500m_fp16.onnx` + `inswapper_128_fp16.onnx`.

### 🎬 توليد الفيديو
- يكتب البرومبت عربي/إنجليزي → مترجم محلي عبر lexicon.
- النموذج يولّد 3-5 ثوانٍ، ثم التدوير الذكي يمدّها لـ 60 ثانية.

### 📂 إدارة القوالب
- إضافة قوالب من المعرض → نسخ إلى `files/templates/<uuid>/`.

### ✂️ إزالة الملابس
- `cloth_parse_fp16.onnx` لتجزئة الملابس.
- `inpaint_body_fp16.onnx` لتعبئة الفراغات.

## 🏗️ البنية

```
app/src/main/kotlin/com/shadow306/app/
  core/
    util/          Format, Checksums, FileIo, Retry
    modeldownload/ Manifest, Repository, Downloader, Service, Verifier
    data/          AppPreferences, TemplateStore
    engine/        OnnxEnv, TryOn, FaceSwap, Video, Removal, Translator
    export/        ExportManager, PermissionHandler
    work/          ModelVerificationWorker
  ui/
    theme/         OLED dark-first
    home/          HomeScreen
    setup/         SetupScreen
    tryon/         TryOnScreen
    faceswap/      FaceSwapScreen
    video/         VideoScreen
    templates/     TemplatesScreen
    removal/       RemovalScreen
    settings/      SettingsScreen
```

## 🌐 ثنائي اللغة

- `res/values/strings.xml` (170 مفتاح) — إنجليزي
- `res/values-ar/strings.xml` — عربي RTL كامل
- `android:supportsRtl="true"`

## 🔒 الخصوصية

- `INTERNET` مطلوب فقط لتنزيل النماذج أول مرة.
- بعد ذلك، صفر اتصالات.
- قفل العزل الشبكي في الإعدادات.

## 🛠️ البناء

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📦 توزيع بدون إنترنت

```bash
adb push models/ /sdcard/Download/Shadow306/models/
```

**مبني للخصوصية. مبني للعمل بدون إنترنت. Shadow306 — كل شيء يعمل داخل هاتفك.**
