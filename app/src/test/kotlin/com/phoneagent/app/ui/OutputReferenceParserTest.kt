package com.phoneagent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputReferenceParserTest {
    @Test fun extractsMarkdownFilesAndUrlsWithoutDuplicates() {
        val result = OutputReferenceParser.parse("生成了 [报告](docs/report.pdf)，预览 https://example.com/demo 。文件也位于 `docs/report.pdf`。")
        assertEquals(2, result.size)
        assertTrue(result.any { it.kind == OutputReferenceKind.FILE && it.target == "docs/report.pdf" })
        assertTrue(result.any { it.kind == OutputReferenceKind.URL && it.target == "https://example.com/demo" })
    }

    @Test fun ignoresUnlinkedWordsThatOnlyLookLikePaths() {
        assertTrue(OutputReferenceParser.parse("普通回复，不包含输出文件或网址").isEmpty())
    }
}
