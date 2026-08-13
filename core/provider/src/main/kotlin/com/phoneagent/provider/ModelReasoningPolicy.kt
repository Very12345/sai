package com.phoneagent.provider

data class ReasoningOption(
    val effort: ReasoningEffort,
    val label: String,
    val selection: ReasoningSelection = ReasoningSelection.fromLegacy(effort),
)

/**
 * Resolves reasoning controls in a stable order: explicit model overrides, provider metadata,
 * official model-family rules, then no control. Unknown models never receive guessed parameters.
 */
object ModelReasoningPolicy {
    fun capabilities(profile: ProviderProfile, modelId: String = profile.defaultModel): ModelReasoningCapabilities? {
        profile.modelReasoningCapabilities[modelId]?.let { return it }
        profile.modelReasoningCapabilities.entries.firstOrNull { it.key.equals(modelId, true) }?.value?.let { return it }
        return officialCapabilities(modelId, profile.protocol)
    }

    fun options(profile: ProviderProfile): List<ReasoningOption> {
        val caps = capabilities(profile) ?: return emptyList()
        val canonical = caps.supportedEfforts
            .map { caps.aliases[it] ?: it }
            .filter { it != ReasoningEffort.NONE && it != ReasoningEffort.AUTO }
            .distinct()
            .toMutableList()
        val result = mutableListOf<ReasoningOption>()
        if (ReasoningMode.AUTO in caps.supportedModes) {
            result += ReasoningOption(ReasoningEffort.AUTO, "Auto", ReasoningSelection(ReasoningMode.AUTO))
        }
        if (ReasoningMode.ADAPTIVE in caps.supportedModes) {
            result += ReasoningOption(ReasoningEffort.AUTO, "Adaptive", ReasoningSelection(ReasoningMode.ADAPTIVE))
        }
        canonical.forEach { effort ->
            result += ReasoningOption(effort, labelFor(effort), ReasoningSelection(ReasoningMode.ENABLED, effort))
        }
        if (!caps.mandatory && ReasoningMode.DISABLED in caps.supportedModes) {
            result += ReasoningOption(ReasoningEffort.NONE, "Disabled", ReasoningSelection(ReasoningMode.DISABLED))
        }
        return result.distinctBy { it.selection }
    }

    fun normalize(profile: ProviderProfile): ProviderProfile {
        val options = options(profile)
        if (options.isEmpty()) return profile.copy(
            reasoningEffort = ReasoningEffort.AUTO,
            reasoningSelection = ReasoningSelection(),
        )
        val requested = if (profile.reasoningSelection == ReasoningSelection() && profile.reasoningEffort != ReasoningEffort.AUTO) {
            ReasoningSelection.fromLegacy(profile.reasoningEffort)
        } else profile.reasoningSelection
        val selected = options.firstOrNull { it.selection == requested }
            ?: options.firstOrNull { it.effort == requested.legacyEffort() }
            ?: options.firstOrNull { it.selection.mode == ReasoningMode.AUTO }
            ?: options.first()
        return profile.copy(reasoningEffort = selected.effort, reasoningSelection = selected.selection)
    }

    fun usesDeepSeekThinkingWire(profile: ProviderProfile): Boolean =
        capabilities(profile)?.parameterFormat == ReasoningParameterFormat.DEEPSEEK_THINKING

    private fun labelFor(effort: ReasoningEffort): String = when (effort) {
        ReasoningEffort.AUTO -> "Auto"
        ReasoningEffort.NONE -> "Disabled"
        ReasoningEffort.MINIMAL -> "Minimal"
        ReasoningEffort.LOW -> "Low"
        ReasoningEffort.MEDIUM -> "Medium"
        ReasoningEffort.HIGH -> "High"
        ReasoningEffort.XHIGH -> "XHigh"
        ReasoningEffort.MAX -> "Max"
    }

