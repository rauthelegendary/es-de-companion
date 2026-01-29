package com.esde.companion.ui.contextmenu.scraper

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.esde.companion.art.MediaSearchResult
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ui.ContentType

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SaveMediaStep(
    media: MediaSearchResult,
    onSave: (String, ContentType, Int) -> Unit,
    isVideo: Boolean,
    mediaService: YoutubeMediaService
) {
    val selectableMediaTypes = listOf<ContentType>(
        ContentType.BOX_2D,
        ContentType.BOX_3D,
        ContentType.MARQUEE,
        ContentType.FANART,
        ContentType.SCREENSHOT,
        ContentType.MIX_IMAGE,
        ContentType.TITLE_SCREEN,
        ContentType.BACK_COVER,
        ContentType.PHYSICAL_MEDIA
    )
    var selectedType by remember { mutableStateOf(if(isVideo) ContentType.VIDEO else ContentType.BOX_2D) }
    var selectedSlot by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val playableUrlState = produceState<String?>(initialValue = null, media.url) {
        value = if (isVideo) {
            mediaService.handleIgdbMedia(media)
        } else {
            media.url
        }
    }

    val resolvedUrl = playableUrlState.value

    val exoPlayer = remember(resolvedUrl) {
        if (isVideo && resolvedUrl != null && resolvedUrl.startsWith("http")) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(resolvedUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0.7f
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                release()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier.fillMaxWidth().height(250.dp)
                .background(Color.DarkGray, RoundedCornerShape(8.dp))
        ) {
            if (isVideo) {
                if (resolvedUrl == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { view ->
                            view.player = null

                        }
                    )
                }
            } else {
                AsyncImage(
                    model = media.url,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            println("Image failed to load: ${state.result.throwable}")
                        }
                    }
                )
            }
        }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isVideo) {
                Text("SAVE AS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectableMediaTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.toDisplayName()) }
                        )
                    }
                }
            }

            Text("TARGET SLOT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { slot ->
                    FilterChip(
                        selected = selectedSlot == slot,
                        onClick = { selectedSlot = slot },
                        label = { Text("Alt $slot") }
                    )
                }
            }

            //a small summary to explain what we're doing
            val isEsDe = selectedSlot == 0
            val typeName = selectedType.toDisplayName()
            //TODO: no longer required but I'll leave it in for now in case I change my mind, doesn't affect anything anyway
            val summaryText = if (isEsDe) {
                "Image will overwrite the existing \"$typeName\" within ES-DE. This will affect both ES-DE and the companion app. Are you sure?"
            } else {
                "Image will be saved as \"$typeName\" in the \"Alt $selectedSlot\" slot. Any existing image in that slot will be overwritten."
            }

            Text(
                text = summaryText,
                color = if (isEsDe) Color(0xFFEF5350) else Color.White, // Red for ES-DE warning
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Button(
                onClick = {
                    exoPlayer?.stop()
                    onSave(resolvedUrl!!, selectedType, selectedSlot)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEsDe) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("CONFIRM AND SAVE")
            }
        }
    }