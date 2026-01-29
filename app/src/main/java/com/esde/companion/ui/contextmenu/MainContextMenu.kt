package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.AppState
import com.esde.companion.WidgetPage
import com.esde.companion.art.ArtRepository
import com.esde.companion.isInGameBrowsingMode
import com.esde.companion.isInSystemBrowsingMode
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.contextmenu.scraper.ScraperMenuContent
import org.schabi.newpipe.extractor.stream.StreamInfoItem


@Composable
fun MainContextMenu(
    state: AppState,
    uiState: MenuUiState,
    currentPageIndex: Int,
    currentPage: WidgetPage,
    onDismiss: () -> Unit,
    widgetActions: WidgetActions,
    artRepository: ArtRepository,
    musicResults: List<StreamInfoItem>,
    isSearchingMusic: Boolean,
    onMusicSearch: (String) -> Unit,
    onMusicSelect: (StreamInfoItem, (Float) -> Unit) -> Unit,
    onSave: (String, ContentType, Int) -> Unit,
    mediaService: YoutubeMediaService
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var gameName = ""
    val tabs: List<String> = remember(state) {
        if (state.isInGameBrowsingMode()) {
            gameName = (state as AppState.GameBrowsing).gameName!!
            listOf("Widgets", "Music", "Scraper")
        } else {
            listOf("Widgets")
        }
    }


    Surface(
    color = Color(0xFF1A1A1A),
    tonalElevation = 8.dp,
    modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tabs.size > 1) {
                    Box(modifier = Modifier.weight(1f)) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color(0xFF333333),
                            contentColor = Color.White
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                    when (tabs[selectedTab]) {
                        "Widgets" -> WidgetMenuContent(
                            uiState = uiState,
                            isSystemView = state.isInSystemBrowsingMode(),
                            actions = widgetActions,
                            currentPageIndex = currentPageIndex,
                            currentPage = currentPage
                        )

                        "Music" -> {
                            val s = state as AppState.GameBrowsing
                            MusicMenuContent(
                                initialQuery = "\"${s.gameName} ${YoutubeMediaService.searchString}\"",
                                results = musicResults,
                                isLoading = isSearchingMusic,
                                onSearch = onMusicSearch,
                                onVideoSelected = onMusicSelect,
                                mediaService = mediaService
                            )
                        }

                        "Scraper" -> {
                            val s = state as AppState.GameBrowsing
                            ScraperMenuContent(
                                repository = artRepository,
                                initialSearchQuery = s.gameName!!,
                                onSave = onSave,
                                mediaService = mediaService
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            labelColor = Color.White
        )
    )
}