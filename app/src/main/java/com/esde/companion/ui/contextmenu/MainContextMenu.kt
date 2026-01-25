package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.esde.companion.OverlayWidget
import com.esde.companion.art.ArtRepository
import com.esde.companion.isInGameBrowsingMode
import com.esde.companion.isInSystemBrowsingMode
import com.esde.companion.ost.MusicDownloader
import com.esde.companion.ui.contextmenu.scraper.ScraperMenuContent
import org.schabi.newpipe.extractor.stream.StreamInfoItem


@Composable
fun MainContextMenu(
    state: AppState,
    uiState: MenuUiState,
    onDismiss: () -> Unit,
    widgetActions: WidgetActions,
    artRepository: ArtRepository,
    musicResults: List<StreamInfoItem>,
    isSearchingMusic: Boolean,
    onMusicSearch: (String) -> Unit,
    onMusicSelect: (StreamInfoItem) -> Unit,
    onSave: (String, OverlayWidget.ContentType, Int) -> Unit
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)) // Dim the background
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Removes the ripple so it just feels like "empty space"
            ) {
                onDismiss() // Tapping the dark area closes the menu
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A1A1A),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight(0.85f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
        ) {
            Column(modifier = Modifier.fillMaxSize()){
                if (tabs.size > 1) {
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

                Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                    when (tabs[selectedTab]) {
                        "Widgets" -> WidgetMenuContent(
                            uiState = uiState,
                            isSystemView = state.isInSystemBrowsingMode(),
                            actions = widgetActions
                        )
                        "Music" -> {
                            val s = state as AppState.GameBrowsing
                            MusicMenuContent(
                                initialQuery = "\"${s.gameName} ${MusicDownloader.searchString}\"",
                                results = musicResults,
                                isLoading = isSearchingMusic,
                                onSearch = onMusicSearch,
                                onVideoSelected = onMusicSelect
                            )
                        }
                        "Scraper" -> ScraperMenuContent(
                            repository = artRepository,
                            initialSearchQuery = gameName,
                            onSave = onSave                        )
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