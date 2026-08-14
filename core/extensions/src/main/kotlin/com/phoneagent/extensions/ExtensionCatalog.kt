package com.phoneagent.extensions

import com.phoneagent.network.ProtectedHttpClients
import java.io.File
import java.security.MessageDigest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit

@Serializable
enum class CatalogKind { MCP_REGISTRY, SKILLS_SH, CLAUDE_MARKETPLACE, GIT, ZIP }

@Serializable
data class ExtensionCatalogSource(
    val id: String,
    val displayName: String,
    val kind: CatalogKind,
    val endpoint: String,
    val enabled: Boolean = true,
)

@Serializable
data class CatalogExtension(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val source: String,
    val kind: ExtensionKind,
    val installUrl: String? = null,
    val homepage: String? = null,
    val installs: Long? = null,
    val auditSummary: String? = null,
)

@Serializable
data class ExtensionInstallPlan(
    val id: String,
    val name: String,
    val version: String,
    val source: String,
    val kind: ExtensionKind,
    val sourceDigest: String,
    val files: List<StagedExtensionFile>,
    val permissions: Set<ExtensionPermission>,
    val warnings: List<String>,
    val license: String? = null,
    val safeToStage: Boolean,
)

@Serializable
data class StagedExtensionFile(val path: String, val contents: String, val digest: String)

@Serializable
data class CapabilityDiagnostic(
    val id: String,
    val status: String,
    val summary: String,
    val details: List<String> = emptyList(),
)

