package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
 *
 * <p><b>Why taking the result is handled here.</b> The recipe exists only as a
 * result set in {@link PrepareItemCraftEvent}; there is no registered recipe
 * behind it. When the result is taken, the server looks up the recipe to learn
 * what stays in the grid, finds none, and falls back to handing back the
 * ingredients themselves - the fragments are not used up, and stacked ones even
 * multiply. So a click on such a result is cancelled and the craft is done here:
 * one fragment per occupied slot is consumed and the disc is handed out.
 */
public class DiscCraftListener implements Listener {

    /** Upper bound for one shift-click, far above what a grid can hold. */
    private static final int MAX_SHIFT_CRAFTS = 64;

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
        if (!containsFragment(matrix)) {
            return; // Not ours - leave vanilla recipes alone
        }

        CustomDisc disc = matchRecipe(matrix);
        if (disc != null) {
            inventory.setResult(disc.createItemStack());
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Crafting recipe matched: " + disc.getFragmentCount() +
                    " fragments for disc " + disc.getId());
            }
            return;
        }

        // Fragments in the grid, but no valid recipe
        inventory.setResult(null);
    }

    /**
     * Takes over clicks on a fragment recipe's result, see the class comment.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeResult(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT
                || !(event.getClickedInventory() instanceof CraftingInventory inventory)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfigManager().isCraftingEnabled() || matchRecipe(inventory.getMatrix()) == null) {
            return;
        }

        event.setCancelled(true);
        ClickType click = event.getClick();
        int hotbarButton = event.getHotbarButton();
        // Inventories must not be changed from within the click event itself;
        // the next tick on the player's thread is safe, and the grid is checked
        // again there in case it changed in between
        SchedulerUtil.runPlayerTask(plugin, player, () -> craft(player, inventory, click, hotbarButton));
    }

    private void craft(Player player, CraftingInventory inventory, ClickType click, int hotbarButton) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
            return;
        }
        CustomDisc disc = matchRecipe(inventory.getMatrix());
        if (disc == null) {
            return;
        }
        ItemStack result = disc.createItemStack();

        switch (click) {
            case LEFT, RIGHT -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    return; // Discs do not stack - nothing to merge with
                }
                consumeIngredients(inventory);
                player.setItemOnCursor(result);
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                // Like vanilla: craft as many as the grid and free slots allow
                int crafted = 0;
                while (disc != null && crafted < MAX_SHIFT_CRAFTS && player.getInventory().firstEmpty() >= 0) {
                    consumeIngredients(inventory);
                    player.getInventory().addItem(disc.createItemStack());
                    crafted++;
                    disc = matchRecipe(inventory.getMatrix());
                }
            }
            case SWAP_OFFHAND -> {
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand != null && offHand.getType() != Material.AIR) {
                    return;
                }
                consumeIngredients(inventory);
                player.getInventory().setItemInOffHand(result);
            }
            case NUMBER_KEY -> {
                if (hotbarButton < 0 || hotbarButton > 8) {
                    return;
                }
                ItemStack target = player.getInventory().getItem(hotbarButton);
                if (target != null && target.getType() != Material.AIR) {
                    return;
                }
                consumeIngredients(inventory);
                player.getInventory().setItem(hotbarButton, result);
            }
            case DROP, CONTROL_DROP -> {
                consumeIngredients(inventory);
                player.getWorld().dropItem(player.getEyeLocation(), result,
                    item -> item.setVelocity(player.getEyeLocation().getDirection().multiply(0.3)));
            }
            default -> {
                return; // Other click types never take a crafting result
            }
        }
        player.updateInventory();
    }

    /**
     * Uses up one fragment from every occupied slot. Setting the grid makes the
     * server re-evaluate it, which fires {@link PrepareItemCraftEvent} again and
     * refreshes (or clears) the result.
     */
    private void consumeIngredients(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getAmount() <= 1) {
                matrix[i] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
            }
        }
        inventory.setMatrix(matrix);
    }

    private boolean containsFragment(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == Material.DISC_FRAGMENT_5 && fragmentDiscId(item) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The disc a crafting grid makes, or null.
     *
     * <p>Every occupied slot must hold a fragment of one and the same disc, and
     * the number of occupied slots must equal that disc's fragment count. Any
     * other item in the grid voids the recipe - it would otherwise be used up
     * along with the fragments.
     */
    private CustomDisc matchRecipe(ItemStack[] matrix) {
        Map<String, Integer> fragmentCounts = new HashMap<>();
        int totalFragments = 0;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() != Material.DISC_FRAGMENT_5) {
                return null;
            }
            String discId = fragmentDiscId(item);
            if (discId == null) {
                return null;
            }
            fragmentCounts.merge(discId, 1, Integer::sum);
            totalFragments++;
        }

        // Check if we have exactly one disc type with the required amount
        if (fragmentCounts.size() != 1) {
            return null;
        }
        String discId = fragmentCounts.keySet().iterator().next();
        CustomDisc disc = plugin.getDiscManager().getDisc(discId);
        if (disc == null || !disc.hasFragments() || totalFragments != disc.getFragmentCount()) {
            return null;
        }
        return disc;
    }

    private String fragmentDiscId(ItemStack item) {
        DiscFragment fragment = plugin.getDiscManager().getFragmentFromItem(item);
        if (fragment != null) {
            return fragment.getDiscId();
        }
        // Lore-based ID extraction for fragments created before the PDC tag
        return DiscFragment.getFragmentDiscId(item);
    }
}
