package com.phoneagent.runtime

class RuntimeProvisioner(private val runtime: LinuxRuntime) {
    suspend fun provision(onStage: (String) -> Unit): Result<Unit> = runCatching {
        onStage("正在配置内置 Debian 与 Git")
        val result = runtime.run(
            RunRequest(
                command = SCRIPT,
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 60_000,
                outputLimitBytes = 4_000_000,
            ),
        )
        check(!result.timedOut) { "内置环境配置超时" }
        check(result.exitCode == 0) {
            (result.stderr.ifBlank { result.stdout }).takeLast(4_000).ifBlank { "内置 Debian 与 Git 配置失败" }
        }
        onStage("正在离线验证 Bash 与 Git")
        val verification = runtime.run(
            RunRequest(
                command = "bash --version | head -n1 && git --version && test -f /.sai-offline-base-v1",
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 30_000,
            ),
        )
        check(verification.exitCode == 0) { verification.stderr.ifBlank { "内置 Git 验证失败" } }
    }

    companion object {
        private val SCRIPT = """
            set -e
            test -x /usr/bin/git
            rm -f /etc/apt/apt.conf.d/99-sai-build-proxy
            for sai_apt_conf in /etc/apt/apt.conf.d/*; do
                if [ -f "${'$'}sai_apt_conf" ] && grep -q '127\.0\.0\.1:18080' "${'$'}sai_apt_conf"; then rm -f "${'$'}sai_apt_conf"; fi
            done
            sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' /etc/apt/sources.list
            mkdir -p /home/phoneagent
            git config --global init.defaultBranch main
            git config --global user.name sai
            git config --global user.email sai@localhost
            touch /.sai-offline-base-v1 /.phoneagent-provisioned-v1
        """.trimIndent()
    }
}