    private fun officialCapabilities(rawModel: String, protocol: ProviderProtocol): ModelReasoningCapabilities? {
        val model = rawModel.lowercase().substringAfterLast('/')
        val commonModes = setOf(ReasoningMode.AUTO, ReasoningMode.ENABLED, ReasoningMode.DISABLED)
        fun caps(
            efforts: List<ReasoningEffort>,
            format: ReasoningParameterFormat,
            mandatory: Boolean = false,
            modes: Set<ReasoningMode> = commonModes,
            minBudget: Int? = null,
            maxBudget: Int? = null,
            aliases: Map<ReasoningEffort, ReasoningEffort> = emptyMap(),
        ) = ModelReasoningCapabilities(
            supportedModes = if (mandatory) modes - ReasoningMode.DISABLED else modes,
            supportedEfforts = efforts,
            mandatory = mandatory,
            minBudgetTokens = minBudget,
            maxBudgetTokens = maxBudget,
            parameterFormat = format,
            source = ReasoningCapabilitySource.OFFICIAL_RULE,
            aliases = aliases,
        )

        return when {
            model.contains("deepseek") || model.contains("reasoner") || Regex("(^|[_-])r1([_-]|$)").containsMatchIn(model) -> caps(
                efforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
                format = ReasoningParameterFormat.DEEPSEEK_THINKING,
            )
            model.contains("glm-5.2") || model.contains("glm5.2") || model.contains("glm-5-2") -> caps(
                efforts = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX),
                format = ReasoningParameterFormat.OPENAI_EFFORT,
                aliases = mapOf(
                    ReasoningEffort.MINIMAL to ReasoningEffort.NONE,
                    ReasoningEffort.LOW to ReasoningEffort.HIGH,
                    ReasoningEffort.MEDIUM to ReasoningEffort.HIGH,
                    ReasoningEffort.XHIGH to ReasoningEffort.MAX,
                ),
            )
            Regex("glm[-_ ]?(4\\.5|4\\.6|4\\.7|5\\.0|5\\.1)").containsMatchIn(model) -> caps(
                efforts = listOf(ReasoningEffort.HIGH),
                format = ReasoningParameterFormat.BINARY_THINKING,
            )
            model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") || model.startsWith("gpt-5") -> caps(
                efforts = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH),
                format = ReasoningParameterFormat.OPENAI_EFFORT,
            )
            protocol == ProviderProtocol.ANTHROPIC_MESSAGES || model.contains("claude") -> caps(
                efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX),
                format = ReasoningParameterFormat.ANTHROPIC_EFFORT,
                modes = setOf(ReasoningMode.ADAPTIVE, ReasoningMode.ENABLED, ReasoningMode.DISABLED),
            )
            model.contains("gemini-2.5-pro") -> caps(
                efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
                format = ReasoningParameterFormat.GEMINI_BUDGET,
                mandatory = true,
                minBudget = 128,
                maxBudget = 32_768,
            )
            model.contains("gemini-2.5") -> caps(
                efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
                format = ReasoningParameterFormat.GEMINI_BUDGET,
                minBudget = 0,
                maxBudget = 24_576,
            )
            model.contains("gemini-3") -> caps(
                efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
                format = ReasoningParameterFormat.GEMINI_LEVEL,
            )
            model.contains("qwen") || model.contains("kimi") -> caps(
                efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.MAX),
                format = ReasoningParameterFormat.QWEN_BUDGET,
                minBudget = 0,
                maxBudget = 32_768,
            )
            model.contains("mistral") || model.contains("magistral") -> caps(
                efforts = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH),
                format = ReasoningParameterFormat.OPENAI_EFFORT,
            )
            model.contains("minimax") -> caps(
                efforts = emptyList(),
                format = ReasoningParameterFormat.BINARY_THINKING,
                modes = setOf(ReasoningMode.ADAPTIVE, ReasoningMode.DISABLED),
            )
            else -> null
        }
    }
}
