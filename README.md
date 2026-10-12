# Shadow306 — On-Device AI Studio (100% Offline)

> **تطبيق أندرويد متطور يعمل بنسبة 100% بدون إنترنت بعد التثبيت الأولي.**
> كل معالجة الصور تتم على عتاد الهاتف عبر ONNX Runtime Mobile.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.09.00-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![AGP](https://img.shields.io/badge/AGP-9.2.0-3DDC84?logo=android)](https://developer.android.com/build)

---

## 📥 1. نظام التحميل الأولي للنماذج (Setup & Local Storage)

- الحجم الأساسي الخفيف: APK < 50 MB (بدون نماذج).
- التنزيل لمرة واحدة: عند أول فتح، شاشة إعداد تطلب Wi-Fi لتنزيل حزمة النماذج (500 MB – 1.2 GB).
- الحفظ المحلي: تُخزَّن في `noBackupFilesDir/models/` (لا تُرفع لـ Drive).
- المانيفست: `app/src/main/assets/models/manifest.json` يحتوي روابط التحميل، الأحجام، `sha256`، والـ mirrors.
- مدير التحميل: `ModelDownloader` يدعم استئناف التنزيل (Range)، mirrors، backoff، وفحص SHA-256.
- الاستيراد بدون إنترنت: ضع المجلد في `/sdcard/Download/Shadow306/models/` وسيكتشفه `SideloadScanner`.

### Manifest example

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

> **مهم:** استبدل الـ URLs الوهمية بروابط CDN حقيقية، واحسب الـ sha256 الحقيقي قبل الإنتاج.

---

## 📱 2. الواجهات الخمس

### 💠 تغيير الملابس (Virtual Try-On) — `ui/tryon`
- يختار المستخدم صورة جسم كامل + قالب ملابس.
- ONNX: `tryon_warp_fp16.onnx` (TPS/flow) + `tryon_synth_fp16.onnx`.
- Fallback CPU: alpha blend فوق منطقة الجذع مع مراعاة `fit tightness`.

### 👥 تبديل الأوجه (Face Swap) — `ui/faceswap`
- InsightFace مضغوط: `scrfd_500m_fp16.onnx` + `inswapper_128_fp16.onnx`.
- Fallback: نسخ وجه مركزي + feather.

### 🎬 توليد الفيديو — `ui/video`
- يكتب البرومبت عربي/إنجليزي → `LocalTranslator` يترجم محلياً عبر lexicon + ONNX اختياري.
- النموذج يولّد 3-5 ثوانٍ أساسية، ثم `FrameInterpolator` + `VideoLooper` يمدّها إلى 60 ثانية.
- الترميز عبر `MediaCodec` بدون شبكة.

### 📂 إدارة القوالب — `ui/templates` + `core/data/db/TemplateStore`
- إضافة قوالب من المعرض → نسخ إلى `files/templates/<uuid>/`.
- قاعدة بيانات محلية بدون Room/KSP (ملفات + `index.json`) لتجنب ثقل البناء.

### ✂️ إزالة الملابس — `ui/removal`
- `cloth_parse_fp16.onnx` لتجزئة الملابس بكسل بكسل.
- `inpaint_body_fp16.onnx` لتعبئة الفراغات بلون بشرة متناسق.
- Fallback: heuristic + `InpaintingUtils`.

---

## 🏗️ البنية

```
app/src/main/kotlin/com/shadow306/app/
  Shadow306App.kt
  MainActivity.kt
  core/
    util/          Format, Checksums, FileIo, Retry
    modeldownload/ Manifest, DownloadState, ModelRepository, ModelDownloader, ModelDownloadService, Verifier, SideloadScanner
    data/
      prefs/       AppPreferences (DataStore)
      db/          TemplateStore (file-based)
    engine/
      onnx/        OnnxEnv, OnnxSession (reflection, no hard dep)
      imaging/     BitmapUtils, InpaintingUtils
      body/        ClothParser, TryOnEngine, RemovalEngine
      face/        FaceEngine
      video/       VideoEngine, FrameInterpolator, Looper, Encoder
      translate/   LocalTranslator (offline lexicon)
    locale/        LocaleManager (ar/en/system, RTL)
    usecase/       UseCases
  di/              ServiceLocator (manual DI, no Hilt/KSP)
  ui/
    theme/         dark-first OLED palette
    nav/           NavGraph (Compose Navigation)
    common/        Components, ImagePicker
    home/          HomeScreen (5 tools + pack status)
    setup/         SetupScreen (download + sideload)
    tryon/         TryOnScreen
    faceswap/      FaceSwapScreen
    video/         VideoScreen
    templates/     TemplatesScreen
    removal/       RemovalScreen
    settings/      SettingsScreen
```

**لماذا بدون Hilt/KSP/Room؟**
- يقلل زمن البناء في CI.
- يعمل في بيئات بدون Android SDK (هذا الساندبوكس).
- سهل الترحيل إلى Hilt لاحقاً — `ServiceLocator` يحمل نفس الواجهات.

---

## 🌐 ثنائي اللغة (Bilingual)

- `res/values/strings.xml` (170 مفتاح) — إنجليزي.
- `res/values-ar/strings.xml` — عربي RTL كامل، نفس المفاتيح.
- `android:supportsRtl="true"` + `localeConfig`.
- `LocaleManager` يطبق اللغة عبر `AppCompatDelegate.setApplicationLocales()`.

---

## 🔒 الخصوصية (Offline-First)

- `INTERNET` مطلوب فقط لتنزيل النماذج أول مرة.
- `network_security_config.xml` يمنع cleartext ويحدّد دومينات النماذج فقط.
- `backup_rules.xml` + `data_extraction_rules.xml` تمنع نسخ النماذج والوسائط.
- `FileProvider` للمشاركة الآمنة.
- إعداد `Air-gapped lock` يحظر أي socket حتى أثناء الإعداد.

---

## 🛠️ البناء

### المتطلبات
- JDK 17
- Android SDK 37 (compileSdk 37, target 37, min 26)
- Gradle 9.4.1 (عبر wrapper)

### خطوات

```bash
# 1. انسخ manifest الحقيقي (اختياري)
cp models/manifest.json app/src/main/assets/models/manifest.json

# 2. بناء Debug
./gradlew :app:assembleDebug

# 3. تثبيت
adb install app/build/outputs/apk/debug/app-debug.apk
```

> إذا كان `gradle/wrapper/gradle-wrapper.jar` غير موجود (بسبب حظر الشبكة في هذه البيئة)، ثبّت Gradle 9.4.1 محلياً وشغّل:
> ```
> gradle wrapper --gradle-version 9.4.1
> ```

---

## 🧪 الاختبارات

- `core/util/Retry` و `Checksums` قابلة للاختبار على JVM بدون Android.
- شغّل:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

---

## 📦 توزيع النماذج بدون إنترنت

للمحلات / المناطق بدون إنترنت:

```bash
# على الكمبيوتر
adb push models/ /sdcard/Download/Shadow306/models/

# على الجهاز: افتح Shadow306 → سيظهر "وُجدت نماذج على هذا الجهاز"
```

---

## 🔮 خارطة الطريق

- [ ] تصدير النماذج الحقيقية بـ FP16 + ORT Mobile conversion.
- [ ] MediaCodec encoder حقيقي (حالياً placeholder).
- [ ] RIFE frame interpolation ONNX.
- [ ] Hilt + Room migration (اختياري).
- [ ] اختبارات UI (Compose Test).

---

## 📄 الترخيص

MIT — انظر `LICENSE` (أضف ملفك الخاص).

---

**Built for privacy. Built for offline. Shadow306 — كل شيء يعمل داخل هاتفك.**

---

## 🚀 بناء APK في السحابة (GitHub Actions)

بما أن بيئة الساندبوكس لا تحتوي على Android SDK، تم إعداد Workflow متكامل يبني الـ APK تلقائياً في السحابة:

### 📥 كيف تحمّل الـ APK الجاهز؟

1. **اذهب لتبويب Actions في GitHub:**
   ```
   https://github.com/mhameed716/ShadowVision/actions
   ```

2. **اختر آخر Build:**
   - اضغط على **Build Shadow306 APK** (آخر run أخضر ✅)

3. **حمّل من Artifacts:**
   - في أسفل الصفحة → **Artifacts** → `Shadow306-debug-APK-xxx`
   - حمّل الـ ZIP، فك الضغط، ستحصل على `app-debug.apk`

4. **ثبّت على هاتفك:**
   ```bash
   adb install app-debug.apk
   # أو انقل الملف للهاتف وثبّته يدوياً
   ```

### 🔧 كيف يعمل الـ Workflow؟

**الملف:** `.github/workflows/build.yml`

```yaml
- JDK 17 (Temurin) - مطلوب لـ AGP 9.2 + Kotlin 2.4
- Android SDK 35 (platforms + build-tools 35.0.0)
- Android SDK 34, 33 (للتوافق)
- NDK 28.2.13676358
- Gradle 9.4.1 عبر Wrapper (48KB jar موجود في المشروع)
```

**الخطوات:**
1. يثبت بيئة أندرويد كاملة (JDK 17 + SDK 35)
2. يتحقق من `manifest.json` (روابط النماذج + SHA256)
3. يبني Debug APK (<50MB)
4. يشغل اختبارات الوحدة
5. يرفع APK كـ Artifact (30 يوم)

### 🎮 البناء اليدوي

يمكنك تشغيل البناء يدوياً:

- GitHub → Actions → **Build Shadow306 APK** → **Run workflow**
- اختر:
  - `debug` - APK للاختبار (سريع، <50MB)
  - `release` - APK محسن للنشر
  - `both` - الاثنان + AAB لـ Play Store

### 📊 حجم الـ APK

| النوع | الحجم | الوصف |
|-------|-------|-------|
| Base APK | <50MB | بدون نماذج، يُرفع للمتجر |
| Models | 1.1GB | تُنزّل مرة واحدة عبر Wi-Fi |
| بعد الإعداد | 100% offline | يعمل بدون إنترنت |

### 🔍 Workflows المتاحة

| Workflow | الملف | الوصف | متى يعمل |
|----------|-------|-------|----------|
| **Build APK** | `build.yml` | ⭐ الأساسي - يبني APK كامل | كل push لـ main/arena |
| Quick CI | `android.yml` | سريع - اختبارات + Lint فقط | PRs |

انظر `.github/workflows/README.md` للتفاصيل الكاملة.

---

## 📦 وضعان للتوزيع: Standard vs Bundled

### Standard (الافتراضي - للـ Play Store)
- **الحجم**: <50MB APK
- **النماذج**: تُنزّل مرة واحدة عند أول فتح عبر Wi-Fi (1.1GB)
- **البناء**: `./gradlew :app:assembleStandardDebug`
- **مناسب لـ**: Play Store، عامة المستخدمين

### Bundled (النماذج مدمجة - بدون إنترنت أبداً)
- **الحجم**: ~1.1GB APK
- **النماذج**: مدمجة داخل APK، يعمل 100% بدون إنترنت من أول تشغيل
- **البناء**: `./gradlew :app:assembleBundledDebug` (يتطلب وضع .onnx في assets)
- **مناسب لـ**: توزيع مباشر، مناطق بدون إنترنت، متاجر

**كيف تدمج النماذج؟**

1. حمّل النماذج الحقيقية محلياً (خارج الساندبوكس):
   ```bash
   ./tools/download_and_bundle_models.sh
   # أو حمّل يدوياً من insightface, VITON-HD, etc.
   ```

2. ضعها في `app/src/main/assets/models/` ثم:
   ```bash
   python3 tools/compute_sha.py app/src/main/assets/models/ app/src/main/assets/models/manifest.json
   ./gradlew :app:assembleBundledDebug
   ```

انظر `docs/BUNDLED_MODE.md` للتفاصيل الكاملة.

**لماذا لا نستطيع التحميل في الساندبوكس؟**

بيئة الساندبوكس تسمح فقط بـ `github.com`, `codeload.github.com`, `api.github.com`.
نماذج InsightFace تُستضاف على `objects.githubusercontent.com` (محظور) و HuggingFace (محظور).
لذا يجب التحميل محلياً حيث الإنترنت غير محدود.

الكود جاهز لدعم الوضعين - `BundledModelInstaller.kt` ينسخ النماذج من assets تلقائياً عند أول تشغيل.
