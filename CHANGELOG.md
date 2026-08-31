# Changelog

All notable changes to CustomJukebox will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.6.0] - 2026-08-31

### Fixed
- **`/cjb music volume` did nothing until the current track ended.** It stored the value and said so, which on a looping ambient zone means minutes of no audible change — while `/cjb music on|off` right next to it took effect at once. Setting a personal volume now restarts what that player is hearing, and nobody else's playback is touched. It also accepts the same presets, percentages and decibels as the other two volume commands.
- **Every volume command now says when a value cannot get any louder.** At 1.0 a sound is already at full loudness; above it Minecraft widens the audible radius (`volume * 16` blocks) instead of raising the gain. Comparing 4.0 against 1.0 next to a jukebox therefore sounds identical, and the command looks broken. All three commands now say so and name the resulting radius.
- **`/cjb volume` looked broken for an admin who had set a personal volume.** A personal volume replaces the server volume rather than scaling it, so the admin's own command changed nothing they could hear. The command now says so, and names the reset that undoes it.
- **Chat confirmations printed the markup they had just applied.** Setting a name or author replied with the raw text — `<gradient:#ff0000:#0000ff>Name</gradient>` — while the item itself came out correctly coloured, because the confirmation went through a serializer that only understands `&` codes. Values a user wrote are now rendered the way the game renders them.
- **Long names ran past the edge of a GUI.** Inventory titles are drawn into a fixed-width frame, and a disc or playlist name was pasted in whole. Names are now measured by their *visible* length and shortened if they do not fit — markup is not a guide to width, since a gradient can be forty characters of tags around two visible ones, and cutting the raw string would slice a tag in half. The same limit applies to the preview lines in tooltips.
- **Colour codes in a disc's author field were printed instead of applied.** `&eArtist` showed up in the tooltip as the literal text `<yellow>Artist`. The author line is assembled as `§7By: §e` plus the author, and MiniMessage rejects a section sign outright — so every such call threw, fell back to the legacy serializer, and that serializer printed the already-converted tags as visible text. Unformatted names came through the fallback looking correct, which is why this went unnoticed. The display name, which is not prefixed that way, always worked, so formatting appeared to be supported everywhere except the one field where it silently was not.

  Legacy codes are now recognised with either marker (`&` or `§`) and in either case, so `&c`, `§c` and `&C` all work, hex (`&#ff8800`) and gradients survive, and the same fix covers every other place that mixed the two — fragment lore, the jukebox subtitle, GUI text.
- **Language files on disk fell behind and never caught up.** A shipped message is only written out when the file does not exist yet, so a server that has run since an early version keeps its original file forever — one long-running server was missing 170 of 226 messages. Nothing was visibly broken, because missing keys resolve through the bundled defaults, but an admin cannot reword or translate a message that is not in the file. Messages the shipped file has and the server's copy does not are now written into it on every start. Strictly additive: an existing line is never touched, so rewordings and translations survive, and comments are preserved.
- **A zone's own volume followed the server volume for anyone with a personal volume set.** The per-listener calculation divided the zone's volume by the server volume, so a zone deliberately set to a fixed level swung with a setting it had opted out of — at the default server volume of 4.0, a zone at 0.2 collapsed to a twentieth of the player's setting, and lowering the server volume made that zone jump up instead of down. A zone with its own volume is now absolute; only zones left on `inherit` follow the server. A personal volume still applies on top, as a factor where 1.0 means "as configured".
- **`/cjb volume ... restart` ignored ambient zones.** It restarted jukebox playbacks only, so a zone set to `inherit` kept playing at the old volume until its current track ended — the flag looked broken wherever ambient music was running. The same applied to `/cjb mute` and `/cjb unmute` with `restart`. Zones on `inherit` are now restarted as well.
- **`norestart` had no effect.** A zone's volume is part of its playback signature, so saving the zone restarted it before the flag was ever read. Saving can now deliberately leave a running timeline alone, which is what the option always claimed to do.
- **Tab completion never offered a quiet value.** `/cjb zone volume <zone>` suggested `inherit, 1, 2, 3, 4` only. Since `1.0` is already full loudness — anything above it widens the audible radius rather than making the sound louder — "turn it down" reliably ended at 1 and stayed loud. The suggestions now start at the quiet end: presets plus `0.1`, `0.25`, `0.5`, `0.75`, `1`.

### Added
- **Volume in decibels**, for both the global and the zone command: `-6db` is half, `-12db` a quarter, `-20db` a tenth. The linear scale is hard to judge by ear — 0.5 does not sound half as loud — while a decibel step always sounds like the same step. Positive values are refused, because above 1.0 Minecraft widens the audible radius instead of raising the volume.
- `/cjb zone volume` takes a `norestart` option for anyone who would rather the current track finish at its old volume.
- Zone volume accepts the same presets as the global volume (`silent`, `quiet`, `normal`, `loud`, `max`) and percentages such as `30%`, instead of bare numbers only.
- Volume feedback states the percentage and decibels — `0.30 (30%, -10.5 dB)` — and the help line spells out that `1.0` is full loudness and that higher values only widen the radius. The scale reads as if 1 were low; it is not.

