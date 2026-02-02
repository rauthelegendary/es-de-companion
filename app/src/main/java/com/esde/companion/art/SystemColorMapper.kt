package com.esde.companion.art

import androidx.core.graphics.toColorInt

    object SystemColorMapper {
        private val colorMap = mapOf(
            // Nintendo Era
            "gb"          to "#99A977", // DMG Green
            "gbc"         to "#B284BE", // Grape
            "gba"         to "#807BB1", // Indigo
            "nds"         to "#8DA9C4", // Silver Blue
            "n3ds"        to "#D9534F", // Red
            "nes"         to "#AAB0B0", // Gray
            "snes"        to "#8B82AF", // SNES Purple
            "n64"         to "#4A7C59", // Jungle Green
            "gc"          to "#6A5ACD", // Indigo
            "wii"         to "#00ADEF", // Wii Blue
            "switch"      to "#E3000F", // Switch Red
            "virtualboy"  to "#910000", // Deep Red

            // --- NEW ATARI VARIANTS (No Brown) ---
            "atari2600"   to "#434343", // Charcoal (The black plastic ribs)
            "atari5200"   to "#5F6266", // Metallic Slate
            "atari7800"   to "#2C3E50", // Midnight Blue/Black
            "atari800"    to "#B8C6DB", // "Computery" Silver/Gray
            "atarijaguar" to "#1A1A1B", // Near-Black
            "atarilynx"   to "#6D7993", // Industrial Gray/Blue
            "atarist"     to "#D1D5DB", // ST Light Gray
            "atari"       to "#434343", // Default Atari catch-all

            // SEGA
            "mastersystem" to "#3E6990", // Grid Blue
            "genesis"      to "#2D2D2D", // High Tech Black
            "megadrive"    to "#2D2D2D",
            "dreamcast"    to "#F26522", // Swirl Orange
            "saturn"       to "#4D4D7A", // Saturn Blue
            "gamegear"     to "#3B4D61", // Blue-ish Charcoal

            // Sony
            "psx"         to "#B0B3B8", // Original Gray
            "ps2"         to "#2E3A8C", // Blue
            "ps3"         to "#333333", // Matte Black
            "ps4"         to "#003791", // Sony Blue
            "psp"         to "#484848", // Slate
            "psvita"      to "#202020", // OLED Black

            // Arcade & Others
            "arcade"      to "#F4B41A", // Gold
            "neogeo"      to "#E10600", // NeoGeo Red
            "3do"         to "#34495E", // Professional Navy
            "pcengine"    to "#E7E7E7", // White/Gray Duo
            "pcfx"        to "#FFFFFF", // Pure White
            "wonderswan"  to "#94D2BD", // Muted Teal

            // Special Collections
            "auto-favorites"  to "#F9D423", // Bright Gold
            "auto-lastplayed" to "#48C6EF", // Sky Blue
            "auto-allgames"   to "#F093FB"  // Light Purple
        )

        private val fallbackTones = listOf(
            "#4B79A1", "#F19066", "#786FA6", "#546DE5", "#63C2D1",
            "#CF6A87", "#F78FB3", "#3DC1D3", "#E66767", "#303952"
        )

    fun getColorForSystem(systemName: String?): Int {
        val name = systemName?.lowercase() ?: ""

        val hex = colorMap[name]
            ?: colorMap.entries.find { name.contains(it.key) }?.value
            ?: fallbackTones[Math.abs(name.hashCode()) % fallbackTones.size]

        return hex.toColorInt()
    }
}