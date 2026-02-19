package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InventoryUtil;
import de.boondocksulfur.customjukebox.utils.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuiSubcommand implements SubCommand, Listener {

    private final CustomJukebox plugin;

    public GuiSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
        // Register this as listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "gui";
    }

    @Override
    public String getDescription() {
        return "Open the disc selection GUI";
    }

    @Override
    public String getUsage() {
        return "/cjb gui";
    }

    @Override
    public String getPermission() {
        return "customjukebox.gui";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        Player player = (Player) sender;
        openDiscGui(player);

        return true;
    }

    private void openDiscGui(Player player) {
        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (guiTitle == null || guiTitle.isEmpty()) {
            guiTitle = "Custom Jukebox"; // Fallback
        }

        Inventory gui = InventoryUtil.createInventory(null, 54, guiTitle);

        // Add discs
        int slot = 0;
        for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
            if (slot >= 45) break; // Leave space for admin button
            gui.setItem(slot++, disc.createItemStack());
        }

        // Admin button (only visible for admins)
        if (player.hasPermission("customjukebox.admin")) {
            ItemStack adminButton = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = adminButton.getItemMeta();
            if (meta != null) {
                ItemUtil.setDisplayName(meta, "§6§l⚙ Admin Panel");
                ItemUtil.setLore(meta, Arrays.asList(
                    "§7Manage discs, playlists & categories",
                    "",
                    "§e§lClick to open Admin GUI"
                ));
                adminButton.setItemMeta(meta);
            }
            gui.setItem(49, adminButton);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if it's the main GUI
        String title = AdventureUtil.toLegacy(event.getView().title());
        String guiTitle = plugin.getLanguageManager().getMessage("gui-title");
        if (!title.equals(guiTitle) && !title.equals("Custom Jukebox")) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Check if admin button was clicked
        if (clicked.getType() == Material.NETHER_STAR && event.getSlot() == 49) {
            if (player.hasPermission("customjukebox.admin")) {
                event.setCancelled(true);
                player.closeInventory();
                plugin.getAdminGUI().openMainMenu(player);
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
