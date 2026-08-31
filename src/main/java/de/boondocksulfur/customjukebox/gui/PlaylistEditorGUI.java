package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.GuiPageUtil;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI for editing playlists in-game.
 * Players can add/remove discs from playlists by clicking on them.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class PlaylistEditorGUI implements Listener {

    /** How much of a name fits an inventory title before it runs past the frame. */
    private static final int TITLE_NAME_LIMIT = 24;


    private static final int DISCS_PER_PAGE = 45;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT_PAGE = 53;

    private final CustomJukebox plugin;
    private final Map<UUID, String> activeEditors = new ConcurrentHashMap<>();
    // Current page per editing player
    private final Map<UUID, Integer> editorPage = new ConcurrentHashMap<>();

    public PlaylistEditorGUI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the playlist editor for the given playlist.
     */
    public void openEditor(Player player, DiscPlaylist playlist) {
        openEditor(player, playlist, 0);
    }

    /**
     * Opens the playlist editor at a specific page of the disc list.
     * @param player editor
     * @param playlist playlist being edited
     * @param page zero-based page (clamped)
     */
    public void openEditor(Player player, DiscPlaylist playlist, int page) {
        int total = plugin.getDiscManager().getAllDiscs().size();
        page = GuiPageUtil.clampPage(page, total, DISCS_PER_PAGE);
        Inventory gui = createEditorInventory(playlist, page);
        activeEditors.put(player.getUniqueId(), playlist.getId());
        editorPage.put(player.getUniqueId(), page);
        player.openInventory(gui);
    }

    /**
     * Creates the inventory GUI for the playlist editor.
     */
    private Inventory createEditorInventory(DiscPlaylist playlist, int page) {
        String title = "§6§lEdit: §e" + AdventureUtil.fit(playlist.getDisplayName(), TITLE_NAME_LIMIT);
        Inventory inv = InventoryUtil.createGuiInventory(this, 54, title);

        // Get all discs in playlist
        List<CustomDisc> playlistDiscs = plugin.getDiscManager().getDiscsFromPlaylist(playlist.getId());
        Set<String> playlistDiscIds = new HashSet<>();
        for (CustomDisc disc : playlistDiscs) {
            playlistDiscIds.add(disc.getId());
        }

        // Populate with the current page of available discs
        List<CustomDisc> allDiscs = new ArrayList<>(plugin.getDiscManager().getAllDiscs());
        int slot = 0;

        for (CustomDisc disc : GuiPageUtil.slice(allDiscs, page, DISCS_PER_PAGE)) {
            ItemStack item = disc.createItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = ItemUtil.getLore(meta);
                if (lore == null) {
                    lore = new ArrayList<>();
                }

                // Add status indicator
                if (playlistDiscIds.contains(disc.getId())) {
                    lore.add("");
                    lore.add("§a✔ In playlist");
                    lore.add("§7Click to §cremove");
                } else {
                    lore.add("");
                    lore.add("§7Not in playlist");
                    lore.add("§7Click to §aadd");
                }

                ItemUtil.setLore(meta, lore);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        // Add info bar at bottom
        addInfoBar(inv, playlist, page, allDiscs.size());

        return inv;
    }

    /**
     * Adds the info bar at the bottom of the GUI.
     */
    private void addInfoBar(Inventory inv, DiscPlaylist playlist, int page, int totalDiscs) {
        // Playlist info
        ItemStack info = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            ItemUtil.setDisplayName(infoMeta, "§6§lPlaylist Info");
            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §e" + playlist.getId());
            lore.add("§7Name: §e" + playlist.getDisplayName());
            lore.add("§7Description: §e" + playlist.getDescription());
            lore.add("§7Discs: §e" + playlist.getDiscCount());
            ItemUtil.setLore(infoMeta, lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, info);

        // Paging
        int pageCount = GuiPageUtil.pageCount(totalDiscs, DISCS_PER_PAGE);
        inv.setItem(SLOT_PREV_PAGE, GuiPageUtil.previousButton(page));
        inv.setItem(SLOT_NEXT_PAGE, GuiPageUtil.nextButton(page, pageCount));
        inv.setItem(48, GuiPageUtil.pageIndicator(page, pageCount, totalDiscs, "discs"));

        // Separator for the remaining bottom-row slots
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            ItemUtil.setDisplayName(sepMeta, " ");
            separator.setItemMeta(sepMeta);
        }
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, separator);
            }
        }
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
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return; // Don't handle clicks in player's own inventory
        }

        // Check if player is in playlist editor
        String playlistId = activeEditors.get(player.getUniqueId());
        if (playlistId == null) return;

        // Permission check - ensure player still has permission
        if (!player.hasPermission("customjukebox.playlist")) {
            activeEditors.remove(player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou no longer have permission to edit playlists!");
            // Close next tick - closeInventory() inside a click handler is undefined behavior
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
        }

        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);
        if (playlist == null) {
            activeEditors.remove(player.getUniqueId());
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int page = editorPage.getOrDefault(player.getUniqueId(), 0);

        // Bottom bar: paging only, everything else is decoration
        if (event.getSlot() >= 45) {
            if (event.getSlot() == SLOT_PREV_PAGE || event.getSlot() == SLOT_NEXT_PAGE) {
                openEditor(player, playlist, event.getSlot() == SLOT_PREV_PAGE ? page - 1 : page + 1);
            }
            return;
        }

        // Get disc from clicked item
        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(clicked);
        if (disc == null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("PlaylistEditor: Could not identify disc from clicked item");
            }
            return;
        }

        // Check if disc is in playlist
        List<CustomDisc> playlistDiscs = plugin.getDiscManager().getDiscsFromPlaylist(playlistId);
        boolean isInPlaylist = playlistDiscs.stream().anyMatch(d -> d.getId().equals(disc.getId()));

        if (isInPlaylist) {
            // Remove from playlist
            boolean success = plugin.getDiscManager().removeDiscFromPlaylist(playlistId, disc.getId());
            if (success) {
                MessageUtil.sendWithValues(player, "&a✓ Removed &e", disc.getDisplayName(),
                    " &afrom playlist &e", playlist.getDisplayName());
            } else {
                MessageUtil.sendMessage(player, "&c✗ Failed to remove disc from playlist");
                plugin.getLogger().warning("Failed to remove disc '" + disc.getId() + "' from playlist '" + playlistId + "'");
            }
        } else {
            // Add to playlist
            boolean success = plugin.getDiscManager().addDiscToPlaylist(playlistId, disc.getId());
            if (success) {
                MessageUtil.sendWithValues(player, "&a✓ Added &e", disc.getDisplayName(),
                    " &ato playlist &e", playlist.getDisplayName());
            } else {
                MessageUtil.sendMessage(player, "&c✗ Failed to add disc to playlist");
                plugin.getLogger().warning("Failed to add disc '" + disc.getId() + "' to playlist '" + playlistId + "'");
            }
        }

        // Refresh GUI, staying on the current page
        playlist = plugin.getDiscManager().getPlaylist(playlistId); // Reload
        if (playlist != null) {
            Inventory newInv = createEditorInventory(playlist, page);
            player.getOpenInventory().getTopInventory().setContents(newInv.getContents());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Only react when one of our own inventories closes - the close event
        // caused by opening this editor over another GUI must not clear the context
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;
        // Keep the context when one of our editors is replaced by a new inventory
        // (e.g. openEditor called while an editor is already open)
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        // Remove from active editors
        editorPage.remove(player.getUniqueId());
        String playlistId = activeEditors.remove(player.getUniqueId());
        if (playlistId != null) {
            MessageUtil.sendMessage(player, "&aPlaylist editor closed.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryUtil.cancelDragIntoGui(event, this);
    }

    /**
     * Cleans up when player logs out.
     */
    public void cleanup(Player player) {
        activeEditors.remove(player.getUniqueId());
        editorPage.remove(player.getUniqueId());
    }
}
