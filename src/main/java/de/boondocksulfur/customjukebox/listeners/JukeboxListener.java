package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.GuiPageUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles jukebox interaction and custom disc playback.
 * Manages:
 * - Inserting custom discs into jukeboxes
 * - Ejecting discs and stopping playback
 * - GUI for disc selection (if enabled)
 */
public class JukeboxListener implements Listener {

    private final CustomJukebox plugin;

    // Disc selection GUI layout
    private static final int DISCS_PER_PAGE = 45;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_PAGE_INFO = 46;
    private static final int SLOT_ADMIN = 49;
    private static final int SLOT_NEXT_PAGE = 53;

    // Track jukebox location for GUI selections (replaces deprecated FixedMetadataValue)
    private final Map<UUID, Location> playerJukeboxLocations = new ConcurrentHashMap<>();

    // Current page of the disc selection GUI, per viewer
    private final Map<UUID, Integer> discGuiPage = new ConcurrentHashMap<>();

    // Track recent disc changes to prevent race conditions (using String keys for reliability;
    // concurrent map because Folia region threads may touch different jukeboxes in parallel)
    private final Map<String, Long> recentDiscChanges = new ConcurrentHashMap<>();
    private static final long DISC_CHANGE_COOLDOWN_MS = 500; // 500ms cooldown between disc changes

    // Constants for jukebox timing - improved with more attempts
    private static final int VANILLA_SOUND_STOP_INITIAL_DELAY = 1; // Ticks
    private static final int VANILLA_SOUND_STOP_SECOND_DELAY = 5; // Ticks
    private static final int VANILLA_SOUND_STOP_THIRD_DELAY = 10; // Ticks
    private static final int VANILLA_SOUND_STOP_FOURTH_DELAY = 20; // Ticks (1 second)

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

