package com.esde.companion.art

import com.esde.companion.managers.MediaManager
import com.esde.companion.data.Widget
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.ui.ContentType
import java.io.File

class MediaService (
    private val mediaOverrideRepository: MediaOverrideRepository,
    private val mediaManager: MediaManager
) {

    suspend fun deleteMedia(game: String, type: ContentType, system: String, slot: Widget.MediaSlot) {
        if(slot.index != 0) {
            val file = mediaManager.findMediaFileDefault(type, system, game, slot)

            file?.let {
                val croppedFile = mediaManager.findMediaFile(type, system, game, slot)
                if (croppedFile?.exists() == true && croppedFile.name != file.name) croppedFile.delete()

                if (it.exists()) it.delete()
            }

            val currentOverride = mediaOverrideRepository.getOverride(game, system, type)
            if (currentOverride?.altSlot == slot) {
                mediaOverrideRepository.removeOverride(game, system, type)
            }
        }
    }

    /**
     * Swaps media assets between two slots
     */
    suspend fun swapMedia(game: String, type: ContentType, system: String, sourceSlot: Widget.MediaSlot, targetSlot: Widget.MediaSlot) {
        val sourceFile = mediaManager.findMediaFileDefault(type, system, game, sourceSlot) ?: mediaManager.getPotentialFile(type, system, game, sourceSlot)
        val targetFile = mediaManager.findMediaFileDefault(type, system, game, targetSlot) ?: mediaManager.getPotentialFile(type, system, game, targetSlot)

        swapPhysicalFiles(sourceFile, targetFile)

        val sourceCrop = sourceFile?.let {
            File(
                it.parent,
                "${it.nameWithoutExtension}_cropped.png"
            )
        }
        val targetCrop = targetFile?.let {
            File(
                it.parent,
                "${it.nameWithoutExtension}_cropped.png"
            )
        }
        swapPhysicalFiles(sourceCrop, targetCrop)

        val currentOverride = mediaOverrideRepository.getOverride(game, system, type)
        if (currentOverride?.altSlot == sourceSlot) {
            mediaOverrideRepository.updateOverride(game, system, type, targetSlot)
        } else if (currentOverride?.altSlot == targetSlot) {
            mediaOverrideRepository.updateOverride(game, system, type, sourceSlot)
        }
    }

    private fun swapPhysicalFiles(f1: File?, f2: File?) {
        val temp = File(f1?.parent, "swap_temp_${System.currentTimeMillis()}")

        val f1Exists = f1?.exists() == true
        val f2Exists = f2?.exists() == true

        if (f1Exists) f1?.renameTo(temp)
        if (f2Exists) f2?.renameTo(f1!!)
        if (f1Exists) temp.renameTo(f2!!)
    }
}