### Changed
- Both volume commands share one parser (`VolumeUtil`), so presets, percentages and ranges cannot drift apart between the global volume and a zone's.

---

## [3.5.1] - 2026-08-26

### Changed
- **The example resource pack is now a complete, working pack** rather than a sketch. It ships `pack.mcmeta`, `sounds.json`, item definitions, models and placeholder disc textures for exactly the three discs `disc.json` creates on first start — add three `.ogg` files, zip it, host it, and custom music works end to end. Its README is shipped inside the pack and covers conversion, hosting, SHA-1, custom textures, pack formats per Minecraft version and the failure modes that produce silence.
- **The documented resource pack layout now matches a real, running server.** The pack guide and the shipped `disc.json` disagreed about where sounds live: the guide in the main README described `assets/minecraft/` with unprefixed sound keys, the example pack used a `customjukebox:` namespace, and the shipped discs followed the example. Anyone following the main README got a pack that did not match the discs the plugin had just created — silence, with no error on either side. Everything now uses the layout verified on a production server: sounds under `assets/minecraft/sounds/`, `sounds.json` event keys of the form `music_disc.<id>`, and the same key in `disc.json`. Existing servers are unaffected — `disc.json` is only written when it does not exist, and any sound key that resolves keeps working, namespace or not.
- The disc creation wizard's sound key prompt now names the `sounds.json` event key it is actually asking for, instead of offering two namespaces without saying what the value refers to.

### Fixed
- **The plugin loaded on servers it cannot work on.** `api-version: '1.21'` lets a 1.21.0–1.21.3 server accept the jar, but disc items are built through the `CustomModelDataComponent` float API that only exists from 1.21.4 — every disc would have failed with a `NoSuchMethodError` from deep inside item creation. The server version is now checked at startup and anything below 1.21.4 is refused with a message that names the version and the reason. Verified against real `getBukkitVersion()` strings from 1.21 through 26.2, including 1.21.10, which a naive string comparison would have rejected.
- **The documented way to give a disc a custom texture could not work on any supported version.** Both guides showed the pre-1.21.4 `overrides` / `predicate` block with an integer `custom_model_data`. Since 1.21.4 the plugin writes the `custom_model_data` *component* (floats), which a legacy predicate cannot match, and item appearance is decided in `assets/<ns>/items/<item>.json`. Both guides now document `range_dispatch` with a `fallback`, and the example pack ships working item definitions.
- Corrected pack format numbers: the table stopped at 1.21.11 and did not cover 26.x (26.1–26.1.2 is `84`, 26.2 is `88`). `example-resourcepack/pack.mcmeta` claimed a range its own README contradicted.
- The README footer had claimed version 3.1.0 since that release.

---

## [3.5.0] - 2026-08-26

### Added
- **Per-player sound delivery hooks** — `CustomSoundPlayEvent` and `CustomSoundStopEvent`, both cancellable, fire once per player immediately before a custom disc sound is sent or stopped. Every delivery path passes through them: jukebox playback, a listener attaching to a running playback, `/cjb music on`, and ambient zones (including radio). Companion plugins that need to deliver a sound differently for some players — the Bedrock extension speaks a different sound namespace — cancel the event and play it themselves. Cancelling suppresses only that one sound packet; the player stays a tracked listener, so progress bar, `/cjb skip` and stop handling keep working for them.
- **Disc items now carry their disc id as a `custom_model_data` string tag** in addition to the existing float. Java resource packs are unaffected (they select models by the float); the string gives Geyser an exact, unambiguous key to match a Bedrock item against, which a float threshold cannot provide. Disc items handed out before this version keep working but lack the tag — re-issue them with `/cjb give` if Bedrock textures matter.

---

## [3.4.0] - 2026-08-16

