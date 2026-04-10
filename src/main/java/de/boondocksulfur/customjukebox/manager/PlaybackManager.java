package de.boondocksulfur.customjukebox.manager;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.model.JukeboxPlayback;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
 * Thread Safety: Uses ConcurrentHashMap for all internal maps to ensure thread-safe operations.
 * All playback operations should be performed on the main server thread.
 */
public class PlaybackManager {

    private final CustomJukebox plugin;
    private final Map<String, JukeboxPlayback> activePlaybacks; // Location key -> Playback (thread-safe)
    private final Map<String, BukkitTask> autoStopTasks;         // Location key -> Stop task (thread-safe)

    // Playlist queue management
    private final Map<String, PlaylistQueue> playlistQueues;     // Location key -> Queue (thread-safe)

    // Sound configuration
    private static final SoundCategory SOUND_CATEGORY = SoundCategory.RECORDS;
    private static final float DEFAULT_PITCH = 1.0f;

    /**
     * Internal class to manage playlist queues.
     */
    private static class PlaylistQueue {
        private final List<CustomDisc> discs;
        private int currentIndex;
        private final boolean loop;
        private final PlaybackRange range;

        PlaylistQueue(List<CustomDisc> discs, boolean loop, PlaybackRange range) {
            this.discs = new ArrayList<>(discs);
            this.currentIndex = 0;
            this.loop = loop;
            this.range = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);
        }

        CustomDisc getCurrentDisc() {
            if (discs.isEmpty()) return null;
            return discs.get(currentIndex);
        }

        boolean hasNext() {
            return loop || (currentIndex + 1 < discs.size());
        }

        CustomDisc next() {
            if (discs.isEmpty()) return null;

            // Check if we can advance before incrementing
            if (!hasNext()) {
                return null;
            }

            currentIndex++;
            if (currentIndex >= discs.size()) {
                if (loop) {
                    currentIndex = 0;
                } else {
                    // This should not happen due to hasNext() check
                    currentIndex = discs.size() - 1;
                    return null;
                }
            }

            return discs.get(currentIndex);
        }

        CustomDisc peekNext() {
            if (!hasNext()) return null;

            int nextIndex = currentIndex + 1;
            if (nextIndex >= discs.size()) {
                if (loop) {
                    return discs.get(0);
                } else {
                    return null;
                }
            }
            return discs.get(nextIndex);
        }

        int getSize() {
            return discs.size();
        }

