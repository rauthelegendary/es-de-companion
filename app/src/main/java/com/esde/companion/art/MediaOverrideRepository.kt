package com.esde.companion.art

class MediaOverrideRepository(private val mediaOverrideDao: MediaOverrideDao) {
    private val overrideCache = HashMap<String, MediaOverride>()

    fun initialize() {
        val all = mediaOverrideDao.getAllOverrides()
        all.forEach {
            overrideCache["${it.filePath}_${it.contentType}"] = it
        }
    }

    fun getOverride(gamePath: String, contentType: String): MediaOverride? {
        return overrideCache["${gamePath}_$contentType"]
    }

    suspend fun updateOverride(override: MediaOverride) {
        mediaOverrideDao.saveOverride(override)
        overrideCache["${override.filePath}_${override.contentType}"] = override
    }

    suspend fun removeOverride(override: MediaOverride) {
        mediaOverrideDao.deleteOverride(override.filePath, override.contentType.name)
        overrideCache.remove("${override.filePath}_${override.contentType}")
    }
}