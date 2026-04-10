package de.boondocksulfur.customjukebox.model;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an active playback session for a jukebox.
 * Tracks which disc is playing, when it started, and which players are hearing it.
 */
public class JukeboxPlayback {

    private final Location jukeboxLocation;
    private final CustomDisc disc;
    private final long startTime;           // System.currentTimeMillis() when playback started
    private final Set<UUID> listeners;      // Players currently hearing this disc
    private boolean stopped;
    private boolean loop;                   // Whether this playback should loop
    private PlaybackRange range;            // Playback range/scope

    public JukeboxPlayback(Location jukeboxLocation, CustomDisc disc) {
        this(jukeboxLocation, disc, false, new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    public JukeboxPlayback(Location jukeboxLocation, CustomDisc disc, boolean loop) {
        this(jukeboxLocation, disc, loop, new PlaybackRange(PlaybackRange.RangeType.NORMAL));
    }

    public JukeboxPlayback(Location jukeboxLocation, CustomDisc disc, boolean loop, PlaybackRange range) {
        // Clone once on construction to ensure immutability
        this.jukeboxLocation = jukeboxLocation.clone();
        this.disc = disc;
        this.startTime = System.currentTimeMillis();
        this.listeners = new HashSet<>();
        this.stopped = false;
        this.loop = loop;
        this.range = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);
    }

    /**
     * Gets the jukebox location.
     * WARNING: This returns the internal location reference for performance.
     * DO NOT modify the returned location!
     * @return The jukebox location (do not modify!)
     */
    public Location getJukeboxLocation() {
        // Return reference for performance - caller must not modify!
        return jukeboxLocation;
    }

    /**
     * Gets a safe clone of the jukebox location.
     * Use this if you need to modify the location.
     * @return A cloned location that is safe to modify
     */
    public Location getJukeboxLocationClone() {
        return jukeboxLocation.clone();
    }

    public CustomDisc getDisc() {
        return disc;
    }

    public long getStartTime() {
        return startTime;
    }

    public Set<UUID> getListeners() {
        return new HashSet<>(listeners);
    }

    public void addListener(Player player) {
        listeners.add(player.getUniqueId());
    }

    public void removeListener(Player player) {
        listeners.remove(player.getUniqueId());
    }

    public void removeListener(UUID playerUUID) {
        listeners.remove(playerUUID);
    }

    public boolean hasListener(Player player) {
        return listeners.contains(player.getUniqueId());
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public PlaybackRange getRange() {
        return range;
    }

    public void setRange(PlaybackRange range) {
        this.range = range != null ? range : new PlaybackRange(PlaybackRange.RangeType.NORMAL);
    }

    /**
     * Gets the elapsed time in milliseconds since playback started.
     * @return Elapsed time in ms
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Gets the elapsed time in ticks since playback started.
     * @return Elapsed time in ticks (20 ticks = 1 second)
     */
    public long getElapsedTicks() {
        return getElapsedTime() / 50; // 1 tick = 50ms
    }

    /**
     * Checks if the playback has exceeded the disc's duration.
     * @return true if playback should be finished
     */
    public boolean isFinished() {
        if (disc.getDurationTicks() <= 0) {
            return false; // No duration set, plays indefinitely
        }
        return getElapsedTicks() >= disc.getDurationTicks();
    }

    /**
     * Gets the remaining time in ticks.
     * @return Remaining ticks, or -1 if infinite duration
     */
    public long getRemainingTicks() {
        if (disc.getDurationTicks() <= 0) {
            return -1; // Infinite
        }
        long remaining = disc.getDurationTicks() - getElapsedTicks();
        return Math.max(0, remaining);
    }

    /**
     * Gets a simple location key for map storage.
     * @return Location key (world:x:y:z)
     */
    public String getLocationKey() {
        return jukeboxLocation.getWorld().getName() + ":" +
               jukeboxLocation.getBlockX() + ":" +
               jukeboxLocation.getBlockY() + ":" +
               jukeboxLocation.getBlockZ();
    }

    /**
     * Creates a location key from a location.
     * @param loc Location
     * @return Location key
     */
    public static String getLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" +
               loc.getBlockX() + ":" +
               loc.getBlockY() + ":" +
               loc.getBlockZ();
    }
}