        int getCurrentIndex() {
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

        String locationKey = JukeboxPlayback.getLocationKey(location);

        // Stop any existing playback at this location first
        stopPlayback(location);

        // Create new playback session with loop flag and range
        JukeboxPlayback playback = new JukeboxPlayback(location, disc, loop, range);
        activePlaybacks.put(locationKey, playback);

        // Play sound to players based on range
        playSoundToPlayers(playback);

        // Schedule auto-stop or loop if disc has a duration
        if (disc.getDurationTicks() > 0) {
            if (loop) {
                scheduleLoop(location, disc.getDurationTicks());
            } else {
                scheduleAutoStop(location, disc.getDurationTicks());
            }
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Started playback: " + disc.getDisplayName() +
                " at " + locationKey + " (duration: " + disc.getDurationSeconds() + "s, loop: " + loop +
                ", range: " + range.toString() + ")");
        }
    }

    /**
     * Stops playback at a jukebox location.
     * @param location Jukebox location
     */
    public void stopPlayback(Location location) {
        stopPlayback(location, true);
    }

    /**
     * Stops playback at a jukebox location.
     * @param location Jukebox location
     * @param clearPlaylistQueue Whether to clear the playlist queue (false when auto-progressing)
     */
    private void stopPlayback(Location location, boolean clearPlaylistQueue) {
        // Input validation
        if (location == null) {
            plugin.getLogger().warning("Cannot stop playback: location is null");
            return;
        }

        String locationKey = JukeboxPlayback.getLocationKey(location);

        JukeboxPlayback playback = activePlaybacks.get(locationKey);
        if (playback == null) {
            return; // No active playback at this location
        }

        // Cancel auto-stop task (null-safe for Folia)
        BukkitTask task = autoStopTasks.remove(locationKey);
        SchedulerUtil.cancelTask(task);

        // Stop sound for all listeners
        stopSoundForListeners(playback);

        // Mark as stopped and remove
        playback.setStopped(true);
        activePlaybacks.remove(locationKey);

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
     * Stops all active playbacks (used on plugin disable).
     */
    public void stopAllPlaybacks() {
        // Copy keys to avoid ConcurrentModificationException
        for (String locationKey : new HashMap<>(activePlaybacks).keySet()) {
            JukeboxPlayback playback = activePlaybacks.get(locationKey);
            if (playback != null) {
                stopPlayback(playback.getJukeboxLocation());
            }
        }

        plugin.getLogger().info("Stopped all active playbacks");
    }

    /**
     * Restarts all active playbacks with current settings.
     * Useful for applying volume changes to running songs.
     */
    public void restartAllPlaybacks() {
        // Copy current playbacks to avoid ConcurrentModificationException
        Map<String, JukeboxPlayback> currentPlaybacks = new HashMap<>(activePlaybacks);

        for (JukeboxPlayback playback : currentPlaybacks.values()) {
            Location location = playback.getJukeboxLocation();
            CustomDisc disc = playback.getDisc();
            boolean loop = playback.isLoop();
            PlaybackRange range = playback.getRange();

            // Stop current playback
            stopPlayback(location);

            // Restart with same settings
            startPlayback(location, disc, loop, range);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Restarted " + currentPlaybacks.size() + " active playback(s)");
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Plays the disc sound to players based on the playback range.
     * @param playback JukeboxPlayback session
     */
    private void playSoundToPlayers(JukeboxPlayback playback) {
        CustomDisc disc = playback.getDisc();
        if (!disc.hasCustomSound()) {
            // No custom sound defined, let vanilla handle it
            return;
        }

        Location location = playback.getJukeboxLocation();
        String soundKey = disc.getSoundKey();
        PlaybackRange range = playback.getRange();

        // Determine which players should hear the sound based on range
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (shouldPlayerHearPlayback(player, location, range)) {
                playSound(player, location, soundKey);
                playback.addListener(player);
            }
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
     * Plays a sound to a specific player.
     * @param player Player
     * @param location Sound location
     * @param soundKey Sound key (e.g. "customjukebox:epic_journey")
     */
    private void playSound(Player player, Location location, String soundKey) {
        try {
            float volume = plugin.getConfigManager().getVolume();
            player.playSound(location, soundKey, SOUND_CATEGORY, volume, DEFAULT_PITCH);

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Playing sound '" + soundKey + "' to " + player.getName() +
                    " (volume: " + volume + ")");
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

        String soundKey = playback.getDisc().getSoundKey();

        for (UUID listenerId : playback.getListeners()) {
            Player player = plugin.getServer().getPlayer(listenerId);
            if (player != null && player.isOnline()) {
                stopSound(player, soundKey);
            }
        }
    }

    /**
     * Stops a specific sound for a player.
     * @param player Player
     * @param soundKey Sound key
     */
    private void stopSound(Player player, String soundKey) {
        try {
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
     * @param location Jukebox location
     * @param durationTicks Duration in ticks
     */
    private void scheduleAutoStop(Location location, int durationTicks) {
        String locationKey = JukeboxPlayback.getLocationKey(location);

        BukkitTask task = SchedulerUtil.runLater(plugin, location, () -> {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AutoStop] Task triggered for " + locationKey +
                    " after " + durationTicks + " ticks");
            }

            JukeboxPlayback playback = getPlayback(location);
            if (playback != null) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AutoStop] Playback active, stopping: " +
                        playback.getDisc().getId());
                }

                // Stop playback BUT keep playlist queue for progression
                // (false = don't clear playlist queue)
                stopPlayback(location, false);

                // Check if this is part of a playlist and play next disc
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AutoStop] Checking for playlist progression...");
                }
                handlePlaylistProgression(location);

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AutoStop] Completed for " + locationKey);
                }
            } else {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AutoStop] No active playback found at " + locationKey);
                }
            }
        }, durationTicks);

        // Only store task if not null (Folia returns null, which is fine)
        if (task != null) {
            autoStopTasks.put(locationKey, task);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AutoStop] Scheduled task for " + locationKey +
                " in " + durationTicks + " ticks (" + (durationTicks / 20) + " seconds)");
        }
    }

    /**
     * Schedules a loop task for a playback.
     * Restarts the sound after the duration is reached.
     * Folia-compatible: Uses SchedulerUtil for cross-platform scheduling.
     * @param location Jukebox location
     * @param durationTicks Duration in ticks
     */
    private void scheduleLoop(Location location, int durationTicks) {
        String locationKey = JukeboxPlayback.getLocationKey(location);

        BukkitTask task = SchedulerUtil.runLater(plugin, location, () -> {
            JukeboxPlayback playback = getPlayback(location);
            if (playback != null && !playback.isStopped() && playback.isLoop()) {
                // Save settings before stopping
                CustomDisc disc = playback.getDisc();
                PlaybackRange range = playback.getRange();

                // Cancel the old task FIRST (before removing playback)
                // Null-safe for Folia compatibility
                BukkitTask oldTask = autoStopTasks.remove(locationKey);
                SchedulerUtil.cancelTask(oldTask);

                // Stop sound for current listeners
                stopSoundForListeners(playback);

                // Mark as stopped and remove playback
                playback.setStopped(true);
                activePlaybacks.remove(locationKey);

                // Start new playback with loop and range enabled
                // This will create a fresh playback session and new loop task
                startPlayback(location, disc, true, range);

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Looping playback at " + locationKey);
                }
            }
        }, durationTicks);

        // Only store task if not null (Folia returns null, which is fine)
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
        startPlaylistPlayback(location, playlist, loop, new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    /**
     * Starts playing a playlist at a location with specified range.
     * @param location Location to play at
     * @param playlist Playlist to play
     * @param loop Whether to loop the playlist
     * @param range Playback range for all discs in playlist
     */
    public void startPlaylistPlayback(Location location, DiscPlaylist playlist, boolean loop, PlaybackRange range) {
        if (location == null || playlist == null) {
            return;
        }

        // Get discs from playlist
        List<CustomDisc> discs = plugin.getDiscManager().getDiscsFromPlaylist(playlist.getId());
        if (discs.isEmpty()) {
            plugin.getLogger().warning("Cannot start playlist '" + playlist.getId() + "': No valid discs found");
            return;
        }

        String locationKey = getLocationKey(location);

        // Stop any existing playback
        stopPlayback(location);

        // Create playlist queue with range
        PlaylistQueue queue = new PlaylistQueue(discs, loop, range);
        playlistQueues.put(locationKey, queue);

        // Start playing first disc
        CustomDisc firstDisc = queue.getCurrentDisc();
        if (firstDisc != null) {
            startPlayback(location, firstDisc, false, range);

            plugin.getLogger().info("Started playlist '" + playlist.getId() + "' at " + locationKey +
                " (" + queue.getSize() + " discs, loop: " + loop + ", range: " + range.toString() + ")");
        }
    }

    /**
     * Handles playlist queue progression.
     * Called when a disc finishes playing.
     * @param location Location where disc finished
     */
    private void handlePlaylistProgression(Location location) {
        String locationKey = getLocationKey(location);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Playlist] Checking progression at " + locationKey);
            plugin.getLogger().info("[Playlist] Active queues: " + playlistQueues.size());
            plugin.getLogger().info("[Playlist] Queue keys: " + playlistQueues.keySet());
        }

        // Synchronize access to prevent race conditions
        PlaylistQueue queue = playlistQueues.get(locationKey);

        if (queue == null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[Playlist] No queue found - not a playlist playback");
            }
            return; // No playlist active
        }

        plugin.getLogger().info("[Playlist] Progressing at " + locationKey);

        // Peek at next disc without advancing the index yet
        if (queue.hasNext()) {
            CustomDisc nextDisc = queue.peekNext();
            if (nextDisc != null) {
                // Only advance the index after successful peek
                queue.next(); // Now safe to advance
                // Play next disc in queue using the queue's stored range
                plugin.getLogger().info("[Playlist] Playing next: " + nextDisc.getId() +
                    " (" + (queue.getCurrentIndex() + 1) + "/" + queue.getSize() + ")");

                startPlayback(location, nextDisc, false, queue.range);
            } else {
                plugin.getLogger().warning("[Playlist] Next disc is null at " + locationKey);
            }
        } else {
            // Playlist finished
            playlistQueues.remove(locationKey);
            plugin.getLogger().info("[Playlist] Finished at " + locationKey);
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
