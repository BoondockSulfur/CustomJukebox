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
import de.boondocksulfur.customjukebox.utils.BackupUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages custom discs with manual JSON configuration.
 * Simple and straightforward - no auto-discovery, no magic.
 * Just load disc.json and create discs from it.
 */
public class DiscManager {

    private static final int DISC_CONFIG_VERSION = 1; // Current disc.json version for migration support
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB max file size
    // Maximum allowed CustomModelData to prevent overflow in fragment calculation
    private static final int MAX_CUSTOM_MODEL_DATA = de.boondocksulfur.customjukebox.utils.InputValidator.MAX_CUSTOM_MODEL_DATA;

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File discsFile;
    private final Map<String, CustomDisc> discs;
    private final Map<String, DiscFragment> fragments;
    private final Map<String, DiscCategory> categories;
    private final Map<String, DiscPlaylist> playlists;
    private JsonObject discsConfig;
    /**
     * Guards edits to the {@link #discsConfig} tree and the snapshot taken for
     * saving - see the note in ConfigManager. Blocks never call out.
     */
    private final Object configLock = new Object();

    public DiscManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Prevent & from becoming &amp; in JSON
            .create();
        this.discsFile = new File(plugin.getDataFolder(), "disc.json");
        // Concurrent maps: on Folia, /cjb reload clears and refills these while
        // other region threads read them (e.g. getDiscFromItem on disc insert)
        this.discs = new ConcurrentHashMap<>();
        this.fragments = new ConcurrentHashMap<>();
        this.categories = new ConcurrentHashMap<>();
        this.playlists = new ConcurrentHashMap<>();

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
            // A queued save must land before we read the file back
            if (plugin.getConfigWriter() != null) {
                plugin.getConfigWriter().flush();
            }
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

            // Read disc.json (explicit UTF-8 - saveDiscsFile() writes UTF-8, so
            // reading with the platform default would break on a non-UTF-8 JVM)
            try (Reader reader = new InputStreamReader(new FileInputStream(discsFile), StandardCharsets.UTF_8)) {
                this.discsConfig = gson.fromJson(reader, JsonObject.class);
            }
            if (discsConfig == null) {
                discsConfig = new JsonObject();
            }

            boolean addedKeys;
            synchronized (configLock) {
                // Ensure "discs" object exists (guards against a corrupt non-object value)
                if (discsConfig.has("discs") && !discsConfig.get("discs").isJsonObject()) {
                    discsConfig.add("discs", new JsonObject());
                }

                // Merge in structural keys added by newer plugin versions. The
                // discs/categories/playlists maps are user content and are NEVER
                // seeded from the defaults, so example entries stay deleted.
                addedKeys = mergeDefaults();
            }

            // Check and log disc.json version
            int fileVersion = discsConfig.has("version") ? discsConfig.get("version").getAsInt() : 0;
            boolean versionChanged = fileVersion != DISC_CONFIG_VERSION;
            if (fileVersion == 0) {
                plugin.getLogger().warning("disc.json has no version field - adding version " + DISC_CONFIG_VERSION);
            } else if (fileVersion < DISC_CONFIG_VERSION) {
                plugin.getLogger().info("disc.json version " + fileVersion + " detected - current version is " + DISC_CONFIG_VERSION);
            } else if (fileVersion > DISC_CONFIG_VERSION) {
                plugin.getLogger().warning("disc.json version " + fileVersion + " is newer than supported version " + DISC_CONFIG_VERSION + "!");
                versionChanged = false; // don't downgrade a newer file
            } else {
                plugin.getLogger().info("Loaded disc.json (version " + fileVersion + ")");
            }

