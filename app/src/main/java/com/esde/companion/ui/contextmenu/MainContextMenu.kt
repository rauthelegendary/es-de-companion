package com.esde.companion.ui.contextmenu

import android.graphics.Bitmap
import android.net.Uri
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
import com.esde.companion.AnimationSettings
import com.esde.companion.data.AppState
import com.esde.companion.managers.MediaManager
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.PageEditorItem
import com.esde.companion.WidgetPage
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.mediaoverride.MediaOverride
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.data.getCurrentSystemName
import com.esde.companion.data.isInGameBrowsingMode
import com.esde.companion.data.isInSystemBrowsingMode
import com.esde.companion.ost.GameMusicRepository
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ost.khinsider.KhSong
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.contextmenu.scraper.ScraperMenuContent
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File


@Composable
fun MainContextMenu(
    state: AppState,
    uiState: MenuUiState,
    currentPageIndex: Int,
    currentPage: WidgetPage,
    pages: List<WidgetPage>,
    onSavePages: (List<PageEditorItem>) -> Unit,
    onDismiss: () -> Unit,
    widgetActions: WidgetActions,
    artRepository: ArtRepository,
    onMusicSearch: (String) -> Unit,
    onMusicSelect: (StreamInfoItem, (Float) -> Unit) -> Unit,
    onSave: (String, ContentType, Int) -> Unit,
    mediaService: YoutubeMediaService,
    mediaManager: MediaManager,
    mediaOverrideRepository: MediaOverrideRepository,
    onSaveOverride: (MediaOverride) -> Unit,
    onRemoveOverride: (MediaOverride) -> Unit,
    onCropSave: (File, Bitmap) -> Unit,
    removeCrop: (File) -> Unit,
    onRenamePage: (String) -> Unit,
    swapMedia: (String, ContentType, String, MediaSlot, MediaSlot) -> Unit,
    deleteMedia: (String, ContentType, String, MediaSlot) -> Unit,
    launchBoxDao: LaunchBoxDao,
    animationSettings: AnimationSettings,
    musicRepository: GameMusicRepository,
    onKhMusicSelect: (KhSong, String, (Float) -> Unit) -> Unit,
    isSearchingMusic: Boolean,
    musicResults: List<StreamInfoItem>,
    currentGameVolume: Double,
    onVolumeChanged: (Double) -> Unit,
    setManualFileForSlot: (Uri, ContentType, String, String, MediaSlot, () -> Unit) -> Unit
    ) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var fileName = ""
    val system = state.getCurrentSystemName()!!
    val tabs: List<String> = remember(state) {
        if (state.isInGameBrowsingMode()) {
            fileName = (state as AppState.GameBrowsing).gameFilename!!
            listOf("User Interface", "Game Media", "Music", "Scraper")
        } else {
            listOf("User Interface")
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
                Box(modifier = Modifier.weight(1f).padding(10.dp, 2.dp, 10.dp, 2.dp)) {
                    when (tabs[selectedTab]) {
                        "User Interface" -> WidgetMenuContent(
                            uiState = uiState,
                            isSystemView = state.isInSystemBrowsingMode(),
                            actions = widgetActions,
                            currentPageIndex = currentPageIndex,
                            currentPage = currentPage,
                            pages = pages,
                            onSavePages = onSavePages,
                            onRenamePage = onRenamePage,
                            animationSettings = animationSettings,
                            currentGameVolume = currentGameVolume,
                            onVolumeChanged = onVolumeChanged,
                        )

                        "Game Media" -> GameMediaContent(
                            game = fileName,
                            system = system,
                            mediaManager = mediaManager,
                            onSaveOverride = onSaveOverride,
                            onRemoveOverride = onRemoveOverride,
                            removeCrop = removeCrop,
                            onCropSave = onCropSave,
                            mediaOverrideRepository = mediaOverrideRepository,
                            swapMedia = swapMedia,
                            deleteMedia = deleteMedia,
                            setManualFileForSlot = setManualFileForSlot
                        )

                        "Music" -> {
                            val s = state as AppState.GameBrowsing
                            MusicMenuContent(
                                musicRepository = musicRepository,
                                youtubeMediaService = mediaService,
                                initialKhSearchQuery = s.gameName!!,
                                initialYtSearchQuery = "\"${s.gameName} ${YoutubeMediaService.searchString}\"",
                                onMusicSearch = onMusicSearch,
                                onSaveYoutube = onMusicSelect,
                                onSaveKh = onKhMusicSelect,
                                musicResults = musicResults,
                                isSearchingMusic = isSearchingMusic
                            )
                        }
                        "Scraper" -> {
                            val s = state as AppState.GameBrowsing
                            ScraperMenuContent(
                                repository = artRepository,
                                initialSearchQuery = s.gameName!!,
                                onSave = onSave,
                                mediaService = mediaService,
                                mediaManager = mediaManager,
                                gameFileName = fileName,
                                systemName = system,
                                launchBoxDao = launchBoxDao
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