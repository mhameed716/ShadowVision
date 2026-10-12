package com.shadow306.app.core.util

import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Exponential backoff policy shared by the model downloader and the network
 * calls that check for manifest updates.
 *
 * Kept free of Android APIs so it can be unit tested on the JVM (see RetryTest).
 */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMillis: Long = 1_000L,
    val maxDelayMillis: Long = 30_000L,
    val multiplier: Double = 2.0,
    val jitterRatio: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(baseDelayMillis >= 0) { "baseDelayMillis must be >= 0" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in 0..1" }
    }

    /** Backoff for a 1-based [attempt] number, before jitter. */
    fun baseDelay(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val raw = baseDelayMillis * multiplier.pow(safeAttempt - 1.0)
        return raw.toLong().coerceAtMost(maxDelayMillis)
    }

    /**
     * Backoff with symmetric jitter so many devices retrying against the same CDN
     * do not synchronise into a thundering herd.
     */
    fun delayFor(attempt: Int, seed: Long = 0L): Long {
        val base = baseDelay(attempt)
        if (base <= 0L) return 0L
        val random = if (seed == 0L) Random.nextDouble(-jitterRatio, jitterRatio)
        else Random(seed).nextDouble(-jitterRatio, jitterRatio)
        return (base * (1.0 + random)).toLong().coerceIn(0L, maxDelayMillis * 2)
    }

    /**
     * Runs [block] until it succeeds or the attempts are exhausted. The last
     * exception is rethrown. [shouldRetry] can veto a retry (e.g. for 404s).
     */
    suspend fun <T> run(
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend (attempt: Int) -> T,
    ): T {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (t: Throwable) {
                lastError = t
                val attemptsLeft = attempt < maxAttempts
                if (!attemptsLeft || !shouldRetry(t)) throw t
                delay(delayFor(attempt))
            }
        }
        throw lastError ?: IllegalStateException("RetryPolicy.run failed without an exception")
    }
}
