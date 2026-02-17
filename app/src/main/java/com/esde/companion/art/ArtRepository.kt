package com.esde.companion.art

import com.esde.companion.art.LaunchBox.LaunchBoxScraper

class ArtRepository(
    private var sgdbScraper: ArtScraper?,
    private var igdbScraper: ArtScraper?,
    private var launchBoxScraper: LaunchBoxScraper?
) {
    fun getScraper(type: ScraperType): ArtScraper? {
        return when(type) {
            ScraperType.SGDB -> sgdbScraper
            ScraperType.IGDB -> igdbScraper
            ScraperType.LaunchBox -> launchBoxScraper
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
        if(launchBoxScraper != null) {
            typesAvailable.add(ScraperType.LaunchBox)
        }
        return typesAvailable
    }

    fun setScraper(newScraper: ArtScraper?, type: ScraperType) {
        if (newScraper != null) {
            when (type) {
                ScraperType.SGDB -> sgdbScraper = newScraper
                ScraperType.IGDB -> igdbScraper = newScraper
                ScraperType.LaunchBox -> {
                    if (newScraper is LaunchBoxScraper) {
                        launchBoxScraper = newScraper
                    }
                }
            }
        }
    }
}

enum class ScraperType {
    SGDB,
    IGDB,
    LaunchBox
}
