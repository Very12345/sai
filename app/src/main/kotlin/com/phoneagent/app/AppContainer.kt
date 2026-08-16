package com.phoneagent.app

import android.app.Application
import android.content.Context
import com.phoneagent.data.PhoneAgentDatabase
import com.phoneagent.data.ProviderEntity
import com.phoneagent.data.SecretStore
import com.phoneagent.provider.ProviderCredential
import com.phoneagent.provider.ProviderPresets
import com.phoneagent.provider.ProviderProfile
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RootfsInstallerFactory
import com.phoneagent.runtime.BundledGitHubCli
import com.phoneagent.dsh.DshRuntimeProvisioner
import com.phoneagent.dsh.DshRuntimeSupervisor
import com.phoneagent.dsh.DshApiClient
import com.phoneagent.dsh.DshHarnessAdapter
import com.phoneagent.harness.HarnessRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class AppContainer(
    val application: Application,
    val database: PhoneAgentDatabase,
    val secretStore: SecretStore,
    val runtime: LinuxRuntime,
    val workspace: File,
) {
    private val dshWorkspaceRoot: File = workspace.parentFile ?: workspace
    val projectsRoot: File = File(application.filesDir, "workspaces/Project").apply { mkdirs() }
    val rootfsInstaller = RootfsInstallerFactory.create(application)
    val githubCli = GitHubCliManager(
        BundledGitHubCli(application, rootfsInstaller.rootfsDir),
        runtime,
        secretStore,
        workspace,
        File(application.cacheDir, "github-auth"),
    )
    val providerSettings = ProviderSettingsRepository(application, secretStore, database)
    val dshBridge = SaiDshBridgeServer(application, githubCli, providerSettings)
    val dshProvisioner = DshRuntimeProvisioner(application)
    val dshSettings = DshSettingsSynchronizer(providerSettings, database, dshProvisioner)
    val dshExtensions = DshExtensionSynchronizer(application, database, dshProvisioner)
    val legacyDshMigration = LegacyDshMigrationWriter(database, dshProvisioner, dshWorkspaceRoot)
    val dshRuntime = DshRuntimeSupervisor(
        runtime,
        dshProvisioner,
        dshWorkspaceRoot,
        dshBridge::endpoint,
        prepareConfiguration = {
            dshSettings.syncNow()
            dshExtensions.syncNow()
            legacyDshMigration.writePendingMigration()
        },
    )
    val dshApi = DshApiClient(state = { dshRuntime.state.value })
    val dshHarnessAdapter by lazy { DshHarnessAdapter(dshRuntime, dshApi) }
    val harnessRegistry by lazy { HarnessRegistry(listOf(dshHarnessAdapter)) }
    val bundledCliHarnesses by lazy {
        BundledCliHarnessController(runtime, dshProvisioner, providerSettings, File(application.filesDir, "harness-history"))
    }
    val harnessWebRuntime by lazy {
        HarnessWebRuntimeSupervisor(runtime, dshProvisioner, dshWorkspaceRoot) {
            val profile = providerSettings.profile.value
            profile to providerSettings.credentialFor(profile.id)?.apiKey?.toCharArray()
        }
    }
    val managerHarness by lazy { SaiManagerHarness(this) }
    val desktopConnection by lazy { DesktopConnectionManager(application, this) }
}

