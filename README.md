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
- **Ambient Zones** (NEW in v3.3.0): Auto-play a looping playlist for everyone who enters an area (radius, cuboid or WorldGuard region) — perfect for lobby/hub background music, with per-zone `synced`/`individual` playback
- **Server Radio** (NEW in v3.4.0): A zone that reaches every player in every world (`/cjb zone global <id>`)
- **Shuffle & Repeat** (NEW in v3.4.0): Random order and `loop`/`repeat-one` for playlists and zones
- **Progress Bar** (NEW in v3.4.0): Boss bar showing the current track and how far into it you are
- **Personal Settings & Favourites** (NEW in v3.4.0): Every player can turn music off, set their own volume, and keep a favourites list

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
    "debug": false,
    "max-backups": 5,
    "backup-min-interval-minutes": 5
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
  "playback": {
    "volume": 4.0,
    "default-loop": false,
    "jukebox-hearing-radius": 64,
    "show-title": true,
    "show-actionbar": true,
    "show-progress-bar": true,
    "progress-update-ticks": 20
  },
  "parrots": {
    "enable-dancing": true,
    "dance-radius": 3
  },
  "ambient-zones": {
    "enabled": true,
    "sound-category": "RECORDS"
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
| `settings.max-backups` | number | Timestamped backups kept per config file (`0` disables and prunes) | `5` |
| `settings.backup-min-interval-minutes` | number | Minimum gap between two backups of the same file (`0` = back up on every save) | `5` |
| `playback.volume` | number | Server-wide playback volume (0.0-4.0) | `4.0` |
| `playback.jukebox-hearing-radius` | number | Radius for the "Now Playing" announcements | `64` |
| `playback.show-title` | boolean | Title shown when a custom disc starts | `true` |
| `playback.show-actionbar` | boolean | Actionbar shown when a custom disc starts | `true` |
| `playback.show-progress-bar` | boolean | Boss bar showing the current track and its progress | `true` |
| `playback.progress-update-ticks` | number | Progress bar refresh interval in ticks (5-100) | `20` |
| `discs.creeper-drops` | boolean | Fragment drops from Creepers | `true` |
| `discs.creeper-drop-chance` | number | Drop chance (0.05 = 5%, `0` disables) | `0.05` |
| `discs.dungeon-loot` | boolean | Fragments in loot chests | `true` |
| `discs.loot-chance` | number | Loot chance (0.15 = 15%, `0` disables) | `0.15` |
| `discs.max-loot-discs` | number | Max fragment stacks per loot chest (`0` disables loot fragments) | `2` |
| `discs.enable-crafting` | boolean | Enable fragment crafting | `true` |
| `discs.fragments-per-disc` | number | Fragments per disc | `9` |
| `parrots.enable-dancing` | boolean | Parrots dance to music | `true` |
| `parrots.dance-radius` | number | Dance radius in blocks | `3` |
| `ambient-zones.enabled` | boolean | Master switch for ambient music zones | `true` |
| `ambient-zones.sound-category` | string | Sound category for zone music (`RECORDS`, `MUSIC`, `AMBIENT`, ...) | `"RECORDS"` |
| `integrations.worldguard` | boolean | WorldGuard integration | `true` |
| `integrations.griefprevention` | boolean | GriefPrevention integration | `true` |

> Config files are saved asynchronously on a dedicated writer thread and written atomically,
> so edits never stall the server tick and an interrupted write cannot damage a config file.

---

## 🎧 Player Commands (NEW in v3.4.0)

Every player gets these by default — no permission setup needed.

```bash
/cjb music status                  # Your settings + what is currently playing
/cjb music on | off | toggle       # Opt out of (or back into) plugin music
/cjb music volume <0-4|reset>      # Your own volume, independent of the server's
/cjb skip                          # Skip the track you are hearing right now

/cjb favorite add|remove <disc>    # Manage your favourites (or shift-click in /cjb gui)
/cjb favorite list                 # Show them
/cjb favorite play [shuffle] [loop|repeat-one] [global|world|<radius>]
/cjb favorite clear
```

### What the sound engine allows — and what it doesn't

CustomJukebox plays real `.ogg` audio from a resource pack. The server sends one
play-sound packet and the client owns playback from there, which is what makes
full-quality music possible in the first place. The trade-off is that the server
can only **start** and **stop** a sound:

| | Supported |
|---|---|
| Skip, shuffle, repeat, favourites, radio | ✅ |
| Progress display | ✅ (server-side bookkeeping, not a client readout) |
| Per-player volume & mute | ✅ (applies from the next track) |
| **Pause / resume** | ❌ — resuming could only restart the track |
| **Seek / jump to a position** | ❌ |
| **Volume change mid-track** | ❌ — takes effect at the next track |

Note-block plugins can pause and seek because they generate every note themselves;
they cannot play produced audio. This plugin makes the opposite trade.

---

## 🎵 Creating Custom Discs

### `disc.json` - Examples

```json
{
  "discs": {
    "epic_journey": {
      "displayName": "&6Epic Journey",
      "author": "Composer Name",
      "sound": "music_disc.epic_journey",
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
      "sound": "music_disc.calm_waters",
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

> Note: `/cjb playlist play` starts a playlist **once, at your location**, for whoever is in range at that moment. For background music that **auto-starts when players enter an area and loops forever** (e.g. a lobby), use **Ambient Zones** below.

---

## 🌐 Ambient Zones (NEW in v3.3.0)

An **ambient zone** continuously loops a playlist and starts **automatically for every player who enters its area** — no disc, no command, once it's set up. Perfect for lobby/hub background music or event ambience.

### Quick start (lobby example)

```bash
# 1. Build a playlist (see above) — e.g. "lobby-mix"
# 2. Stand in the middle of your lobby and create a zone there:
/cjb zone create lobby            # centers a radius zone on your position
/cjb zone radius lobby 60         # audible/active radius in blocks
/cjb zone playlist lobby lobby-mix
# Done — the zone is enabled, loops by default, and starts for anyone within 60 blocks.
```

Prefer a precise, non-spherical area? Use a **WorldGuard region** or a **cuboid** (box between two corners) instead of a radius:

```bash
# WorldGuard region:
/cjb zone region lobby spawn_lobby   # uses your current world + the region "spawn_lobby"

# Cuboid — stand in one corner, then the opposite corner:
/cjb zone pos1 lobby                  # first corner = your position
/cjb zone pos2 lobby                  # opposite corner = your position
```

By default a zone covers the **full height** (any Y): a radius zone is a vertical **cylinder** and a cuboid an infinite **column** — so players hear it at any height within the horizontal footprint, not just near the center's Y. If you want the zone bounded in Y too (a true 3D sphere/box), set `/cjb zone height <id> limited`.

### Playback mode: synced vs individual

`/cjb zone playback <id> <synced|individual>` chooses how the playlist reaches the people inside:

- **`synced`** (default): one shared timeline — everyone hears the **same track at the same time**. Late arrivals are handled by `syncMode` (see below). Best for **events**.
- **`individual`**: each player runs the playlist **on their own from the moment they enter**, always hearing **complete tracks start-to-finish** (no mid-song switching) and looping the whole playlist — but players are not in sync with each other. Best for a **lobby / background music**. `syncMode` is ignored in this mode.

```bash
/cjb zone playback lobby individual   # lobby: full songs per player
/cjb zone playback eventstage synced  # event: everyone in sync
```

### Commands

All under permission `customjukebox.zone` (default: op).

```bash
/cjb zone list                        # List all zones with their state
/cjb zone info <id>                   # Show a zone's full configuration
/cjb zone create <id>                 # Create a zone (centered on you if in-game)
/cjb zone delete <id>                 # Delete a zone
/cjb zone edit <id>                   # Open the zone editor GUI

/cjb zone playlist <id> <playlist>    # Assign the playlist to loop
/cjb zone radius <id> <blocks>        # Make it a radius zone with this radius
/cjb zone center <id>                 # Set the radius center to your position
/cjb zone pos1 <id>                   # Cuboid: first corner = your position
/cjb zone pos2 <id>                   # Cuboid: opposite corner = your position
/cjb zone region <id> <wg-region>     # Make it a WorldGuard-region zone
/cjb zone global <id>                 # Server radio: reaches every player, every world
/cjb zone shuffle <id> <true|false>   # Random order, reshuffled every lap
/cjb zone height <id> <full|limited>  # full = any Y (cylinder/column, default); limited = bounded in Y (3D)
/cjb zone loop <id> <true|false>      # Loop the whole playlist (default true)
/cjb zone playback <id> <synced|individual>  # Shared timeline vs per-player full songs
/cjb zone sync <id> <immediate|next_track>   # (synced only) how late arrivals join
/cjb zone volume <id> <inherit|0-4>   # Volume = audible radius (inherit = global)
/cjb zone priority <id> <number>      # Overlapping zones: highest priority wins
/cjb zone enable|disable <id>         # Turn a zone on/off
/cjb zone reload                      # Reload zones.json
```

Every `/cjb zone` change reports back whether the zone can actually play. If it stays
silent, the command tells you why in-game — most often an assigned playlist whose discs
have no `durationTicks` (a zone needs a duration to advance to the next track).

### What restarts the music, and what doesn't

Changing a zone's **playlist**, **loop**, **playback mode** or **volume** rebuilds the
zone's timeline, so the playlist starts again from its first track. Changing the **area**
(radius, center, corners, region, world), the **height range**, the **priority** or the
**sync mode** does *not* interrupt playback — the scanner simply re-evaluates who is
inside within one scan interval.

Editing the **contents** of a playlist a zone uses (`/cjb playlist add|remove`, deleting a
disc, changing a disc's sound or duration) rebuilds the affected zones automatically — no
`/cjb zone reload` needed.

### Zones and jukeboxes playing the same disc

Zone music uses the `RECORDS` sound category by default, the same one jukeboxes use, so it
follows the player's "Jukebox/Note Blocks" slider. Stop-sound packets are addressed by
sound key *and* category, so if the **same disc** plays in a zone and in a nearby jukebox
at the same time, either one stopping its track also silences the other for that player.
If that affects your setup, give zones their own category:

```json
"ambient-zones": { "enabled": true, "sound-category": "MUSIC" }
```

### Late arrivals & the sync modes (synced mode)

> This only applies to `playback: synced`. In `individual` mode every player hears full tracks from entry, so there is no "late arrival" problem to solve.

Minecraft's sound engine **cannot seek**, so a player who walks in while a track is already playing cannot be dropped into the exact middle of it. You choose how that's handled per zone:

- **`immediate`** (default): the new arrival hears the current track **from its beginning** — never silence, but slightly offset from players already listening.
- **`next_track`**: the new arrival stays silent until the **next track starts**, then joins perfectly in sync with everyone.

Either way, **everyone re-syncs at every track boundary**, so drift never accumulates.

### How it works

- Each enabled zone runs its own playlist "timeline" that advances and loops **independently of who is listening**, so entering players always know which track is current.
- With `loop false`, a zone plays its playlist through once and then goes silent; it **replays from the first track when a player next enters**, so the area is never permanently dead. Leave `loop true` (default) for continuous background music.
- A scanner checks every online player's position on an interval (`settings.scan-interval-ticks` in `zones.json`, default `20` = 1 s) and starts/stops the zone's audio as players cross the boundary.
- Discs need a configured `durationTicks` to be used in a zone (that's how tracks advance/loop) — discs without a duration are skipped with a warning.
- **Folia**: fully supported — player checks and sound playback run on each player's region thread.

### `zones.json`

Zones are stored in `zones.json` (created automatically). The `/cjb zone` commands and the editor GUI write this file for you, but it can also be edited by hand and applied with `/cjb zone reload`:

```json
{
  "version": 1,
  "settings": { "scan-interval-ticks": 20 },
  "zones": {
    "lobby": {
      "enabled": true,
      "world": "world",
      "type": "radius",
      "center": { "x": 0.0, "y": 64.0, "z": 0.0 },
      "radius": 60.0,
      "region": "",
      "playlist": "lobby-mix",
      "loop": true,
      "volume": -1,
      "syncMode": "immediate",
      "playback": "synced",
      "fullHeight": true,
      "priority": 0
    }
  }
}
```

- Set `"type": "worldguard"` + a `"region"` name to use a WorldGuard region, or `"type": "cuboid"` + `"pos1"`/`"pos2"` (`{ "x":…, "y":…, "z":… }` block coords) for a box, instead of `center`/`radius`.
- `"playback"` is `"synced"` (shared timeline) or `"individual"` (per-player full songs).
- `"fullHeight": true` (default) ignores the Y axis (cylinder/column — any height); `false` bounds the zone in Y too (sphere/box).
- `"volume": -1` means "inherit the global playback volume". The whole feature can be switched off with `ambient-zones.enabled` in `config.json`.

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

Your music lives in a resource pack; the plugin only tells the client which sound
to play. **A ready-made pack is in [`example-resourcepack/`](example-resourcepack/)** —
it already contains `pack.mcmeta`, `sounds.json`, item definitions, models and
placeholder textures for the three discs the plugin ships with. Add your `.ogg`
files, zip it, host it.

**[→ Full step-by-step guide: `example-resourcepack/README.md`](example-resourcepack/README.md)**
— conversion, hosting, SHA-1, custom textures, pack formats and troubleshooting.

The short version, and the part that goes wrong most often — four names have to
line up:

| Where | Value |
|---|---|
| The file | `assets/minecraft/sounds/records/my_song.ogg` |
| `sounds.json` — event key | `"music_disc.my_song"` |
| `sounds.json` — `name` | `"records/my_song"` (path under `sounds/`, no `.ogg`) |
| `disc.json` — `sound` | `"music_disc.my_song"` |

```json
{
  "music_disc.my_song": {
    "sounds": [
      { "name": "records/my_song", "stream": true }
    ]
  }
}
```

`"stream": true` is required — without it the whole track is loaded into memory
before playback. Files must be **OGG Vorbis**; MP3 does not work:

```bash
ffmpeg -i input.mp3 -c:a libvorbis -q:a 5 my_song.ogg
```

Then in `server.properties`:

```properties
resource-pack=https://example.com/customjukebox-pack.zip
resource-pack-sha1=<sha1sum of the zip>
require-resource-pack=false
```

Regenerate the SHA-1 every time you change the pack, and zip the *contents* of the
folder so that `pack.mcmeta` sits at the top level of the archive.

> **Do not use a GitHub `/blob/` URL.** It points at a webpage, not the ZIP.
> Use the release asset link: `https://github.com/user/repo/releases/download/v1/pack.zip`

### Custom disc textures

Since Minecraft 1.21.4, item appearance is no longer controlled by `overrides` /
`predicate` blocks inside `models/item/*.json`. It is decided in
`assets/minecraft/items/<base_item>.json`, dispatching on the `custom_model_data`
value the plugin sets on the disc item:

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "entries": [
      { "threshold": 1001,
        "model": { "type": "minecraft:model", "model": "minecraft:item/epic_journey_disc" } }
    ],
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/music_disc_13" }
  }
}
```

`threshold` is the disc's `customModelData`; `fallback` keeps vanilla discs looking
vanilla. One entry per disc sharing that base item.

### Pack format

`example-resourcepack/pack.mcmeta` uses `min_format: [69, 0]` / `max_format: [88, 0]`,
covering 1.21.9 through 26.2. Those fields exist since 1.21.9; on 1.21.4 – 1.21.8
use a single `pack_format` instead:

| Minecraft Version | Resource Pack Format |
|-------------------|---------------------|
| 1.21.4 | `pack_format: 46` |
| 1.21.5 | `pack_format: 55` |
| 1.21.7 – 1.21.8 | `pack_format: 64` |
| 1.21.9 – 1.21.10 | `69` |
| 1.21.11 | `75` |
| 26.1 – 26.1.2 | `84` |
| 26.2 | `88` |

A mismatched number usually still loads with a warning in the client's pack list.


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
| `/cjb zone <action> <id> [value]` | Manage ambient music zones — auto-playing looping playlists for a region/radius/cuboid. Actions: `list`, `info`, `create`, `delete`, `edit`, `playlist`, `radius`, `center`, `pos1`, `pos2`, `region`, `height`, `loop`, `sync`, `playback`, `volume`, `priority`, `enable`, `disable`, `reload`. See [Ambient Zones](#-ambient-zones-new-in-v330). (v3.3.0+) | `customjukebox.zone` |

### Permissions

#### Parent Permissions (Gruppen)

| Permission | Beschreibung | Standard | Enthält |
|------------|-------------|----------|---------|
| `customjukebox.admin` | Alle Admin-Befehle | **OP** | `reload`, `give`, `fragment`, `list`, `info`, `gui`, `play`, `stop`, `volume`, `playlist`, `zone`, `updatenotify` |
| `customjukebox.user` | Alle Spieler-Befehle | **Alle** | `use`, `gui`, `list`, `info` |

#### Einzelne Permissions

| Permission | Beschreibung | Standard | Befehle |
|------------|-------------|----------|---------|
| `customjukebox.use` | Benutzung von Custom Jukeboxen (Discs einlegen/entnehmen) | Alle | - |
| `customjukebox.gui` | Disc-Auswahl GUI öffnen | Alle | `/cjb gui` |
| `customjukebox.list` | Alle Custom Discs auflisten | Alle | `/cjb list` |
| `customjukebox.info` | Details einer Disc anzeigen | Alle | `/cjb info <disc>` |
| `customjukebox.reload` | Plugin-Konfiguration neu laden | OP | `/cjb reload` |
| `customjukebox.give` | Custom Discs an Spieler geben | OP | `/cjb give <player> <disc> [amount]` |
| `customjukebox.fragment` | Fragmente an Spieler geben | OP | `/cjb fragment <player> <disc> [amount]` |
| `customjukebox.play` | Discs direkt abspielen (ohne Jukebox) | OP | `/cjb play <disc> [loop] [range]` |
| `customjukebox.stop` | Alle aktiven Wiedergaben stoppen | OP | `/cjb stop` |
| `customjukebox.volume` | Lautstärke ändern, Mute/Unmute | OP | `/cjb volume`, `/cjb mute`, `/cjb unmute` |
| `customjukebox.playlist` | Playlists verwalten und abspielen | OP | `/cjb playlist <...>` |
| `customjukebox.zone` | Ambient-Zonen verwalten | OP | `/cjb zone <...>` |
| `customjukebox.updatenotify` | Update-Benachrichtigungen beim Login | OP | - |

#### Übersicht als Baum

```
customjukebox.admin          (OP)     → Alle Admin-Befehle
  ├── customjukebox.reload             → Config neu laden
  ├── customjukebox.give               → Discs geben
  ├── customjukebox.fragment           → Fragmente geben
  ├── customjukebox.play               → Direkt abspielen
  ├── customjukebox.stop               → Wiedergabe stoppen
  ├── customjukebox.volume             → Lautstärke / Mute
  ├── customjukebox.playlist           → Playlist-Verwaltung
  ├── customjukebox.zone               → Ambient-Zonen-Verwaltung
  └── customjukebox.updatenotify       → Update-Hinweise

