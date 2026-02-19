package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Main admin GUI for managing discs, playlists, and categories.
 */
public class AdminGUI implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, GUIContext> activeGUIs = new HashMap<>();
    private final Map<UUID, String> chatInputMode = new HashMap<>();

    public AdminGUI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main admin menu.
     */
    public void openMainMenu(Player player) {
        Inventory gui = InventoryUtil.createInventory(null, 27, "§6§lAdmin §8» §eMain Menu");

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
        Inventory gui = InventoryUtil.createInventory(null, 54, "§6§lAdmin §8» §eDisc Management");

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
        Inventory gui = InventoryUtil.createInventory(null, 54, "§b§lAdmin §8» §ePlaylist Management");

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
        Inventory gui = InventoryUtil.createInventory(null, 54, "§d§lAdmin §8» §eCategory Management");

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

        GUIContext context = activeGUIs.get(player.getUniqueId());
        if (context == null) return;

        // Only cancel if clicking in the top inventory (the GUI)
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else {
            // Allow shift-click from player inventory to be cancelled (prevents moving items to GUI)
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return; // Don't handle clicks in player's own inventory
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
                player.sendMessage("§aAdmin Panel closed");
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
            player.sendMessage("§7Enter new §ePlaylist ID §7in chat:");
            player.sendMessage("§8Example: epic_music");
            player.sendMessage("§8Type §ccancel §8to abort");
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
                // Delete playlist
                boolean success = plugin.getDiscManager().deletePlaylist(playlistId);
                if (success) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("playlist-deleted")
                        .replace("{playlist}", playlistId));
                    openPlaylistManagement(player); // Refresh
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
                // Delete category
                boolean success = plugin.getDiscManager().deleteCategory(categoryId);
                if (success) {
                    player.sendMessage("§aCategory deleted: §e" + categoryId);
                    openCategoryManagement(player); // Refresh
                } else {
                    player.sendMessage("§cFailed to delete category!");
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

        // Keep GUI context for potential re-opening
        // Only remove on certain conditions
    }

    public void cleanup(Player player) {
        activeGUIs.remove(player.getUniqueId());
        chatInputMode.remove(player.getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        if (event.isCancelled()) return; // Already handled by another GUI

        Player player = event.getPlayer();
        String mode = chatInputMode.get(player.getUniqueId());

        if (mode == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§cInput cancelled");
            chatInputMode.remove(player.getUniqueId());

            // Reopen playlist management
            SchedulerUtil.runPlayerTask(plugin, player, () -> openPlaylistManagement(player));
            return;
        }

        SchedulerUtil.runPlayerTask(plugin, player, () -> handleChatInput(player, mode, input));
    }

    private void handleChatInput(Player player, String mode, String input) {
        if (mode.equals("createPlaylist")) {
            String playlistId = input.toLowerCase().replace(" ", "_");

            // Create empty playlist with default name
            boolean success = plugin.getDiscManager().createPlaylist(playlistId, input, "Created via GUI");

            if (success) {
                player.sendMessage("§a✓ Playlist created: §e" + input);
                player.sendMessage("§7Use the GUI to add discs to the playlist");
            } else {
                player.sendMessage("§cPlaylist ID already exists!");
            }

            chatInputMode.remove(player.getUniqueId());

            // Reopen playlist management
            SchedulerUtil.runPlayerTaskLater(plugin, player, () -> openPlaylistManagement(player), 3L);
        }
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
}
