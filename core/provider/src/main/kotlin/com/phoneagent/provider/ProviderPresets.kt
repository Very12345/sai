package com.phoneagent.provider

object ProviderPresets {
    val all: List<ProviderProfile> = listOf(
        ProviderProfile(
            id = "openai",
            displayName = "OpenAI",
            protocol = ProviderProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com",
            requestPath = "/v1/responses",
            modelsPath = "/v1/models",
            defaultModel = "gpt-5.6-terra",
            contextWindow = 1_050_000,
            maxOutputTokens = 128_000,
            capabilities = ProviderCapabilities(reasoning = true, imageInput = true, modelDiscovery = true),
        ),
        ProviderProfile(
            id = "anthropic",
            displayName = "Anthropic",
            protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://api.anthropic.com",
            requestPath = "/v1/messages",
            modelsPath = "/v1/models",
            defaultModel = "claude-sonnet-5",
            customHeaders = mapOf("anthropic-version" to "2023-06-01"),
            contextWindow = 200_000,
            capabilities = ProviderCapabilities(reasoning = true, imageInput = true, modelDiscovery = true),
        ),
        ProviderProfile(
            id = "gemini",
            displayName = "Google Gemini",
            protocol = ProviderProtocol.GEMINI_NATIVE,
            baseUrl = "https://generativelanguage.googleapis.com",
            requestPath = "/v1beta/models/{model}:streamGenerateContent",
            modelsPath = "/v1beta/models",
            defaultModel = "gemini-3.6-flash",
            contextWindow = 1_000_000,
            capabilities = ProviderCapabilities(reasoning = true, imageInput = true, modelDiscovery = true),
        ),
        openAiCompatible(
            "deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash", 1_000_000,
            pricing = mapOf(
                "deepseek-v4-flash" to ModelPricing(0.02, 1.0, 2.0, "DeepSeek 官方中国区价表", "CNY"),
                "deepseek-v4-pro" to ModelPricing(0.025, 3.0, 6.0, "DeepSeek 官方中国区价表", "CNY"),
            ),
        ),
        ProviderProfile(
            id = "opencode-zen",
            displayName = "OpenCode Zen（免费）",
            protocol = ProviderProtocol.OPENAI_CHAT,
            baseUrl = "https://opencode.ai/zen",
            requestPath = "/v1/chat/completions",
            modelsPath = "/v1/models",
            defaultModel = "deepseek-v4-flash-free",
            anonymousApiKey = "public",
            contextWindow = 128_000,
            maxOutputTokens = 16_384,
            capabilities = ProviderCapabilities(
                reasoning = true,
                imageInput = true,
                modelDiscovery = true,
            ),
        ),
        openAiCompatible("openrouter", "OpenRouter", "https://openrouter.ai/api", "openai/gpt-5.6-terra", 1_000_000),
        openAiCompatible("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode", "qwen-max", 128_000),
        openAiCompatible("kimi", "Kimi", "https://api.moonshot.cn", "kimi-k2.5", 256_000),
        openAiCompatible("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas", "glm-5", 128_000),
        openAiCompatible("mistral", "Mistral", "https://api.mistral.ai", "mistral-large-latest", 128_000),
        openAiCompatible("groq", "Groq", "https://api.groq.com/openai", "openai/gpt-oss-120b", 128_000),
        customOpenAi("custom", "自定义渠道", "https://api.example.com", "model-name"),
    )

    fun customOpenAi(
        id: String,
        name: String,
        baseUrl: String,
        model: String,
        protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT,
    ) = ProviderProfile(
        id = id,
        displayName = name,
        protocol = protocol,
        baseUrl = baseUrl.trimEnd('/'),
        requestPath = if (protocol == ProviderProtocol.OPENAI_RESPONSES) "/v1/responses" else "/v1/chat/completions",
        modelsPath = "/v1/models",
        defaultModel = model,
        capabilities = ProviderCapabilities(reasoning = true, modelDiscovery = true),
    )

    fun customAnthropic(id: String, name: String, baseUrl: String, model: String) = ProviderProfile(
        id = id,
        displayName = name,
        protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
        baseUrl = baseUrl.trimEnd('/'),
        requestPath = "/v1/messages",
        modelsPath = "/v1/models",
        defaultModel = model,
        customHeaders = mapOf("anthropic-version" to "2023-06-01"),
        capabilities = ProviderCapabilities(reasoning = true, modelDiscovery = true),
    )

    private fun openAiCompatible(
        id: String,
        name: String,
        baseUrl: String,
        model: String,
        context: Int,
        pricing: Map<String, ModelPricing> = emptyMap(),
    ) =
        ProviderProfile(
            id = id,
            displayName = name,
            protocol = ProviderProtocol.OPENAI_CHAT,
            baseUrl = baseUrl,
            requestPath = "/v1/chat/completions",
            modelsPath = "/v1/models",
            defaultModel = model,
            contextWindow = context,
            capabilities = ProviderCapabilities(reasoning = true, modelDiscovery = true),
            modelPricing = pricing,
        )
}
