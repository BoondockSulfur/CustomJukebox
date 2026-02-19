package de.boondocksulfur.customjukebox.integrations;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for CustomJukebox.
 * Provides placeholders for disc information and player statistics.
 *
 * <p>Available placeholders:
 * <ul>
 *   <li>%customjukebox_version% - Plugin version</li>
 *   <li>%customjukebox_total_discs% - Total number of custom discs</li>
 *   <li>%customjukebox_total_fragments% - Total number of disc fragments</li>
 *   <li>%customjukebox_hand_disc_name% - Name of disc in main hand</li>
 *   <li>%customjukebox_hand_disc_id% - ID of disc in main hand</li>
 *   <li>%customjukebox_hand_disc_author% - Author of disc in main hand</li>
 *   <li>%customjukebox_hand_disc_duration% - Duration of disc in main hand (in seconds)</li>
 *   <li>%customjukebox_language% - Current plugin language</li>
 *   <li>%customjukebox_gui_enabled% - Whether GUI is enabled</li>
 * </ul>
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final CustomJukebox plugin;

    public PlaceholderAPIExpansion(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "customjukebox";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getPluginMeta().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Required for the expansion to stay loaded
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Plugin info placeholders
        if (params.equalsIgnoreCase("version")) {
            return plugin.getPluginMeta().getVersion();
        }

        if (params.equalsIgnoreCase("total_discs")) {
            return String.valueOf(plugin.getDiscManager().getAllDiscs().size());
        }

        if (params.equalsIgnoreCase("total_fragments")) {
            return String.valueOf(plugin.getDiscManager().getAllFragments().size());
        }

        if (params.equalsIgnoreCase("language")) {
            return plugin.getConfigManager().getLanguage();
        }

        if (params.equalsIgnoreCase("gui_enabled")) {
            return plugin.getConfigManager().isGuiEnabled() ? "Yes" : "No";
        }

        // Player-specific placeholders (require online player)
        if (player == null || !player.isOnline()) {
            return null;
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return null;
        }

        // Disc in hand placeholders
        ItemStack handItem = onlinePlayer.getInventory().getItemInMainHand();
        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(handItem);

        if (params.equalsIgnoreCase("hand_disc_name")) {
            return disc != null ? disc.getDisplayName() : "None";
        }

        if (params.equalsIgnoreCase("hand_disc_id")) {
            return disc != null ? disc.getId() : "none";
        }

        if (params.equalsIgnoreCase("hand_disc_author")) {
            return disc != null ? disc.getAuthor() : "Unknown";
        }

        if (params.equalsIgnoreCase("hand_disc_duration")) {
            return disc != null ? String.valueOf(disc.getDurationSeconds()) : "0";
        }

        if (params.equalsIgnoreCase("hand_is_disc")) {
            return disc != null ? "Yes" : "No";
        }

        if (params.equalsIgnoreCase("hand_is_fragment")) {
            return plugin.getDiscManager().getFragmentFromItem(handItem) != null ? "Yes" : "No";
        }

        // Integration status
        if (params.equalsIgnoreCase("worldguard_enabled")) {
            return plugin.getIntegrationManager().isWorldGuardEnabled() ? "Yes" : "No";
        }

        if (params.equalsIgnoreCase("griefprevention_enabled")) {
            return plugin.getIntegrationManager().isGriefPreventionEnabled() ? "Yes" : "No";
        }

        // Active playback info
        if (params.equalsIgnoreCase("playback_at_location")) {
            return plugin.getPlaybackManager().isPlaying(onlinePlayer.getLocation()) ? "Yes" : "No";
        }

        if (params.equalsIgnoreCase("volume")) {
            return String.format("%.1f", plugin.getConfigManager().getVolume());
        }

        if (params.equalsIgnoreCase("is_muted")) {
            return plugin.getConfigManager().isMuted() ? "Yes" : "No";
        }

        return null; // Unknown placeholder
    }
}
