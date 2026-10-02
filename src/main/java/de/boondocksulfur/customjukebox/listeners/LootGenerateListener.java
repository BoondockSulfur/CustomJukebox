package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BrushableBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * Handles adding custom disc fragments to loot tables.
 * Supports:
 * - Dungeon chests (desert pyramid, jungle temple, etc.)
 * - Trail ruins archaeology (suspicious blocks - these have no chest, and their
 *   loot is rolled without a LootGenerateEvent, see {@link #onBrush})
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
    /** Marks a suspicious block whose fragment chance was already rolled. */
    private final NamespacedKey rolledKey;

    public LootGenerateListener(CustomJukebox plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.rolledKey = new NamespacedKey(plugin, "archaeology_rolled");
    }

    /**
     * Trail ruins fragments.
     *
     * <p>Brushing unpacks a suspicious block's loot table without firing
     * {@link LootGenerateEvent} - that event only covers containers - so
     * {@link #onLootGenerate} never saw archaeology loot. Instead, when a player
     * starts brushing a trail ruins block whose loot is still unrolled, the
     * fragment chance is rolled here once; on a hit the block's loot is replaced
     * by the fragment, which brushing then reveals.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBrush(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY
                || event.getItem() == null || event.getItem().getType() != Material.BRUSH) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.SUSPICIOUS_SAND
                && block.getType() != Material.SUSPICIOUS_GRAVEL)) {
            return;
        }
        if (!plugin.getConfigManager().isTrailRuinsLootEnabled()
                || !(block.getState() instanceof BrushableBlock brushable)) {
            return;
        }
        LootTable lootTable = brushable.getLootTable();
        if (lootTable == null || !lootTable.getKey().getKey().startsWith("archaeology/trail_ruins")) {
            return; // Already brushed open, or not trail ruins
        }
        if (brushable.getPersistentDataContainer().has(rolledKey, PersistentDataType.BYTE)) {
            return; // Rolled on an earlier attempt
        }
        brushable.getPersistentDataContainer().set(rolledKey, PersistentDataType.BYTE, (byte) 1);

        double lootChance = plugin.getConfigManager().getLootChance();
        DiscFragment fragment = null;
        if (lootChance > 0.0 && random.nextDouble() < lootChance) {
            fragment = plugin.getDiscManager().getRandomFragment();
        }
        if (fragment != null) {
            brushable.setLootTable(null);
            brushable.setItem(fragment.createItemStack(1));
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Placed a fragment for disc " + fragment.getDiscId()
                    + " in a trail ruins block at " + block.getX() + "," + block.getY() + "," + block.getZ());
            }
        }
        brushable.update(true, false);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLootGenerate(LootGenerateEvent event) {
        // Check if loot generation is enabled
        // Trail ruins loot comes from suspicious blocks, see onBrush
        boolean isDungeon = plugin.getConfigManager().isDungeonLootEnabled();
        if (!isDungeon) {
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

        if (!shouldAddLoot) {
            return;
        }

        // Number of fragment stacks to add; 0 disables loot fragments entirely
        // (nextInt() below requires a positive bound)
        int maxStacks = plugin.getConfigManager().getMaxLootDiscs(); // Reuse config value
        if (maxStacks <= 0) {
            return;
        }

        // Random chance to add fragments (0.0 must never roll true)
        double lootChance = plugin.getConfigManager().getLootChance();
        if (lootChance <= 0.0 || random.nextDouble() >= lootChance) {
            return;
        }

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
