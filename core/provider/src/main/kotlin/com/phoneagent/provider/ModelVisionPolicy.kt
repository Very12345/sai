package com.phoneagent.provider

/** Conservative model-modality hints used when a provider does not return structured metadata. */
object ModelVisionPolicy {
    private val visionMarkers = listOf(
        "gpt-4o", "gpt-4.1", "gpt-5", "claude-", "gemini",
        "vision", "pixtral", "kimi-k2.5", "qwen-vl", "qvq", "vl-", "-vl",
        "llava", "llama-vision", "grok-vision", "deepseek-vl",
    )

    fun supportsImageInput(profile: ProviderProfile, modelId: String = profile.defaultModel): Boolean {
        val normalized = modelId.lowercase()
        return visionMarkers.any(normalized::contains)
    }

    fun isVisionCandidate(profile: ProviderProfile, modelId: String): Boolean {
        val normalized = modelId.lowercase()
        return visionMarkers.any(normalized::contains)
    }
}
