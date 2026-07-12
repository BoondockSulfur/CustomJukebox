package de.boondocksulfur.customjukebox.utils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder that tags plugin GUIs with their owning GUI class instance.
 * Listeners identify their own inventories via the holder instead of comparing
 * titles or per-player context maps, so clicks in foreign inventories (chests,
 * other plugin GUIs) can never be misinterpreted as menu clicks.
 *
 * @author BoondockSulfur
 * @since 3.2.0
 */
public class GUIHolder implements InventoryHolder {

    private final Object owner;
    private Inventory inventory;

    public GUIHolder(Object owner) {
        this.owner = owner;
    }

    /**
     * @return the GUI class instance that created this inventory
     */
    public Object getOwner() {
        return owner;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Checks whether the given inventory is a plugin GUI created by the given owner.
     * Uses getHolder(false) so block containers (chests etc.) don't pay for a
     * block-state snapshot on every event.
     */
    public static boolean isOwnedBy(Inventory inventory, Object owner) {
        return inventory != null
            && inventory.getHolder(false) instanceof GUIHolder holder
            && holder.getOwner() == owner;
    }
}
