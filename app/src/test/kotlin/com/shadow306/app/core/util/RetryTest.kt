package com.shadow306.app.core.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryTest {

    @Test
    fun `base delay grows exponentially`() {
        val policy = RetryPolicy(baseDelayMillis = 1000, multiplier = 2.0, maxDelayMillis = 10000, jitterRatio = 0.0)
        assertEquals(1000, policy.baseDelay(1))
        assertEquals(2000, policy.baseDelay(2))
        assertEquals(4000, policy.baseDelay(3))
        assertEquals(8000, policy.baseDelay(4))
        assertEquals(10000, policy.baseDelay(5)) // capped
    }

    @Test
    fun `retry succeeds on second attempt`() = runTest {
        val policy = RetryPolicy(maxAttempts = 3, baseDelayMillis = 1, jitterRatio = 0.0)
        var attempts = 0
        val result = policy.run {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retry respects maxAttempts`() = runTest {
        val policy = RetryPolicy(maxAttempts = 2, baseDelayMillis = 1, jitterRatio = 0.0)
        var attempts = 0
        try {
            policy.run {
                attempts++
                throw RuntimeException("always fail")
            }
        } catch (_: RuntimeException) {
            // expected
        }
        assertEquals(2, attempts)
    }
}
