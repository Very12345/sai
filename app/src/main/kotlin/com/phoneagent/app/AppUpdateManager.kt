package com.phoneagent.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class AppUpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

data class AppUpdateState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val releaseNotes: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val apkPath: String? = null,
    val message: String? = null,
)

internal data class SaiRelease(
    val tag: String,
    val pageUrl: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val checksumsUrl: String,
)

internal data class SaiApkAsset(
    val tag: String,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val checksumsUrl: String,
)

internal class AppUpdateManager(
    private val context: Context,
    private val githubTokenProvider: () -> CharArray? = { null },
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(true).build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val updateDirectory = File(context.cacheDir, "app-updates")

    suspend fun check(): SaiRelease? = withContext(Dispatchers.IO) {
        val releases = releaseRoots()
        val allowPrerelease = BuildConfig.VERSION_NAME.contains("preview", true) || BuildConfig.VERSION_NAME.contains("rc", true)
        releases
            .filterNot { (it["draft"] as? JsonPrimitive)?.booleanOrNull == true }
            .filter { allowPrerelease || (it["prerelease"] as? JsonPrimitive)?.booleanOrNull != true }
            .mapNotNull(::parseRelease)
            .filter { compareSaiVersions(it.tag, BuildConfig.VERSION_NAME) > 0 }
            .maxWithOrNull { left, right -> compareSaiVersions(left.tag, right.tag) }
    }

    /** Resolves a module APK from real Release assets, including preview releases used by sai. */
    suspend fun latestApkAsset(assetName: String): SaiApkAsset? = withContext(Dispatchers.IO) {
        require(assetName.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "APK 资产名称无效" }
        releaseRoots()
            .filterNot { (it["draft"] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull { root -> parseApkAsset(root, assetName) }
            .maxWithOrNull { left, right -> compareSaiVersions(left.tag, right.tag) }
    }

    suspend fun download(
        release: SaiRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = downloadVerifiedApk(
        apkName = release.apkName,
        apkUrl = release.apkUrl,
        checksumsUrl = release.checksumsUrl,
        expectedPackageName = context.packageName,
        onProgress = onProgress,
    )

    suspend fun downloadModule(
        asset: SaiApkAsset,
        expectedPackageName: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = downloadVerifiedApk(
        apkName = asset.apkName,
        apkUrl = asset.apkUrl,
        checksumsUrl = asset.checksumsUrl,
        expectedPackageName = expectedPackageName,
        onProgress = onProgress,
    )

    private suspend fun downloadVerifiedApk(
        apkName: String,
        apkUrl: String,
        checksumsUrl: String,
        expectedPackageName: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        updateDirectory.mkdirs()
        val checksumText = client.newCall(request(checksumsUrl)).execute().use { response ->
            check(response.isSuccessful) { "校验清单下载失败：HTTP ${response.code}" }
            response.body.string()
        }
        val expected = checksumText.lineSequence().map(String::trim).firstNotNullOfOrNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[1].removePrefix("*").trim() == apkName) parts[0].lowercase() else null
        } ?: error("Release 中没有 $apkName 的 SHA-256")

        val partial = File(updateDirectory, "$apkName.part")
        val target = File(updateDirectory, apkName)
        client.newCall(request(apkUrl)).execute().use { response ->
            check(response.isSuccessful) { "APK 下载失败：HTTP ${response.code}" }
            val total = response.body.contentLength().coerceAtLeast(0)
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(copied, total)
                    }
                }
            }
        }
        val actual = sha256(partial)
        check(actual.equals(expected, true)) { "APK SHA-256 校验失败，下载文件已删除" }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "无法保存已校验的 APK" }
        val archiveInfo = archivePackageInfo(target)
        check(archiveInfo.packageName == expectedPackageName) {
            target.delete()
            "APK 包名不匹配，已阻止安装"
        }
        check(signingDigests(archiveInfo) == signingDigests(currentPackageInfo())) {
            target.delete()
            "APK 签名与当前 sai 不一致，已阻止安装"
        }
        target
    }

    fun launchInstaller(apk: File): Boolean {
        require(apk.isFile && apk.canonicalPath.startsWith(updateDirectory.canonicalPath + File.separator)) {
            "更新 APK 路径无效"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return true
    }

    private fun parseRelease(root: JsonObject): SaiRelease? {
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val assets = root["assets"] as? JsonArray ?: return null
        val apkName = if (Build.SUPPORTED_ABIS.any { it == "x86_64" }) "sai-android-x86_64.apk" else "sai-android-arm64.apk"
        fun assetUrl(name: String) = assets.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { asset ->
            if ((asset["name"] as? JsonPrimitive)?.contentOrNull == name) {
                (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull
            } else null
        }
        return SaiRelease(
            tag = tag,
            pageUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            notes = (root["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            apkName = apkName,
            apkUrl = assetUrl(apkName) ?: return null,
            checksumsUrl = assetUrl("SHA256SUMS.txt") ?: return null,
        )
    }

    private fun parseApkAsset(root: JsonObject, assetName: String): SaiApkAsset? {
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val assets = root["assets"] as? JsonArray ?: return null
        fun assetUrl(name: String) = assets.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { asset ->
            if ((asset["name"] as? JsonPrimitive)?.contentOrNull == name) {
                (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull
            } else null
        }
        return SaiApkAsset(
            tag = tag,
            pageUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            apkName = assetName,
            apkUrl = assetUrl(assetName) ?: return null,
            checksumsUrl = assetUrl("SHA256SUMS.txt") ?: return null,
        )
    }

    private fun releaseRoots(): List<JsonObject> {
        val releaseRequest = request("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases?per_page=20")
        return client.newCall(releaseRequest).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "GitHub Release 检查失败：HTTP ${response.code}" }
            val roots = json.parseToJsonElement(body) as? JsonArray ?: error("GitHub Release 返回格式无效")
            roots.mapNotNull { it as? JsonObject }
        }
    }

    private fun request(url: String): Request {
        val builder = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val token = githubTokenProvider()
        try {
            token?.concatToString()?.let { builder.header("Authorization", "Bearer $it") }
        } finally {
            token?.fill('\u0000')
        }
        return builder.build()
    }

    private fun currentPackageInfo(): PackageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
    )

    private fun archivePackageInfo(file: File): PackageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(
        file.absolutePath,
        PackageManager.GET_SIGNING_CERTIFICATES,
    )) { "下载的文件不是有效 APK" }

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signingInfo = requireNotNull(info.signingInfo) { "APK 没有签名信息" }
        val certificates = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        return certificates.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"

        internal fun compareSaiVersions(left: String, right: String): Int {
            fun parse(value: String): VersionParts {
                val clean = value.removePrefix("v")
                val base = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(clean)?.groupValues
                val major = base?.getOrNull(1)?.toIntOrNull() ?: 0
                val minor = base?.getOrNull(2)?.toIntOrNull() ?: 0
                val patch = base?.getOrNull(3)?.toIntOrNull() ?: 0
                val lower = clean.lowercase()
                val rank = when {
                    "preview" in lower || "alpha" in lower || "beta" in lower -> 1
                    "rc" in lower -> 2
                    else -> 3
                }
                val pre = Regex("(?:preview|alpha|beta|rc)[.-]?(\\d+)", RegexOption.IGNORE_CASE)
                    .find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                return VersionParts(major, minor, patch, rank, pre)
            }
            return parse(left).compareTo(parse(right))
        }
    }
}

private data class VersionParts(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val releaseRank: Int,
    val previewNumber: Int,
) : Comparable<VersionParts> {
    override fun compareTo(other: VersionParts): Int = compareValuesBy(
        this, other,
        VersionParts::major,
        VersionParts::minor,
        VersionParts::patch,
        VersionParts::releaseRank,
        VersionParts::previewNumber,
    )
}
