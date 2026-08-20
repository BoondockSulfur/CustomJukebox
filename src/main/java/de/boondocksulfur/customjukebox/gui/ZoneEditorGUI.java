package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.AmbientZone;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-game editor for an {@link AmbientZone}. Exposes the toggle/cycle options
 * (enabled, loop, sync mode, volume, priority, playlist) as clickable buttons;
 * every click mutates the zone, persists it and restarts its playback via
 * {@link de.boondocksulfur.customjukebox.manager.AmbientZoneManager#saveZone}.
 *
 * <p>Spatial settings that need a value or the player's position (radius,
 * region, center) are set with {@code /cjb zone radius|region|center} - the GUI
 * shows their current values but points to the commands, since chat-input flows
 * add complexity without much gain here.
 */
public class ZoneEditorGUI implements Listener {

    // Row 1: status. Row 2: the per-zone options. Row 3: area + master switch.
    private static final int SLOT_INFO = 4;
    private static final int SLOT_PLAYLIST = 10;
    private static final int SLOT_LOOP = 11;
    private static final int SLOT_PLAYBACK = 12;
    private static final int SLOT_SYNC = 13;
    private static final int SLOT_VOLUME = 14;
    private static final int SLOT_PRIORITY = 15;
    private static final int SLOT_HEIGHT = 16;
    private static final int SLOT_AREA = 21;
    private static final int SLOT_SHUFFLE = 22;
    private static final int SLOT_ENABLED = 23;

    private final CustomJukebox plugin;
    private final Map<UUID, String> activeEditors = new ConcurrentHashMap<>();

