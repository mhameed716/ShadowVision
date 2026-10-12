package com.shadow306.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "shadow306_prefs")

/**
 * All user-editable settings live here. DataStore is used instead of
 * SharedPreferences because it is coroutine-friendly and crash-safe.
 *
 * Keys are intentionally stable - renaming them would wipe user settings.
 */
class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language") // "ar" | "en" | "system"
        private val KEY_AIRGAPPED = booleanPreferencesKey("airgapped")
        private val KEY_ALLOW_METERED = booleanPreferencesKey("allow_metered")
        private val KEY_ENGINE_MODE = stringPreferencesKey("engine_mode") // auto | onnx | fallback
        private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
        private val KEY_LAST_PACK_VERSION = intPreferencesKey("last_pack_version")
        private val KEY_TRYON_FIT = intPreferencesKey("tryon_fit") // 0..100
        private val KEY_TRYON_SHADOW = booleanPreferencesKey("tryon_shadow")
        private val KEY_FACESWAP_COLOR = booleanPreferencesKey("faceswap_color_match")
        private val KEY_REMOVAL_SKIN_MATCH = booleanPreferencesKey("removal_skin_match")
        private val KEY_VIDEO_DURATION = intPreferencesKey("video_duration_sec")
        private val KEY_VIDEO_FPS = intPreferencesKey("video_fps")
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "system" }
    val airgappedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AIRGAPPED] ?: false }
    val allowMeteredFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_METERED] ?: false }
    val engineModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_ENGINE_MODE] ?: "auto" }
    val setupDoneFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }
    val lastPackVersionFlow: Flow<Int> = context.dataStore.data.map { it[KEY_LAST_PACK_VERSION] ?: 0 }

    val tryOnFitFlow: Flow<Int> = context.dataStore.data.map { it[KEY_TRYON_FIT] ?: 50 }
    val tryOnShadowFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_TRYON_SHADOW] ?: true }
    val faceSwapColorFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FACESWAP_COLOR] ?: true }
    val removalSkinMatchFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_REMOVAL_SKIN_MATCH] ?: true }
    val videoDurationFlow: Flow<Int> = context.dataStore.data.map { it[KEY_VIDEO_DURATION] ?: 60 }
    val videoFpsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_VIDEO_FPS] ?: 24 }

    suspend fun setLanguage(code: String) = context.dataStore.edit { it[KEY_LANGUAGE] = code }
    suspend fun setAirgapped(v: Boolean) = context.dataStore.edit { it[KEY_AIRGAPPED] = v }
    suspend fun setAllowMetered(v: Boolean) = context.dataStore.edit { it[KEY_ALLOW_METERED] = v }
    suspend fun setEngineMode(mode: String) = context.dataStore.edit { it[KEY_ENGINE_MODE] = mode }
    suspend fun setSetupDone(done: Boolean) = context.dataStore.edit { it[KEY_SETUP_DONE] = done }
    suspend fun setLastPackVersion(v: Int) = context.dataStore.edit { it[KEY_LAST_PACK_VERSION] = v }

    suspend fun setTryOnFit(v: Int) = context.dataStore.edit { it[KEY_TRYON_FIT] = v.coerceIn(0, 100) }
    suspend fun setTryOnShadow(v: Boolean) = context.dataStore.edit { it[KEY_TRYON_SHADOW] = v }
    suspend fun setFaceSwapColor(v: Boolean) = context.dataStore.edit { it[KEY_FACESWAP_COLOR] = v }
    suspend fun setRemovalSkinMatch(v: Boolean) = context.dataStore.edit { it[KEY_REMOVAL_SKIN_MATCH] = v }
    suspend fun setVideoDuration(sec: Int) = context.dataStore.edit { it[KEY_VIDEO_DURATION] = sec.coerceIn(5, 60) }
    suspend fun setVideoFps(fps: Int) = context.dataStore.edit { it[KEY_VIDEO_FPS] = fps.coerceIn(12, 30) }

    /** Generic read for tests. */
    fun <T> flowOf(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }
}
