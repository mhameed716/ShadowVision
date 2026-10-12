package com.shadow306.app

import android.app.Application
import android.os.StrictMode
import com.shadow306.app.core.modeldownload.BundledModelInstaller
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Shadow306App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // 1. فحص النماذج المدمجة في APK (للتوزيع بدون إنترنت)
        appScope.launch(Dispatchers.IO) {
            try {
                val installer = BundledModelInstaller(this@Shadow306App)
                if (installer.isBundled()) {
                    val result = installer.installIfNeeded()
                    if (result != null && (result.copied > 0 || result.skipped > 0)) {
                        // إذا تم تثبيت النماذج المدمجة، ضع علامة اكتمال الإعداد
                        val prefs = ServiceLocator.providePrefs(this@Shadow306App)
                        prefs.setSetupDone(true)
                        result.let {
                            android.util.Log.i("Shadow306", "Bundled models installed: ${it.copied} copied, ${it.skipped} skipped, ${it.failed} failed, ${it.totalBytes / 1024 / 1024} MB")
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("Shadow306", "Failed to install bundled models", t)
            }
        }

        // 2. تطبيق اللغة المحفوظة
        appScope.launch {
            val prefs = ServiceLocator.providePrefs(this@Shadow306App)
            val lang = prefs.languageFlow.first()
            ServiceLocator.provideLocaleManager(this@Shadow306App).applyLanguage(lang)
        }

        // 3. تهيئة مستودع النماذج
        appScope.launch {
            runCatching {
                val repo = ServiceLocator.provideModelRepository(this@Shadow306App)
                repo.loadBundledManifest()
                repo.refreshVerification()
                ServiceLocator.provideTemplateStore(this@Shadow306App).load()
            }
        }
    }
}
