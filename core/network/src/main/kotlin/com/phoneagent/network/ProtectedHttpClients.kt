package com.phoneagent.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.random.Random

/** Shared request protection for model APIs, GitHub and extension catalogs. */
object ProtectedHttpClients {
    private val dispatcher = Dispatcher().apply {
        maxRequests = 6
        maxRequestsPerHost = 2
    }
    private val pool = ConnectionPool(5, 5, TimeUnit.MINUTES)
    private val interceptor = RequestProtectionInterceptor()

    fun model(): OkHttpClient = base().readTimeout(0, TimeUnit.MILLISECONDS).build()
    fun catalog(): OkHttpClient = base().readTimeout(45, TimeUnit.SECONDS).build()

    private fun base(): OkHttpClient.Builder = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(pool)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(interceptor)
}

internal class RequestProtectionInterceptor(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    private data class HostState(
        val mutationLock: ReentrantLock = ReentrantLock(true),
        @Volatile var nextMutationAt: Long = 0,
        @Volatile var cooldownUntil: Long = 0,
    )

    private val hosts = ConcurrentHashMap<String, HostState>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase(Locale.ROOT)
        val state = hosts.computeIfAbsent(host) { HostState() }
        val mutation = isGitHub(host) && request.method !in SAFE_METHODS
        return if (mutation) state.mutationLock.withLock {
            val wait = state.nextMutationAt - clockMillis()
            if (wait > 0) sleeper(wait)
            try {
                execute(chain, state)
            } finally {
                state.nextMutationAt = clockMillis() + GITHUB_MUTATION_GAP_MS
            }
        } else execute(chain, state)
    }

    private fun execute(chain: Interceptor.Chain, state: HostState): Response {
        val request = chain.request()
        val preflightWait = state.cooldownUntil - clockMillis()
        if (preflightWait > MAX_INLINE_WAIT_MS) {
            throw RequestThrottledException(state.cooldownUntil, "服务端正在限流，请稍后重试")
        }
        if (preflightWait > 0) sleeper(preflightWait)

        var attempt = 0
        while (true) {
            val response = chain.proceed(request)
            val delay = RetryDelayPolicy.delayMillis(response, attempt, clockMillis())
            if (delay == null) return response

            val cooldown = clockMillis() + delay
            if (cooldown > state.cooldownUntil) state.cooldownUntil = cooldown
            val canReplay = request.method in SAFE_METHODS && attempt < MAX_RETRIES && delay <= MAX_INLINE_WAIT_MS
            if (!canReplay) return response
            response.close()
            sleeper(delay + Random.nextLong(0, 251))
            attempt++
        }
    }

    private fun isGitHub(host: String): Boolean = host == "github.com" || host.endsWith(".github.com") ||
        host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")

    companion object {
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
        private const val MAX_RETRIES = 2
        private const val MAX_INLINE_WAIT_MS = 30_000L
        private const val GITHUB_MUTATION_GAP_MS = 1_000L
    }
}

class RequestThrottledException(val retryAtEpochMillis: Long, message: String) : IOException(message)

internal object RetryDelayPolicy {
    fun delayMillis(response: Response, attempt: Int, nowMillis: Long): Long? {
        val limited = response.code == 429 || response.code == 403 &&
            (response.header("x-ratelimit-remaining") == "0" || response.header("retry-after") != null)
        val transient = response.code in setOf(502, 503, 504)
        if (!limited && !transient) return null

        parseRetryAfter(response.header("retry-after"), nowMillis)?.let { return it }
        response.header("x-ratelimit-reset")?.toLongOrNull()?.let { resetSeconds ->
            return (resetSeconds * 1_000L - nowMillis).coerceAtLeast(1_000L)
        }
        return if (limited) 60_000L else min(1_000L shl attempt.coerceAtMost(5), 30_000L)
    }

    internal fun parseRetryAfter(value: String?, nowMillis: Long): Long? {
        if (value.isNullOrBlank()) return null
        value.trim().toLongOrNull()?.let { return (it * 1_000L).coerceAtLeast(0L) }
        return runCatching {
            val at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            (at - nowMillis).coerceAtLeast(0L)
        }.getOrNull()
    }
}
