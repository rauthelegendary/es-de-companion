package com.esde.companion.ui.contextmenu.scraper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.managers.MediaManager
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.MediaCategory
import com.esde.companion.art.MediaSearchResult
import com.esde.companion.art.ScraperResult
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ui.ContentType
import kotlinx.coroutines.launch

enum class ScraperStep { SEARCH, TYPES, GALLERY, SAVE, LB_UPDATE }

@Composable
fun ScraperMenuContent(
    repository: ArtRepository,
    initialSearchQuery: String,
    gameFileName: String,
    systemName: String,
    mediaManager: MediaManager,
    onSave: (url: String, contentType: ContentType, slot: Int) -> Unit,
    mediaService: YoutubeMediaService,
    launchBoxDao: LaunchBoxDao
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(ScraperStep.SEARCH) }
    var selectedScraper by remember { mutableStateOf(repository.getAvailableScraperTypes().firstOrNull()) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedCategoryKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var availableCategories by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
    var galleryImages by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<MediaSearchResult?>(null) }
    var isVideo by remember {mutableStateOf<Boolean>(false)}
    var imageResultPage by remember { mutableIntStateOf(0) }


    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF222222))) {
        val scraperTypes = repository.getAvailableScraperTypes()
        //SCRAPER TABS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            scraperTypes.forEach { type ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedScraper = type
                            //reset search results when switching scrapers to avoid confusion
                            searchState = SearchState.Idle
                            currentStep = ScraperStep.SEARCH
                        }
                        .background(if (selectedScraper == type) Color(0xFF444444) else Color.Transparent)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        type.name,
                        color = if (selectedScraper == type) Color.Cyan else Color.White
                    )
                }
            }
        }

        if (scraperTypes.isEmpty()) {
            Text(
                text = "No scrapers available. Please check the API settings in the app drawer.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        } else {
            when (currentStep) {
                //search for possible games based on name
                ScraperStep.SEARCH -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchState is SearchState.Error) {
                            Text(
                                text = (searchState as SearchState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SearchStepContent(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                isLoading = searchState is SearchState.Loading,
                                onSearchExecute = {
                                    scope.launch {
                                        searchState = SearchState.Loading
                                        val scraper = repository.getScraper(selectedScraper!!)
                                        searchState = when (val result = scraper?.searchGame(searchQuery)) {
                                            is ScraperResult.Success -> SearchState.Success(result.results)
                                            is ScraperResult.Error -> SearchState.Error(result.message)
                                            null -> SearchState.Error("Scraper is not available - please check the API credentials or connection")
                                        }
                                    }
                                },
                                results = if (searchState is SearchState.Success)
                                    (searchState as SearchState.Success).results
                                else emptyList(),
                                onGameSelected = { gameId ->
                                    selectedGameId = gameId
                                    currentStep = ScraperStep.TYPES
                                }
                            )
                        }
                        if (selectedScraper?.name == "LaunchBox") {
                            Button(
                                onClick = { currentStep = ScraperStep.LB_UPDATE },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("UPDATE METADATA DATABASE")
                            }
                        }
                    }
                }

                ScraperStep.LB_UPDATE -> {
                    DatabaseUpdateContent(
                        onComplete = { },
                        onBack = { currentStep = ScraperStep.SEARCH },
                        dao = launchBoxDao
                    )
                }
                //after we've selected our game, select an image type
                ScraperStep.TYPES -> {
                    StepHeader(
                        title = "SELECT IMAGE TYPE",
                        onBack = { currentStep = ScraperStep.SEARCH })
                    //fetch possible image types for the scraper first
                    LaunchedEffect(selectedGameId) {
                        val id = selectedGameId ?: return@LaunchedEffect
                        isLoading = true
                        val scraper = repository.getScraper(selectedScraper!!)
                        availableCategories = scraper?.getAvailableMediaTypes(id) ?: emptyList()
                        isLoading = false
                        imageResultPage = 0
                    }
                    //select type step
                    CategorySelectionStep(
                        categories = availableCategories,
                        isLoading = isLoading,
                        onCategorySelected = { categoryKey ->
                            selectedCategoryKey = categoryKey
                            currentStep = ScraperStep.GALLERY
                        }
                    )
                }
                //after the type has been seleceted, retrieve thumbnails for the images and display them
                ScraperStep.GALLERY -> {
                    LaunchedEffect(currentStep, selectedCategoryKey) {
                        if (currentStep == ScraperStep.GALLERY && selectedCategoryKey.isNotEmpty()) {
                            val id = selectedGameId ?: return@LaunchedEffect
                            isLoading = true
                            val scraper = repository.getScraper(selectedScraper!!)
                            galleryImages =
                                scraper?.fetchImages(id, selectedCategoryKey, null) ?: emptyList()
                            isLoading = false
                        }
                    }

                    StepHeader(
                        title = "IMAGE RESULTS",
                        onBack = { currentStep = ScraperStep.TYPES })
                    // This triggers the actual image fetch once we have the ID and the Key
                    GalleryStepContent(
                        images = galleryImages,
                        startPage = imageResultPage,
                        aspectRatio = availableCategories.find { it.key == selectedCategoryKey }?.aspect
                            ?: 1f,
                        isLoading = isLoading,
                        onImageSelected = { image, newPage ->
                            selectedImage = image
                            imageResultPage = newPage
                            currentStep = ScraperStep.SAVE
                            isVideo = selectedCategoryKey == "videos"
                        }
                    )
                }

                ScraperStep.SAVE -> {
                    StepHeader(title = "SAVE MEDIA", onBack = { currentStep = ScraperStep.GALLERY })
                    selectedImage?.let { media ->
                        SaveMediaStep(
                            media = media,
                            onSave = onSave,
                            isVideo = isVideo,
                            mediaService = mediaService,
                            mediaManager = mediaManager,
                            gameName = gameFileName,
                            systemName = systemName
                        )
                    }
                }
            }
        }
    }
}

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<GameSearchResult>) : SearchState()
    data class Error(val message: String) : SearchState()
}