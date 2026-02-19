package de.boondocksulfur.customjukebox.model;

import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a disc fragment that can be crafted into a complete CustomDisc.
 * Similar to vanilla DISC_FRAGMENT_5 but for custom discs.
 */
public class DiscFragment {

    private final String discId;          // ID of the disc this fragment belongs to
    private final String displayName;     // e.g. "§bFragment - Ocean Dreams"
    private final int customModelData;    // CustomModelData for resource pack texture
    private final Material fragmentType;  // Usually DISC_FRAGMENT_5

    public DiscFragment(String discId, String displayName, int customModelData, Material fragmentType) {
        this.discId = discId;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.fragmentType = fragmentType;
    }

    public String getDiscId() {
        return discId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public Material getFragmentType() {
        return fragmentType;
    }

    /**
     * Creates an ItemStack for this fragment.
     * @return ItemStack with proper metadata
     */
    public ItemStack createItemStack() {
        return createItemStack(1);
    }

    /**
     * Creates an ItemStack for this fragment with specified amount.
     * @param amount Number of fragments
     * @return ItemStack with proper metadata
     */
    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(fragmentType, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Use Adventure API for display name
            meta.displayName(AdventureUtil.parseComponent(displayName));

            // Convert lore to Adventure Components
            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(AdventureUtil.parseComponent("§7Fragment for crafting"));
            loreComponents.add(AdventureUtil.parseComponent("§8ID: " + discId));
            meta.lore(loreComponents);

            meta.setCustomModelData(customModelData);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Checks if an ItemStack matches this fragment.
     * @param item ItemStack to check
     * @return true if the item is this fragment
     */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != fragmentType) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.hasCustomModelData() && meta.getCustomModelData() == customModelData;
    }

    /**
     * Gets the fragment ID from an ItemStack's lore.
     * Used to identify which disc this fragment belongs to.
     * @param item ItemStack to check
     * @return disc ID or null if not a fragment
     */
    public static String getFragmentDiscId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }

        // Use Adventure API to get lore
        List<Component> loreComponents = meta.lore();
        if (loreComponents == null || loreComponents.size() < 2) {
            return null;
        }

        // Convert components to legacy string for parsing
        for (Component component : loreComponents) {
            String line = AdventureUtil.toLegacy(component);
            if (line.startsWith("§8ID: ")) {
                return line.substring(6);
            }
        }

        return null;
    }
}
