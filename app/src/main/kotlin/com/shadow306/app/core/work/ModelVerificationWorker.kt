package com.shadow306.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shadow306.app.di.ServiceLocator

/**
 * Periodic worker that verifies installed models and repairs corrupted ones.
 * Runs on boot and when network becomes available.
 *
 * - Checks sha256 of each installed model
 * - Deletes corrupted files so they will be re-downloaded
 * - Updates ModelRepository state
 */
class ModelVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = ServiceLocator.provideModelRepository(applicationContext)
            val verifier = ServiceLocator.provideVerifier(applicationContext)

            val manifest = repo.loadBundledManifest()
            val verification = verifier.verify(manifest)

            if (verification.corrupted.isNotEmpty()) {
                verifier.deleteCorrupted(manifest)
            }

            repo.refreshVerification()

            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "model_verification"
    }
}
