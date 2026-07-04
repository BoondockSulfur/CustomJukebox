package de.boondocksulfur.customjukebox.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import org.bukkit.ChatColor;

import java.io.*;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Manages plugin configuration from config.json (JEXT-compatible JSON format).
 * Replaces YAML-based configuration with JSON for better compatibility.
 */
public class ConfigManager {

    private static final int CONFIG_VERSION = 1; // Current config version for migration support
    private static final int MAX_BACKUPS = 5; // Maximum number of backup files to keep
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB max file size for config

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File configFile;
    private JsonObject config;

    // Mute state tracking (persisted in config.json)
    private boolean isMuted = false;
    private float volumeBeforeMute = 1.0f;

    private void loadMuteState() {
        isMuted = getBoolean("playback.muted", false);
        volumeBeforeMute = (float) getDouble("playback.volume-before-mute", 4.0);
    }

    private void saveMuteState() {
        try {
            if (!config.has("playback")) {
                config.add("playback", new JsonObject());
            }
            JsonObject playback = config.getAsJsonObject("playback");
            playback.addProperty("muted", isMuted);
            playback.addProperty("volume-before-mute", volumeBeforeMute);
            save();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save mute state: " + e.getMessage());
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

            // Read config.json
            try (Reader reader = new FileReader(configFile)) {
                this.config = gson.fromJson(reader, JsonObject.class);
            }

            // Check and log config version
            int fileVersion = getInt("version", 0);
            if (fileVersion == 0) {
                plugin.getLogger().warning("Config file has no version field - adding version " + CONFIG_VERSION);
                config.addProperty("version", CONFIG_VERSION);
                save();
            } else if (fileVersion < CONFIG_VERSION) {
                plugin.getLogger().info("Config version " + fileVersion + " detected - current version is " + CONFIG_VERSION);
                // Future: Add migration logic here
                config.addProperty("version", CONFIG_VERSION);
                save();
            } else if (fileVersion > CONFIG_VERSION) {
                plugin.getLogger().warning("Config version " + fileVersion + " is newer than supported version " + CONFIG_VERSION + "!");
            } else {
                plugin.getLogger().info("Loaded configuration from config.json (version " + fileVersion + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load config.json", e);

            // Create default config
            this.config = new JsonObject();
        }
    }

    /**
     * Reloads config.json from disk.
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Saves current configuration to config.json.
     */
    public void save() {
        try {
            // Create backup before saving
            createBackup(configFile);

            // Ensure version is always set
            config.addProperty("version", CONFIG_VERSION);

            try (Writer writer = new FileWriter(configFile)) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config.json", e);
        }
    }

    /**
     * Creates a backup of the given file with timestamp.
     * Automatically manages backup count (keeps only the last MAX_BACKUPS files).
     * @param file File to backup
     */
    private void createBackup(File file) {
        if (!file.exists()) {
            return; // No file to backup
        }

        try {
            // Create backup filename with timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = dateFormat.format(new Date());
            String backupName = file.getName().replace(".json", "_backup_" + timestamp + ".json");
            File backupFile = new File(file.getParentFile(), backupName);

            // Copy file to backup
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Debug logging removed to prevent NullPointerException during initialization
            // plugin.getLogger().info("Created backup: " + backupName);

            // Clean up old backups
            cleanupOldBackups(file);

        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create backup for " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Removes old backup files, keeping only the most recent MAX_BACKUPS files.
     * @param originalFile The original file (used to find related backups)
     */
    private void cleanupOldBackups(File originalFile) {
        File parentDir = originalFile.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            return;
        }

        // Find all backup files for this config
        String baseName = originalFile.getName().replace(".json", "");
        File[] backupFiles = parentDir.listFiles((dir, name) ->
            name.startsWith(baseName + "_backup_") && name.endsWith(".json")
        );

        if (backupFiles == null || backupFiles.length <= MAX_BACKUPS) {
            return; // No cleanup needed
        }

        // Sort by modification time (oldest first)
        Arrays.sort(backupFiles, (f1, f2) ->
            Long.compare(f1.lastModified(), f2.lastModified())
        );

        // Delete oldest backups
        int toDelete = backupFiles.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (backupFiles[i].delete()) {
                // Debug logging removed to prevent NullPointerException during initialization
                // plugin.getLogger().info("Deleted old backup: " + backupFiles[i].getName());
            }
        }
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

    public void setVolume(float volume) {
        try {
            if (!config.has("playback")) {
                config.add("playback", new JsonObject());
            }
            config.getAsJsonObject("playback").addProperty("volume", volume);
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
     * Mutes playback by setting volume to 0 and saving the previous volume.
     * @return true if mute was successful, false if already muted
     */
    public boolean mute() {
        if (isMuted) {
            return false; // Already muted
        }

        volumeBeforeMute = getVolume();
        isMuted = true;
        setVolume(0.0f);
        saveMuteState();
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
        setVolume(volumeBeforeMute);
        saveMuteState();
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

    private String colorize(String message) {
        return AdventureUtil.toLegacy(AdventureUtil.parseComponent(message));
    }
}
