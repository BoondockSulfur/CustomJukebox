package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

import java.util.Random;

/**
 * Handles adding custom disc fragments to loot tables.
 * Supports:
 * - Dungeon chests (desert pyramid, jungle temple, etc.)
 * - Trail ruins archaeology
 * - Ancient cities
 * - Shipwrecks and underwater ruins
 *
 * Configurable:
 * - Drop chance
 * - Max fragments per chest
 * - Enable/disable per loot type
 */
public class LootGenerateListener implements Listener {

    private final CustomJukebox plugin;
    private final Random random;

    public LootGenerateListener(CustomJukebox plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLootGenerate(LootGenerateEvent event) {
        // Check if loot generation is enabled
        boolean isDungeon = plugin.getConfigManager().isDungeonLootEnabled();
        boolean isTrailRuins = plugin.getConfigManager().isTrailRuinsLootEnabled();

        if (!isDungeon && !isTrailRuins) {
            return;
        }

        // Get loot table
        if (event.getLootTable() == null) {
            return;
        }

        String lootTableKey = event.getLootTable().getKey().toString();

        // Check if it's a valid loot table for fragments
        boolean shouldAddLoot = false;

        if (isDungeon) {
            shouldAddLoot = lootTableKey.contains("simple_dungeon") ||
                           lootTableKey.contains("desert_pyramid") ||
                           lootTableKey.contains("jungle_temple") ||
                           lootTableKey.contains("stronghold") ||
                           lootTableKey.contains("woodland_mansion") ||
                           lootTableKey.contains("buried_treasure") ||
                           lootTableKey.contains("shipwreck") ||
                           lootTableKey.contains("underwater_ruin") ||
                           lootTableKey.contains("pillager_outpost") ||
                           lootTableKey.contains("ancient_city") ||
                           lootTableKey.contains("end_city") ||
                           lootTableKey.contains("bastion") ||
                           lootTableKey.contains("nether_bridge");
        }

        if (!shouldAddLoot && isTrailRuins) {
            shouldAddLoot = lootTableKey.contains("trail_ruins") ||
                           lootTableKey.contains("archaeology");
        }

        if (!shouldAddLoot) {
            return;
        }

        // Random chance to add fragments
        double lootChance = plugin.getConfigManager().getLootChance();
        if (random.nextDouble() > lootChance) {
            return;
        }

        // Add random fragments
        int maxStacks = plugin.getConfigManager().getMaxLootDiscs(); // Reuse config value
        int stacksToAdd = random.nextInt(maxStacks) + 1;

        for (int i = 0; i < stacksToAdd; i++) {
            DiscFragment randomFragment = plugin.getDiscManager().getRandomFragment();
            if (randomFragment != null) {
                // Each stack has 1-5 fragments
                int fragmentAmount = random.nextInt(5) + 1;
                event.getLoot().add(randomFragment.createItemStack(fragmentAmount));

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Added " + fragmentAmount + " fragment(s) for disc " +
                        randomFragment.getDiscId() + " to loot table: " + lootTableKey);
                }
            }
        }
    }
}
