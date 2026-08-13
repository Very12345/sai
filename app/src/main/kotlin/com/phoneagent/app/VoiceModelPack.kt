package com.phoneagent.app

import android.content.Context
import android.content.Intent
import android.net.Uri

object VoiceModelPack {
    const val PACKAGE_NAME = "com.sai.voice.pack"
    fun context(host: Context): Context? = runCatching {
        host.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()
    fun isInstalled(host: Context): Boolean = context(host) != null
    fun uninstallIntent(): Intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$PACKAGE_NAME"))
    fun downloadUri(): Uri? = BuildConfig.GITHUB_REPOSITORY.takeIf(String::isNotBlank)?.let { repository ->
        Uri.parse("https://github.com/$repository/releases/latest/download/sai-voice-pack-zh-en.apk")
    }
}
