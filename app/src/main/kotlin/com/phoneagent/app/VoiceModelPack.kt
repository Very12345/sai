package com.phoneagent.app

import android.content.Context
import android.content.Intent
import android.net.Uri

object VoiceModelPack {
    const val PACKAGE_NAME = "com.sai.voice.pack"
    const val ASSET_NAME = "sai-voice-pack-zh-en.apk"
    fun context(host: Context): Context? = runCatching {
        host.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()
    fun isInstalled(host: Context): Boolean = context(host) != null
    fun uninstallIntent(): Intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$PACKAGE_NAME"))
}
