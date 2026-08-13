package com.phoneagent.provider

import kotlinx.coroutines.flow.Flow

interface ProviderAdapter {
    val profile: ProviderProfile

    suspend fun probe(credential: ProviderCredential): ProviderProbe

    suspend fun listModels(credential: ProviderCredential): List<ModelInfo>

    fun stream(request: ModelRequest, credential: ProviderCredential): Flow<ModelEvent>
}

class ProviderRegistry(adapters: Collection<ProviderAdapter> = emptyList()) {
    private val adapters = linkedMapOf<String, ProviderAdapter>()

    init {
        adapters.forEach(::register)
    }

    @Synchronized
    fun register(adapter: ProviderAdapter) {
        adapters[adapter.profile.id] = adapter
    }

    @Synchronized
    fun remove(id: String) {
        adapters.remove(id)
    }

    @Synchronized
    fun get(id: String): ProviderAdapter =
        requireNotNull(adapters[id]) { "Unknown provider profile: $id" }

    @Synchronized
    fun all(): List<ProviderAdapter> = adapters.values.toList()
}