### Added
- **`/cjb skip`** — skips the track you are currently hearing. Resolves the source the same way the progress bar does: an ambient zone you are standing in takes precedence over a jukebox you are merely in range of. In a `synced` zone the whole zone advances; in an `individual` zone only your own cursor moves on. There is deliberately no "previous track": a resource-pack sound can only be started from its beginning, so going back would just be skipping forward through the rest of the playlist.
- **Progress bar** — a boss bar showing the current track and how far into it you are, for jukeboxes, zones and radio alike. The elapsed time is the plugin's own bookkeeping (the server never learns the client's real playhead), which is exact for anyone present when the track started. Configurable via `playback.show-progress-bar` and `playback.progress-update-ticks`. This finally uses the timing methods `JukeboxPlayback` has carried unused since 1.x.
- **Shuffle and repeat modes for playlists**: `/cjb playlist play <id> [shuffle] [loop|repeat-one|off]`, order-independent. Shuffling reshuffles on every lap (and avoids repeating a track across the wrap) so a looping playlist is not one fixed sequence forever.
- **Shuffle for ambient zones**: `/cjb zone shuffle <id> <true|false>`, plus a toggle in the zone editor GUI.
- **Server radio** — a new zone type that reaches every player in every world: `/cjb zone global <id>`. It reuses the whole zone machinery, so a radio station has the same playlist, loop, shuffle, playback mode and volume options, and a local zone with a higher `priority` still overrides it for players standing inside that zone.
- **Personal music settings** (`/cjb music`, permission `customjukebox.music`, default true): `on`/`off`/`toggle` to opt out of plugin music entirely, `volume <0-4|reset>` for a personal volume, and `status` to see your settings plus what is currently playing. Both persist across sessions in a new `players.json`. A personal volume replaces the server volume rather than scaling it, so it stays predictable when an admin changes the global value; a server-wide `/cjb mute` still wins. Turning music off silences the current track immediately rather than at its end.
- **Favourites** (`/cjb favorite`, permission `customjukebox.favorite`, default true): `add`/`remove`/`toggle`/`list`/`clear`, and `play [shuffle] [loop|repeat-one] [global|world|<radius>]` to play them as an ad-hoc playlist. Discs can also be favourited by shift-clicking them in the disc menu, which shows a ★ for entries already on the list.

### Fixed
- **`/cjb music on` stayed silent until the next track.** A jukebox/playlist listener set is only filled when a track *starts*, and turning music off removed the player from it - nothing put them back mid-track. Re-enabling now re-attaches immediately: zones re-evaluate in the same tick instead of at the next scan, and any playback within earshot starts for that player. The track necessarily restarts from its beginning for them (the sound engine cannot seek), which the plugin now says out loud instead of leaving it to be discovered.
- **`/cjb skip` reported "nothing playing" when something clearly was.** It resolved the playback strictly by listener membership, so a player who had just re-enabled their music - or who walked up after the track started - was told there was nothing to skip. Skip now resolves by earshot, preferring what the player actually hears and falling back to what is playing in range.
- Both were found within minutes of the first real in-game test, and both traced back to the same root: listener sets are only populated at track boundaries.

### Changed
- **`/cjb playlist play` and `/cjb favorite play` accept a playback range**: `[global|world|<radius>]`, the same vocabulary `/cjb play` already used. The queue stored a range all along and passed it to every following track - only the command never exposed it, so playlists were locked to `normal`. For permanently server-wide music a radio zone (`/cjb zone global`) remains the better tool; a global-range playlist is a one-off from your position.
- **`all` is no longer a repeat-mode alias.** It means "server-wide range" in `/cjb play`, and now that the play commands accept both kinds of flag it would have meant two different things depending on the command. `loop`, `true` and `yes` still select repeat-all.

### Notes
- Volume changes — personal or server-wide — apply from the next track. A sound already handed to the client cannot be adjusted, only restarted.
- `players.json` only stores players who actually changed something; entries that are all-default are dropped on save.

## [3.3.0] - 2026-07-19

### Added
- **Ambient zones — auto-playing looping playlists for regions/areas**: a zone continuously loops a playlist and starts automatically for every player who enters its area, with no disc or command needed once configured. Ideal for lobby/hub background music or event ambience. Zones are stored in a new `zones.json` and managed via `/cjb zone` (permission `customjukebox.zone`, default op) or the `/cjb zone edit <id>` GUI.
  - **Three area types**: a **radius** around a point (no dependencies), a named **WorldGuard region** (`/cjb zone region <id> <region>`), or a **cuboid** box between two corner positions (`/cjb zone pos1 <id>` / `/cjb zone pos2 <id>`, covering the full block range between them like a WorldGuard selection). Overlapping zones resolve by `priority` (highest wins).
  - **Height range** (`/cjb zone height <id> <full|limited>`): by default (`full`) a zone ignores the Y axis — a radius zone is a vertical **cylinder** and a cuboid an infinite **column**, so players hear it at any height within the horizontal footprint. `limited` bounds the zone in Y too (a true 3D sphere/box). Defaults to `full` so lobbies aren't limited to a few blocks of vertical range.
  - **Whole-playlist loop**: each zone loops its playlist back to the first track at the end.
  - **Per-zone playback mode** (`/cjb zone playback <id> <synced|individual>`):
    - `synced` (default): one shared timeline — everyone in the zone hears the same track at the same time. Best for events. Late arrivals are handled by two **sync modes** (`/cjb zone sync <id> <immediate|next_track>`): `immediate` starts the current track from its beginning for a player who walks in mid-track (never silence, slightly offset); `next_track` waits for the next track boundary so they join perfectly in sync. This is because Minecraft's sound engine cannot seek — a running track cannot be joined at its exact current position; everyone re-syncs at each track boundary regardless.
    - `individual`: each player runs the playlist on their own from the moment they enter, always hearing **complete tracks start-to-finish** (no mid-song switching), looping the whole playlist — but not in sync with other players. Best for lobby/background music. `syncMode` does not apply here.
  - **Per-zone options**: `playlist`, `loop`, `playback`, `syncMode`, `fullHeight` (see Height range), `volume` (`inherit` or 0–4, controlling audible radius), `priority`, `enabled`. Set via subcommands or the editor GUI; `/cjb zone radius|center`, `pos1|pos2` and `region` shape the area from your position.
  - **Config**: master switch `ambient-zones.enabled` in `config.json`; scan cadence `settings.scan-interval-ticks` in `zones.json` (default 20 ticks). A single scanner assigns players to zones each interval.
  - **Folia-safe**: the scanner dispatches each player's evaluation and sound playback to that player's region thread; a new global scheduler path in `SchedulerUtil` drives the per-zone (and per-player, in `individual` mode) track timers. Discs without a configured duration are skipped in zones (a zone needs durations to advance/loop).
