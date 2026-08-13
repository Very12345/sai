package com.phoneagent.agent.tools

import com.phoneagent.agent.Tool
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.ToolExecutionContext
import com.phoneagent.agent.ToolResult
import com.phoneagent.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

class HttpFetchTool(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns { hostname ->
            InetAddress.getAllByName(hostname).toList().also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private DNS result is blocked" }
            }
        }
        .build(),
) : Tool {
    override val definition = ToolDefinition(
        "http_fetch", "Fetch a public HTTP(S) resource with private-network and size protections.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("url", buildJsonObject { put("type", "string") })
                put("max_bytes", buildJsonObject { put("type", "integer"); put("minimum", 1024); put("maximum", 2_000_000) })
            })
            put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("url")) })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.NETWORK)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val limit = arguments.int("max_bytes", 1_000_000).coerceIn(1_024, 2_000_000)
        var uri = validatePublicUrl(arguments.string("url"))
        repeat(6) { redirectCount ->
            val response = client.newCall(Request.Builder().url(uri.toString()).get().build()).execute()
            response.use {
                if (it.isRedirect) {
                    require(redirectCount < 5) { "Too many redirects" }
                    val location = requireNotNull(it.header("Location")) { "Redirect has no Location" }
                    uri = validatePublicUrl(uri.resolve(location).toString())
                } else {
                    val source = it.body.source()
                    source.request((limit + 1).toLong())
                    val bytes = source.buffer.clone().readByteArray(minOf(source.buffer.size, (limit + 1).toLong()))
                    val truncated = bytes.size > limit
                    val body = bytes.copyOf(minOf(bytes.size, limit)).toString(Charsets.UTF_8)
                    return@withContext ToolResult(
                        success = it.isSuccessful,
                        output = "HTTP ${it.code} ${it.header("Content-Type").orEmpty()}\n$body",
                        metadata = mapOf("url" to uri.toString(), "status" to it.code.toString()),
                        truncated = truncated,
                    )
                }
            }
        }
        error("Too many redirects")
    }

    private fun validatePublicUrl(raw: String): URI {
        val uri = URI(raw)
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) { "Only HTTP(S) URLs are allowed" }
        require(uri.userInfo == null) { "URL credentials are not allowed" }
        val host = requireNotNull(uri.host) { "URL host is missing" }
        require(!host.equals("localhost", true) && !host.endsWith(".local", true)) { "Local hosts are blocked" }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private, local or multicast addresses are blocked" }
        return uri
    }
}

private fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    if (address is Inet6Address) {
        val first = address.address.first().toInt() and 0xff
        if (first and 0xfe == 0xfc) return false
    }
    return true
}
