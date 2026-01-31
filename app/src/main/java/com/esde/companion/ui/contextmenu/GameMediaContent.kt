package com.esde.companion.ui.contextmenu

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.esde.companion.MediaFileLocator
import com.esde.companion.OverlayWidget.MediaSlot
import com.esde.companion.art.MediaOverride
import com.esde.companion.art.MediaOverrideRepository
import com.esde.companion.ui.ContentType
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameMediaContent(
    game: String,
    system: String,
    mediaFileLocator: MediaFileLocator,
    mediaOverrideRepository: MediaOverrideRepository,
    onSaveOverride: (MediaOverride) -> Unit,
    onRemoveOverride: (MediaOverride) -> Unit,
    onCropSave: (File, Bitmap) -> Unit,
    removeCrop: (File) -> Unit,
    swapMedia: (String, ContentType, String, MediaSlot, MediaSlot) -> Unit,
    deleteMedia: (String, ContentType, String, MediaSlot) -> Unit
) {
    var selectedType by remember { mutableStateOf(ContentType.BOX_2D) }
    var selectedSlot by remember { mutableStateOf(MediaSlot.Default) }

    var activeOverride by remember { mutableStateOf<MediaOverride?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(game, selectedType, refreshKey) {
        activeOverride = mediaOverrideRepository.getOverride(game, selectedType.name)
    }

    val availableSlots = remember(game, system, selectedType, refreshKey) {
        MediaSlot.entries.map { slot ->
            slot to (mediaFileLocator.findMediaFile(selectedType, system, game, slot)
                ?.exists() == true)
        }
    }

    val visibleTypes = remember {
        ContentType.entries.filter {
            it != ContentType.GAME_DESCRIPTION && it != ContentType.SYSTEM_LOGO
        }
    }

    val selectedIndex = remember(selectedType, visibleTypes) {
        visibleTypes.indexOf(selectedType).coerceAtLeast(0)
    }

    var fileToCrop by remember { mutableStateOf<File?>(null) }

    if (fileToCrop != null) {
        ImageCropperScreen(
            imageUri = Uri.fromFile(fileToCrop!!),
            onCancel = { fileToCrop = null },
            onCropSuccess = { bitmap ->
                onCropSave(fileToCrop!!, bitmap)
                fileToCrop = null
                refreshKey++
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent
            ) {
                visibleTypes.forEach { type ->
                    Tab(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        text = { Text(type.name.replace("_", " ")) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // --- LEFT COLUMN (CONTROLS) ---
                Column(modifier = Modifier.weight(1f)) {
                    Text("Select slot", style = MaterialTheme.typography.labelLarge, color = Color.Gray)

                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSlots.forEach { (slot, exists) ->
                            val isDefault = activeOverride?.altSlot == slot
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = {
                                    Text(slot.name)
                                    if (isDefault) {
                                        Box(
                                            Modifier.padding(start = 4.dp).size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                },
                                enabled = exists
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider(thickness = 1.dp, color = Color.Gray.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))

                    SlotManagementActions(
                        slot = selectedSlot,
                        onDelete = {showDeleteConfirm = true},
                        onMove = { targetSlot ->
                            swapMedia(game, selectedType, system, selectedSlot, targetSlot)
                            selectedSlot = targetSlot
                            refreshKey++
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    PreviewActions(
                        game = game, system = system, type = selectedType,
                        slot = selectedSlot, mediaFileLocator = mediaFileLocator,
                        activeOverride = activeOverride,
                        onSetDefault = { override ->
                            activeOverride = override; onSaveOverride(
                            override
                        )
                        },
                        onRemoveOverride = { override ->
                            activeOverride = null; onRemoveOverride(
                            override
                        )
                        },
                        onCrop = { file -> fileToCrop = file },
                        removeCrop = removeCrop,
                        refreshKey = refreshKey,
                        onRefreshRequest = { refreshKey++ }
                    )
                }

                Column(
                    modifier = Modifier.width(350.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CompactPreview(
                        game = game, system = system, type = selectedType,
                        slot = selectedSlot, mediaFileLocator = mediaFileLocator,
                        refreshKey = refreshKey,
                        onRefreshRequest = { refreshKey++ }
                    )
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning", // Good for accessibility
                    tint = MaterialTheme.colorScheme.error
                )},
                title = { Text("Delete Media?") },
                text = {
                    Text("This will permanently remove the media file, any cropped versions, and its default status for this game.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteMedia(game, selectedType, system, selectedSlot)
                            showDeleteConfirm = false
                            refreshKey++
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CompactPreview(
    game: String,
    system: String,
    type: ContentType,
    slot: MediaSlot,
    mediaFileLocator: MediaFileLocator,
    refreshKey: Int,
    onRefreshRequest: () -> Unit
) {
    val file = remember(game, system, type, slot, refreshKey) { mediaFileLocator.findMediaFile(type, system, game, slot) }
    val isVideo = file?.path?.let { it.endsWith(".mp4") || it.endsWith(".mkv") } ?: false

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
        }
    }
    LaunchedEffect(file) {
        val isVideo = file?.path?.let { it.endsWith(".mp4") || it.endsWith(".mkv") } ?: false
        if(isVideo) {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier.width(400.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(350.dp, 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (file?.exists() == true) {
                if (isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                player?.volume = 0.6f
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { view ->
                            view.player = null
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .allowHardware(false)
                            .crossfade(true)
                            .memoryCacheKey("${file.absolutePath}_${file.lastModified()}")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text("Empty", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun PreviewActions(
    game: String,
    system: String,
    type: ContentType,
    slot: MediaSlot,
    mediaFileLocator: MediaFileLocator,
    activeOverride: MediaOverride?,
    onSetDefault: (MediaOverride) -> Unit,
    onRemoveOverride: (MediaOverride) -> Unit,
    onCrop: (File) -> Unit,
    removeCrop: (File) -> Unit,
    refreshKey: Int,
    onRefreshRequest: () -> Unit
) {
    val file = remember(game, type, slot, refreshKey) { mediaFileLocator.findMediaFileDefault(type, system, game, slot) }
    val isVideo = file?.path?.let { it.endsWith(".mp4") || it.endsWith(".mkv") } ?: false
    val isCurrentDefault = activeOverride?.altSlot == slot
    val croppedFile = remember(file) {
        file?.let { File(it.parent, "${it.nameWithoutExtension}_cropped.png") }
    }
    var hasCrop = croppedFile?.exists() == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCurrentDefault) {
            Button(
                onClick = { onRemoveOverride(activeOverride!!) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Remove Default")
            }
        } else {
            Button(
                onClick = { onSetDefault(MediaOverride(game, type, slot)) },
                modifier = Modifier.weight(1f),
                enabled = slot != MediaSlot.Default && file?.exists() == true
            ) {
                Icon(Icons.Default.Star, null)
                Spacer(Modifier.width(8.dp))
                Text("Set Default")
            }
        }

        if (!hasCrop && !isVideo && slot != MediaSlot.Default && file?.exists() == true) {
            OutlinedButton(
                onClick = { onCrop(file) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Crop, null)
                Spacer(Modifier.width(8.dp))
                Text("Crop")
            }
        } else if (hasCrop) {
            OutlinedButton(
                onClick = {
                    removeCrop(croppedFile!!)
                    onRefreshRequest()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Remove Crop")
                Spacer(Modifier.width(8.dp))
                Text("Remove Crop")
            }
        }
    }
}

@Composable
fun SlotManagementActions(
    slot: MediaSlot,
    onDelete: () -> Unit,
    onMove: (MediaSlot) -> Unit
) {
    if (slot == MediaSlot.Default) return

    var showMoveMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text("Delete File")
        }

        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = { showMoveMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DriveFileMove, null)
                Spacer(Modifier.width(8.dp))
                Text("Move To...")
            }

            DropdownMenu(
                expanded = showMoveMenu,
                onDismissRequest = { showMoveMenu = false }
            ) {
                MediaSlot.entries.filter { it != slot && it != MediaSlot.Default }.forEach { targetSlot ->
                    DropdownMenuItem(
                        text = { Text("Slot ${targetSlot.name}") },
                        onClick = {
                            onMove(targetSlot)
                            showMoveMenu = false
                        }
                    )
                }
            }
        }
    }
}