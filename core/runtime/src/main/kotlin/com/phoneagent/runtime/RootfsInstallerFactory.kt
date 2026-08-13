package com.phoneagent.runtime

import android.content.Context
import android.os.Build
import okhttp3.OkHttpClient

object RootfsInstallerFactory {
    fun create(context: Context): RootfsInstaller = RootfsInstaller(
        context.applicationContext,
        OkHttpClient.Builder().retryOnConnectionFailure(true).build(),
        CommonsCompressRootfsExtractor(),
    )
}

object PhoneAgentRootfs {
    val debian13Arm64 = RootfsManifest(
        version = "sai-debian-13-arm64-git-v1",
        architecture = "arm64-v8a",
        url = "https://github.com/termux/proot-distro/releases/download/v4.26.0/debian-trixie-aarch64-pd-v4.26.0.tar.xz",
        sha256 = "17ff9b64e3358bde16429db0ac92c6411f4fdcddbbc73e24d83d665de3c43ec6",
        compressedBytes = 56_915_812,
        installedBytes = 900_000_000,
        sourceUrl = "https://github.com/termux/proot-distro/tree/v4.26.0",
        embeddedAsset = "runtime/sai-debian13-arm64-git-v1.tar.xz",
    )

    val debian13X86_64 = RootfsManifest(
        version = "debian-13-trixie-x86_64-pd-v4.26.0",
        architecture = "x86_64",
        url = "https://github.com/termux/proot-distro/releases/download/v4.26.0/debian-trixie-x86_64-pd-v4.26.0.tar.xz",
        sha256 = "e2edc15363395936cf0cba8c440a108458dba58fb496d3d962909d7a8d9777ae",
        compressedBytes = 36_725_620,
        installedBytes = 1_200_000_000,
        sourceUrl = "https://github.com/termux/proot-distro/tree/v4.26.0",
    )

    fun forDevice(): RootfsManifest = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> debian13Arm64
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> debian13X86_64
        else -> error("PhoneAgent supports only arm64-v8a and x86_64")
    }
}
