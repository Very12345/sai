package com.phoneagent.app.ui

internal enum class OutputReferenceKind { URL, FILE }

internal data class OutputReference(
    val kind: OutputReferenceKind,
    val target: String,
    val label: String = target,
)

internal object OutputReferenceParser {
    private val markdownLink = Regex("\\[([^]\\n]{1,120})]\\(([^)\\s]+)\\)")
    private val bareUrl = Regex("https?://[^\\s<>()]+")
    private val codePath = Regex("`([^`\\n]+\\.(?:pdf|txt|md|html?|css|js|ts|tsx|jsx|json|ya?ml|toml|xml|csv|py|kt|java|c|cc|cpp|h|hpp|rs|go|zip|png|jpe?g|gif|webp|svg|docx?|xlsx?|pptx?))`", RegexOption.IGNORE_CASE)
    private val fileExtension = Regex("\\.(?:pdf|txt|md|html?|css|js|ts|tsx|jsx|json|ya?ml|toml|xml|csv|py|kt|java|c|cc|cpp|h|hpp|rs|go|zip|png|jpe?g|gif|webp|svg|docx?|xlsx?|pptx?)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<OutputReference> {
        val found = linkedMapOf<String, OutputReference>()
        markdownLink.findAll(text).forEach { match ->
            val label = match.groupValues[1]
            val target = match.groupValues[2].trim().trimEnd('.', ',', ';', '，', '。')
            val kind = when {
                target.startsWith("http://") || target.startsWith("https://") -> OutputReferenceKind.URL
                fileExtension.containsMatchIn(target.substringBefore('#').substringBefore('?')) -> OutputReferenceKind.FILE
                else -> null
            }
            if (kind != null) found["$kind:$target"] = OutputReference(kind, target, label)
        }
        bareUrl.findAll(text).forEach { match ->
            val target = match.value.trimEnd('.', ',', ';', ':', '，', '。')
            found.putIfAbsent("${OutputReferenceKind.URL}:$target", OutputReference(OutputReferenceKind.URL, target, target))
        }
        codePath.findAll(text).forEach { match ->
            val target = match.groupValues[1].trim()
            found.putIfAbsent("${OutputReferenceKind.FILE}:$target", OutputReference(OutputReferenceKind.FILE, target, target.substringAfterLast('/').substringAfterLast('\\')))
        }
        return found.values.take(12)
    }
}
