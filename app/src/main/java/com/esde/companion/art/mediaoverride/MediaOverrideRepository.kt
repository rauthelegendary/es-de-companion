package com.esde.companion.art.mediaoverride

import com.esde.companion.MediaFileHelper
import com.esde.companion.data.Widget
import com.esde.companion.ui.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaOverrideRepository(private val mediaOverrideDao: MediaOverrideDao) {
    private val overrideCache = HashMap<MediaOverrideKey, MediaOverride>()

    fun initialize() {
        val all = mediaOverrideDao.getAllOverrides()
        synchronized(overrideCache) {
            all.forEach {
                overrideCache[it.key] = it
            }
        }
    }

    private fun createKey(pathOrName: String, system: String, type: ContentType): MediaOverrideKey {
        return MediaOverrideKey(getCleanFileName(pathOrName), system, type)
    }

    private fun getCleanFileName(pathOrName: String):String {
        return MediaFileHelper.extractGameFilenameWithoutExtension(pathOrName)
    }

    fun getOverride(path: String, system: String, type: ContentType): MediaOverride? {
        return synchronized(overrideCache) {
            overrideCache[createKey(path, system, type)]
        }
    }

    suspend fun updateOverride(override: MediaOverride) = withContext(Dispatchers.IO) {
        updateOverride(override.key.filePath, override.key.system, override.key.contentType, override.altSlot)
    }

    suspend fun updateOverride(
        path: String,
        system: String,
        type: ContentType,
        slot: Widget.MediaSlot
    ) = withContext(Dispatchers.IO) {
        val key = createKey(path, system, type)
        val override = MediaOverride(key, slot)
        mediaOverrideDao.saveOverride(override)
        synchronized(overrideCache) {
            overrideCache[key] = override
        }
    }

    suspend fun removeOverride(override: MediaOverride) = withContext(Dispatchers.IO) {
        removeOverride(override.key.filePath, override.key.system, override.key.contentType)
    }

    suspend fun removeOverride(path: String, system: String, type: ContentType) = withContext(Dispatchers.IO) {
        val key = createKey(path, system, type)
        mediaOverrideDao.deleteOverride(key.filePath, key.system, key.contentType.name)
        synchronized(overrideCache) {
            overrideCache.remove(key)
        }
    }
}
