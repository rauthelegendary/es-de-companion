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

    suspend fun swapMedia(game: String, type: ContentType, system: String, sourceSlot: Widget.MediaSlot, targetSlot: Widget.MediaSlot) {
        val sourceFile = mediaManager.findMediaFileDefault(type, system, game, sourceSlot)
        val targetFile = mediaManager.findMediaFileDefault(type, system, game, targetSlot)

        val sourcePotential = sourceFile
            ?: mediaManager.getPotentialFile(type, system, game, sourceSlot, existingFile = targetFile)
        val targetPotential = targetFile
            ?: mediaManager.getPotentialFile(type, system, game, targetSlot, existingFile = sourceFile)

        val finalSource = if (sourceFile != null && targetFile != null && sourceFile.extension != targetFile.extension) {
            File(sourceFile.parent, "${sourceFile.nameWithoutExtension}.${targetFile.extension}")
        } else sourcePotential

        val finalTarget = if (sourceFile != null && targetFile != null && sourceFile.extension != targetFile.extension) {
            File(targetFile.parent, "${targetFile.nameWithoutExtension}.${sourceFile.extension}")
        } else targetPotential

        swapPhysicalFiles(sourcePotential, targetPotential, finalSource, finalTarget)

        val sourceCrop = sourcePotential?.let { File(it.parent, "${it.nameWithoutExtension}_cropped.png") }
        val targetCrop = targetPotential?.let { File(it.parent, "${it.nameWithoutExtension}_cropped.png") }
        swapPhysicalFiles(sourceCrop, targetCrop, sourceCrop, targetCrop)

        val currentOverride = mediaOverrideRepository.getOverride(game, system, type)
        if (currentOverride?.altSlot == sourceSlot) {
            mediaOverrideRepository.updateOverride(game, system, type, targetSlot)
        } else if (currentOverride?.altSlot == targetSlot) {
            mediaOverrideRepository.updateOverride(game, system, type, sourceSlot)
        }
    }

    private fun swapPhysicalFiles(f1: File?, f2: File?, f1Target: File? = f1, f2Target: File? = f2) {
        val f1Exists = f1?.exists() == true
        val f2Exists = f2?.exists() == true
        if (!f1Exists && !f2Exists) return

        val temp = File(f1?.parent, "swap_temp_${System.currentTimeMillis()}")

        if (f1Exists) f1?.renameTo(temp)
        if (f2Exists) f2?.renameTo(f1Target!!)
        if (f1Exists) temp.renameTo(f2Target!!)
    }
}