- **Pagination for every list**: the jukebox disc menu, `/cjb gui`, the admin panel's disc/playlist/category lists, the playlist editor and the disc editor's category selector previously filled their slots and silently dropped everything that did not fit - those entries were unreachable. All of them now page, with a page indicator showing the total count. `/cjb list` is paged too (`/cjb list <page>`).
- **`ambient-zones.sound-category`**: zone music defaults to `RECORDS` (the jukebox category). Because stop-sound packets are addressed by sound key *and* category, the same disc playing in a zone and in a nearby jukebox would cut each other off; servers hitting this can now move zones to their own category, e.g. `MUSIC`.
- **`settings.backup-min-interval-minutes`** (default `5`): timestamped backups were written on every single save, so a short GUI editing session rotated the whole retained history away within a handful of clicks - the state worth rolling back to was the first one deleted. Backups are now throttled; `0` restores the previous behaviour.

### Fixed
- **Loot generation crashed with `max-loot-discs: 0`**: `settings` allows `0` (and the value is clamped to it), but the loot listener passed it straight to `Random.nextInt()`, which throws on a non-positive bound - every loot chest generated an `IllegalArgumentException`. `0` now correctly means "no loot fragments". A `creeper-drop-chance` or `loot-chance` of `0` could likewise still roll true (`nextDouble()` can return exactly `0.0`); both are now hard-disabled at `0`.
- **Deleting a disc left its fragment behind**: the fragment registry was only rebuilt on a full reload, so creeper drops, loot chests and `/cjb fragment` kept handing out fragments for a deleted disc - fragments that could never be crafted into anything. Fragments are now added and removed together with their disc (including when `fragmentCount` is edited).
- **Spanish and Italian were missing all 30 playlist messages**: `es.yml` and `it.yml` never received the playlist keys added in the 2.x line. They fell back to English and logged a `Missing translation key` warning on every use. All four language files now carry an identical key set.
- **`/cjb volume` while muted**: setting a volume left the mute flag set, so a later `/cjb unmute` silently overwrote the value that had just been set with the pre-mute volume. An explicit volume change now clears the mute state. As a side effect `/cjb mute`, `/cjb unmute` and `/cjb volume` write `config.json` once instead of twice.
- **Clicks in the player's own inventory were treated as disc-GUI selections**: with the jukebox/`/cjb gui` disc menu open, clicking a custom disc in your own inventory inserted it into the jukebox or (with `customjukebox.give`) handed out another copy. Only clicks inside the menu itself count now.
- **Category creation wizard ignored the ID length limit**: it validated the format by hand instead of using `InputValidator`, so arbitrarily long category IDs could be written into `disc.json`. Display name and description are now length-checked too, and `CustomModelData` is bounded in the disc wizard and editor.
- **Ambient zones reported success while staying silent**: `/cjb zone ...` confirmed every change even when the zone could not play (no playlist, playlist with no usable discs, missing WorldGuard region, unloaded world, ...) - the reason only appeared in the console. Commands, `/cjb zone info` and the editor GUI now name the blocking condition in-game, in all four languages.
- **Ambient zones missed playlist edits**: a zone snapshots its playable discs when its timeline starts, so adding or removing a disc - or changing a disc's sound or duration - did not reach a running zone until `/cjb zone reload`. Affected zones are now rebuilt automatically.
- **Ambient zones: editing a zone restarted the music**: every setting - including priority, height range and the area - tore the timeline down and restarted the playlist from track 1 for everyone inside. Only playlist, loop, playback mode and volume restart playback now; area, priority, height and sync mode are picked up live.
- **Ambient zones: teardown races**: a track timer that fired while a zone was being deactivated (edit, `/cjb reload`, disable) could start a track nothing would ever stop again. Timeline advance, restart and teardown now run under the same per-zone lock, and per-player cursors in `individual` mode carry an explicit cancellation flag instead of relying on task cancellation alone.
- **Ambient zones in an unloaded world are no longer refused**: a zone whose world was not loaded yet (late-loading world managers) was skipped until the next reload. It now activates and simply matches nobody until the world exists.
- **Chat wizards could process two messages at once**: the disc and category creation wizards read their session non-atomically, so two quickly-sent chat lines could both be applied to the same step. Both now claim the session before handling input, matching the fix already applied to the other chat inputs in 3.2.0.
- **MiniMessage tags from user input**: names, lore and titles are parsed with MiniMessage, which accepted `<click:run_command:...>`, `<hover:...>` and data tags from chat-wizard input and config files. Parsing is now restricted to colour and decoration tags; everything else stays literal text.
- **Admin panel resolved playlists and categories by parsing item lore**: the ID is now carried in the item's persistent data, so a display name containing the same prefix can no longer misdirect an edit - or a deletion.
- `config.json` and `disc.json` are read as UTF-8 explicitly (they are written as UTF-8), and malformed entries inside `discs`, `categories` or `playlists` are skipped with a warning instead of aborting the whole load.