class ExtensionCatalogClient(
    private val client: OkHttpClient = ProtectedHttpClients.catalog(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val githubTokenProvider: () -> CharArray? = { null },
) {
    suspend fun searchMcp(query: String, limit: Int = 30): List<CatalogExtension> = withContext(Dispatchers.IO) {
        val url = "https://registry.modelcontextprotocol.io/v0.1/servers".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("version", "latest")
            .addQueryParameter("limit", limit.toString())
            .build()
        val root = getJson(url.toString()).jsonObject
        val servers = root.arrayAt("servers", "data")
        servers.mapNotNull { item ->
            val wrapper = item.jsonObject
            val server = (wrapper["server"] as? JsonObject) ?: wrapper
            val name = server.stringAt("name") ?: return@mapNotNull null
            CatalogExtension(
                id = name,
                name = server.stringAt("title", "displayName") ?: name,
                description = server.stringAt("description").orEmpty(),
                version = server.stringAt("version").orEmpty(),
                source = "MCP Registry",
                kind = ExtensionKind.MCP,
                homepage = server.urlAt("websiteUrl", "repository") ?: "https://registry.modelcontextprotocol.io",
            )
        }
    }

    suspend fun searchSkills(query: String, limit: Int = 30): List<CatalogExtension> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        publicSkills("trending", 80)
            .plus(publicSkills("hot", 80))
            .distinctBy(CatalogExtension::id)
            .filter { normalized.isBlank() || it.name.contains(normalized, true) || it.source.contains(normalized, true) }
            .sortedByDescending { it.installs ?: 0L }
            .take(limit)
    }

    suspend fun searchPlugins(query: String, limit: Int = 30): List<CatalogExtension> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        (runCatching { officialSaiPlugins() }.getOrDefault(emptyList()) +
            runCatching { searchDshPlugins(normalized, limit) }.getOrDefault(emptyList()))
            .distinctBy(CatalogExtension::id)
            .filter { normalized.isBlank() || it.name.contains(normalized, true) || it.description.contains(normalized, true) }
            .take(limit)
    }

    suspend fun recommendations(limit: Int = 40): List<CatalogExtension> = withContext(Dispatchers.IO) {
        val perKind = (limit / 2).coerceAtLeast(30)
        val skills = runCatching { publicSkills("trending", perKind) }.getOrDefault(emptyList())
        val mcp = runCatching { searchMcp("", perKind) }.getOrDefault(emptyList())
        val dsh = (runCatching { officialSaiPlugins() }.getOrDefault(emptyList()) +
            runCatching { searchDshPlugins("", perKind) }.getOrDefault(emptyList()))
        val builtIns = builtInRecommendations()
        val remote = (dsh + skills + mcp).distinctBy { "${it.kind}:${it.id}" }
            .sortedWith(compareByDescending<CatalogExtension> { it.installs != null }.thenByDescending { it.installs ?: 0L })
        (remote + builtIns).distinctBy { "${it.kind}:${it.id}" }
    }

    private fun searchDshPlugins(query: String, limit: Int): List<CatalogExtension> {
        val terms = buildString {
            append("topic:dsh-plugin")
            if (query.isNotBlank() && query.lowercase() !in setOf("dsh-puglin", "dsh-plugin")) append(" ${query.trim()}")
        }
        val url = "https://api.github.com/search/repositories".toHttpUrl().newBuilder()
            .addQueryParameter("q", terms)
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", limit.coerceIn(1, 100).toString())
            .build()
        val epoch = Instant.parse("2026-08-13T00:00:00Z")
        val activeAfter = Instant.now().minus(180, ChronoUnit.DAYS)
        return getJson(url.toString()).jsonObject.arrayAt("items").mapNotNull { element ->
            val repo = element as? JsonObject ?: return@mapNotNull null
            val fullName = repo.stringAt("full_name") ?: return@mapNotNull null
            val created = repo.stringAt("created_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val updated = repo.stringAt("updated_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (updated == null || updated.isBefore(activeAfter)) return@mapNotNull null
            val recentRepository = created != null && !created.isBefore(epoch)
            CatalogExtension(
                id = "dsh:$fullName",
                name = repo.stringAt("name") ?: fullName.substringAfter('/'),
                description = repo.stringAt("description").orEmpty(),
                version = repo.stringAt("default_branch").orEmpty(),
                source = "GitHub · dsh-plugin",
                kind = ExtensionKind.PLUGIN,
                installUrl = repo.stringAt("clone_url"),
                homepage = repo.stringAt("html_url"),
                installs = (repo["stargazers_count"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
                auditSummary = if (recentRepository) {
                    "DSH 语义纪元后发布 · 安装前仍需验证 bundle、许可证与预构建产物"
                } else {
                    "旧仓库中的近期 DSH 项目 · 仅供发现，存在兼容包后才能安装"
                },
            )
        }
    }

    /** First-party packages are accepted only from the detached Ed25519-signed static catalog. */
    private fun officialSaiPlugins(): List<CatalogExtension> {
        val base = "https://very12345.github.io/sai-dsh-plugins"
        val payload = getText("$base/index.json", "application/json")
        val signature = getText("$base/index.json.sig", "text/plain").trim()
        check(verifySaiCatalog(payload.toByteArray(Charsets.UTF_8), signature)) {
            "sai DSH 市场签名无效"
        }
        val root = json.parseToJsonElement(payload).jsonObject
        check(root.stringAt("topic") == "dsh-plugin") { "sai DSH 市场主题无效" }
        return root.arrayAt("packages").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.stringAt("id") ?: return@mapNotNull null
            val version = item.stringAt("version").orEmpty()
            CatalogExtension(
                id = "sai:$id",
                name = id.substringAfterLast('/'),
                description = "sai 官方 DeepSeek Harness 插件 · 兼容 ${item.stringAt("dshVersion").orEmpty()}",
                version = version,
                source = "sai 签名 DSH 市场",
                kind = ExtensionKind.PLUGIN,
                installUrl = "https://github.com/Very12345/sai-dsh-plugins/releases/download/v$version/${id.removePrefix("@").replace('/', '-')}-$version.tgz",
                homepage = "https://github.com/Very12345/sai-dsh-plugins/tree/main/packages/${id.substringAfterLast('/')}",
                auditSummary = "Ed25519 签名目录 · 启用前仍展示权限与摘要",
            )
        }
    }

    private fun verifySaiCatalog(payload: ByteArray, encodedSignature: String): Boolean = runCatching {
        val publicDer = Base64.getDecoder().decode(SAI_CATALOG_PUBLIC_KEY_BASE64)
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicDer))
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(payload)
            verify(Base64.getDecoder().decode(encodedSignature))
        }
    }.getOrDefault(false)

    private fun builtInRecommendations(): List<CatalogExtension> = listOf(
        CatalogExtension(
            id = "modelcontextprotocol/servers/filesystem",
            name = "Filesystem MCP",
            description = "受控文件系统工具示例；安装前必须把允许目录限制到项目工作区。",
            source = "sai 内置推荐",
            kind = ExtensionKind.MCP,
            homepage = "https://github.com/modelcontextprotocol/servers",
            installs = 1,
            auditSummary = "内置目录条目 · 仍需本地权限审查",
        ),
        CatalogExtension(
            id = "modelcontextprotocol/servers/git",
            name = "Git MCP",
            description = "Git 仓库读取与操作能力，写入和历史修改必须单独审批。",
            source = "sai 内置推荐",
            kind = ExtensionKind.MCP,
            homepage = "https://github.com/modelcontextprotocol/servers",
            installs = 1,
            auditSummary = "内置目录条目 · 仍需本地权限审查",
        ),
        CatalogExtension(
            id = "anthropics/skills/frontend-design",
            name = "frontend-design",
            description = "前端界面设计工作流；通过 Git/ZIP 快照安装。",
            source = "sai 内置推荐",
            kind = ExtensionKind.SKILL,
            installUrl = "https://github.com/anthropics/skills",
            homepage = "https://github.com/anthropics/skills",
            installs = 1,
            auditSummary = "内置目录条目 · 第三方内容",
        ),
        CatalogExtension(
            id = "anthropics/skills/skill-creator",
            name = "skill-creator",
            description = "创建、审查和改进 Agent Skill 的标准工作流。",
            source = "Anthropic Skills",
            kind = ExtensionKind.SKILL,
            installUrl = "https://github.com/anthropics/skills",
            homepage = "https://github.com/anthropics/skills",
            installs = 1,
            auditSummary = "官方公开仓库 · 安装前仍需本地审查",
        ),
    )

    suspend fun stageSkill(item: CatalogExtension): ExtensionInstallPlan = withContext(Dispatchers.IO) {
        require(item.kind == ExtensionKind.SKILL) { "Only skills can use the skills.sh snapshot installer" }
        val detail = runCatching { getJson("https://skills.sh/api/v1/skills/${item.id}").jsonObject }
            .getOrElse { return@withContext stageGitHubSkill(item) }
        val remoteFiles = detail["files"] as? JsonArray
            ?: throw CatalogAuthenticationException("skills.sh API 需要认证；请使用内置市场页查看，并从项目 Git 源安装")
        val warnings = mutableListOf<String>()
        val permissions = mutableSetOf(ExtensionPermission.WORKSPACE_READ)
        val files = remoteFiles.map { entry ->
            val file = entry.jsonObject
            val path = validateRelativePath(file.stringAt("path").orEmpty())
            val contents = file.stringAt("contents").orEmpty()
            scanCapabilities(path, contents, permissions, warnings)
            StagedExtensionFile(path, contents, sha256(contents.encodeToByteArray()))
        }
        require(files.any { it.path == "SKILL.md" || it.path.endsWith("/SKILL.md") }) { "扩展快照中没有 SKILL.md" }
        val digest = sha256(files.sortedBy { it.path }.joinToString("\n") { "${it.path}:${it.digest}" }.encodeToByteArray())
        ExtensionInstallPlan(
            id = item.id,
            name = item.name,
            version = detail.stringAt("hash")?.take(12).orEmpty(),
            source = item.installUrl ?: item.source,
            kind = ExtensionKind.SKILL,
            sourceDigest = digest,
            files = files,
            permissions = permissions,
            warnings = warnings.distinct(),
            safeToStage = warnings.none { it.startsWith("阻止") },
        )
    }

    /**
     * Stages a source-discovered DSH plugin without executing npm or repository scripts.
     * Only repositories that already contain a JavaScript entry point and a DSH/Cordis
     * bundle declaration are installable on a phone; source-only TypeScript projects
     * remain visible in the catalog but are deliberately rejected here.
     */
    suspend fun stageDshPlugin(
        item: CatalogExtension,
        onProgress: (stage: String, progress: Float) -> Unit = { _, _ -> },
    ): ExtensionInstallPlan = withContext(Dispatchers.IO) {
        require(item.kind == ExtensionKind.PLUGIN) { "只有插件条目可以使用 DSH 便携安装器" }
        require(item.id.startsWith("dsh:")) { "该插件没有可验证的 GitHub DSH 源；当前只能查看介绍" }
        val repositoryId = item.id.removePrefix("dsh:")
        val parts = repositoryId.split('/')
        require(parts.size == 2) { "DSH 插件仓库标识无效" }
        val (owner, repository) = parts
        onProgress("正在读取 GitHub 仓库信息…", 0.08f)
        val repo = getJson("https://api.github.com/repos/$owner/$repository").jsonObject
        val branch = repo.stringAt("default_branch") ?: "main"
        val license = (repo["license"] as? JsonObject)?.stringAt("spdx_id", "name")
        val licenseUnknown = license.isNullOrBlank() || license in setOf("NOASSERTION", "OTHER")
        onProgress("正在读取文件树与兼容清单…", 0.2f)
        val tree = getJson("https://api.github.com/repos/$owner/$repository/git/trees/${urlPart(branch)}?recursive=1")
            .jsonObject.arrayAt("tree").mapNotNull { it as? JsonObject }
        val packageEntry = tree
            .filter { it.stringAt("type") == "blob" && it.stringAt("path")?.endsWith("package.json") == true }
            .sortedBy { it.stringAt("path")?.count { char -> char == '/' } ?: Int.MAX_VALUE }
            .firstOrNull() ?: error("仓库中没有 package.json")
        val packagePath = packageEntry.stringAt("path") ?: error("package.json 路径无效")
        val packageDirectory = packagePath.substringBeforeLast('/', "")
        val prefix = packageDirectory.takeIf(String::isNotBlank)?.plus('/') ?: ""
        val packageText = getText(
            "https://raw.githubusercontent.com/$owner/$repository/${urlPath(branch)}/${urlPath(packagePath)}",
            "text/plain",
        )
        onProgress("正在验证 DSH bundle 与预构建入口…", 0.38f)
        val manifest = json.parseToJsonElement(packageText).jsonObject
        val scripts = manifest["scripts"] as? JsonObject
        val blockedScripts = setOf("preinstall", "install", "postinstall", "prepare", "prepublish", "prepublishOnly")
            .filter { scripts?.containsKey(it) == true }
        require(blockedScripts.isEmpty()) {
            "插件声明了禁止自动执行的生命周期脚本：${blockedScripts.joinToString()}"
        }
        val main = manifest.stringAt("main", "module") ?: "index.js"
        require(main.endsWith(".js") || main.endsWith(".mjs") || main.endsWith(".cjs")) {
            "插件只有源码入口，没有手机可直接运行的预构建 JavaScript"
        }
        val mainPath = validateRelativePath(prefix + main.removePrefix("./"))
        require(tree.any { it.stringAt("type") == "blob" && it.stringAt("path") == mainPath }) {
            "预构建入口不存在：$main"
        }
        val packageTree = tree.filter { entry ->
            val path = entry.stringAt("path") ?: return@filter false
            entry.stringAt("type") == "blob" && path.startsWith(prefix)
        }
        val hasCordisPatch = packageTree.any { entry ->
            entry.stringAt("path")?.removePrefix(prefix) in setOf("cordis.patch.yml", "cordis.patch.yaml", "dsh.bundle.patch.yml")
        }
        val hasDshManifest = manifest.containsKey("dsh") || packageText.contains("dsh.bundle.patch")
        require(hasCordisPatch || hasDshManifest) { "package.json 未声明 DSH bundle，且缺少 Cordis patch" }

        val allowed = setOf("json", "js", "mjs", "cjs", "yml", "yaml", "md", "css", "html", "svg", "txt")
        val selected = packageTree.filter { entry ->
            val relative = entry.stringAt("path")?.removePrefix(prefix).orEmpty()
            relative.substringAfterLast('.', "").lowercase() in allowed &&
                !relative.startsWith("node_modules/") && !relative.startsWith(".git/")
        }
        require(selected.size <= 160) { "插件预构建包文件过多（${selected.size}），请使用作者提供的 npm tarball 或 Release" }
        val permissions = mutableSetOf(ExtensionPermission.WORKSPACE_READ)
        val warnings = mutableListOf<String>()
        if (licenseUnknown) warnings += "未检测到明确开源许可证；公开仓库不等同于 MIT，请在安装和使用前自行确认作者授权"
        var totalBytes = 0
        val files = selected.mapIndexed { index, entry ->
            onProgress(
                "正在下载并扫描文件 ${index + 1}/${selected.size}…",
                0.5f + (index.toFloat() / selected.size.coerceAtLeast(1)) * 0.42f,
            )
            val remotePath = entry.stringAt("path") ?: error("插件文件路径无效")
            val relative = validateRelativePath(remotePath.removePrefix(prefix))
            val declaredSize = (entry["size"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            totalBytes += declaredSize
            require(totalBytes <= 4_000_000) { "插件预构建包超过 4 MB 文本安全上限" }
            val contents = if (remotePath == packagePath) packageText else getText(
                "https://raw.githubusercontent.com/$owner/$repository/${urlPath(branch)}/${urlPath(remotePath)}",
                "text/plain",
            )
            scanCapabilities(relative, contents, permissions, warnings)
            StagedExtensionFile(relative, contents, sha256(contents.encodeToByteArray()))
        }
        require(files.any { it.path == main.removePrefix("./") }) { "暂存包缺少声明的入口文件" }
        onProgress("正在计算摘要与安装计划…", 0.95f)
        warnings += "第三方 DSH 插件安装后默认禁用；启用会重启当前 DSH Profile"
        val digest = sha256(files.sortedBy { it.path }.joinToString("\n") { "${it.path}:${it.digest}" }.encodeToByteArray())
        ExtensionInstallPlan(
            id = item.id,
            name = manifest.stringAt("name") ?: item.name,
            version = manifest.stringAt("version") ?: item.version.ifBlank { branch },
            source = "https://github.com/$owner/$repository/tree/$branch/$packageDirectory",
            kind = ExtensionKind.PLUGIN,
            sourceDigest = digest,
            files = files,
            permissions = permissions,
            warnings = warnings.distinct(),
            license = license,
            safeToStage = warnings.none { it.startsWith("阻止") },
        )
    }

    suspend fun skillAudit(id: String): CapabilityDiagnostic = withContext(Dispatchers.IO) {
        runCatching { getJson("https://skills.sh/api/v1/skills/audit/$id").jsonObject }
            .fold(
                onSuccess = { root ->
                    val audits = (root["audits"] as? JsonArray).orEmpty().map { it.jsonObject }
                    val details = audits.map { audit ->
                        "${audit.stringAt("provider").orEmpty()}: ${audit.stringAt("status").orEmpty()} · ${audit.stringAt("summary").orEmpty()}"
                    }
                    val failed = audits.any { it.stringAt("status") == "fail" }
                    CapabilityDiagnostic(id, if (failed) "FAIL" else "REVIEW", "第三方审计仅供参考，仍需本地检查", details)
                },
                onFailure = { CapabilityDiagnostic(id, "UNKNOWN", "没有可用的匿名审计结果") },
            )
    }

    private fun getJson(url: String): JsonElement {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        val token = if (url.toHttpUrl().host == "api.github.com") githubTokenProvider() else null
        try {
            token?.concatToString()?.let { builder.header("Authorization", "Bearer $it") }
        } finally {
            token?.fill('\u0000')
        }
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 401 || response.code == 403) throw CatalogAuthenticationException("市场 API 需要认证")
            check(response.isSuccessful) { "市场请求失败：HTTP ${response.code}" }
            return json.parseToJsonElement(body)
        }
    }

    private fun getText(url: String, accept: String = "text/html"): String {
        val request = Request.Builder().url(url).header("Accept", accept).build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "市场请求失败：HTTP ${response.code}" }
            return body
        }
    }

    private fun publicSkills(view: String, limit: Int): List<CatalogExtension> {
        val html = getText("https://www.skills.sh/$view")
        val anchors = Regex(
            """href=[\"']/([^\"'?#]+/[^\"'?#]+/[^\"'?#]+)[\"'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return anchors.findAll(html).mapNotNull { match ->
            val path = match.groupValues[1].trim('/')
            val parts = path.split('/')
            if (parts.size != 3 || parts.first() in setOf("_next", "api", "docs")) return@mapNotNull null
            val text = decodeHtml(match.groupValues[2]).replace(Regex("\\s+"), " ").trim()
            val rank = Regex("""^\s*(\d+)\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val installs = Regex("""\b(\d+(?:\.\d+)?)\s*([KMB])\b""", RegexOption.IGNORE_CASE)
                .findAll(text).lastOrNull()?.let { count ->
                    val factor = when (count.groupValues[2].uppercase()) { "K" -> 1_000L; "M" -> 1_000_000L; "B" -> 1_000_000_000L; else -> 1L }
                    (count.groupValues[1].toDouble() * factor).toLong()
                } ?: rank?.let { 1_000_000L - it }
            val source = "${parts[0]}/${parts[1]}"
            val githubSource = parts[0] != "site" && parts[1].contains('.') == false
            CatalogExtension(
                id = path,
                name = parts[2],
                description = rank?.let { "skills.sh 热门榜第 $it 位" }.orEmpty(),
                source = source,
                kind = ExtensionKind.SKILL,
                installUrl = if (githubSource) "https://github.com/$source" else null,
                homepage = "https://www.skills.sh/$path",
                installs = installs,
                auditSummary = "公开热榜 · 安装前仍需本地审查",
            )
        }.distinctBy(CatalogExtension::id).take(limit).toList()
    }

    private fun stageGitHubSkill(item: CatalogExtension): ExtensionInstallPlan {
        val parts = item.id.split('/')
        require(parts.size == 3 && item.installUrl?.startsWith("https://github.com/") == true) {
            "该 Skill 的公开条目没有可验证的 Git 源，暂时只能浏览"
        }
        val owner = parts[0]
        val repository = parts[1]
        val slug = parts[2]
        val repo = getJson("https://api.github.com/repos/$owner/$repository").jsonObject
        val branch = repo.stringAt("default_branch") ?: "main"
        val tree = getJson("https://api.github.com/repos/$owner/$repository/git/trees/${urlPart(branch)}?recursive=1").jsonObject
        val entries = tree.arrayAt("tree").mapNotNull { it as? JsonObject }
        val skillFile = entries.firstOrNull { entry ->
            entry.stringAt("type") == "blob" && entry.stringAt("path")?.let { path ->
                path == "SKILL.md" || path.endsWith("/$slug/SKILL.md")
            } == true
        } ?: error("Git 仓库中找不到 $slug/SKILL.md")
        val skillPath = skillFile.stringAt("path") ?: error("Skill 路径无效")
        val directory = skillPath.substringBeforeLast('/', "")
        val prefix = directory.takeIf(String::isNotBlank)?.plus('/') ?: ""
        val blobs = entries.filter { entry ->
            entry.stringAt("type") == "blob" && entry.stringAt("path")?.startsWith(prefix) == true
        }.take(64)
        val warnings = mutableListOf<String>()
        val permissions = mutableSetOf(ExtensionPermission.WORKSPACE_READ)
        var totalBytes = 0
        val files = blobs.map { entry ->
            val remotePath = entry.stringAt("path") ?: error("Git 文件路径无效")
            val relative = validateRelativePath(remotePath.removePrefix(prefix))
            val contents = getText(
                "https://raw.githubusercontent.com/$owner/$repository/${urlPath(branch)}/${urlPath(remotePath)}",
                "text/plain",
            )
            totalBytes += contents.encodeToByteArray().size
            require(totalBytes <= 2_000_000) { "Skill 快照超过 2 MB 安全上限" }
            scanCapabilities(relative, contents, permissions, warnings)
            StagedExtensionFile(relative, contents, sha256(contents.encodeToByteArray()))
        }
        require(files.any { it.path == "SKILL.md" }) { "Skill 快照中没有 SKILL.md" }
        if (blobs.size == 64) warnings += "仓库文件较多，仅暂存 Skill 目录前 64 个文件"
        val digest = sha256(files.sortedBy { it.path }.joinToString("\n") { "${it.path}:${it.digest}" }.encodeToByteArray())
        return ExtensionInstallPlan(
            id = item.id,
            name = item.name,
            version = branch,
            source = "https://github.com/$owner/$repository/tree/$branch/$directory",
            kind = ExtensionKind.SKILL,
            sourceDigest = digest,
            files = files,
            permissions = permissions,
            warnings = warnings.distinct(),
            safeToStage = warnings.none { it.startsWith("阻止") },
        )
    }

    private fun decodeHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun urlPart(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun urlPath(value: String): String = value.split('/').joinToString("/") { urlPart(it) }

    private fun validateRelativePath(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "空文件路径" }
        require(normalized.split('/').none { it == ".." || it.isBlank() }) { "阻止路径穿越：$raw" }
        require(!normalized.startsWith(".git/")) { "阻止写入 Git 元数据" }
        return normalized
    }

    private fun scanCapabilities(
        path: String,
        contents: String,
        permissions: MutableSet<ExtensionPermission>,
        warnings: MutableList<String>,
    ) {
        if (Regex("(?i)\\b(curl|wget|http[s]?://|fetch\\()") .containsMatchIn(contents)) {
            permissions += ExtensionPermission.NETWORK
            warnings += "$path 可能访问网络"
        }
        if (Regex("(?i)\\b(shell|bash|sh |powershell|exec|subprocess|child_process)\\b").containsMatchIn(contents)) {
            permissions += ExtensionPermission.SHELL
            warnings += "$path 可能执行命令"
        }
        if (Regex("(?i)(api[_ -]?key|token|secret|credential|authorization)").containsMatchIn(contents)) {
            permissions += ExtensionPermission.SECRETS
            warnings += "$path 提及凭据或令牌；启用前请逐行审查"
        }
        if (Regex("(?i)(write|patch|delete|remove|rename|move) (file|directory|workspace)").containsMatchIn(contents)) {
            permissions += ExtensionPermission.WORKSPACE_WRITE
        }
        if (contents.length > 1_000_000) warnings += "阻止超大文本文件：$path"
    }

    private fun JsonObject.stringAt(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.urlAt(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is JsonPrimitive -> value.contentOrNull
            is JsonObject -> value.stringAt("url", "href", "homepage")
            else -> null
        }
    }

    private fun JsonObject.arrayAt(vararg keys: String): JsonArray =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray } ?: JsonArray(emptyList())

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SAI_CATALOG_PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAoXTnJvN3uPC1QIGOeKUMh5kh7W68V1TzT9NFrLorr7c="
    }
}

