package de.boondocksulfur.customjukebox.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents an ambient music zone: an area (a WorldGuard region or a radius
 * around a point) that continuously plays a looping playlist to every player
 * inside it. Unlike jukebox playback, a zone auto-starts for players as they
 * enter its area - no disc or command needed once configured.
 *
 * <p>Zones are mutable so they can be edited in-game via commands/GUI; the
 * {@code AmbientZoneManager} persists them to zones.json and restarts the
 * affected zone's playback when a value changes.
 *
 * @author BoondockSulfur
 * @since 3.3.0
 */
public class AmbientZone {

    /** How a zone decides whether a player is inside it. */
    public enum ZoneType {
        /** Membership delegated to a named WorldGuard region. */
        WORLDGUARD,
        /** Membership is "within {@code radius} blocks of {@code center}". */
        RADIUS,
        /** Membership is "inside the box spanned by two corner positions". */
        CUBOID,
        /**
         * Everyone on the server, in every world - a server-wide radio station.
         * Ignores world, area and height; a local zone with a higher priority
         * still takes precedence for players inside it.
         */
        GLOBAL
    }

    /**
     * How the zone's playlist is played to the people inside it.
     */
    public enum PlaybackMode {
        /** One shared timeline: everyone hears the same track at the same time.
         *  Late arrivals are handled by {@link SyncMode}. Good for events. */
        SYNCED,
        /** Each player runs the playlist on their own from the moment they
         *  enter, always hearing complete tracks (no mid-song switch), but not
         *  in sync with other players. Good for lobby/background music. */
        INDIVIDUAL
    }

    /**
     * How a player who enters mid-track is treated. The vanilla sound engine
     * cannot seek, so a late arrival can never be frame-synced into a running
     * track - these are the two honest options.
     */
    public enum SyncMode {
        /** Start the current track from its beginning for the new arrival
         *  (slightly offset from others, but never silence). */
        IMMEDIATE,
        /** Wait until the next track boundary, then join in sync with everyone
         *  (perfectly synced, but silent until the current track ends). */
        NEXT_TRACK
    }

    /** Sentinel for {@link #volume}: use the global playback volume. */
    public static final float VOLUME_INHERIT = -1f;

    private final String id;
    private boolean enabled;
    private String world;
    private ZoneType type;

    // RADIUS type
    private double centerX;
    private double centerY;
    private double centerZ;
    private double radius;

    // WORLDGUARD type
    private String region;

    // CUBOID type (two block corners; inclusive block range)
    private int x1, y1, z1;
    private int x2, y2, z2;
    private boolean pos1Set;
    private boolean pos2Set;

    private String playlistId;
    private boolean loop;
    private float volume;       // VOLUME_INHERIT or 0.0..4.0
    private SyncMode syncMode;
    private PlaybackMode playbackMode;
    private boolean fullHeight;  // ignore the Y axis (cylinder/column) vs. 3D (sphere/box)
    private int priority;       // higher wins when zones overlap
    private boolean shuffle;    // play the playlist in random order

