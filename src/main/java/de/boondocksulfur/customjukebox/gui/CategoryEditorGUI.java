package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.DiscCategory;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
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

/**
 * GUI-based category editor with chat input for text fields.
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class CategoryEditorGUI implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, EditorContext> activeEditors = new HashMap<>();
    private final Map<UUID, EditMode> chatInputMode = new HashMap<>();

    public CategoryEditorGUI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the category editor for a specific category.
     */
    public void openEditor(Player player, String categoryId) {
        DiscCategory category = plugin.getDiscManager().getCategory(categoryId);
        if (category == null) {
            player.sendMessage("§cCategory not found: " + categoryId);
            return;
        }

        activeEditors.put(player.getUniqueId(), new EditorContext(categoryId));
        Inventory gui = createEditorGUI(category);
        player.openInventory(gui);
    }

    /**
     * Creates the main editor GUI.
     */
    private Inventory createEditorGUI(DiscCategory category) {
        Inventory gui = InventoryUtil.createInventory(null, 27, "§6§lEdit Category: " + category.getId());

        // Display Name editor
        ItemStack displayNameItem = createEditorItem(Material.NAME_TAG,
            "§eDisplay Name",
            "§7Current: " + category.getDisplayName(),
            "",
            "§e§lClick to edit via chat");
        gui.setItem(11, displayNameItem);

        // Description editor
        ItemStack descriptionItem = createEditorItem(Material.WRITABLE_BOOK,
            "§eDescription",
            "§7Current: " + (category.getDescription().isEmpty() ? "§8(none)" : category.getDescription()),
            "",
            "§e§lClick to edit via chat");
        gui.setItem(13, descriptionItem);

        // Save & Close
        ItemStack saveItem = createEditorItem(Material.EMERALD,
            "§a§lSave & Close",
            "§7All changes are saved automatically",
            "",
            "§e§lClick to close editor");
        gui.setItem(15, saveItem);

        // Info display
        ItemStack infoItem = createEditorItem(Material.BOOK,
            "§6§lCategory Info",
            "§7ID: §e" + category.getId(),
            "§7Display Name: " + category.getDisplayName(),
            "§7Description: §f" + (category.getDescription().isEmpty() ? "§8(none)" : category.getDescription()),
            "",
            "§7Discs in category: §e" + plugin.getDiscManager().getDiscsByCategory(category.getId()).size());
        gui.setItem(4, infoItem);

        // Back/Cancel button
        ItemStack backItem = createEditorItem(Material.ARROW,
            "§7« Back",
            "§7Return to Category Management");
        gui.setItem(22, backItem);

        return gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        EditorContext context = activeEditors.get(player.getUniqueId());
        if (context == null) return;

        String title = AdventureUtil.toLegacy(event.getView().title());
        if (!title.startsWith("§6§lEdit Category:")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        // Handle clicks
        switch (slot) {
            case 11: // Edit Display Name
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage("§6§l╔════════════════════════════════════╗");
                player.sendMessage("§6§l║  §eEdit Display Name               ║");
                player.sendMessage("§6§l╚════════════════════════════════════╝");
                player.sendMessage("");
                player.sendMessage("§7Enter the new §eDisplay Name§7:");
                player.sendMessage("§8Colors: §7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
                player.sendMessage("");
                player.sendMessage("§7Type §ccancel §7to abort");
                chatInputMode.put(player.getUniqueId(), EditMode.DISPLAY_NAME);
                break;

            case 13: // Edit Description
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage("§6§l╔════════════════════════════════════╗");
                player.sendMessage("§6§l║  §eEdit Description                ║");
                player.sendMessage("§6§l╚════════════════════════════════════╝");
                player.sendMessage("");
                player.sendMessage("§7Enter the new §eDescription§7:");
                player.sendMessage("§8Type 'none' to remove description");
                player.sendMessage("");
                player.sendMessage("§7Type §ccancel §7to abort");
                chatInputMode.put(player.getUniqueId(), EditMode.DESCRIPTION);
                break;

            case 15: // Save & Close
                player.sendMessage("§a§l✓ §aCategory editor closed. All changes saved!");
                activeEditors.remove(player.getUniqueId());
                player.closeInventory();
                break;

            case 22: // Back
                activeEditors.remove(player.getUniqueId());
                player.closeInventory();
                // Reopen admin GUI category management
                plugin.getAdminGUI().openCategoryManagement(player);
                break;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        EditMode mode = chatInputMode.get(player.getUniqueId());
        EditorContext context = activeEditors.get(player.getUniqueId());

        if (mode == null || context == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§cEdit cancelled.");
            chatInputMode.remove(player.getUniqueId());
            SchedulerUtil.runPlayerTask(plugin, player, () -> {
                DiscCategory category = plugin.getDiscManager().getCategory(context.categoryId);
                if (category != null) {
                    player.openInventory(createEditorGUI(category));
                }
            });
            return;
        }

        // Process input
        SchedulerUtil.runPlayerTask(plugin, player, () -> handleChatInput(player, context, mode, input));
    }

    private void handleChatInput(Player player, EditorContext context, EditMode mode, String input) {
        DiscCategory currentCategory = plugin.getDiscManager().getCategory(context.categoryId);
        if (currentCategory == null) {
            player.sendMessage("§cError: Category no longer exists!");
            activeEditors.remove(player.getUniqueId());
            chatInputMode.remove(player.getUniqueId());
            return;
        }

        boolean success = false;
        String newDisplayName = currentCategory.getDisplayName();
        String newDescription = currentCategory.getDescription();

        switch (mode) {
            case DISPLAY_NAME:
                newDisplayName = AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
                success = plugin.getDiscManager().updateCategory(context.categoryId, newDisplayName, newDescription);
                if (success) {
                    player.sendMessage("§a§l✓ §aDisplay name updated to: " + newDisplayName);
                }
                break;

            case DESCRIPTION:
                newDescription = input.equalsIgnoreCase("none") ? "" : AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
                success = plugin.getDiscManager().updateCategory(context.categoryId, newDisplayName, newDescription);
                if (success) {
                    player.sendMessage("§a§l✓ §aDescription updated!");
                }
                break;
        }

        if (!success) {
            player.sendMessage("§c§l✗ §cFailed to update category!");
        }

        // Reopen GUI
        chatInputMode.remove(player.getUniqueId());
        DiscCategory updatedCategory = plugin.getDiscManager().getCategory(context.categoryId);
        if (updatedCategory != null) {
            player.openInventory(createEditorGUI(updatedCategory));
        }
    }

    /**
     * Creates an item for the editor GUI.
     */
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

    /**
     * Checks if a player has an active editor session.
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeEditors.containsKey(playerId);
    }

    /**
     * Cancels an active editor session.
     */
    public void cancelSession(UUID playerId) {
        activeEditors.remove(playerId);
        chatInputMode.remove(playerId);
    }

    /**
     * Internal context for tracking the editor session.
     */
    private static class EditorContext {
        final String categoryId;

        EditorContext(String categoryId) {
            this.categoryId = categoryId;
        }
    }

    /**
     * Edit mode for chat input.
     */
    private enum EditMode {
        DISPLAY_NAME,
        DESCRIPTION
    }
}
