package de.boondocksulfur.customjukebox.manager;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.api.events.CustomSoundPlayEvent;
import de.boondocksulfur.customjukebox.api.events.CustomSoundStopEvent;
import de.boondocksulfur.customjukebox.api.events.DiscPlaybackStartEvent;
import de.boondocksulfur.customjukebox.api.events.DiscPlaybackStopEvent;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.model.JukeboxPlayback;
import de.boondocksulfur.customjukebox.model.NowPlaying;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import de.boondocksulfur.customjukebox.model.RepeatMode;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages playback of custom disc sounds in jukeboxes.
 * Handles:
 * - Playing custom sounds from resource packs
 * - Tracking active playback sessions
 * - Auto-stopping sounds after duration
 * - Stopping sounds when jukeboxes are broken/ejected
 *
 * Thread Safety: Uses ConcurrentHashMap for all internal maps. Every operation
 * that changes playback state (start, stop, skip, track end, restart) runs
 * under one lock: on Folia a command, the track-end timer and a block event can
 * reach the same jukebox from different threads, and without the lock a stop
 * and a start could interleave into two playbacks, one of them never stopped.
 * Reads stay lock-free.
 */
public class PlaybackManager {

    private final CustomJukebox plugin;
    private final Map<String, JukeboxPlayback> activePlaybacks;          // Location key -> Playback (thread-safe)
    private final Map<String, SchedulerUtil.TaskHandle> autoStopTasks;   // Location key -> Stop task (thread-safe)

    // Playlist queue management
    private final Map<String, PlaylistQueue> playlistQueues;     // Location key -> Queue (thread-safe)

    /** Serialises every state change, see the class comment. */
    private final Object lock = new Object();

    // Sound configuration
    private static final SoundCategory SOUND_CATEGORY = SoundCategory.RECORDS;
    private static final float DEFAULT_PITCH = 1.0f;

    // Discs without a configured duration cannot be auto-stopped at track end;
    // clean up their tracking entry after this fallback period to avoid leaks.
    private static final int NO_DURATION_CLEANUP_TICKS = 20 * 60 * 60; // 1 hour

    /**
     * Internal class to manage playlist queues.
     * All methods are synchronized to prevent race conditions when accessed from multiple threads.
     */
    private static class PlaylistQueue {
        private final String playlistId;
        private final List<CustomDisc> discs;
        private int currentIndex;
        private final RepeatMode repeatMode;
        private final boolean shuffle;
        private final PlaybackRange range;
        private final Random random = new Random();

        PlaylistQueue(String playlistId, List<CustomDisc> discs, RepeatMode repeatMode, boolean shuffle,
                      PlaybackRange range) {
            this.playlistId = playlistId;
            this.discs = new ArrayList<>(discs);
            this.repeatMode = repeatMode != null ? repeatMode : RepeatMode.OFF;
            this.shuffle = shuffle;
            this.currentIndex = 0;
            this.range = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);
            if (shuffle) {
                Collections.shuffle(this.discs, random);
            }
        }

        synchronized CustomDisc getCurrentDisc() {
            if (discs.isEmpty()) return null;
            return discs.get(currentIndex);
        }

        synchronized boolean hasNext() {
            if (discs.isEmpty()) return false;
            if (repeatMode == RepeatMode.ONE) return true;
            return repeatMode == RepeatMode.ALL || currentIndex + 1 < discs.size();
        }

        synchronized CustomDisc next() {
            if (!hasNext()) {
                return null;
            }
            if (repeatMode == RepeatMode.ONE) {
                return discs.get(currentIndex); // Stay on the same track
            }

            currentIndex++;
            if (currentIndex >= discs.size()) {
                // Only reachable with RepeatMode.ALL - hasNext() guards the rest
                currentIndex = 0;
                if (shuffle && discs.size() > 1) {
                    // Re-shuffle each lap so looping is not one fixed order
                    CustomDisc last = discs.get(discs.size() - 1);
                    Collections.shuffle(discs, random);
                    // Don't repeat the track that just played across the wrap
                    if (discs.get(0).getId().equals(last.getId())) {
                        Collections.swap(discs, 0, discs.size() - 1);
                    }
                }
            }
            return discs.get(currentIndex);
        }

        synchronized int getSize() {
            return discs.size();
        }

