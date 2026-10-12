package com.shadow306.app

import android.app.Application
import android.os.StrictMode
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

        // StrictMode for debug builds - enabled via debuggable flag check
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // Apply saved language early
        appScope.launch {
            val prefs = ServiceLocator.providePrefs(this@Shadow306App)
            val lang = prefs.languageFlow.first()
            ServiceLocator.provideLocaleManager(this@Shadow306App).applyLanguage(lang)
        }

        // Pre-warm model repository verification (does not download)
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
