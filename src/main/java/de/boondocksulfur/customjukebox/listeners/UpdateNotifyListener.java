package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies players with permission about available updates.
 */
public class UpdateNotifyListener implements Listener {

    private final CustomJukebox plugin;

    public UpdateNotifyListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only notify players with permission
        if (!player.hasPermission("customjukebox.updatenotify")) {
            return;
        }

        // Check if update is available
        if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
            // Delay message by 2 seconds so it doesn't get lost in join spam
            SchedulerUtil.runPlayerTaskLater(plugin, player, () -> {
                player.sendMessage("");
                player.sendMessage("§6§l[CustomJukebox] §eUpdate available!");
                player.sendMessage("§7Current version: §e" + plugin.getUpdateChecker().getCurrentVersion());
                player.sendMessage("§7Latest version: §a" + plugin.getUpdateChecker().getLatestVersion());
                player.sendMessage("§7Download: §bhttps://modrinth.com/plugin/customjukebox");
                player.sendMessage("");
            }, 40L); // 2 seconds delay
        }
    }
}
