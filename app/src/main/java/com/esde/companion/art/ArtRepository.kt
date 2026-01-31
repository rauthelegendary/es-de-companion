package com.esde.companion.art

import com.esde.companion.art.LaunchBox.LaunchBoxScraper

class ArtRepository(
    private val sgdbScraper: ArtScraper,
    private val igdbScraper: ArtScraper,
    private val launchBoxScraper: LaunchBoxScraper
) {
    fun getScraper(type: ScraperType): ArtScraper? {
        return when(type) {
            ScraperType.SGDB -> sgdbScraper
            ScraperType.IGDB -> igdbScraper
            ScraperType.LaunchBox -> launchBoxScraper
        }
    }

    fun getAvailableScraperTypes(): List<ScraperType> {
        return listOf(ScraperType.SGDB, ScraperType.IGDB, ScraperType.LaunchBox)
    }
}

enum class ScraperType {
    SGDB,
    IGDB,
    LaunchBox
}
