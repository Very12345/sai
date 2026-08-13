package com.phoneagent.runtime

import android.content.Context
import android.os.Build
import java.io.File

object NativeRuntimeAssets {
    data class Prepared(
        val abi: String,
        val proot: File,
        val loader: File,
        val libraryDirectory: File,
    )

    /**
     * Runtime ELF files stay in nativeLibraryDir so Android grants execute permission under W^X.
     * PRoot is launched through the system linker and its injection loader is selected explicitly.
     */
    fun prepare(context: Context): Prepared? {
        val directory = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(directory, "libproot.so")
        val loader = File(directory, "libproot-loader.so")
        if (!proot.isFile || !loader.isFile) return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return Prepared(abi, proot, loader, directory)
    }
}