class ExtensionInstaller(private val installRoot: File) {
    fun install(plan: ExtensionInstallPlan): File {
        require(plan.safeToStage) { "静态检查阻止安装" }
        val safeId = plan.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val version = plan.version.ifBlank { plan.sourceDigest.take(12) }
        val target = File(installRoot, "$safeId/$version").canonicalFile
        require(target.path.startsWith(installRoot.canonicalPath + File.separator)) { "安装路径越界" }
        target.mkdirs()
        plan.files.forEach { staged ->
            val file = File(target, staged.path).canonicalFile
            require(file.path.startsWith(target.path + File.separator)) { "扩展文件逃逸安装目录" }
            file.parentFile?.mkdirs()
            file.writeText(staged.contents)
        }
        return target
    }

    fun uninstall(plan: ExtensionInstallPlan): Boolean {
        val safeId = plan.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val version = plan.version.ifBlank { plan.sourceDigest.take(12) }
        val root = installRoot.canonicalFile
        val target = File(root, "$safeId/$version").canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "卸载路径越界" }
        val removed = !target.exists() || target.deleteRecursively()
        val parent = target.parentFile
        if (removed && parent?.list().isNullOrEmpty() && parent?.canonicalFile?.parentFile == root) parent.delete()
        return removed
    }
}

class CatalogAuthenticationException(message: String) : IllegalStateException(message)
