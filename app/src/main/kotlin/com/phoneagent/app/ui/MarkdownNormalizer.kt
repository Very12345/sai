package com.phoneagent.app.ui

/** Converts common single-dollar and bracket math delimiters to Markwon's double-dollar inline format. */
internal fun normalizeMarkdownMath(markdown: String): String {
    val output = StringBuilder(markdown.length + 32)
    var fenced = false
    val lines = markdown.lines()
    lines.forEachIndexed { index, original ->
        val trimmed = original.trimStart()
        if (trimmed.startsWith("```")) {
            fenced = !fenced
            output.append(original)
        } else if (fenced) {
            output.append(original)
        } else {
            val blockNormalized = when (trimmed) {
                "\\[" -> original.substringBefore("\\[") + "$$"
                "\\]" -> original.substringBefore("\\]") + "$$"
                else -> normalizeInlineMath(original.replaceMathParentheses())
            }
            output.append(blockNormalized)
        }
        if (index != lines.lastIndex) output.append('\n')
    }
    return output.toString()
}

private fun String.replaceMathParentheses(): String =
    replace(Regex("""(?<!\\)\\\((.+?)(?<!\\)\\\)""")) { "$$${it.groupValues[1]}$$" }

private fun normalizeInlineMath(line: String): String {
    val output = StringBuilder(line.length + 8)
    var index = 0
    while (index < line.length) {
        val isSingleDollar = line[index] == '$' &&
            (index == 0 || line[index - 1] != '\\') &&
            (index == 0 || line[index - 1] != '$') &&
            (index + 1 >= line.length || line[index + 1] != '$')
        if (!isSingleDollar) {
            output.append(line[index++])
            continue
        }
        var end = index + 1
        while (end < line.length) {
            if (line[end] == '$' && line[end - 1] != '\\' && (end + 1 >= line.length || line[end + 1] != '$')) break
            end++
        }
        if (end >= line.length) {
            output.append(line[index++])
            continue
        }
        output.append("$$").append(line, index + 1, end).append("$$")
        index = end + 1
    }
    return output.toString()
}
