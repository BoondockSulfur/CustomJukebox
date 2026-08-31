package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.GuiPageUtil;
import de.boondocksulfur.customjukebox.utils.InputValidator;
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
 * Fully GUI-based disc editor (no chat input for editing).
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class DiscEditorGUIv2 implements Listener {

    /** First CustomModelData offered by the selector (matches disc.json defaults). */
    private static final int MODEL_DATA_PRESET_BASE = 1001;
    private static final int MODEL_DATA_PRESET_COUNT = 20;

    // Category selector layout
    private static final int CATEGORIES_PER_PAGE = 27;
    private static final int CAT_FIRST_SLOT = 9;
    private static final int CAT_LAST_SLOT = 36; // exclusive
    private static final int CAT_SLOT_NONE = 4;
    private static final int CAT_SLOT_BACK = 45;
    private static final int CAT_SLOT_PREV = 46;
    private static final int CAT_SLOT_PAGE = 48;
    private static final int CAT_SLOT_CREATE = 49;
    private static final int CAT_SLOT_NEXT = 52;

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
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§a§lSelect Duration");

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
        openCategorySelector(player, discId, 0);
    }

    private void openCategorySelector(Player player, String discId, int page) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§6§lSelect Category");

        // No category option
        ItemStack noCategory = createEditorItem(Material.BARRIER,
            "§8§lNo Category",
            "§7Remove category from disc",
            "",
            "§e§lClick to select");
        gui.setItem(CAT_SLOT_NONE, noCategory);

        // Existing categories (paged)
        List<DiscCategory> categories = new ArrayList<>(plugin.getDiscManager().getAllCategories());
        page = GuiPageUtil.clampPage(page, categories.size(), CATEGORIES_PER_PAGE);
        int slot = CAT_FIRST_SLOT;

        for (DiscCategory cat : GuiPageUtil.slice(categories, page, CATEGORIES_PER_PAGE)) {
            int discCount = plugin.getDiscManager().getDiscsByCategory(cat.getId()).size();
            ItemStack item = createEditorItem(Material.BOOKSHELF,
                "§e" + cat.getDisplayName(),
                "§7ID: §e" + cat.getId(),
                "§7Discs: §e" + discCount,
                "",
                "§e§lClick to select");
            item = ItemUtil.withPdcString(item, ItemUtil.CATEGORY_ID_KEY, cat.getId());
            gui.setItem(slot++, item);
        }

        // Create new category
        ItemStack createNew = createEditorItem(Material.EMERALD,
            "§a§l+ Create New Category",
            "§7Enter category ID via chat",
            "",
            "§e§lClick to create");
        gui.setItem(CAT_SLOT_CREATE, createNew);

        // Back button
        ItemStack back = createEditorItem(Material.ARROW,
            "§7« Back",
            "§7Return to editor");
        gui.setItem(CAT_SLOT_BACK, back);

        int pageCount = GuiPageUtil.pageCount(categories.size(), CATEGORIES_PER_PAGE);
        gui.setItem(CAT_SLOT_PREV, GuiPageUtil.previousButton(page));
        gui.setItem(CAT_SLOT_NEXT, GuiPageUtil.nextButton(page, pageCount));
        gui.setItem(CAT_SLOT_PAGE, GuiPageUtil.pageIndicator(page, pageCount, categories.size(), "categories"));

        activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.CATEGORY_SELECTOR, false, page));
        player.openInventory(gui);
    }

    /**
     * Opens custom model data selector.
     */
    private void openModelDataSelector(Player player, String discId) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§c§lSelect Model Data");

        // Presets follow the 1001+ convention used by the bundled disc.json and
        // the README. The old 1..20 list collided with nothing in a default
        // setup but silently broke the texture mapping of every shipped disc.
        int slot = 10;
        for (int value = MODEL_DATA_PRESET_BASE; value < MODEL_DATA_PRESET_BASE + MODEL_DATA_PRESET_COUNT; value++) {
            String owner = discUsingModelData(value, discId);
            ItemStack item = createEditorItem(Material.MUSIC_DISC_13,
                "§eModel Data: " + value,
                owner == null ? "§7Free" : "§cAlready used by §e" + owner,
                "",
                "§8Use this for custom textures",
                "§e§lClick to select");
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
        // Update inventory content without closing - but only if the open
        // inventory really is one of ours (never overwrite e.g. a chest)
        Inventory currentInv = player.getOpenInventory().getTopInventory();
        if (!GUIHolder.isOwnedBy(currentInv, this)) {
            confirmDeleteFromExternal(player, disc);
            return;
        }
        currentInv.setContents(gui.getContents());
        activeEditors.put(player.getUniqueId(), new EditorContext(disc.getId(), EditorMode.CONFIRM_DELETE));
    }

    /**
     * Creates the delete confirmation GUI inventory.
     */
    private Inventory createDeleteConfirmationGUI(CustomDisc disc) {
        Inventory gui = InventoryUtil.createGuiInventory(this, 27, "§c§lDelete: " + disc.getDisplayName());

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

        EditorContext context = activeEditors.get(player.getUniqueId());
        if (context == null) return;

        // Permission check - ensure player still has admin permission
        if (!player.hasPermission("customjukebox.admin")) {
            activeEditors.remove(player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou no longer have permission to edit discs!");
            // Close next tick - closeInventory() inside a click handler is undefined behavior
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
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
                handleCategorySelectorClick(player, context.discId, slot, clicked, context.page);
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
                // Spelled out rather than pointing at the Display Name prompt:
                // whoever opens this field first never sees that one.
                MessageUtil.sendMessage(player, "&8Colors: &7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
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
        Inventory gui = InventoryUtil.createGuiInventory(this, 54, "§6§lEdit: §e" + disc.getId());

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

    private void handleCategorySelectorClick(Player player, String discId, int slot, ItemStack clicked, int page) {
        if (slot == CAT_SLOT_PREV || slot == CAT_SLOT_NEXT) {
            openCategorySelector(player, discId, slot == CAT_SLOT_PREV ? page - 1 : page + 1);
            return;
        }

        if (slot == CAT_SLOT_BACK) {
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

        if (slot == CAT_SLOT_NONE) {
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

        if (slot == CAT_SLOT_CREATE) {
            // Create new category
            player.closeInventory();
            MessageUtil.sendMessage(player, "&7Enter new &eCategory ID &7in chat:");
            MessageUtil.sendMessage(player, "&8Type &ccancel &8to abort");
            chatInputMode.put(player.getUniqueId(), "newCategory:" + discId);
            return;
        }

        if (slot < CAT_FIRST_SLOT || slot >= CAT_LAST_SLOT) {
            return;
        }

        // Select existing category - resolved from item data, not from lore text
        String catId = ItemUtil.getPdcString(clicked, ItemUtil.CATEGORY_ID_KEY);
        if (catId == null) {
            return;
        }
        plugin.getDiscManager().updateDiscField(discId, "category", catId);
        MessageUtil.sendMessage(player, "&a✓ Category set: &e" + catId);
        CustomDisc disc = plugin.getDiscManager().getDisc(discId);
        if (disc != null) {
            Inventory currentInv = player.getOpenInventory().getTopInventory();
            Inventory newInv = createMainEditor(disc);
            currentInv.setContents(newInv.getContents());
            activeEditors.put(player.getUniqueId(), new EditorContext(discId, EditorMode.MAIN_EDITOR));
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

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Delete confirm click: player=" + player.getName() + ", slot=" + slot + ", fromExternal=" + fromExternal);
        }

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
            if (fromExternal) {
                // Return to AdminGUI Disc Management
                player.closeInventory();
                activeEditors.remove(player.getUniqueId());
                SchedulerUtil.runPlayerTaskLater(plugin, player, () ->
                    plugin.getAdminGUI().openDiscManagement(player), 1L);
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Only react to our own inventories; keep the context while navigating
        // between our menus (closing is caused by opening the next inventory)
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        activeEditors.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryUtil.cancelDragIntoGui(event, this);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return; // Already handled by another GUI

        Player player = event.getPlayer();
        // Remove atomically so rapid consecutive messages are not processed twice
        String mode = chatInputMode.remove(player.getUniqueId());

        if (mode == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendMessage(player, "&cInput cancelled");
            String[] cancelParts = mode.split(":");
            if (cancelParts.length < 2) {
                return;
            }
            String discId = cancelParts[1];

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
        // Re-check permission - it may have been revoked mid-input
        if (!player.hasPermission("customjukebox.admin")) {
            MessageUtil.sendMessage(player, "&cYou no longer have permission to edit discs!");
            return;
        }

        String[] parts = mode.split(":");
        if (parts.length < 2) {
            MessageUtil.sendMessage(player, "&cInvalid editor state!");
            return;
        }
        String field = parts[0];
        String discId = parts[1];

        CustomDisc disc = plugin.getDiscManager().getDisc(discId);
        if (disc == null) {
            MessageUtil.sendMessage(player, "&cDisc not found!");
            return;
        }

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
                if (!InputValidator.isValidSoundKey(input)) {
                    MessageUtil.sendMessage(player, "&cInvalid format! Use: namespace:sound_name (lowercase)");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                } else {
                    plugin.getDiscManager().updateDiscField(discId, "sound", input);
                    MessageUtil.sendMessage(player, "&a✓ Sound Key updated: &b" + input);
                }
                break;
            case "duration":
                try {
                    int seconds = Integer.parseInt(input);
                    if (seconds <= 0) {
                        MessageUtil.sendMessage(player, "&cDuration must be greater than 0!");
                    } else {
                        plugin.getDiscManager().updateDiscField(discId, "durationTicks", seconds * 20);
                        MessageUtil.sendMessage(player, "&a✓ Duration updated: &e" + seconds + " seconds");
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "&cInvalid number!");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                }
                break;
            case "modelData":
                try {
                    int value = Integer.parseInt(input);
                    if (value < 1 || value > InputValidator.MAX_CUSTOM_MODEL_DATA) {
                        MessageUtil.sendMessage(player, "&cModel Data must be between 1 and "
                            + InputValidator.MAX_CUSTOM_MODEL_DATA + "!");
                    } else {
                        String owner = discUsingModelData(value, discId);
                        if (owner != null) {
                            MessageUtil.sendMessage(player, "&e⚠ Model Data " + value
                                + " is already used by &e" + owner + "&e - both discs will share the texture.");
                        }
                        plugin.getDiscManager().updateDiscField(discId, "customModelData", value);
                        MessageUtil.sendMessage(player, "&a✓ Model Data updated: &e" + value);
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "&cInvalid number!");
                    MessageUtil.sendMessage(player, "&7Reopening editor...");
                }
                break;
            case "newCategory":
                String categoryId = input.toLowerCase().replace(" ", "_");
                if (!InputValidator.isValidCategoryId(categoryId)) {
                    MessageUtil.sendMessage(player, "&cInvalid category ID! Use letters, numbers, - and _ (max "
                        + InputValidator.MAX_CATEGORY_ID_LENGTH + " characters)");
                    break;
                }
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

    /**
     * Returns the id of another disc already using this CustomModelData, or null
     * if the value is free. Two discs sharing a value share their texture.
     */
    private String discUsingModelData(int value, String excludeDiscId) {
        for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
            if (disc.getCustomModelData() == value && !disc.getId().equals(excludeDiscId)) {
                return disc.getId();
            }
        }
        return null;
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
        final String discId;
        final EditorMode mode;
        final boolean fromExternal; // True if opened from AdminGUI, false if from within DiscEditor
        /** Zero-based page of the category selector; 0 everywhere else. */
        final int page;

        EditorContext(String discId) {
            this(discId, EditorMode.MAIN_EDITOR, false, 0);
        }

        EditorContext(String discId, EditorMode mode) {
            this(discId, mode, false, 0);
        }

        EditorContext(String discId, EditorMode mode, boolean fromExternal) {
            this(discId, mode, fromExternal, 0);
        }

        EditorContext(String discId, EditorMode mode, boolean fromExternal, int page) {
            this.discId = discId;
            this.mode = mode;
            this.fromExternal = fromExternal;
            this.page = page;
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