        synchronized int getCurrentIndex() {
            return currentIndex;
        }
    }

    public PlaybackManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.activePlaybacks = new ConcurrentHashMap<>();
        this.autoStopTasks = new ConcurrentHashMap<>();
        this.playlistQueues = new ConcurrentHashMap<>();
    }

    /**
     * Starts playing a custom disc in a jukebox.
     * @param location Jukebox location
     * @param disc CustomDisc to play
     */
    public void startPlayback(Location location, CustomDisc disc) {
        startPlayback(location, disc, false, new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    /**
     * Starts playing a custom disc in a jukebox with loop option.
     * @param location Jukebox location
     * @param disc CustomDisc to play
     * @param loop Whether to loop the playback
     */
    public void startPlayback(Location location, CustomDisc disc, boolean loop) {
        startPlayback(location, disc, loop, new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    /**
     * Starts playing a custom disc in a jukebox with loop and range options.
     * @param location Jukebox location
     * @param disc CustomDisc to play
     * @param loop Whether to loop the playback
     * @param range Playback range
     */
    public void startPlayback(Location location, CustomDisc disc, boolean loop, PlaybackRange range) {
        // Input validation
        if (location == null) {
            plugin.getLogger().warning("Cannot start playback: location is null");
            return;
        }
        if (disc == null) {
            plugin.getLogger().warning("Cannot start playback: disc is null");
            return;
        }
        PlaybackRange effectiveRange = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);

        synchronized (lock) {
            String locationKey = JukeboxPlayback.getLocationKey(location);

            // Stop any existing playback at this location first. Only a playback
            // that is still running ends its playlist: between two tracks of a
            // playlist nothing runs here, and the queue must survive the start.
            if (activePlaybacks.containsKey(locationKey)) {
                stopPlaybackByKey(locationKey, true, true, DiscPlaybackStopEvent.StopReason.MANUAL);
            }

            // Determine eligible listeners before creating playback
            Set<Player> eligiblePlayers = new HashSet<>();
            if (disc.hasCustomSound()) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (shouldPlayerHearPlayback(player, location, effectiveRange)) {
                        eligiblePlayers.add(player);
                    }
                }
            }

            // Fire event — companion plugins can cancel or modify listener set
            DiscPlaybackStartEvent event = new DiscPlaybackStartEvent(disc, location, eligiblePlayers, loop, effectiveRange);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }

            // Create new playback session with loop flag and range
            JukeboxPlayback playback = new JukeboxPlayback(location, disc, loop, effectiveRange);
            activePlaybacks.put(locationKey, playback);

            // Play sound to eligible players (may have been modified by event listeners)
            playSoundToPlayers(playback, event.getListeners());

            // Schedule auto-stop or loop if disc has a duration
            if (disc.getDurationTicks() > 0) {
                if (loop) {
                    scheduleLoop(location, playback, disc.getDurationTicks());
                } else {
                    scheduleAutoStop(location, playback, disc.getDurationTicks());
                }
            } else {
                if (loop) {
                    plugin.getLogger().warning("Disc '" + disc.getId() + "' has no duration - loop is ignored");
                }
                // No duration: the tracking entry would otherwise live forever
                // (e.g. /cjb play at a player position has no eject to stop it)
                scheduleTrackingCleanup(location, playback);
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Started playback: " + disc.getDisplayName() +
                    " at " + locationKey + " (duration: " + disc.getDurationSeconds() + "s, loop: " + loop +
                    ", range: " + effectiveRange + ")");
            }
        }
    }

    /**
     * Stops playback at a jukebox location.
     * @param location Jukebox location
     */
    public void stopPlayback(Location location) {
        stopPlayback(location, DiscPlaybackStopEvent.StopReason.MANUAL);
    }

    /**
     * Stops playback at a jukebox location, reporting why.
     * @param location Jukebox location
     * @param reason reason passed on in {@link DiscPlaybackStopEvent}
     */
    public void stopPlayback(Location location, DiscPlaybackStopEvent.StopReason reason) {
        if (location == null) {
            plugin.getLogger().warning("Cannot stop playback: location is null");
            return;
        }
        stopPlaybackByKey(JukeboxPlayback.getLocationKey(location), true, true, reason);
    }

    /**
     * Stops the playback stored under a location key.
     *
     * <p>Works by key rather than by {@link Location}, so a playback in a world
     * that has since been unloaded can still be stopped.
     *
     * @param locationKey key of the playback
     * @param clearPlaylistQueue whether to drop the playlist queue too (false while progressing)
     * @param resumeZones whether the listeners go back to their ambient zone
     * @param reason reason passed on in {@link DiscPlaybackStopEvent}
     */
    private void stopPlaybackByKey(String locationKey, boolean clearPlaylistQueue, boolean resumeZones,
                                   DiscPlaybackStopEvent.StopReason reason) {
        synchronized (lock) {
            JukeboxPlayback playback = activePlaybacks.get(locationKey);
            if (playback == null) {
                return; // No active playback at this location
            }

            // Cancel auto-stop task
            SchedulerUtil.TaskHandle task = autoStopTasks.remove(locationKey);
            SchedulerUtil.cancelTask(task);

            // Stop sound for all listeners
            stopSoundForListeners(playback);

            // Fire stop event for companion plugins
            plugin.getServer().getPluginManager().callEvent(
                new DiscPlaybackStopEvent(playback.getDisc(), playback.getJukeboxLocation(), reason));

            // Mark as stopped and remove
            playback.setStopped(true);
            activePlaybacks.remove(locationKey);

            // ... and hand the listeners back to whatever zone they are standing
            // in. Only once the playback is off the map, or the zone would still
            // see the disc as audible and stay silent. Not between the tracks of a
            // playlist though - the next one starts immediately, and the zone would
            // blip in for a moment in between.
            if (resumeZones && plugin.getConfigManager().pauseZonesDuringDisc()) {
                resumeZonesFor(playback);
            }

            // Remove playlist queue if requested (don't remove when progressing to next track)
            if (clearPlaylistQueue) {
                playlistQueues.remove(locationKey);
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Cleared playlist queue at " + locationKey);
                }
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Stopped playback at " + locationKey);
            }
        }
    }

    /**
     * Drops every playback in a world that is being unloaded.
     *
     * <p>Nobody is left in that world to hear anything, so no stop packets are
     * needed - but the entries must go: once the world object is gone, every
     * later {@code getWorld()} on their locations throws, which would break
     * track timers, {@code /cjb skip} and the zone checks for everyone.
     *
     * @param world the world being unloaded
     */
    public void handleWorldUnload(World world) {
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        synchronized (lock) {
            for (JukeboxPlayback playback : new ArrayList<>(activePlaybacks.values())) {
                if (!worldName.equals(playback.getWorldName())) {
                    continue;
                }
                String key = playback.getLocationKey();
                SchedulerUtil.cancelTask(autoStopTasks.remove(key));
                playback.setStopped(true);
                activePlaybacks.remove(key);
                playlistQueues.remove(key);
                plugin.getServer().getPluginManager().callEvent(new DiscPlaybackStopEvent(
                    playback.getDisc(), playback.getJukeboxLocation(), DiscPlaybackStopEvent.StopReason.PLUGIN));
            }
            // Queues whose playback was already gone
            playlistQueues.keySet().removeIf(key -> key.startsWith(worldName + ":"));
        }
    }

    /**
     * Gets active playback at a location.
     * @param location Jukebox location
     * @return JukeboxPlayback or null if none active
     */
    public JukeboxPlayback getPlayback(Location location) {
        if (location == null) {
            return null;
        }
        String locationKey = JukeboxPlayback.getLocationKey(location);
        return activePlaybacks.get(locationKey);
    }

    /**
     * Checks if a jukebox is currently playing.
     * @param location Jukebox location
     * @return true if playing
     */
    public boolean isPlaying(Location location) {
        return getPlayback(location) != null;
    }

    /**
     * ID of the playlist running at a location.
     * @param location Jukebox location
     * @return the playlist ID, or null if no playlist runs there
     */
    public String getPlaylistIdAt(Location location) {
        if (location == null) {
            return null;
        }
        PlaylistQueue queue = playlistQueues.get(JukeboxPlayback.getLocationKey(location));
        return queue == null ? null : queue.playlistId;
    }

    /**
     * Whether a playlist is running at a location.
     * @param location Jukebox location
     * @return true if a playlist queue exists there
     */
    public boolean hasPlaylist(Location location) {
        return location != null && playlistQueues.containsKey(JukeboxPlayback.getLocationKey(location));
    }

    /**
     * Removes a player from all active playbacks.
     * Called when a player quits the server to prevent memory leaks.
     * @param player The player who is leaving
     */
    public void removePlayerFromAllPlaybacks(Player player) {
        if (player == null) {
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // Remove player from all active playbacks
        for (JukeboxPlayback playback : activePlaybacks.values()) {
            if (playback != null) {
                playback.removeListener(playerUUID);
            }
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Removed " + player.getName() + " from all active playbacks");
        }
    }

    /**
     * Stops any sound this player is currently being sent and drops them from
     * the listener sets, without affecting anyone else.
     *
     * <p>Used when a player turns their music off, so it goes quiet immediately
     * instead of at the end of the current track.
     *
     * @param player the player to silence
     */
    public void stopSoundFor(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        for (JukeboxPlayback playback : activePlaybacks.values()) {
            if (playback == null || !playback.getListeners().contains(uuid)) {
                continue;
            }
            if (playback.getDisc().hasCustomSound()) {
                stopSound(player, playback.getDisc());
            }
            playback.removeListener(uuid);
        }
    }

    /**
     * Attaches a player to every active playback they are in range of but not
     * currently hearing, and starts that track for them.
     *
     * <p>Used when a player turns their music back on. The track necessarily
     * starts from its beginning for them - the sound engine cannot seek - so
     * they are briefly offset from listeners who were there all along, until
     * the next track boundary re-syncs everyone.
     *
     * @param player the player to attach
     * @return how many playbacks the player was attached to
     */
    public int resumeSoundFor(Player player) {
        if (player == null || !player.isOnline()
                || !plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId())) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        int attached = 0;
        for (JukeboxPlayback playback : activePlaybacks.values()) {
            if (playback == null || playback.isStopped() || playback.getListeners().contains(uuid)) {
                continue;
            }
            CustomDisc disc = playback.getDisc();
            if (!disc.hasCustomSound()) {
                continue;
            }
            Location location = playback.getJukeboxLocation();
            if (!isInPlaybackRange(player, location, playback.getRange())) {
                continue;
            }
            playSound(player, location, disc);
            playback.addListener(player);
            attached++;
        }
        return attached;
    }

    /**
     * The active playback a player can act on - what they hear, or failing that
     * what is playing within earshot.
     *
     * <p>{@link #getPlaybackFor} resolves strictly by listener membership, which
     * is right for the progress bar but too strict for {@code /cjb skip}: a
     * player who just re-enabled their music, or who walked up after the track
     * started, is in range without being a listener yet.
     *
     * @param player the player
     * @return the playback, or null if nothing is playing nearby
     */
    public JukeboxPlayback getAudiblePlaybackFor(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        JukeboxPlayback best = null;
        boolean bestIsListener = false;

        for (JukeboxPlayback playback : activePlaybacks.values()) {
            if (playback == null || playback.isStopped()) {
                continue;
            }
            boolean listener = playback.getListeners().contains(uuid);
            if (!listener && !isInPlaybackRange(player, playback.getJukeboxLocation(), playback.getRange())) {
                continue;
            }
            // Something the player actually hears beats something merely in
            // range; among equals, the most recently started one.
            boolean better = best == null
                || (listener && !bestIsListener)
                || (listener == bestIsListener && playback.getStartTime() > best.getStartTime());
            if (better) {
                best = playback;
                bestIsListener = listener;
            }
        }
        return best;
    }

    /**
     * Stops all active playbacks (used on plugin disable).
     */
    public void stopAllPlaybacks() {
        synchronized (lock) {
            // Copy keys to avoid ConcurrentModificationException
            for (String locationKey : new ArrayList<>(activePlaybacks.keySet())) {
                stopPlaybackByKey(locationKey, true, true, DiscPlaybackStopEvent.StopReason.PLUGIN);
            }
            playlistQueues.clear();
        }

        plugin.getLogger().info("Stopped all active playbacks");
    }

    /**
     * Restarts all active playbacks with current settings.
     * Useful for applying volume changes to running songs.
     */
    public void restartAllPlaybacks() {
        int restarted = 0;
        synchronized (lock) {
            for (JukeboxPlayback playback : new ArrayList<>(activePlaybacks.values())) {
                Location location = playback.getJukeboxLocation();
                if (!location.isWorldLoaded()) {
                    continue;
                }

                // Stop current playback but keep the playlist queue - a running
                // playlist must survive e.g. /cjb mute + unmute or volume restarts
                stopPlaybackByKey(playback.getLocationKey(), false, false,
                    DiscPlaybackStopEvent.StopReason.PLUGIN);

                // Restart with same settings
                startPlayback(location, playback.getDisc(), playback.isLoop(), playback.getRange());
                restarted++;
            }
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Restarted " + restarted + " active playback(s)");
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Plays the disc sound to a pre-determined set of players.
     * Used when the listener set has been determined and potentially modified by events.
     * @param playback JukeboxPlayback session
     * @param players Players to receive the sound
     */
    private void playSoundToPlayers(JukeboxPlayback playback, Set<Player> players) {
        CustomDisc disc = playback.getDisc();
        if (!disc.hasCustomSound()) {
            return;
        }

        Location location = playback.getJukeboxLocation();

        for (Player player : players) {
            if (player.isOnline() && plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId())) {
                // Listener first: a stop that lands before the delivery below
                // must already see this player
                playback.addListener(player);
                onPlayerThread(player, () -> {
                    if (!playback.isStopped() && playback.hasListener(player)) {
                        playSound(player, location, disc);
                    }
                });
            }
        }
    }

    /**
     * Runs per-player work on the thread that owns that player.
     *
     * <p>A jukebox with a global, world or large radius range reaches players in
     * other Folia regions. Stopping their zone music and firing the delivery
     * events for them has to happen on their own thread, where companion
     * plugins may safely touch the player. On Paper, and for players in the
     * jukebox's own region, this runs immediately.
     */
    private void onPlayerThread(Player player, Runnable task) {
        if (!SchedulerUtil.isFolia() || Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            SchedulerUtil.runPlayerTask(plugin, player, task);
        }
    }

    /**
     * Determines if a player should hear the playback based on range settings.
     * @param player Player to check
     * @param location Playback location
     * @param range Playback range
     * @return true if player should hear the sound
     */
    private boolean shouldPlayerHearPlayback(Player player, Location location, PlaybackRange range) {
        // Players who turned plugin music off never enter a listener set
        if (!plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId())) {
            return false;
        }
        return isInPlaybackRange(player, location, range);
    }

    /**
     * Pure range test, without the per-player music toggle.
     *
     * <p>Separate from {@link #shouldPlayerHearPlayback} because commands like
     * {@code /cjb skip} need to resolve "what is playing near me" as a spatial
     * question, independent of whether that player is currently being sent the
     * sound.
     *
     * @param player Player to check
     * @param location Playback location
     * @param range Playback range
     * @return true if the player is within range
     */
    private boolean isInPlaybackRange(Player player, Location location, PlaybackRange range) {
        // A playback left over in an unloaded world is audible to nobody -
        // getWorld() would throw below
        if (!location.isWorldLoaded()) {
            return false;
        }
        switch (range.getType()) {
            case GLOBAL:
                // All players on the server
                return true;

            case WORLD:
                // Only players in the same world
                return player.getWorld().equals(location.getWorld());

            case CUSTOM_RADIUS:
                // Players within custom radius
                if (!player.getWorld().equals(location.getWorld())) {
                    return false;
                }
                double distance = player.getLocation().distance(location);
                return distance <= range.getCustomRadius();

            case NORMAL:
            default:
                // Standard range based on volume
                if (!player.getWorld().equals(location.getWorld())) {
                    return false;
                }
                float volume = plugin.getConfigManager().getVolume();
                return player.getLocation().distance(location) <= volume * 16;
        }
    }

    /**
     * Lets ambient zones take over again for everyone who was listening to a
     * playback that just ended.
     *
     * @param playback the playback that stopped
     */
    private void resumeZonesFor(JukeboxPlayback playback) {
        // On disable the zones are already stopped; nothing to hand back to
        if (!plugin.isEnabled()) {
            return;
        }
        for (UUID listenerId : playback.getListeners()) {
            Player listener = plugin.getServer().getPlayer(listenerId);
            if (listener == null || !listener.isOnline()) {
                continue;
            }
            SchedulerUtil.runPlayerTask(plugin, listener,
                () -> plugin.getAmbientZoneManager().resumeSoundFor(listener));
        }
    }

    /**
     * Plays a disc's sound to a specific player.
     *
     * <p>Single delivery point for jukebox playback: fires {@link CustomSoundPlayEvent}
     * so companion plugins can take delivery over for individual players.
     *
     * @param player Player
     * @param location Sound location
     * @param disc Disc whose sound to play
     */
    private void playSound(Player player, Location location, CustomDisc disc) {
        String soundKey = disc.getSoundKey();
        try {
            // A disc takes the floor: whatever the ambient zone was playing for
            // this listener stops, rather than the two running over each other.
            if (plugin.getConfigManager().pauseZonesDuringDisc()) {
                plugin.getAmbientZoneManager().stopSoundFor(player);
            }

            // Personal volume replaces the server volume for this listener
            float volume = plugin.getPlayerPreferencesManager().effectiveVolume(player.getUniqueId());

            CustomSoundPlayEvent deliveryEvent = new CustomSoundPlayEvent(
                player, disc, location, CustomSoundPlayEvent.Source.JUKEBOX, volume);
            plugin.getServer().getPluginManager().callEvent(deliveryEvent);
            if (deliveryEvent.isCancelled()) {
                return; // A companion plugin delivers this sound instead
            }

            player.playSound(location, soundKey, SOUND_CATEGORY, volume, DEFAULT_PITCH);

            if (plugin.getConfigManager().isDebug()) {
                boolean sameWorld = player.getWorld().equals(location.getWorld());
                String distance = sameWorld
                    ? String.format(java.util.Locale.ROOT, "%.1f blocks", player.getLocation().distance(location))
                    : "N/A (different world)";
                plugin.getLogger().info("[Volume Debug] Playing '" + soundKey + "' to " + player.getName() +
                    " | volume=" + volume +
                    " | distance=" + distance +
                    " | soundLocation=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                    " | playerLocation=" + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() +
                    " | sameWorld=" + sameWorld +
                    " | maxRange=" + String.format(java.util.Locale.ROOT, "%.1f", volume * 16) + " blocks");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
            plugin.getLogger().severe("FAILED TO PLAY SOUND: '" + soundKey + "'");
            plugin.getLogger().severe("Player: " + player.getName());
            plugin.getLogger().severe("Error: " + e.getMessage());
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
            plugin.getLogger().severe("Possible causes:");
            plugin.getLogger().severe("  1. Sound '" + soundKey + "' is not defined in sounds.json");
            plugin.getLogger().severe("  2. Resource pack is not loaded by the player");
            plugin.getLogger().severe("  3. Sound file (.ogg) is missing from resource pack");
            plugin.getLogger().severe("  4. Player declined the resource pack");
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
            plugin.getLogger().severe("Troubleshooting steps:");
            plugin.getLogger().severe("  1. Check if '" + soundKey + "' exists in your resource pack's sounds.json");
            plugin.getLogger().severe("  2. Verify the .ogg file exists in assets/customjukebox/sounds/");
            plugin.getLogger().severe("  3. Ask player to /reload resource packs or rejoin the server");
            plugin.getLogger().severe("  4. Enable debug mode in config.json for more details");
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════");

            // Notify player about the issue
            if (player != null && player.isOnline()) {
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&c&l[CustomJukebox] Sound playback failed!");
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&7Sound: &e" + soundKey);
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&7This might be because:");
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&7  - You haven't loaded the resource pack");
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&7  - The sound file is missing");
                de.boondocksulfur.customjukebox.utils.MessageUtil.sendMessage(player, "&7Try: &e/reload &7or rejoin the server");
            }
        }
    }

    /**
     * Stops sound for all tracked listeners of a playback.
     * @param playback JukeboxPlayback
     */
    private void stopSoundForListeners(JukeboxPlayback playback) {
        if (!playback.getDisc().hasCustomSound()) {
            return; // No custom sound to stop
        }

        CustomDisc disc = playback.getDisc();

        for (UUID listenerId : playback.getListeners()) {
            Player player = plugin.getServer().getPlayer(listenerId);
            if (player != null && player.isOnline()) {
                onPlayerThread(player, () -> stopSound(player, disc));
            }
        }
    }

    /**
     * Stops a disc's sound for a player.
     *
     * <p>Counterpart to {@link #playSound}: fires {@link CustomSoundStopEvent} so a
     * companion plugin that took delivery over can stop its own sound instead.
     *
     * @param player Player
     * @param disc Disc whose sound to stop
     */
    private void stopSound(Player player, CustomDisc disc) {
        String soundKey = disc.getSoundKey();
        try {
            CustomSoundStopEvent stopEvent = new CustomSoundStopEvent(
                player, disc, CustomSoundPlayEvent.Source.JUKEBOX);
            plugin.getServer().getPluginManager().callEvent(stopEvent);
            if (stopEvent.isCancelled()) {
                return; // A companion plugin stops this sound instead
            }

            player.stopSound(soundKey, SOUND_CATEGORY);

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Stopped sound '" + soundKey + "' for " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to stop sound '" + soundKey + "' for " + player.getName() +
                ": " + e.getMessage());
        }
    }

    /**
     * Schedules an auto-stop task for a playback.
     * Folia-compatible: Uses SchedulerUtil for cross-platform scheduling.
     * The task verifies it still belongs to the same playback session when it
     * fires, so a stale task can never stop a newer playback at this location.
     * @param location Jukebox location
     * @param expectedPlayback Playback session this task belongs to
     * @param durationTicks Duration in ticks
     */
    private void scheduleAutoStop(Location location, JukeboxPlayback expectedPlayback, int durationTicks) {
        String locationKey = JukeboxPlayback.getLocationKey(location);

        SchedulerUtil.TaskHandle task = SchedulerUtil.runLater(plugin, location, () -> {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AutoStop] Task triggered for " + locationKey +
                    " after " + durationTicks + " ticks");
            }

            synchronized (lock) {
                JukeboxPlayback playback = activePlaybacks.get(locationKey);
                if (playback == null || playback != expectedPlayback || playback.isStopped()) {
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[AutoStop] No matching playback found at " + locationKey);
                    }
                    return;
                }

                // Stop playback BUT keep playlist queue for progression; the
                // zones get their listeners back only if nothing follows
                stopPlaybackByKey(locationKey, false, false, DiscPlaybackStopEvent.StopReason.DURATION_END);
                if (!handlePlaylistProgression(location)) {
                    resumeZonesIfEnabled(playback);
                }

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AutoStop] Completed for " + locationKey);
                }
            }
        }, durationTicks);

        if (task != null) {
            autoStopTasks.put(locationKey, task);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AutoStop] Scheduled task for " + locationKey +
                " in " + durationTicks + " ticks (" + (durationTicks / 20) + " seconds)");
        }
    }

    /**
     * Schedules a bookkeeping-only cleanup for playbacks without a configured
     * duration. The client-side sound ends on its own long before this fires;
     * the task merely drops the stale tracking entry - it must NOT trigger
     * stop-sound packets, stop events, or playlist progression.
     * @param location Jukebox location
     * @param expectedPlayback Playback session this task belongs to
     */
    private void scheduleTrackingCleanup(Location location, JukeboxPlayback expectedPlayback) {
        String locationKey = JukeboxPlayback.getLocationKey(location);

        SchedulerUtil.TaskHandle task = SchedulerUtil.runLater(plugin, location, () -> {
            synchronized (lock) {
                if (activePlaybacks.get(locationKey) == expectedPlayback) {
                    expectedPlayback.setStopped(true);
                    activePlaybacks.remove(locationKey);
                    autoStopTasks.remove(locationKey);
                    // A playlist cannot advance past a track without a duration,
                    // so its queue would otherwise outlive the playback
                    playlistQueues.remove(locationKey);
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[Cleanup] Removed stale no-duration playback entry at " + locationKey);
                    }
                }
            }
        }, NO_DURATION_CLEANUP_TICKS);

        if (task != null) {
            autoStopTasks.put(locationKey, task);
        }
    }

    /**
     * Schedules a loop task for a playback.
     * Restarts the sound after the duration is reached.
     * Folia-compatible: Uses SchedulerUtil for cross-platform scheduling.
     * @param location Jukebox location
     * @param durationTicks Duration in ticks
     */
    private void scheduleLoop(Location location, JukeboxPlayback expectedPlayback, int durationTicks) {
        String locationKey = JukeboxPlayback.getLocationKey(location);

        SchedulerUtil.TaskHandle task = SchedulerUtil.runLater(plugin, location, () -> {
            synchronized (lock) {
                JukeboxPlayback playback = activePlaybacks.get(locationKey);
                // Identity check: a stale loop task must never restart a newer playback
                if (playback == null || playback != expectedPlayback || playback.isStopped() || !playback.isLoop()) {
                    return;
                }

                // One lap is over: report it like any other track end, so start
                // and stop events stay paired for companion plugins
                stopPlaybackByKey(locationKey, false, false, DiscPlaybackStopEvent.StopReason.DURATION_END);

                // Start new playback with loop and range enabled
                // This will create a fresh playback session and new loop task
                startPlayback(location, playback.getDisc(), true, playback.getRange());

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Looping playback at " + locationKey);
                }
            }
        }, durationTicks);

        if (task != null) {
            autoStopTasks.put(locationKey, task);
        }
    }

    // ==================== PLAYLIST PLAYBACK ====================

    /**
     * Starts playing a playlist at a location with default normal range.
     * @param location Location to play at
     * @param playlist Playlist to play
     * @param loop Whether to loop the playlist
     */
    public void startPlaylistPlayback(Location location, DiscPlaylist playlist, boolean loop) {
        startPlaylistPlayback(location, playlist, loop ? RepeatMode.ALL : RepeatMode.OFF, false,
            new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    /**
     * Starts playing a playlist at a location with specified range.
     * @param location Location to play at
     * @param playlist Playlist to play
     * @param loop Whether to loop the playlist
     * @param range Playback range for all discs in playlist
     */
    public void startPlaylistPlayback(Location location, DiscPlaylist playlist, boolean loop, PlaybackRange range) {
        startPlaylistPlayback(location, playlist, loop ? RepeatMode.ALL : RepeatMode.OFF, false, range);
    }

    /**
     * Starts a playlist with an explicit repeat mode and shuffle setting.
     *
     * @param location Location to play at
     * @param playlist Playlist to play
     * @param repeatMode What happens when a track ends
     * @param shuffle Whether to play the discs in random order
     * @param range Playback range for all discs in the playlist
     */
    public void startPlaylistPlayback(Location location, DiscPlaylist playlist,
                                      RepeatMode repeatMode, boolean shuffle, PlaybackRange range) {
        if (location == null || playlist == null) {
            return;
        }

        // Resolve from the playlist object, not its ID - an ad-hoc playlist such
        // as a player's favourites is never registered and would resolve empty
        List<CustomDisc> discs = plugin.getDiscManager().resolveDiscs(playlist);
        if (discs.isEmpty()) {
            plugin.getLogger().warning("Cannot start playlist '" + playlist.getId() + "': No valid discs found");
            return;
        }
        PlaybackRange effectiveRange = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);

        synchronized (lock) {
            String locationKey = getLocationKey(location);

            // Stop any existing playback
            stopPlayback(location);

            // Create playlist queue with range
            PlaylistQueue queue = new PlaylistQueue(playlist.getId(), discs, repeatMode, shuffle, effectiveRange);
            playlistQueues.put(locationKey, queue);

            // Start playing first disc
            CustomDisc firstDisc = queue.getCurrentDisc();
            if (firstDisc != null) {
                startPlayback(location, firstDisc, false, effectiveRange);
            }
            if (!activePlaybacks.containsKey(locationKey)) {
                // A companion plugin cancelled the start - an orphaned queue
                // would later take over whatever disc is played here next
                playlistQueues.remove(locationKey);
                return;
            }

            plugin.getLogger().info("Started playlist '" + playlist.getId() + "' at " + locationKey +
                " (" + queue.getSize() + " discs, repeat: " + repeatMode.display()
                + ", shuffle: " + shuffle + ", range: " + effectiveRange + ")");
        }
    }

    /**
     * Skips to the next track of the playlist running at a location.
     *
     * <p>Only a playlist has a next track. A single disc is left alone - ending
     * it is a stop, which is a separate permission (see {@code /cjb skip}).
     *
     * @param location playback location
     * @return the disc now playing, or null if nothing follows (playback stopped)
     *         or no playlist runs there
     */
    public CustomDisc skipToNext(Location location) {
        if (location == null) {
            return null;
        }
        synchronized (lock) {
            String locationKey = getLocationKey(location);
            JukeboxPlayback current = activePlaybacks.get(locationKey);
            if (current == null || !playlistQueues.containsKey(locationKey)) {
                return null;
            }

            // Keep the queue when stopping so progression can continue
            stopPlaybackByKey(locationKey, false, false, DiscPlaybackStopEvent.StopReason.MANUAL);
            if (!handlePlaylistProgression(location)) {
                resumeZonesIfEnabled(current);
                return null;
            }
            JukeboxPlayback playback = activePlaybacks.get(locationKey);
            return playback == null ? null : playback.getDisc();
        }
    }

    /**
     * Finds the playback a player is currently being sent sound from.
     *
     * <p>Resolved from the tracked listener sets - that is exactly who received
     * the play-sound packet - preferring the most recently started playback when
     * a player is in range of several.
     *
     * @param player the player
     * @return what the player is hearing, or null
     */
    public NowPlaying getNowPlaying(Player player) {
        JukeboxPlayback playback = getPlaybackFor(player);
        if (playback == null) {
            return null;
        }
        return new NowPlaying(playback.getDisc(), playback.getElapsedTicks(), NowPlaying.Source.JUKEBOX);
    }

    /**
     * The active playback session a player is being sent sound from.
     *
     * <p>Resolved from the tracked listener sets - that is exactly who received
     * the play-sound packet - preferring the most recently started session when
     * a player is in range of several.
     *
     * @param player the player
     * @return the session, or null if the player is hearing nothing
     */
    public JukeboxPlayback getPlaybackFor(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        JukeboxPlayback best = null;
        for (JukeboxPlayback playback : activePlaybacks.values()) {
            if (playback == null || playback.isStopped() || !playback.getListeners().contains(uuid)) {
                continue;
            }
            if (best == null || playback.getStartTime() > best.getStartTime()) {
                best = playback;
            }
        }
        return best;
    }

    /**
     * Handles playlist queue progression.
     * Called when a disc finishes playing.
     * @param location Location where disc finished
     * @return true if a next track started
     */
    private boolean handlePlaylistProgression(Location location) {
        synchronized (lock) {
            String locationKey = getLocationKey(location);

            PlaylistQueue queue = playlistQueues.get(locationKey);
            if (queue == null) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[Playlist] No queue found at " + locationKey + " - not a playlist playback");
                }
                return false; // No playlist active
            }

            // next() is what the queue now points at - with shuffle and repeat it
            // reshuffles on the wrap, so a peek beforehand can name another disc
            CustomDisc nextDisc = queue.next();
            if (nextDisc == null) {
                // Playlist finished
                playlistQueues.remove(locationKey);
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[Playlist] Finished at " + locationKey);
                }
                return false;
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[Playlist] Playing next: " + nextDisc.getId() +
                    " (" + (queue.getCurrentIndex() + 1) + "/" + queue.getSize() + ")");
            }
            startPlayback(location, nextDisc, false, queue.range);

            if (!activePlaybacks.containsKey(locationKey)) {
                // Start cancelled by a companion plugin: the playlist ends here
                playlistQueues.remove(locationKey);
                return false;
            }
            return true;
        }
    }

    /**
     * Hands the listeners of an ended playback back to their ambient zones, if
     * discs pause zones at all.
     */
    private void resumeZonesIfEnabled(JukeboxPlayback playback) {
        if (plugin.getConfigManager().pauseZonesDuringDisc()) {
            resumeZonesFor(playback);
        }
    }

    /**
     * Gets location key for HashMap lookups.
     * @param location Location
     * @return Location key string
     */
    private String getLocationKey(Location location) {
        return JukeboxPlayback.getLocationKey(location);
    }
}
