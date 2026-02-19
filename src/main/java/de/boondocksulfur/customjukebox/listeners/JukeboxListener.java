package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;

/**
 * Handles jukebox interaction and custom disc playback.
 * Manages:
 * - Inserting custom discs into jukeboxes
 * - Ejecting discs and stopping playback
 * - GUI for disc selection (if enabled)
 */
public class JukeboxListener implements Listener {

    private final CustomJukebox plugin;

    // Constants for jukebox timing
    private static final int VANILLA_SOUND_STOP_INITIAL_DELAY = 1; // Ticks
    private static final int VANILLA_SOUND_STOP_SECOND_DELAY = 5; // Ticks

    public JukeboxListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles jukebox interaction.
     * Priorities:
     * 1. If holding disc -> insert and play
     * 2. If jukebox has disc -> eject and stop
     * 3. If GUI enabled and empty hand -> open GUI
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJukeboxInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) return;

        Player player = event.getPlayer();

        // Check integrations (WorldGuard, GriefPrevention)
        if (!plugin.getIntegrationManager().canUseJukebox(player, block.getLocation())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no-permission"));
            event.setCancelled(true);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Jukebox jukebox = (Jukebox) block.getState();

        // Check if player is holding a disc
        if (item != null && item.getType().name().contains("MUSIC_DISC")) {
            handleDiscInsertion(event, player, block, jukebox, item);
            return;
        }

        // Check if jukebox already has a disc (eject mode)
        ItemStack record = jukebox.getRecord();
        if (record != null && record.getType() != Material.AIR) {
            handleDiscEjection(block, jukebox);
            return;
        }

        // No disc in hand and jukebox is empty - open GUI if enabled
        if (plugin.getConfigManager().isGuiEnabled()) {
            event.setCancelled(true);
            openJukeboxGui(player, block);
        }
    }

    /**
     * Handles inserting a disc into the jukebox.
     */
    private void handleDiscInsertion(PlayerInteractEvent event, Player player, Block block,
                                     Jukebox jukebox, ItemStack item) {
        // Check if jukebox already has a disc
        ItemStack record = jukebox.getRecord();
        if (record != null && record.getType() != Material.AIR) {
            return; // Vanilla will eject the current disc
        }

        // Check if this is a custom disc
        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(item);
        if (disc == null) {
            return; // Not a custom disc, let vanilla handle it
        }

        // Let vanilla insert the disc first, then play custom sound
        // We delay the sound playback by 1 tick to ensure the disc is inserted
        SchedulerUtil.runLater(plugin, block.getLocation(), () -> {
            // Verify disc was inserted
            Jukebox updatedJukebox = (Jukebox) block.getState();
            ItemStack insertedDisc = updatedJukebox.getRecord();
            if (insertedDisc != null && insertedDisc.getType() != Material.AIR) {
                // Disc was inserted successfully
                startCustomPlayback(block, disc);
            }
        }, 1L);
    }

    /**
     * Handles ejecting a disc from the jukebox.
     */
    private void handleDiscEjection(Block block, Jukebox jukebox) {
        // Stop custom playback if active
        if (plugin.getPlaybackManager().isPlaying(block.getLocation())) {
            plugin.getPlaybackManager().stopPlayback(block.getLocation());
        }
        // Vanilla will handle the disc ejection
    }

    /**
     * Starts custom sound playback for a disc.
     */
    private void startCustomPlayback(Block block, CustomDisc disc) {
        if (!disc.hasCustomSound()) {
            // No custom sound, let vanilla sound play
            return;
        }

        // Stop vanilla sound twice to ensure it's stopped (vanilla sound can start with slight delay)
        // First stop - immediately after insertion
        stopVanillaSound(block, disc);

        // Second stop - after short delay to catch any delayed vanilla sound start
        SchedulerUtil.runLater(plugin, block.getLocation(), () -> {
            stopVanillaSound(block, disc);
        }, VANILLA_SOUND_STOP_SECOND_DELAY);

        // Show custom disc title to nearby players (replaces vanilla display)
        showCustomDiscTitle(block, disc);

        // Start custom playback (this will play the sound from resource pack)
        plugin.getPlaybackManager().startPlayback(block.getLocation(), disc);
    }

    /**
     * Shows a custom title for the disc to all nearby players.
     * Replaces the vanilla "Now Playing: C418 - 13" message.
     * @param block Jukebox block
     * @param disc CustomDisc that was inserted
     */
    private void showCustomDiscTitle(Block block, CustomDisc disc) {
        if (block.getWorld() == null) return;

        // Create title and subtitle using Adventure API
        Component titleComponent = AdventureUtil.parseComponent(disc.getDisplayName());
        Component subtitleComponent = AdventureUtil.parseComponent("§7" + disc.getAuthor());

        // Create actionbar message (replaces vanilla "Now Playing" message)
        String actionbarText = plugin.getLanguageManager().getMessage("playback-now-playing") + " " + disc.getDisplayName();
        Component actionbarComponent = AdventureUtil.parseComponent(actionbarText);

        // Show to all nearby players
        int hearingRadius = plugin.getConfigManager().getJukeboxHearingRadius();
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getLocation().distance(block.getLocation()) <= hearingRadius) {
                // Show title for 3 seconds using Adventure API
                Title title = Title.title(
                    titleComponent,
                    subtitleComponent,
                    Title.Times.times(
                        Duration.ofMillis(500),  // fade in: 10 ticks = 500ms
                        Duration.ofMillis(3000), // stay: 60 ticks = 3000ms
                        Duration.ofMillis(500)   // fade out: 10 ticks = 500ms
                    )
                );
                player.showTitle(title);

                // Override vanilla actionbar with custom message (needs small delay)
                SchedulerUtil.runPlayerTaskLater(plugin, player, () -> {
                    player.sendActionBar(actionbarComponent);
                }, 2L); // 2 ticks delay to override vanilla message

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Showing disc title to " + player.getName() +
                        ": " + disc.getDisplayName() + " - " + disc.getAuthor());
                }
            }
        }
    }

    /**
     * Stops the vanilla disc sound for all nearby players.
     * This prevents double-playback (vanilla + custom sound).
     * @param block Jukebox block
     * @param disc CustomDisc that was inserted
     */
    private void stopVanillaSound(Block block, CustomDisc disc) {
        if (block.getWorld() == null) return;

        // Get vanilla sound name from disc type
        String vanillaSound = getVanillaSoundName(disc.getDiscType());
        if (vanillaSound == null) return;

        // Stop vanilla sound for all nearby players
        int hearingRadius = plugin.getConfigManager().getJukeboxHearingRadius();
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getLocation().distance(block.getLocation()) <= hearingRadius) {
                player.stopSound(vanillaSound, org.bukkit.SoundCategory.RECORDS);

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Stopped vanilla sound '" + vanillaSound +
                        "' for " + player.getName());
                }
            }
        }
    }

    /**
     * Gets the vanilla sound name for a disc material type.
     * @param discType Material type (e.g. MUSIC_DISC_13)
     * @return Vanilla sound name (e.g. "minecraft:music_disc.13")
     */
    private String getVanillaSoundName(org.bukkit.Material discType) {
        switch (discType) {
            case MUSIC_DISC_13: return "minecraft:music_disc.13";
            case MUSIC_DISC_CAT: return "minecraft:music_disc.cat";
            case MUSIC_DISC_BLOCKS: return "minecraft:music_disc.blocks";
            case MUSIC_DISC_CHIRP: return "minecraft:music_disc.chirp";
            case MUSIC_DISC_FAR: return "minecraft:music_disc.far";
            case MUSIC_DISC_MALL: return "minecraft:music_disc.mall";
            case MUSIC_DISC_MELLOHI: return "minecraft:music_disc.mellohi";
            case MUSIC_DISC_STAL: return "minecraft:music_disc.stal";
            case MUSIC_DISC_STRAD: return "minecraft:music_disc.strad";
            case MUSIC_DISC_WARD: return "minecraft:music_disc.ward";
            case MUSIC_DISC_11: return "minecraft:music_disc.11";
            case MUSIC_DISC_WAIT: return "minecraft:music_disc.wait";
            case MUSIC_DISC_OTHERSIDE: return "minecraft:music_disc.otherside";
            case MUSIC_DISC_5: return "minecraft:music_disc.5";
            case MUSIC_DISC_PIGSTEP: return "minecraft:music_disc.pigstep";
            case MUSIC_DISC_RELIC: return "minecraft:music_disc.relic";
            case MUSIC_DISC_CREATOR: return "minecraft:music_disc.creator";
            case MUSIC_DISC_CREATOR_MUSIC_BOX: return "minecraft:music_disc.creator_music_box";
            case MUSIC_DISC_PRECIPICE: return "minecraft:music_disc.precipice";
            default: return null;
        }
    }

    private void openJukeboxGui(Player player, Block jukeboxBlock) {
        if (!player.hasPermission("customjukebox.gui")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no-permission"));
            return;
        }

        // Check integrations (WorldGuard, GriefPrevention)
        if (!plugin.getIntegrationManager().canUseJukebox(player, jukeboxBlock.getLocation())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no-permission"));
            return;
        }

        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (guiTitle == null || guiTitle.isEmpty()) {
            guiTitle = "Custom Jukebox"; // Fallback
        }

        Inventory gui = InventoryUtil.createInventory(null, 54, guiTitle);

        int slot = 0;
        for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
            if (slot >= 54) break;
            gui.setItem(slot++, disc.createItemStack());
        }

        player.openInventory(gui);

        // Store jukebox location for later use (when player clicks a disc)
        player.setMetadata("jukebox_location", new org.bukkit.metadata.FixedMetadataValue(plugin,
            jukeboxBlock.getLocation()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (guiTitle == null || guiTitle.isEmpty()) {
            guiTitle = "Custom Jukebox"; // Fallback
        }

        String title = AdventureUtil.toLegacy(event.getView().title());
        if (!title.equals(guiTitle)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(clicked);

        if (disc == null) return;

        // Check if GUI was opened from jukebox (has metadata) or from command (no metadata)
        boolean hasJukeboxLocation = player.hasMetadata("jukebox_location");

        if (hasJukeboxLocation) {
            // GUI opened from jukebox - insert disc into jukebox
            handleJukeboxGuiClick(player, disc);
        } else {
            // GUI opened from command - give disc to player
            handleCommandGuiClick(player, disc);
        }
    }

    /**
     * Handles clicking a disc in the GUI when opened from a jukebox.
     * Inserts the disc into the jukebox.
     */
    private void handleJukeboxGuiClick(Player player, CustomDisc disc) {
        // Get jukebox location from metadata
        org.bukkit.Location jukeboxLoc = (org.bukkit.Location) player.getMetadata("jukebox_location").get(0).value();

        // Validate jukebox location
        if (jukeboxLoc == null || jukeboxLoc.getBlock().getType() != Material.JUKEBOX) {
            player.sendMessage(plugin.getLanguageManager().getMessage("gui-jukebox-invalid"));
            player.removeMetadata("jukebox_location", plugin);
            player.closeInventory();
            return;
        }

        Jukebox jukebox = (Jukebox) jukeboxLoc.getBlock().getState();

        // Check if jukebox is empty
        ItemStack record = jukebox.getRecord();
        if (record != null && record.getType() != Material.AIR) {
            player.sendMessage(plugin.getLanguageManager().getMessage("gui-jukebox-occupied"));
            player.closeInventory();
            player.removeMetadata("jukebox_location", plugin);
            return;
        }

        // Check if player has permission to get discs for free
        // Only admins with "customjukebox.give" permission can get free discs from GUI
        // Regular players need to have the specific disc in their inventory
        if (!player.hasPermission("customjukebox.give")) {
            // Find the specific disc in player's inventory (matching CustomModelData)
            ItemStack discInInventory = null;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && disc.matches(item)) {
                    discInInventory = item;
                    break;
                }
            }

            if (discInInventory == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("gui-no-permission-disc"));
                player.closeInventory();
                player.removeMetadata("jukebox_location", plugin);
                return;
            }

            // Remove one disc from player's inventory
            discInInventory.setAmount(discInInventory.getAmount() - 1);
        }

        // Insert disc into jukebox
        jukebox.setRecord(disc.createItemStack());
        jukebox.update();

        // Start custom playback
        startCustomPlayback(jukeboxLoc.getBlock(), disc);

        // Send success message
        String message = plugin.getLanguageManager().getMessage("gui-disc-inserted");
        message = message.replace("{disc}", disc.getDisplayName());
        player.sendMessage(message);

        // Close inventory and cleanup metadata
        player.closeInventory();
        player.removeMetadata("jukebox_location", plugin);
    }

    /**
     * Handles clicking a disc in the GUI when opened from /cjb gui command.
     * Gives the disc to the player if they have permission.
     */
    private void handleCommandGuiClick(Player player, CustomDisc disc) {
        // Check if player has permission to get discs
        if (!player.hasPermission("customjukebox.give")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("gui-no-permission-disc"));
            player.closeInventory();
            return;
        }

        // Give the disc to the player
        player.getInventory().addItem(disc.createItemStack());

        String message = plugin.getLanguageManager().getMessage("disc-received");
        message = message.replace("{disc}", disc.getDisplayName());
        player.sendMessage(message);

        player.closeInventory();
    }

    /**
     * Cleanup metadata when player closes the GUI without selecting a disc.
     * This prevents memory leaks from abandoned metadata.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (guiTitle == null || guiTitle.isEmpty()) {
            guiTitle = "Custom Jukebox"; // Fallback
        }

        String title = AdventureUtil.toLegacy(event.getView().title());
        if (!title.equals(guiTitle)) return;

        // Remove metadata if player closes GUI without selecting a disc
        if (player.hasMetadata("jukebox_location")) {
            player.removeMetadata("jukebox_location", plugin);
        }
    }
}

