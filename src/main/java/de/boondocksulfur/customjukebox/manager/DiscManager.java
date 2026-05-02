package de.boondocksulfur.customjukebox.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.api.events.DiscRegisteredEvent;
import de.boondocksulfur.customjukebox.api.events.DiscRemovedEvent;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages custom discs with manual JSON configuration.
 * Simple and straightforward - no auto-discovery, no magic.
 * Just load disc.json and create discs from it.
 */
public class DiscManager {

    private static final int DISC_CONFIG_VERSION = 1; // Current disc.json version for migration support
    private static final int MAX_BACKUPS = 5; // Maximum number of backup files to keep
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB max file size
    private static final int MAX_CUSTOM_MODEL_DATA = 1000000; // Maximum allowed CustomModelData to prevent overflow

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File discsFile;
    private final Map<String, CustomDisc> discs;
    private final Map<String, DiscFragment> fragments;
    private final Map<String, DiscCategory> categories;
    private final Map<String, DiscPlaylist> playlists;
    private JsonObject discsConfig;

    public DiscManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Prevent & from becoming &amp; in JSON
            .create();
        this.discsFile = new File(plugin.getDataFolder(), "disc.json");
        this.discs = new HashMap<>();
        this.fragments = new HashMap<>();
        this.categories = new HashMap<>();
        this.playlists = new HashMap<>();

