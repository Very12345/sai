package com.phoneagent.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryDelayPolicyTest {
    private val request = Request.Builder().url("https://api.github.com/rate_limit").build()

    @Test fun honorsRetryAfterSeconds() {
        assertEquals(7_000L, RetryDelayPolicy.delayMillis(response(429, "retry-after" to "7"), 0, 1_000L))
    }

    @Test fun honorsGitHubReset() {
        val response = response(403, "x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "12")
        assertEquals(2_000L, RetryDelayPolicy.delayMillis(response, 0, 10_000L))
    }

    @Test fun successfulResponseIsNotRetried() {
        assertNull(RetryDelayPolicy.delayMillis(response(200), 0, 0L))
    }

    private fun response(code: Int, vararg headers: Pair<String, String>) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
}