            if (versionChanged) {
                synchronized (configLock) {
                    discsConfig.addProperty("version", DISC_CONFIG_VERSION);
                }
            }
            if (addedKeys) {
                plugin.getLogger().info("Added missing disc.json keys from defaults");
            }
            if (addedKeys || versionChanged) {
                saveDiscsFile();
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load disc.json", e);

            // Create default config
            this.discsConfig = new JsonObject();
            this.discsConfig.add("discs", new JsonObject());
        }
    }

    /**
     * Merges structural keys from the bundled default disc.json into the loaded
     * config. The user-owned content maps (discs, categories, playlists) are
     * protected: they are only created empty when missing and never seeded with
     * the default example entries.
     * @return true if at least one key was added
     */
    private boolean mergeDefaults() {
        try (InputStream defaultStream = plugin.getResource("disc.json")) {
            if (defaultStream == null) {
                return false;
            }
            JsonObject defaults = gson.fromJson(
                new InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            if (defaults == null) {
                return false;
            }
            Set<String> protectedSections = Set.of("discs", "categories", "playlists");
            return de.boondocksulfur.customjukebox.utils.JsonConfigUtil.mergeDefaults(discsConfig, defaults, protectedSections);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to merge default disc.json keys: " + e.getMessage());
            return false;
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
            if (!categoriesSection.get(categoryId).isJsonObject()) {
                plugin.getLogger().warning("Skipping malformed category '" + categoryId + "' in disc.json (not an object)");
                continue;
            }
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
            if (!playlistsSection.get(playlistId).isJsonObject()) {
                plugin.getLogger().warning("Skipping malformed playlist '" + playlistId + "' in disc.json (not an object)");
                continue;
            }
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
            if (!discsSection.get(discId).isJsonObject()) {
                plugin.getLogger().warning("Skipping malformed disc '" + discId + "' in disc.json (not an object)");
                continue;
            }
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
    /**
     * Adds or removes the fragment entry for a disc so the registry always
     * matches the disc's current {@code fragmentCount}.
     * @param disc disc whose fragment should be synchronised
     */
    private void syncFragment(CustomDisc disc) {
        if (disc.hasFragments()) {
            createFragment(disc);
        } else {
            fragments.remove(disc.getId());
        }
    }

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
     * The namespace prefix every disc id shares, if they all share one.
     *
     * <p>Ids commonly carry a prefix like {@code music_disc.} or a pack's own
     * {@code custom.}, which distinguishes nothing and costs a third of a GUI
     * title. Rather than assume a particular prefix, this reads what the
     * server's own discs actually use: the longest leading run ending in a dot
     * that every id begins with. Servers whose ids share nothing get an empty
     * result and keep their ids whole.
     *
     * @return the shared prefix including its trailing dot, or an empty string
     */
    public String getCommonIdPrefix() {
        String candidate = null;
        for (CustomDisc disc : getAllDiscs()) {
            String id = disc.getId();
            int dot = id.lastIndexOf('.');
            String prefix = dot >= 0 ? id.substring(0, dot + 1) : "";
            if (prefix.isEmpty()) {
                return ""; // one id without a prefix means there is no shared one
            }
            if (candidate == null) {
                candidate = prefix;
                continue;
            }
            // Shrink to what both still share, always ending on a dot
            while (!candidate.isEmpty() && !prefix.startsWith(candidate)) {
                int previous = candidate.lastIndexOf('.', candidate.length() - 2);
                candidate = previous >= 0 ? candidate.substring(0, previous + 1) : "";
            }
            if (candidate.isEmpty()) {
                return "";
            }
        }
        return candidate == null ? "" : candidate;
    }

    /**
     * Saves current disc configuration to disc.json.
     */
    /**
     * Persists disc.json. The document is snapshotted here and written by the
     * shared {@link de.boondocksulfur.customjukebox.utils.ConfigWriter} off the
     * server thread; the write itself is atomic, so a failure leaves the
     * previous file intact and there is nothing to restore.
     */
    private void saveDiscsFile() {
        JsonObject snapshot;
        synchronized (configLock) {
            discsConfig.addProperty("version", DISC_CONFIG_VERSION);
            snapshot = discsConfig.deepCopy();
        }
        plugin.getConfigWriter().save(discsFile, snapshot,
            plugin.getConfigManager().getMaxBackups(),
            plugin.getConfigManager().getBackupMinIntervalMillis());
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

        // Fast path: items created since the PDC tag carry their disc ID
        String pdcId = ItemUtil.getPdcString(item, ItemUtil.DISC_ID_KEY);
        if (pdcId != null) {
            CustomDisc disc = discs.get(pdcId);
            // Verify the material still matches (guards against stale/foreign tags)
            return disc != null && item.getType() == disc.getDiscType() ? disc : null;
        }

        // Legacy items without PDC tag: match by material + CustomModelData
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
        // Keep the fragment registry in sync - otherwise a disc added at runtime
        // would have no craftable/droppable fragment until the next reload
        syncFragment(disc);

        // Save to JSON
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

        synchronized (configLock) {
            if (!discsConfig.has("discs") || !discsConfig.get("discs").isJsonObject()) {
                discsConfig.add("discs", new JsonObject());
            }
            discsConfig.getAsJsonObject("discs").add(id, discData);
        }

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
        // Drop the fragment too - a leftover entry keeps handing out fragments
        // (creeper drops, loot chests, /cjb fragment) for a disc that no longer
        // exists, and those fragments can never be crafted into anything.
        fragments.remove(id);

        synchronized (configLock) {
            if (discsConfig.has("discs") && discsConfig.get("discs").isJsonObject()) {
                discsConfig.getAsJsonObject("discs").remove(id);
            }
        }

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

        // Fast path: items created since the PDC tag carry their disc ID
        String pdcId = ItemUtil.getPdcString(item, ItemUtil.FRAGMENT_DISC_ID_KEY);
        if (pdcId != null) {
            DiscFragment fragment = fragments.get(pdcId);
            return fragment != null && item.getType() == fragment.getFragmentType() ? fragment : null;
        }

        // Legacy items without PDC tag: match by material + CustomModelData
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
        return resolveDiscs(playlists.get(playlistId));
    }

    /**
     * Resolves a playlist's disc IDs to discs, skipping ones that no longer
     * exist.
     *
     * <p>Works off the playlist object rather than looking its ID up in the
     * registry, so ad-hoc playlists that were never registered - a player's
     * favourites, for instance - resolve correctly too.
     *
     * @param playlist playlist to resolve, may be null
     * @return the existing discs, in playlist order
     */
    public List<CustomDisc> resolveDiscs(DiscPlaylist playlist) {
        List<CustomDisc> result = new ArrayList<>();
        if (playlist == null) {
            return result;
        }
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
        notifyZonesPlaylistChanged(id);
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
        notifyZonesPlaylistChanged(playlistId);
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
        notifyZonesPlaylistChanged(playlistId);
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

        // Batch both JSON edits into a single file write (and backup rotation)
        synchronized (configLock) {
            if (discsConfig.has("playlists") && discsConfig.get("playlists").isJsonObject()) {
                discsConfig.getAsJsonObject("playlists").remove(oldId);
            }
        }
        writePlaylistToConfig(renamedPlaylist);
        saveDiscsFile();
        // Zones still reference the old id and can no longer resolve it
        notifyZonesPlaylistChanged(oldId);
        return true;
    }

    /**
     * Saves a playlist to the config file.
     */
    private void savePlaylistToConfig(DiscPlaylist playlist) {
        writePlaylistToConfig(playlist);
        saveDiscsFile();
    }

    /**
     * Writes a playlist into the in-memory JSON config without saving the file.
     * Callers batching several updates save once afterwards.
     */
    private void writePlaylistToConfig(DiscPlaylist playlist) {
        JsonObject playlistData = new JsonObject();
        playlistData.addProperty("displayName", playlist.getDisplayName());
        playlistData.addProperty("description", playlist.getDescription());

        JsonArray discsArray = new JsonArray();
        for (String discId : playlist.getDiscIds()) {
            discsArray.add(discId);
        }
        playlistData.add("discs", discsArray);

        synchronized (configLock) {
            if (!discsConfig.has("playlists") || !discsConfig.get("playlists").isJsonObject()) {
                discsConfig.add("playlists", new JsonObject());
            }
            discsConfig.getAsJsonObject("playlists").add(playlist.getId(), playlistData);
        }
    }

    /**
     * Removes a playlist from the config file.
     */
    private void removePlaylistFromConfig(String id) {
        synchronized (configLock) {
            if (!discsConfig.has("playlists") || !discsConfig.get("playlists").isJsonObject()) {
                return;
            }
            discsConfig.getAsJsonObject("playlists").remove(id);
        }
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

    /**
     * Lets live ambient zones pick up a playlist content change immediately.
     * Null-guarded because DiscManager is constructed before AmbientZoneManager.
     */
    private void notifyZonesPlaylistChanged(String playlistId) {
        AmbientZoneManager zones = plugin.getAmbientZoneManager();
        if (zones != null) {
            zones.refreshZonesUsingPlaylist(playlistId);
        }
    }

    /** Same, for a change to a disc that playlists may reference. */
    private void notifyZonesDiscChanged(String discId) {
        AmbientZoneManager zones = plugin.getAmbientZoneManager();
        if (zones != null) {
            zones.refreshZonesUsingDisc(discId);
        }
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

        // Warn about duplicate material+CustomModelData combos: new items carry
        // the disc ID in their PDC, but legacy items are matched by model data
        for (CustomDisc existing : discs.values()) {
            if (existing.getDiscType() == Material.MUSIC_DISC_13
                && existing.getCustomModelData() == customModelData) {
                plugin.getLogger().warning("Disc '" + id + "' uses the same CustomModelData ("
                    + customModelData + ") as '" + existing.getId()
                    + "' - both will share the same texture, and pre-3.2.0 items may be ambiguous");
                break;
            }
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
        // Drop the fragment too (see removeDisc)
        fragments.remove(id);

        // Remove the disc from all playlists so no dangling references remain.
        // JSON updates are batched; removeDiscFromConfig below saves the file once.
        List<String> affectedPlaylists = new ArrayList<>();
        for (DiscPlaylist playlist : new ArrayList<>(playlists.values())) {
            if (!playlist.contains(id)) {
                continue;
            }
            affectedPlaylists.add(playlist.getId());
            List<String> newDiscIds = new ArrayList<>(playlist.getDiscIds());
            newDiscIds.remove(id);
            DiscPlaylist updatedPlaylist = new DiscPlaylist(
                playlist.getId(),
                playlist.getDisplayName(),
                playlist.getDescription(),
                newDiscIds
            );
            playlists.put(playlist.getId(), updatedPlaylist);
            writePlaylistToConfig(updatedPlaylist);
        }

        removeDiscFromConfig(id);

        // Playlists lost a track - rebuild any zone that was playing them.
        // Done after the playlist updates above so the new contents are seen.
        for (String playlistId : affectedPlaylists) {
            notifyZonesPlaylistChanged(playlistId);
        }

        // Fire event for companion plugins
        if (disc != null) {
            plugin.getServer().getPluginManager().callEvent(new DiscRemovedEvent(id, disc));
        }

        return true;
    }

    /**
     * Updates a single field of a disc and persists the change.
     *
     * <p>Only the edited disc is rebuilt in memory. This used to call
     * {@link #reload()}, which re-read disc.json from disk and rebuilt every
     * disc, category and playlist (including the full validation log) after
     * every single field edit in the GUI.
     *
     * @param id disc ID
     * @param field field name (displayName, author, category, sound/soundKey,
     *              durationTicks, customModelData, fragmentCount)
     * @param value new value; String fields accept null to remove the key
     * @return true if the field was known and the disc was updated
     */
    public boolean updateDiscField(String id, String field, Object value) {
        if (!discs.containsKey(id)) {
            return false;
        }

        if (!discsConfig.has("discs") || !discsConfig.get("discs").isJsonObject()) {
            return false;
        }

        JsonObject discsSection = discsConfig.getAsJsonObject("discs");
        if (!discsSection.has(id) || !discsSection.get(id).isJsonObject()) {
            return false;
        }

        JsonObject discData = discsSection.getAsJsonObject(id);

        CustomDisc updated;
        synchronized (configLock) {
            // Update field
            switch (field) {
                case "displayName":
                case "author":
                case "category":
                    if (value == null) {
                        discData.remove(field);
                    } else if (value instanceof String stringValue) {
                        discData.addProperty(field, stringValue);
                    } else {
                        plugin.getLogger().warning("updateDiscField('" + field + "') expects a String, got "
                            + value.getClass().getSimpleName());
                        return false;
                    }
                    break;
                case "sound":
                case "soundKey": // Accept both, always write as "sound"
                    discData.remove("soundKey"); // Clean up legacy key if present
                    if (value == null) {
                        discData.remove("sound");
                    } else if (value instanceof String stringValue) {
                        discData.addProperty("sound", stringValue);
                    } else {
                        plugin.getLogger().warning("updateDiscField('sound') expects a String, got "
                            + value.getClass().getSimpleName());
                        return false;
                    }
                    break;
                case "durationTicks":
                case "customModelData":
                case "fragmentCount":
                    if (value instanceof Integer intValue) {
                        discData.addProperty(field, intValue);
                    } else {
                        plugin.getLogger().warning("updateDiscField('" + field + "') expects an Integer, got "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
                        return false;
                    }
                    break;
                default:
                    plugin.getLogger().warning("updateDiscField: unknown field '" + field + "'");
                    return false;
            }

            // Rebuild just this disc from its (now updated) JSON entry, using the
            // same parse path as loading, so colorization and defaults match.
            updated = parseDiscFromJson(id, discData);
        }
        if (updated == null) {
            plugin.getLogger().severe("Failed to re-parse disc '" + id + "' after updating '" + field + "'");
            return false;
        }
        discs.put(id, updated);
        syncFragment(updated);

        saveDiscsFile();
        // A changed sound key or duration changes what zones can play
        notifyZonesDiscChanged(id);
        return true;
    }

    /**
     * Saves a disc to the config file.
     */
    private void saveDiscToConfig(CustomDisc disc) {
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

        synchronized (configLock) {
            if (!discsConfig.has("discs") || !discsConfig.get("discs").isJsonObject()) {
                discsConfig.add("discs", new JsonObject());
            }
            discsConfig.getAsJsonObject("discs").add(disc.getId(), discData);
        }
        saveDiscsFile();
    }

    /**
     * Removes a disc from the config file.
     */
    private void removeDiscFromConfig(String id) {
        synchronized (configLock) {
            if (!discsConfig.has("discs") || !discsConfig.get("discs").isJsonObject()) {
                return;
            }
            discsConfig.getAsJsonObject("discs").remove(id);
        }
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
        writeCategoryToConfig(category);
        saveDiscsFile();
    }

    /**
     * Writes a category into the in-memory JSON config without saving the file.
     * Callers batching several updates save once afterwards.
     */
    private void writeCategoryToConfig(DiscCategory category) {
        JsonObject categoryData = new JsonObject();
        categoryData.addProperty("displayName", category.getDisplayName());
        categoryData.addProperty("description", category.getDescription());

        synchronized (configLock) {
            if (!discsConfig.has("categories") || !discsConfig.get("categories").isJsonObject()) {
                discsConfig.add("categories", new JsonObject());
            }
            discsConfig.getAsJsonObject("categories").add(category.getId(), categoryData);
        }
    }

    /**
     * Updates an existing category with new display name and/or description.
     */
    public boolean updateCategory(String id, String newDisplayName, String newDescription) {
        if (!categories.containsKey(id)) {
            return false; // Category doesn't exist
        }

        // Replace in place - the previous remove+add wrote disc.json twice
        // (and rotated two backups) for a single edit.
        DiscCategory updatedCategory = new DiscCategory(id, newDisplayName, newDescription);
        categories.put(id, updatedCategory);

        writeCategoryToConfig(updatedCategory);
        saveDiscsFile();
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

        // Detach the category from all discs that referenced it, both in the
        // JSON config and in memory (CustomDisc is immutable, so affected discs
        // are replaced with detached copies). One save persists everything -
        // no full reload, which would briefly empty the registries on Folia.
        synchronized (configLock) {
        if (discsConfig.has("discs") && discsConfig.get("discs").isJsonObject()) {
                JsonObject discsSection = discsConfig.getAsJsonObject("discs");
                for (CustomDisc disc : new ArrayList<>(discs.values())) {
                    if (!id.equals(disc.getCategory())) continue;
                    if (discsSection.has(disc.getId()) && discsSection.get(disc.getId()).isJsonObject()) {
                        discsSection.getAsJsonObject(disc.getId()).remove("category");
                    }
                    discs.put(disc.getId(), new CustomDisc(disc.getId(), disc.getDisplayName(),
                        disc.getAuthor(), disc.getLore(), disc.getDiscType(), disc.getCustomModelData(),
                        disc.getSoundKey(), disc.getDurationTicks(), disc.getFragmentCount(),
                        disc.getDescription(), null));
                }
            }

            if (discsConfig.has("categories") && discsConfig.get("categories").isJsonObject()) {
                discsConfig.getAsJsonObject("categories").remove(id);
            }
        }
        saveDiscsFile();

        return true;
    }

}