customjukebox.user           (Alle)   → Alle Spieler-Befehle
  ├── customjukebox.use                → Jukeboxen benutzen
  ├── customjukebox.gui                → GUI öffnen
  ├── customjukebox.list               → Discs auflisten
  └── customjukebox.info               → Disc-Details anzeigen
```

> **Hinweis:** Spieler mit `customjukebox.give` können im GUI auch kostenlos Discs erhalten. Spieler ohne diese Permission müssen die entsprechende Disc im Inventar haben, um sie über das GUI in eine Jukebox einzulegen.

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

### Events

All events live in `de.boondocksulfur.customjukebox.api.events`.

| Event | Cancellable | Fired when |
|-------|-------------|------------|
| `DiscPlaybackStartEvent` | ✅ | A playback starts. Carries the listener set, which handlers may modify. |
| `DiscPlaybackStopEvent` | ❌ | A playback stops (with a `StopReason`). |
| `DiscRegisteredEvent` | ❌ | A disc is registered. |
| `DiscRemovedEvent` | ❌ | A disc is removed. |
| `CustomSoundPlayEvent` | ✅ | A disc sound is about to be delivered **to one player**. |
| `CustomSoundStopEvent` | ✅ | A disc sound is about to be stopped **for one player**. |

`DiscPlaybackStartEvent` is the coarse hook — one event per playback. The two
`CustomSound*` events are the fine one: they fire once per player, on **every**
delivery path (jukebox playback, a player walking into range, `/cjb music on`,
ambient zones and radio), and let a companion plugin take delivery over for
individual players:

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onSoundPlay(CustomSoundPlayEvent event) {
    if (!isMyKindOfPlayer(event.getPlayer())) {
        return;
    }

    // We deliver this one ourselves
    event.setCancelled(true);
    event.getPlayer().playSound(event.getLocation(), myOwnSoundKey(event.getDisc()),
        SoundCategory.RECORDS, event.getVolume(), 1.0f);
}
```

Cancelling suppresses only that one sound packet. The player stays a tracked
listener of the playback, so the progress bar, `/cjb skip` and stop handling keep
working for them — cancel `CustomSoundStopEvent` the same way to stop your own
sound. This is exactly how the
[Bedrock extension](https://github.com/BoondockSulfur/BS-CustomJukebox-BedrockExtension)
plays discs to Bedrock clients, which need a different sound namespace.

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
- **Documentation**: This README + [`example-resourcepack/README.md`](example-resourcepack/README.md) for the resource pack + inline config comments

---

**Version**: 3.5.1
**Minecraft Version**: Paper/Folia 1.21.4+ and 26.x
**Java Version**: 21+ (26.x servers run on Java 25 — the plugin jar works on both)
**Author**: BoondockSulfur
