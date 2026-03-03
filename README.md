# ES-DE Second Screen Companion

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.esde.companion%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FRobZombie9043%2Fes-de-companion%22%2C%22author%22%3A%22RobZombie9043%22%2C%22name%22%3A%22ES-DE%20Companion%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3Anull%7D">
  <img
    src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png?raw=true"
    alt="Add ES-DE Companion to Obtainium"
    width="182"
  >
</a>

![Version](https://img.shields.io/badge/version-0.4.5-blue)
![Android](https://img.shields.io/badge/Android-10%2B-green)



A companion app for [ES-DE](https://es-de.org/) that displays beautiful game artwork and marquees on a secondary display, transforming your dual-screen device into an immersive retro gaming interface.

> **Note**: This is a semi-official companion app designed to enhance your ES-DE experience on dual-screen devices.

> **Note**: This app was developed using AI assistance, the full source code has been made available.

[![Watch the video](https://img.youtube.com/vi/sMCEDMRibs0/0.jpg)](https://youtu.be/sMCEDMRibs0)

## New Features (in beta)
- **Pagination** - Can now set up multiple pages displaying different types of artwork or videos
    - Pages/widgets are now separated by systems and games
    - Pages have various functions to account for different available media and preferences in navigation
- **Scrapers** - Added support for YouTube, [SteamgridDB](https://www.steamgriddb.com/login) and [IGDB](https://api-docs.igdb.com/#getting-started), the API keys for the latter two can be set in the app drawer settings menu
- **Media Management** - You can now set alternatives to media from ES-DE through either the scrapers or manual selection
- **Automatic Music Scraping** - If activated will automatically search on YouTube for the OST of the game and play it in the background. Can be manually overridden through the scraper or turned off.
- **Improved widget editing** - It's now easier to select, move, resize and stack widgets when there's multiple or large widgets on a page
- **More text widgets for ES-DE game data** - Now has separate text widgets for title, release date, description, developer, etc.

### Detailed description:
- **Alt slots** - all the ES-DE media types (marquee, fanart, 2D boxart, etc.) now have alt slots. These can be set either through the scraper or manually to add additional media for a game. Alt slots can be marked as default (meaning it will always be loaded instead of ES-DE's media) on a per game basis
  - **Manual set**: copies the selected file into the ES-DE companion folder. This can be used to select videos or animated images for types that would normally be image only. Webp or mp4 is recommended over gifs. 
  - **ES-DE**: any media within the ES-DE folder remains untouched. The alt slots and any media management are purely limited to the companion app.
- **Page modifiers**
  - **Default:** when scrolling between games/systems you'll automatically return to this page
  - **Required:** only displays the page if the background media actually exists, can also be set on a widget level
    - Can be used to, for example, set up a page that only shows when the game in question has an image for alt slot 1 for fanart
    - The custom folder and image options override this check whether they exist or not
  - **Auto transition**: You can set automatic transitions between pages either on a timer or on video end (takes precedence). This respects the required tag if the page in question is missing content.
  - **Manual transition**: Simply tap on the right or left of the screen to go to the next/previous page. Will automatically skip past invalid required pages or pages marked as auto transition only. When in edit mode (widgets unlocked), you can manually transition to every page and the tap region will be a bit smaller.
- **Page management**: You can name pages (purely for your own convenience) and use the page manager to swap page order.
- **Automatic Music Scraping**: Searches for the first result on YouTube, downloads it to a local folder and plays it automatically. While scrolling the music file for the selected game will automatically play. Keep in mind the automatic scraper can give false positives. The music file can be overridden through the YouTube scraper or even by manually replacing the music file. Volume can be adjusted on a per game basis. The app supports a single music file per game.
- **Pan/zoom animation**: Optional zoom in animation for background images. Helps to make fanart in particular feel less static.
- **Glint animation**: Optional animation for marquees. Adds a shine/glint animation that plays on page load and then on a slow interval.
- **Custom Folder/Image** Allows you to set a specific folder or image as your media source. Folders require more manual setup compared to other options and are only recommended if the alt slots, system images or system icons aren't enough to achieve your vision. The main use for this is when you want multiple video pages in the systems view. Both custom folders and images are ignored by the required tag on pages.

#### Custom folder
Custom folders require a specific folder structure corresponding to ES-DE. For game media the file will have to be in the correct platform folder and have the same filename as the game file. System media needs to be in the systems folder and have the platform name as filename. Here's an example:
```
YourCustomFolder/
├── snes/                                       # Game media for platform
│   └── Chrono Trigger (U).png                  # Filename needs to match the game filename in ES-DE minus extension
├── gba/
│   └── Fire Emblem (E).mp4          
└── systems/                                    # System media
    └── wii.mp4             
    └── n64.png
```


## 🎮 Features

### Dynamic Display
- **Real-time artwork display** - Shows game fanart, screenshots, and marquees as you browse in ES-DE
- **Video playback support** - Play game videos when browsing games
  - Configurable delay (instant to 5 seconds) before video starts
  - Optional audio control (muted by default)
  - Videos automatically stop during gameplay and screensavers
- **System view support** - Displays built-in system logos, custom images, or random game artwork when browsing systems
- **Smooth animations** - Configurable fade and scale effects with custom timing options
- **Background customization** - Adjustable dimming and blur effects

### Application Behavior
- **Game Launch Display** - Choose what displays while games are running
  - **Black**: Plain black screen (minimal distraction)
  - **Default Image**: Fallback background with current marquee
  - **Game Image**: Shows the launched game's artwork and marquee
- **Screensaver Display** - Choose what displays during ES-DE screensavers
  - **Black**: Plain black screen
  - **Default Image**: Fallback background with current marquee
  - **Game Image**: Shows screensaver game artwork (slideshow/video modes only)

### App Drawer
- **Full Android app launcher** - Access all your installed apps from one place
- **Smart search** - Quickly find apps with the built-in search bar
- **Customizable grid** - Adjust column count to your preference
- **App visibility control** - Hide apps you don't want to see in the drawer
- **Long-press menu** - Configure launch behavior for each app
  - Open app info/settings
  - Choose display (this screen/other screen) for launching apps with per-app preferences saved

### Easy Setup
- **Quick Setup Wizard** - Step-by-step configuration on first launch
- **Auto-script creation** - Automatically generates ES-DE integration scripts
- **Comprehensive onboarding** - Tutorial dialog explaining key features and gestures

### Visual Customization
- **Background priority** - Choose between Fanart or Screenshot priority
- **Animation styles** - None, Fade, Scale + Fade, or Custom with adjustable duration and scale
- **Logo controls** - Independent on/off for system/game logos with shared size control
- **Dimming control** - 0-100% background darkening
- **Blur effects** - Optional background blur (Android 12+)
- **Drawer opacity** - Customize app drawer transparency

## 📱 Requirements

- **Android 10+** (API 29 or higher)
- **Dual-screen device** or external display support
- **ES-DE** installed with downloaded media
- **Storage permissions** for accessing media and creating scripts

## 🚀 Installation

1. Download the latest APK from [Releases](../../releases)
2. Install on your device
3. Launch the app and follow the Quick Setup wizard
4. Grant storage permissions when prompted
5. Enable scripts in ES-DE:
   - Open ES-DE
   - Press START → Other Settings
   - Toggle ON "Custom Event Scripts"
   - Toggle ON "Browsing Custom Events"

## 🏠 Recommended Setup

For the best experience, use [Mjolnir](https://github.com/blacksheepmvp/mjolnir) to run this companion app together with ES-DE as your home screens on dual-display devices.

## ⚙️ Configuration

### File Paths

The app uses these default paths (configurable in settings):

| Path | Default Location | Purpose |
|------|-----------------|---------|
| **Downloaded Media** | `/storage/emulated/0/ES-DE/downloaded_media` | Game artwork - fanart, screenshots, marquees, Game videos |
| **System Images** | `/storage/emulated/0/ES-DE/downloaded_media/system_images` | Custom system images (optional override) |
| **System Logos** | `/storage/emulated/0/ES-DE/downloaded_media/system_logos` | Custom system logos (optional override) |
| **Scripts** | `/storage/emulated/0/ES-DE/scripts` | Integration scripts |
| **Logs** | `/storage/emulated/0/ES-DE Companion/logs` | Event log files |

**Note**: Scripts and logs have moved to internal storage (`/ES-DE Companion/`) for better compatibility with SD card installations.

### Custom System Images (Optional Override)

To override random game artwork in system view with your own images:
1. Place custom images in the system images folder
2. Use filenames matching ES-DE system shortnames: `snes.webp`, `arcade.png`, `psx.jpg`, etc.
3. These will override the random game art displayed

### Custom System Logos (Optional Override)

To add additional system logos or override built-in system logos in system view with your own images:
1. Place custom images in the system logos folder
2. Use filenames matching ES-DE system shortnames: `snes.svg`, `snes.png`, `arcade.webp`, etc.
3. These will take priority over built-in logos

## 🎨 How It Works

1. **ES-DE Integration**: The app creates custom event scripts that ES-DE calls when you browse games/systems, launch games, and activate screensavers
2. **Real-time Updates**: Scripts write to log files that the app monitors using FileObserver
3. **Image Loading**: The app reads game metadata and displays corresponding artwork
4. **Smart State Management**: Automatically handles gameplay, screensavers, and browsing states

### Script Files

The Quick Setup creates these scripts automatically:

```
ES-DE Companion/scripts/
├── esdecompanion-game-select/
│   └── esdecompanion-game-select.sh            # Browsing games
├── esdecompanion-system-select/
│   └── esdecompanion-system-select.sh          # Browsing systems
├── esdecompanion-game-start/
│   └── esdecompanion-game-start.sh             # Game launched
├── esdecompanion-game-end/
│   └── esdecompanion-game-end.sh               # Game exited
├── esdecompanion-screensaver-start/
│   └── esdecompanion-screensaver-start.sh      # Screensaver started
├── esdecompanion-screensaver-end/
│   └── esdecompanion-screensaver-end.sh        # Screensaver ended
└── esdecompanion-screensavergameselect/
    └── esdecompanion-screensavergameselect.sh  # Screensaver game browsing
```

## 🤝 Contributing

Contributions are welcome! Please feel free to open an Issue or submit a Pull Request.

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

## 🙏 Credits

- Built for [ES-DE](https://es-de.org/) by Leon Styhre
- Works best in a dual home screen set up using [Mjolnir](https://github.com/blacksheepmvp/mjolnir) home screen manager by Blacksheep
- Uses [Glide](https://github.com/bumptech/glide) for efficient image loading
- Uses [AndroidSVG](https://github.com/BigBadaboom/androidsvg) for SVG rendering
- Uses [ExoPlayer](https://github.com/google/ExoPlayer) for video playback

## 📞 Support

If you encounter any issues or have questions:
1. Check the [Issues](../../issues) page
2. Create a new issue with details about your problem

## 🔄 Changelog
[Changelog](https://github.com/RobZombie9043/es-de-companion/blob/master/CHANGELOG.md)

---

**Enjoy your enhanced ES-DE dual screen experience!** 🎮✨
