package com.esde.companion.art

class ArtRepository(
    private var sgdbScraper: ArtScraper?,
    private var igdbScraper: ArtScraper?
) {
    fun getScraper(type: ScraperType): ArtScraper? {
        return when(type) {
            ScraperType.SGDB -> sgdbScraper
            ScraperType.IGDB -> igdbScraper
        }
    }

    fun getAvailableScraperTypes(): List<ScraperType> {
        val typesAvailable = mutableListOf<ScraperType>()
        if(sgdbScraper != null) {
            typesAvailable.add(ScraperType.SGDB)
        }
        if(igdbScraper != null) {
            typesAvailable.add(ScraperType.IGDB)
        }
        return typesAvailable
    }

    fun setScraper(newScraper: ArtScraper?, type: ScraperType) {
        if (newScraper != null) {
            when (type) {
                ScraperType.SGDB -> sgdbScraper = newScraper
                ScraperType.IGDB -> igdbScraper = newScraper
            }
        }
    }
}

enum class ScraperType {
    SGDB,
    IGDB
}
