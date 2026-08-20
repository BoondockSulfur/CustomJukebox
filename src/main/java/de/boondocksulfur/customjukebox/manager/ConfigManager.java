package de.boondocksulfur.customjukebox.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.BackupUtil;
import de.boondocksulfur.customjukebox.utils.JsonConfigUtil;
import org.bukkit.SoundCategory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Manages plugin configuration from config.json (JEXT-compatible JSON format).
 * Replaces YAML-based configuration with JSON for better compatibility.
 */
public class ConfigManager {

    private static final int CONFIG_VERSION = 1; // Current config version for migration support
    private static final int DEFAULT_MAX_BACKUPS = 5; // Default backups kept per file
    private static final int MAX_BACKUPS_LIMIT = 100; // Hard cap to avoid runaway backup counts
    private static final int DEFAULT_BACKUP_MIN_INTERVAL_MINUTES = 5; // Throttle between backups
    private static final int MAX_BACKUP_MIN_INTERVAL_MINUTES = 1440; // 24 h hard cap
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB max file size for config

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File configFile;
    /**
     * Guards edits to the {@link #config} tree and the snapshot taken for saving.
     * Gson's JsonObject is not thread-safe and on Folia two region threads can
     * run config-changing commands at the same time. Blocks are kept short and
     * never call out, so no lock ordering can arise.
     */
    private final Object configLock = new Object();
    // volatile: read from region threads on Folia while /cjb reload may replace them
    private volatile JsonObject config;

    // Mute state tracking (persisted in config.json)
    private volatile boolean isMuted = false;
    private volatile float volumeBeforeMute = 1.0f;

    private void loadMuteState() {
        isMuted = getBoolean("playback.muted", false);
        volumeBeforeMute = (float) getDouble("playback.volume-before-mute", 4.0);
    }

    /**
     * Writes the current volume and mute state into the in-memory config.
     * Callers persist afterwards with a single {@link #save()} - volume and mute
     * state always change together, and two saves would mean two file writes
     * (and two backup rotations) for one logical operation.
     */
    private void writePlaybackState(float volume) {
        synchronized (configLock) {
            if (!config.has("playback") || !config.get("playback").isJsonObject()) {
                config.add("playback", new JsonObject());
            }
            JsonObject playback = config.getAsJsonObject("playback");
            playback.addProperty("volume", volume);
            playback.addProperty("muted", isMuted);
            playback.addProperty("volume-before-mute", volumeBeforeMute);
        }
    }

    public ConfigManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Prevent & from becoming &amp; in JSON
            .create();
        this.configFile = new File(plugin.getDataFolder(), "config.json");

