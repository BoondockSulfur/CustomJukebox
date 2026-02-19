package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles crafting of custom discs from fragments.
 * Supports:
 * - Shapeless crafting (any arrangement)
 * - Requires exact number of fragments per disc
 * - Fragments must be for the same disc (identified by CustomModelData)
 */
public class DiscCraftListener implements Listener {

    private final CustomJukebox plugin;

    public DiscCraftListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        if (!plugin.getConfigManager().isCraftingEnabled()) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        // Count fragments by disc ID
        Map<String, Integer> fragmentCounts = new HashMap<>();
        int totalFragments = 0;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() != Material.DISC_FRAGMENT_5) {
                continue;
            }

            // Check if this is a custom fragment
            DiscFragment fragment = plugin.getDiscManager().getFragmentFromItem(item);
            if (fragment != null) {
                String discId = fragment.getDiscId();
                fragmentCounts.put(discId, fragmentCounts.getOrDefault(discId, 0) + 1);
                totalFragments++;
            } else {
                // Also check using lore-based ID extraction (fallback)
                String discId = DiscFragment.getFragmentDiscId(item);
                if (discId != null) {
                    fragmentCounts.put(discId, fragmentCounts.getOrDefault(discId, 0) + 1);
                    totalFragments++;
                }
            }
        }

        // Check if we have exactly one disc type with the required amount
        if (fragmentCounts.size() == 1) {
            Map.Entry<String, Integer> entry = fragmentCounts.entrySet().iterator().next();
            String discId = entry.getKey();
            int count = entry.getValue();

            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null && disc.hasFragments()) {
                int required = disc.getFragmentCount();

                // Check if we have exactly the required number of fragments
                if (count == required && totalFragments == required) {
                    inventory.setResult(disc.createItemStack());

                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("Crafting recipe matched: " + count +
                            " fragments for disc " + discId);
                    }
                    return;
                }
            }
        }

        // No valid recipe found, clear result
        if (totalFragments > 0) {
            inventory.setResult(null);
        }
    }
}
