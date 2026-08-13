package com.phoneagent.app.runtime

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.runtime.PhoneAgentRootfs
import com.phoneagent.runtime.RunRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Build-pipeline helper. It runs only when an explicit trigger is pushed by scripts. */
@RunWith(AndroidJUnit4::class)
class OfflineRuntimeBuilderInstrumentedTest {
    @Test
    fun prepareArm64GitBaseWhenExplicitlyTriggered() {
        val application = ApplicationProvider.getApplicationContext<PhoneAgentApplication>()
        val external = requireNotNull(application.getExternalFilesDir(null))
        val trigger = File(external, "BUILD_OFFLINE_RUNTIME")
        val recover = File(external, "RECOVER_OFFLINE_RUNTIME")
        val source = File(external, "debian-trixie-aarch64.tar.xz")
        assumeTrue("offline runtime build was not requested", recover.isFile || trigger.isFile && source.isFile)
        try {
            runBlocking {
                if (!recover.isFile) {
                    application.container.rootfsInstaller.importArchive(source, PhoneAgentRootfs.debian13Arm64) { }.getOrThrow()
                }
                val result = application.container.runtime.run(
                    RunRequest(
                        command = if (recover.isFile) RECOVERY_SCRIPT else SCRIPT,
                        workingDirectory = "/home/phoneagent",
                        timeoutMillis = 20 * 60 * 1_000L,
                        outputLimitBytes = 8_000_000,
                    ),
                )
                check(!result.timedOut) { "offline Git staging timed out" }
                check(result.exitCode == 0) { (result.stderr.ifBlank { result.stdout }).takeLast(8_000) }
                val check = application.container.runtime.run(
                    RunRequest("git --version && test -f /.sai-offline-base-v1", "/home/phoneagent", timeoutMillis = 30_000),
                )
                assertTrue(check.stderr.ifBlank { check.stdout }, check.exitCode == 0)
            }
        } finally {
            trigger.delete()
            recover.delete()
        }
    }

    private companion object {
        val SCRIPT = """
            set -eu
            export DEBIAN_FRONTEND=noninteractive
            printf 'Acquire::http::Proxy "http://127.0.0.1:18080";\n' > /etc/apt/apt.conf.d/99-sai-build-proxy
            trap 'rm -f /etc/apt/apt.conf.d/99-sai-build-proxy' EXIT
            apt-get update
            apt-get install -y --no-install-recommends git
            git --version
            apt-get clean
            rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb
            mkdir -p /home/phoneagent
            touch /.sai-offline-base-v1 /.phoneagent-provisioned-v1
        """.trimIndent()

        val RECOVERY_SCRIPT = """
            set -eu
            rm -f /var/lib/dpkg/lock /var/lib/dpkg/lock-frontend /var/cache/apt/archives/lock
            dpkg --configure -a
            git --version
            apt-get clean
            rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb /tmp/apt-dpkg-install-*
            mkdir -p /home/phoneagent
            touch /.sai-offline-base-v1 /.phoneagent-provisioned-v1
        """.trimIndent()
    }
}