        loadConfig();
        loadMuteState();
    }

    /**
     * Loads config.json from plugin folder.
     * If file doesn't exist, copies default from resources.
     */
    private void loadConfig() {
        try {
            // A queued save must land before we read the file back
            if (plugin.getConfigWriter() != null) {
                plugin.getConfigWriter().flush();
            }
            // Create plugin folder if it doesn't exist
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // If config.json doesn't exist, copy from resources
            if (!configFile.exists()) {
                plugin.saveResource("config.json", false);
                plugin.getLogger().info("Created default config.json");
            }

            // Check file size before loading
            long fileSize = configFile.length();
            if (fileSize > MAX_FILE_SIZE) {
                plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
                plugin.getLogger().severe("CONFIG.JSON FILE TOO LARGE!");
                plugin.getLogger().severe("File size: " + (fileSize / 1024 / 1024) + " MB");
                plugin.getLogger().severe("Maximum allowed: " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
                plugin.getLogger().severe("Please check your configuration file for corruption.");
                plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
                throw new IOException("config.json exceeds maximum file size of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
            }

            // Read config.json (explicit UTF-8 - save() writes UTF-8, so reading
            // with the platform default would break on a non-UTF-8 JVM default)
            try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                this.config = gson.fromJson(reader, JsonObject.class);
            }
            if (config == null) {
                config = new JsonObject();
            }

            // Merge in any keys added by newer plugin versions (e.g. new
            // playback options) without overwriting the user's existing values
            boolean addedKeys;
            synchronized (configLock) {
                addedKeys = mergeDefaults();
            }

            // Check and log config version
            int fileVersion = getInt("version", 0);
            boolean versionChanged = fileVersion != CONFIG_VERSION;
            if (fileVersion == 0) {
                plugin.getLogger().warning("Config file has no version field - adding version " + CONFIG_VERSION);
            } else if (fileVersion < CONFIG_VERSION) {
                plugin.getLogger().info("Config version " + fileVersion + " detected - current version is " + CONFIG_VERSION);
            } else if (fileVersion > CONFIG_VERSION) {
                plugin.getLogger().warning("Config version " + fileVersion + " is newer than supported version " + CONFIG_VERSION + "!");
                versionChanged = false; // don't downgrade a newer file
            } else {
                plugin.getLogger().info("Loaded configuration from config.json (version " + fileVersion + ")");
            }

            if (versionChanged) {
                synchronized (configLock) {
                    config.addProperty("version", CONFIG_VERSION);
                }
            }
            if (addedKeys) {
                plugin.getLogger().info("Added missing config keys from defaults");
            }
            // Persist once if anything changed (new keys and/or version bump)
            if (addedKeys || versionChanged) {
                save();
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load config.json", e);

            // Create default config
            this.config = new JsonObject();
        }
    }

    /**
     * Merges keys from the bundled default config.json into the loaded config,
     * adding any that a newer plugin version introduced. Existing user values
     * are never overwritten.
     * @return true if at least one key was added
     */
    private boolean mergeDefaults() {
        try (InputStream defaultStream = plugin.getResource("config.json")) {
            if (defaultStream == null) {
                return false; // No bundled default (should not happen)
            }
            JsonObject defaults = gson.fromJson(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8), JsonObject.class);
            if (defaults == null) {
                return false;
            }
            // config.json has no user-owned content maps - merge everything
            return JsonConfigUtil.mergeDefaults(config, defaults, java.util.Collections.emptySet());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to merge default config keys: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reloads config.json from disk.
     */
    public void reload() {
        loadConfig();
        loadMuteState();
    }

    /**
     * Saves current configuration to config.json.
     * Writes to a temp file first and moves it atomically, so a crash or full
     * disk during the write can never leave a truncated config.json behind.
     */
    public void save() {
        JsonObject snapshot;
        synchronized (configLock) {
            // Ensure version is always set
            config.addProperty("version", CONFIG_VERSION);
            // Copy on the calling thread so later edits cannot change what the
            // writer thread ends up persisting
            snapshot = config.deepCopy();
        }
        plugin.getConfigWriter().save(configFile, snapshot, getMaxBackups(), getBackupMinIntervalMillis());
    }

    /**
     * Number of timestamped backups to keep per config file.
     * Configurable via {@code settings.max-backups}; {@code 0} disables backups
     * (and prunes existing ones). Clamped to [0, {@value #MAX_BACKUPS_LIMIT}].
     * @return effective backup count
     */
    public int getMaxBackups() {
        int max = getInt("settings.max-backups", DEFAULT_MAX_BACKUPS);
        return Math.max(0, Math.min(MAX_BACKUPS_LIMIT, max));
    }

    /**
     * Minimum age of the newest existing backup before another one is taken,
     * in milliseconds. Configurable via {@code settings.backup-min-interval-minutes};
     * {@code 0} backs up on every single save (the pre-3.3.0 behaviour).
     *
     * <p>Without a throttle a short GUI editing session rotated the entire
     * retained history away within a few clicks, because every button press
     * saves the file it belongs to.
     *
     * @return throttle interval in milliseconds
     */
    public long getBackupMinIntervalMillis() {
        int minutes = getInt("settings.backup-min-interval-minutes", DEFAULT_BACKUP_MIN_INTERVAL_MINUTES);
        minutes = Math.max(0, Math.min(MAX_BACKUP_MIN_INTERVAL_MINUTES, minutes));
        return minutes * 60_000L;
    }

    // ═══════════════════════════════════════════════════════════
    // General Settings
    // ═══════════════════════════════════════════════════════════

    public boolean isEnabled() {
        return getBoolean("settings.enabled", true);
    }

    public String getLanguage() {
        return getString("settings.language", "en");
    }

    public boolean isGuiEnabled() {
        return getBoolean("settings.enable-gui", true);
    }

    public boolean isDebug() {
        return getBoolean("settings.debug", false);
    }

    // ═══════════════════════════════════════════════════════════
    // Disc Settings
    // ═══════════════════════════════════════════════════════════

    public boolean isCreeperDropsEnabled() {
        return getBoolean("discs.creeper-drops", true);
    }

    public double getCreeperDropChance() {
        double chance = getDouble("discs.creeper-drop-chance", 0.05);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    public boolean isDungeonLootEnabled() {
        return getBoolean("discs.dungeon-loot", true);
    }

    public boolean isTrailRuinsLootEnabled() {
        return getBoolean("discs.trail-ruins-loot", true);
    }

    public int getMaxLootDiscs() {
        int max = getInt("discs.max-loot-discs", 2);
        return Math.max(0, Math.min(64, max));
    }

    public double getLootChance() {
        double chance = getDouble("discs.loot-chance", 0.15);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    public boolean isCraftingEnabled() {
        return getBoolean("discs.enable-crafting", true);
    }

    public int getFragmentsPerDisc() {
        int fragments = getInt("discs.fragments-per-disc", 9);
        return Math.max(1, Math.min(64, fragments));
    }

    // ═══════════════════════════════════════════════════════════
    // Playback Settings
    // ═══════════════════════════════════════════════════════════

    public float getVolume() {
        float volume = (float) getDouble("playback.volume", 4.0);
        return Math.max(0.0f, Math.min(4.0f, volume));
    }

    /**
     * Sets the global playback volume.
     *
     * <p>An explicit volume change also lifts an active mute: otherwise the
     * plugin would still consider itself muted, and a later {@code /cjb unmute}
     * would silently overwrite the value that was just set with the volume from
     * before the mute.
     *
     * @param volume Volume (0.0 to 4.0)
     */
    public void setVolume(float volume) {
        try {
            if (isMuted) {
                isMuted = false;
                volumeBeforeMute = volume;
            }
            writePlaybackState(volume);
            save();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set volume: " + e.getMessage());
        }
    }

    public boolean isDefaultLoopEnabled() {
        return getBoolean("playback.default-loop", false);
    }

    public int getJukeboxHearingRadius() {
        int radius = getInt("playback.jukebox-hearing-radius", 64);
        return Math.max(1, Math.min(512, radius));
    }

    /**
     * Whether the "Now Playing" title (center of the screen) is shown to nearby
     * players when a custom disc starts.
     */
    public boolean isShowTitleEnabled() {
        return getBoolean("playback.show-title", true);
    }

    /**
     * Whether the "Now Playing" actionbar message is shown to nearby players
     * when a custom disc starts.
     */
    public boolean isShowActionbarEnabled() {
        return getBoolean("playback.show-actionbar", true);
    }

    /**
     * Whether the boss bar showing the current track and its progress is used.
     * @return true if the progress bar is enabled
     */
    public boolean isProgressBarEnabled() {
        return getBoolean("playback.show-progress-bar", true);
    }

    /**
     * How often the progress bar refreshes, in ticks. Clamped to [5, 100].
     * @return refresh interval in ticks
     */
    public int getProgressUpdateTicks() {
        int ticks = getInt("playback.progress-update-ticks", 20);
        return Math.max(5, Math.min(100, ticks));
    }

    /**
     * Mutes playback by setting volume to 0 and saving the previous volume.
     * @return true if mute was successful, false if already muted
     */
    public boolean mute() {
        if (isMuted) {
            return false; // Already muted
        }

        volumeBeforeMute = getVolume();
        isMuted = true;
        writePlaybackState(0.0f);
        save();
        return true;
    }

    /**
     * Unmutes playback by restoring the previous volume.
     * @return true if unmute was successful, false if not muted
     */
    public boolean unmute() {
        if (!isMuted) {
            return false; // Not muted
        }

        isMuted = false;
        writePlaybackState(volumeBeforeMute);
        save();
        return true;
    }

    /**
     * Checks if playback is currently muted.
     * @return true if muted
     */
    public boolean isMuted() {
        return isMuted;
    }

    /**
     * Gets the volume that was active before muting.
     * @return Volume before mute
     */
    public float getVolumeBeforeMute() {
        return volumeBeforeMute;
    }

    // ═══════════════════════════════════════════════════════════
    // Parrot Settings
    // ═══════════════════════════════════════════════════════════

    public boolean isParrotDancingEnabled() {
        return getBoolean("parrots.enable-dancing", true);
    }

    public int getDanceRadius() {
        int radius = getInt("parrots.dance-radius", 3);
        return Math.max(1, Math.min(32, radius));
    }

    // ═══════════════════════════════════════════════════════════
    // Ambient Zone Settings
    // ═══════════════════════════════════════════════════════════

    /**
     * Master switch for the ambient-zone feature. When false, no zone scanner
     * runs and zones never auto-start, regardless of zones.json.
     */
    public boolean isAmbientZonesEnabled() {
        return getBoolean("ambient-zones.enabled", true);
    }

    /**
     * Sound category used for ambient-zone playback.
     *
     * <p>Defaults to {@code RECORDS}, the same category jukebox playback uses, so
     * zone music follows the player's "Jukebox/Note Blocks" slider. The downside
     * is that stop-sound packets are addressed by sound key <em>and</em> category:
     * if the same disc plays in a zone and in a nearby jukebox, either system
     * stopping its track also silences the other one for that player. Servers
     * that hit this can move zones to their own category (e.g. {@code MUSIC} or
     * {@code AMBIENT}) so the two never interfere.
     *
     * @return configured sound category, or RECORDS if the value is unknown
     */
    public SoundCategory getAmbientZoneSoundCategory() {
        String raw = getString("ambient-zones.sound-category", "RECORDS");
        try {
            return SoundCategory.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown ambient-zones.sound-category '" + raw
                + "' - falling back to RECORDS. Valid values: "
                + Arrays.toString(SoundCategory.values()));
            return SoundCategory.RECORDS;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Integration Settings
    // ═══════════════════════════════════════════════════════════

    public boolean isWorldGuardEnabled() {
        return getBoolean("integrations.worldguard", true);
    }

    public boolean isGriefPreventionEnabled() {
        return getBoolean("integrations.griefprevention", true);
    }

    // ═══════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════

    private String getString(String path, String defaultValue) {
        try {
            String[] keys = path.split("\\.");
            JsonObject current = config;

            for (int i = 0; i < keys.length - 1; i++) {
                if (current.has(keys[i]) && current.get(keys[i]).isJsonObject()) {
                    current = current.getAsJsonObject(keys[i]);
                } else {
                    return defaultValue;
                }
            }

            String lastKey = keys[keys.length - 1];
            if (current.has(lastKey)) {
                return current.get(lastKey).getAsString();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get string '" + path + "': " + e.getMessage());
        }
        return defaultValue;
    }

    private boolean getBoolean(String path, boolean defaultValue) {
        try {
            String[] keys = path.split("\\.");
            JsonObject current = config;

            for (int i = 0; i < keys.length - 1; i++) {
                if (current.has(keys[i]) && current.get(keys[i]).isJsonObject()) {
                    current = current.getAsJsonObject(keys[i]);
                } else {
                    return defaultValue;
                }
            }

            String lastKey = keys[keys.length - 1];
            if (current.has(lastKey)) {
                return current.get(lastKey).getAsBoolean();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get boolean '" + path + "': " + e.getMessage());
        }
        return defaultValue;
    }

    private int getInt(String path, int defaultValue) {
        try {
            String[] keys = path.split("\\.");
            JsonObject current = config;

            for (int i = 0; i < keys.length - 1; i++) {
                if (current.has(keys[i]) && current.get(keys[i]).isJsonObject()) {
                    current = current.getAsJsonObject(keys[i]);
                } else {
                    return defaultValue;
                }
            }

            String lastKey = keys[keys.length - 1];
            if (current.has(lastKey)) {
                return current.get(lastKey).getAsInt();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get int '" + path + "': " + e.getMessage());
        }
        return defaultValue;
    }

    private double getDouble(String path, double defaultValue) {
        try {
            String[] keys = path.split("\\.");
            JsonObject current = config;

            for (int i = 0; i < keys.length - 1; i++) {
                if (current.has(keys[i]) && current.get(keys[i]).isJsonObject()) {
                    current = current.getAsJsonObject(keys[i]);
                } else {
                    return defaultValue;
                }
            }

            String lastKey = keys[keys.length - 1];
            if (current.has(lastKey)) {
                return current.get(lastKey).getAsDouble();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get double '" + path + "': " + e.getMessage());
        }
        return defaultValue;
    }
}
