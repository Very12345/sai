package com.phoneagent.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal object NativePtyBridge {
    init { System.loadLibrary("phoneagent_pty") }

    external fun start(arguments: Array<String>, environment: Array<String>, columns: Int, rows: Int): LongArray?
    external fun read(descriptor: Int, destination: ByteArray): Int
    external fun write(descriptor: Int, source: ByteArray): Int
    external fun resize(descriptor: Int, columns: Int, rows: Int): Int
    external fun waitFor(processId: Int): Int
    external fun terminate(processId: Int)
    external fun close(descriptor: Int)
}

internal class NativePtySession private constructor(
    private val processId: Int,
    private val descriptor: Int,
) : PtySession {
    private val closed = AtomicBoolean(false)
    private val descriptorClosed = AtomicBoolean(false)
    private val channel = Channel<TerminalEvent>(Channel.BUFFERED)
    override val events: Flow<TerminalEvent> = channel.receiveAsFlow()

    init {
        Thread({
            try {
                val buffer = ByteArray(8192)
                while (!closed.get()) {
                    val count = NativePtyBridge.read(descriptor, buffer)
                    if (count <= 0) break
                    channel.trySend(TerminalEvent.Output(buffer.copyOf(count)))
                }
                val exitCode = NativePtyBridge.waitFor(processId)
                channel.trySend(TerminalEvent.Closed(exitCode))
            } catch (error: Throwable) {
                if (!closed.get()) channel.trySend(TerminalEvent.Failure(error.message ?: "PTY failed"))
            } finally {
                closeDescriptor()
                channel.close()
            }
        }, "phoneagent-pty-reader").start()
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        check(!closed.get()) { "PTY is closed" }
        var offset = 0
        while (offset < bytes.size) {
            val chunk = if (offset == 0) bytes else bytes.copyOfRange(offset, bytes.size)
            val written = NativePtyBridge.write(descriptor, chunk)
            if (written <= 0) throw IOException("PTY write failed: $written")
            offset += written
        }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        require(columns > 0 && rows > 0)
        val result = NativePtyBridge.resize(descriptor, columns, rows)
        if (result < 0) throw IOException("PTY resize failed: $result")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        NativePtyBridge.terminate(processId)
        closeDescriptor()
    }

    private fun closeDescriptor() {
        if (descriptor >= 0 && descriptorClosed.compareAndSet(false, true)) {
            runCatching { NativePtyBridge.close(descriptor) }
        }
    }

    companion object {
        fun start(arguments: List<String>, environment: Map<String, String>): NativePtySession {
            val result = NativePtyBridge.start(
                arguments.toTypedArray(),
                environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                80,
                24,
            ) ?: throw IOException("Unable to create PTY")
            require(result.size == 2 && result[0] > 0 && result[1] >= 0) { "Invalid PTY result" }
            return NativePtySession(result[0].toInt(), result[1].toInt())
        }
    }
}
