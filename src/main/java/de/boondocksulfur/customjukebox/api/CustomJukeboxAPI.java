package de.boondocksulfur.customjukebox.api;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Public API for CustomJukebox plugin.
 * Allows other plugins to interact with custom discs and playback functionality.
 *
 * <p>Usage example:
 * <pre>{@code
 * CustomJukeboxAPI api = CustomJukeboxAPI.getInstance();
 * if (api != null) {
 *     Collection<CustomDisc> discs = api.getAllDiscs();
 *     // Do something with discs
 * }
 * }</pre>
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class CustomJukeboxAPI {

    private final CustomJukebox plugin;

    /**
     * Internal constructor. Use {@link #getInstance()} to access the API.
     * @param plugin Plugin instance
     */
    public CustomJukeboxAPI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the API instance.
     * @return CustomJukeboxAPI instance, or null if plugin is not loaded
     */
    public static CustomJukeboxAPI getInstance() {
        CustomJukebox plugin = CustomJukebox.getInstance();
        if (plugin == null) {
            return null;
        }
        return new CustomJukeboxAPI(plugin);
    }

    // ═══════════════════════════════════════════════════════════
    // Disc Management
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets all registered custom discs.
     * @return Collection of all custom discs
     */
    public Collection<CustomDisc> getAllDiscs() {
        return plugin.getDiscManager().getAllDiscs();
    }

    /**
     * Gets a custom disc by its ID.
     * @param id Disc ID (e.g., "epic_journey")
     * @return CustomDisc or null if not found
     */
    public CustomDisc getDisc(String id) {
        return plugin.getDiscManager().getDisc(id);
    }

    /**
     * Gets a custom disc from an ItemStack.
     * @param item ItemStack to check
     * @return CustomDisc or null if not a custom disc
     */
    public CustomDisc getDiscFromItem(ItemStack item) {
        return plugin.getDiscManager().getDiscFromItem(item);
    }

    /**
     * Gets a random custom disc.
     * @return Random CustomDisc or null if no discs available
     */
    public CustomDisc getRandomDisc() {
        return plugin.getDiscManager().getRandomDisc();
    }

    /**
     * Checks if an ItemStack is a custom disc.
     * @param item ItemStack to check
     * @return true if it's a custom disc
     */
    public boolean isCustomDisc(ItemStack item) {
        return getDiscFromItem(item) != null;
    }

    // ═══════════════════════════════════════════════════════════
    // Fragment Management
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets all registered disc fragments.
     * @return Collection of all disc fragments
     */
    public Collection<DiscFragment> getAllFragments() {
        return plugin.getDiscManager().getAllFragments();
    }

    /**
     * Gets a fragment by disc ID.
     * @param discId Disc ID
     * @return DiscFragment or null if not found
     */
    public DiscFragment getFragment(String discId) {
        return plugin.getDiscManager().getFragment(discId);
    }

    /**
     * Gets a fragment from an ItemStack.
     * @param item ItemStack to check
     * @return DiscFragment or null if not a fragment
     */
    public DiscFragment getFragmentFromItem(ItemStack item) {
        return plugin.getDiscManager().getFragmentFromItem(item);
    }

    /**
     * Gets a random fragment.
     * @return Random DiscFragment or null if no fragments available
     */
    public DiscFragment getRandomFragment() {
        return plugin.getDiscManager().getRandomFragment();
    }

    /**
     * Checks if an ItemStack is a disc fragment.
     * @param item ItemStack to check
     * @return true if it's a disc fragment
     */
    public boolean isDiscFragment(ItemStack item) {
        return getFragmentFromItem(item) != null;
    }

    // ═══════════════════════════════════════════════════════════
    // Playback Control
    // ═══════════════════════════════════════════════════════════

    /**
     * Starts playing a custom disc at a location.
     * @param location Location to play at
     * @param disc CustomDisc to play
     */
    public void startPlayback(Location location, CustomDisc disc) {
        plugin.getPlaybackManager().startPlayback(location, disc);
    }

    /**
     * Starts playing a custom disc at a location with loop option.
     * @param location Location to play at
     * @param disc CustomDisc to play
     * @param loop Whether to loop the playback
     */
    public void startPlayback(Location location, CustomDisc disc, boolean loop) {
        plugin.getPlaybackManager().startPlayback(location, disc, loop);
    }

    /**
     * Starts playing a custom disc at a location with loop and range options.
     * @param location Location to play at
     * @param disc CustomDisc to play
     * @param loop Whether to loop the playback
     * @param range Playback range
     */
    public void startPlayback(Location location, CustomDisc disc, boolean loop, PlaybackRange range) {
        plugin.getPlaybackManager().startPlayback(location, disc, loop, range);
    }

    /**
     * Stops playback at a location.
     * @param location Location to stop playback at
     */
    public void stopPlayback(Location location) {
        plugin.getPlaybackManager().stopPlayback(location);
    }

    /**
     * Checks if a location is currently playing.
     * @param location Location to check
     * @return true if playing
     */
    public boolean isPlaying(Location location) {
        return plugin.getPlaybackManager().isPlaying(location);
    }

    /**
     * Stops all active playbacks.
     */
    public void stopAllPlaybacks() {
        plugin.getPlaybackManager().stopAllPlaybacks();
    }

    /**
     * Restarts all active playbacks with current settings.
     * Useful for applying volume changes.
     */
    public void restartAllPlaybacks() {
        plugin.getPlaybackManager().restartAllPlaybacks();
    }

    // ═══════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets the current playback volume.
     * @return Volume (0.0 to 4.0)
     */
    public float getVolume() {
        return plugin.getConfigManager().getVolume();
    }

    /**
     * Sets the playback volume.
     * @param volume Volume (0.0 to 4.0)
     */
    public void setVolume(float volume) {
        plugin.getConfigManager().setVolume(volume);
    }

    /**
     * Checks if the plugin is enabled.
     * @return true if enabled
     */
    public boolean isEnabled() {
        return plugin.getConfigManager().isEnabled();
    }

    /**
     * Checks if GUI is enabled.
     * @return true if GUI is enabled
     */
    public boolean isGuiEnabled() {
        return plugin.getConfigManager().isGuiEnabled();
    }

    /**
     * Gets the configured language.
     * @return Language code (e.g., "en", "de")
     */
    public String getLanguage() {
        return plugin.getConfigManager().getLanguage();
    }

    // ═══════════════════════════════════════════════════════════
    // Integration
    // ═══════════════════════════════════════════════════════════

    /**
     * Checks if a player can use a jukebox at a location.
     * Considers WorldGuard and GriefPrevention permissions.
     * @param player Player to check
     * @param location Location to check
     * @return true if player can use jukebox
     */
    public boolean canUseJukebox(Player player, Location location) {
        return plugin.getIntegrationManager().canUseJukebox(player, location);
    }

    /**
     * Checks if WorldGuard integration is enabled.
     * @return true if WorldGuard is active
     */
    public boolean isWorldGuardEnabled() {
        return plugin.getIntegrationManager().isWorldGuardEnabled();
    }

    /**
     * Checks if GriefPrevention integration is enabled.
     * @return true if GriefPrevention is active
     */
    public boolean isGriefPreventionEnabled() {
        return plugin.getIntegrationManager().isGriefPreventionEnabled();
    }

    // ═══════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════

    /**
     * Reloads the plugin configuration.
     */
    public void reload() {
        plugin.reload();
    }

    /**
     * Gets the plugin version.
     * @return Plugin version string
     */
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Translates a message key to the configured language.
     * @param key Message key
     * @return Translated message
     */
    public String getMessage(String key) {
        return plugin.getLanguageManager().getMessage(key);
    }

    /**
     * Translates a message key with placeholders.
     * @param key Message key
     * @param placeholder Placeholder key
     * @param value Placeholder value
     * @return Translated message with replaced placeholder
     */
    public String getMessage(String key, String placeholder, String value) {
        return plugin.getLanguageManager().getMessage(key, placeholder, value);
    }

    // ═══════════════════════════════════════════════════════════
    // Data Access (for companion plugins)
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets the plugin's data folder.
     * Companion plugins can use this to locate disc sound files.
     * @return Plugin data folder
     */
    public java.io.File getPluginDataFolder() {
        return plugin.getDataFolder();
    }
}
