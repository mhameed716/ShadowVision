#!/bin/bash
# تحميل النماذج الحقيقية ودمجها في APK
# يُشغل محلياً (خارج الساندبوكس) حيث الإنترنت غير محدود

set -e

MODELS_DIR="models"
ASSETS_DIR="app/src/main/assets/models"
MANIFEST="$ASSETS_DIR/manifest.json"

echo "🌟 Shadow306 - تحميل النماذج ودمجها في APK"
echo "=========================================="
echo ""

# تحقق من وجود Python
if ! command -v python3 &> /dev/null; then
    echo "❌ python3 غير موجود"
    exit 1
fi

# إنشاء المجلدات
mkdir -p "$MODELS_DIR" "$ASSETS_DIR"

echo "📋 النماذج المطلوبة (8 نماذج، ~1.1GB):"
echo "  - tryon_warp_fp16.onnx (120MB) - تغيير الملابس"
echo "  - tryon_synth_fp16.onnx (180MB) - تركيب الملابس"
echo "  - cloth_parse_fp16.onnx (45MB) - تجزئة الملابس"
echo "  - inpaint_body_fp16.onnx (150MB) - تعبئة الفراغات"
echo "  - scrfd_500m_fp16.onnx (20MB) - كشف الوجوه"
echo "  - inswapper_128_fp16.onnx (180MB) - تبديل الوجوه"
echo "  - video_gen_fp16.onnx (350MB) - توليد الفيديو"
echo "  - translate_ar_en_fp16.onnx (60MB) - ترجمة (اختياري)"
echo ""

echo "⚠️  ملاحظة مهمة:"
echo "  - حجم APK مع النماذج سيكون ~1.1GB"
echo "  - غير مناسب لـ Play Store (الحد 150MB)"
echo "  - مناسب للتوزيع المباشر (مشاركة APK مباشرة)"
echo "  - للـ Play Store استخدم النوع العادي (تحميل عند أول فتح)"
echo ""

read -p "هل تريد المتابعة؟ (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "تم الإلغاء"
    exit 0
fi

echo ""
echo "📥 تحميل النماذج الحقيقية..."
echo "يجب عليك تحميل النماذج يدوياً من المصادر التالية:"
echo ""
echo "1. Face Detection & Swap (InsightFace):"
echo "   https://github.com/deepinsight/insightface/releases"
echo "   - buffalo_l.zip يحتوي على scrfd + inswapper"
echo "   - فك الضغط وانسخ scrfd_10g_bnkps.onnx → scrfd_500m_fp16.onnx"
echo "   - و inswapper_128.onnx → inswapper_128_fp16.onnx"
echo ""
echo "2. Cloth Parsing (SCHP / ATR):"
echo "   https://github.com/GoGoDuck912/Self-Correction-Human-Parsing"
echo "   - حمّل SCHP model وحوّله لـ ONNX"
echo "   - أو استخدم: https://huggingface.co/spaces/levihsu/OOTDiffusion"
echo ""
echo "3. Try-On (VITON-HD / HR-VITON):"
echo "   https://github.com/shadow2496/VITON-HD"
echo "   https://github.com/sangyun884/HR-VITON"
echo "   - صدّر النموذج لـ ONNX opset 14+"
echo ""
echo "4. Video Generation (AnimateDiff):"
echo "   https://github.com/guoyww/AnimateDiff"
echo "   https://huggingface.co/guoyww/animatediff-motion-adapter-v1-5-2"
echo ""
echo "5. Translation (Helsinki-NLP):"
echo "   https://huggingface.co/Helsinki-NLP/opus-mt-ar-en"
echo ""

echo "📝 بعد تحميل النماذج، ضعها في: $ASSETS_DIR/"
echo "   ثم شغّل:"
echo "   python3 tools/compute_sha.py $ASSETS_DIR $MANIFEST"
echo ""

# إذا كانت النماذج موجودة، احسب SHA
if ls "$ASSETS_DIR"/*.onnx 1> /dev/null 2>&1; then
    echo "✅ وجدت نماذج في $ASSETS_DIR"
    ls -lh "$ASSETS_DIR"/*.onnx
    echo ""
    echo "🔐 حساب SHA256 وتحديث manifest..."
    python3 tools/compute_sha.py "$ASSETS_DIR" "$MANIFEST"
    echo ""
    echo "✅ تم تحديث manifest.json"
    python3 tools/verify_manifest.py "$MANIFEST"
else
    echo "⚠️  لا توجد نماذج في $ASSETS_DIR"
    echo "   ضع ملفات .onnx هناك ثم شغّل compute_sha.py"
fi

echo ""
echo "🔨 لبناء APK مع النماذج المدمجة:"
echo "   ./gradlew :app:assembleDebug"
echo "   # سيكون الحجم ~1.1GB"
echo ""
echo "📦 للتوزيع بدون إنترنت:"
echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
echo "   # التطبيق سيعمل مباشرة بدون تحميل"
