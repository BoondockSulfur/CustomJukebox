package de.boondocksulfur.customjukebox.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Paging helpers shared by the plugin's list GUIs.
 *
 * <p>Every list GUI used to fill a fixed slot range and {@code break} once it ran
 * out of slots, so a server with more discs, playlists or categories than fit on
 * one screen simply could not see - or edit - the rest, with no indication that
 * anything had been cut off.
 *
 * @author BoondockSulfur
 * @since 3.3.0
 */
public final class GuiPageUtil {

    private GuiPageUtil() {
    }

    /**
     * Number of pages needed for a collection (at least 1, so an empty list
     * still renders as "page 1 of 1" rather than "page 1 of 0").
     *
     * @param totalItems total number of entries
     * @param perPage entries per page (must be positive)
     * @return page count, minimum 1
     */
    public static int pageCount(int totalItems, int perPage) {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (totalItems + perPage - 1) / perPage);
    }

    /**
     * Clamps a page index into the valid range for the given collection size.
     *
     * @param page requested zero-based page
     * @param totalItems total number of entries
     * @param perPage entries per page
     * @return valid zero-based page index
     */
    public static int clampPage(int page, int totalItems, int perPage) {
        return Math.max(0, Math.min(page, pageCount(totalItems, perPage) - 1));
    }

    /**
     * Returns the entries belonging to one page.
     *
     * @param all all entries, in display order
     * @param page zero-based page index
     * @param perPage entries per page
     * @param <T> entry type
     * @return the page's entries (possibly empty, never null)
     */
    public static <T> List<T> slice(Collection<T> all, int page, int perPage) {
        List<T> list = new ArrayList<>(all);
        int from = Math.max(0, page * perPage);
        if (from >= list.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(from, Math.min(list.size(), from + perPage)));
    }

    /**
     * "Previous page" button, or null when already on the first page.
     *
     * @param page current zero-based page
     * @return button item or null
     */
    public static ItemStack previousButton(int page) {
        if (page <= 0) {
            return null;
        }
        return navItem(Material.SPECTRAL_ARROW, "§e« Previous page",
            "§7Go to page " + page);
    }

    /**
     * "Next page" button, or null when already on the last page.
     *
     * @param page current zero-based page
     * @param pageCount total number of pages
     * @return button item or null
     */
    public static ItemStack nextButton(int page, int pageCount) {
        if (page >= pageCount - 1) {
            return null;
        }
        return navItem(Material.SPECTRAL_ARROW, "§eNext page »",
            "§7Go to page " + (page + 2));
    }

    /**
     * Page indicator showing the current page and the total entry count.
     *
     * @param page current zero-based page
     * @param pageCount total number of pages
     * @param totalItems total number of entries
     * @param entryLabel plural noun for the entries (e.g. "discs")
     * @return indicator item
     */
    public static ItemStack pageIndicator(int page, int pageCount, int totalItems, String entryLabel) {
        return navItem(Material.PAPER,
            "§6Page §e" + (page + 1) + "§6 / §e" + pageCount,
            "§7" + totalItems + " " + entryLabel + " total");
    }

    private static ItemStack navItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemUtil.setDisplayName(meta, name);
            ItemUtil.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
