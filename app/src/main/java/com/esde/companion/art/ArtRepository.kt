package com.esde.companion.art

class ArtRepository(
    private val sgdbScraper: ArtScraper
) {
    fun getScraper(type: ScraperType): ArtScraper? {
        return when(type) {
            ScraperType.SGDB -> sgdbScraper
            else -> null
        }
    }

    fun getAvailableScraperTypes(): List<ScraperType> {
        return listOf(ScraperType.SGDB)
    }
}

enum class ScraperType {
    SGDB,
    IGDB,
    LaunchBox
}
