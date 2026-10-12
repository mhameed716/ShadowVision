package com.shadow306.app.di

import android.content.Context
import com.shadow306.app.core.data.db.TemplateStore
import com.shadow306.app.core.data.prefs.AppPreferences
import com.shadow306.app.core.engine.body.ClothParser
import com.shadow306.app.core.engine.body.RemovalEngine
import com.shadow306.app.core.engine.body.TryOnEngine
import com.shadow306.app.core.engine.face.FaceEngine
import com.shadow306.app.core.engine.video.VideoEncoderImpl
import com.shadow306.app.core.engine.video.VideoEngine
import com.shadow306.app.core.export.ExportManager
import com.shadow306.app.core.locale.LocaleManager
import com.shadow306.app.core.modeldownload.ModelDownloader
import com.shadow306.app.core.modeldownload.ModelRepository
import com.shadow306.app.core.modeldownload.ModelVerifier
import com.shadow306.app.core.modeldownload.SideloadScanner
import com.shadow306.app.core.usecase.FaceSwapUseCase
import com.shadow306.app.core.usecase.GetEngineStatusUseCase
import com.shadow306.app.core.usecase.RemovalUseCase
import com.shadow306.app.core.usecase.TemplateUseCase
import com.shadow306.app.core.usecase.TryOnUseCase
import com.shadow306.app.core.usecase.VideoGenUseCase

/**
 * Simple manual DI container (no Hilt/KSP) to keep build fast and avoid
 * annotation processors in this sandbox. Each dependency is lazy singleton
 * per process.
 *
 * On a real device, this would be replaced by Hilt modules, but the API
 * surface stays the same so migration is mechanical.
 */
object ServiceLocator {

    @Volatile private var prefs: AppPreferences? = null
    @Volatile private var verifier: ModelVerifier? = null
    @Volatile private var repository: ModelRepository? = null
    @Volatile private var downloader: ModelDownloader? = null
    @Volatile private var sideloadScanner: SideloadScanner? = null
    @Volatile private var templateStore: TemplateStore? = null
    @Volatile private var localeManager: LocaleManager? = null
    @Volatile private var exportManager: ExportManager? = null

    @Volatile private var clothParser: ClothParser? = null
    @Volatile private var tryOnEngine: TryOnEngine? = null
    @Volatile private var faceEngine: FaceEngine? = null
    @Volatile private var removalEngine: RemovalEngine? = null
    @Volatile private var videoEngine: VideoEngine? = null
    @Volatile private var videoEncoder: VideoEncoderImpl? = null

    fun providePrefs(context: Context): AppPreferences =
        prefs ?: synchronized(this) {
            prefs ?: AppPreferences(context.applicationContext).also { prefs = it }
        }

    fun provideVerifier(context: Context): ModelVerifier =
        verifier ?: synchronized(this) {
            verifier ?: ModelVerifier(context.applicationContext).also { verifier = it }
        }

    fun provideModelRepository(context: Context): ModelRepository =
        repository ?: synchronized(this) {
            repository ?: ModelRepository(
                context.applicationContext,
                providePrefs(context),
                provideVerifier(context)
            ).also { repository = it }
        }

    fun provideModelDownloader(context: Context): ModelDownloader =
        downloader ?: synchronized(this) {
            downloader ?: ModelDownloader(
                context.applicationContext,
                provideModelRepository(context),
                providePrefs(context)
            ).also { downloader = it }
        }

    fun provideSideloadScanner(context: Context): SideloadScanner =
        sideloadScanner ?: synchronized(this) {
            sideloadScanner ?: SideloadScanner(context.applicationContext).also { sideloadScanner = it }
        }

    fun provideTemplateStore(context: Context): TemplateStore =
        templateStore ?: synchronized(this) {
            templateStore ?: TemplateStore(context.applicationContext).also { templateStore = it }
        }

    fun provideLocaleManager(context: Context): LocaleManager =
        localeManager ?: synchronized(this) {
            localeManager ?: LocaleManager(context.applicationContext, providePrefs(context)).also { localeManager = it }
        }

    fun provideExportManager(context: Context): ExportManager =
        exportManager ?: synchronized(this) {
            exportManager ?: ExportManager(context.applicationContext).also { exportManager = it }
        }

    fun provideClothParser(context: Context): ClothParser =
        clothParser ?: synchronized(this) {
            clothParser ?: ClothParser(context.applicationContext).also { clothParser = it }
        }

    fun provideTryOnEngine(context: Context): TryOnEngine =
        tryOnEngine ?: synchronized(this) {
            tryOnEngine ?: TryOnEngine(context.applicationContext).also { tryOnEngine = it }
        }

    fun provideFaceEngine(context: Context): FaceEngine =
        faceEngine ?: synchronized(this) {
            faceEngine ?: FaceEngine(context.applicationContext).also { faceEngine = it }
        }

    fun provideRemovalEngine(context: Context): RemovalEngine =
        removalEngine ?: synchronized(this) {
            removalEngine ?: RemovalEngine(context.applicationContext).also { removalEngine = it }
        }

    fun provideVideoEncoder(context: Context): VideoEncoderImpl =
        videoEncoder ?: synchronized(this) {
            videoEncoder ?: VideoEncoderImpl(context.applicationContext).also { videoEncoder = it }
        }

    fun provideVideoEngine(context: Context): VideoEngine =
        videoEngine ?: synchronized(this) {
            videoEngine ?: VideoEngine(context.applicationContext).also { videoEngine = it }
        }

    // Use cases
    fun provideEngineStatusUseCase(context: Context) = GetEngineStatusUseCase(
        provideModelRepository(context), providePrefs(context)
    )

    fun provideTryOnUseCase(context: Context) = TryOnUseCase(provideTryOnEngine(context))
    fun provideFaceSwapUseCase(context: Context) = FaceSwapUseCase(provideFaceEngine(context))
    fun provideRemovalUseCase(context: Context) = RemovalUseCase(provideRemovalEngine(context))
    fun provideVideoGenUseCase(context: Context) = VideoGenUseCase(provideVideoEngine(context))
    fun provideTemplateUseCase(context: Context) = TemplateUseCase(provideTemplateStore(context))

    /** For tests - clear all singletons */
    fun clear() {
        prefs = null; verifier = null; repository = null; downloader = null
        sideloadScanner = null; templateStore = null; localeManager = null
        exportManager = null
        clothParser = null; tryOnEngine = null; faceEngine = null
        removalEngine = null; videoEngine = null; videoEncoder = null
    }
}
