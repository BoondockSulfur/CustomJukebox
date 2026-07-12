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

    /**
     * Creates a plugin GUI inventory tagged with a {@link GUIHolder}, so the owning
     * GUI class can reliably recognize its own inventories in event handlers.
     * @param owner GUI class instance that owns the inventory
     * @param size Inventory size (must be multiple of 9)
     * @param title Legacy string title (will be converted to Component)
     * @return Created inventory
     */
    public static Inventory createGuiInventory(Object owner, int size, String title) {
        GUIHolder holder = new GUIHolder(owner);
        Component titleComponent = AdventureUtil.parseComponent(title);
        Inventory inventory = Bukkit.createInventory(holder, size, titleComponent);
        holder.setInventory(inventory);
        return inventory;
    }

    /**
     * Cancels a drag event if it belongs to the given GUI owner and any of the
     * dragged slots lies in the top (GUI) inventory. Shared by all GUI classes.
     */
    public static void cancelDragIntoGui(org.bukkit.event.inventory.InventoryDragEvent event, Object owner) {
        if (!GUIHolder.isOwnedBy(event.getInventory(), owner)) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
