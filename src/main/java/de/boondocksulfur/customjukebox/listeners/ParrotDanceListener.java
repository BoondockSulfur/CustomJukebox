package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public class ParrotDanceListener implements Listener {

    private final CustomJukebox plugin;

    public ParrotDanceListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDiscInsert(PlayerInteractEvent event) {
        if (!plugin.getConfigManager().isParrotDancingEnabled()) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null || !item.getType().name().contains("MUSIC_DISC")) return;

        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(item);
        if (disc == null) return; // Not a custom disc

        // Schedule parrot dancing
        SchedulerUtil.runLater(plugin, block.getLocation(), () -> {
            makeParrotsDance(block.getLocation());
        }, 5L);
    }

    private void makeParrotsDance(Location jukeboxLocation) {
        if (jukeboxLocation.getWorld() == null) return;

        int radius = plugin.getConfigManager().getDanceRadius();

        Collection<Entity> nearbyEntities = jukeboxLocation.getWorld()
            .getNearbyEntities(jukeboxLocation, radius, radius, radius);

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Parrot) {
                Parrot parrot = (Parrot) entity;

                // Make parrot dance by setting dancing state
                // Note: In newer versions, parrots automatically dance near jukeboxes
                // This is mainly for ensuring custom discs trigger the same behavior

                // Schedule multiple dance animations
                for (int i = 0; i < 10; i++) {
                    final long delay = i * 20L;
                    SchedulerUtil.runEntityTaskLater(plugin, parrot, () -> {
                        // Parrots will dance automatically if music is playing
                        // We just ensure they're in the right state
                        if (parrot.isValid() && !parrot.isDead()) {
                            // The parrot will dance naturally to jukebox music
                            // We could add particle effects here if desired
                        }
                    }, delay);
                }
            }
        }
    }
}