        // Step 1: Check basic jukebox permission
        if (!player.hasPermission("customjukebox.use")) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission-jukebox"));
            event.setCancelled(true);
            return;
        }

        // Step 2: Check region/claim protection (WorldGuard, GriefPrevention)
        de.boondocksulfur.customjukebox.manager.IntegrationManager.ProtectionResult protectionResult =
            plugin.getIntegrationManager().checkProtection(player, block.getLocation());
        if (protectionResult != de.boondocksulfur.customjukebox.manager.IntegrationManager.ProtectionResult.ALLOWED) {
            switch (protectionResult) {
                case DENIED_WORLDGUARD:
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission-region"));
                    break;
                case DENIED_GRIEFPREVENTION:
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission-claim"));
                    break;
                default:
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission"));
                    break;
            }
            event.setCancelled(true);
            return;
        }

        // The event fires once per hand - use the item of the hand that interacts,
        // so discs held in the off-hand are recognized as well
        ItemStack item = event.getItem();
        Jukebox jukebox = (Jukebox) block.getState();

        // Check if player is holding a disc
        if (item != null && item.getType().name().contains("MUSIC_DISC")) {
            handleDiscInsertion(event, player, block, jukebox, item);
            return;
        }

        // Empty-hand paths: only react to the main-hand event to avoid double handling
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Check if jukebox already has a disc (eject mode)
        ItemStack record = jukebox.getRecord();
        if (record != null && record.getType() != Material.AIR) {
            handleDiscEjection(block, jukebox);
            return;
        }

        // No disc in hand and jukebox is empty - open GUI if enabled.
        // If the off-hand holds a disc, skip the GUI so the separate off-hand
        // event can insert it (otherwise the disc would never be insertable,
        // or vanilla would insert it behind the open GUI).
        if (plugin.getConfigManager().isGuiEnabled()) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType().name().contains("MUSIC_DISC")) {
                return;
            }
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
            // Vanilla will eject the current disc - stop the custom sound too,
            // otherwise it keeps playing over the now-empty jukebox
            handleDiscEjection(block, jukebox);
            return;
        }

        // Check for recent disc changes to prevent race conditions
        Location loc = block.getLocation();
        String locationKey = de.boondocksulfur.customjukebox.model.JukeboxPlayback.getLocationKey(loc);
        Long lastChange = recentDiscChanges.get(locationKey);
        if (lastChange != null && (System.currentTimeMillis() - lastChange) < DISC_CHANGE_COOLDOWN_MS) {
            // Too soon after last disc change - ignore to prevent race conditions
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Disc change too rapid - ignoring to prevent race condition");
            }
            return;
        }

        // Check if this is a custom disc
        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(item);
        if (disc == null) {
            return; // Not a custom disc, let vanilla handle it
        }

        // Mark this location as having a recent disc change
        recentDiscChanges.put(locationKey, System.currentTimeMillis());

        // Clean up old entries after 2 seconds
        SchedulerUtil.runLater(plugin, loc, () -> {
            recentDiscChanges.remove(locationKey);
        }, 40L); // 2 seconds

        // Stop any existing playback IMMEDIATELY to prevent overlap
        // This is important for quick disc switches
        if (plugin.getPlaybackManager().isPlaying(block.getLocation())) {
            plugin.getPlaybackManager().stopPlayback(block.getLocation());
        }

        // Let vanilla insert the disc first, then play custom sound
        // We delay the sound playback by 1 tick to ensure the disc is inserted
        SchedulerUtil.runLater(plugin, block.getLocation(), () -> {
            // Verify disc was inserted
            Jukebox updatedJukebox = (Jukebox) block.getState();
            ItemStack insertedDisc = updatedJukebox.getRecord();
            if (insertedDisc != null && insertedDisc.getType() != Material.AIR) {
                // IMPORTANT: Re-identify the disc from the actual inserted item
                // This prevents playing the wrong disc if items were switched quickly
                CustomDisc actualDisc = plugin.getDiscManager().getDiscFromItem(insertedDisc);
                if (actualDisc != null) {
                    // Double-check: Stop any playback that might have started in the meantime
                    if (plugin.getPlaybackManager().isPlaying(block.getLocation())) {
                        plugin.getPlaybackManager().stopPlayback(block.getLocation());
                    }
                    startCustomPlayback(block, actualDisc);
                } else {
                    // The inserted disc is not a custom disc anymore, let vanilla handle it
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().warning("Disc switch detected - inserted disc is not custom");
                    }
                }
            }
        }, 1L);
    }

    /**
     * Handles ejecting a disc from the jukebox.
     */
    private void handleDiscEjection(Block block, Jukebox jukebox) {
        Location loc = block.getLocation();
        String locationKey = de.boondocksulfur.customjukebox.model.JukeboxPlayback.getLocationKey(loc);

        // Mark this location as having a recent disc change
        recentDiscChanges.put(locationKey, System.currentTimeMillis());

        // Clean up old entries after 2 seconds
        SchedulerUtil.runLater(plugin, loc, () -> {
            recentDiscChanges.remove(locationKey);
        }, 40L); // 2 seconds

        // Stop custom playback if active
        if (plugin.getPlaybackManager().isPlaying(loc)) {
            plugin.getPlaybackManager().stopPlayback(loc);
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

        // Final verification: Check if the jukebox still contains the expected disc
        // This prevents race conditions where the disc might have been swapped
        Jukebox jukebox = (Jukebox) block.getState();
        ItemStack currentRecord = jukebox.getRecord();
        if (currentRecord == null || currentRecord.getType() == Material.AIR) {
            // Jukebox is empty - disc was already ejected
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Disc ejected before playback could start");
            }
            return;
        }

        CustomDisc currentDisc = plugin.getDiscManager().getDiscFromItem(currentRecord);
        if (currentDisc == null || !currentDisc.getId().equals(disc.getId())) {
            // Different disc or no custom disc - abort
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Disc mismatch detected - expected: " + disc.getId() +
                    ", found: " + (currentDisc != null ? currentDisc.getId() : "vanilla"));
            }
            return;
        }

        // Stop vanilla playback server-side (prevents the Jukebox block entity from
        // periodically re-triggering the vanilla sound during custom playback)
        jukebox.stopPlaying();
        jukebox.update();

        // Also stop client-side vanilla sound for all nearby players
        stopVanillaSound(block, disc);

        // Second client-side stop after short delay to catch any delayed vanilla sound
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

        // Both announcements are configurable (playback.show-title / show-actionbar)
        boolean showTitle = plugin.getConfigManager().isShowTitleEnabled();
        boolean showActionbar = plugin.getConfigManager().isShowActionbarEnabled();
        if (!showTitle && !showActionbar) return;

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
                if (showTitle) {
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
                }

                if (showActionbar) {
                    // Override vanilla actionbar with custom message (needs small delay)
                    SchedulerUtil.runPlayerTaskLater(plugin, player, () -> {
                        player.sendActionBar(actionbarComponent);
                    }, 2L); // 2 ticks delay to override vanilla message
                }

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
     * Optimized to only check players within hearing radius.
     * @param block Jukebox block
     * @param disc CustomDisc that was inserted
     */
    private void stopVanillaSound(Block block, CustomDisc disc) {
        if (block.getWorld() == null) return;

        // Get vanilla sound name from disc type
        String vanillaSound = getVanillaSoundName(disc.getDiscType());
        if (vanillaSound == null) return;

        // Get hearing radius and calculate squared distance for performance
        int hearingRadius = plugin.getConfigManager().getJukeboxHearingRadius();
        double hearingRadiusSquared = hearingRadius * hearingRadius;

        // Get location once to avoid multiple calls
        org.bukkit.Location blockLocation = block.getLocation();

        // Only check players within a reasonable chunk distance
        int chunkRadius = (hearingRadius >> 4) + 1; // Convert blocks to chunks (divide by 16)
        int centerChunkX = blockLocation.getBlockX() >> 4;
        int centerChunkZ = blockLocation.getBlockZ() >> 4;

        // Stop vanilla sound for nearby players (optimized)
        for (Player player : block.getWorld().getPlayers()) {
            // Quick chunk distance check first
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;

            if (Math.abs(playerChunkX - centerChunkX) > chunkRadius ||
                Math.abs(playerChunkZ - centerChunkZ) > chunkRadius) {
                continue; // Player is too far away, skip distance calculation
            }

            // Use squared distance to avoid expensive sqrt calculation
            if (player.getLocation().distanceSquared(blockLocation) <= hearingRadiusSquared) {
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
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission"));
            return;
        }

        // Check integrations (WorldGuard, GriefPrevention)
        if (!plugin.getIntegrationManager().canUseJukebox(player, jukeboxBlock.getLocation())) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("no-permission-region"));
            return;
        }

        // Store jukebox location for later use (when player clicks a disc)
        playerJukeboxLocations.put(player.getUniqueId(), jukeboxBlock.getLocation());
        openDiscSelection(player, 0);
    }

    /**
     * Opens (or re-opens) the paged disc selection GUI.
     *
     * <p>Shared by the jukebox interaction and {@code /cjb gui}: whether a click
     * inserts a disc into a jukebox or hands it to the player is decided by
     * {@link #playerJukeboxLocations}, not by which code opened the inventory.
     *
     * @param player viewer
     * @param page zero-based page (clamped to the available range)
     */
    public void openDiscSelection(Player player, int page) {
        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (guiTitle == null || guiTitle.isEmpty()) {
            guiTitle = "Custom Jukebox"; // Fallback
        }

        List<CustomDisc> allDiscs = new ArrayList<>(plugin.getDiscManager().getAllDiscs());
        int pageCount = GuiPageUtil.pageCount(allDiscs.size(), DISCS_PER_PAGE);
        page = GuiPageUtil.clampPage(page, allDiscs.size(), DISCS_PER_PAGE);
        discGuiPage.put(player.getUniqueId(), page);

        Inventory gui = InventoryUtil.createGuiInventory(this, 54, guiTitle);

        int slot = 0;
        for (CustomDisc disc : GuiPageUtil.slice(allDiscs, page, DISCS_PER_PAGE)) {
            gui.setItem(slot++, decorate(disc, player));
        }

        gui.setItem(SLOT_PREV_PAGE, GuiPageUtil.previousButton(page));
        gui.setItem(SLOT_NEXT_PAGE, GuiPageUtil.nextButton(page, pageCount));
        gui.setItem(SLOT_PAGE_INFO, GuiPageUtil.pageIndicator(page, pageCount, allDiscs.size(), "discs"));

        // Admin shortcut, only in the command-opened variant
        if (!playerJukeboxLocations.containsKey(player.getUniqueId())
                && player.hasPermission("customjukebox.admin")) {
            ItemStack adminButton = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = adminButton.getItemMeta();
            if (meta != null) {
                ItemUtil.setDisplayName(meta, "§6§l⚙ Admin Panel");
                ItemUtil.setLore(meta,
                    "§7Manage discs, playlists & categories",
                    "",
                    "§e§lClick to open Admin GUI");
                adminButton.setItemMeta(meta);
            }
            gui.setItem(SLOT_ADMIN, adminButton);
        }

        player.openInventory(gui);
    }

    /**
     * Builds the GUI entry for a disc, marking it when the viewer favourited it.
     */
    private ItemStack decorate(CustomDisc disc, Player player) {
        ItemStack item = disc.createItemStack();
        boolean favorite = plugin.getPlayerPreferencesManager()
            .isFavorite(player.getUniqueId(), disc.getId());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = ItemUtil.getLore(meta);
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add("");
            lore.add(favorite ? "§6★ §7In your favourites" : "§8☆ Not a favourite");
            lore.add("§8Shift-click to " + (favorite ? "remove" : "add"));
            ItemUtil.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Identify our GUI via its holder - title comparison breaks with hex colors
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;

        event.setCancelled(true);

        // Only clicks inside the GUI itself count. Without this check a click on
        // a disc in the player's OWN inventory was handled as a menu selection
        // (inserting it into the jukebox, or handing out another copy).
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        // Paging (the admin button on SLOT_ADMIN is handled by GuiSubcommand)
        int slot = event.getSlot();
        if (slot == SLOT_PREV_PAGE || slot == SLOT_NEXT_PAGE) {
            int current = discGuiPage.getOrDefault(player.getUniqueId(), 0);
            openDiscSelection(player, slot == SLOT_PREV_PAGE ? current - 1 : current + 1);
            return;
        }
        if (slot == SLOT_PAGE_INFO || slot == SLOT_ADMIN) {
            return;
        }

        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(clicked);

        if (disc == null) return;

        // Shift-click toggles the favourite instead of selecting the disc
        if (event.isShiftClick() && player.hasPermission("customjukebox.favorite")) {
            boolean nowFavorite = plugin.getPlayerPreferencesManager()
                .toggleFavorite(player.getUniqueId(), disc.getId());
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage(nowFavorite ? "favorite-added" : "favorite-removed",
                    "disc", disc.getDisplayName()));
            // Redraw so the star updates without closing the menu
            event.getView().getTopInventory().setItem(slot, decorate(disc, player));
            return;
        }

        // Check if GUI was opened from jukebox (has metadata) or from command (no metadata)
        boolean hasJukeboxLocation = playerJukeboxLocations.containsKey(player.getUniqueId());

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
        // Get jukebox location from stored map
        org.bukkit.Location jukeboxLoc = playerJukeboxLocations.get(player.getUniqueId());

        // Validate jukebox location
        if (jukeboxLoc == null || jukeboxLoc.getBlock().getType() != Material.JUKEBOX) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("gui-jukebox-invalid"));
            playerJukeboxLocations.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        Jukebox jukebox = (Jukebox) jukeboxLoc.getBlock().getState();

        // Check if jukebox is empty
        ItemStack record = jukebox.getRecord();
        if (record != null && record.getType() != Material.AIR) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("gui-jukebox-occupied"));
            player.closeInventory();
            playerJukeboxLocations.remove(player.getUniqueId());
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
                MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("gui-no-permission-disc"));
                player.closeInventory();
                playerJukeboxLocations.remove(player.getUniqueId());
                return;
            }

            // Remove one disc from player's inventory
            discInInventory.setAmount(discInInventory.getAmount() - 1);
        }

        // Insert disc into jukebox (setRecord triggers vanilla playback automatically)
        jukebox.setRecord(disc.createItemStack());
        jukebox.stopPlaying(); // Immediately stop vanilla playback server-side
        jukebox.update();

        // Start custom playback
        startCustomPlayback(jukeboxLoc.getBlock(), disc);

        // Send success message
        String message = plugin.getLanguageManager().getMessage("gui-disc-inserted");
        message = message.replace("{disc}", disc.getDisplayName());
        MessageUtil.sendMessage(player, message);

        // Close inventory and cleanup metadata
        player.closeInventory();
        playerJukeboxLocations.remove(player.getUniqueId());
    }

    /**
     * Handles clicking a disc in the GUI when opened from /cjb gui command.
     * Gives the disc to the player if they have permission.
     */
    private void handleCommandGuiClick(Player player, CustomDisc disc) {
        // Check if player has permission to get discs
        if (!player.hasPermission("customjukebox.give")) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("gui-no-permission-disc"));
            player.closeInventory();
            return;
        }

        // Give the disc to the player
        player.getInventory().addItem(disc.createItemStack());

        String message = plugin.getLanguageManager().getMessage("disc-received");
        message = message.replace("{disc}", disc.getDisplayName());
        MessageUtil.sendMessage(player, message);

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

        // Identify our GUI via its holder - title comparison breaks with hex colors
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;
        // Paging re-opens the inventory; that close must not drop the context
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        // Remove metadata if player closes GUI without selecting a disc
        playerJukeboxLocations.remove(player.getUniqueId());
        discGuiPage.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryUtil.cancelDragIntoGui(event, this);
    }

    /**
     * Keeps custom playback in sync with hoppers:
     * - a hopper extracting the disc stops the custom sound
     * - a hopper inserting a custom disc starts the custom sound
     */
    @EventHandler(ignoreCancelled = true)
    public void onHopperMoveDisc(InventoryMoveItemEvent event) {
        // This event fires for every hopper transfer on the server - bail out
        // cheaply unless a music disc moves and a jukebox inventory is involved
        // (getType() avoids the block-state snapshot that getHolder() builds)
        if (!event.getItem().getType().name().contains("MUSIC_DISC")) return;
        if (event.getSource().getType() != org.bukkit.event.inventory.InventoryType.JUKEBOX
            && event.getDestination().getType() != org.bukkit.event.inventory.InventoryType.JUKEBOX) {
            return;
        }

        if (event.getSource().getHolder() instanceof Jukebox sourceJukebox) {
            Location loc = sourceJukebox.getLocation();
            if (!plugin.getPlaybackManager().isPlaying(loc)) return;

            Block block = sourceJukebox.getBlock();
            // Verify next tick: a higher-priority handler may still cancel the
            // transfer, in which case the disc stays in and playback continues
            SchedulerUtil.runLater(plugin, loc, () -> {
                if (!plugin.getPlaybackManager().isPlaying(loc)) return;
                if (block.getType() == Material.JUKEBOX) {
                    ItemStack rec = ((Jukebox) block.getState()).getRecord();
                    if (rec != null && rec.getType() != Material.AIR) {
                        return; // Transfer was cancelled - disc is still in
                    }
                }
                plugin.getPlaybackManager().stopPlayback(loc);
            }, 1L);
            return;
        }

        if (event.getDestination().getHolder() instanceof Jukebox destJukebox) {
            CustomDisc disc = plugin.getDiscManager().getDiscFromItem(event.getItem());
            if (disc == null) return;

            Block block = destJukebox.getBlock();
            // The disc lands in the jukebox after this event - verify next tick
            SchedulerUtil.runLater(plugin, block.getLocation(), () -> {
                if (block.getType() != Material.JUKEBOX) return;
                Jukebox updated = (Jukebox) block.getState();
                CustomDisc actual = plugin.getDiscManager().getDiscFromItem(updated.getRecord());
                if (actual != null && !plugin.getPlaybackManager().isPlaying(block.getLocation())) {
                    startCustomPlayback(block, actual);
                }
            }, 1L);
        }
    }

    /**
     * Handles player quit event to prevent memory leaks.
     * Removes the player from all active playback listeners and clears
     * every GUI/wizard session so chat input is not hijacked after rejoin.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove player from all active playbacks to prevent memory leak
        plugin.getPlaybackManager().removePlayerFromAllPlaybacks(player);

        // Detach from any ambient zone so listener sets don't leak
        plugin.getAmbientZoneManager().handleQuit(player);

        // Clean up any GUI metadata
        playerJukeboxLocations.remove(player.getUniqueId());
        discGuiPage.remove(player.getUniqueId());

        // Clear all GUI and wizard sessions
        plugin.getNowPlayingManager().cleanup(player);
        plugin.getAdminGUI().cleanup(player);
        plugin.getDiscEditorGUIv2().cleanup(player);
        plugin.getPlaylistEditorGUI().cleanup(player);
        plugin.getZoneEditorGUI().cleanup(player);
        plugin.getDiscCreationWizard().cleanup(player);
        plugin.getCategoryEditorGUI().cancelSession(player.getUniqueId());
        plugin.getCategoryCreationWizard().cancelSession(player.getUniqueId());
    }

}

