package com.phoneagent.app.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserLocalhostInstrumentedTest {
    @Test
    fun agentWebViewCanReachDeviceLoopback() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val responder = thread(name = "phoneagent-localhost-test") {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val body = "<html><head><title>PhoneAgent loopback</title></head><body>PHONEAGENT_LOCALHOST_OK</body></html>"
                socket.getOutputStream().bufferedWriter().use { writer ->
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: text/html; charset=utf-8\r\n")
                    writer.write("Content-Length: ${body.toByteArray().size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(body)
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val browser = AgentBrowserSession(context, "localhost-instrumentation")
        try {
            val observation = runBlocking { browser.navigate("http://127.0.0.1:${server.localPort}") }
            assertTrue(observation, observation.contains("PHONEAGENT_LOCALHOST_OK"))
        } finally {
            runBlocking { browser.destroy() }
            server.close()
            responder.join(2_000)
        }
    }
}
