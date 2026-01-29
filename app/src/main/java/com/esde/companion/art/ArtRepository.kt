package com.esde.companion.art

class ArtRepository(
    private val sgdbScraper: ArtScraper,
    private val igdbScraper: ArtScraper
) {
    fun getScraper(type: ScraperType): ArtScraper? {
        return when(type) {
            ScraperType.SGDB -> sgdbScraper
            ScraperType.IGDB -> igdbScraper
            else -> null
        }
    }

    fun getAvailableScraperTypes(): List<ScraperType> {
        return listOf(ScraperType.SGDB, ScraperType.IGDB)
    }
}

enum class ScraperType {
    SGDB,
    IGDB,
    LaunchBox
}
