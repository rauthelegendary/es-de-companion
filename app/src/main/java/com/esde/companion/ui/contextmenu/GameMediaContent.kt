package com.esde.companion.ui.contextmenu

import android.R.attr.value
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.esde.companion.managers.MediaManager
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.art.mediaoverride.MediaOverride
import com.esde.companion.art.mediaoverride.MediaOverrideKey
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.ui.ContentType
import org.schabi.newpipe.extractor.timeago.patterns.it
import java.io.File
import java.net.URI

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameMediaContent(
    game: String,
    system: String,
    mediaManager: MediaManager,
    mediaOverrideRepository: MediaOverrideRepository,
    onSaveOverride: (MediaOverride) -> Unit,
    onRemoveOverride: (MediaOverride) -> Unit,
    onCropSave: (File, Bitmap) -> Unit,
    removeCrop: (File) -> Unit,
    swapMedia: (String, ContentType, String, MediaSlot, MediaSlot) -> Unit,
    deleteMedia: (String, ContentType, String, MediaSlot) -> Unit,
    setManualFileForSlot: (Uri, ContentType, String, String, MediaSlot, () -> Unit) -> Unit
) {
    var selectedType by remember { mutableStateOf(ContentType.BOX_2D) }
    var selectedSlot by remember { mutableStateOf(MediaSlot.Default) }

    var activeOverride by remember { mutableStateOf<MediaOverride?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var refreshKey by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()


    LaunchedEffect(game, selectedType, refreshKey) {
        activeOverride = mediaOverrideRepository.getOverride(game, system, selectedType)
    }

    var availableSlots by remember { mutableStateOf(emptyList<Pair<MediaSlot, Boolean>>()) }

    LaunchedEffect(game, system, selectedType, refreshKey) {
        availableSlots = MediaSlot.entries.map { slot ->
            slot to (mediaManager.findMediaFile(selectedType, system, game, slot)?.exists() == true)
        }
    }

    val visibleTypes = remember {
        ContentType.entries.filter {
            it.hasAltSlots()
        }
    }

    val selectedIndex = remember(selectedType, visibleTypes) {
        visibleTypes.indexOf(selectedType).coerceAtLeast(0)
    }

    val slotFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {it ->
            setManualFileForSlot(it, selectedType, game, system, selectedSlot) {
                refreshKey++
            }
        }
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
                .verticalScroll(scrollState)
                .padding(0.dp)
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

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // --- LEFT COLUMN (CONTROLS) ---
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Select slot",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )

                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSlots.forEach { (slot, exists) ->
                            val isDefault = activeOverride?.altSlot == slot
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = { Text(slot.name) },
                                trailingIcon = {
                                    if (isDefault) {
                                        Icon(Icons.Default.Star, contentDescription = "Default", modifier = Modifier.size(14.dp))
                                    } else if (exists) {
                                        Icon(Icons.Default.Circle, contentDescription = "Has file", modifier = Modifier.size(10.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CompactPreview(
                        game = game, system = system, type = selectedType,
                        slot = selectedSlot, mediaManager = mediaManager,
                        refreshKey = refreshKey,
                        onRefreshRequest = { refreshKey++ }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(thickness = 1.dp, color = Color.Gray.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {

                val selectedSlotHasFile = availableSlots.find { it.first == selectedSlot }?.second == true
                SlotManagementActions(
                    slot = selectedSlot,
                    onDelete = {showDeleteConfirm = true},
                    onMove = { targetSlot ->
                        swapMedia(game, selectedType, system, selectedSlot, targetSlot)
                        selectedSlot = targetSlot
                        refreshKey++
                    },
                    hasFile = selectedSlotHasFile
                )
                PreviewActions(
                    game = game, system = system, type = selectedType,
                    slot = selectedSlot, mediaManager = mediaManager,
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
                    onRefreshRequest = { refreshKey++ },
                    onManualFile = { slotFilePicker.launch(arrayOf("image/*", "video/*")) }
                )
            }
        }
        Spacer(Modifier.height(40.dp))

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
    mediaManager: MediaManager,
    refreshKey: Int,
    onRefreshRequest: () -> Unit
) {
    val file = remember(game, system, type, slot, refreshKey) { mediaManager.findMediaFile(type, system, game, slot) }
    val isVideo = remember(file) { mediaManager.isVideo(file) }

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
        }
    }
    
    LaunchedEffect(file, refreshKey) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
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

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (isVideo) exoPlayer.play()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                            .allowHardware(true)
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
    mediaManager: MediaManager,
    activeOverride: MediaOverride?,
    onSetDefault: (MediaOverride) -> Unit,
    onRemoveOverride: (MediaOverride) -> Unit,
    onCrop: (File) -> Unit,
    removeCrop: (File) -> Unit,
    refreshKey: Int,
    onRefreshRequest: () -> Unit,
    onManualFile: () -> Unit
) {
    val file = remember(game, type, slot, refreshKey) { mediaManager.findMediaFileDefault(type, system, game, slot) }
    val isVideo = mediaManager.isVideo(file)
    val isCurrentDefault = activeOverride?.altSlot == slot
    val croppedFile = remember(file) {
        file?.let { File(it.parent, "${it.nameWithoutExtension}_cropped.png") }
    }
    var hasCrop = croppedFile?.exists() == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (file != null) {
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
                    onClick = {
                        onSetDefault(
                            MediaOverride(
                                MediaOverrideKey(game, system, type),
                                slot
                            )
                        )
                    },
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
        if(slot != MediaSlot.Default) {
            Button(
                onClick = { onManualFile() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("Set file manually")
            }
        }
    }
}

@Composable
fun SlotManagementActions(
    slot: MediaSlot,
    onDelete: () -> Unit,
    onMove: (MediaSlot) -> Unit,
    hasFile: Boolean
) {
    if (slot == MediaSlot.Default || !hasFile) return

    var showMoveMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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