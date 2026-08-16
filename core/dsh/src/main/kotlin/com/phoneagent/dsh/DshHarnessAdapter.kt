package com.phoneagent.dsh

import com.phoneagent.harness.ApprovalDecision
import com.phoneagent.harness.HarnessAdapter
import com.phoneagent.harness.HarnessCapabilities
import com.phoneagent.harness.HarnessConfigSnapshot
import com.phoneagent.harness.HarnessEvent
import com.phoneagent.harness.HarnessHandle
import com.phoneagent.harness.HarnessHealth
import com.phoneagent.harness.HarnessHealthState
import com.phoneagent.harness.HarnessInput
import com.phoneagent.harness.HarnessInputPart
import com.phoneagent.harness.HarnessKind
import com.phoneagent.harness.HarnessSessionSpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

/** Native DSH control adapter. UI events can be fed from the bridge via [publish]. */
class DshHarnessAdapter(
    private val runtime: DshRuntimeSupervisor,
    private val api: DshApiClient,
    private val configurationSync: suspend (HarnessConfigSnapshot) -> Unit = {},
    private val approvalResponder: suspend (String, ApprovalDecision) -> Unit = { _, _ -> },
) : HarnessAdapter {
    override val kind = HarnessKind.DSH
    override val capabilities = HarnessCapabilities(
        attachments = true,
        images = true,
        approvals = true,
        steering = true,
        resume = true,
        extensions = true,
    )

    private val handles = ConcurrentHashMap<String, HarnessHandle>()
    private val events = MutableSharedFlow<HarnessEvent>(extraBufferCapacity = 256)

    override suspend fun prepare(): HarnessHealth {
        runtime.ensureStarted()
        return runCatching { runtime.awaitReady() }.fold(
            onSuccess = { HarnessHealth(HarnessHealthState.READY, it.runtimeVersion, it.detail) },
            onFailure = { HarnessHealth(HarnessHealthState.FAILED, message = it.message.orEmpty()) },
        )
    }

    override suspend fun syncConfiguration(snapshot: HarnessConfigSnapshot) = configurationSync(snapshot)

    override suspend fun createSession(spec: HarnessSessionSpec): HarnessHandle {
        val health = prepare()
        check(health.state == HarnessHealthState.READY) { health.message }
        val external = api.ensureSession(spec.saiSessionId, spec.workingDirectory, spec.preset)
        return HarnessHandle(kind, spec.saiSessionId, external, spec.workspaceId).also {
            handles[it.saiSessionId] = it
        }
    }

    override suspend fun listSessions(workspaceId: String): List<HarnessHandle> =
        handles.values.filter { it.workspaceId == workspaceId }.sortedBy(HarnessHandle::saiSessionId)

    override suspend fun send(handle: HarnessHandle, input: HarnessInput) {
        api.prompt(handle.externalSessionId, input.asDshText(), steer = false)
    }

    override suspend fun steer(handle: HarnessHandle, input: HarnessInput) {
        api.prompt(handle.externalSessionId, input.asDshText(), steer = true)
    }

    override suspend fun cancel(handle: HarnessHandle) {
        api.cancel(handle.externalSessionId)
    }

    override suspend fun respondApproval(requestId: String, decision: ApprovalDecision) =
        approvalResponder(requestId, decision)

    override fun observe(handle: HarnessHandle): Flow<HarnessEvent> =
        events.filter { it.sessionId == handle.saiSessionId || it.sessionId == handle.externalSessionId }

    fun publish(event: HarnessEvent): Boolean = events.tryEmit(event)

    private fun HarnessInput.asDshText(): String = parts.joinToString("\n") { part ->
        when (part) {
            is HarnessInputPart.Text -> part.text
            is HarnessInputPart.File -> "@file:${part.localPath} (${part.mimeType}, ${part.sizeBytes} bytes)"
            is HarnessInputPart.Image -> "@image:${part.localPath} (${part.mimeType})"
            is HarnessInputPart.Audio -> "@audio:${part.localPath} (${part.mimeType})"
        }
    }.trim().also { require(it.isNotBlank()) { "Cannot send an empty request" } }
}
