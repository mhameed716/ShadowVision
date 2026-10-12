# GitHub Actions Workflows - Shadow306

## 📦 build.yml (الأساسي - لبناء APK)

**هذا هو الـ Workflow المطلوب لبناء APK في السحابة**

### ماذا يفعل؟

1. **يثبت بيئة أندرويد كاملة:**
   - JDK 17 (Temurin) - مطلوب لـ AGP 9.2 + Kotlin 2.4
   - Android SDK 35 (platforms + build-tools 35.0.0)
   - Android SDK 34, 33 (للتوافق)
   - NDK 28.2.13676358
   - Gradle 9.4.1 عبر Wrapper

2. **يتحقق من المشروع:**
   - يتحقق من `manifest.json` (روابط النماذج + SHA256)
   - يعرض معلومات البيئة

3. **يبني الـ APK:**
   - Debug APK (<50MB، بدون نماذج) - للاختبار
   - Release APK (مُحسّن + Proguard) - للنشر (عند اختيار release/both)
   - يشغل اختبارات الوحدة

4. **يرفع الـ APK:**
   - يحفظ APK كـ Artifact لمدة 30-90 يوم
   - يمكنك تحميله من تبويب Actions

### كيف تستخدمه؟

#### تلقائياً:
- كل push لـ `main` أو `arena/**` يبني Debug APK تلقائياً
- اذهب لـ GitHub → تبويب **Actions** → اختر آخر build → حمّل من **Artifacts**

#### يدوياً:
- GitHub → Actions → **Build Shadow306 APK** → **Run workflow**
- اختر نوع البناء:
  - `debug` - APK للاختبار (سريع)
  - `release` - APK محسن للنشر
  - `both` - الاثنان + AAB لـ Play Store

### أين أجد الـ APK؟

```
GitHub Repo → Actions → آخر workflow → Artifacts → Shadow306-debug-APK-xxx
```

حمّل الـ ZIP، فك الضغط، ثبّت APK على هاتفك:
```bash
adb install Shadow306-debug.apk
```

### حجم الـ APK

- **Base APK**: <50MB (بدون نماذج) - يُرفع للمتجر
- **Models**: 1.1GB تُنزّل مرة واحدة عبر Wi-Fi عند أول فتح
- **بعد الإعداد**: يعمل 100% بدون إنترنت

---

## 🔍 android.yml (سريع - للتحقق)

Workflow خفيف للتحقق من صحة الكود:

- يشغل اختبارات الوحدة فقط
- يشغل Lint
- لا يبني APK كامل (أسرع)

يُستخدم للـ PRs والـ pushes الصغيرة.

---

## 🛠️ المتطلبات

### في السحابة (GitHub Actions):
- `ubuntu-latest`
- JDK 17
- Android SDK 35 (يُثبت تلقائياً)
- لا يحتاج إعداد يدوي

### محلياً (على جهازك):
```bash
# المتطلبات
- JDK 17
- Android SDK 35+
- Gradle 9.4.1 (عبر wrapper)

# البناء
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 استكشاف الأخطاء

### فشل البناء بسبب SDK 37؟
- الـ Workflow يحاول تثبيت SDK 37 تلقائياً
- إذا لم يكن متاحاً، يستخدم SDK 35 كـ fallback
- يمكنك تغيير `compileSdk` في `app/build.gradle.kts` إلى 35

### فشل بسبب نقص المساحة؟
- الـ Workflow يستخدم cache لتسريع البناء
- إذا فشل، جرّب `workflow_dispatch` مع `both`

### أين الـ APK؟
- تأكد أن البناء نجح (علامة خضراء ✅)
- اذهب لـ Artifacts في أسفل صفحة الـ workflow run
- إذا لم تجد Artifacts، تحقق من logs

---

## 📚 للمزيد

- `docs/BUILD.md` - دليل البناء الكامل
- `tools/verify_manifest.py` - التحقق من manifest
- `tools/compute_sha.py` - حساب SHA للنماذج الحقيقية
