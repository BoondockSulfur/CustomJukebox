# CustomJukebox Plugin

A fully-featured Minecraft Paper Plugin for version 1.21+ with advanced Jukebox features - completely implemented in Java.

## 📋 Table of Contents

- [Features](#-features)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Creating Custom Discs](#-creating-custom-discs)
- [Resource Pack Setup](#-resource-pack-setup)
- [Fragment System](#-fragment-system)
- [Commands & Permissions](#-commands--permissions)
- [Multi-Language Support](#-multi-language-support)
- [Integrations](#-integrations)
- [Build Instructions](#-build-instructions)

---

## ✨ Features

### ⚡ Server Compatibility (NEW in v2.0.0!)
- **Full Folia Support**: Works on both Paper/Spigot AND Folia servers
- **Automatic Detection**: Detects server type at runtime
- **Region-Threaded Scheduling**: Uses Folia's region scheduler for optimal performance
- **Zero Configuration**: No settings needed - just install and run
- **Backwards Compatible**: No performance impact on Paper/Spigot servers

### 🎵 Custom Music Discs & Organization
- **Custom Music Discs** without replacing vanilla discs
- **Custom Sounds** via Resource Pack (`.ogg` format)
- **Customizable Metadata**: Title, Author, Lore, CustomModelData
- **Duration Tracking**: Automatic stop after disc duration
- **JSON Configuration**: Easy management via `disc.json`
- **Categories**: Organize discs by theme (Ambient, Epic, Nature, etc.)
- **Playlists**: Group multiple discs for sequential playback

### 🧩 Fragment System
- **Disc Fragments**: Collect fragments and craft complete discs
- **Creeper Drops**: Skeletons kill Creepers → Fragments drop
- **Loot Integration**: Fragments in Dungeons, Bastions, End Cities
- **Shapeless Crafting**: 9 fragments = 1 complete disc

### 🎨 GUI & Admin Interface (NEW in v1.3.0!)
- **User GUI**: `/cjb gui` - Browse and select custom discs
- **Admin Panel**: Admins see special Admin button (⚙) in main GUI
- **In-Game Disc Creation**: 7-step chat wizard for creating new discs
  - Validates all inputs (ID format, sound key syntax, etc.)
  - Shows existing categories during creation
  - Creates disc with all fields in one flow
- **In-Game Disc Editing**: Hybrid GUI/Chat system for editing
  - Text fields (name, author, sound key) via chat input
  - Numeric values (duration, model data) via GUI selectors with presets
  - Category selection via GUI showing all available categories
  - All changes auto-save immediately
- **Duration Selector**: Choose from presets (30s-600s) or enter custom value
- **Model Data Selector**: Visual selection (1-20) or custom input for textures
- **Category Selector**: Browse all categories, create new, or remove category
- **Playlist Editor GUI**: Click-to-add/remove discs from playlists with live status
- **Delete Confirmations**: Safe deletion dialogs for discs, playlists, and categories
- **Jukebox GUI**: Right-click on empty jukebox opens disc selection
- **Category Management** (NEW!):
  - **Category Creation Wizard**: 3-step chat wizard for creating categories
  - **Category Editor GUI**: Edit display names and descriptions with color support
  - All category changes auto-save to `disc.json`

### 🎨 Advanced Color System (NEW in v1.3.0!)
- **HEX Colors**: Use `&#RRGGBB` or `#RRGGBB` for millions of colors
  - Example: `&#FF5555Red Text` or `#00FF00Green Text`
- **Gradient Support**: Create smooth color transitions
  - Example: `<gradient:#FF0000:#0000FF>Rainbow Text</gradient>`
  - Automatically interpolates between colors across each character
- **Legacy Codes**: Standard Minecraft codes still work (`&a`, `&b`, `&c`, etc.)
- **Formatting Codes**: Bold (`&l`), Italic (`&o`), Underline (`&n`), Strikethrough (`&m`)
- **Works Everywhere**: Display names, authors, descriptions, categories, playlist names, lore
- **User-Friendly**: All wizards and editors show color code examples

**Color Code Examples:**
```
Display Name: <gradient:#FFD700:#FFA500>Golden Sunset</gradient>
Author: &#FF5555Epic Artist
Description: &6Legendary &l&nEPIC&r &6soundtrack
Category: <gradient:#00FF00:#0000FF>Nature Sounds</gradient>
```

### 🦜 Parrot Dance System
- **Synchronized Dancing** to custom music
- **Configurable Radius** (Default: 3 blocks)

### 🌍 Plugin Integrations
- **WorldGuard**: Region-based jukebox permissions (USE flag)
- **GriefPrevention**: Claim-based jukebox permissions (Container trust)
- **PlaceholderAPI**: 15+ placeholders for other plugins
- **Public API**: Developers can integrate CustomJukebox features
- **bStats**: Anonymous usage statistics (configurable)

---

## 📦 Installation

### Prerequisites
- **Minecraft Server**: Paper 1.21+ or Folia 1.21+
- **Java Version**: Java 21 or higher
- **Optional**: WorldGuard, GriefPrevention

### Steps

1. **Download**: Download the latest `CustomJukebox-x.x.x.jar`
2. **Installation**: Place the JAR in `plugins/` on your server
3. **Server Start**: Start the server (Plugin creates config files)
4. **Configuration**: Adjust `config.json` and `disc.json`
5. **Resource Pack**: Create and host your resource pack (see below)
6. **Optional**: Install PlaceholderAPI for placeholder support
7. **Reload**: `/cjb reload` or server restart

---

## ⚙️ Configuration

### `config.json` - Complete Reference

```json
{
  "settings": {
    "enabled": true,
    "language": "en",
    "enable-gui": true,
    "debug": false
  },
  "discs": {
    "creeper-drops": true,
    "creeper-drop-chance": 0.05,
    "dungeon-loot": true,
    "trail-ruins-loot": true,
    "max-loot-discs": 2,
    "loot-chance": 0.15,
    "enable-crafting": true,
    "fragments-per-disc": 9
  },
  "parrots": {
    "enable-dancing": true,
    "dance-radius": 3
  },
  "integrations": {
    "worldguard": true,
    "griefprevention": true
  }
}
```

### Configuration Options

| Option | Type | Description | Default |
|--------|------|-------------|---------|
| `settings.enabled` | boolean | Master switch for the plugin | `true` |
| `settings.language` | string | Language (en, de, es, it) | `"en"` |
| `settings.enable-gui` | boolean | Enable jukebox GUI | `true` |
| `settings.debug` | boolean | Debug mode (verbose logging) | `false` |
| `discs.creeper-drops` | boolean | Fragment drops from Creepers | `true` |
| `discs.creeper-drop-chance` | number | Drop chance (0.05 = 5%) | `0.05` |
| `discs.dungeon-loot` | boolean | Fragments in loot chests | `true` |
| `discs.loot-chance` | number | Loot chance (0.15 = 15%) | `0.15` |
| `discs.enable-crafting` | boolean | Enable fragment crafting | `true` |
| `discs.fragments-per-disc` | number | Fragments per disc | `9` |
| `parrots.enable-dancing` | boolean | Parrots dance to music | `true` |
| `parrots.dance-radius` | number | Dance radius in blocks | `3` |
| `integrations.worldguard` | boolean | WorldGuard integration | `true` |
| `integrations.griefprevention` | boolean | GriefPrevention integration | `true` |

---

## 🎵 Creating Custom Discs

### `disc.json` - Examples

```json
{
  "discs": {
    "epic_journey": {
      "displayName": "&6Epic Journey",
      "author": "Composer Name",
      "sound": "minecraft:music_disc.epic_journey",
      "type": "MUSIC_DISC_13",
      "customModelData": 1001,
      "durationTicks": 4500,
      "fragmentCount": 9,
      "lore": [
        "&7An epic orchestral piece",
        "&7Duration: 3:45",
        "&eRequires 9 fragments to craft"
      ],
      "description": "Epic Journey"
    },
    "calm_waters": {
      "displayName": "&bCalm Waters",
      "author": "Nature Sounds",
      "sound": "minecraft:music_disc.calm_waters",
      "type": "MUSIC_DISC_CAT",
      "customModelData": 1002,
      "durationTicks": 6000,
      "fragmentCount": 9,
      "lore": [
        "&7Peaceful ambient sounds",
        "&7Duration: 5:00",
        "&eRequires 9 fragments to craft"
      ],
      "description": "Calm Waters"
    }
  }
}
```

**Important Notes**:
- Disc IDs (keys in JSON) should be simple identifiers (e.g., `epic_journey`)
- Sound keys must use the format `namespace:music_disc.<disc_id>` (e.g., `minecraft:music_disc.epic_journey`)
- Sound keys must match the sound event keys in your resource pack's `sounds.json`
- The plugin validates all configurations on startup (v1.2.1+)

### Field Explanations

| Field | Type | Description | Required |
|------|------|-------------|----------|
| `displayName` | string | Display name (with `&` color codes) | ✅ Yes |
| `author` | string | Author/Composer | ✅ Yes |
| `sound` | string | Sound key in resource pack | ❌ No |
| `type` | string | Base material (e.g. `MUSIC_DISC_13`) | ✅ Yes |
| `customModelData` | number | CustomModelData for texture | ✅ Yes |
| `durationTicks` | number | Duration in ticks (20 ticks = 1 sec) | ❌ No (0 = vanilla) |
| `fragmentCount` | number | Number of fragments (0 = no crafting) | ❌ No (Default: 0) |
| `lore` | array | Lore lines (with `&` color codes) | ❌ No |
| `description` | string | Short description | ❌ No |
| `category` | string | Category ID for organization | ❌ No (Since 1.3.0) |

---

## 🎵 Categories & Playlists

### Categories

Organize your discs by theme or genre. Define categories in `disc.json`:

```json
{
  "categories": {
    "ambient": {
      "displayName": "&bAmbient Sounds",
      "description": "Calm and peaceful background music"
    },
    "epic": {
      "displayName": "&6Epic Music",
      "description": "Grand and adventurous compositions"
    }
  }
}
```

Then assign discs to categories:
```json
{
  "discs": {
    "ocean_dreams": {
      "displayName": "&bOcean Dreams",
      "category": "ambient",
      ...
    }
  }
}
```

### Playlists

Group multiple discs for automatic sequential playback:

```json
{
  "playlists": {
    "relaxation": {
      "displayName": "&dRelaxation Mix",
      "description": "Relaxing music for peaceful moments",
      "discs": ["ocean_dreams", "forest_walk", "calm_waters"]
    },
    "adventure": {
      "displayName": "&cAdventure Soundtrack",
      "description": "Epic music for your adventures",
      "discs": ["epic_journey", "battle_theme"]
    }
  }
}
```

**Commands**:
```bash
# View and Play
/cjb playlist list                   # Show all playlists
/cjb playlist info relaxation        # Show playlist details
/cjb playlist play relaxation        # Play playlist once
/cjb playlist play relaxation loop   # Play playlist endlessly

# In-Game Management (v1.3.0+)
/cjb playlist create myplaylist "My Playlist"  # Create new playlist
/cjb playlist delete myplaylist                # Delete playlist
/cjb playlist add myplaylist epic_journey      # Add disc to playlist
/cjb playlist remove myplaylist epic_journey   # Remove disc from playlist
/cjb playlist rename oldname newname           # Rename playlist ID
/cjb playlist edit myplaylist                  # Open GUI editor
```

**How it works**:
1. Starts first disc in playlist
2. Automatically plays next disc when current finishes
3. Continues until all discs played
4. With `loop`: Restarts from first disc

---

## 🎮 Admin GUI System (v1.3.0)

CustomJukebox now includes a comprehensive in-game administration system! No more manual JSON editing for most operations.

### Access Admin Panel

1. Run `/cjb gui` as an admin (permission: `customjukebox.admin`)
2. Click the **⚙ Admin Panel** button at the bottom
3. Navigate through the three management sections

### Disc Management

**Create New Discs** (Chat Wizard):
1. Click "Disc Management" → "Create New Disc"
2. Follow the 7-step chat wizard:
   - **Step 1**: Disc ID (e.g., `my_epic_song`)
   - **Step 2**: Display Name (with color codes)
   - **Step 3**: Author name
   - **Step 4**: Sound Key (format: `namespace:sound_name`)
   - **Step 5**: Duration in seconds
   - **Step 6**: Category (shows existing categories)
   - **Step 7**: Custom Model Data (for textures)
3. Review summary and disc is created automatically
4. Saved instantly to `disc.json`

**Edit Existing Discs** (GUI-based):
1. **Left-click** any disc to open editor
2. Click the field you want to change:
   - **Display Name** → Chat input
   - **Author** → Chat input
   - **Sound Key** → Chat input (format validated)
   - **Duration** → GUI selector with presets (30s-600s) or custom input
   - **Category** → GUI selector showing all categories + create new
   - **Custom Model Data** → GUI selector (1-20) or custom input
3. All changes auto-save immediately
4. Click "Save & Close" when done

**Delete Discs**:
1. **Right-click** any disc
2. Confirm deletion in dialog
3. Disc removed from `disc.json` instantly

### Playlist Management

**Visual Editor**:
1. Click "Playlist Management"
2. **Left-click** any playlist to edit with GUI
3. Click discs to add/remove them from playlist
4. Changes save automatically

**Delete Playlists**:
1. **Right-click** any playlist
2. Confirm deletion

### Category Management

**Organize Discs**:
1. Click "Category Management"
2. View all categories and disc counts
3. Create/delete categories
4. Assign categories in disc editor

All changes are **instantly saved** to `disc.json`!

---

## 📦 Resource Pack Setup

### Step 1: Create Pack Structure

```
customjukebox-resourcepack/
├── pack.mcmeta
├── pack.png (optional)
└── assets/
    └── minecraft/
        ├── sounds.json
        ├── models/
        │   └── item/
        │       ├── music_disc_13.json
        │       └── disc_fragment_5.json
        ├── textures/
        │   └── item/
        │       ├── epic_journey_disc.png
        │       └── fragment_epic_journey.png
        └── sounds/
            └── custom/
                └── music/
                    └── epic_journey.ogg
```

### Step 2: Create `pack.mcmeta`

```json
{
  "pack": {
    "pack_format": 34,
    "description": "CustomJukebox Resource Pack\nCustom Music Discs & Fragments"
  }
}
```

**Pack Format Versions:**
- Minecraft 1.21.x: `34`
- Minecraft 1.20.5-1.20.6: `32`

### Step 3: Create `sounds.json`

```json
{
  "music_disc.epic_journey": {
    "sounds": [
      {
        "name": "custom/music/epic_journey",
        "stream": true
      }
    ]
  },
  "music_disc.calm_waters": {
    "sounds": [
      {
        "name": "custom/music/calm_waters",
        "stream": true
      }
    ]
  }
}
```

**Important Notes**:
- `"stream": true` is **required** for music files to reduce memory usage
- Sound event keys should use the format `music_disc.<disc_id>` (e.g., `music_disc.epic_journey`)
- The `name` field is the path to your `.ogg` file relative to `assets/minecraft/sounds/`
- Example: `"custom/music/epic_journey"` → `assets/minecraft/sounds/custom/music/epic_journey.ogg`

### Step 4: Create Item Models

**`assets/minecraft/models/item/music_disc_13.json`**:
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "item/music_disc_13"
  },
  "overrides": [
    {
      "predicate": {
        "custom_model_data": 1001
      },
      "model": "item/custom/epic_journey_disc"
    }
  ]
}
```

**`assets/minecraft/models/item/custom/epic_journey_disc.json`**:
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "item/epic_journey_disc"
  }
}
```

### Step 5: Convert Audio Files

**Requirements**:
- Format: **OGG Vorbis** (not MP3!)
- Mono: Preferred (smaller file size)
- Bitrate: 96-128 kbps recommended
- Sample Rate: 44100 Hz

**Conversion with ffmpeg**:
```bash
# MP3 → OGG
ffmpeg -i input.mp3 -c:a libvorbis -q:a 4 epic_journey.ogg

# WAV → OGG (Mono, 96kbps)
ffmpeg -i input.wav -ac 1 -c:a libvorbis -b:a 96k epic_journey.ogg
```

### Step 6: Host Pack

#### Option A: GitHub (Recommended)
1. Create GitHub Repository
2. Upload `customjukebox-pack.zip`
3. Create Release
4. Copy download URL (Direct Download Link!)

#### Option B: Own Server
- Host on your web server (HTTPS required!)
- URL: `https://yourserver.com/packs/customjukebox-pack.zip`

---

## 🧩 Fragment System

### How Does It Work?

1. **Collect Fragments**:
   - Creeper drops (Skeleton kills Creeper = 100% drop)
   - Player kills Creeper (5% chance, configurable)
   - Loot chests (Dungeons, Bastions, End Cities, etc.)

2. **Craft Disc**:
   - Shapeless Crafting Table recipe
   - Exactly **9 fragments** of the same disc (configurable via `disc.json`)
   - Example: 9x "Epic Journey Fragment" → 1x "Epic Journey Disc"

### Fragment Drop Locations

| Location | Chance | Fragments |
|----------|--------|-----------|
| Dungeon Chest | 15% | 1-5 |
| Desert Temple | 15% | 1-5 |
| Bastion Remnant | 15% | 2-5 |
| End City Chest | 15% | 2-5 |
| Creeper (Player Kill) | 5% | 1-3 |
| Creeper (Skeleton Kill) | 100% | 1-3 |

---

## 🎮 Commands & Permissions

### Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/cjb help` | Shows all commands | `customjukebox.help` |
| `/cjb reload` | Reloads plugin configuration | `customjukebox.admin` |
| `/cjb list` | Shows all custom discs | `customjukebox.list` |
| `/cjb info <disc>` | Shows details of a disc | `customjukebox.info` |
| `/cjb give <player> <disc> [amount]` | Gives disc to player | `customjukebox.give` |
| `/cjb fragment <player> <disc> [amount]` | Gives fragments to player | `customjukebox.fragment` |
| `/cjb gui [player]` | Opens disc selection GUI | `customjukebox.gui` |
| `/cjb play <disc> [loop] [range]` | Plays disc directly (v1.1.0+) | `customjukebox.play` |
| `/cjb stop` | Stops all active playbacks (v1.1.0+) | `customjukebox.stop` |
| `/cjb volume [value\|preset] [restart]` | Manages playback volume (v1.1.0+) | `customjukebox.volume` |
| `/cjb mute [restart]` | Mutes all playback (v1.2.2+) | `customjukebox.volume` |
| `/cjb unmute [restart]` | Unmutes playback (v1.2.2+) | `customjukebox.volume` |
| `/cjb playlist list` | Lists all playlists (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist info <name>` | Shows playlist details (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist play <name> [loop]` | Plays playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist create <id> [name]` | Creates new playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist delete <name>` | Deletes playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist add <name> <disc>` | Adds disc to playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist remove <name> <disc>` | Removes disc from playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist rename <old> <new>` | Renames playlist (v1.3.0+) | `customjukebox.playlist` |
| `/cjb playlist edit <name>` | Opens GUI editor (v1.3.0+) | `customjukebox.playlist` |

### Permissions

```
customjukebox.*                    # Full access
  ├── customjukebox.admin          # All admin commands
  │   ├── customjukebox.reload
  │   ├── customjukebox.give
  │   ├── customjukebox.fragment
  │   ├── customjukebox.play
  │   ├── customjukebox.stop
  │   ├── customjukebox.volume
  │   └── customjukebox.playlist
  └── customjukebox.user           # Player features
      ├── customjukebox.use
      ├── customjukebox.gui
      ├── customjukebox.list
      ├── customjukebox.info
      └── customjukebox.help
```

---

## 🌐 Multi-Language Support

### Supported Languages

- 🇬🇧 **English** (`en`)
- 🇩🇪 **German** (`de`)
- 🇪🇸 **Spanish** (`es`)
- 🇮🇹 **Italian** (`it`)

### Change Language

**`config.json`**:
```json
{
  "settings": {
    "language": "en"
  }
}
```

---

## 🔗 Integrations

### WorldGuard

**Function**:
- Players can only use jukeboxes in regions where they have `USE` permission
- OP Bypass: OPs can always use jukeboxes

**Example**:
```bash
/rg define spawn
/rg flag spawn use -g nonmembers deny
```

### GriefPrevention

**Function**:
- Players can only use jukeboxes in their own claims
- Requires at least container trust
- OP bypass active

---

## 🔨 Build Instructions

### Prerequisites
- **Java Development Kit (JDK)**: Version 21 or higher
- **Gradle**: Provided automatically via Gradle Wrapper

### Build

**Windows**:
```bash
gradlew.bat clean shadowJar
```

**Linux/Mac**:
```bash
./gradlew clean shadowJar
```

### Output

The finished JAR can be found at:
```
build/libs/CustomJukebox-2.0.0.jar
```

### Testing & Debugging

**Enable Debug Mode** in `config.json`:
```json
{
  "settings": {
    "debug": true
  }
}
```

This will:
- Show detailed sound playback logs
- Display configuration validation results
- Help identify resource pack loading issues
- Show which players receive sounds

---

## 🔌 API for Developers

CustomJukebox provides a public API for other plugins to interact with discs, playlists, and playback.

### Getting the API

```java
import de.boondocksulfur.customjukebox.api.CustomJukeboxAPI;

// Get API instance
CustomJukeboxAPI api = CustomJukeboxAPI.getInstance();
if (api == null) {
    // CustomJukebox not loaded
    return;
}
```

### Working with Discs

```java
// Get all discs
Collection<CustomDisc> discs = api.getAllDiscs();

// Get specific disc
CustomDisc disc = api.getDisc("epic_journey");

// Get disc from ItemStack
ItemStack item = player.getInventory().getItemInMainHand();
CustomDisc disc = api.getDiscFromItem(item);

// Check if ItemStack is a custom disc
boolean isDisc = api.isCustomDisc(item);

// Get random disc
CustomDisc randomDisc = api.getRandomDisc();
```

### Working with Categories

```java
// Get all categories
Collection<DiscCategory> categories = api.getAllCategories();

// Get specific category
DiscCategory category = api.getCategory("ambient");

// Get discs by category
Collection<CustomDisc> ambientDiscs = api.getDiscsByCategory("ambient");
```

### Working with Playlists

```java
// Get all playlists
Collection<DiscPlaylist> playlists = api.getAllPlaylists();

// Get specific playlist
DiscPlaylist playlist = api.getPlaylist("relaxation");

// Get discs from playlist
List<CustomDisc> discsInPlaylist = api.getDiscsFromPlaylist("relaxation");
```

### Playback Control

```java
Location location = player.getLocation();

// Start simple playback
api.startPlayback(location, disc);

// Start with loop
api.startPlayback(location, disc, true);

// Start with loop and range
PlaybackRange range = new PlaybackRange(PlaybackRange.RangeType.GLOBAL);
api.startPlayback(location, disc, true, range);

// Stop playback
api.stopPlayback(location);

// Check if playing
boolean isPlaying = api.isPlaying(location);

// Stop all playbacks
api.stopAllPlaybacks();
```

### Configuration Access

```java
// Get volume
float volume = api.getVolume();

// Set volume
api.setVolume(2.5f);

// Check if enabled
boolean enabled = api.isEnabled();

// Get language
String lang = api.getLanguage();
```

### Integration Checks

```java
// Check if player can use jukebox at location
boolean canUse = api.canUseJukebox(player, location);

// Check integrations
boolean hasWorldGuard = api.isWorldGuardEnabled();
boolean hasGriefPrevention = api.isGriefPreventionEnabled();
```

### Utility Methods

```java
// Reload plugin
api.reload();

// Get version
String version = api.getVersion();

// Get translated message
String message = api.getMessage("disc-given");
String messageWithPlaceholder = api.getMessage("disc-given", "disc", "Epic Journey");
```

### Add to your plugin

**plugin.yml**:
```yaml
depend: [CustomJukebox]
# or
softdepend: [CustomJukebox]
```

**Maven** (if you want compile-time access):
```xml
<dependency>
    <groupId>de.boondocksulfur</groupId>
    <artifactId>CustomJukebox</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## 👨‍💻 Developer Information

### Project Structure

```
src/main/
├── java/de/boondocksulfur/customjukebox/
│   ├── CustomJukebox.java              # Main Plugin Class
│   ├── commands/                        # Command Handler & Subcommands
│   ├── listeners/                       # Event Listeners
│   ├── manager/                         # Manager Classes
│   │   ├── ConfigManager.java           # JSON Config Management
│   │   ├── DiscManager.java             # Disc & Fragment Management
│   │   ├── PlaybackManager.java         # Sound Playback
│   │   ├── LanguageManager.java         # Multi-Language Support
│   │   └── IntegrationManager.java      # Plugin Integrations
│   └── model/                           # Data Models
└── resources/
    ├── plugin.yml
    ├── config.json                      # Main Config (JSON)
    ├── disc.json                        # Disc Definitions (JSON)
    └── languages/                       # Translation Files (YAML)
```

### Architecture

**Manager Pattern**:
- **ConfigManager**: JSON-based configuration management
- **DiscManager**: Disc and fragment registry
- **PlaybackManager**: Sound playback sessions
- **LanguageManager**: Translations and placeholders
- **IntegrationManager**: WorldGuard & GriefPrevention integration

---

## 📄 License

**MIT License**

```
Copyright (c) 2025 BoondockSulfur

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 📞 Support & Links

- **Issues**: [Discord](https://discord.gg/xEJjF65K46)
- **Documentation**: This README + inline config comments

---

**Version**: 2.0.0
**Minecraft Version**: Paper 1.21+ / Folia 1.21+
**Java Version**: 21+
**Author**: BoondockSulfur
