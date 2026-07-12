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

        // The event fires once per hand - use the item of the hand that interacts
        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().contains("MUSIC_DISC")) return;

        CustomDisc disc = plugin.getDiscManager().getDiscFromItem(item);
        if (disc == null) return; // Not a custom disc

        // Show a short note-particle burst above nearby parrots. The vanilla
        // dance animation is client-side and cannot be forced via the Bukkit
        // API - especially since the plugin suppresses the jukebox "playing"
        // state to mute the vanilla track.
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
            if (entity instanceof Parrot parrot) {
                parrot.getWorld().spawnParticle(org.bukkit.Particle.NOTE,
                    parrot.getLocation().add(0, 0.8, 0), 3, 0.3, 0.3, 0.3);
            }
        }
    }
}
