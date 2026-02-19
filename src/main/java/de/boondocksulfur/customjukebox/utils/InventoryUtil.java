package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Utility class for creating inventories with Adventure API support.
 * Replaces deprecated Bukkit.createInventory(holder, size, String) calls.
 *
 * @author BoondockSulfur
 * @version 2.1.0
 * @since 2.1.0
 */
public class InventoryUtil {

    /**
     * Creates an inventory with Adventure Component title.
     * @param holder Inventory holder (null for no holder)
     * @param size Inventory size (must be multiple of 9)
     * @param title Legacy string title (will be converted to Component)
     * @return Created inventory
     */
    public static Inventory createInventory(InventoryHolder holder, int size, String title) {
        Component titleComponent = AdventureUtil.parseComponent(title);
        return Bukkit.createInventory(holder, size, titleComponent);
    }

    /**
     * Creates an inventory with Adventure Component title.
     * @param holder Inventory holder (null for no holder)
     * @param size Inventory size (must be multiple of 9)
     * @param title Component title
     * @return Created inventory
     */
    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }
}