### Changed
- **Config files are written off the server thread**: `config.json`, `disc.json` and `zones.json` used to be saved inline on whichever thread triggered the change - for GUI clicks and commands that is the main (or, on Folia, a region) thread, which then paid for a backup copy, a backup-directory scan, serialisation and a file move. Saves now hand a snapshot to a single writer thread. Writes stay strictly ordered, a file saved again while an earlier save is still queued simply replaces the queued content (a burst of GUI clicks collapses into far fewer writes), reads flush pending writes first, and plugin disable flushes everything before shutting the writer down.
- Writes are atomic on all three files (temp file + atomic move), so a failed save can never truncate the real one. `disc.json`'s "restore from backup on save failure" path was removed with it: the original file is untouched by a failed write, so restoring only rolled the config back to an older state for no reason.
- JSON tree edits and the snapshot taken for saving are guarded by a per-file lock. Gson's `JsonObject` is not thread-safe and on Folia two region threads can run config-changing commands simultaneously.
- Editing a single disc field no longer reloads `disc.json` from disk and rebuilds every disc, category and playlist (including the validation log) - only the edited disc is rebuilt. Renaming a playlist and editing a category now write the file once instead of twice, and creating a zone writes once instead of twice.
- The disc editor's CustomModelData presets are `1001-1020`, matching the convention used by the bundled `disc.json` and the README; the old `1..20` list broke the texture mapping of every shipped disc. Values already used by another disc are marked in the selector and warned about on manual input.
- Backup handling for `config.json`, `disc.json` and `zones.json` lives in one shared helper instead of three near-identical copies.
- A disc without a category is stored as having none, instead of being given a synthetic `uncategorized` category that made `hasCategory()` always true and was written into `disc.json` for every disc.
- On Folia, a startup warning names WorldGuard-backed ambient zones as a possible source of trouble (WorldGuard is not Folia-safe; the lookups are read-only and failure-tolerant, but `radius`/`cuboid` zones avoid the dependency entirely).

---

## [3.2.0] - 2026-07-12

### Added
- **Configurable "Now Playing" announcements**: the title shown in the center of the screen and the actionbar message when a custom disc starts can now be disabled individually via `playback.show-title` and `playback.show-actionbar` in `config.json` (both default to `true`, matching previous behavior).
- **Automatic config merging**: on load, `config.json` and `disc.json` are compared against the plugin's bundled defaults and any keys introduced by a newer version are added automatically, without overwriting existing values. New options (like the `show-*` flags above) now appear in existing config files after an update/`reload` instead of having to be added by hand. In `disc.json` the `discs`/`categories`/`playlists` sections are treated as user content and are never re-seeded with the default examples, so deleted example discs do not reappear.
- **Configurable backup count**: the number of timestamped `config`/`disc` backups kept is now set via `settings.max-backups` (default `5`, same as before). Set it to `0` to disable backups entirely — existing backup files are then pruned on the next save. Values are clamped to a maximum of 100. Previously the count was hard-coded to 5.

### Fixed
- **GUI system rebuilt on InventoryHolder**: All plugin GUIs (Admin panel, disc/category/playlist editors, jukebox disc GUI) are now identified via a custom `InventoryHolder` instead of window titles and per-player context maps. This fixes a family of serious bugs:
  - Closing a GUI with ESC no longer leaves a stale session that hijacked clicks in chests/containers opened afterwards (previously this could **overwrite real chest contents** with GUI items and spawn free disc copies via the delete-confirmation dialog).
  - Opening the playlist editor or disc editor from the admin panel no longer breaks their click handling (the close event of the previous menu wiped the new context).
  - The same click is no longer processed by two GUIs at once (admin panel + disc editor).
  - The jukebox disc GUI no longer depends on a lossy title round-trip — hex/gradient `gui-title` values previously broke the comparison and made GUI items freely extractable.
  - `/cjb gui` uses the same holder-tagged inventory, and all GUI click handlers cancel the click *before* any session/permission checks, so a missing session can never make GUI items extractable.
