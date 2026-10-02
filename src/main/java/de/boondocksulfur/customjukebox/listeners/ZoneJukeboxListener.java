package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.AmbientZone;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Zone jukeboxes: a jukebox item bound to an ambient zone. Placing it makes the
 * zone play from that block - a point-source radius zone centered on it - and
 * picking it up pauses the zone until it is placed again.
 *
 * <p>Region protection is respected by listening with {@code ignoreCancelled}:
 * WorldGuard and similar plugins cancel the place and break events of players
 * who may not build there, and those never reach the handlers below. Clicks on
 * a zone jukebox are handled in {@link JukeboxListener}.
 */
public class ZoneJukeboxListener implements Listener {

    private final CustomJukebox plugin;

    public ZoneJukeboxListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the jukebox item for a zone.
     * @param zone zone the item places
     * @return a jukebox carrying the zone ID
     */
    public static ItemStack createItem(AmbientZone zone) {
        ItemStack item = new ItemStack(Material.JUKEBOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemUtil.setDisplayName(meta, "&6Zone Jukebox: &e" + zone.getId());
            ItemUtil.setLore(meta, List.of(
                "&7Place it where the music of zone &f" + zone.getId(),
                "&7should come from. It fades with distance.",
                "&8Right-click (admins): zone settings",
                "&8Break it to move it - the zone pauses meanwhile."));
            meta.getPersistentDataContainer().set(ItemUtil.ZONE_JUKEBOX_KEY, PersistentDataType.STRING, zone.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Zone ID carried by a zone jukebox item, or null. */
    private static String zoneIdOf(ItemStack item) {
        return item == null || item.getType() != Material.JUKEBOX
            ? null : ItemUtil.getPdcString(item, ItemUtil.ZONE_JUKEBOX_KEY);
    }

    /**
     * Refuses a placement that cannot work: the zone is gone, or it already has
     * its one jukebox standing somewhere.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        String zoneId = zoneIdOf(event.getItemInHand());
        if (zoneId == null) {
            return;
        }
        Player player = event.getPlayer();
        AmbientZone zone = plugin.getAmbientZoneManager().getZone(zoneId);
        if (zone == null) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("zone-jukebox-unknown-zone", "zone", zoneId));
            return;
        }
        if (zone.isJukeboxPlaced()) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("zone-jukebox-already-placed", placeholders(zone)));
        }
    }

    /** Binds the placed block to its zone and moves the zone's source there. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String zoneId = zoneIdOf(event.getItemInHand());
        if (zoneId == null) {
            return;
        }
        AmbientZone zone = plugin.getAmbientZoneManager().getZone(zoneId);
        if (zone == null || zone.isJukeboxPlaced()) {
            return; // Refused above
        }
        Block block = event.getBlockPlaced();
        if (block.getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(ItemUtil.ZONE_JUKEBOX_KEY, PersistentDataType.STRING, zoneId);
            state.update(true, false);
        }

        zone.setType(AmbientZone.ZoneType.RADIUS);
        zone.setWorld(block.getWorld().getName());
        zone.setCenter(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        zone.setSoundSource(AmbientZone.SoundSource.POINT);
        zone.placeJukebox(block.getX(), block.getY(), block.getZ(), event.getPlayer().getUniqueId().toString());
        plugin.getAmbientZoneManager().saveZone(zone);

        MessageUtil.sendMessage(event.getPlayer(), plugin.getLanguageManager()
            .getMessage("zone-jukebox-placed", "zone", zoneId));
        String idle = plugin.getAmbientZoneManager().getIdleReasonKey(zone);
        if (idle != null) {
            MessageUtil.sendMessage(event.getPlayer(), plugin.getLanguageManager()
                .getMessage(idle, placeholders(zone)));
        }
    }

    /** Only an admin or whoever placed it may pick a zone jukebox up. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakCheck(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.JUKEBOX) {
            return;
        }
        AmbientZone zone = plugin.getAmbientZoneManager().zoneForJukebox(event.getBlock());
        if (zone == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean owner = player.getUniqueId().toString().equals(zone.getJukeboxOwner());
        if (!owner && !player.hasPermission("customjukebox.zone")) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("zone-jukebox-no-break", "zone", zone.getId()));
        }
    }

    /**
     * Picking it up: the bound item drops instead of a plain jukebox, and the
     * zone pauses until the jukebox is placed again.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.JUKEBOX) {
            return;
        }
        AmbientZone zone = plugin.getAmbientZoneManager().zoneForJukebox(block);
        if (zone == null) {
            return;
        }
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createItem(zone));

        zone.removeJukebox();
        plugin.getAmbientZoneManager().saveZone(zone);
        MessageUtil.sendMessage(event.getPlayer(), plugin.getLanguageManager()
            .getMessage("zone-jukebox-broken", "zone", zone.getId()));
    }

    /** Explosions leave zone jukeboxes standing - nobody picked them up. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isZoneJukebox);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isZoneJukebox);
    }

    /** Hoppers must not feed discs into a zone jukebox. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopperInsert(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.JUKEBOX
                && event.getDestination().getHolder() instanceof Jukebox jukebox
                && plugin.getAmbientZoneManager().zoneForJukebox(jukebox.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isZoneJukebox(Block block) {
        return block.getType() == Material.JUKEBOX && plugin.getAmbientZoneManager().zoneForJukebox(block) != null;
    }

    private java.util.Map<String, String> placeholders(AmbientZone zone) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("zone", zone.getId());
        map.put("coords", zone.getJukeboxX() + ", " + zone.getJukeboxY() + ", " + zone.getJukeboxZ());
        map.put("world", zone.getWorld());
        map.put("playlist", zone.getPlaylistId() == null ? "" : zone.getPlaylistId());
        map.put("region", zone.getRegion());
        return map;
    }
}