    public ZoneEditorGUI(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the editor for a zone.
     * @param player editor
     * @param zone zone to edit
     */
    public void openEditor(Player player, AmbientZone zone) {
        Inventory gui = build(zone);
        activeEditors.put(player.getUniqueId(), zone.getId());
        player.openInventory(gui);
    }

    private Inventory build(AmbientZone zone) {
        Inventory inv = InventoryUtil.createGuiInventory(this, 27, "§6§lZone: §e" + zone.getId());

        boolean active = plugin.getAmbientZoneManager().isZoneActive(zone.getId());
        // The manager knows exactly what is blocking the zone (empty playlist,
        // discs without a duration, missing region, ...) - show that instead of
        // guessing from the zone's own fields.
        String idleReason = idleReasonText(zone);
        inv.setItem(SLOT_INFO, button(Material.JUKEBOX, "§6§lZone " + zone.getId(),
            "§7State: " + (!zone.isEnabled() ? "§8disabled" : active ? "§aactive" : "§eidle (not playing)"),
            idleReason == null ? "§aReady to play." : "§c" + idleReason));

        inv.setItem(SLOT_PLAYLIST, button(Material.MUSIC_DISC_CAT, "§b§lPlaylist",
            "§7Current: §f" + (zone.getPlaylistId().isEmpty() ? "§8none" : zone.getPlaylistId()),
            "§eClick: §7next playlist",
            "§eRight-click: §7previous playlist"));

        inv.setItem(SLOT_LOOP, button(zone.isLoop() ? Material.LIME_DYE : Material.GRAY_DYE,
            "§a§lLoop", "§7Loops the whole playlist forever.",
            "§7Current: §f" + zone.isLoop(), "§eClick to toggle"));

        boolean individual = zone.getPlaybackMode() == AmbientZone.PlaybackMode.INDIVIDUAL;

        inv.setItem(SLOT_PLAYBACK, button(individual ? Material.PLAYER_HEAD : Material.JUKEBOX,
            "§6§lPlayback mode",
            "§7synced §8- §feveryone hears the same song together (events)",
            "§7individual §8- §feach player hears full songs from entry (lobby)",
            "§7Current: §f" + zone.getPlaybackMode().name().toLowerCase(Locale.ROOT),
            "§eClick to toggle"));

        inv.setItem(SLOT_SYNC, button(Material.COMPASS, "§d§lSync mode",
            "§7How late arrivals join a running track §8(synced mode only)§7:",
            "§7immediate §8- §fstart current track from the beginning",
            "§7next_track §8- §fwait for the next track (perfectly synced)",
            "§7Current: §f" + zone.getSyncMode().name().toLowerCase(Locale.ROOT),
            individual ? "§8Ignored while playback = individual" : "§eClick to toggle"));

        inv.setItem(SLOT_VOLUME, button(Material.NOTE_BLOCK, "§e§lVolume",
            "§7Higher volume = larger audible radius.",
            "§7Current: §f" + (zone.inheritsVolume() ? "inherit (global)" : trim(zone.getVolume())),
            "§8Changing this restarts the zone's playlist.",
            "§eClick: §7cycle inherit → 1 → 2 → 3 → 4"));

        inv.setItem(SLOT_PRIORITY, button(Material.HOPPER, "§7§lPriority",
            "§7When zones overlap, the highest wins.",
            "§7Current: §f" + zone.getPriority(),
            "§eClick: §7+1   §eRight-click: §7-1"));

        List<String> areaLore = new ArrayList<>();
        areaLore.add("§7Type: §f" + zone.getType().name().toLowerCase(Locale.ROOT));
        if (zone.getType() == AmbientZone.ZoneType.GLOBAL) {
            areaLore.add("§7Reaches §fevery player on the server");
            areaLore.add("§8Set with /cjb zone radius|region|pos1 " + zone.getId() + " to make it local");
        } else {
            areaLore.add("§7World: §f" + zone.getWorld());
            if (zone.getType() == AmbientZone.ZoneType.WORLDGUARD) {
                areaLore.add("§7Region: §f" + (zone.getRegion().isEmpty() ? "§8unset" : zone.getRegion()));
                areaLore.add("§8Set with /cjb zone region " + zone.getId() + " <name>");
            } else if (zone.getType() == AmbientZone.ZoneType.CUBOID) {
                areaLore.add("§7Pos1: §f" + (zone.isPos1Set()
                    ? zone.getX1() + ", " + zone.getY1() + ", " + zone.getZ1() : "§8unset"));
                areaLore.add("§7Pos2: §f" + (zone.isPos2Set()
                    ? zone.getX2() + ", " + zone.getY2() + ", " + zone.getZ2() : "§8unset"));
                areaLore.add("§8Set with /cjb zone pos1|pos2 " + zone.getId() + " (your position)");
            } else {
                areaLore.add(String.format(Locale.ROOT, "§7Center: §f%.0f, %.0f, %.0f",
                    zone.getCenterX(), zone.getCenterY(), zone.getCenterZ()));
                areaLore.add("§7Radius: §f" + (int) zone.getRadius());
                areaLore.add("§8Set with /cjb zone radius|center " + zone.getId());
            }
        }
        inv.setItem(SLOT_AREA, button(Material.MAP, "§3§lArea", areaLore.toArray(new String[0])));

        inv.setItem(SLOT_HEIGHT, button(zone.isFullHeight() ? Material.LIGHT_BLUE_STAINED_GLASS : Material.IRON_BARS,
            "§b§lHeight range",
            "§7full §8- §fany Y (cylinder / column) — good for lobbies",
            "§7limited §8- §fbounded by radius/box in Y too (3D)",
            "§7Current: §f" + (zone.isFullHeight() ? "full (any Y)" : "limited (bounded Y)"),
            "§eClick to toggle"));

        inv.setItem(SLOT_SHUFFLE, button(zone.isShuffle() ? Material.ENDER_PEARL : Material.ENDER_EYE,
            "§d§lShuffle",
            "§7Plays the playlist in random order,",
            "§7reshuffling on every lap.",
            "§7Current: §f" + zone.isShuffle(),
            "§8Changing this restarts the zone's playlist.",
            "§eClick to toggle"));

        inv.setItem(SLOT_ENABLED, button(zone.isEnabled() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
            zone.isEnabled() ? "§a§lEnabled" : "§c§lDisabled",
            "§7A disabled zone never plays.",
            "§eClick to " + (zone.isEnabled() ? "disable" : "enable")));

        // Fill empty slots with panes
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
        return inv;
    }

    /**
     * Human-readable reason why the zone is not playing, or null if it is fine.
     * Resolved through the language manager so it matches the server language.
     */
    private String idleReasonText(AmbientZone zone) {
        String key = plugin.getAmbientZoneManager().getIdleReasonKey(zone);
        if (key == null) {
            return null;
        }
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("zone", zone.getId());
        placeholders.put("playlist", zone.getPlaylistId() == null ? "" : zone.getPlaylistId());
        placeholders.put("world", zone.getWorld());
        placeholders.put("region", zone.getRegion());
        return MessageUtil.stripColors(plugin.getLanguageManager().getMessage(key, placeholders));
    }

    /** Formats a float without a trailing ".0" (2.0 -> "2"). */
    private String trim(float value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%.2f", value);
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemUtil.setDisplayName(meta, name);
            if (lore.length > 0) {
                ItemUtil.setLore(meta, lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;

        // Our GUI never hands out items
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        String zoneId = activeEditors.get(player.getUniqueId());
        if (zoneId == null) return;

        if (!player.hasPermission("customjukebox.zone")) {
            activeEditors.remove(player.getUniqueId());
            MessageUtil.sendMessage(player, "&cYou no longer have permission to edit zones!");
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
        }

        AmbientZone zone = plugin.getAmbientZoneManager().getZone(zoneId);
        if (zone == null) {
            activeEditors.remove(player.getUniqueId());
            SchedulerUtil.runPlayerTask(plugin, player, player::closeInventory);
            return;
        }

        boolean rightClick = event.isRightClick();
        boolean changed = true;
        switch (event.getSlot()) {
            case SLOT_PLAYLIST:
                cyclePlaylist(zone, !rightClick);
                break;
            case SLOT_LOOP:
                zone.setLoop(!zone.isLoop());
                break;
            case SLOT_SYNC:
                zone.setSyncMode(zone.getSyncMode() == AmbientZone.SyncMode.IMMEDIATE
                    ? AmbientZone.SyncMode.NEXT_TRACK : AmbientZone.SyncMode.IMMEDIATE);
                break;
            case SLOT_PLAYBACK:
                zone.setPlaybackMode(zone.getPlaybackMode() == AmbientZone.PlaybackMode.SYNCED
                    ? AmbientZone.PlaybackMode.INDIVIDUAL : AmbientZone.PlaybackMode.SYNCED);
                break;
            case SLOT_VOLUME:
                cycleVolume(zone);
                break;
            case SLOT_PRIORITY:
                zone.setPriority(zone.getPriority() + (rightClick ? -1 : 1));
                break;
            case SLOT_HEIGHT:
                zone.setFullHeight(!zone.isFullHeight());
                break;
            case SLOT_SHUFFLE:
                zone.setShuffle(!zone.isShuffle());
                break;
            case SLOT_ENABLED:
                zone.setEnabled(!zone.isEnabled());
                break;
            default:
                changed = false;
                break;
        }

        if (changed) {
            plugin.getAmbientZoneManager().saveZone(zone);
            // Refresh the view from the persisted zone
            AmbientZone refreshed = plugin.getAmbientZoneManager().getZone(zoneId);
            if (refreshed != null) {
                Inventory rebuilt = build(refreshed);
                player.getOpenInventory().getTopInventory().setContents(rebuilt.getContents());
            }
        }
    }

    /**
     * Cycles the zone's playlist through all available playlists (plus a "none"
     * step), forward or backward.
     */
    private void cyclePlaylist(AmbientZone zone, boolean forward) {
        List<String> ids = new ArrayList<>();
        ids.add(""); // "none"
        for (DiscPlaylist pl : plugin.getDiscManager().getAllPlaylists()) {
            ids.add(pl.getId());
        }
        if (ids.size() == 1) {
            return; // No playlists exist
        }
        int idx = Math.max(0, ids.indexOf(zone.getPlaylistId()));
        idx = (idx + (forward ? 1 : ids.size() - 1)) % ids.size();
        zone.setPlaylistId(ids.get(idx));
    }

    private void cycleVolume(AmbientZone zone) {
        // inherit -> 1 -> 2 -> 3 -> 4 -> inherit
        if (zone.inheritsVolume()) {
            zone.setVolume(1f);
        } else if (zone.getVolume() >= 4f) {
            zone.setVolume(AmbientZone.VOLUME_INHERIT);
        } else {
            zone.setVolume((float) Math.floor(zone.getVolume()) + 1f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!GUIHolder.isOwnedBy(event.getInventory(), this)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;
        activeEditors.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryUtil.cancelDragIntoGui(event, this);
    }

    /**
     * Clears a player's editor session on logout.
     * @param player the player leaving
     */
    public void cleanup(Player player) {
        activeEditors.remove(player.getUniqueId());
    }
}
