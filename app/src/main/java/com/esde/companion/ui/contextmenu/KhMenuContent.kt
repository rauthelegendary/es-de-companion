package com.esde.companion.ui.contextmenu

import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.ost.khinsider.KhAlbum
import com.esde.companion.ost.khinsider.KhSong

@Composable
fun KhMenuContent(
    initialQuery: String,
    isLoading: Boolean,
    onSearch: (String, Int) -> Unit,
    onAlbumSelected: (KhAlbum) -> Unit,
    albumResults: List<KhAlbum>?,
    songResults: List<KhSong>?,
    onBackToAlbums: () -> Unit,
    onPlaySong: (KhSong, (String?) -> Unit) -> Unit,
    onSaveSong: (KhSong, String, (Float) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedType by remember { mutableIntStateOf(1) }

    // Playback State
    var playingSong by remember { mutableStateOf<KhSong?>(null) }
    var resolvedAudioUrl by remember { mutableStateOf<String?>(null) }

    // Download State
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    // ExoPlayer Setup
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(playingSong) {
        if (playingSong != null) {
            resolvedAudioUrl = null
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            onPlaySong(playingSong!!) { url ->
                resolvedAudioUrl = url
                url?.let {
                    val mediaItem = MediaItem.fromUri(Uri.parse(it))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.volume = 0.6f
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
    }

    LaunchedEffect(songResults, albumResults) {
        listState.scrollToItem(0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (songResults != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    onBackToAlbums()
                    exoPlayer.stop()
                    playingSong = null
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Album Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Search KHInsider...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Cyan,
                            cursorColor = Color.Cyan,
                            focusedLabelColor = Color.Cyan,
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    // Search Button - Now passes the selectedType
                    Button(
                        onClick = { onSearch(searchQuery, selectedType) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔍", color = Color.Black)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Type Selection Row (Segmented Control style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TypeToggleButton("Soundtracks", isSelected = selectedType == 1) { selectedType = 1 }
                    Spacer(Modifier.width(8.dp))
                    TypeToggleButton("Game Rips", isSelected = selectedType == 2) { selectedType = 2 }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.Cyan)
        }

        LazyColumn(modifier = Modifier.weight(1f), state = listState) {

            if (songResults != null) {
                items(songResults) { song ->
                    val isCurrent = playingSong?.detailUrl == song.detailUrl

                    SongCard(
                        song = song,
                        isCurrent = isCurrent,
                        resolvedPreviewUrl = if(isCurrent) resolvedAudioUrl else null,
                        exoPlayer = exoPlayer,
                        isDownloading = isDownloading && isCurrent,
                        downloadProgress = downloadProgress,
                        onClick = { playingSong = song },
                        onSave = {
                            if (resolvedAudioUrl != null) {
                                isDownloading = true
                                downloadProgress = 0f
                                onSaveSong(song, resolvedAudioUrl!!) { progress ->
                                    downloadProgress = progress
                                    isDownloading = progress < 1.0f && progress > 0f
                                }
                            }
                        }
                    )
                }
            }
            else if (albumResults != null) {
                items(albumResults) { album ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onAlbumSelected(album) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = album.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongCard(
    song: KhSong,
    isCurrent: Boolean,
    resolvedPreviewUrl: String?,
    exoPlayer: ExoPlayer,
    isDownloading: Boolean,
    downloadProgress: Float,
    onClick: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF444444) else Color(0xFF333333)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) Color.Cyan else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Expanded Player Controls
        if (isCurrent) {
            if (resolvedPreviewUrl == null) {
                // Loading State (Resolving MP3 Link)
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Cyan)
                }
            } else {
                // Player Controls
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    // Reuse your existing MusicPreviewControls
                    MusicPreviewControls(exoPlayer)

                    // Save Button Area
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 8.dp, end = 8.dp)
                            .background(Color.Cyan.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .clickable(enabled = !isDownloading) { onSave() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier.size(16.dp),
                                color = Color.Cyan,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Text(
                                text = "SAVE FOR GAME",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Cyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeToggleButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.Cyan else Color(0xFF333333))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.Gray,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}