package com.esde.companion.ui.contextmenu.scraper

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.OverlayWidget
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.ImageSearchResult
import com.esde.companion.art.MediaCategory
import kotlinx.coroutines.launch

enum class ScraperStep { SEARCH, TYPES, GALLERY, SAVE }

@Composable
fun ScraperMenuContent(
    repository: ArtRepository,
    initialSearchQuery: String,
    onSave: (url: String, contentType: OverlayWidget.ContentType, slot: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(ScraperStep.SEARCH) }
    var selectedScraper by remember { mutableStateOf(repository.getAvailableScraperTypes().firstOrNull()) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedCategoryKey by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<List<GameSearchResult>>(emptyList()) }
    var availableCategories by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
    var galleryImages by remember { mutableStateOf<List<ImageSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedGameId by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<ImageSearchResult?>(null) }


    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF222222))) {

        //SCRAPER TABS
        Row(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
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
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type.name, color = if (selectedScraper == type) Color.Cyan else Color.White)
                }
            }
        }

        when (currentStep) {
            //search for possible games based on name
            ScraperStep.SEARCH -> {
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
            //after we've selected our game, select an image type
            ScraperStep.TYPES -> {
                StepHeader(title = "SELECT IMAGE TYPE", onBack = { currentStep = ScraperStep.SEARCH })
                //fetch possible image types for the scraper first
                LaunchedEffect(selectedGameId) {
                    val id = selectedGameId ?: return@LaunchedEffect
                    isLoading = true
                    val scraper = repository.getScraper(selectedScraper!!)
                    availableCategories = scraper?.getAvailableMediaTypes(id.toInt()) ?: emptyList()
                    isLoading = false
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
                        galleryImages = scraper?.fetchImages(id.toInt(), selectedCategoryKey) ?: emptyList()
                        isLoading = false
                    }
                }

                StepHeader(title = "IMAGE RESULTS", onBack = { currentStep = ScraperStep.TYPES })
                // This triggers the actual image fetch once we have the ID and the Key
                GalleryStepContent(
                    images = galleryImages,
                    aspectRatio = availableCategories.find { it.key == selectedCategoryKey }?.aspect ?: 1f,
                    isLoading = isLoading,
                    onImageSelected = { image ->
                        selectedImage = image
                        currentStep = ScraperStep.SAVE
                    }
                )
            }
            ScraperStep.SAVE -> {
                StepHeader(title = "SAVE IMAGE", onBack = { currentStep = ScraperStep.GALLERY })
                selectedImage?.let { image ->
                    SaveImageStep(
                        image = image,
                        onConfirm = { type, slot ->
                            onSave(image.url, type, slot)
                        }
                    )
                }
            }
        }
    }
}