- **Item safety in GUIs**: `InventoryDragEvent` is now cancelled in all GUIs (items could previously be dragged into a GUI and lost), and collect-to-cursor double-clicks can no longer pull items out.
- **Logout cleanup**: All GUI/wizard sessions are cleared on player quit. Previously a player who logged out mid-wizard had every chat message swallowed and processed as wizard input after rejoining.
- **Folia: auto-stop/loop tasks are now cancellable**: `SchedulerUtil` returns a platform-independent `TaskHandle` (wrapping Folia's `ScheduledTask.cancel()`). Stale auto-stop tasks could previously stop a *newer* playback at the same jukebox (e.g. disc B stopped mid-song at disc A's old end time), and orphaned loop tasks multiplied. Tasks additionally verify they still belong to the same playback session when firing.
- **Playlists survive volume/mute restarts**: `/cjb mute`, `/cjb unmute` and `/cjb volume <x> restart` no longer wipe the running playlist queue (playback previously went silent after the current track).
- **Skeleton-kill fragment drops actually work**: the guaranteed drop when a skeleton kills a creeper never triggered (`getKiller()` only returns players). Now resolved via the last damage cause, including arrows, covering Skeleton/Stray/Bogged.
- **Off-hand discs are recognized**: interaction handlers now use the hand that triggered the event. Off-hand discs previously got inserted by vanilla without the custom sound; the GUI also opened twice. With the GUI enabled, an off-hand disc now takes priority over opening the GUI.
- **Ejecting by disc swap stops the sound**: right-clicking an occupied jukebox while holding another disc lets vanilla eject the record — the custom sound now stops with it (previously it kept playing over the empty jukebox).
- **Hopper support**: a hopper extracting the disc stops the custom sound (it previously played to the end); a hopper inserting a custom disc starts the custom sound. Extraction is verified one tick later so transfers cancelled by protection plugins don't cut the music.
- **Disc identity via PersistentDataContainer**: disc and fragment items now carry their ID in the PDC. Two discs sharing the same CustomModelData are no longer confused when editing/deleting/giving (legacy items still match by model data). Note: deleting a disc and recreating it under a new ID invalidates already-issued items of the old ID — they intentionally no longer match by texture alone.
- **Thread-safety on Folia**: disc/category/playlist/language registries are now `ConcurrentHashMap` (a `/cjb reload` could previously throw `ConcurrentModificationException` in event handlers on other region threads); `UpdateChecker` results, mute state and playback flags are `volatile`; `onDisable` no longer calls the Bukkit scheduler on Folia (threw `UnsupportedOperationException`).
- **config.json saves atomically** (temp file + atomic move, like disc.json) and `/cjb reload` reloads the persisted mute state.
- **Validation restored**: disc/category/playlist IDs must be `[A-Za-z0-9_-]+` (IDs with spaces were previously creatable but unusable in commands); sound keys are validated as resource locations again — the wizard's error messages for these cases were dead code.
- **Gradients fixed**: `<gradient:#RRGGBB:#RRGGBB>` was destroyed by the hex pre-pass in `AdventureUtil` and never rendered; hex colors are no longer downsampled to legacy codes on serialization.
- **`/cjb volume`**: tab-completion no longer suggests locale-formatted values like `0,5` that the parser rejects; `NaN` no longer passes the range check.
- **Permission `customjukebox.fragment`** is now actually checked by `/cjb fragment` (previously it required `customjukebox.give`, and the declared node was dead). Migration note: servers that granted `customjukebox.give` directly (not via `customjukebox.admin`) must additionally grant `customjukebox.fragment` to keep `/cjb fragment` access.
- **Playback tracking leak**: discs without a configured duration no longer leave a tracking entry behind forever (fallback cleanup after 1 hour); `loop` without a duration now logs a warning instead of being silently ignored.
- **Orphan cleanup**: deleting a disc removes it from all playlists; deleting a category detaches it from its discs.
- **Deleting playlists/categories** in the admin panel now requires a confirming second right-click (they were previously deleted instantly and irreversibly); failed deletions report an error.
- **Chat-input hardening**: input modes are consumed atomically (rapid double messages were processed twice), the disc editor's chat handler respects cancelled events, and all chat/wizard handlers re-check the admin permission before applying input.
- **Log hygiene**: per-click and per-playlist-progression INFO logs are now behind the debug flag.

### Changed
- `ParrotDanceListener` no longer schedules hundreds of empty per-parrot tasks (the vanilla dance animation cannot be forced via the Bukkit API); it now shows a short note-particle burst above nearby parrots instead.

### Performance
- **Fewer disk writes on delete**: deleting a disc or category now rewrites `disc.json` a single time instead of once per affected playlist/disc (each of which previously also created a backup and scanned the backup directory).
- **Folia scheduler**: reflection handles are resolved once against the public scheduler interfaces and cached, instead of being looked up on every schedule/cancel call on the playback hot path.
- **Cheaper event hot paths**: hopper transfers are filtered by item/inventory type before any block-state lookup, and GUI event handlers use a non-snapshotting holder check — so hopper-heavy servers and players interacting with vanilla containers no longer pay for block-entity snapshots on every event.

