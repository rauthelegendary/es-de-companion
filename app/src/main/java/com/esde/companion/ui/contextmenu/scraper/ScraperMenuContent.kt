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
import com.esde.companion.MediaFileLocator
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.MediaCategory
import com.esde.companion.art.MediaSearchResult
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
    mediaFileLocator: MediaFileLocator,
    onSave: (url: String, contentType: ContentType, slot: Int) -> Unit,
    mediaService: YoutubeMediaService,
    launchBoxDao: LaunchBoxDao
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(ScraperStep.SEARCH) }
    var selectedScraper by remember { mutableStateOf(repository.getAvailableScraperTypes().firstOrNull()) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedCategoryKey by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<List<GameSearchResult>>(emptyList()) }
    var availableCategories by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
    var galleryImages by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedGameId by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<MediaSearchResult?>(null) }
    var isVideo by remember {mutableStateOf<Boolean>(false)}
    var imageResultPage by remember { mutableIntStateOf(0) }


    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF222222))) {

        //SCRAPER TABS
        Row(modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)) {
            repository.getAvailableScraperTypes().forEach { type ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedScraper = type
                            // Reset search results when switching scrapers to avoid confusion
                            searchResults = emptyList()
                            currentStep = ScraperStep.SEARCH
                        }
                        .background(if (selectedScraper == type) Color(0xFF444444) else Color.Transparent)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type.name, color = if (selectedScraper == type) Color.Cyan else Color.White)
                }
            }
        }

        when (currentStep) {
            //search for possible games based on name
            ScraperStep.SEARCH -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchStepContent(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            isLoading = isLoading,
                            onSearchExecute = {
                                scope.launch {
                                    isLoading = true
                                    val scraper = repository.getScraper(selectedScraper!!)
                                    searchResults = scraper?.searchGame(searchQuery) ?: emptyList()
                                    isLoading = false
                                }
                            },
                            results = searchResults,
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
                StepHeader(title = "SELECT IMAGE TYPE", onBack = { currentStep = ScraperStep.SEARCH })
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
                        galleryImages = scraper?.fetchImages(id, selectedCategoryKey) ?: emptyList()
                        isLoading = false
                    }
                }

                StepHeader(title = "IMAGE RESULTS", onBack = { currentStep = ScraperStep.TYPES })
                // This triggers the actual image fetch once we have the ID and the Key
                GalleryStepContent(
                    images = galleryImages,
                    startPage = imageResultPage,
                    aspectRatio = availableCategories.find { it.key == selectedCategoryKey }?.aspect ?: 1f,
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
                        mediaFileLocator = mediaFileLocator,
                        gameName = gameFileName,
                        systemName = systemName
                    )
                }
            }
        }
    }
}