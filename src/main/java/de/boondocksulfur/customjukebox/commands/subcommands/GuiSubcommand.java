package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.utils.GUIHolder;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        Player player = (Player) sender;
        openDiscGui(player);

        return true;
    }

    /**
     * Opens the paged disc selection GUI. Building it lives in JukeboxListener
     * so both entry points (jukebox interaction and this command) share one
     * layout, one paging implementation and one click handler.
     */
    private void openDiscGui(Player player) {
        plugin.getJukeboxListener().openDiscSelection(player, 0);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if it's the main disc GUI (owned by the JukeboxListener)
        if (!GUIHolder.isOwnedBy(event.getInventory(), plugin.getJukeboxListener())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Check if admin button was clicked (cancellation is done by JukeboxListener)
        if (clicked.getType() == Material.NETHER_STAR && event.getSlot() == 49) {
            if (player.hasPermission("customjukebox.admin")) {
                event.setCancelled(true);
                // openInventory replaces the current view - no closeInventory
                // inside the click handler (undefined behavior per Bukkit docs)
                plugin.getAdminGUI().openMainMenu(player);
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
