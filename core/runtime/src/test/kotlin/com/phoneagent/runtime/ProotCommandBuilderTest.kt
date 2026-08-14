package com.phoneagent.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory

class ProotCommandBuilderTest {
    @Test
    fun buildsIsolatedGuestEnvironmentAndExplicitLoader() {
        val base = createTempDirectory("phoneagent-proot-").toFile()
        val loader = base.resolve("loader").apply { writeText("loader") }
        val systemLinker = base.resolve("linker64").apply { writeText("linker") }
        val builder = ProotCommandBuilder(ProotConfig(
            executable = base.resolve("libproot.so"),
            rootfs = base.resolve("rootfs"),
            home = base.resolve("workspace"),
            tmp = base.resolve("tmp"),
            systemLinker = systemLinker,
            loader = loader,
        ))

        val command = builder.shell("python3 -V", "/home/phoneagent")
        assertEquals(systemLinker.absolutePath, command.first())
        assertTrue(command.contains("--root-id"))
        assertTrue(command.contains("/usr/bin/env"))
        assertTrue(command.contains("-i"))
        assertFalse(command.any { it.startsWith("OPENAI_API_KEY=") })
        assertEquals(loader.absolutePath, builder.hostEnvironment()["PROOT_LOADER"])
        assertEquals(base.resolve("tmp").absolutePath, builder.hostEnvironment()["PROOT_TMP_DIR"])
        assertTrue(command.contains("--bind=/proc"))
        assertTrue(command.contains("--bind=/dev"))
    }

    @Test
    fun sensitiveEnvironmentIsExcludedFromSerializedRequest() {
        val request = RunRequest(
            command = "gh api user",
            workingDirectory = "/home/phoneagent",
            sensitiveEnvironment = mapOf("GH_TOKEN" to "github_pat_secret"),
            trustedBinds = mapOf("/private/dsh-runtime" to "/opt/sai-dsh"),
        )
        val encoded = Json.encodeToString(request)
        assertFalse(encoded.contains("github_pat_secret"))
        assertFalse(encoded.contains("GH_TOKEN"))
        assertFalse(encoded.contains("dsh-runtime"))
    }

    @Test
    fun createsAndroidAppDataTargetsBeforeBindingPrivateRuntimeFiles() {
        val base = createTempDirectory("phoneagent-proot-data-").toFile()
        val appData = base.resolve("data/user/0/com.phoneagent.app")
        val rootfs = appData.resolve("files/runtime/debian").apply { mkdirs() }
        val workspace = appData.resolve("files/workspaces").apply { mkdirs() }
        val builder = ProotCommandBuilder(ProotConfig(
            executable = base.resolve("libproot.so"),
            rootfs = rootfs,
            home = workspace,
            tmp = appData.resolve("cache/runtime-tmp"),
        ))

        val trustedRuntime = appData.resolve("files/dsh/runtime/current").apply { mkdirs() }
        val command = builder.shell(
            "true",
            "/home/phoneagent",
            trustedBinds = mapOf(trustedRuntime.absolutePath to "/opt/sai-dsh"),
        )

        assertTrue(command.contains("--bind=${trustedRuntime.canonicalPath}:/opt/sai-dsh"))
        assertTrue(rootfs.resolve("opt/sai-dsh").isDirectory)
    }
}
