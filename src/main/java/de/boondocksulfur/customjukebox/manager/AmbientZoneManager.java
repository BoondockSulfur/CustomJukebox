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
        volatile SchedulerUtil.TaskHandle trackTask;
        // A non-looping playlist that played through: the zone stays active but
        // idle (silent) until a new player entering restarts it from track 0.
        volatile boolean finished;

        // ----- INDIVIDUAL mode: each player runs their own cursor -----
        final Map<UUID, IndividualTrack> individual = new ConcurrentHashMap<>();

        ZonePlayback(AmbientZone zone, List<CustomDisc> discs) {
            this.zone = zone;
            this.discs = discs;
            this.signature = playbackSignature(zone);
            this.index = 0;
            this.current = discs.isEmpty() ? null : discs.get(0);
            this.trackStartMillis = System.currentTimeMillis();
            this.active = false;
            this.finished = false;
        }

        boolean isIndividual() {
            return zone.getPlaybackMode() == AmbientZone.PlaybackMode.INDIVIDUAL;
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
            + "|" + zone.getVolume();
    }

    /** A single player's playlist cursor inside an INDIVIDUAL-mode zone. */
    private static final class IndividualTrack {
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

    private void loadZonesFile() {
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

            try (Reader reader = new InputStreamReader(new FileInputStream(zonesFile), StandardCharsets.UTF_8)) {
                this.zonesConfig = gson.fromJson(reader, JsonObject.class);
            }
            if (zonesConfig == null) {
                zonesConfig = new JsonObject();
            }
            boolean addedKeys;
            boolean versionChanged;
            synchronized (configLock) {
                if (zonesConfig.has("zones") && !zonesConfig.get("zones").isJsonObject()) {
                    zonesConfig.add("zones", new JsonObject());
                }

                // Merge structural defaults (settings), but never seed the user-owned
                // "zones" map with examples.
                addedKeys = mergeDefaults();

                int fileVersion = zonesConfig.has("version") ? zonesConfig.get("version").getAsInt() : 0;
                versionChanged = fileVersion != ZONES_CONFIG_VERSION && fileVersion <= ZONES_CONFIG_VERSION;
                if (versionChanged) {
                    zonesConfig.addProperty("version", ZONES_CONFIG_VERSION);
                }
            }
            if (addedKeys || versionChanged) {
                saveZonesFile();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load zones.json", e);
            this.zonesConfig = new JsonObject();
            this.zonesConfig.add("zones", new JsonObject());
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
        zones.clear();
        if (!zonesConfig.has("zones") || !zonesConfig.get("zones").isJsonObject()) {
            return;
        }
        JsonObject zonesSection = zonesConfig.getAsJsonObject("zones");
        for (String id : zonesSection.keySet()) {
            try {
                AmbientZone zone = parseZone(id, zonesSection.getAsJsonObject(id));
                zones.put(id, zone);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse ambient zone '" + id + "': " + e.getMessage());
            }
        }
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
        zone.setRadius(getDbl(data, "radius", 32));
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
        zone.setVolume((float) getDbl(data, "volume", AmbientZone.VOLUME_INHERIT));

        String sync = getStr(data, "syncMode", "immediate");
        zone.setSyncMode("next_track".equalsIgnoreCase(sync)
            ? AmbientZone.SyncMode.NEXT_TRACK : AmbientZone.SyncMode.IMMEDIATE);

        String playback = getStr(data, "playback", "synced");
        zone.setPlaybackMode("individual".equalsIgnoreCase(playback)
            ? AmbientZone.PlaybackMode.INDIVIDUAL : AmbientZone.PlaybackMode.SYNCED);

        zone.setFullHeight(getBool(data, "fullHeight", true));
        zone.setShuffle(getBool(data, "shuffle", false));
        zone.setPriority((int) getDbl(data, "priority", 0));
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
        }
        plugin.getConfigWriter().save(zonesFile, snapshot,
            plugin.getConfigManager().getMaxBackups(),
            plugin.getConfigManager().getBackupMinIntervalMillis());
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
        loadZonesFile();
        loadZones();
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
            scheduleTrackEnd(zp);
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

    private void scheduleTrackEnd(ZonePlayback zp) {
        CustomDisc disc = zp.current;
        if (disc == null) {
            return;
        }
        int duration = disc.getDurationTicks();
        zp.trackTask = SchedulerUtil.runGlobalLater(plugin, () -> onTrackEnd(zp), duration);
    }

    /**
     * Advances a zone's shared timeline by one track.
     *
     * <p>Runs under the playback's monitor, which {@link #deactivateZone} and
     * {@link #stop} also take: without it, a teardown could land between the
     * {@code active} check and the playback below, leaving a track playing that
     * nothing would ever stop again.
     */
    private void onTrackEnd(ZonePlayback zp) {
        synchronized (zp) {
            if (!zp.active) {
                return;
            }

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
                        dispatchStop(zp.listeners, previous);
                    }
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
                dispatchStop(zp.listeners, previous);
            }
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
    }

    private void evaluatePlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();

        String newZoneId = findZoneFor(loc);
        String oldZoneId = playerZone.get(uuid);

        if (Objects.equals(newZoneId, oldZoneId)) {
            return; // No boundary crossed.
        }

        // Leaving the previous zone.
        if (oldZoneId != null) {
            ZonePlayback oldZp = playbacks.get(oldZoneId);
            if (oldZp != null) {
                if (oldZp.isIndividual()) {
                    stopIndividual(oldZp, player);
                } else {
                    oldZp.listeners.remove(uuid);
                    if (oldZp.current != null) {
                        stopSound(player, oldZp.current);
                    }
                }
            }
        }

        // Entering a new zone.
        if (newZoneId != null) {
            ZonePlayback newZp = playbacks.get(newZoneId);
            if (newZp != null && newZp.active) {
                if (newZp.isIndividual()) {
                    // Each player runs the playlist on their own, always hearing
                    // complete tracks.
                    startIndividual(newZp, player);
                } else {
                    newZp.listeners.add(uuid);
                    if (newZp.finished) {
                        // A non-loop zone that had played through: entering revives
                        // it from track 0 for everyone currently inside.
                        restartTimeline(newZp);
                    } else if (newZp.zone.getSyncMode() == AmbientZone.SyncMode.IMMEDIATE && newZp.current != null) {
                        playSound(player, newZp.current, volumeFor(newZp.zone, uuid));
                    }
                    // NEXT_TRACK: the player is now a listener and will be included
                    // when onTrackEnd next fires - no sound yet.
                }
            }
            playerZone.put(uuid, newZoneId);
        } else {
            playerZone.remove(uuid);
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
        IndividualTrack it = new IndividualTrack();
        it.index = 0;
        it.current = zp.discs.get(0);
        it.trackStartMillis = System.currentTimeMillis();
        // Replace any prior cursor (defensive - a stale one shouldn't exist).
        IndividualTrack previous = zp.individual.put(uuid, it);
        if (previous != null) {
            cancelCursor(previous);
        }
        playSound(player, it.current, volumeFor(zp.zone, uuid));
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
        CustomDisc disc = it.current;
        if (disc == null) {
            return;
        }
        it.task = SchedulerUtil.runGlobalLater(plugin, () -> onIndividualTrackEnd(zp, uuid, it), disc.getDurationTicks());
    }

    private void onIndividualTrackEnd(ZonePlayback zp, UUID uuid, IndividualTrack it) {
        // Runs under the cursor's monitor so a concurrent teardown cannot slip
        // between the guard below and the playback that follows it.
        synchronized (it) {
            // Identity guard: the player may have left (and possibly re-entered
            // with a fresh cursor), or the zone may have been torn down.
            if (it.cancelled || !zp.active || zp.individual.get(uuid) != it) {
                return;
            }

            CustomDisc previous = it.current;

            int next = it.index + 1;
            if (next >= zp.discs.size()) {
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

            it.index = next;
            it.current = zp.discs.get(next);
            it.trackStartMillis = System.currentTimeMillis();

            // Stop the finished track (in case its .ogg outlasts the duration) and
            // play the next one to this single player.
            dispatchStopOne(uuid, previous);
            dispatchPlayOne(uuid, it.current, volumeFor(zp.zone, uuid));
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

    private void dispatchPlayOne(UUID uuid, CustomDisc disc, float volume) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (SchedulerUtil.isFolia()) {
            SchedulerUtil.runPlayerTask(plugin, player, () -> playSound(player, disc, volume));
        } else {
            playSound(player, disc, volume);
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
            if (!zp.active) {
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
            if (folia) {
                SchedulerUtil.runPlayerTask(plugin, player, () -> playSound(player, disc, volume));
            } else {
                playSound(player, disc, volume);
            }
        }
    }

    private void stopSoundForAll(ZonePlayback zp) {
        dispatchStop(zp.listeners, zp.current);
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
     * Plays a disc's custom sound to a player, anchored at their current
     * position. Must run on the player's region thread on Folia.
     */
    private void playSound(Player player, CustomDisc disc, float volume) {
        if (!disc.hasCustomSound()) {
            return;
        }
        // Players who turned plugin music off hear nothing from zones either
        if (!plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId())) {
            return;
        }
        try {
            CustomSoundPlayEvent deliveryEvent = new CustomSoundPlayEvent(
                player, disc, player.getLocation(), CustomSoundPlayEvent.Source.AMBIENT_ZONE, volume);
            plugin.getServer().getPluginManager().callEvent(deliveryEvent);
            if (deliveryEvent.isCancelled()) {
                return; // A companion plugin delivers this sound instead
            }

            player.playSound(player.getLocation(), disc.getSoundKey(), soundCategory, volume, DEFAULT_PITCH);
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
        String zoneId = playerZone.remove(uuid);
        if (zoneId == null) {
            return;
        }
        ZonePlayback zp = playbacks.get(zoneId);
        if (zp == null) {
            return;
        }
        if (zp.isIndividual()) {
            stopIndividual(zp, player);
            return;
        }
        zp.listeners.remove(uuid);
        if (zp.current != null) {
            stopSound(player, zp.current);
        }
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
        String zoneId = playerZone.get(uuid);
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
                SchedulerUtil.cancelTask(it.task);
                it.task = null;
            }
            onIndividualTrackEnd(zp, requester.getUniqueId(), it);
            return it.cancelled ? null : it.current;
        }

        synchronized (zp) {
            SchedulerUtil.cancelTask(zp.trackTask);
            zp.trackTask = null;
        }
        onTrackEnd(zp);
        return zp.finished ? null : zp.current;
    }

    /**
     * The zone a player is currently assigned to, or null.
     * @param player the player
     * @return zone id or null
     */
    public String getZoneIdFor(Player player) {
        return player == null ? null : playerZone.get(player.getUniqueId());
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
        if (!running || !plugin.getConfigManager().isAmbientZonesEnabled()) {
            return 0;
        }
        int restarted = 0;
        for (AmbientZone zone : zones.values()) {
            if (!zone.inheritsVolume() || !zone.isEnabled() || !isZoneActive(zone.getId())) {
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
                if (zone.getRadius() <= 0) {
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
        // Under the same monitor as onTrackEnd, so a track callback already in
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