    public AmbientZone(String id) {
        this.id = id;
        this.enabled = true;
        this.world = "world";
        this.type = ZoneType.RADIUS;
        this.centerX = 0;
        this.centerY = 64;
        this.centerZ = 0;
        this.radius = 32;
        this.region = "";
        this.pos1Set = false;
        this.pos2Set = false;
        this.playlistId = "";
        this.loop = true;
        this.volume = VOLUME_INHERIT;
        this.syncMode = SyncMode.IMMEDIATE;
        this.playbackMode = PlaybackMode.SYNCED;
        this.fullHeight = true;
        this.priority = 0;
        this.shuffle = false;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public ZoneType getType() {
        return type;
    }

    public void setType(ZoneType type) {
        this.type = type;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenter(double x, double y, double z) {
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region != null ? region : "";
    }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }
    public boolean isPos1Set() { return pos1Set; }
    public boolean isPos2Set() { return pos2Set; }

    /**
     * Sets the first cuboid corner (block coordinates).
     */
    public void setPos1(int x, int y, int z) {
        this.x1 = x;
        this.y1 = y;
        this.z1 = z;
        this.pos1Set = true;
    }

    /**
     * Sets the second cuboid corner (block coordinates).
     */
    public void setPos2(int x, int y, int z) {
        this.x2 = x;
        this.y2 = y;
        this.z2 = z;
        this.pos2Set = true;
    }

    /** Whether both cuboid corners have been set. */
    public boolean hasBothCorners() {
        return pos1Set && pos2Set;
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }

    /**
     * Whether the zone ignores the Y axis: a radius zone becomes a vertical
     * cylinder and a cuboid an infinite column (only X/Z bound it). When false,
     * the zone is a true 3D sphere/box. Defaults to true so a lobby covers all
     * heights within its horizontal footprint.
     */
    public boolean isFullHeight() {
        return fullHeight;
    }

    public void setFullHeight(boolean fullHeight) {
        this.fullHeight = fullHeight;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(String playlistId) {
        this.playlistId = playlistId != null ? playlistId : "";
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public boolean inheritsVolume() {
        return volume < 0;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(SyncMode syncMode) {
        this.syncMode = syncMode;
    }

    /**
     * Whether the zone plays its playlist in random order, re-shuffling on each
     * lap so a looping zone is not one fixed sequence forever.
     * @return true if shuffled
     */
    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Whether a location's world matches this zone's world. A null world (e.g.
     * an unloaded world) never matches.
     * @param location location to test
     * @return true if the worlds match by name
     */
    public boolean matchesWorld(Location location) {
        if (location == null) {
            return false;
        }
        // A radio station is not bound to a world
        if (type == ZoneType.GLOBAL) {
            return true;
        }
        World w = location.getWorld();
        return w != null && w.getName().equals(world);
    }

    /**
     * Radius containment test for {@link ZoneType#RADIUS} zones. Uses squared
     * distance to avoid a sqrt on the hot scan path. Callers must ensure the
     * world already matches; WorldGuard zones are resolved elsewhere.
     * @param location location to test
     * @return true if within radius (inclusive)
     */
    public boolean withinRadius(Location location) {
        double dx = location.getX() - centerX;
        double dz = location.getZ() - centerZ;
        double horizontal = dx * dx + dz * dz;
        if (fullHeight) {
            // Cylinder: horizontal distance only, any height.
            return horizontal <= (radius * radius);
        }
        double dy = location.getY() - centerY;
        return (horizontal + dy * dy) <= (radius * radius);
    }

    /**
     * Inclusive block-range containment for {@link ZoneType#CUBOID} zones - the
     * box spans both corner blocks fully (like a WorldGuard cuboid selection).
     * Callers must ensure the world already matches.
     * @param location location to test
     * @return true if the player's block is inside the box
     */
    public boolean withinCuboid(Location location) {
        if (!pos1Set || !pos2Set) {
            return false;
        }
        int bx = location.getBlockX();
        int bz = location.getBlockZ();
        boolean insideXZ = bx >= Math.min(x1, x2) && bx <= Math.max(x1, x2)
            && bz >= Math.min(z1, z2) && bz <= Math.max(z1, z2);
        if (!insideXZ) {
            return false;
        }
        if (fullHeight) {
            // Infinite column: only X/Z bound it.
            return true;
        }
        int by = location.getBlockY();
        return by >= Math.min(y1, y2) && by <= Math.max(y1, y2);
    }

    /**
     * Whether this zone is fully configured and can actually play. A disabled
     * zone, a zone without a playlist, or a WorldGuard zone without a region
     * name is not runnable.
     * @return true if the zone can be activated
     */
    public boolean isRunnable() {
        if (!enabled || playlistId == null || playlistId.isEmpty()) {
            return false;
        }
        switch (type) {
            case GLOBAL:
                return true; // Covers the whole server - nothing else to configure
            case WORLDGUARD:
                return region != null && !region.isEmpty();
            case CUBOID:
                return hasBothCorners();
            case RADIUS:
            default:
                return radius > 0;
        }
    }
}