        loadDiscsFile();
        loadCategories();
        loadPlaylists();
        loadDiscs();
    }

    /**
     * Loads disc.json from plugin folder.
     * If file doesn't exist, copies default from resources.
     */
    private void loadDiscsFile() {
        try {
            // Create plugin folder if it doesn't exist
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // If disc.json doesn't exist, copy from resources
            if (!discsFile.exists()) {
                plugin.saveResource("disc.json", false);
                plugin.getLogger().info("Created default disc.json");
            }

            // Check file size before loading
            long fileSize = discsFile.length();
            if (fileSize > MAX_FILE_SIZE) {
                plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
                plugin.getLogger().severe("DISC.JSON FILE TOO LARGE!");
                plugin.getLogger().severe("File size: " + (fileSize / 1024 / 1024) + " MB");
                plugin.getLogger().severe("Maximum allowed: " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
                plugin.getLogger().severe("Please reduce the number of discs or optimize your configuration.");
                plugin.getLogger().severe("═══════════════════════════════════════════════════════════");
                throw new IOException("disc.json exceeds maximum file size of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
            }

            // Read disc.json
            try (Reader reader = new FileReader(discsFile)) {
                this.discsConfig = gson.fromJson(reader, JsonObject.class);
            }

            // Ensure "discs" object exists
            if (!discsConfig.has("discs") || !discsConfig.get("discs").isJsonObject()) {
                discsConfig.add("discs", new JsonObject());
            }

            // Check and log disc.json version
            int fileVersion = discsConfig.has("version") ? discsConfig.get("version").getAsInt() : 0;
            if (fileVersion == 0) {
                plugin.getLogger().warning("disc.json has no version field - adding version " + DISC_CONFIG_VERSION);
                discsConfig.addProperty("version", DISC_CONFIG_VERSION);
                saveDiscsFile();
            } else if (fileVersion < DISC_CONFIG_VERSION) {
                plugin.getLogger().info("disc.json version " + fileVersion + " detected - current version is " + DISC_CONFIG_VERSION);
                // Future: Add migration logic here
                discsConfig.addProperty("version", DISC_CONFIG_VERSION);
                saveDiscsFile();
            } else if (fileVersion > DISC_CONFIG_VERSION) {
                plugin.getLogger().warning("disc.json version " + fileVersion + " is newer than supported version " + DISC_CONFIG_VERSION + "!");
            } else {
                plugin.getLogger().info("Loaded disc.json (version " + fileVersion + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load disc.json", e);

            // Create default config
            this.discsConfig = new JsonObject();
            this.discsConfig.add("discs", new JsonObject());
        }
    }

    /**
     * Loads categories from disc.json.
     */
    private void loadCategories() {
        categories.clear();

        if (!discsConfig.has("categories") || !discsConfig.get("categories").isJsonObject()) {
            plugin.getLogger().info("No categories found in disc.json");
            return;
        }

        JsonObject categoriesSection = discsConfig.getAsJsonObject("categories");
        for (String categoryId : categoriesSection.keySet()) {
            JsonObject categoryData = categoriesSection.getAsJsonObject(categoryId);
            String displayName = colorize(getString(categoryData, "displayName", categoryId));
            String description = colorize(getString(categoryData, "description", ""));

            DiscCategory category = new DiscCategory(categoryId, displayName, description);
            categories.put(categoryId, category);
        }

        plugin.getLogger().info("Loaded " + categories.size() + " disc categories");
    }

    /**
     * Loads playlists from disc.json.
     */
    private void loadPlaylists() {
        playlists.clear();

        if (!discsConfig.has("playlists") || !discsConfig.get("playlists").isJsonObject()) {
            plugin.getLogger().info("No playlists found in disc.json");
            return;
        }

        JsonObject playlistsSection = discsConfig.getAsJsonObject("playlists");
        for (String playlistId : playlistsSection.keySet()) {
            JsonObject playlistData = playlistsSection.getAsJsonObject(playlistId);
            String displayName = colorize(getString(playlistData, "displayName", playlistId));
            String description = colorize(getString(playlistData, "description", ""));

            List<String> discIds = new ArrayList<>();
            if (playlistData.has("discs") && playlistData.get("discs").isJsonArray()) {
                JsonArray discsArray = playlistData.getAsJsonArray("discs");
                for (int i = 0; i < discsArray.size(); i++) {
                    discIds.add(discsArray.get(i).getAsString());
                }
            }

            DiscPlaylist playlist = new DiscPlaylist(playlistId, displayName, description, discIds);
            playlists.put(playlistId, playlist);
        }

        plugin.getLogger().info("Loaded " + playlists.size() + " playlists");
    }

    /**
     * Loads all discs from disc.json.
     * Simple: Just read the JSON file and create CustomDisc objects.
     */
    private void loadDiscs() {
        discs.clear();
        fragments.clear();

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");

        if (discsSection == null || discsSection.size() == 0) {
            plugin.getLogger().warning("No discs found in disc.json!");
            return;
        }

        // Load all discs from JSON
        for (String discId : discsSection.keySet()) {
            JsonObject discData = discsSection.getAsJsonObject(discId);
            CustomDisc disc = parseDiscFromJson(discId, discData);

            if (disc != null) {
                discs.put(discId, disc);

                // Create fragment if fragmentCount > 0
                if (disc.hasFragments()) {
                    createFragment(disc);
                }
            }
        }

        plugin.getLogger().info("Loaded " + discs.size() + " custom discs!");
        if (fragments.size() > 0) {
            plugin.getLogger().info("Loaded " + fragments.size() + " disc fragments!");
        }

        // Validate disc configurations
        validateDiscs();
    }

    /**
     * Parses a CustomDisc from JSON data.
     */
    private CustomDisc parseDiscFromJson(String id, JsonObject data) {
        try {
            String displayName = colorize(getString(data, "displayName", "Custom Disc"));
            String author = colorize(getString(data, "author", "Unknown"));
            // Read "sound" (official) with "soundKey" fallback (legacy from GUI writes before v2.1.6)
            String soundKey = getString(data, "sound", null);
            if (soundKey == null) {
                soundKey = getString(data, "soundKey", "");
            }
            String discTypeName = getString(data, "type", "MUSIC_DISC_13");
            int customModelData = getInt(data, "customModelData", 1001);
            int durationTicks = getInt(data, "durationTicks", 0);
            int fragmentCount = getInt(data, "fragmentCount", 0);
            String description = colorize(getString(data, "description", ""));
            String category = getString(data, "category", null);

            // Parse lore
            List<String> lore = new ArrayList<>();
            if (data.has("lore") && data.get("lore").isJsonArray()) {
                JsonArray loreArray = data.getAsJsonArray("lore");
                for (int i = 0; i < loreArray.size(); i++) {
                    lore.add(colorize(loreArray.get(i).getAsString()));
                }
            }

            Material discType = Material.valueOf(discTypeName);

            return new CustomDisc(id, displayName, author, lore, discType,
                customModelData, soundKey, durationTicks, fragmentCount, description, category);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse disc '" + id + "': " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse disc '" + id + "'", e);
            }
            return null;
        }
    }

    /**
     * Creates a DiscFragment for a disc.
     * Uses a safer calculation for fragment CustomModelData to avoid conflicts.
     */
    private void createFragment(CustomDisc disc) {
        String fragmentName = colorize("&7Fragment - " + de.boondocksulfur.customjukebox.utils.AdventureUtil.stripColor(disc.getDisplayName()));

        // Validate CustomModelData to prevent overflow
        int baseModelData = disc.getCustomModelData();
        if (baseModelData > MAX_CUSTOM_MODEL_DATA) {
            plugin.getLogger().warning("Disc '" + disc.getId() + "' has excessive CustomModelData (" + baseModelData + ")");
            plugin.getLogger().warning("Maximum recommended value is " + MAX_CUSTOM_MODEL_DATA + " to prevent overflow in fragment calculation");
            plugin.getLogger().warning("Fragment will use capped value to prevent overflow");
            baseModelData = MAX_CUSTOM_MODEL_DATA;
        }

        // Fragment CustomModelData = (disc CustomModelData * 10) + 50000
        // This avoids conflicts and prevents overflow issues
        int fragmentModelData = Math.min((baseModelData * 10) + 50000, Integer.MAX_VALUE - 1000);

        DiscFragment fragment = new DiscFragment(disc.getId(), fragmentName,
            fragmentModelData, Material.DISC_FRAGMENT_5);
        fragments.put(disc.getId(), fragment);
    }

    /**
     * Saves current disc configuration to disc.json.
     */
    private void saveDiscsFile() {
        File tempFile = new File(discsFile.getParentFile(), discsFile.getName() + ".tmp");

        try {
            // Create backup before saving
            createBackup(discsFile);

            // Ensure version is always set
            discsConfig.addProperty("version", DISC_CONFIG_VERSION);

            // Write to temporary file first
            try (Writer writer = new FileWriter(tempFile)) {
                gson.toJson(discsConfig, writer);
                writer.flush(); // Ensure all data is written
            }

            // Verify temp file was created and has content
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new IOException("Temporary file creation failed or file is empty");
            }

            // Atomic rename: use Files.move which handles replace on most platforms
            // This is safer than delete+rename which can lose data if rename fails after delete
            Files.move(tempFile.toPath(), discsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Saved disc configuration to disc.json");

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save disc.json", e);

            // Try to restore from backup if save failed
            File latestBackup = getLatestBackup(discsFile);
            if (latestBackup != null && latestBackup.exists()) {
                try {
                    Files.copy(latestBackup.toPath(), discsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("Restored disc.json from backup due to save failure");
                    // Reload the config from the restored file
                    loadDiscsFile();
                } catch (IOException restoreException) {
                    plugin.getLogger().severe("Failed to restore from backup: " + restoreException.getMessage());
                }
            }

            // Clean up temp file if it still exists
            if (tempFile.exists()) {
                tempFile.delete();
            }
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

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Created backup: " + backupName);
            }

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
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Deleted old backup: " + backupFiles[i].getName());
                }
            }
        }
    }

    /**
     * Gets the latest backup file for the given original file.
     * @param originalFile The original file (used to find related backups)
     * @return The latest backup file, or null if no backups exist
     */
    private File getLatestBackup(File originalFile) {
        File parentDir = originalFile.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            return null;
        }

        // Find all backup files for this config
        String baseName = originalFile.getName().replace(".json", "");
        File[] backupFiles = parentDir.listFiles((dir, name) ->
            name.startsWith(baseName + "_backup_") && name.endsWith(".json")
        );

        if (backupFiles == null || backupFiles.length == 0) {
            return null;
        }

        // Sort by modification time (newest first)
        Arrays.sort(backupFiles, (f1, f2) ->
            Long.compare(f2.lastModified(), f1.lastModified())
        );

        return backupFiles[0]; // Return the most recent backup
    }

    public void reload() {
        loadDiscsFile();
        loadCategories();
        loadPlaylists();
        loadDiscs();
    }

    public CustomDisc getDisc(String id) {
        return discs.get(id);
    }

    public Collection<CustomDisc> getAllDiscs() {
        return discs.values();
    }

    public CustomDisc getDiscFromItem(ItemStack item) {
        if (item == null) return null;

        for (CustomDisc disc : discs.values()) {
            if (disc.matches(item)) {
                return disc;
            }
        }

        return null;
    }

    public boolean addDisc(String id, String displayName, String author, List<String> lore,
                          Material discType, int customModelData, String soundKey,
                          int durationTicks, int fragmentCount, String description, String category) {
        // Input validation
        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("Cannot add disc: id is null or empty");
            return false;
        }
        if (displayName == null || displayName.isEmpty()) {
            plugin.getLogger().warning("Cannot add disc: displayName is null or empty");
            return false;
        }
        if (discType == null) {
            plugin.getLogger().warning("Cannot add disc: discType is null");
            return false;
        }

        CustomDisc disc = new CustomDisc(id, displayName, author, lore, discType,
            customModelData, soundKey, durationTicks, fragmentCount, description, category);
        discs.put(id, disc);

        // Save to JSON
        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        JsonObject discData = new JsonObject();

        discData.addProperty("displayName", displayName);
        discData.addProperty("author", author);

        JsonArray loreArray = new JsonArray();
        for (String line : lore) {
            loreArray.add(line);
        }
        discData.add("lore", loreArray);

        discData.addProperty("type", discType.name());
        discData.addProperty("customModelData", customModelData);
        discData.addProperty("sound", soundKey);
        discData.addProperty("durationTicks", durationTicks);
        discData.addProperty("fragmentCount", fragmentCount);
        discData.addProperty("description", description);
        if (category != null && !category.isEmpty()) {
            discData.addProperty("category", category);
        }

        discsSection.add(id, discData);

        saveDiscsFile();

        // Fire event for companion plugins
        plugin.getServer().getPluginManager().callEvent(new DiscRegisteredEvent(disc));

        return true;
    }

    public boolean removeDisc(String id) {
        if (!discs.containsKey(id)) {
            return false;
        }

        CustomDisc disc = discs.remove(id);

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        discsSection.remove(id);

        saveDiscsFile();

        // Fire event for companion plugins
        if (disc != null) {
            plugin.getServer().getPluginManager().callEvent(new DiscRemovedEvent(id, disc));
        }

        return true;
    }

    public CustomDisc getRandomDisc() {
        if (discs.isEmpty()) return null;

        List<CustomDisc> discList = new ArrayList<>(discs.values());
        return discList.get(new Random().nextInt(discList.size()));
    }

    // ==================== FRAGMENT METHODS ====================

    public DiscFragment getFragment(String discId) {
        return fragments.get(discId);
    }

    public Collection<DiscFragment> getAllFragments() {
        return fragments.values();
    }

    public DiscFragment getFragmentFromItem(ItemStack item) {
        if (item == null) return null;

        for (DiscFragment fragment : fragments.values()) {
            if (fragment.matches(item)) {
                return fragment;
            }
        }

        return null;
    }

    public DiscFragment getRandomFragment() {
        if (fragments.isEmpty()) return null;

        List<DiscFragment> fragmentList = new ArrayList<>(fragments.values());
        return fragmentList.get(new Random().nextInt(fragmentList.size()));
    }

    public boolean hasFragments(String discId) {
        return fragments.containsKey(discId);
    }

    // ==================== CATEGORY METHODS ====================

    public DiscCategory getCategory(String id) {
        return categories.get(id);
    }

    public Collection<DiscCategory> getAllCategories() {
        return categories.values();
    }

    public Collection<CustomDisc> getDiscsByCategory(String categoryId) {
        List<CustomDisc> result = new ArrayList<>();
        for (CustomDisc disc : discs.values()) {
            if (categoryId.equals(disc.getCategory())) {
                result.add(disc);
            }
        }
        return result;
    }

    // ==================== PLAYLIST METHODS ====================

    public DiscPlaylist getPlaylist(String id) {
        return playlists.get(id);
    }

    public Collection<DiscPlaylist> getAllPlaylists() {
        return playlists.values();
    }

    public List<CustomDisc> getDiscsFromPlaylist(String playlistId) {
        DiscPlaylist playlist = playlists.get(playlistId);
        if (playlist == null) {
            return new ArrayList<>();
        }

        List<CustomDisc> result = new ArrayList<>();
        for (String discId : playlist.getDiscIds()) {
            CustomDisc disc = discs.get(discId);
            if (disc != null) {
                result.add(disc);
            }
        }
        return result;
    }

    /**
     * Creates a new playlist.
     * @param id Playlist ID
     * @param displayName Display name
     * @param description Description
     * @return true if created successfully
     */
    public boolean createPlaylist(String id, String displayName, String description) {
        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("Cannot create playlist: ID is null or empty");
            return false;
        }

        if (playlists.containsKey(id)) {
            plugin.getLogger().warning("Playlist '" + id + "' already exists");
            return false;
        }

        DiscPlaylist playlist = new DiscPlaylist(id, displayName, description, new ArrayList<>());
        playlists.put(id, playlist);
        savePlaylistToConfig(playlist);
        return true;
    }

    /**
     * Deletes a playlist.
     * @param id Playlist ID
     * @return true if deleted successfully
     */
    public boolean deletePlaylist(String id) {
        if (!playlists.containsKey(id)) {
            return false;
        }

        playlists.remove(id);
        removePlaylistFromConfig(id);
        return true;
    }

    /**
     * Adds a disc to a playlist.
     * @param playlistId Playlist ID
     * @param discId Disc ID
     * @return true if added successfully
     */
    public boolean addDiscToPlaylist(String playlistId, String discId) {
        DiscPlaylist playlist = playlists.get(playlistId);
        if (playlist == null) {
            plugin.getLogger().warning("Playlist '" + playlistId + "' not found");
            return false;
        }

        if (!discs.containsKey(discId)) {
            plugin.getLogger().warning("Disc '" + discId + "' not found");
            return false;
        }

        if (playlist.contains(discId)) {
            plugin.getLogger().warning("Disc '" + discId + "' already in playlist '" + playlistId + "'");
            return false;
        }

        // Create new playlist with added disc
        List<String> newDiscIds = new ArrayList<>(playlist.getDiscIds());
        newDiscIds.add(discId);
        DiscPlaylist updatedPlaylist = new DiscPlaylist(
            playlist.getId(),
            playlist.getDisplayName(),
            playlist.getDescription(),
            newDiscIds
        );

        playlists.put(playlistId, updatedPlaylist);
        savePlaylistToConfig(updatedPlaylist);
        return true;
    }

    /**
     * Removes a disc from a playlist.
     * @param playlistId Playlist ID
     * @param discId Disc ID
     * @return true if removed successfully
     */
    public boolean removeDiscFromPlaylist(String playlistId, String discId) {
        DiscPlaylist playlist = playlists.get(playlistId);
        if (playlist == null) {
            return false;
        }

        if (!playlist.contains(discId)) {
            return false;
        }

        // Create new playlist without the disc
        List<String> newDiscIds = new ArrayList<>(playlist.getDiscIds());
        newDiscIds.remove(discId);
        DiscPlaylist updatedPlaylist = new DiscPlaylist(
            playlist.getId(),
            playlist.getDisplayName(),
            playlist.getDescription(),
            newDiscIds
        );

        playlists.put(playlistId, updatedPlaylist);
        savePlaylistToConfig(updatedPlaylist);
        return true;
    }

    /**
     * Renames a playlist.
     * @param oldId Old playlist ID
     * @param newId New playlist ID
     * @return true if renamed successfully
     */
    public boolean renamePlaylist(String oldId, String newId) {
        DiscPlaylist playlist = playlists.get(oldId);
        if (playlist == null) {
            return false;
        }

        if (playlists.containsKey(newId)) {
            plugin.getLogger().warning("Playlist '" + newId + "' already exists");
            return false;
        }

        // Create new playlist with new ID
        DiscPlaylist renamedPlaylist = new DiscPlaylist(
            newId,
            playlist.getDisplayName(),
            playlist.getDescription(),
            playlist.getDiscIds()
        );

        playlists.remove(oldId);
        playlists.put(newId, renamedPlaylist);

        removePlaylistFromConfig(oldId);
        savePlaylistToConfig(renamedPlaylist);
        return true;
    }

    /**
     * Saves a playlist to the config file.
     */
    private void savePlaylistToConfig(DiscPlaylist playlist) {
        if (!discsConfig.has("playlists")) {
            discsConfig.add("playlists", new JsonObject());
        }

        JsonObject playlistsSection = discsConfig.getAsJsonObject("playlists");
        JsonObject playlistData = new JsonObject();

        playlistData.addProperty("displayName", playlist.getDisplayName());
        playlistData.addProperty("description", playlist.getDescription());

        JsonArray discsArray = new JsonArray();
        for (String discId : playlist.getDiscIds()) {
            discsArray.add(discId);
        }
        playlistData.add("discs", discsArray);

        playlistsSection.add(playlist.getId(), playlistData);
        saveDiscsFile();
    }

    /**
     * Removes a playlist from the config file.
     */
    private void removePlaylistFromConfig(String id) {
        if (!discsConfig.has("playlists")) {
            return;
        }

        JsonObject playlistsSection = discsConfig.getAsJsonObject("playlists");
        playlistsSection.remove(id);
        saveDiscsFile();
    }

    /**
     * Validates all loaded discs for common configuration issues.
     * Logs warnings for potential problems that might cause playback failures.
     */
    private void validateDiscs() {
        int warnings = 0;
        int errors = 0;

        for (CustomDisc disc : discs.values()) {
            // Check for missing or empty sound key
            if (!disc.hasCustomSound() || disc.getSoundKey().isEmpty()) {
                plugin.getLogger().warning("[Validation] Disc '" + disc.getId() + "' has no custom sound defined!");
                plugin.getLogger().warning("  → This disc will play vanilla sounds only.");
                warnings++;
                continue;
            }

            // Sound key validation removed - both 'namespace:key' and 'music_disc.name' formats are valid
            // String soundKey = disc.getSoundKey();
            // if (!soundKey.contains(":")) {
            //     plugin.getLogger().severe("[Validation] Disc '" + disc.getId() + "' has invalid sound key: '" + soundKey + "'");
            //     plugin.getLogger().severe("  → Sound keys must be in format 'namespace:sound_name' (e.g. 'customjukebox:epic_journey')");
            //     errors++;
            // }

            // Check for missing duration
            if (disc.getDurationTicks() <= 0) {
                plugin.getLogger().warning("[Validation] Disc '" + disc.getId() + "' has no duration set!");
                plugin.getLogger().warning("  → Sound will play but won't auto-stop. Consider setting 'durationTicks'.");
                warnings++;
            }

            // Info about successful disc
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[Validation] ✓ Disc '" + disc.getId() + "' validated successfully");
                plugin.getLogger().info("  → Sound: " + disc.getSoundKey() + ", Duration: " + disc.getDurationSeconds() + "s");
            }
        }

        // Summary
        if (errors > 0) {
            plugin.getLogger().severe("════════════════════════════════════════════════════════");
            plugin.getLogger().severe("Found " + errors + " critical error(s) in disc configuration!");
            plugin.getLogger().severe("These discs will NOT work correctly. Please fix disc.json!");
            plugin.getLogger().severe("════════════════════════════════════════════════════════");
        }

        if (warnings > 0) {
            plugin.getLogger().warning("Found " + warnings + " warning(s) in disc configuration.");
            plugin.getLogger().warning("Discs may work but could have issues. Check logs above.");
        }

        if (errors == 0 && warnings == 0) {
            plugin.getLogger().info("All discs validated successfully! No configuration issues found.");
        }
    }

    // ==================== HELPER METHODS ====================

    private String getString(JsonObject obj, String key, String defaultValue) {
        try {
            if (obj.has(key)) {
                return obj.get(key).getAsString();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get string '" + key + "': " + e.getMessage());
        }
        return defaultValue;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        try {
            if (obj.has(key)) {
                return obj.get(key).getAsInt();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get int '" + key + "': " + e.getMessage());
        }
        return defaultValue;
    }

    private String colorize(String message) {
        return AdventureUtil.toLegacy(AdventureUtil.parseComponent(message));
    }

    // ==================== DISC CRUD OPERATIONS ====================

    /**
     * Creates a new custom disc and saves it to disc.json.
     */
    public boolean createDisc(String id, String displayName, String author, String soundKey,
                             int durationTicks, String category, int customModelData, List<String> lore) {
        if (discs.containsKey(id)) {
            return false;
        }

        // Create disc object
        CustomDisc disc = new CustomDisc(id, displayName, author, lore, Material.MUSIC_DISC_13,
                                        customModelData, soundKey, durationTicks, 0, "", category);
        discs.put(id, disc);

        // Save to JSON
        saveDiscToConfig(disc);

        // Fire event for companion plugins
        plugin.getServer().getPluginManager().callEvent(new DiscRegisteredEvent(disc));

        return true;
    }

    /**
     * Deletes a disc from the configuration.
     */
    public boolean deleteDisc(String id) {
        if (!discs.containsKey(id)) {
            return false;
        }

        CustomDisc disc = discs.remove(id);
        removeDiscFromConfig(id);

        // Fire event for companion plugins
        if (disc != null) {
            plugin.getServer().getPluginManager().callEvent(new DiscRemovedEvent(id, disc));
        }

        return true;
    }

    /**
     * Updates a single field of a disc.
     */
    public boolean updateDiscField(String id, String field, Object value) {
        if (!discs.containsKey(id)) {
            return false;
        }

        if (!discsConfig.has("discs")) {
            return false;
        }

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        if (!discsSection.has(id)) {
            return false;
        }

        JsonObject discData = discsSection.getAsJsonObject(id);

        // Update field
        switch (field) {
            case "displayName":
            case "author":
            case "category":
                if (value == null) {
                    discData.remove(field);
                } else {
                    discData.addProperty(field, (String) value);
                }
                break;
            case "sound":
            case "soundKey": // Accept both, always write as "sound"
                discData.remove("soundKey"); // Clean up legacy key if present
                if (value == null) {
                    discData.remove("sound");
                } else {
                    discData.addProperty("sound", (String) value);
                }
                break;
            case "durationTicks":
            case "customModelData":
            case "fragmentCount":
                discData.addProperty(field, (Integer) value);
                break;
        }

        saveDiscsFile();

        // Reload disc
        reload();
        return true;
    }

    /**
     * Saves a disc to the config file.
     */
    private void saveDiscToConfig(CustomDisc disc) {
        if (!discsConfig.has("discs")) {
            discsConfig.add("discs", new JsonObject());
        }

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        JsonObject discData = new JsonObject();

        discData.addProperty("displayName", disc.getDisplayName());
        discData.addProperty("author", disc.getAuthor());
        discData.addProperty("sound", disc.getSoundKey());
        discData.addProperty("durationTicks", disc.getDurationTicks());
        discData.addProperty("customModelData", disc.getCustomModelData());

        if (disc.getCategory() != null) {
            discData.addProperty("category", disc.getCategory());
        }

        if (disc.getLore() != null && !disc.getLore().isEmpty()) {
            JsonArray loreArray = new JsonArray();
            for (String line : disc.getLore()) {
                loreArray.add(line);
            }
            discData.add("lore", loreArray);
        }

        discsSection.add(disc.getId(), discData);
        saveDiscsFile();
    }

    /**
     * Removes a disc from the config file.
     */
    private void removeDiscFromConfig(String id) {
        if (!discsConfig.has("discs")) {
            return;
        }

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        discsSection.remove(id);
        saveDiscsFile();
    }

    // ==================== CATEGORY CRUD OPERATIONS ====================

    /**
     * Creates a new category and saves it to disc.json.
     */
    public boolean createCategory(String id, String displayName, String description) {
        if (categories.containsKey(id)) {
            return false; // Category already exists
        }

        // Create category object
        DiscCategory category = new DiscCategory(id, displayName, description);
        categories.put(id, category);

        // Save to JSON
        saveCategoryToConfig(category);
        return true;
    }

    /**
     * Saves a category to the config file.
     */
    private void saveCategoryToConfig(DiscCategory category) {
        if (!discsConfig.has("categories")) {
            discsConfig.add("categories", new JsonObject());
        }

        JsonObject categoriesSection = discsConfig.getAsJsonObject("categories");
        JsonObject categoryData = new JsonObject();

        categoryData.addProperty("displayName", category.getDisplayName());
        categoryData.addProperty("description", category.getDescription());

        categoriesSection.add(category.getId(), categoryData);
        saveDiscsFile();
    }

    /**
     * Updates an existing category with new display name and/or description.
     */
    public boolean updateCategory(String id, String newDisplayName, String newDescription) {
        if (!categories.containsKey(id)) {
            return false; // Category doesn't exist
        }

        // Remove old category
        categories.remove(id);
        removeCategoryFromConfig(id);

        // Create updated category with same ID
        DiscCategory updatedCategory = new DiscCategory(id, newDisplayName, newDescription);
        categories.put(id, updatedCategory);

        // Save to JSON
        saveCategoryToConfig(updatedCategory);
        return true;
    }

    /**
     * Deletes a category from the configuration.
     */
    public boolean deleteCategory(String id) {
        if (!categories.containsKey(id)) {
            return false;
        }

        categories.remove(id);
        removeCategoryFromConfig(id);
        return true;
    }

    /**
     * Removes a category from the config file.
     */
    private void removeCategoryFromConfig(String id) {
        if (!discsConfig.has("categories")) {
            return;
        }

        JsonObject categoriesSection = discsConfig.getAsJsonObject("categories");
        categoriesSection.remove(id);
        saveDiscsFile();
    }
}
