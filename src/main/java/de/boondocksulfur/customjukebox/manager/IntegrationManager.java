package de.boondocksulfur.customjukebox.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import de.boondocksulfur.customjukebox.CustomJukebox;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages third-party plugin integrations.
 * Supports:
 * - WorldGuard (region protection)
 * - GriefPrevention (claim protection)
 *
 * Soft-dependency approach: Plugin works without these, but uses them if available.
 */
public class IntegrationManager {

    private final CustomJukebox plugin;
    private boolean worldGuardEnabled;
    private boolean griefPreventionEnabled;

    // Plugin instances (null if not present)
    private WorldGuardPlugin worldGuard;
    private GriefPrevention griefPrevention;

    public IntegrationManager(CustomJukebox plugin) {
        this.plugin = plugin;
        detectIntegrations();
    }

    /**
     * Detects and initializes integrations with third-party plugins.
     */
    private void detectIntegrations() {
        // WorldGuard detection
        if (plugin.getConfigManager().isWorldGuardEnabled()) {
            Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (wgPlugin instanceof WorldGuardPlugin && wgPlugin.isEnabled()) {
                worldGuard = (WorldGuardPlugin) wgPlugin;
                worldGuardEnabled = true;
                plugin.getLogger().info("WorldGuard integration enabled!");
            } else {
                plugin.getLogger().info("WorldGuard not found (soft-dependency)");
            }
        }

        // GriefPrevention detection
        if (plugin.getConfigManager().isGriefPreventionEnabled()) {
            Plugin gpPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gpPlugin instanceof GriefPrevention && gpPlugin.isEnabled()) {
                griefPrevention = (GriefPrevention) gpPlugin;
                griefPreventionEnabled = true;
                plugin.getLogger().info("GriefPrevention integration enabled!");
            } else {
                plugin.getLogger().info("GriefPrevention not found (soft-dependency)");
            }
        }
    }

    /**
     * Checks if a player can use a jukebox at a location.
     * Considers WorldGuard regions and GriefPrevention claims.
     *
     * @param player Player to check
     * @param location Jukebox location
     * @return true if player can use jukebox
     */
    public boolean canUseJukebox(Player player, Location location) {
        // OPs always have permission
        if (player.isOp()) {
            return true;
        }

        // Check WorldGuard
        if (worldGuardEnabled && !canUseJukeboxWorldGuard(player, location)) {
            return false;
        }

        // Check GriefPrevention
        if (griefPreventionEnabled && !canUseJukeboxGriefPrevention(player, location)) {
            return false;
        }

        return true; // No restrictions or all checks passed
    }

    /**
     * Checks WorldGuard permission for jukebox use.
     * Requires USE flag in the region.
     *
     * @param player Player
     * @param location Location
     * @return true if allowed
     */
    private boolean canUseJukeboxWorldGuard(Player player, Location location) {
        if (!worldGuardEnabled || worldGuard == null) {
            return true;
        }

        try {
            LocalPlayer localPlayer = worldGuard.wrapPlayer(player);
            com.sk89q.worldedit.util.Location wgLocation = BukkitAdapter.adapt(location);

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            // Check USE flag (required to interact with jukeboxes)
            if (!query.testState(wgLocation, localPlayer, Flags.USE)) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("WorldGuard: Player " + player.getName() +
                        " denied jukebox use at " + location);
                }
                return false;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("WorldGuard integration error: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
            return true; // Fail-safe: allow usage
        }
    }

    /**
     * Checks GriefPrevention permission for jukebox use.
     * Requires at least container trust in the claim.
     *
     * @param player Player
     * @param location Location
     * @return true if allowed
     */
    private boolean canUseJukeboxGriefPrevention(Player player, Location location) {
        if (!griefPreventionEnabled || griefPrevention == null) {
            return true;
        }

        try {
            Claim claim = griefPrevention.dataStore.getClaimAt(location, false, null);

            // No claim at location = wilderness = allowed
            if (claim == null) {
                return true;
            }

            // Check if player has container trust (required to use jukeboxes)
            String errorMessage = claim.allowContainers(player);
            if (errorMessage != null) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("GriefPrevention: Player " + player.getName() +
                        " denied jukebox use at " + location + " - " + errorMessage);
                }
                return false;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("GriefPrevention integration error: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
            return true; // Fail-safe: allow usage
        }
    }

    /**
     * Checks if WorldGuard integration is enabled and available.
     * @return true if WorldGuard is active
     */
    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    /**
     * Checks if GriefPrevention integration is enabled and available.
     * @return true if GriefPrevention is active
     */
    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

    /**
     * Gets a summary of active integrations.
     * @return Summary string
     */
    public String getIntegrationSummary() {
        StringBuilder summary = new StringBuilder("Active integrations: ");

        if (!worldGuardEnabled && !griefPreventionEnabled) {
            summary.append("None");
        } else {
            if (worldGuardEnabled) {
                summary.append("WorldGuard ");
            }
            if (griefPreventionEnabled) {
                summary.append("GriefPrevention ");
            }
        }

        return summary.toString();
    }

    /**
     * Reloads integration settings.
     */
    public void reload() {
        worldGuardEnabled = false;
        griefPreventionEnabled = false;
        worldGuard = null;
        griefPrevention = null;
        detectIntegrations();
    }
}