---

## [3.1.0] - 2026-07-04

### Changed
- **Unified release**: The 2.x (Minecraft 1.21.x) and 3.0.0 (Minecraft 26.x) lines are merged into a single version. One jar now supports **Paper/Folia 1.21.4 through 26.x**.
  - Built against Paper API 1.21.4 with `api-version: '1.21'` and Java 21 bytecode — loads on 1.21.4+ servers (Java 21) and 26.x servers (Java 25) alike.
  - Contains all fixes from the 2.x line (2.1.5–2.2.1) plus the 3.0.0 modernizations (component-based CustomModelData, metadata API replacement).
- **Example resource pack**: `max_format` raised to `[84, 0]` so the pack is accepted by Minecraft 26.x clients (format 84) while staying compatible with 1.21.9+.
- **Debug logging**: Volume debug output now includes distance, sound/player coordinates, world check, and estimated max range to help diagnose audibility issues.

### Fixed
- **Debug logging**: Cross-world distance calculation no longer throws an exception when a `GLOBAL` playback reaches players in other worlds (previously triggered a misleading "FAILED TO PLAY SOUND" error with debug mode enabled). Distance is now logged as `N/A (different world)` instead.
- **Debug logging**: Numbers are formatted locale-independently (always `12.5` instead of `12,5` on e.g. German systems).

### Notes
- Servers on 1.21.0–1.21.3 should stay on v2.2.1 — the component-based CustomModelData API requires 1.21.4+.

---

## [3.0.0] - 2026-05-02

### Changed
- **Minecraft 26.1 support**: Upgraded to Paper API 26.1 (Java 25, `api-version: '26.1'`). This release ran on 26.x servers only; superseded by 3.1.0, which supports 1.21.4+ and 26.x with a single jar.
- **CustomModelData**: Migrated from the deprecated integer API to the component-based API (`CustomModelDataComponent`, floats).
- **JukeboxListener**: GUI jukebox-location tracking migrated from the deprecated metadata API (`FixedMetadataValue`) to an internal map.

---

## [2.2.1] - 2026-05-03

### Fixed
- **UpdateChecker**: Now filters by game version via Modrinth API so users only see updates compatible with their Minecraft version. Prevents cross-version update notifications (e.g., 26.1 updates shown to 1.21.x servers).
- **UpdateChecker**: Added missing `import java.net.URL` that caused compilation failure.

---

## [2.2.0] - 2026-05-02

### Added
- **Public API Events**: New event system for companion plugins
  - `DiscPlaybackStartEvent` — Cancellable event fired when a disc starts playing. Exposes disc, location, and mutable listener set.
  - `DiscPlaybackStopEvent` — Fired when playback stops, with `StopReason` enum (MANUAL, DURATION_END, BLOCK_BREAK, PLUGIN).
  - `DiscRegisteredEvent` — Fired when a new disc is created via GUI or config.
  - `DiscRemovedEvent` — Fired when a disc is removed, includes a snapshot of the deleted disc.
- **API method**: `CustomJukeboxAPI.getPluginDataFolder()` — Allows companion plugins to locate disc sound files.

### Changed
- **PlaybackManager**: Now fires `DiscPlaybackStartEvent` before playing sounds (allows cancellation and listener modification) and `DiscPlaybackStopEvent` on stop.
- **DiscManager**: Now fires `DiscRegisteredEvent` on disc creation and `DiscRemovedEvent` on disc deletion.

