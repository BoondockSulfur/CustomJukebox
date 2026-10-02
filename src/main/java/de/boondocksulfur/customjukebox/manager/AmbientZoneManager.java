package de.boondocksulfur.customjukebox.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.api.events.CustomSoundPlayEvent;
import de.boondocksulfur.customjukebox.api.events.CustomSoundStopEvent;
import de.boondocksulfur.customjukebox.model.AmbientZone;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.model.NowPlaying;
import de.boondocksulfur.customjukebox.utils.BackupUtil;
import de.boondocksulfur.customjukebox.utils.JsonConfigUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages ambient music zones: areas that continuously play a looping playlist
 * to every player inside them, auto-starting as players enter.
 *
 * <p><b>How it works.</b> Each runnable zone runs its own playlist "timeline":
 * a per-zone track timer advances through the playlist (looping back to the
 * start) independently of who is listening. A single repeating scanner checks
 * every online player's position each interval; when a player crosses into or
 * out of a zone it starts/stops the zone's current track for that player.
 *
 * <p><b>The unavoidable limit.</b> Minecraft's sound engine cannot seek, so a
 * player entering mid-track can only start the current track from its beginning
 * ({@link AmbientZone.SyncMode#IMMEDIATE}) or wait for the next track boundary
 * ({@link AmbientZone.SyncMode#NEXT_TRACK}). Everyone re-syncs at each boundary.
 *
 * <p><b>Threading.</b> On Folia the scanner dispatches each player's evaluation
 * to that player's region thread, and the global track timer dispatches sound
 * playback per-player the same way, so player state is only ever touched on the
 * owning thread. All shared maps are concurrent.
 *
 * @author BoondockSulfur
 * @since 3.3.0
 */
public class AmbientZoneManager {

    private static final int ZONES_CONFIG_VERSION = 1;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final float DEFAULT_PITCH = 1.0f;
    private static final int DEFAULT_SCAN_INTERVAL = 20; // ticks (1s)
    private static final int MIN_SCAN_INTERVAL = 5;
    private static final int MAX_SCAN_INTERVAL = 200;

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File zonesFile;
    private volatile JsonObject zonesConfig;
    /** Guards edits to the zonesConfig tree and the snapshot taken for saving. */
    private final Object configLock = new Object();

    // Configured zones (id -> zone), refilled on reload.
    private final Map<String, AmbientZone> zones = new ConcurrentHashMap<>();
    // Live playback state for currently-active zones (id -> playback).
    private final Map<String, ZonePlayback> playbacks = new ConcurrentHashMap<>();
    // Which zone each player is currently assigned to (uuid -> zoneId).
    private final Map<UUID, String> playerZone = new ConcurrentHashMap<>();

    private volatile SchedulerUtil.TaskHandle scannerTask;
    private volatile boolean running;
    // Resolved once per start/reload instead of re-parsing the config string on
    // every play/stop call in the timeline hot path.
    private volatile SoundCategory soundCategory = SoundCategory.RECORDS;

    /**
     * Live timeline for one active zone. The playlist is pre-filtered to discs
     * that have a duration (a zero-duration disc can't be auto-advanced).
     */
    private static final class ZonePlayback {
        final AmbientZone zone;
        final List<CustomDisc> discs;
        /**
         * Snapshot of the zone settings that actually shape this live timeline.
         * An edit that leaves the signature unchanged (area, priority, sync
         * mode) must not tear the timeline down and restart the music.
         */
        final String signature;
        volatile boolean active;

        // ----- SYNCED mode: one shared timeline all listeners hear -----
        volatile int index;
        volatile CustomDisc current;
        /** When the current track started, for the progress display. */
        volatile long trackStartMillis;
        final Set<UUID> listeners = ConcurrentHashMap.newKeySet();
        /**
         * Point-source zones only: everyone who was sent the current track. A
         * player who walks out keeps hearing it fade away, so they leave the
         * listener set but stay in here - to be stopped at the next track
         * boundary, and so that walking back in does not start the track a
         * second time over the one still playing.
         */
        final Set<UUID> received = ConcurrentHashMap.newKeySet();
        /** Captured at start: the source is part of the signature, so a change restarts the timeline. */
        final boolean point;
        volatile SchedulerUtil.TaskHandle trackTask;
        /**
         * Bumped whenever a track timer is scheduled. The timer callback only
         * acts if it still carries the current value, so a skip that coincides
         * with a regular track end cannot advance twice and leave two timer
         * chains running.
         */
        int trackGeneration;
        // A non-looping playlist that played through: the zone stays active but
        // idle (silent) until a new player entering restarts it from track 0.
        volatile boolean finished;

        // ----- INDIVIDUAL mode: each player runs their own cursor -----
        final Map<UUID, IndividualTrack> individual = new ConcurrentHashMap<>();

        ZonePlayback(AmbientZone zone, List<CustomDisc> discs) {
            this.zone = zone;
            this.discs = discs;
            this.signature = playbackSignature(zone);
            this.point = zone.isPointSource();
            this.index = 0;
            this.current = discs.isEmpty() ? null : discs.get(0);
            this.trackStartMillis = System.currentTimeMillis();
            this.active = false;
            this.finished = false;
        }

        boolean isIndividual() {
            // A point source is one shared jukebox - everyone hears the same track
            return !point && zone.getPlaybackMode() == AmbientZone.PlaybackMode.INDIVIDUAL;
        }

        /** Who must be stopped when the current track ends or the zone goes quiet. */
        Set<UUID> hearing() {
            return point ? received : listeners;
        }
    }

    /**
     * The zone settings a running timeline is built from. Only a change to one
     * of these requires restarting playback; everything else (area, priority,
     * height, sync mode) is evaluated live by the scanner or on arrival.
     */
    private static String playbackSignature(AmbientZone zone) {
        return zone.isEnabled()
            + "|" + zone.getPlaylistId()
            + "|" + zone.isLoop()
            + "|" + zone.getPlaybackMode()
            + "|" + zone.isShuffle()
            + "|" + zone.getVolume()
            + "|" + zone.getSoundSource();
    }

    /** A single player's playlist cursor inside an INDIVIDUAL-mode zone. */
    private static final class IndividualTrack {
        /** This player's own order - shuffled per player, reshuffled per lap. */
        final List<CustomDisc> order;
        volatile int index;
        volatile CustomDisc current;
        volatile long trackStartMillis;
        volatile SchedulerUtil.TaskHandle task;
        /**
         * Set when the cursor is torn down. The scheduled end-of-track callback
         * may already be running when that happens, so it re-checks this flag
         * under the cursor's monitor before starting the next track.
         */
        volatile boolean cancelled;
        /** Same purpose as {@link ZonePlayback#trackGeneration}. */
        int generation;

        IndividualTrack(List<CustomDisc> order) {
            this.order = order;
        }
    }

    public AmbientZoneManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
        this.zonesFile = new File(plugin.getDataFolder(), "zones.json");

        loadZonesFile();
        loadZones();
    }

    // ==================== CONFIG LOADING ====================

    /**
     * @return true if zones.json was read; false if not, in which case the
     *         previously loaded zones stay as they are
     */
    private boolean loadZonesFile() {
        try {
            // A queued save must land before we read the file back
            if (plugin.getConfigWriter() != null) {
                plugin.getConfigWriter().flush();
            }
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            if (!zonesFile.exists()) {
                plugin.saveResource("zones.json", false);
                plugin.getLogger().info("Created default zones.json");
            }

            long fileSize = zonesFile.length();
            if (fileSize > MAX_FILE_SIZE) {
                throw new IOException("zones.json exceeds maximum file size of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
            }

            JsonObject loaded;
            try (Reader reader = new InputStreamReader(new FileInputStream(zonesFile), StandardCharsets.UTF_8)) {
                loaded = gson.fromJson(reader, JsonObject.class);
            }
            if (loaded == null) {
                loaded = new JsonObject();
            }
            synchronized (configLock) {
                this.zonesConfig = loaded;
            }
            plugin.getConfigWriter().unblock(zonesFile);
            boolean addedKeys;
            boolean versionChanged;
            synchronized (configLock) {
                if (zonesConfig.has("zones") && !zonesConfig.get("zones").isJsonObject()) {
                    zonesConfig.add("zones", new JsonObject());
                }

                // Merge structural defaults (settings), but never seed the user-owned
                // "zones" map with examples.
                addedKeys = mergeDefaults();

                int fileVersion = (int) getDbl(zonesConfig, "version", 0);
                versionChanged = fileVersion != ZONES_CONFIG_VERSION && fileVersion <= ZONES_CONFIG_VERSION;
                if (versionChanged) {
                    zonesConfig.addProperty("version", ZONES_CONFIG_VERSION);
                }
            }
            if (addedKeys || versionChanged) {
                saveZonesFile();
            }
            return true;
        } catch (Exception e) {
            // Never write the fallback over the file - see ConfigWriter.quarantine
            plugin.getConfigWriter().quarantine(zonesFile, e);
            if (this.zonesConfig == null) {
                JsonObject empty = new JsonObject();
                empty.add("zones", new JsonObject());
                this.zonesConfig = empty;
                return true;
            }
            plugin.getLogger().severe("Keeping the previously loaded zones.");
            return false;
        }
    }

    private boolean mergeDefaults() {
        try (InputStream defaultStream = plugin.getResource("zones.json")) {
            if (defaultStream == null) {
                return false;
            }
            JsonObject defaults = gson.fromJson(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8), JsonObject.class);
            if (defaults == null) {
                return false;
            }
            return JsonConfigUtil.mergeDefaults(zonesConfig, defaults, Set.of("zones"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to merge default zones.json keys: " + e.getMessage());
            return false;
        }
    }

    private void loadZones() {
        Map<String, AmbientZone> loaded = new HashMap<>();
        if (zonesConfig.has("zones") && zonesConfig.get("zones").isJsonObject()) {
            JsonObject zonesSection = zonesConfig.getAsJsonObject("zones");
            for (String id : zonesSection.keySet()) {
                try {
                    AmbientZone zone = parseZone(id, zonesSection.getAsJsonObject(id));
                    loaded.put(id, zone);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse ambient zone '" + id + "': " + e.getMessage());
                }
            }
        }
        zones.clear();
        zones.putAll(loaded);
        if (!zones.isEmpty()) {
            plugin.getLogger().info("Loaded " + zones.size() + " ambient zone(s)");
        }
    }

    private AmbientZone parseZone(String id, JsonObject data) {
        AmbientZone zone = new AmbientZone(id);
        zone.setEnabled(getBool(data, "enabled", true));
        zone.setWorld(getStr(data, "world", "world"));

        String typeStr = getStr(data, "type", "radius");
        if ("worldguard".equalsIgnoreCase(typeStr)) {
            zone.setType(AmbientZone.ZoneType.WORLDGUARD);
        } else if ("cuboid".equalsIgnoreCase(typeStr)) {
            zone.setType(AmbientZone.ZoneType.CUBOID);
        } else if ("global".equalsIgnoreCase(typeStr) || "radio".equalsIgnoreCase(typeStr)) {
            zone.setType(AmbientZone.ZoneType.GLOBAL);
        } else {
            zone.setType(AmbientZone.ZoneType.RADIUS);
        }

        if (data.has("center") && data.get("center").isJsonObject()) {
            JsonObject center = data.getAsJsonObject("center");
            zone.setCenter(getDbl(center, "x", 0), getDbl(center, "y", 64), getDbl(center, "z", 0));
        }
        double radius = getDbl(data, "radius", 32);
        zone.setRadius(Double.isFinite(radius) ? radius : 0);
        zone.setRegion(getStr(data, "region", ""));

        if (data.has("pos1") && data.get("pos1").isJsonObject()) {
            JsonObject p = data.getAsJsonObject("pos1");
            zone.setPos1((int) getDbl(p, "x", 0), (int) getDbl(p, "y", 0), (int) getDbl(p, "z", 0));
        }
        if (data.has("pos2") && data.get("pos2").isJsonObject()) {
            JsonObject p = data.getAsJsonObject("pos2");
            zone.setPos2((int) getDbl(p, "x", 0), (int) getDbl(p, "y", 0), (int) getDbl(p, "z", 0));
        }

        zone.setPlaylistId(getStr(data, "playlist", ""));
        zone.setLoop(getBool(data, "loop", true));
        float volume = (float) getDbl(data, "volume", AmbientZone.VOLUME_INHERIT);
        zone.setVolume(Float.isFinite(volume) ? volume : AmbientZone.VOLUME_INHERIT);

        String sync = getStr(data, "syncMode", "immediate");
        zone.setSyncMode("next_track".equalsIgnoreCase(sync)
            ? AmbientZone.SyncMode.NEXT_TRACK : AmbientZone.SyncMode.IMMEDIATE);

        String playback = getStr(data, "playback", "synced");
        zone.setPlaybackMode("individual".equalsIgnoreCase(playback)
            ? AmbientZone.PlaybackMode.INDIVIDUAL : AmbientZone.PlaybackMode.SYNCED);

        zone.setFullHeight(getBool(data, "fullHeight", true));
        zone.setShuffle(getBool(data, "shuffle", false));
        zone.setPriority((int) getDbl(data, "priority", 0));
        zone.setSoundSource("point".equalsIgnoreCase(getStr(data, "source", "player"))
            ? AmbientZone.SoundSource.POINT : AmbientZone.SoundSource.PLAYER);
        if (data.has("jukebox") && data.get("jukebox").isJsonObject()) {
            JsonObject jukebox = data.getAsJsonObject("jukebox");
            zone.placeJukebox((int) getDbl(jukebox, "x", 0), (int) getDbl(jukebox, "y", 0),
                (int) getDbl(jukebox, "z", 0), getStr(jukebox, "owner", ""));
            if (!getBool(jukebox, "placed", false)) {
                zone.removeJukebox();
            }
        }
        return zone;
    }

    private JsonObject serializeZone(AmbientZone zone) {
        JsonObject data = new JsonObject();
        data.addProperty("enabled", zone.isEnabled());
        data.addProperty("world", zone.getWorld());
        String typeStr = switch (zone.getType()) {
            case WORLDGUARD -> "worldguard";
            case CUBOID -> "cuboid";
            case GLOBAL -> "global";
            default -> "radius";
        };
        data.addProperty("type", typeStr);

        JsonObject center = new JsonObject();
        center.addProperty("x", zone.getCenterX());
        center.addProperty("y", zone.getCenterY());
        center.addProperty("z", zone.getCenterZ());
        data.add("center", center);

        data.addProperty("radius", zone.getRadius());
        data.addProperty("region", zone.getRegion());

        if (zone.isPos1Set()) {
            data.add("pos1", corner(zone.getX1(), zone.getY1(), zone.getZ1()));
        }
        if (zone.isPos2Set()) {
            data.add("pos2", corner(zone.getX2(), zone.getY2(), zone.getZ2()));
        }

        data.addProperty("playlist", zone.getPlaylistId());
        data.addProperty("loop", zone.isLoop());
        data.addProperty("volume", zone.getVolume());
        data.addProperty("syncMode", zone.getSyncMode() == AmbientZone.SyncMode.NEXT_TRACK ? "next_track" : "immediate");
        data.addProperty("playback", zone.getPlaybackMode() == AmbientZone.PlaybackMode.INDIVIDUAL ? "individual" : "synced");
        data.addProperty("fullHeight", zone.isFullHeight());
        data.addProperty("shuffle", zone.isShuffle());
        data.addProperty("priority", zone.getPriority());
        data.addProperty("source", zone.isPointSource() ? "point" : "player");
        // Only written for zones that use a zone jukebox, so other zones' JSON stays as it was
        if (zone.isJukeboxBound()) {
            JsonObject jukebox = corner(zone.getJukeboxX(), zone.getJukeboxY(), zone.getJukeboxZ());
            jukebox.addProperty("placed", zone.isJukeboxPlaced());
            jukebox.addProperty("owner", zone.getJukeboxOwner());
            data.add("jukebox", jukebox);
        }
        return data;
    }

    private JsonObject corner(int x, int y, int z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }

    private void saveZonesFile() {
        JsonObject snapshot;
        synchronized (configLock) {
            zonesConfig.addProperty("version", ZONES_CONFIG_VERSION);
            snapshot = zonesConfig.deepCopy();
            // Queued under the lock the snapshot was taken under, so an older
            // snapshot from a concurrent save can never land after a newer one
            plugin.getConfigWriter().save(zonesFile, snapshot,
                plugin.getConfigManager().getMaxBackups(),
                plugin.getConfigManager().getBackupMinIntervalMillis());
        }
    }

    // ==================== LIFECYCLE ====================

    /**
     * Starts the ambient-zone system: activates every runnable zone's timeline
     * and begins the membership scanner. Safe to call when already running (it
     * is a no-op then) and when the feature is disabled in config (also a no-op).
     */
    public void start() {
        if (running) {
            return;
        }
        if (!plugin.getConfigManager().isAmbientZonesEnabled()) {
            return;
        }
        running = true;
        soundCategory = plugin.getConfigManager().getAmbientZoneSoundCategory();

        int activated = 0;
        for (AmbientZone zone : zones.values()) {
            if (startZonePlayback(zone)) {
                activated++;
            }
        }

        warnAboutWorldGuardOnFolia();

        long interval = getScanIntervalTicks();
        scannerTask = SchedulerUtil.runGlobalTimer(plugin, this::scan, interval, interval);
        if (scannerTask == null) {
            plugin.getLogger().severe("Ambient-zone scanner could not be scheduled - zones will not auto-start. "
                + "This usually means the Folia scheduler API changed; please update the plugin.");
            running = false;
            return;
        }

        if (activated > 0) {
            plugin.getLogger().info("Ambient zones active: " + activated
                + " zone(s), scan interval " + interval + " ticks, sound category " + soundCategory);
        }
    }

    /**
     * WorldGuard region lookups happen on the scanning player's region thread on
     * Folia, and WorldGuard is not officially Folia-safe. The query is read-only
     * and guarded, but an admin should know why a zone might behave oddly.
     */
    private void warnAboutWorldGuardOnFolia() {
        if (!SchedulerUtil.isFolia() || !plugin.getIntegrationManager().isWorldGuardEnabled()) {
            return;
        }
        boolean usesWorldGuard = zones.values().stream()
            .anyMatch(z -> z.isEnabled() && z.getType() == AmbientZone.ZoneType.WORLDGUARD);
        if (usesWorldGuard) {
            plugin.getLogger().warning("Ambient zones of type 'worldguard' are configured on Folia. "
                + "WorldGuard is not Folia-safe; region lookups are read-only and failure-tolerant, "
                + "but if zones misbehave, switch them to 'radius' or 'cuboid' (/cjb zone radius|pos1|pos2).");
        }
    }

    /**
     * Stops the scanner, cancels all track timers, and stops sound for every
     * listener. Leaves the loaded {@link #zones} map intact so {@link #start()}
     * can bring them back.
     */
    public void stop() {
        running = false;

        SchedulerUtil.cancelTask(scannerTask);
        scannerTask = null;

        for (ZonePlayback zp : playbacks.values()) {
            // Same lock the track timer takes, so a callback that is already
            // running cannot start another track behind our back
            synchronized (zp) {
                zp.active = false;
                SchedulerUtil.cancelTask(zp.trackTask);
                zp.trackTask = null;
                stopSoundForAll(zp);
                stopAllIndividual(zp);
            }
        }
        playbacks.clear();
        playerZone.clear();
    }

    /**
     * Reloads zones.json and restarts the system from scratch.
     */
    public void reload() {
        stop();
        if (loadZonesFile()) {
            loadZones();
        }
        start();
    }

    private boolean startZonePlayback(AmbientZone zone) {
        if (!zone.isRunnable()) {
            return false;
        }
        // A world that is not loaded yet (late-loading world managers) is not a
        // reason to refuse the zone: membership is tested against each player's
        // own world, so the zone simply matches nobody until the world appears.
        if (zone.getType() != AmbientZone.ZoneType.GLOBAL && Bukkit.getWorld(zone.getWorld()) == null) {
            plugin.getLogger().warning("Ambient zone '" + zone.getId()
                + "' references world '" + zone.getWorld() + "', which is not loaded (yet)"
                + " - the zone stays silent until that world exists");
        }

        List<CustomDisc> playable = collectPlayableDiscs(zone.getPlaylistId());
        if (zone.isShuffle() && playable.size() > 1) {
            Collections.shuffle(playable);
        }
        if (playable.isEmpty()) {
            plugin.getLogger().warning("Ambient zone '" + zone.getId() + "' playlist '" + zone.getPlaylistId()
                + "' has no playable discs (need a custom sound and a duration) - skipping");
            return false;
        }

        ZonePlayback zp = new ZonePlayback(zone, playable);
        zp.active = true;
        playbacks.put(zone.getId(), zp);
        // SYNCED runs one shared timeline immediately; INDIVIDUAL starts each
        // player's own timeline when they enter (see startIndividual).
        if (!zp.isIndividual()) {
            synchronized (zp) {
                scheduleTrackEnd(zp);
            }
        }
        return true;
    }

    // ==================== TRACK TIMELINE ====================

    /**
     * Discs of a playlist that a zone can actually advance through: they need a
     * custom sound to play and a duration to schedule the next track from.
     */
    private List<CustomDisc> collectPlayableDiscs(String playlistId) {
        List<CustomDisc> playable = new ArrayList<>();
        for (CustomDisc disc : plugin.getDiscManager().getDiscsFromPlaylist(playlistId)) {
            if (disc.hasCustomSound() && disc.getDurationTicks() > 0) {
                playable.add(disc);
            }
        }
        return playable;
    }

    /** Must be called holding the playback's monitor. */
    private void scheduleTrackEnd(ZonePlayback zp) {
        SchedulerUtil.cancelTask(zp.trackTask);
        zp.trackTask = null;
        CustomDisc disc = zp.current;
        if (disc == null) {
            return;
        }
        int generation = ++zp.trackGeneration;
        zp.trackTask = SchedulerUtil.runGlobalLater(plugin, () -> {
            synchronized (zp) {
                if (generation == zp.trackGeneration) {
                    advanceTrack(zp);
                }
            }
        }, disc.getDurationTicks());
    }

    /**
     * Advances a zone's shared timeline by one track.
     *
     * <p>Runs under the playback's monitor, which {@link #deactivateZone} and
     * {@link #stop} also take: without it, a teardown could land between the
     * {@code active} check and the playback below, leaving a track playing that
     * nothing would ever stop again.
     */
    private void advanceTrack(ZonePlayback zp) {
        synchronized (zp) {
            if (!zp.active) {
                return;
            }
            // Whatever timer was pending belongs to the track that just ended
            ++zp.trackGeneration;
            SchedulerUtil.cancelTask(zp.trackTask);

            CustomDisc previous = zp.current;

            int next = zp.index + 1;
            if (next >= zp.discs.size()) {
                if (zp.zone.isLoop()) {
                    next = 0;
                } else {
                    // Non-looping playlist reached its end. Keep the zone active but
                    // idle: current listeners fall silent, and the next player to
                    // enter restarts it from the first track (see restartTimeline).
                    zp.finished = true;
                    zp.trackTask = null;
                    if (previous != null) {
                        dispatchStop(zp.hearing(), previous);
                    }
                    zp.received.clear();
                    return;
                }
            }

            if (next == 0 && zp.zone.isShuffle() && zp.discs.size() > 1) {
                // New lap: reshuffle so a looping zone is not one fixed order.
                // Avoid repeating the track that just played across the wrap.
                CustomDisc last = zp.discs.get(zp.discs.size() - 1);
                Collections.shuffle(zp.discs);
                if (zp.discs.get(0).getId().equals(last.getId())) {
                    Collections.swap(zp.discs, 0, zp.discs.size() - 1);
                }
            }

            zp.index = next;
            zp.current = zp.discs.get(next);
            zp.trackStartMillis = System.currentTimeMillis();

            // Track boundary: stop the finished track (in case its .ogg outlasts the
            // configured duration) and (re)play the new one to everyone in the zone.
            // This is where IMMEDIATE arrivals re-sync and NEXT_TRACK arrivals join.
            if (previous != null) {
                dispatchStop(zp.hearing(), previous);
            }
            zp.received.clear();
            playSoundForAll(zp);
            scheduleTrackEnd(zp);
        }
    }

    // ==================== SCANNER ====================

    private void scan() {
        if (!running) {
            return;
        }
        boolean folia = SchedulerUtil.isFolia();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (folia) {
                // Read location + play/stop sound only on the player's region thread.
                SchedulerUtil.runPlayerTask(plugin, player, () -> evaluatePlayer(player));
            } else {
                evaluatePlayer(player);
            }
        }
        // Drop assignments for players who logged off between the join map and now.
        playerZone.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        showJukeboxNotes();
    }

    /**
     * Note particles over every zone jukebox that is playing, like a vanilla
     * jukebox shows them - so players can see where the music comes from.
     */
    private void showJukeboxNotes() {
        for (ZonePlayback zp : playbacks.values()) {
            AmbientZone zone = zp.zone;
            if (!zp.active || zp.finished || zp.current == null || !zone.isJukeboxPlaced()) {
                continue;
            }
            org.bukkit.World world = Bukkit.getWorld(zone.getWorld());
            if (world == null) {
                continue;
            }
            int bx = zone.getJukeboxX();
            int bz = zone.getJukeboxZ();
            Location above = new Location(world, bx + 0.5, zone.getJukeboxY() + 1.2, bz + 0.5);
            // The note particle takes its color from the X offset (0..1)
            double color = java.util.concurrent.ThreadLocalRandom.current().nextInt(25) / 24.0;
            SchedulerUtil.run(plugin, above, () -> {
                if (world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    world.spawnParticle(org.bukkit.Particle.NOTE, above, 0, color, 0, 0, 1);
                }
            });
        }
    }

    /**
     * The zone a placed zone jukebox block belongs to.
     *
     * <p>The block carries the zone ID, but that alone is not trusted: the zone
     * must still exist and record this very block as its placed jukebox. A block
     * left behind after its zone was deleted, unbound or re-created is then just
     * an ordinary jukebox again.
     *
     * @param block block to check; must be read on its region thread on Folia
     * @return the zone, or null if the block is not a zone jukebox
     */
    public AmbientZone zoneForJukebox(org.bukkit.block.Block block) {
        if (block == null || block.getType() != org.bukkit.Material.JUKEBOX
                || !(block.getState() instanceof org.bukkit.block.TileState state)) {
            return null;
        }
        String zoneId = state.getPersistentDataContainer().get(
            de.boondocksulfur.customjukebox.utils.ItemUtil.ZONE_JUKEBOX_KEY,
            org.bukkit.persistence.PersistentDataType.STRING);
        AmbientZone zone = zoneId == null ? null : zones.get(zoneId);
        if (zone == null || !zone.isJukeboxAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
            return null;
        }
        return zone;
    }

    private void evaluatePlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();

        // Point-source zones overlap like jukeboxes do and stay outside the
        // one-zone-per-player assignment below
        evaluatePointZones(player, loc);

        String newZoneId = findZoneFor(loc);
        String oldZoneId = playerZone.get(uuid);

        if (Objects.equals(newZoneId, oldZoneId)) {
            return; // No boundary crossed.
        }

        // Leaving the previous zone.
        if (oldZoneId != null) {
            ZonePlayback oldZp = playbacks.get(oldZoneId);
            if (oldZp != null) {
                leaveZone(oldZp, player);
            }
            playerZone.remove(uuid, oldZoneId);
        }

        // Entering a new zone. Only recorded once the player is actually
        // attached: a zone restarted by a command in the meantime is left
        // unrecorded, so the next scan attaches the player to its new timeline
        // instead of treating them as already inside.
        if (newZoneId != null) {
            ZonePlayback newZp = playbacks.get(newZoneId);
            if (newZp != null) {
                enterZone(newZp, player, newZoneId);
            }
        }
    }

    /**
     * Joins and leaves point-source zones. Leaving never stops the sound - it
     * fades with distance on the client, like walking away from a jukebox.
     */
    private void evaluatePointZones(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        for (ZonePlayback zp : playbacks.values()) {
            if (!zp.point) {
                continue;
            }
            boolean inside = zp.zone.matchesWorld(loc) && zp.zone.withinRadius(loc);
            synchronized (zp) {
                if (!zp.active) {
                    continue;
                }
                boolean listening = zp.listeners.contains(uuid);
                if (inside && !listening) {
                    zp.listeners.add(uuid);
                    if (zp.finished) {
                        restartTimeline(zp);
                    } else if (zp.zone.getSyncMode() == AmbientZone.SyncMode.IMMEDIATE
                            && zp.current != null && !zp.received.contains(uuid)) {
                        // Still hearing this track from before? Then it goes on
                        // as it is - starting it again would play it twice
                        zp.received.add(uuid);
                        playSound(player, zp.current, volumeFor(zp.zone, uuid), sourceOf(zp, player));
                    }
                } else if (!inside && listening) {
                    zp.listeners.remove(uuid);
                }
            }
        }
    }

    /**
     * Where a zone's sound is played for a player: the zone center for a point
     * source, otherwise the player's own position.
     */
    private Location sourceOf(ZonePlayback zp, Player player) {
        if (zp.point) {
            org.bukkit.World world = Bukkit.getWorld(zp.zone.getWorld());
            if (world != null) {
                return new Location(world, zp.zone.getCenterX(), zp.zone.getCenterY(), zp.zone.getCenterZ());
            }
        }
        return player.getLocation();
    }

    /**
     * Detaches a player from a zone and stops what they hear from it. Under the
     * playback's monitor, so a track change cannot pick the player up again
     * halfway through.
     */
    private void leaveZone(ZonePlayback zp, Player player) {
        synchronized (zp) {
            if (zp.isIndividual()) {
                stopIndividual(zp, player);
            } else {
                zp.listeners.remove(player.getUniqueId());
                if (zp.current != null) {
                    stopSound(player, zp.current);
                }
            }
        }
    }

    /**
     * Attaches a player to a zone. Under the playback's monitor: joining the
     * listener set and reading the current track must not straddle a track
     * change, or the player would hear the new track twice, slightly offset.
     */
    private void enterZone(ZonePlayback zp, Player player, String zoneId) {
        UUID uuid = player.getUniqueId();
        synchronized (zp) {
            if (!zp.active) {
                return; // Torn down concurrently - the next scan retries
            }
            if (zp.isIndividual()) {
                // Each player runs the playlist on their own, always hearing
                // complete tracks.
                startIndividual(zp, player);
            } else {
                zp.listeners.add(uuid);
                if (zp.finished) {
                    // A non-loop zone that had played through: entering revives
                    // it from track 0 for everyone currently inside.
                    restartTimeline(zp);
                } else if (zp.zone.getSyncMode() == AmbientZone.SyncMode.IMMEDIATE && zp.current != null) {
                    playSound(player, zp.current, volumeFor(zp.zone, uuid), player.getLocation());
                }
                // NEXT_TRACK: the player is now a listener and will be included
                // when the track next changes - no sound yet.
            }
            playerZone.put(uuid, zoneId);
        }
    }

    // ==================== INDIVIDUAL MODE ====================

    /**
     * Starts a player's personal playlist timeline from the first track. Runs
     * on the player's region thread (called from the scanner), so it can play
     * sound directly.
     */
    private void startIndividual(ZonePlayback zp, Player player) {
        if (zp.discs.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        List<CustomDisc> order = new ArrayList<>(zp.discs);
        if (zp.zone.isShuffle() && order.size() > 1) {
            Collections.shuffle(order);
        }
        IndividualTrack it = new IndividualTrack(order);
        it.index = 0;
        it.current = order.get(0);
        it.trackStartMillis = System.currentTimeMillis();
        // Replace any prior cursor (defensive - a stale one shouldn't exist).
        IndividualTrack previous = zp.individual.put(uuid, it);
        if (previous != null) {
            cancelCursor(previous);
        }
        playSound(player, it.current, volumeFor(zp.zone, uuid), player.getLocation());
        scheduleIndividualEnd(zp, uuid, it);
    }

    /**
     * Marks a personal cursor dead and cancels its timer. The flag is what makes
     * an already-running end-of-track callback stand down - cancelling the task
     * alone loses that race.
     */
    private void cancelCursor(IndividualTrack it) {
        synchronized (it) {
            it.cancelled = true;
            SchedulerUtil.cancelTask(it.task);
            it.task = null;
        }
    }

    /**
     * Stops and removes a player's personal timeline. Runs on the player's
     * region thread (called from the scanner on leave).
     */
    private void stopIndividual(ZonePlayback zp, Player player) {
        IndividualTrack it = zp.individual.remove(player.getUniqueId());
        if (it != null) {
            cancelCursor(it);
            if (it.current != null) {
                stopSound(player, it.current);
            }
        }
    }

    private void scheduleIndividualEnd(ZonePlayback zp, UUID uuid, IndividualTrack it) {
        synchronized (it) {
            SchedulerUtil.cancelTask(it.task);
            it.task = null;
            CustomDisc disc = it.current;
            if (disc == null) {
                return;
            }
            int generation = ++it.generation;
            it.task = SchedulerUtil.runGlobalLater(plugin, () -> {
                synchronized (it) {
                    if (generation == it.generation) {
                        advanceIndividual(zp, uuid, it);
                    }
                }
            }, disc.getDurationTicks());
        }
    }

    private void advanceIndividual(ZonePlayback zp, UUID uuid, IndividualTrack it) {
        // Runs under the cursor's monitor so a concurrent teardown cannot slip
        // between the guard below and the playback that follows it.
        synchronized (it) {
            // Identity guard: the player may have left (and possibly re-entered
            // with a fresh cursor), or the zone may have been torn down.
            if (it.cancelled || !zp.active || zp.individual.get(uuid) != it) {
                return;
            }
            ++it.generation;
            SchedulerUtil.cancelTask(it.task);

            CustomDisc previous = it.current;

            int next = it.index + 1;
            if (next >= it.order.size()) {
                if (zp.zone.isLoop()) {
                    next = 0;
                } else {
                    // Player finished the playlist once: stop and drop their cursor.
                    // Re-entering the zone starts them over.
                    it.cancelled = true;
                    zp.individual.remove(uuid, it);
                    dispatchStopOne(uuid, previous);
                    return;
                }
            }

            if (next == 0 && zp.zone.isShuffle() && it.order.size() > 1) {
                // New lap: reshuffle this player's order, without repeating the
                // track that just played across the wrap
                CustomDisc last = it.order.get(it.order.size() - 1);
                Collections.shuffle(it.order);
                if (it.order.get(0).getId().equals(last.getId())) {
                    Collections.swap(it.order, 0, it.order.size() - 1);
                }
            }

            it.index = next;
            it.current = it.order.get(next);
            it.trackStartMillis = System.currentTimeMillis();

            // Stop the finished track (in case its .ogg outlasts the duration) and
            // play the next one to this single player.
            dispatchStopOne(uuid, previous);
            CustomDisc disc = it.current;
            dispatchPlayOne(uuid, disc, volumeFor(zp.zone, uuid),
                () -> !it.cancelled && zp.active && zp.individual.get(uuid) == it && it.current == disc);
            scheduleIndividualEnd(zp, uuid, it);
        }
    }

    /** Cancels every individual cursor of a zone and stops its sounds. */
    private void stopAllIndividual(ZonePlayback zp) {
        for (Map.Entry<UUID, IndividualTrack> e : zp.individual.entrySet()) {
            cancelCursor(e.getValue());
            dispatchStopOne(e.getKey(), e.getValue().current);
        }
        zp.individual.clear();
    }

    /**
     * @param stillWanted re-checked when the play actually runs: on Folia it is
     *                    queued to the player's thread, and the player may have
     *                    left the zone by then
     */
    private void dispatchPlayOne(UUID uuid, CustomDisc disc, float volume, java.util.function.BooleanSupplier stillWanted) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (SchedulerUtil.isFolia()) {
            SchedulerUtil.runPlayerTask(plugin, player, () -> {
                if (stillWanted.getAsBoolean()) {
                    playSound(player, disc, volume, player.getLocation());
                }
            });
        } else {
            playSound(player, disc, volume, player.getLocation());
        }
    }

    private void dispatchStopOne(UUID uuid, CustomDisc disc) {
        if (disc == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (SchedulerUtil.isFolia()) {
            SchedulerUtil.runPlayerTask(plugin, player, () -> stopSound(player, disc));
        } else {
            stopSound(player, disc);
        }
    }

    /**
     * Restarts a finished non-looping zone from its first track and plays it to
     * everyone currently inside. Guarded so two players entering in the same
     * tick can't schedule two competing track timers.
     */
    private void restartTimeline(ZonePlayback zp) {
        synchronized (zp) {
            if (!zp.active || !zp.finished) {
                return; // Torn down, or already revived by a concurrent enter.
            }
            zp.finished = false;
            zp.index = 0;
            zp.current = zp.discs.get(0);
            zp.trackStartMillis = System.currentTimeMillis();
            playSoundForAll(zp);
            scheduleTrackEnd(zp);
        }
    }

    /**
     * Finds the highest-priority active zone containing the location, or null.
     */
    private String findZoneFor(Location loc) {
        ZonePlayback best = null;
        for (ZonePlayback zp : playbacks.values()) {
            if (!zp.active || zp.point) {
                continue;
            }
            AmbientZone zone = zp.zone;
            if (!zone.matchesWorld(loc)) {
                continue;
            }
            boolean inside;
            switch (zone.getType()) {
                case GLOBAL:
                    inside = true; // Server-wide radio
                    break;
                case WORLDGUARD:
                    inside = plugin.getIntegrationManager().isInRegion(loc, zone.getRegion());
                    break;
                case CUBOID:
                    inside = zone.withinCuboid(loc);
                    break;
                case RADIUS:
                default:
                    inside = zone.withinRadius(loc);
                    break;
            }
            if (!inside) {
                continue;
            }
            // Highest priority wins; ties broken by zone id so the choice is
            // stable across scans (an unstable pick would flip a player between
            // two overlapping equal-priority zones and stutter the audio).
            if (best == null
                    || zone.getPriority() > best.zone.getPriority()
                    || (zone.getPriority() == best.zone.getPriority()
                        && zone.getId().compareTo(best.zone.getId()) < 0)) {
                best = zp;
            }
        }
        return best == null ? null : best.zone.getId();
    }

    // ==================== SOUND ====================

    private void playSoundForAll(ZonePlayback zp) {
        CustomDisc disc = zp.current;
        if (disc == null) {
            return;
        }
        boolean folia = SchedulerUtil.isFolia();
        for (UUID id : zp.listeners) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            // Resolved per listener - each may have a personal volume
            float volume = volumeFor(zp.zone, id);
            if (zp.point) {
                zp.received.add(id);
            }
            if (folia) {
                // Queued to the player's thread: by the time it runs, they may
                // have left the zone or the track may have moved on
                SchedulerUtil.runPlayerTask(plugin, player, () -> {
                    if (zp.active && zp.current == disc && zp.listeners.contains(id)) {
                        playSound(player, disc, volume, sourceOf(zp, player));
                    }
                });
            } else {
                playSound(player, disc, volume, sourceOf(zp, player));
            }
        }
    }

    private void stopSoundForAll(ZonePlayback zp) {
        dispatchStop(zp.hearing(), zp.current);
        zp.received.clear();
    }

    /**
     * Stops a specific disc's sound for every listener in the set, dispatching
     * to each player's region thread on Folia. Used both when a zone goes quiet
     * and at a track boundary (to stop the finished track before the next one).
     */
    private void dispatchStop(Set<UUID> listeners, CustomDisc disc) {
        if (disc == null) {
            return;
        }
        boolean folia = SchedulerUtil.isFolia();
        for (UUID id : new HashSet<>(listeners)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (folia) {
                SchedulerUtil.runPlayerTask(plugin, player, () -> stopSound(player, disc));
            } else {
                stopSound(player, disc);
            }
        }
    }

    /**
     * Plays a disc's custom sound to a player from the given position - their
     * own for ordinary zones, the zone center for a point source. Must run on
     * the player's region thread on Folia.
     */
    private void playSound(Player player, CustomDisc disc, float volume, Location source) {
        if (!disc.hasCustomSound()) {
            return;
        }
        // Players who turned plugin music off hear nothing from zones either
        if (!plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId())) {
            return;
        }
        // A disc is deliberate, a zone is background: never lay one over the
        // other. Guarding here covers every route into zone audio - a new
        // track, a player arriving, a resume - rather than each caller.
        if (isHearingDisc(player)) {
            return;
        }
        try {
            CustomSoundPlayEvent deliveryEvent = new CustomSoundPlayEvent(
                player, disc, source, CustomSoundPlayEvent.Source.AMBIENT_ZONE, volume);
            plugin.getServer().getPluginManager().callEvent(deliveryEvent);
            if (deliveryEvent.isCancelled()) {
                return; // A companion plugin delivers this sound instead
            }

            player.playSound(source, disc.getSoundKey(), soundCategory, volume, DEFAULT_PITCH);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Ambient zone failed to play '" + disc.getSoundKey()
                    + "' to " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void stopSound(Player player, CustomDisc disc) {
        if (!disc.hasCustomSound()) {
            return;
        }
        try {
            CustomSoundStopEvent stopEvent = new CustomSoundStopEvent(
                player, disc, CustomSoundPlayEvent.Source.AMBIENT_ZONE);
            plugin.getServer().getPluginManager().callEvent(stopEvent);
            if (stopEvent.isCancelled()) {
                return; // A companion plugin stops this sound instead
            }

            player.stopSound(disc.getSoundKey(), soundCategory);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Ambient zone failed to stop '" + disc.getSoundKey()
                    + "' for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private float volumeFor(AmbientZone zone) {
        float volume = zone.inheritsVolume() ? plugin.getConfigManager().getVolume() : zone.getVolume();
        return Math.max(0f, Math.min(4f, volume));
    }

    /**
     * Zone volume for one specific listener.
     *
     * <p>A zone with its own volume is absolute: it does not follow the server
     * volume, and changing `/cjb volume` must not move it. Only a zone left on
     * `inherit` follows the server.
     *
     * <p>A player's personal volume applies as a factor on top, where 1.0 means
     * "as configured". Dividing the zone volume by the server volume, as this
     * used to, made an explicitly set zone swing with a setting it was
     * deliberately opted out of - at the default server volume of 4.0 a zone at
     * 0.2 collapsed to a twentieth of the player's setting.
     */
    private float volumeFor(AmbientZone zone, UUID uuid) {
        float personal = plugin.getPlayerPreferencesManager().getPersonalVolume(uuid);
        if (personal < 0) {
            return plugin.getConfigManager().isMuted() ? 0f : volumeFor(zone);
        }
        if (plugin.getConfigManager().isMuted()) {
            return 0f;
        }
        if (zone.inheritsVolume()) {
            return Math.max(0f, Math.min(4f, personal));
        }
        return Math.max(0f, Math.min(4f, zone.getVolume() * personal));
    }

    // ==================== EVENTS / EXTERNAL HOOKS ====================

    /**
     * Stops zone sound for one player and detaches them, without disturbing the
     * zone for anyone else. The scanner re-attaches them on its next pass, so
     * this is only "silence me now" - callers that mean it turn music off first.
     *
     * @param player the player to silence
     */
    public void stopSoundFor(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        for (ZonePlayback zp : playbacks.values()) {
            if (!zp.point) {
                continue;
            }
            synchronized (zp) {
                zp.listeners.remove(uuid);
                if (zp.received.remove(uuid) && zp.current != null) {
                    stopSound(player, zp.current);
                }
            }
        }
        String zoneId = playerZone.remove(uuid);
        if (zoneId == null) {
            return;
        }
        ZonePlayback zp = playbacks.get(zoneId);
        if (zp == null) {
            return;
        }
        leaveZone(zp, player);
    }

    /**
     * Re-attaches a player to whatever zone they are standing in, right now.
     *
     * <p>Detaching and immediately re-evaluating makes the scanner's normal
     * "entered a zone" path run at once instead of at the next scan interval,
     * so turning music back on is not a one-second wait. With
     * {@code syncMode: immediate} the current track then starts for them
     * straight away; with {@code next_track} they join at the next boundary, as
     * configured.
     *
     * @param player the player to re-attach
     * @return true if the player ended up assigned to a zone
     */
    public boolean resumeSoundFor(Player player) {
        if (player == null || !player.isOnline() || !running) {
            return false;
        }
        stopSoundFor(player);
        if (SchedulerUtil.isFolia()) {
            // Reading the location and playing sound belongs on their region thread
            SchedulerUtil.runPlayerTask(plugin, player, () -> evaluatePlayer(player));
            // The dispatch is asynchronous, so report on the configuration instead
            return findZoneFor(player.getLocation()) != null;
        }
        evaluatePlayer(player);
        return playerZone.containsKey(player.getUniqueId());
    }

    /**
     * Removes a quitting player from every zone's listener set and assignment
     * map. No sound is stopped - the player is already gone.
     * @param player the player who left
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        playerZone.remove(uuid);
        for (ZonePlayback zp : playbacks.values()) {
            zp.listeners.remove(uuid);
            zp.received.remove(uuid);
            IndividualTrack it = zp.individual.remove(uuid);
            if (it != null) {
                cancelCursor(it);
            }
        }
    }

    // ==================== ZONE CRUD ====================

    public AmbientZone getZone(String id) {
        return zones.get(id);
    }

    public Collection<AmbientZone> getAllZones() {
        return zones.values();
    }

    /**
     * Whether a zone is currently playing (its timeline is active).
     * @param id zone id
     * @return true if the zone has a live playback
     */
    public boolean isZoneActive(String id) {
        ZonePlayback zp = playbacks.get(id);
        return zp != null && zp.active;
    }

    /**
     * Creates a new, empty zone with default settings and persists it. The zone
     * is not runnable until a playlist is assigned.
     * @param id zone id
     * @return the new zone, or null if one with that id already exists
     */
    public AmbientZone createZone(String id) {
        return createZone(id, null);
    }

    /**
     * Creates a new zone, lets the caller seed its defaults, and persists it once.
     *
     * <p>The initializer runs before the zone is written, so callers that want to
     * pre-fill values (e.g. the creating player's world and position) do not
     * cause a second file write and backup rotation right after creation.
     *
     * @param id zone id
     * @param initializer optional callback to seed the new zone, may be null
     * @return the new zone, or null if one with that id already exists
     */
    public AmbientZone createZone(String id, java.util.function.Consumer<AmbientZone> initializer) {
        if (id == null || id.isEmpty() || zones.containsKey(id)) {
            return null;
        }
        AmbientZone zone = new AmbientZone(id);
        if (initializer != null) {
            initializer.accept(zone);
        }
        zones.put(id, zone);
        persistZone(zone);
        applyZoneChange(zone);
        return zone;
    }

    /**
     * Deletes a zone: stops its playback, removes it from config, and persists.
     * @param id zone id
     * @return true if a zone was removed
     */
    public boolean deleteZone(String id) {
        if (!zones.containsKey(id)) {
            return false;
        }
        deactivateZone(id);
        zones.remove(id);
        synchronized (configLock) {
            if (zonesConfig.has("zones") && zonesConfig.get("zones").isJsonObject()) {
                zonesConfig.getAsJsonObject("zones").remove(id);
            }
        }
        saveZonesFile();
        return true;
    }

    /**
     * Persists an edited zone and restarts its live playback so changes take
     * effect immediately. Call after mutating an {@link AmbientZone}.
     * @param zone the zone to save and (re)activate
     */
    public void saveZone(AmbientZone zone) {
        saveZone(zone, true);
    }

    /**
     * @param applyLive false keeps the running timeline untouched, so a changed
     *                  volume is picked up by the next track instead of
     *                  restarting the current one. The volume is part of the
     *                  playback signature, so without this every save restarts
     *                  the zone and a "do not restart" option cannot work.
     */
    public void saveZone(AmbientZone zone, boolean applyLive) {
        zones.put(zone.getId(), zone);
        persistZone(zone);
        if (applyLive) {
            applyZoneChange(zone);
        }
    }

    /**
     * Brings a zone's live playback in line with its (already persisted) config.
     *
     * <p>Only settings that shape the running timeline force a restart. Editing
     * the area, priority, height or sync mode used to tear the timeline down and
     * start the playlist over from track 1 for everyone inside - one click on
     * "priority +1" in the editor restarted the music. Those settings are now
     * left alone: membership is re-evaluated by the scanner within one interval,
     * and the sync mode only matters for the next arrival.
     */
    private void applyZoneChange(AmbientZone zone) {
        if (!running || !plugin.getConfigManager().isAmbientZonesEnabled()) {
            return;
        }

        ZonePlayback live = playbacks.get(zone.getId());
        if (live != null
                && live.active
                && zone.isRunnable()
                && live.signature.equals(playbackSignature(zone))) {
            return; // Nothing playback-relevant changed - let the music keep running.
        }

        deactivateZone(zone.getId());
        startZonePlayback(zone);
        // The scanner re-adds listeners within one interval; nothing else to do.
    }

    /**
     * What the player is hearing from their current zone, if any.
     *
     * <p>For {@code individual} zones this is that player's own cursor. For
     * {@code synced} zones it is the zone's shared timeline - a player who
     * joined mid-track with {@code immediate} sync hears an offset copy, so the
     * shared position is the honest thing to show.
     *
     * @param player the player
     * @return the current track, or null if the player is not in an active zone
     */
    public NowPlaying getNowPlaying(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        String zoneId = getZoneIdFor(player);
        if (zoneId == null) {
            return null;
        }
        ZonePlayback zp = playbacks.get(zoneId);
        if (zp == null || !zp.active) {
            return null;
        }

        NowPlaying.Source source = zp.zone.getType() == AmbientZone.ZoneType.GLOBAL
            ? NowPlaying.Source.RADIO : NowPlaying.Source.ZONE;

        if (zp.isIndividual()) {
            IndividualTrack it = zp.individual.get(uuid);
            if (it == null || it.current == null || it.cancelled) {
                return null;
            }
            return new NowPlaying(it.current, elapsedTicks(it.trackStartMillis), source);
        }

        if (zp.finished || zp.current == null || !zp.listeners.contains(uuid)) {
            return null;
        }
        return new NowPlaying(zp.current, elapsedTicks(zp.trackStartMillis), source);
    }

    private long elapsedTicks(long startMillis) {
        return Math.max(0, (System.currentTimeMillis() - startMillis) / 50);
    }

    /**
     * Skips a zone's current track.
     *
     * <p>In {@code synced} mode the whole zone advances for everyone; in
     * {@code individual} mode only the requesting player's own cursor moves on.
     *
     * @param zoneId zone to advance
     * @param requester player whose cursor to skip in individual mode, may be null
     * @return the disc now playing, or null if nothing was skipped
     */
    public CustomDisc skipTrack(String zoneId, Player requester) {
        ZonePlayback zp = playbacks.get(zoneId);
        if (zp == null || !zp.active) {
            return null;
        }

        if (zp.isIndividual()) {
            if (requester == null) {
                return null;
            }
            IndividualTrack it = zp.individual.get(requester.getUniqueId());
            if (it == null) {
                return null;
            }
            synchronized (it) {
                // advanceIndividual invalidates the pending timer itself
                advanceIndividual(zp, requester.getUniqueId(), it);
                return it.cancelled ? null : it.current;
            }
        }

        synchronized (zp) {
            advanceTrack(zp);
            return zp.finished || !zp.active ? null : zp.current;
        }
    }

    /**
     * The zone a player is currently assigned to, or null.
     * @param player the player
     * @return zone id or null
     */
    public String getZoneIdFor(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        String zoneId = playerZone.get(uuid);
        if (zoneId != null) {
            return zoneId;
        }
        // Otherwise a point-source zone the player stands in; with several, the
        // highest priority (then the lowest id, for a stable choice)
        ZonePlayback best = null;
        for (ZonePlayback zp : playbacks.values()) {
            if (!zp.point || !zp.active || !zp.listeners.contains(uuid)) {
                continue;
            }
            if (best == null || zp.zone.getPriority() > best.zone.getPriority()
                    || (zp.zone.getPriority() == best.zone.getPriority()
                        && zp.zone.getId().compareTo(best.zone.getId()) < 0)) {
                best = zp;
            }
        }
        return best == null ? null : best.zone.getId();
    }

    /**
     * Rebuilds every live zone that plays the given playlist.
     *
     * <p>A zone snapshots its playable discs when its timeline starts, so adding
     * or removing a disc - or editing a disc's sound or duration - would not
     * reach a running zone until the next {@code /cjb zone reload}. DiscManager
     * calls this after any change that can affect a playlist's contents.
     *
     * @param playlistId playlist whose contents changed; ignored if null/empty
     */
    public void refreshZonesUsingPlaylist(String playlistId) {
        if (!running || playlistId == null || playlistId.isEmpty()) {
            return;
        }
        for (AmbientZone zone : zones.values()) {
            if (playlistId.equals(zone.getPlaylistId())) {
                deactivateZone(zone.getId());
                startZonePlayback(zone);
            }
        }
    }

    /**
     * Whether a jukebox disc is currently audible to this player.
     *
     * @param player the listener
     * @return true if zone audio should stay silent for them
     */
    private boolean isHearingDisc(Player player) {
        return plugin.getConfigManager().pauseZonesDuringDisc()
            && plugin.getPlaybackManager().getAudiblePlaybackFor(player) != null;
    }

    /**
     * Restarts every zone that follows the server volume.
     *
     * <p>`/cjb volume ... restart` only restarted jukebox playbacks, so a zone
     * on `inherit` kept playing at the old volume until its current track
     * ended - the restart flag appeared to do nothing where zones were
     * concerned.
     *
     * @return how many zones were restarted
     */
    public int restartInheritingZones() {
        return restartZones(true);
    }

    /**
     * Restarts every active zone.
     *
     * <p>Muting silences all zones, including those with their own volume, so
     * mute, unmute and a volume change that lifts a mute have to restart all of
     * them - restarting only the inheriting ones left the others playing on at
     * full volume (mute) or silent until their next track (unmute).
     *
     * @return how many zones were restarted
     */
    public int restartAllZones() {
        return restartZones(false);
    }

    private int restartZones(boolean inheritingOnly) {
        if (!running || !plugin.getConfigManager().isAmbientZonesEnabled()) {
            return 0;
        }
        int restarted = 0;
        for (AmbientZone zone : zones.values()) {
            if ((inheritingOnly && !zone.inheritsVolume()) || !zone.isEnabled() || !isZoneActive(zone.getId())) {
                continue;
            }
            deactivateZone(zone.getId());
            startZonePlayback(zone);
            restarted++;
        }
        return restarted;
    }

    /**
     * Rebuilds every live zone whose playlist contains the given disc.
     *
     * @param discId disc that was changed or removed
     */
    public void refreshZonesUsingDisc(String discId) {
        if (!running || discId == null || discId.isEmpty()) {
            return;
        }
        for (DiscPlaylist playlist : plugin.getDiscManager().getAllPlaylists()) {
            if (playlist.contains(discId)) {
                refreshZonesUsingPlaylist(playlist.getId());
            }
        }
    }

    /**
     * Explains why a zone is not currently playing.
     *
     * <p>Returns a language-file key describing the first blocking condition, or
     * {@code null} if the zone is fine. Commands and the editor GUI use this so
     * an admin is told in-game that e.g. the assigned playlist has no usable
     * discs, instead of the zone silently staying idle with only a console line.
     *
     * @param zone zone to inspect
     * @return message key, or null if the zone can play
     */
    public String getIdleReasonKey(AmbientZone zone) {
        if (zone == null) {
            return null;
        }
        if (!plugin.getConfigManager().isAmbientZonesEnabled()) {
            return "zone-idle-feature-disabled";
        }
        if (!zone.isEnabled()) {
            return "zone-idle-disabled";
        }
        if (!zone.isSoundSourceValid()) {
            return "zone-idle-point-needs-radius";
        }
        if (zone.isJukeboxBound() && !zone.isJukeboxPlaced()) {
            return "zone-idle-jukebox-not-placed";
        }
        if (zone.getPlaylistId() == null || zone.getPlaylistId().isEmpty()) {
            return "zone-idle-no-playlist";
        }
        switch (zone.getType()) {
            case GLOBAL:
                break; // Nothing spatial to configure
            case WORLDGUARD:
                if (zone.getRegion().isEmpty()) {
                    return "zone-idle-no-region";
                }
                if (!plugin.getIntegrationManager().isWorldGuardEnabled()) {
                    return "zone-idle-worldguard-missing";
                }
                break;
            case CUBOID:
                if (!zone.hasBothCorners()) {
                    return "zone-idle-no-corners";
                }
                break;
            case RADIUS:
            default:
                if (!(zone.getRadius() > 0)) { // also catches NaN
                    return "zone-idle-no-radius";
                }
                break;
        }
        if (plugin.getDiscManager().getPlaylist(zone.getPlaylistId()) == null) {
            return "zone-idle-playlist-missing";
        }
        if (collectPlayableDiscs(zone.getPlaylistId()).isEmpty()) {
            return "zone-idle-playlist-unplayable";
        }
        if (zone.getType() != AmbientZone.ZoneType.GLOBAL && Bukkit.getWorld(zone.getWorld()) == null) {
            return "zone-idle-unknown-world";
        }
        return null;
    }

    private void persistZone(AmbientZone zone) {
        JsonObject data = serializeZone(zone);
        synchronized (configLock) {
            if (!zonesConfig.has("zones") || !zonesConfig.get("zones").isJsonObject()) {
                zonesConfig.add("zones", new JsonObject());
            }
            zonesConfig.getAsJsonObject("zones").add(zone.getId(), data);
        }
        saveZonesFile();
    }

    /**
     * Tears down a zone's live playback (stops sound, cancels timer, detaches
     * listeners) without touching its configuration.
     */
    private void deactivateZone(String id) {
        ZonePlayback zp = playbacks.remove(id);
        if (zp == null) {
            return;
        }
        // Under the same monitor as advanceTrack, so a track callback already in
        // flight cannot start a track after we stopped the zone.
        synchronized (zp) {
            zp.active = false;
            SchedulerUtil.cancelTask(zp.trackTask);
            zp.trackTask = null;
            stopSoundForAll(zp);
            // Detach every member (synced listeners + individual cursors) so the
            // scanner re-detects them if the zone is restarted.
            Set<UUID> members = new HashSet<>(zp.listeners);
            members.addAll(zp.individual.keySet());
            stopAllIndividual(zp);
            for (UUID member : members) {
                playerZone.remove(member);
            }
            zp.listeners.clear();
        }
    }

    private int getScanIntervalTicks() {
        int interval = DEFAULT_SCAN_INTERVAL;
        if (zonesConfig.has("settings") && zonesConfig.get("settings").isJsonObject()) {
            JsonObject settings = zonesConfig.getAsJsonObject("settings");
            interval = (int) getDbl(settings, "scan-interval-ticks", DEFAULT_SCAN_INTERVAL);
        }
        return Math.max(MIN_SCAN_INTERVAL, Math.min(MAX_SCAN_INTERVAL, interval));
    }

    // ==================== JSON HELPERS ====================

    private String getStr(JsonObject obj, String key, String def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private boolean getBool(JsonObject obj, String key, boolean def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private double getDbl(JsonObject obj, String key, double def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
