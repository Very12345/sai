package com.phoneagent.provider

import okhttp3.OkHttpClient

object ProviderFactory {
    fun create(profile: ProviderProfile, client: OkHttpClient = HttpProviderAdapter.defaultClient()): ProviderAdapter =
        when (profile.protocol) {
            ProviderProtocol.OPENAI_RESPONSES -> OpenAiResponsesAdapter(profile, client)
            ProviderProtocol.OPENAI_CHAT -> OpenAiChatAdapter(profile, client)
            ProviderProtocol.ANTHROPIC_MESSAGES -> AnthropicAdapter(profile, client)
            ProviderProtocol.GEMINI_NATIVE -> GeminiAdapter(profile, client)
        }
}