### Technical
- New package: `de.boondocksulfur.customjukebox.api.events` with 4 event classes
- All events follow standard Bukkit event patterns (HandlerList, static getHandlerList)
- Zero behavior change for existing users — events are no-ops without listeners
- Foundation for the new [BS-CustomJukebox Bedrock Extension](https://modrinth.com/plugin/bs-customjukebox-bedrock-extension)

---

## [2.1.6] - 2026-05-01

### Fixed
- **Critical: sound/soundKey field mismatch** — Discs created or edited via GUI wrote `"soundKey"` to disc.json, but the loader only read `"sound"`. After a reload, the custom sound was silently lost.
  - `saveDiscToConfig()` and `updateDiscField()` now consistently write `"sound"` (the official field name)
  - `parseDiscFromJson()` now reads `"sound"` with `"soundKey"` as backward-compatible fallback
  - DiscEditorGUIv2 now passes `"sound"` instead of `"soundKey"` to `updateDiscField()`
  - Existing disc.json files with `"soundKey"` entries will be read correctly (no manual migration needed)

- **ParrotDanceListener NPE**: Added null-check for `getWorld()` before calling `getNearbyEntities()`. Prevents crash when jukebox is in an unloaded world.

- **JukeboxPlayback thread-safety**: Changed internal `listeners` set from `HashSet` to `ConcurrentHashMap.newKeySet()`. Prevents potential `ConcurrentModificationException` when players join/leave during playback.

- **Config values without bounds validation**: All numeric config getters now clamp to valid ranges:
  - `volume`: 0.0–4.0
  - `creeper-drop-chance` / `loot-chance`: 0.0–1.0
  - `max-loot-discs` / `fragments-per-disc`: 1–64
  - `jukebox-hearing-radius`: 1–512
  - `dance-radius`: 1–32

### Removed
- **ColorUtil class deleted**: Deprecated since v2.1.0, internally fully replaced by `AdventureUtil`. No remaining usages in plugin code. Removed unused import from `DiscEditorGUIv2`.

### Changed
- **README overhauled: Resource Pack documentation**
  - Replaced misleading `pack_format: 34` for "Minecraft 1.21.x" with accurate per-version format table
  - Added modern `min_format` / `max_format` examples for Minecraft 1.21.9, 1.21.10, and 1.21.11
  - Added GitHub URL warning (don't use `/blob/` URLs for server resource packs)
  - Added ZIP structure documentation (correct vs incorrect root layout)
  - Added troubleshooting checklist for "Resource Pack hash is outdated" error
- **Example resource pack updated**: `pack.mcmeta` now uses `min_format: [69, 0]` / `max_format: [75, 0]` (compatible with MC 1.21.9–1.21.11)
- **Example resource pack README fixed**:
  - Added note that template does not include real `.ogg` files
  - Replaced outdated `supported_formats` reference with `min_format`/`max_format` explanation
  - Unified sound key examples to consistently use `music_disc.` prefix
  - Fixed incorrect `config.yml` reference → `server.properties` as `resource-pack-sha1`

---

## [2.1.5] - 2026-04-18

### Fixed
- **Permission system completely overhauled**: Players were incorrectly blocked in areas without explicit WorldGuard flags
  - `customjukebox.use` is now actually checked (previously only defined but never used)
  - WorldGuard now only checks for explicit `use deny` - Areas without the flag allow jukeboxes
  - Specific error messages instead of generic “no-permission” (Region/Claim/Permission separated)
  - New permission `customjukebox.bypass.protection` to bypass WorldGuard/GriefPrevention (default: OP)

- **Various bug fixes**: Fixed several potential crashes and race conditions
  - Thread safety for the playlist queue (synchronized methods)
  - Safer file saving with `Files.move()` instead of delete+rename (prevents data loss on Windows)
  - Null checks for metadata access, WorldGuard locations, and Adventure API colors
  - ArrayIndexOutOfBounds protection in the Disc Editor for corrupt states
  - UpdateChecker: JSON null safety and resource leak fix

- **Minor improvements**:
  - Mute state is now persisted in config.json (survives server restart)
  - Location-based HashMap key replaced with string key (more reliable cooldown)
  - Tab completion now shows subcommands even with empty input
  - Category validation in the Creation Wizard (warning for non-existent categories)
  - More efficient item distribution (a stack instead of a loop)
  - Plugin tasks are now properly terminated on onDisable

### Changed
- **Paper API 1.21.11**: Plugin now compiles and runs against Paper 1.21.11
- **Resource Pack updated**: `pack_format` set to 75 (1.21.11), sound namespace corrected
- **Default Configs**: `version` field added to config.json and disc.json
- **README**: Detailed permissions documentation with tables and descriptions

### Added
- Permission `customjukebox.bypass.protection` (default: OP, included in `customjukebox.admin`)
- New error messages in all 4 languages: `no-permission-jukebox`, `no-permission-region`, `no-permission-claim`

---

## [2.1.4] - 2026-04-14

### Fixed
- **False update notification**: Fixed plugin incorrectly showing "Update to 2.1.3 available" despite already running 2.1.3
  - Gradle's `processResources` did not track the project version as an explicit task input
  - Added `inputs.property("version", project.version)` to ensure version changes always trigger resource re-processing

---

## [2.1.3] - 2026-04-10

### Fixed
- **Message formatting**: All chat messages now use MessageUtil with Adventure API
  - Replaced all raw `sender.sendMessage(String)` / `player.sendMessage(String)` calls
  - Replaced hardcoded section sign color codes with ampersand codes
  - Affects all commands, listeners, and GUI components

- **Error handling**: Replaced all `printStackTrace()` calls with proper `Logger.log()` usage
  - CJBCommand, ConfigManager, DiscManager, IntegrationManager

- **Give/Fragment command bug**: Fixed commands giving items even with invalid amount input
  - Added missing `return` after NumberFormatException in GiveSubcommand and FragmentSubcommand

- **Vanilla sound overlap**: Fixed volume fluctuations during custom disc playback
  - Added `jukebox.stopPlaying()` to stop server-side vanilla playback
  - Prevents Jukebox block entity from periodically re-triggering vanilla sound
  - Applied to both manual disc insertion and GUI-based insertion

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
