# Changelog

All notable changes to CustomJukebox will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.1.2] - 2026-03-29

### Fixed
- **Message formatting**: Fixed color codes showing as garbled text on Paper 1.21+ servers
- **Critical: Wrong disc playing after quick switch**: Added comprehensive fix for race conditions
  - Implemented 500ms cooldown between disc changes to prevent race conditions
  - Added triple-verification system to ensure correct disc identification
  - Re-identify disc after insertion to catch quick swaps
  - Stop any existing playback immediately on new disc insertion
  - Added final verification before playback starts

- **Critical: Playlist skip race condition**: Fixed tracks being skipped in playlists
  - Added `peekNext()` method for safe preview without index advancement
  - Eliminated race conditions in playlist progression logic
  - Ensured thread-safe playlist queue operations

- **Critical: Data loss on disc save**: Implemented atomic file operations
  - Write to temporary file first, then atomic rename
  - Automatic backup restoration on save failure
  - Added `getLatestBackup()` recovery mechanism
  - Prevents disc.json corruption on write failures

- **Memory leak with player UUIDs**: Fixed players remaining in listener lists
  - Added `PlayerQuitEvent` handler to clean up UUIDs
  - Implemented `removePlayerFromAllPlaybacks()` method
  - Prevents memory accumulation over time

- **HTML entities in display texts**: Fixed &amp; showing instead of color codes
  - Added `unescapeHtmlEntities()` method in DiscManager
  - Properly decodes HTML entities when loading from JSON
  - Backup decoder in JukeboxListener for compatibility

- **Vanilla sound stop mechanism**: Improved reliability
  - Increased attempts from 2 to 4 (at 1, 5, 10, 20 ticks)
  - Optimized performance with squared distance calculations
  - Added chunk-based pre-filtering for efficiency

- **Folia support issues**: Fixed fallback problems
  - Better error messages instead of silent fallback
  - Improved API change detection
  - Warns admins about compatibility issues

- **Mute state not persistent**: Now saves across reloads
  - Added `loadMuteState()` and `saveMuteState()` methods
  - Mute state stored in config.json
  - Survives plugin reloads and server restarts

### Security
- **Thread-safety improvements**: Migrated to ConcurrentHashMap
  - All GUI classes now use thread-safe collections
  - Prevents ConcurrentModificationException
  - Better multi-threaded performance

- **Permission checks in GUIs**: Added runtime permission validation
  - GUI handlers now verify permissions on every click
  - Prevents exploitation when permissions change during use
  - Immediate closure if permissions are revoked

### Performance
- **Location cloning optimization**: Reduced unnecessary object creation
  - Added `getJukeboxLocationClone()` for explicit cloning
  - Internal methods use reference for performance
  - Documented when cloning is necessary

- **Input validation**: Added comprehensive parameter checking
  - CustomDisc constructor validates all inputs
  - Throws IllegalArgumentException for invalid values
  - Prevents creation of invalid disc objects

### Technical
- Fixed missing imports in JukeboxListener
- Updated all GUI classes for thread-safety
- Enhanced debug logging for troubleshooting
- Improved error recovery mechanisms

---

## [2.1.0] - 2026-02-18

### Added
- **Adventure API Integration**: Migrated to modern Paper Adventure API
  - New `AdventureUtil` class for unified text component handling
  - Full support for HEX colors, gradients, and MiniMessage format
  - Better performance and future-proofing for Paper 1.21+

### Changed
- **Modernized Text Handling**: Core components now use Adventure API
  - `CustomDisc.createItemStack()` uses `displayName()` and `lore()` methods
  - `DiscFragment.createItemStack()` uses Adventure Components
  - `JukeboxListener` titles and action bars use Adventure API
  - Player.sendTitle() → Title.title() with proper durations
  - Player.sendActionBar() → Adventure Component-based