class ProviderSettingsRepository(
    context: Context,
    private val secrets: SecretStore,
    private val database: PhoneAgentDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val preferences = context.getSharedPreferences("provider_settings", Context.MODE_PRIVATE)
    private val initialProfiles = loadProfiles()
    private val _profiles = MutableStateFlow(initialProfiles)
    val profiles: StateFlow<List<ProviderProfile>> = _profiles.asStateFlow()
    private val _profile = MutableStateFlow(loadActiveProfile(initialProfiles))
    val profile: StateFlow<ProviderProfile> = _profile.asStateFlow()

    fun hasCredential(): Boolean = secrets.contains(secretAlias(_profile.value.id))

    fun credential(): ProviderCredential? = credentialFor(_profile.value.id)

    fun credentialFor(providerId: String): ProviderCredential? {
        val chars = secrets.get(secretAlias(providerId)) ?: return null
        return try { ProviderCredential(chars.concatToString()) } finally { chars.fill('\u0000') }
    }

    fun profileFor(providerId: String): ProviderProfile? = _profiles.value.firstOrNull { it.id == providerId }

    fun credentialRefForProvider(providerId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(providerId.toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { "%02X".format(it) }
        return "SAI_PROVIDER_$digest"
    }

    fun resolveCredentialReference(ref: String): CharArray? {
        require(CREDENTIAL_REF.matches(ref)) { "Invalid credential reference" }
        val provider = _profiles.value.firstOrNull { credentialRefForProvider(it.id) == ref }
        return secrets.get(provider?.let { secretAlias(it.id) } ?: dshSecretAlias(ref))
    }

    fun hasCredentialReference(ref: String): Boolean {
        require(CREDENTIAL_REF.matches(ref)) { "Invalid credential reference" }
        val provider = _profiles.value.firstOrNull { credentialRefForProvider(it.id) == ref }
        return secrets.contains(provider?.let { secretAlias(it.id) } ?: dshSecretAlias(ref))
    }

    fun putCredentialReference(ref: String, value: CharArray) {
        require(CREDENTIAL_REF.matches(ref)) { "Invalid credential reference" }
        val provider = _profiles.value.firstOrNull { credentialRefForProvider(it.id) == ref }
        secrets.put(provider?.let { secretAlias(it.id) } ?: dshSecretAlias(ref), value)
    }

    fun removeCredentialReference(ref: String) {
        require(CREDENTIAL_REF.matches(ref)) { "Invalid credential reference" }
        val provider = _profiles.value.firstOrNull { credentialRefForProvider(it.id) == ref }
        secrets.remove(provider?.let { secretAlias(it.id) } ?: dshSecretAlias(ref))
    }

    suspend fun save(profile: ProviderProfile, apiKey: CharArray?) {
        val encoded = json.encodeToString(profile)
        val updated = (_profiles.value.filterNot { it.id == profile.id } + profile)
            .sortedBy { it.displayName.lowercase() }
        check(preferences.edit()
            .putString("active_profile", encoded)
            .putString("active_provider_id", profile.id)
            .putString("provider_profiles", json.encodeToString(ListSerializer(ProviderProfile.serializer()), updated))
            .commit())
        if (apiKey != null && apiKey.isNotEmpty()) secrets.put(secretAlias(profile.id), apiKey)
        _profiles.value = updated
        _profile.value = profile
        database.dao().upsertProvider(ProviderEntity(profile.id, encoded, secretAlias(profile.id)))
    }

    fun select(providerId: String): ProviderProfile? {
        val selected = _profiles.value.firstOrNull { it.id == providerId } ?: return null
        preferences.edit().putString("active_provider_id", providerId).apply()
        _profile.value = selected
        return selected
    }

    suspend fun delete(providerId: String): Boolean {
        if (_profiles.value.size <= 1) return false
        val updated = _profiles.value.filterNot { it.id == providerId }
        if (updated.size == _profiles.value.size) return false
        secrets.remove(secretAlias(providerId))
        database.dao().deleteProvider(providerId)
        _profiles.value = updated
        val next = if (_profile.value.id == providerId) updated.first() else _profile.value
        _profile.value = next
        preferences.edit()
            .putString("provider_profiles", json.encodeToString(ListSerializer(ProviderProfile.serializer()), updated))
            .putString("active_provider_id", next.id)
            .putString("active_profile", json.encodeToString(next))
            .apply()
        return true
    }

    private fun loadProfiles(): List<ProviderProfile> {
        val stored = preferences.getString("provider_profiles", null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(ProviderProfile.serializer()), raw) }.getOrNull()
        }.orEmpty().map(::refreshOfficialPricing)
        if (stored.isNotEmpty()) return stored.distinctBy(ProviderProfile::id)
        val legacy = preferences.getString("active_profile", null)?.let {
            runCatching { json.decodeFromString<ProviderProfile>(it) }.getOrNull()
        }?.let(::refreshOfficialPricing)
        return listOf(legacy ?: ProviderPresets.all.first())
    }

    private fun loadActiveProfile(profiles: List<ProviderProfile>): ProviderProfile =
        profiles.firstOrNull { it.id == preferences.getString("active_provider_id", null) }
            ?: preferences.getString("active_profile", null)?.let {
        runCatching { json.decodeFromString<ProviderProfile>(it) }.getOrNull()
    }?.let(::refreshOfficialPricing)?.let { legacy -> profiles.firstOrNull { it.id == legacy.id } }
            ?: profiles.first()

    private fun refreshOfficialPricing(profile: ProviderProfile): ProviderProfile {
        if (profile.id != "deepseek") return profile
        val official = ProviderPresets.all.first { it.id == "deepseek" }
        // Saved profiles from older PhoneAgent builds carried the former USD table.
        // Preserve endpoint/model/user headers while refreshing only the official rate card.
        return profile.copy(modelPricing = official.modelPricing)
    }

    private fun secretAlias(id: String) = "provider:$id"
    private fun dshSecretAlias(ref: String) = "dsh-credential:$ref"

    private companion object {
        val CREDENTIAL_REF = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
