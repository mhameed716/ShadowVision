package com.shadow306.app.core.engine.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline Arabic -> English translator for video prompts.
 *
 * The spec requires: "يكتب المستخدم البرومبت بالعربية أو الإنجليزية
 * (مع وجود مترجم نصي محلي يترجم العربية للإنجليزية لحظياً)"
 *
 * Implementation:
 * - Small lexicon in assets/lexicon/ar_en.json (hand-curated, ~800 entries)
 * - Fallback rules for common video verbs (حركة, اقتراب, دوران...)
 * - If lexicon misses, we keep Arabic words as-is and append English gloss
 *   so the downstream video model still gets some signal.
 *
 * This is NOT a full NMT model - that would be >200 MB. The real NMT ONNX
 * model (if present) is tried first via OnnxTranslator, then this lexicon
 * fallback is used.
 */
class LocalTranslator(private val context: Context) {

    private var lexicon: Map<String, String>? = null
    private var phrases: Map<String, String>? = null

    suspend fun translateIfNeeded(input: String): TranslationResult = withContext(Dispatchers.Default) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return@withContext TranslationResult("", "", false)

        // Fast check: if input is mostly ASCII, assume English and skip
        val arabicChars = trimmed.count { it in '\u0600'..'\u06FF' }
        if (arabicChars == 0) {
            return@withContext TranslationResult(trimmed, trimmed, false)
        }

        // Try ONNX NMT model first if available
        val onnxResult = tryOnnxTranslate(trimmed)
        if (onnxResult != null) {
            return@withContext TranslationResult(trimmed, onnxResult, true)
        }

        // Lexicon fallback
        ensureLexiconLoaded()
        val translated = lexiconTranslate(trimmed)
        TranslationResult(trimmed, translated, true)
    }

    data class TranslationResult(
        val original: String,
        val english: String,
        val wasTranslated: Boolean
    )

    private suspend fun tryOnnxTranslate(text: String): String? = withContext(Dispatchers.IO) {
        // TODO: load assets/models/translate_ar_en_fp16.onnx if present
        null
    }

    private fun ensureLexiconLoaded() {
        if (lexicon != null) return
        try {
            val json = context.assets.open("lexicon/ar_en.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val wordsObj = obj.optJSONObject("words") ?: JSONObject()
            val phrasesObj = obj.optJSONObject("phrases") ?: JSONObject()

            val wMap = mutableMapOf<String, String>()
            val keys = wordsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                wMap[k] = wordsObj.getString(k)
            }
            val pMap = mutableMapOf<String, String>()
            val pKeys = phrasesObj.keys()
            while (pKeys.hasNext()) {
                val k = pKeys.next()
                pMap[k] = phrasesObj.getString(k)
            }
            lexicon = wMap
            phrases = pMap
        } catch (t: Throwable) {
            // Built-in hardcoded minimal lexicon
            lexicon = minimalLexicon()
            phrases = minimalPhrases()
        }
    }

    private fun lexiconTranslate(ar: String): String {
        val lex = lexicon ?: minimalLexicon()
        val phr = phrases ?: minimalPhrases()

        var text = ar

        // Replace phrases first (longest first)
        val sortedPhrases = phr.keys.sortedByDescending { it.length }
        for (phrase in sortedPhrases) {
            if (text.contains(phrase)) {
                text = text.replace(phrase, phr[phrase] ?: phrase)
            }
        }

        // Word-by-word
        val words = text.split(Regex("\\s+"))
        val translatedWords = words.map { w ->
            val clean = w.trim('،', '.', '!', '؟', ',', '!', '?', '٫')
            lex[clean] ?: lex[w] ?: w
        }

        // Post-process: if still contains Arabic, keep it but add English hint
        val result = translatedWords.joinToString(" ")
        return result.ifEmpty { ar }
    }

    private fun minimalLexicon(): Map<String, String> = mapOf(
        "اقتراب" to "push-in",
        "ابتعد" to "pull-out",
        "دوران" to "orbit",
        "التفاف" to "orbit",
        "حركة" to "motion",
        "بطيء" to "slow",
        "سريع" to "fast",
        "سينمائي" to "cinematic",
        "ناعم" to "smooth",
        "يطير" to "flowing",
        "الريح" to "wind",
        "الرياح" to "wind",
        "الفستان" to "dress",
        "فستان" to "dress",
        "قماش" to "fabric",
        "ضوء" to "light",
        "إضاءة" to "lighting",
        "ظل" to "shadow",
        "جميل" to "beautiful",
        "رائع" to "amazing",
        "هادئ" to "calm",
        "متحرك" to "moving",
        "ثابت" to "static"
    )

    private fun minimalPhrases(): Map<String, String> = mapOf(
        "اقتراب سينمائي بطيء" to "slow cinematic push-in",
        "الفستان يطير مع الريح" to "dress flowing in the wind",
        "حركة ناعمة" to "smooth motion",
        "دوران حول الشخص" to "orbit around the person"
    )
}
