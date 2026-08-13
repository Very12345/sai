package com.phoneagent.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceConversationPhase { STOPPED, PREPARING, LISTENING, RECOGNIZING, THINKING, SPEAKING, ERROR }

data class VoiceConversationState(
    val active: Boolean = false,
    val phase: VoiceConversationPhase = VoiceConversationPhase.STOPPED,
    val transcript: String = "",
    val elapsedMillis: Long = 0,
    val voiceTurnId: String? = null,
    val muted: Boolean = false,
    val sessionId: String? = null,
)

/** Application-scoped truth shared by the Activity, notification and detached pet. */
object VoiceConversationController {
    private val mutable = MutableStateFlow(VoiceConversationState())
    val state: StateFlow<VoiceConversationState> = mutable.asStateFlow()

    fun update(block: (VoiceConversationState) -> VoiceConversationState) { mutable.value = block(mutable.value) }
    fun reset() { mutable.value = VoiceConversationState() }
}
