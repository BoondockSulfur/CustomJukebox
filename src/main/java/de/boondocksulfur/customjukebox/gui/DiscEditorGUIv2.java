package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.ColorUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully GUI-based disc editor (no chat input for editing).
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class DiscEditorGUIv2 implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, EditorContext> activeEditors = new ConcurrentHashMap<>();
    private final Map<UUID, String> chatInputMode = new ConcurrentHashMap<>();

    public DiscEditorGUIv2(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main disc editor.
     */
    public void openEditor(Player player, CustomDisc disc) {
        activeEditors.put(player.getUniqueId(), new EditorContext(disc.getId()));
        Inventory gui = createMainEditor(disc);
        player.openInventory(gui);
    }

    /**
     * Opens duration selector GUI.
     */
    private void openDurationSelector(Player player, String discId) {
        Inventory gui = InventoryUtil.createInventory(null, 54, "§a§lSelect Duration");

        // Preset durations
        int[] durations = {30, 60, 90, 120, 150, 180, 210, 240, 300, 360, 420, 480, 600};
        int slot = 10;

        for (int seconds : durations) {
            int minutes = seconds / 60;
            int secs = seconds % 60;

            ItemStack item = createEditorItem(Material.CLOCK,
                "§e" + seconds + " seconds",
                "§7= " + minutes + "m " + secs + "s",
                "",
                "§e§lClick to select");
            gui.setItem(slot++, item);
        }

        // Custom duration via chat
        ItemStack custom = createEditorItem(Material.WRITABLE_BOOK,
            "§6§lCustom Duration",
            "§7Enter custom value via chat",
            "",
            "§e§lClick to enter manually");
        gui.setItem(49, custom);

        // Back button
        ItemStack back = createEditorItem(Material.ARROW,
            "§7« Back",
            "§7Return to editor");
        gui.setItem(45, back);

        activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.DURATION_SELECTOR));
        player.openInventory(gui);
    }

    /**
     * Opens category selector GUI.
     */
    private void openCategorySelector(Player player, String discId) {
        Inventory gui = InventoryUtil.createInventory(null, 54, "§6§lSelect Category");

        // No category option
        ItemStack noCategory = createEditorItem(Material.BARRIER,
            "§8§lNo Category",
            "§7Remove category from disc",
            "",
            "§e§lClick to select");
        gui.setItem(4, noCategory);

        // Existing categories
        Collection<DiscCategory> categories = plugin.getDiscManager().getAllCategories();
        int slot = 10;

        for (DiscCategory cat : categories) {
            int discCount = plugin.getDiscManager().getDiscsByCategory(cat.getId()).size();
            ItemStack item = createEditorItem(Material.BOOKSHELF,
                "§e" + cat.getDisplayName(),
                "§7ID: §e" + cat.getId(),
                "§7Discs: §e" + discCount,
                "",
                "§e§lClick to select");
            gui.setItem(slot++, item);

            if (slot >= 35) break;
        }

        // Create new category
        ItemStack createNew = createEditorItem(Material.EMERALD,
            "§a§l+ Create New Category",
            "§7Enter category ID via chat",
            "",
            "§e§lClick to create");
        gui.setItem(49, createNew);

        // Back button
        ItemStack back = createEditorItem(Material.ARROW,
            "§7« Back",
            "§7Return to editor");
        gui.setItem(45, back);

        activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.CATEGORY_SELECTOR));
        player.openInventory(gui);
    }

    /**
     * Opens custom model data selector.
     */
    private void openModelDataSelector(Player player, String discId) {
        Inventory gui = InventoryUtil.createInventory(null, 54, "§c§lSelect Model Data");

        // Preset model data values
        int[] modelDataValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        int slot = 10;

        for (int value : modelDataValues) {
            ItemStack item = createEditorItem(Material.MUSIC_DISC_13,
                "§eModel Data: " + value,
                "§7Click to select",
                "",
                "§8Use this for custom textures");
            gui.setItem(slot++, item);

            if (slot >= 35) break;
        }

        // Custom value via chat
        ItemStack custom = createEditorItem(Material.WRITABLE_BOOK,
            "§6§lCustom Value",
            "§7Enter custom number via chat",
            "",
            "§e§lClick to enter manually");
        gui.setItem(49, custom);

        // Back button
        ItemStack back = createEditorItem(Material.ARROW,
            "§7« Back",
            "§7Return to editor");
        gui.setItem(45, back);

        activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MODEL_DATA_SELECTOR));
        player.openInventory(gui);
    }

    /**
     * Opens deletion confirmation dialog from external GUI (like AdminGUI).
     * Uses player.openInventory() to open a new GUI.
     */
    public void confirmDeleteFromExternal(Player player, CustomDisc disc) {
        Inventory gui = createDeleteConfirmationGUI(disc);
        activeEditors.put(player.getUniqueId(), new EditorContext(disc.getId(), EditorMode.CONFIRM_DELETE, true));
        player.openInventory(gui);
    }

    /**
     * Opens deletion confirmation dialog from internal navigation (within DiscEditor).
     * Uses inventory update pattern to avoid closing GUI.
     */
    public void confirmDelete(Player player, CustomDisc disc) {
        Inventory gui = createDeleteConfirmationGUI(disc);
        // Update inventory content without closing
        Inventory currentInv = player.getOpenInventory().getTopInventory();
        currentInv.setContents(gui.getContents());
        activeEditors.put(player.getUniqueId(), new EditorContext(disc.getId(), EditorMode.CONFIRM_DELETE));
    }

    /**
     * Creates the delete confirmation GUI inventory.
     */
    private Inventory createDeleteConfirmationGUI(CustomDisc disc) {
        Inventory gui = InventoryUtil.createInventory(null, 27, "§c§lDelete: " + disc.getDisplayName());

        // Disc preview
        gui.setItem(13, disc.createItemStack());

        // Confirm delete
        ItemStack confirm = createEditorItem(Material.RED_CONCRETE,
            "§c§l✖ CONFIRM DELETE",
            "§7This will permanently delete:",
            "§e" + disc.getDisplayName() + " §7(§e" + disc.getId() + "§7)",
            "",
            "§c§lWARNING: Cannot be undone!");
        gui.setItem(21, confirm);

        // Cancel
        ItemStack cancel = createEditorItem(Material.GREEN_CONCRETE,
            "§a§lCancel",
            "§7Keep this disc");
        gui.setItem(23, cancel);

        return gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        EditorContext context = activeEditors.get(player.getUniqueId());
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
        CustomDisc disc = plugin.getDiscManager().getDisc(context.discId);
        if (disc == null && context.mode != EditorMode.CONFIRM_DELETE) return;

        switch (context.mode) {
            case MAIN_EDITOR:
                handleMainEditorClick(player, disc, slot);
                break;
            case DURATION_SELECTOR:
                handleDurationSelectorClick(player, context.discId, slot, clicked);
                break;
            case CATEGORY_SELECTOR:
                handleCategorySelectorClick(player, context.discId, slot, clicked);
                break;
            case MODEL_DATA_SELECTOR:
                handleModelDataSelectorClick(player, context.discId, slot, clicked);
                break;
            case CONFIRM_DELETE:
                handleDeleteConfirmClick(player, context.discId, slot);
                break;
        }
    }

    private void handleMainEditorClick(Player player, CustomDisc disc, int slot) {
        switch (slot) {
            case 10: // Display Name
                player.closeInventory();
                MessageUtil.sendMessage(player, "&7Enter new &eDisplay Name &7in chat:");
                MessageUtil.sendMessage(player, "&8Colors: &7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
                MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
                chatInputMode.put(player.getUniqueId(), "displayName:" + disc.getId());
                break;
            case 11: // Author
                player.closeInventory();
                MessageUtil.sendMessage(player, "&7Enter new &eAuthor &7in chat:");
                MessageUtil.sendMessage(player, "&8Supports colors & gradients just like Display Name");
                MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
                chatInputMode.put(player.getUniqueId(), "author:" + disc.getId());
                break;
            case 12: // Sound Key
                player.closeInventory();
                MessageUtil.sendMessage(player, "&7Enter new &eSound Key &7in chat (format: namespace:sound_name):");
                MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
                chatInputMode.put(player.getUniqueId(), "soundKey:" + disc.getId());
                break;
            case 13: // Duration
                openDurationSelector(player, disc.getId());
                break;
            case 14: // Category
                openCategorySelector(player, disc.getId());
                break;
            case 15: // Model Data
                openModelDataSelector(player, disc.getId());
                break;
            case 49: // Delete
                confirmDelete(player, disc);
                break;
            case 45: // Back
                activeEditors.remove(player.getUniqueId());
                plugin.getAdminGUI().openDiscManagement(player);
                break;
            case 53: // Save & Close
                player.closeInventory();
                MessageUtil.sendMessage(player, "&a&l✓ All changes saved!");
                activeEditors.remove(player.getUniqueId());
                plugin.getAdminGUI().openDiscManagement(player);
                break;
        }
    }

    private void handleDurationSelectorClick(Player player, String discId, int slot, ItemStack clicked) {
        if (slot == 45) {
            // Back - stay in inventory, just change content
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null) {
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                Inventory newInv = createMainEditor(disc);
                currentInv.setContents(newInv.getContents());
                activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
            }
            return;
        }

        if (slot == 49) {
            // Custom input
            player.closeInventory();
            MessageUtil.sendMessage(player, "&7Enter &eDuration &7in seconds:");
            MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
            chatInputMode.put(player.getUniqueId(), "duration:" + discId);
            return;
        }

        // Extract duration from item name
        ItemMeta meta = clicked.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = ItemUtil.getDisplayName(meta);
            try {
                int seconds = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                plugin.getDiscManager().updateDiscField(discId, "durationTicks", seconds * 20);
                MessageUtil.sendMessage(player, "&a✓ Duration updated: &e" + seconds + " seconds");

                // Update inventory content without closing
                CustomDisc disc = plugin.getDiscManager().getDisc(discId);
                if (disc != null) {
                    Inventory currentInv = player.getOpenInventory().getTopInventory();
                    Inventory newInv = createMainEditor(disc);
                    currentInv.setContents(newInv.getContents());
                    activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private Inventory createMainEditor(CustomDisc disc) {
        Inventory gui = InventoryUtil.createInventory(null, 54, "§6§lEdit: §e" + disc.getId());

        // Display Name
        ItemStack displayName = createEditorItem(Material.NAME_TAG,
            "§e§lDisplay Name",
            "§7Current: §r" + disc.getDisplayName(),
            "",
            "§e§lClick to edit via chat");
        gui.setItem(10, displayName);

        // Author
        ItemStack author = createEditorItem(Material.WRITABLE_BOOK,
            "§b§lAuthor",
            "§7Current: §f" + disc.getAuthor(),
            "",
            "§e§lClick to edit via chat");
        gui.setItem(11, author);

        // Sound Key
        ItemStack soundKey = createEditorItem(Material.NOTE_BLOCK,
            "§d§lSound Key",
            "§7Current: §b" + disc.getSoundKey(),
            "",
            "§e§lClick to edit via chat");
        gui.setItem(12, soundKey);

        // Duration Selector
        int seconds = disc.getDurationSeconds();
        ItemStack duration = createEditorItem(Material.CLOCK,
            "§a§lDuration",
            "§7Current: §e" + seconds + " seconds",
            "",
            "§e§lClick to open duration selector");
        gui.setItem(13, duration);

        // Category Selector
        String categoryDisplay = disc.getCategory() != null ? disc.getCategory() : "§8None";
        ItemStack category = createEditorItem(Material.BOOKSHELF,
            "§6§lCategory",
            "§7Current: §e" + categoryDisplay,
            "",
            "§e§lClick to open category selector");
        gui.setItem(14, category);

        // Custom Model Data Selector
        ItemStack modelData = createEditorItem(Material.PAINTING,
            "§c§lCustom Model Data",
            "§7Current: §e" + disc.getCustomModelData(),
            "",
            "§7For custom disc textures",
            "",
            "§e§lClick to open number selector");
        gui.setItem(15, modelData);

        // Delete Button
        ItemStack delete = createEditorItem(Material.REDSTONE_BLOCK,
            "§c§l✖ Delete Disc",
            "§7Permanently delete this disc",
            "",
            "§c§lWARNING: Cannot be undone!");
        gui.setItem(49, delete);

        // Back Button
        ItemStack back = createEditorItem(Material.ARROW,
            "§7« Back to Disc Management",
            "§7Return to disc list");
        gui.setItem(45, back);

        // Save & Close Button
        ItemStack save = createEditorItem(Material.EMERALD,
            "§a§l✓ Save & Close",
            "§7All changes are auto-saved!");
        gui.setItem(53, save);

        return gui;
    }

    private void handleCategorySelectorClick(Player player, String discId, int slot, ItemStack clicked) {
        if (slot == 45) {
            // Back - stay in inventory, just change content
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null) {
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                Inventory newInv = createMainEditor(disc);
                currentInv.setContents(newInv.getContents());
                activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
            }
            return;
        }

        if (slot == 4) {
            // No category
            plugin.getDiscManager().updateDiscField(discId, "category", null);
            MessageUtil.sendMessage(player, "&a✓ Category removed");
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null) {
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                Inventory newInv = createMainEditor(disc);
                currentInv.setContents(newInv.getContents());
                activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
            }
            return;
        }

        if (slot == 49) {
            // Create new category
            player.closeInventory();
            MessageUtil.sendMessage(player, "&7Enter new &eCategory ID &7in chat:");
            MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
            chatInputMode.put(player.getUniqueId(), "newCategory:" + discId);
            return;
        }

        // Select existing category - extract from lore
        ItemMeta meta = clicked.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = ItemUtil.getLore(meta);
            for (String line : lore) {
                if (line.startsWith("§7ID: §e")) {
                    String catId = line.replace("§7ID: §e", "");
                    plugin.getDiscManager().updateDiscField(discId, "category", catId);
                    MessageUtil.sendMessage(player, "&a✓ Category set: &e" + catId);
                    CustomDisc disc = plugin.getDiscManager().getDisc(discId);
                    if (disc != null) {
                        Inventory currentInv = player.getOpenInventory().getTopInventory();
                        Inventory newInv = createMainEditor(disc);
                        currentInv.setContents(newInv.getContents());
                        activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
                    }
                    return;
                }
            }
        }
    }

    private void handleModelDataSelectorClick(Player player, String discId, int slot, ItemStack clicked) {
        if (slot == 45) {
            // Back - stay in inventory, just change content
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null) {
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                Inventory newInv = createMainEditor(disc);
                currentInv.setContents(newInv.getContents());
                activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
            }
            return;
        }

        if (slot == 49) {
            // Custom input
            player.closeInventory();
            MessageUtil.sendMessage(player, "&7Enter &eCustom Model Data &7value:");
            MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
            chatInputMode.put(player.getUniqueId(), "modelData:" + discId);
            return;
        }

        // Extract model data from item name
        ItemMeta meta = clicked.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = ItemUtil.getDisplayName(meta);
            try {
                int value = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                plugin.getDiscManager().updateDiscField(discId, "customModelData", value);
                MessageUtil.sendMessage(player, "&a✓ Model Data updated: &e" + value);

                // Update inventory content without closing
                CustomDisc disc = plugin.getDiscManager().getDisc(discId);
                if (disc != null) {
                    Inventory currentInv = player.getOpenInventory().getTopInventory();
                    Inventory newInv = createMainEditor(disc);
                    currentInv.setContents(newInv.getContents());
                    activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void handleDeleteConfirmClick(Player player, String discId, int slot) {
        EditorContext context = activeEditors.get(player.getUniqueId());
        boolean fromExternal = context != null && context.fromExternal;

        plugin.getLogger().info("Delete confirm click: player=" + player.getName() + ", slot=" + slot + ", fromExternal=" + fromExternal);

        if (slot == 21) {
            // Confirm delete
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc == null) {
                player.closeInventory();
                MessageUtil.sendMessage(player, "&cDisc not found!");
                activeEditors.remove(player.getUniqueId());
                return;
            }

            String discName = disc.getDisplayName();
            boolean success = plugin.getDiscManager().deleteDisc(discId);

            if (success) {
                player.closeInventory();
                MessageUtil.sendMessage(player, "&c&l✖ Disc deleted: &r" + discName);
                activeEditors.remove(player.getUniqueId());
                // Return to disc management
                SchedulerUtil.runPlayerTaskLater(plugin, player, () ->
                    plugin.getAdminGUI().openDiscManagement(player), 2L);
            } else {
                MessageUtil.sendMessage(player, "&cFailed to delete disc!");
                // Stay on confirmation screen
            }
        } else if (slot == 23) {
            // Cancel
            plugin.getLogger().info("Cancel button clicked - fromExternal=" + fromExternal);
            if (fromExternal) {
                // Return to AdminGUI Disc Management
                plugin.getLogger().info("Returning to AdminGUI Disc Management");
                player.closeInventory();
                activeEditors.remove(player.getUniqueId());
                SchedulerUtil.runPlayerTaskLater(plugin, player, () -> {
                    plugin.getLogger().info("Opening AdminGUI Disc Management for " + player.getName());
                    plugin.getAdminGUI().openDiscManagement(player);
                }, 1L);
            } else {
                // Go back to Disc Editor
                CustomDisc disc = plugin.getDiscManager().getDisc(discId);
                if (disc != null) {
                    Inventory currentInv = player.getOpenInventory().getTopInventory();
                    Inventory newInv = createMainEditor(disc);
                    currentInv.setContents(newInv.getContents());
                    activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
                } else {
                    player.closeInventory();
                    MessageUtil.sendMessage(player, "&cDisc not found!");
                    activeEditors.remove(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String mode = chatInputMode.get(player.getUniqueId());

        if (mode == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendMessage(player, "&cInput cancelled");
            String discId = mode.split(":")[1];
            chatInputMode.remove(player.getUniqueId());

            // Reopen editor without closing
            SchedulerUtil.runPlayerTask(plugin, player, () -> {
                CustomDisc disc = plugin.getDiscManager().getDisc(discId);
                if (disc != null) {
                    openEditor(player, disc);
                }
            });
            return;
        }

        SchedulerUtil.runPlayerTask(plugin, player, () -> handleChatInput(player, mode, input));
    }

    private void handleChatInput(Player player, String mode, String input) {
        String[] parts = mode.split(":");
        String field = parts[0];
        String discId = parts[1];

        CustomDisc disc = plugin.getDiscManager().getDisc(discId);
        if (disc == null) {
            MessageUtil.sendMessage(player, "&cDisc not found!");
            chatInputMode.remove(player.getUniqueId());
            return;
        }

        boolean success = true;
        switch (field) {
            case "displayName":
                plugin.getDiscManager().updateDiscField(discId, "displayName", input);
                MessageUtil.sendMessage(player, "&a✓ Display Name updated: &r" + input);
                break;
            case "author":
                plugin.getDiscManager().updateDiscField(discId, "author", input);
                MessageUtil.sendMessage(player, "&a✓ Author updated: &f" + input);
                break;
            case "soundKey":
                if (!input.contains(":")) {
                    MessageUtil.sendMessage(player, "&cInvalid format! Use: namespace:sound_name");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                    success = false;
                } else {
                    plugin.getDiscManager().updateDiscField(discId, "soundKey", input);
                    MessageUtil.sendMessage(player, "&a✓ Sound Key updated: &b" + input);
                }
                break;
            case "duration":
                try {
                    int seconds = Integer.parseInt(input);
                    if (seconds <= 0) {
                        MessageUtil.sendMessage(player, "&cDuration must be greater than 0!");
                        success = false;
                    } else {
                        plugin.getDiscManager().updateDiscField(discId, "durationTicks", seconds * 20);
                        MessageUtil.sendMessage(player, "&a✓ Duration updated: &e" + seconds + " seconds");
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "&cInvalid number!");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                    success = false;
                }
                break;
            case "modelData":
                try {
                    int value = Integer.parseInt(input);
                    if (value < 1) {
                        MessageUtil.sendMessage(player, "&cModel Data must be at least 1!");
                        success = false;
                    } else {
                        plugin.getDiscManager().updateDiscField(discId, "customModelData", value);
                        MessageUtil.sendMessage(player, "&a✓ Model Data updated: &e" + value);
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "&cInvalid number!");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                    success = false;
                }
                break;
            case "newCategory":
                String categoryId = input.toLowerCase().replace(" ", "_");
                // Create category if it doesn't exist
                boolean created = plugin.getDiscManager().createCategory(categoryId, input, "Created via GUI");
                if (created) {
                    MessageUtil.sendMessage(player, "&a✓ New category created: &e" + input);
                } else {
                    MessageUtil.sendMessage(player, "&e⚠ Category already exists: &e" + input);
                }
                // Assign category to disc
                plugin.getDiscManager().updateDiscField(discId, "category", categoryId);
                MessageUtil.sendMessage(player, "&a✓ Category assigned to disc");
                break;
        }

        chatInputMode.remove(player.getUniqueId());

        // Always reopen editor after input - use scheduler to ensure player is back from chat
        CustomDisc updatedDisc = plugin.getDiscManager().getDisc(discId);
        if (updatedDisc != null) {
            SchedulerUtil.runPlayerTaskLater(plugin, player, () -> openEditor(player, updatedDisc), 3L);
        }
    }

    public void cleanup(Player player) {
        activeEditors.remove(player.getUniqueId());
        chatInputMode.remove(player.getUniqueId());
    }

    private ItemStack createEditorItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemUtil.setDisplayName(meta, name);
            ItemUtil.setLore(meta, Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static class EditorContext {
        String discId;
        EditorMode mode;
        boolean fromExternal; // True if opened from AdminGUI, false if from within DiscEditor

        EditorContext(String discId) {
            this.discId = discId;
            this.mode = EditorMode.MAIN_EDITOR;
            this.fromExternal = false;
        }

        EditorContext(String discId, EditorMode mode) {
            this.discId = discId;
            this.mode = mode;
            this.fromExternal = false;
        }

        EditorContext(String discId, EditorMode mode, boolean fromExternal) {
            this.discId = discId;
            this.mode = mode;
            this.fromExternal = fromExternal;
        }
    }

    private enum EditorMode {
        MAIN_EDITOR,
        DURATION_SELECTOR,
        CATEGORY_SELECTOR,
        MODEL_DATA_SELECTOR,
        CONFIRM_DELETE
    }
}
