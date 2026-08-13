package com.phoneagent.extensions

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillLoaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun parsesFrontMatterAndBody() {
        val root = temporary.newFolder("skills")
        val skill = root.resolve("review").apply { mkdirs() }.resolve("SKILL.md")
        skill.writeText("---\nname: review\ndescription: Review a diff\n---\nCheck correctness.")
        val discovered = SkillLoader().discover(listOf(root))
        assertEquals(1, discovered.size)
        assertEquals("review", discovered.single().name)
        assertEquals("Check correctness.", discovered.single().instructions)
    }
}