### Fixed
- **Critical Playlist Bug**: Fixed playlists stopping after first song
  - `stopPlayback()` now has `clearPlaylistQueue` parameter
  - Playlist queue is preserved during auto-progression to next track
  - Playlist queue is cleared only when manually stopping playback
  - **This was a critical bug preventing playlists from working correctly!**

- **Resource Leak in UpdateChecker**: Properly closes HTTP connections
  - Added try-finally blocks with explicit connection.disconnect()
  - Added proper BufferedReader closing
  - Prevents resource exhaustion on update checks

### Deprecated
- **ColorUtil** class marked as @Deprecated (but still functional)
  - Kept for backwards compatibility
  - New code should use `AdventureUtil` instead
  - Legacy ChatColor/BungeeCord API calls will be phased out

### Technical
- **Deprecation Warning Reduction**: 70 → 1 warning (98.6% reduction)
  - Migrated all ColorUtil.colorize() calls to AdventureUtil
  - Replaced 7 internal deprecated method calls
  - Only remaining warning: GriefPrevention external API (cannot be fixed)
- Adventure API bundled in Paper 1.21+ (no extra dependency needed)
- Full backwards compatibility maintained
- All existing features continue to work unchanged

### Migration Notes
- Plugin users: No action required - update is fully compatible
- Plugin developers using API: Consider migrating to AdventureUtil for new code
- All color codes (&a, &#RRGGBB, gradients) continue to work as before

---

## [2.0.0] - 2026-01-06

### Added
- **Folia Support**: Full compatibility with Folia (region-threaded) servers
  - Added `folia-supported: true` flag in plugin.yml
  - New `SchedulerUtil` class for cross-platform scheduler abstraction
  - Automatic detection of Folia vs Paper/Spigot at runtime
  - Uses reflection to call Folia API without compile-time dependency
  - Region scheduler for location-based tasks
  - Entity scheduler for player/entity-specific tasks
  - Async scheduler for background operations
  - All 26 scheduler calls migrated to use SchedulerUtil

### Fixed
- **Folia Scheduler Bug**: Fixed UnsupportedOperationException when running on Folia servers
  - Corrected all reflection API calls to use proper Folia methods
  - `runLater()` now uses `Bukkit.getRegionScheduler().runDelayed()`
  - `run()` now uses `Bukkit.getRegionScheduler().run()`
  - `runAsync()` now uses `Bukkit.getAsyncScheduler().runNow()`
  - `runAsyncLater()` now uses `Bukkit.getAsyncScheduler().runDelayed()`

- **Sound Key Validation**: Removed strict validation that rejected valid sound keys
  - Now accepts both `namespace:key` format (e.g., `customjukebox:epic_journey`)
  - AND legacy `music_disc.name` format (e.g., `music_disc.traeumer`)
  - Validation errors for 18 discs resolved

- **ConfigManager NullPointerException**: Fixed initialization crash
  - Removed `isDebug()` check in `createBackup()` method during initialization
  - Plugin now starts without errors

- **PlaybackManager NullPointerException**: Fixed task storage crash on Folia
  - Added null checks before storing tasks in ConcurrentHashMap
  - Folia tasks return null (expected behavior) - now handled gracefully
  - Fixed in both `scheduleAutoStop()` and `scheduleLoopTask()` methods

### Technical
- New `SchedulerUtil` class with Folia detection via `io.papermc.paper.threadedregions.RegionizedServer`
- Uses Java reflection to avoid compile-time Folia dependency
- Fallback to Paper scheduler if reflection fails
- All scheduler methods return nullable `BukkitTask` (null on Folia)
- Enhanced error logging for scheduler failures
- Full backwards compatibility with Paper/Spigot servers

### Migration Notes
- Plugin now works on both Folia AND Paper/Spigot servers
- No configuration changes required
- Automatic server type detection
- Zero performance impact on Paper/Spigot servers

---

## [1.3.1] - 2026-01-02

### Fixed
- **Category Creation Wizard**: Fixed "Confirm" button not saving - consolidated event handlers to prevent event cancellation conflicts
- **Playlist Editor**: Fixed CDs not being addable to playlists - improved error handling and replaced language manager dependencies with direct messages
- **Disc Management**: Fixed "Cancel" button hanging when deleting from AdminGUI - added `fromExternal` tracking to determine correct return navigation
- **Playlist Auto-Progression**: Fixed playlists not automatically playing next song - removed unreliable `isFinished()` check in auto-stop scheduler
- **Command Playback Overlap**: Fixed sounds overlapping when using `/cjb play` multiple times - now uses block location instead of exact player position for consistent location keys

### Added
- **Tab-Completion for All Aliases**: All command aliases (`/cjb`, `/customjukebox`, `/jukebox`, `/jb`) now have full tab-completion support
- **Mute Command Warning**: Added clear warning that vanilla music discs cannot be muted due to Minecraft client-side limitations

### Improved
- **Thread Safety**: Replaced HashMap with ConcurrentHashMap in PlaybackManager for better thread safety
- **Config Versioning**: Added version tracking to `config.json` and `disc.json` for future migration support
- **Automatic Backups**: Config and disc files are now automatically backed up before saving (keeps last 5 backups)
- **File Size Limits**: Added maximum file size validation (10 MB for disc.json, 5 MB for config.json) to prevent corruption
- **Input Validation**: Added comprehensive input length limits for all GUI chat inputs with clear error messages
- **Color Code Processing**: Optimized ColorUtil to prevent double-processing of HEX color codes
- **Fragment ModelData Validation**: Added overflow prevention with max value of 1,000,000 for CustomModelData
- **Playlist Editor Logging**: Enhanced debug logging when disc identification fails

---

## [1.3.0] - 2025-12-31

### Added
- **Public API for Plugin Developers**: New `CustomJukeboxAPI` class allows other plugins to interact with CustomJukebox
  - Access to all discs, fragments, categories, and playlists
  - Playback control methods
  - Configuration access
  - Integration checks (WorldGuard, GriefPrevention)
  - Full JavaDoc documentation

- **Disc Categories System**: Organize discs by theme or genre
  - Define categories in `disc.json` with display name and description
  - Assign discs to categories using `"category"` field
  - Filter discs by category via API
  - Example categories: Ambient, Epic, Nature

- **Playlist System**: Group discs for sequential playback with automatic queue management
  - Create playlists in `disc.json` with list of disc IDs OR in-game with commands/GUI
  - Commands: `/cjb playlist list|info|play|create|delete|add|remove|rename|edit` for full control
  - **In-Game Management**: Create and edit playlists without touching config files
  - **GUI Editor**: Visual playlist editor with click-to-add/remove interface
  - Integrated into Admin GUI for centralized management
  - Automatic progression: Plays next disc when current finishes
  - Loop support: Endless playlist playback with `/cjb playlist play <name> loop`
  - Queue management: Tracks current position and handles transitions
  - Auto-save: All changes instantly saved to `disc.json`
  - Perfect for events, ambient music, or themed collections

- **Admin GUI System**: Comprehensive in-game administration interface
  - **Main Admin Panel**: Accessible via `/cjb gui` (Admin button at bottom for OPs)
  - **Disc Creation Wizard**: 7-step chat wizard for creating new discs
    - Step-by-step guidance through all fields (ID, name, author, sound, duration, category, model data)
    - Input validation at each step (ID format, sound key syntax, numeric values)
    - Shows existing categories during creation
    - Summary preview before final creation
    - Auto-save to `disc.json`
  - **Disc Editor**: Hybrid GUI/Chat system for editing existing discs
    - **GUI Selectors** for numeric values:
      - Duration selector with presets (30s, 60s, 90s... up to 600s) + custom input
      - Custom Model Data selector (1-20) + custom input
      - Category selector showing all categories + create new option
    - **Chat Input** for text fields (Display Name, Author, Sound Key)
    - All changes auto-save immediately
    - Delete confirmation dialog for safety
  - **Playlist Management**: Full playlist CRUD operations via GUI
    - Create new playlists (chat-based for now)
    - Edit playlists with visual disc selector (click to add/remove)
    - Delete playlists with confirmation
    - Live status indicators (✔ In playlist)
  - **Category Management**: Organize discs by categories
    - **Category Creation Wizard**: 3-step chat wizard for creating categories
      - ID input with validation (lowercase, no spaces)
      - Display Name with full color support (legacy, HEX, gradients)
      - Optional description field
      - Summary confirmation before creation
    - **Category Editor GUI**: Visual editor for existing categories
      - Edit display name with advanced color support
      - Edit description with color codes
      - Live preview of changes
      - Auto-save to `disc.json`
    - Create and delete categories
    - View disc count per category
    - Easy category assignment via selector in disc editor
  - No more manual JSON editing for most operations!

- **Advanced Color System**: Full support for modern Minecraft color codes
  - **HEX Colors**: Use `&#RRGGBB` or `#RRGGBB` format (e.g., `&#FF5555` for red)
  - **Gradient Support**: Create color gradients with `<gradient:#START:#END>text</gradient>`
    - Example: `<gradient:#FF0000:#0000FF>Epic Soundtrack</gradient>` creates red-to-blue gradient
    - Automatically interpolates colors across each character
  - **Legacy Codes**: Still supports standard codes (`&a`, `&b`, `&c`, etc.)
  - **Formatting**: Bold (`&l`), italic (`&o`), underline (`&n`), strikethrough (`&m`)
  - **Works Everywhere**: Display names, authors, descriptions, categories, lore
  - New `ColorUtil` class handles all color processing
  - Better user guidance in all wizards and editors

- **bStats Metrics Integration**: Anonymous plugin statistics
  - Track plugin usage and feature adoption
  - Custom charts for language, integrations, and feature usage
  - Helps improve plugin development
  - Fully privacy-respecting (configurable via bStats)

- **PlaceholderAPI Support**: 15+ placeholders for use in other plugins
  - `%customjukebox_version%` - Plugin version
  - `%customjukebox_total_discs%` - Total number of custom discs
  - `%customjukebox_hand_disc_name%` - Name of disc in main hand
  - `%customjukebox_hand_disc_author%` - Author of disc in hand
  - `%customjukebox_volume%` - Current playback volume
  - And many more! See API documentation for full list

- **Configurable Jukebox Hearing Radius**: New config option `playback.jukebox-hearing-radius`
  - Default: 64 blocks
  - Controls how far players can see disc title/actionbar when disc is inserted
  - Separate from sound playback radius (controlled by volume)

### Changed
- **Improved Tab-Completion**: Commands now suggest disc display names in addition to IDs
  - Works for `/cjb give`, `/cjb info`, `/cjb play`, `/cjb fragment`
  - Automatically strips color codes for better matching
  - More user-friendly for admins

- **Enhanced CustomDisc Model**: Added category field support
  - Discs can now be assigned to categories
  - Backwards compatible (category is optional)

- **Updated Dependencies**:
  - Added bStats 3.1.0
  - Added PlaceholderAPI 2.11.6 (soft-dependency)

### Fixed
- **Color Codes in Author Field**: Author field now properly supports color codes
  - Previously, color codes in author field were not processed
  - Now supports legacy codes (`&a-&f`), HEX colors (`&#FF5555`), and gradients
  - Resolves user report about non-working color codes in author field

- **Update Checker Version Comparison**: Fixed false "update available" notifications
  - Previously used string comparison instead of semantic versioning
  - Would incorrectly show "1.0.1" as newer than "1.3.0"
  - Now properly compares versions numerically (1.3.0 > 1.0.1)
  - Added support for development versions (shows "development version" message)
  - Handles version suffixes like "-SNAPSHOT" correctly

- **Category Management TODOs**: Completed all category management features
  - Category creation wizard now fully implemented (previously TODO)
  - Category editor GUI now fully functional (previously TODO)
  - All category operations work seamlessly through Admin GUI

### Technical
- New model classes: `DiscCategory`, `DiscPlaylist`
- Extended DiscManager with full CRUD methods for discs, playlists, and categories
- Added PlaybackManager queue system for automatic disc progression
- New command: `PlaylistSubcommand` with 9 actions (list/info/play/create/delete/add/remove/rename/edit)
- **New GUIs**:
  - `AdminGUI` - Main admin panel with navigation to all management functions
  - `DiscCreationWizard` - 7-step chat wizard for disc creation with validation
  - `DiscEditorGUIv2` - Hybrid GUI/Chat editor with selector menus
    - Duration selector with preset values
    - Custom Model Data selector (1-20)
    - Category selector with create option
  - `PlaylistEditorGUI` - Visual playlist editor with click-to-add/remove interface
  - `CategoryCreationWizard` - 3-step chat wizard for category creation
  - `CategoryEditorGUI` - Visual category editor with chat input for text fields
  - Extended `GuiSubcommand` with admin button integration
- **New Utilities**:
  - `ColorUtil` - Advanced color processing with HEX and gradient support
    - Replaces basic `ChatColor.translateAlternateColorCodes()` throughout plugin
    - Supports legacy codes, HEX colors, and gradients
    - Used by all managers and GUIs for consistent color handling
- **Enhanced UpdateChecker**:
  - Semantic versioning comparison with `compareVersions()` method
  - Proper handling of version parts (major.minor.patch)
  - Support for version suffixes (e.g., "-SNAPSHOT")
  - Three-way comparison (older/equal/newer)
- Auto-save system: All changes persist to disc.json immediately
- Wizard pattern for guided multi-step processes
- Selector pattern for numeric value selection with presets
- Added PlaceholderAPIExpansion for PAPI integration
- Improved modular architecture for future extensions
- All new features fully documented with JavaDoc

### Commands Added
- `/cjb playlist list` - List all available playlists
- `/cjb playlist info <playlist>` - Show playlist details and track list
- `/cjb playlist play <playlist> [loop]` - Play playlist with optional looping
- `/cjb playlist create <id> [display name]` - Create new playlist in-game
- `/cjb playlist delete <playlist>` - Delete existing playlist
- `/cjb playlist add <playlist> <disc>` - Add disc to playlist
- `/cjb playlist remove <playlist> <disc>` - Remove disc from playlist
- `/cjb playlist rename <old-id> <new-id>` - Rename playlist ID
- `/cjb playlist edit <playlist>` - Open GUI editor for playlist management

### Permissions Added
- `customjukebox.playlist` - Allows managing and playing playlists (default: op)

---

## [1.2.4] - 2025-12-23

### Fixed
- **Documentation**: Corrected `sounds.json` format in README and example resource pack
  - Sound event keys now use proper format: `music_disc.<disc_id>` (e.g., `music_disc.epic_journey`)
  - Sound file paths now use relative paths without namespace (e.g., `custom/music/epic_journey`)
  - Updated all example configurations to match working server format
  - Removed unnecessary subtitle fields from examples

- **Example Configuration**: Updated default `disc.json` with correct sound key format
  - Changed from `customjukebox:*` to `minecraft:music_disc.*` format
  - All three example discs now use consistent, working configuration
  - Matches the format validated on production servers

### Changed
- **Resource Pack Documentation**: Improved clarity in sounds.json setup instructions
  - Added clear explanation of sound event key format
  - Documented correct file path structure for .ogg files
  - Updated Important Notes section with accurate format information

---

## [1.2.3] - 2025-12-15

### Fixed
- **Critical Loop-Task Bug**: Fixed issue where music would spontaneously restart
  - Loop tasks were not properly canceled before creating new playback sessions
  - Multiple tasks could run simultaneously, causing unexpected music restarts
  - Now properly cancels old tasks BEFORE removing playback and starting new loop
  - Prevents task accumulation that led to random music playback
  - Added proper cleanup sequence: cancel task → stop sound → remove playback → start new

- **Play Command Parameter Order**: Fixed `/cjb play` command parameter parsing issues
  - Parameters can now be provided in any order (e.g., `/cjb play disc loop 100` or `/cjb play disc 100 loop`)
  - Previously required specific order (loop had to come before range)
  - Added duplicate parameter detection with debug logging
  - Improved error messages for invalid parameters with helpful suggestions
  - Enhanced tab-completion to only suggest unused parameters

### Technical
- Improved task cancellation logic in `scheduleLoop()` method
- Better cleanup sequence prevents orphaned tasks
- Enhanced debugging output for loop operations
- Refactored PlaySubcommand parameter parsing with order-independent logic
- Added `playback-invalid-parameter` language key to all 4 language files

---

## [1.2.2] - 2025-12-15

### Added
- **Mute/Unmute Commands**: New commands for quickly silencing and restoring playback
  - `/cjb mute [restart]` - Mutes all playback by setting volume to 0
  - `/cjb unmute [restart]` - Restores previous volume before muting
  - Saves volume state before muting for seamless restoration
  - Optional restart parameter to apply changes to active playbacks immediately

- **Volume Presets**: Quick volume adjustment with preset names
  - `silent`/`mute`/`off` → 0.0
  - `quiet`/`low`/`soft` → 0.5
  - `normal`/`default`/`medium` → 1.0
  - `loud`/`high` → 2.0
  - `max`/`maximum`/`full` → 4.0
  - Example: `/cjb volume quiet` or `/cjb volume loud restart`

### Changed
- **Improved Volume Control**: Enhanced volume adjustment system
  - Finer granularity: 41 volume levels (0.0 to 4.0 in 0.1 increments)
  - Enhanced tab-completion with all numeric values and preset names
  - Better precision display with 2 decimal places (e.g., 1.50)
  - Improved error messages for invalid volume values

### Technical
- Added mute state tracking in ConfigManager
- Volume presets support multiple aliases for user convenience
- Enhanced tab-completion system for better user experience
- All new features fully translated in 4 languages (EN, DE, ES, IT)

---

## [1.2.1] - 2025-12-14

### Fixed
- **Critical Configuration Fix**: Fixed inconsistency between `disc.json` and `sounds.json` default configurations
  - Updated example disc entries to use correct sound keys (`customjukebox:epic_journey`, etc.)
  - Now includes three example discs: Epic Journey, Ocean Dreams, and Forest Walk
  - All sound keys now properly match between disc.json and sounds.json

### Added
- **Startup Validation System**: Plugin now validates all disc configurations on startup
  - Checks for missing or invalid sound keys
  - Verifies sound key format (namespace:sound_name)
  - Warns about missing duration settings
  - Provides clear error messages for configuration issues
  - Helps identify why sounds might not play

- **Enhanced Error Handling**: Significantly improved error feedback when sounds fail to play
  - Detailed console logs with troubleshooting steps
  - In-game notifications to players when sound playback fails
  - Clear explanations of possible causes (missing resource pack, wrong sound key, etc.)
  - Suggestions for how to fix common issues

### Changed
- **Documentation Update**: Completely updated resource pack README.md
  - All YAML references replaced with JSON (disc.json, config.json)
  - Corrected configuration examples to use JSON syntax
  - Updated command examples and troubleshooting guides
  - Added information about server.properties resource pack configuration
  - More detailed testing and debugging instructions

### Technical
- Added `validateDiscs()` method in DiscManager for configuration validation
- Enhanced `playSound()` method in PlaybackManager with comprehensive error reporting
- Improved logging output for debugging sound playback issues
- Better player feedback when sound playback fails

---

## [1.2.0] - 2025-12-13

### Added
- **Playback Range System**: Control who hears custom music with range parameters
  - `/cjb play <disc> [loop] [global|world|<radius>]`
  - **global**: All players on the server hear the music
  - **world**: Only players in the same world
  - **Custom radius**: Specify exact block radius (e.g., `50`, `100`, `200`)
  - Perfect for server-wide events, world-specific ambiance, or localized music zones

- **Volume Restart Feature**: `/cjb volume <value> restart`
  - Apply volume changes to already playing songs instantly
  - Automatically restarts all active playbacks with new volume
  - No need to manually stop and restart songs anymore

- **PlaybackRange Model**: New enum system for managing playback ranges
  - Clean API for future range-based features
  - Supports NORMAL, GLOBAL, WORLD, and CUSTOM_RADIUS types
  - Automatic parsing from command parameters

### Changed
- Extended PlaybackManager with range-aware sound distribution
- Enhanced JukeboxPlayback model to track playback range
- Loop functionality now preserves range settings across restarts
- Updated all command usages to include new parameters

### Added (Features)
- Smart player detection based on range type
- Efficient range checking for different playback scopes
- Range information displayed in playback success messages

### Updated (Translations)
- All 4 languages updated with new range-related messages (DE, EN, ES, IT)
- New messages: `playback-range-info`, `playback-invalid-range`, `volume-restarted`
- Updated command usage strings for all languages

### Technical
- New `PlaybackRange` class for type-safe range management
- Added `restartAllPlaybacks()` method to PlaybackManager
- Extended `shouldPlayerHearPlayback()` with range logic
- Improved playback session management

---

## [1.1.0] - 2025-12-13

### Added
- **Direct Playback Command**: New `/cjb play <disc> [loop]` command allows OPs to play custom discs directly at their location
  - Supports optional `loop` parameter to enable infinite playback
  - No need to place physical jukeboxes anymore
  - Perfect for events and server-wide music

- **Stop Command**: New `/cjb stop` command to stop all active playbacks
  - Instantly stops all looping and non-looping sounds
  - Useful for ending events or silencing the server

- **Volume Control**: New `/cjb volume <0.0-4.0>` command for centralized volume management
  - Adjusts playback volume globally
  - Range: 0.0 (silent) to 4.0 (maximum)
  - Persists in config.json
  - Without arguments, displays current volume

- **Loop Functionality**: Songs can now be played in an infinite loop
  - Automatically restarts when the song ends
  - Configurable via command parameter
  - Perfect for background music during events

- **Playback Configuration**: New `playback` section in config.json
  - `volume`: Global playback volume (default: 4.0)
  - `default-loop`: Default loop behavior (default: false)

### Changed
- Updated PlaybackManager to support dynamic volume control
- Extended JukeboxPlayback model with loop flag tracking
- Volume is now read from config instead of hardcoded constant
- PlaybackManager now schedules loop tasks instead of just stop tasks

### Added (Permissions)
- `customjukebox.play` - Allows playing discs directly (default: op)
- `customjukebox.stop` - Allows stopping all playbacks (default: op)
- `customjukebox.volume` - Allows changing volume (default: op)

### Added (Translations)
- Added German translations for all new commands and messages
- Added English translations for all new commands and messages
- Added Spanish translations for all new commands and messages
- Added Italian translations for all new commands and messages

### Updated (Documentation)
- Updated README.md with new commands and features
- Updated plugin.yml command usage
- Version bumped to 1.1.0

---

## [1.0.0] - 2025-01-01

### Added
- Initial release of CustomJukebox plugin
- Custom music disc system without replacing vanilla discs
- Fragment collection and crafting system
- GUI for disc selection
- Parrot dancing synchronization
- WorldGuard and GriefPrevention integration
- Multi-language support (EN, DE, ES, IT)
- Resource pack integration for custom sounds
- Admin commands: `/cjb give`, `/cjb fragment`, `/cjb reload`
- User commands: `/cjb list`, `/cjb info`, `/cjb gui`, `/cjb help`
- Customizable disc metadata (title, author, duration, lore)
- Automatic playback duration management
- Loot table integration for fragments
- Creeper fragment drops (skeleton kills creeper)
- Shapeless crafting recipes for discs
