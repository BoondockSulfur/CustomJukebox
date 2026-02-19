package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

/**
 * Handles custom disc fragment drops from creepers.
 * Similar to vanilla behavior where creepers drop music discs when killed by skeletons.
 *
 * Drop conditions:
 * - Creeper killed by skeleton: Always drops fragment (if enabled)
 * - Creeper killed by player/other: Configurable chance
 */
public class DiscDropListener implements Listener {

    private final CustomJukebox plugin;
    private final Random random;

    public DiscDropListener(CustomJukebox plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCreeperDeath(EntityDeathEvent event) {
        if (!plugin.getConfigManager().isCreeperDropsEnabled()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Creeper)) {
            return;
        }

        Creeper creeper = (Creeper) entity;

        // Determine if fragment should drop
        boolean shouldDrop = false;

        // Check if creeper was killed by a skeleton (vanilla behavior)
        Entity killer = creeper.getKiller();
        if (killer instanceof Skeleton) {
            shouldDrop = true; // Always drop when killed by skeleton
        } else if (killer != null) {
            // Player or other entity killed creeper - use configurable chance
            if (random.nextDouble() <= plugin.getConfigManager().getCreeperDropChance()) {
                shouldDrop = true;
            }
        }

        if (!shouldDrop) {
            return;
        }

        // Drop random fragment
        DiscFragment randomFragment = plugin.getDiscManager().getRandomFragment();
        if (randomFragment != null) {
            // Drop 1-3 fragments
            int fragmentAmount = random.nextInt(3) + 1;
            event.getDrops().add(randomFragment.createItemStack(fragmentAmount));

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Creeper dropped " + fragmentAmount + " fragment(s) for disc: " +
                    randomFragment.getDiscId());
            }
        } else {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("No fragments available for creeper drop!");
            }
        }
    }
}
