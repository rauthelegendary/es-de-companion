package com.esde.companion.ui.contextmenu

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.ost.YoutubeMediaService
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun MusicMenuContent(
    initialQuery: String,
    results: List<StreamInfoItem>,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onVideoSelected: (StreamInfoItem, (Float) -> Unit) -> Unit,
    mediaService: YoutubeMediaService
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf(initialQuery) }

    var playingItem by remember { mutableStateOf<StreamInfoItem?>(null) }
    var resolvedPreviewUrl by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(playingItem) {
        if (playingItem != null) {
            resolvedPreviewUrl = null
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val url = mediaService.getPlayableAudioUrl(playingItem!!.url)
            resolvedPreviewUrl = url

            url?.let {
                exoPlayer.setMediaItem(MediaItem.fromUri(it))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search YouTube...", color = Color.Gray) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSearch(searchQuery) }) {
                Text("🔍")
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { item ->
                val isCurrent = playingItem?.url == item.url
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF444444) else Color(0xFF333333))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .padding(10.dp)
                            .clickable { playingItem = item },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if(isCurrent) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Cyan.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.duration} - ${item.uploaderName}",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isCurrent) {
                        if (resolvedPreviewUrl == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Column {
                                    MusicPreviewControls(exoPlayer)
                                }


                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clickable {
                                            isDownloading = true
                                            downloadProgress = 0f

                                            onVideoSelected(item) { progress ->
                                                downloadProgress = progress
                                                isDownloading = progress < 1.0f && progress > 0f
                                            }
                                        }
                                        .background(Color.Cyan.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            progress = downloadProgress,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.Cyan
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${(downloadProgress * 100).toInt()}%",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    } else {
                                        Text(
                                            text = "SAVE FOR GAME",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.Cyan,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            tint = Color.Cyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun MusicPreviewControls(player: ExoPlayer) {
    var sliderPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var isUserScrubbing by remember { mutableStateOf(false) }


    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserScrubbing) {
        if (isPlaying && !isUserScrubbing) {
            while (true) {
                sliderPosition = player.currentPosition
                delay(200)
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderPosition), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        Slider(
            value = sliderPosition.toFloat(),
            onValueChange = {
                isUserScrubbing = true
                sliderPosition = it.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(sliderPosition)
                isUserScrubbing = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )

        // Center Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.seekTo(player.currentPosition - 10000) }) {
                Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White)
            }

            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Cyan,
                    contentDescription = null
                )
            }

            IconButton(onClick = { player.seekTo(player.currentPosition + 10000) }) {
                Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}