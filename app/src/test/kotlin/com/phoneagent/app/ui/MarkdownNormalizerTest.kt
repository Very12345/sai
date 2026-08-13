package com.phoneagent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownNormalizerTest {
    @Test fun normalizesCommonMathDelimitersWithoutTouchingCodeFences() {
        val source = """
            Inline ${'$'}E=mc^2${'$'} and \(a+b\).
            \[
            x^2+y^2=z^2
            \]
            ```kotlin
            val price = "${'$'}5"
            ```
        """.trimIndent()

        assertEquals(
            """
                Inline ${'$'}${'$'}E=mc^2${'$'}${'$'} and ${'$'}${'$'}a+b${'$'}${'$'}.
                ${'$'}${'$'}
                x^2+y^2=z^2
                ${'$'}${'$'}
                ```kotlin
                val price = "${'$'}5"
                ```
            """.trimIndent(),
            normalizeMarkdownMath(source),
        )
    }

    @Test fun preservesExistingDoubleDollarMath() {
        assertEquals("before ${'$'}${'$'}x+1${'$'}${'$'} after", normalizeMarkdownMath("before ${'$'}${'$'}x+1${'$'}${'$'} after"))
    }
}
