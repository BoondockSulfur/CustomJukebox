package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main admin GUI for managing discs, playlists, and categories.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class AdminGUI implements Listener {

    private static final long DELETE_CONFIRM_TIMEOUT_MILLIS = 10_000L;

    private final CustomJukebox plugin;
    private final Map<UUID, GUIContext> activeGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, String> chatInputMode = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDelete> pendingDeletes = new ConcurrentHashMap<>();

    public AdminGUI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main admin menu.
     */
    public void openMainMenu(Player player) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 27, "§6§lAdmin §8» §eMain Menu");

        // Disc Management
        ItemStack discMgmt = createMenuItem(Material.MUSIC_DISC_13, "§6§lDisc Management",
            "§7Click to manage discs",
            "§8» §eCreate new discs",
            "§8» §eEdit existing discs",
            "§8» §eDelete discs");
        gui.setItem(11, discMgmt);

        // Playlist Management
        ItemStack playlistMgmt = createMenuItem(Material.NOTE_BLOCK, "§b§lPlaylist Management",
            "§7Click to manage playlists",
            "§8» §eCreate playlists",
            "§8» §eEdit playlists",
            "§8» §eDelete playlists");
        gui.setItem(13, playlistMgmt);

        // Category Management
        ItemStack categoryMgmt = createMenuItem(Material.BOOKSHELF, "§d§lCategory Management",
            "§7Click to manage categories",
            "§8» §eCreate categories",
            "§8» §eEdit categories",
            "§8» §eDelete categories");
        gui.setItem(15, categoryMgmt);

        // Exit button
        ItemStack exit = createMenuItem(Material.BARRIER, "§c§lExit Admin Panel",
            "§7Close and return to game");
        gui.setItem(22, exit);

        activeGUIs.put(player.getUniqueId(), new GUIContext(GUIType.MAIN_MENU));
        player.openInventory(gui);
    }

    /**
     * Opens the disc management menu.
     */
    public void openDiscManagement(Player player) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§6§lAdmin §8» §eDisc Management");

        // Add "Create New Disc" button
        ItemStack createNew = createMenuItem(Material.EMERALD, "§a§l+ Create New Disc",
            "§7Click to create a new custom disc",
            "§8Opens disc configuration GUI");
        gui.setItem(4, createNew);

        // List all existing discs
        Collection<CustomDisc> discs = plugin.getDiscManager().getAllDiscs();
        int slot = 9;

        for (CustomDisc disc : discs) {
            if (slot >= 45) break;

            ItemStack item = disc.createItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = ItemUtil.getLore(meta);
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add("§e§lLeft-Click: §7Edit disc");
                lore.add("§c§lRight-Click: §7Delete disc");
                ItemUtil.setLore(meta, lore);
                item.setItemMeta(meta);
            }

            gui.setItem(slot++, item);
        }

        // Back button
        addBackButton(gui, 49);

        activeGUIs.put(player.getUniqueId(), new GUIContext(GUIType.DISC_MANAGEMENT));
        player.openInventory(gui);
    }

    /**
     * Opens the playlist management menu.
     */
    private void openPlaylistManagement(Player player) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§b§lAdmin §8» §ePlaylist Management");

        // Add "Create New Playlist" button
        ItemStack createNew = createMenuItem(Material.EMERALD, "§a§l+ Create New Playlist",
            "§7Click to create a new playlist",
            "§8Opens playlist configuration");
        gui.setItem(4, createNew);

        // List all existing playlists
        Collection<DiscPlaylist> playlists = plugin.getDiscManager().getAllPlaylists();
        int slot = 9;

        for (DiscPlaylist playlist : playlists) {
            if (slot >= 45) break;

            ItemStack item = createMenuItem(Material.NOTE_BLOCK,
                "§b§l" + playlist.getDisplayName(),
                "§7ID: §e" + playlist.getId(),
                "§7Description: §e" + playlist.getDescription(),
                "§7Discs: §e" + playlist.getDiscCount(),
                "",
                "§e§lLeft-Click: §7Edit playlist",
                "§c§lRight-Click: §7Delete playlist");

            gui.setItem(slot++, item);
        }

        // Back button
        addBackButton(gui, 49);

        activeGUIs.put(player.getUniqueId(), new GUIContext(GUIType.PLAYLIST_MANAGEMENT));
        player.openInventory(gui);
    }

    /**
     * Opens the category management menu.
     */
    public void openCategoryManagement(Player player) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§d§lAdmin §8» §eCategory Management");

        // Add "Create New Category" button
        ItemStack createNew = createMenuItem(Material.EMERALD, "§a§l+ Create New Category",
            "§7Click to create a new category",
            "§8Organize discs by theme");
        gui.setItem(4, createNew);

        // List all existing categories
        Collection<DiscCategory> categories = plugin.getDiscManager().getAllCategories();
        int slot = 9;

        for (DiscCategory category : categories) {
            if (slot >= 45) break;

            int discCount = plugin.getDiscManager().getDiscsByCategory(category.getId()).size();
            ItemStack item = createMenuItem(Material.BOOKSHELF,
                "§d§l" + category.getDisplayName(),
                "§7ID: §e" + category.getId(),
                "§7Description: §e" + category.getDescription(),
                "§7Discs: §e" + discCount,
                "",
                "§e§lLeft-Click: §7Edit category",
                "§c§lRight-Click: §7Delete category");

            gui.setItem(slot++, item);
        }

        // Back button
        addBackButton(gui, 49);

        activeGUIs.put(player.getUniqueId(), new GUIContext(GUIType.CATEGORY_MANAGEMENT));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Only handle clicks while one of our own inventories is open
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;

        // Cancel FIRST - our GUI must never hand out items, even if the
        // session context is missing for some reason
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else {
            // Cancel actions that move items from the player inventory into the GUI
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return; // Don't handle clicks in player's own inventory
        }

        GUIContext context = activeGUIs.get(player.getUniqueId());
        if (context == null) return;

        // Permission check - ensure player still has admin permission
        if (!player.hasPermission("customjukebox.admin")) {
            activeGUIs.remove(player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou no longer have permission to use the admin panel!");
            // Close next tick - closeInventory() inside a click handler is undefined behavior
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();

        switch (context.type) {
            case MAIN_MENU:
                handleMainMenuClick(player, slot);
                break;
            case DISC_MANAGEMENT:
                handleDiscManagementClick(player, slot, clicked, event.isRightClick());
                break;
            case PLAYLIST_MANAGEMENT:
                handlePlaylistManagementClick(player, slot, clicked, event.isRightClick());
                break;
            case CATEGORY_MANAGEMENT:
                handleCategoryManagementClick(player, slot, clicked, event.isRightClick());
                break;
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 11: // Disc Management
                openDiscManagement(player);
                break;
            case 13: // Playlist Management
                openPlaylistManagement(player);
                break;
            case 15: // Category Management
                openCategoryManagement(player);
                break;
            case 22: // Exit
                player.closeInventory();
                activeGUIs.remove(player.getUniqueId());
                MessageUtil.sendMessage(player, "&aAdmin Panel closed");
                break;
        }
    }

    private void handleDiscManagementClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        if (slot == 4) {
            // Create new disc
            player.closeInventory();
            plugin.getDiscCreationWizard().startWizard(player);
            return;
        }

        if (slot == 49) {
            // Back button
            openMainMenu(player);
            return;
        }

        if (slot >= 9 && slot < 45) {
            CustomDisc disc = plugin.getDiscManager().getDiscFromItem(clicked);
            if (disc == null) return;

            if (rightClick) {
                // Delete disc - confirmDeleteFromExternal opens new GUI
                plugin.getDiscEditorGUIv2().confirmDeleteFromExternal(player, disc);
            } else {
                // Edit disc - openEditor opens new GUI
                plugin.getDiscEditorGUIv2().openEditor(player, disc);
            }
        }
    }

    private void handlePlaylistManagementClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        if (slot == 4) {
            // Create new playlist - prompt for ID via chat
            player.closeInventory();
            MessageUtil.sendMessage(player, "&7Enter new &ePlaylist ID &7in chat:");
            MessageUtil.sendMessage(player, "&8Example: epic_music");
            MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
            chatInputMode.put(player.getUniqueId(), "createPlaylist");
            return;
        }

        if (slot == 49) {
            // Back button
            openMainMenu(player);
            return;
        }

        if (slot >= 9 && slot < 45) {
            // Extract playlist from lore
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || ItemUtil.getLore(meta) == null) return;

            String playlistId = extractPlaylistIdFromLore(ItemUtil.getLore(meta));
            if (playlistId == null) return;

            DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);
            if (playlist == null) return;

            if (rightClick) {
                // Delete playlist (needs a second right-click to confirm)
                if (!consumePendingDelete(player, "playlist:" + playlistId)) return;
                boolean success = plugin.getDiscManager().deletePlaylist(playlistId);
                if (success) {
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("playlist-deleted")
                        .replace("{playlist}", playlistId));
                    openPlaylistManagement(player); // Refresh
                } else {
                    MessageUtil.sendMessage(player, "&cFailed to delete playlist!");
                }
            } else {
                // Edit playlist - openEditor handles GUI opening
                plugin.getPlaylistEditorGUI().openEditor(player, playlist);
            }
        }
    }

    private void handleCategoryManagementClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        if (slot == 4) {
            // Create new category
            player.closeInventory();
            plugin.getCategoryCreationWizard().startWizard(player);
            return;
        }

        if (slot == 49) {
            // Back button
            openMainMenu(player);
            return;
        }

        if (slot >= 9 && slot < 45) {
            // Extract category from lore
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || ItemUtil.getLore(meta) == null) return;

            String categoryId = extractCategoryIdFromLore(ItemUtil.getLore(meta));
            if (categoryId == null) return;

            if (rightClick) {
                // Delete category (needs a second right-click to confirm)
                if (!consumePendingDelete(player, "category:" + categoryId)) return;
                boolean success = plugin.getDiscManager().deleteCategory(categoryId);
                if (success) {
                    MessageUtil.sendMessage(player, "&aCategory deleted: &e" + categoryId);
                    openCategoryManagement(player); // Refresh
                } else {
                    MessageUtil.sendMessage(player, "&cFailed to delete category!");
                }
            } else {
                // Edit category
                player.closeInventory();
                plugin.getCategoryEditorGUI().openEditor(player, categoryId);
            }
        }
    }

    private String extractPlaylistIdFromLore(List<String> lore) {
        for (String line : lore) {
            if (line.startsWith("§7ID: §e")) {
                return line.replace("§7ID: §e", "");
            }
        }
        return null;
    }

    private String extractCategoryIdFromLore(List<String> lore) {
        for (String line : lore) {
            if (line.startsWith("§7ID: §e")) {
                return line.replace("§7ID: §e", "");
            }
        }
        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Only react to our own inventories; keep the context while navigating
        // between our menus (closing is caused by opening the next inventory)
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        activeGUIs.remove(player.getUniqueId());
        pendingDeletes.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryUtil.cancelDragIntoGui(event, this);
    }

    public void cleanup(Player player) {
        activeGUIs.remove(player.getUniqueId());
        chatInputMode.remove(player.getUniqueId());
        pendingDeletes.remove(player.getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        if (event.isCancelled()) return; // Already handled by another GUI

        Player player = event.getPlayer();
        // Remove atomically so rapid consecutive messages are not processed twice
        String mode = chatInputMode.remove(player.getUniqueId());

        if (mode == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendMessage(player, "&cInput cancelled");

            // Reopen playlist management
            SchedulerUtil.runPlayerTask(plugin, player, () -> openPlaylistManagement(player));
            return;
        }

        SchedulerUtil.runPlayerTask(plugin, player, () -> handleChatInput(player, mode, input));
    }

    private void handleChatInput(Player player, String mode, String input) {
        // Re-check permission - it may have been revoked mid-input
        if (!player.hasPermission("customjukebox.admin")) {
            MessageUtil.sendMessage(player, "&cYou no longer have permission to use the admin panel!");
            return;
        }

        if (mode.equals("createPlaylist")) {
            String playlistId = input.toLowerCase().replace(" ", "_");

            if (!InputValidator.isValidPlaylistId(playlistId)) {
                MessageUtil.sendMessage(player, "&cInvalid playlist ID! Use letters, numbers, - and _ (max "
                    + InputValidator.MAX_PLAYLIST_ID_LENGTH + " characters)");
                SchedulerUtil.runPlayerTaskLater(plugin, player, () -> openPlaylistManagement(player), 3L);
                return;
            }

            // Create empty playlist with default name
            boolean success = plugin.getDiscManager().createPlaylist(playlistId, input, "Created via GUI");

            if (success) {
                MessageUtil.sendMessage(player, "&a✓ Playlist created: &e" + input);
                MessageUtil.sendMessage(player, "&7Use the GUI to add discs to the playlist");
            } else {
                MessageUtil.sendMessage(player, "&cPlaylist ID already exists!");
            }

            // Reopen playlist management
            SchedulerUtil.runPlayerTaskLater(plugin, player, () -> openPlaylistManagement(player), 3L);
        }
    }

    /**
     * Two-step delete confirmation: the first right-click arms the deletion,
     * a second right-click on the same entry within the timeout confirms it.
     * @return true if the deletion is confirmed and should be executed
     */
    private boolean consumePendingDelete(Player player, String deleteKey) {
        PendingDelete pending = pendingDeletes.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (pending != null && pending.key.equals(deleteKey) && now < pending.expiresAt) {
            pendingDeletes.remove(player.getUniqueId());
            return true;
        }

        pendingDeletes.put(player.getUniqueId(), new PendingDelete(deleteKey, now + DELETE_CONFIRM_TIMEOUT_MILLIS));
        MessageUtil.sendMessage(player, "&e⚠ Right-click again within " + (DELETE_CONFIRM_TIMEOUT_MILLIS / 1000) + " seconds to confirm deletion!");
        return false;
    }

    // Helper methods

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemUtil.setDisplayName(meta, name);
            ItemUtil.setLore(meta, Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addBackButton(Inventory gui, int slot) {
        ItemStack back = createMenuItem(Material.ARROW, "§c§lBack",
            "§7Return to main menu");
        gui.setItem(slot, back);
    }

    // Context tracking

    private static class GUIContext {
        GUIType type;

        GUIContext(GUIType type) {
            this.type = type;
        }
    }

    private enum GUIType {
        MAIN_MENU,
        DISC_MANAGEMENT,
        PLAYLIST_MANAGEMENT,
        CATEGORY_MANAGEMENT
    }

    private static class PendingDelete {
        final String key;
        final long expiresAt;

        PendingDelete(String key, long expiresAt) {
            this.key = key;
            this.expiresAt = expiresAt;
        }
    }
}
