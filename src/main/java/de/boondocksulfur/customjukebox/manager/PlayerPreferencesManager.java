package de.boondocksulfur.customjukebox.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.PlayerPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stores per-player music settings in players.json: whether the player wants to
 * hear plugin music at all, their personal volume, and their favourite discs.
 *
 * <p>Saves go through the shared asynchronous
 * {@link de.boondocksulfur.customjukebox.utils.ConfigWriter}, which coalesces
 * bursts, so a player toggling settings repeatedly does not cause one file write
 * per click.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class PlayerPreferencesManager {

    private static final int PREFERENCES_VERSION = 1;
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    private final CustomJukebox plugin;
    private final Gson gson;
    private final File preferencesFile;
    private final Map<UUID, PlayerPreferences> preferences = new ConcurrentHashMap<>();

    public PlayerPreferencesManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.preferencesFile = new File(plugin.getDataFolder(), "players.json");
        load();
    }

    // ==================== PERSISTENCE ====================

    private void load() {
        preferences.clear();
        try {
            if (plugin.getConfigWriter() != null) {
                plugin.getConfigWriter().flush();
            }
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            if (!preferencesFile.exists()) {
                return; // Nothing saved yet - every player is on defaults
            }
            if (preferencesFile.length() > MAX_FILE_SIZE) {
                throw new IOException("players.json exceeds " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
            }

            JsonObject root;
            try (Reader reader = new InputStreamReader(new FileInputStream(preferencesFile), StandardCharsets.UTF_8)) {
                root = gson.fromJson(reader, JsonObject.class);
            }
            if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
                return;
            }

            JsonObject players = root.getAsJsonObject("players");
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping players.json entry with invalid UUID: " + entry.getKey());
                    continue;
                }
                preferences.put(uuid, parse(entry.getValue().getAsJsonObject()));
            }
            if (!preferences.isEmpty()) {
                plugin.getLogger().info("Loaded music settings for " + preferences.size() + " player(s)");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load players.json - using defaults", e);
        }
    }

    private PlayerPreferences parse(JsonObject data) {
        PlayerPreferences prefs = new PlayerPreferences();
        if (data.has("musicEnabled") && data.get("musicEnabled").isJsonPrimitive()) {
            prefs.setMusicEnabled(data.get("musicEnabled").getAsBoolean());
        }
        if (data.has("volume") && data.get("volume").isJsonPrimitive()) {
            prefs.setVolume(data.get("volume").getAsFloat());
        }
        if (data.has("favorites") && data.get("favorites").isJsonArray()) {
            JsonArray array = data.getAsJsonArray("favorites");
            synchronized (prefs.getFavorites()) {
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive()) {
                        prefs.getFavorites().add(element.getAsString());
                    }
                }
            }
        }
        return prefs;
    }

    /**
     * Persists all non-default preferences. Called after every change; the
     * config writer coalesces repeated saves into a single file write.
     */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", PREFERENCES_VERSION);

        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, PlayerPreferences> entry : preferences.entrySet()) {
            PlayerPreferences prefs = entry.getValue();
            if (prefs.isDefault()) {
                continue; // Nothing worth storing - keeps the file small
            }
            JsonObject data = new JsonObject();
            data.addProperty("musicEnabled", prefs.isMusicEnabled());
            data.addProperty("volume", prefs.getVolume());
            JsonArray favorites = new JsonArray();
            synchronized (prefs.getFavorites()) {
                for (String discId : prefs.getFavorites()) {
                    favorites.add(discId);
                }
            }
            data.add("favorites", favorites);
            players.add(entry.getKey().toString(), data);
        }
        root.add("players", players);

        plugin.getConfigWriter().save(preferencesFile, root,
            plugin.getConfigManager().getMaxBackups(),
            plugin.getConfigManager().getBackupMinIntervalMillis());
    }

    /**
     * Reloads player settings from disk.
     */
    public void reload() {
        load();
    }

    // ==================== ACCESS ====================

    private PlayerPreferences get(UUID uuid) {
        return preferences.computeIfAbsent(uuid, key -> new PlayerPreferences());
    }

    /**
     * Whether the player wants to hear plugin music.
     * @param uuid player UUID
     * @return true unless the player turned music off
     */
    public boolean isMusicEnabled(UUID uuid) {
        PlayerPreferences prefs = preferences.get(uuid);
        return prefs == null || prefs.isMusicEnabled();
    }

    /**
     * Turns plugin music on or off for a player and persists the change.
     * @param uuid player UUID
     * @param enabled whether music should play
     */
    public void setMusicEnabled(UUID uuid, boolean enabled) {
        get(uuid).setMusicEnabled(enabled);
        save();
    }

    /**
     * The player's personal volume, or {@link PlayerPreferences#VOLUME_INHERIT}
     * when they follow the server volume.
     * @param uuid player UUID
     * @return personal volume or the inherit sentinel
     */
    public float getPersonalVolume(UUID uuid) {
        PlayerPreferences prefs = preferences.get(uuid);
        return prefs == null ? PlayerPreferences.VOLUME_INHERIT : prefs.getVolume();
    }

    /**
     * Sets the player's personal volume and persists it.
     * @param uuid player UUID
     * @param volume 0.0-4.0, or {@link PlayerPreferences#VOLUME_INHERIT} to follow the server
     */
    public void setPersonalVolume(UUID uuid, float volume) {
        get(uuid).setVolume(volume < 0 ? PlayerPreferences.VOLUME_INHERIT : Math.min(4f, volume));
        save();
    }

    /**
     * Resolves the volume a specific player should actually be sent.
     *
     * <p>A personal volume replaces the server volume rather than scaling it, so
     * it stays predictable when an admin changes the global value. A server-wide
     * mute always wins - otherwise {@code /cjb mute} would not silence players
     * who had set their own volume.
     *
     * @param uuid player UUID
     * @return volume in the range 0.0-4.0
     */
    public float effectiveVolume(UUID uuid) {
        if (plugin.getConfigManager().isMuted()) {
            return 0f;
        }
        float personal = getPersonalVolume(uuid);
        if (personal < 0) {
            return plugin.getConfigManager().getVolume();
        }
        return Math.max(0f, Math.min(4f, personal));
    }

    // ==================== FAVORITES ====================

    /**
     * The player's favourite disc IDs, in the order they were added.
     * @param uuid player UUID
     * @return a copy of the favourites list
     */
    public List<String> getFavorites(UUID uuid) {
        PlayerPreferences prefs = preferences.get(uuid);
        if (prefs == null) {
            return new ArrayList<>();
        }
        synchronized (prefs.getFavorites()) {
            return new ArrayList<>(prefs.getFavorites());
        }
    }

    /**
     * Whether a disc is among the player's favourites.
     * @param uuid player UUID
     * @param discId disc ID
     * @return true if favourited
     */
    public boolean isFavorite(UUID uuid, String discId) {
        PlayerPreferences prefs = preferences.get(uuid);
        if (prefs == null) {
            return false;
        }
        synchronized (prefs.getFavorites()) {
            return prefs.getFavorites().contains(discId);
        }
    }

    /**
     * Adds a disc to the player's favourites.
     * @param uuid player UUID
     * @param discId disc ID
     * @return false if it was already a favourite
     */
    public boolean addFavorite(UUID uuid, String discId) {
        PlayerPreferences prefs = get(uuid);
        boolean added;
        synchronized (prefs.getFavorites()) {
            added = prefs.getFavorites().add(discId);
        }
        if (added) {
            save();
        }
        return added;
    }

    /**
     * Removes a disc from the player's favourites.
     * @param uuid player UUID
     * @param discId disc ID
     * @return false if it was not a favourite
     */
    public boolean removeFavorite(UUID uuid, String discId) {
        PlayerPreferences prefs = preferences.get(uuid);
        if (prefs == null) {
            return false;
        }
        boolean removed;
        synchronized (prefs.getFavorites()) {
            removed = prefs.getFavorites().remove(discId);
        }
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Adds or removes a favourite, whichever applies.
     * @param uuid player UUID
     * @param discId disc ID
     * @return true if the disc is a favourite afterwards
     */
    public boolean toggleFavorite(UUID uuid, String discId) {
        if (isFavorite(uuid, discId)) {
            removeFavorite(uuid, discId);
            return false;
        }
        addFavorite(uuid, discId);
        return true;
    }

    /**
     * Drops favourites pointing at discs that no longer exist.
     * @return number of stale entries removed
     */
    public int pruneMissingDiscs() {
        int removed = 0;
        for (PlayerPreferences prefs : preferences.values()) {
            synchronized (prefs.getFavorites()) {
                Set<String> stale = new LinkedHashSet<>();
                for (String discId : prefs.getFavorites()) {
                    if (plugin.getDiscManager().getDisc(discId) == null) {
                        stale.add(discId);
                    }
                }
                prefs.getFavorites().removeAll(stale);
                removed += stale.size();
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }
}
