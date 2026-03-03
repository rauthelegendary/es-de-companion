package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esde.companion.ost.GameMusicRepository
import com.esde.companion.ost.YoutubeMediaService
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

enum class MusicScraperType {
    YOUTUBE
}

@Composable
fun MusicMenuContent(
    musicRepository: GameMusicRepository,
    youtubeMediaService: YoutubeMediaService,
    initialYtSearchQuery: String,
    initialKhSearchQuery: String,
    onMusicSearch: (String) -> Unit,
    musicResults: List<StreamInfoItem>,
    isSearchingMusic: Boolean,
    onSaveYoutube: (StreamInfoItem, (Float) -> Unit) -> Unit
) {
    var selectedScraper by remember { mutableStateOf(MusicScraperType.YOUTUBE) }

    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF222222))
    ) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
            MusicScraperType.entries.forEach { type ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (selectedScraper != type) {
                                selectedScraper = type
                            }
                        }
                        .background(if (selectedScraper == type) Color(0xFF444444) else Color.Transparent)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type.name,
                        color = if (selectedScraper == type) Color.Cyan else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedScraper) {
                MusicScraperType.YOUTUBE -> {
                    YoutubeMenuContent(
                        initialQuery = initialYtSearchQuery,
                        results = musicResults,
                        isLoading = isSearchingMusic,
                        mediaService = youtubeMediaService,
                        onSearch = { query ->
                            scope.launch {
                                isLoading = true
                                onMusicSearch(query)
                                isLoading = false
                            }
                        },
                        onVideoSelected = onSaveYoutube
                    )
                }
                else -> {

                }
            }
        }
    }
}