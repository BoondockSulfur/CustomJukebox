package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for ItemStack and ItemMeta operations with Adventure API.
 * Provides helper methods to avoid deprecated API calls.
 *
 * @author BoondockSulfur
 * @version 2.1.0
 * @since 2.1.0
 */
public class ItemUtil {

    /**
     * PDC key that stores the disc ID on custom disc items.
     * Makes discs uniquely identifiable even if two discs share the same
     * material and CustomModelData.
     */
    public static final NamespacedKey DISC_ID_KEY =
        Objects.requireNonNull(NamespacedKey.fromString("customjukebox:disc_id"));

    /**
     * PDC key that stores the target disc ID on fragment items.
     */
    public static final NamespacedKey FRAGMENT_DISC_ID_KEY =
        Objects.requireNonNull(NamespacedKey.fromString("customjukebox:fragment_disc_id"));

    /**
     * Reads a string from an item's PersistentDataContainer.
     * @param item Item to read from
     * @param key PDC key
     * @return Stored value, or null if absent
     */
    public static String getPdcString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * Sets display name on ItemMeta using Adventure API.
     * @param meta ItemMeta to modify
     * @param displayName Display name (legacy string with color codes)
     */
    public static void setDisplayName(ItemMeta meta, String displayName) {
        if (meta != null && displayName != null) {
            meta.displayName(AdventureUtil.parseComponent(displayName));
        }
    }

    /**
     * Sets lore on ItemMeta using Adventure API.
     * @param meta ItemMeta to modify
     * @param lore Lore lines (legacy strings with color codes)
     */
    public static void setLore(ItemMeta meta, List<String> lore) {
        if (meta != null && lore != null) {
            meta.lore(AdventureUtil.parseComponents(lore));
        }
    }

    /**
     * Sets lore on ItemMeta using Adventure API (varargs).
     * @param meta ItemMeta to modify
     * @param lore Lore lines (legacy strings with color codes)
     */
    public static void setLore(ItemMeta meta, String... lore) {
        if (meta != null && lore != null) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            setLore(meta, loreList);
        }
    }

    /**
     * Gets display name from ItemMeta as legacy string.
     * @param meta ItemMeta to read from
     * @return Display name as legacy string, or null
     */
    public static String getDisplayName(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        Component displayName = meta.displayName();
        return displayName != null ? AdventureUtil.toLegacy(displayName) : null;
    }

    /**
     * Gets lore from ItemMeta as legacy strings.
     * @param meta ItemMeta to read from
     * @return Lore as list of legacy strings, or null
     */
    public static List<String> getLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<Component> loreComponents = meta.lore();
        if (loreComponents == null) {
            return null;
        }

        List<String> lore = new ArrayList<>();
        for (Component component : loreComponents) {
            lore.add(AdventureUtil.toLegacy(component));
        }
        return lore;
    }

    /**
     * Checks if ItemMeta has lore.
     * @param meta ItemMeta to check
     * @return true if has lore
     */
    public static boolean hasLore(ItemMeta meta) {
        return meta != null && meta.hasLore();
    }
}
