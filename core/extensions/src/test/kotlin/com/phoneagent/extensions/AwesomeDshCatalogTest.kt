package com.phoneagent.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwesomeDshCatalogTest {
    private val client = ExtensionCatalogClient()

    @Test fun parsesCuratedRepositoriesAndMonorepoPackages() {
        val markdown = """
            ### UI Enhancements
            - [wx-yss/dsh-message-rail](https://github.com/wx-yss/dsh-message-rail) - Message navigation.
            - [TZHR-invest/dsh-plugins#dsh-mobile-ui](https://github.com/TZHR-invest/dsh-plugins/tree/main/packages/dsh-mobile-ui) - Mobile UI.
        """.trimIndent()

        val items = client.parseAwesomeDshCatalog(markdown)

        assertEquals(2, items.size)
        assertEquals("dsh:wx-yss/dsh-message-rail", items[0].id)
        assertEquals("dsh:TZHR-invest/dsh-plugins#packages/dsh-mobile-ui", items[1].id)
        assertEquals("dsh-mobile-ui", items[1].name)
        assertEquals("UI Enhancements", items[1].category)
        assertEquals("", items[1].version)
        assertTrue(items.all { it.source.contains("awesome-dsh-plugin") })
    }

    @Test fun marksDiskFallbackAsCached() {
        val item = client.parseAwesomeDshCatalog(
            "- [owner/plugin](https://github.com/owner/plugin) - Description.",
            cached = true,
        ).single()

        assertTrue(item.source.contains("本地缓存"))
    }
}
