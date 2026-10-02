package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import de.boondocksulfur.customjukebox.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tells operators about an available update when they join.
 *
 * <p>The console line is easy to miss on a server that prints hundreds of
 * startup lines, and the download pages are only clickable in chat. Operators
 * only: everyone else can neither install nor act on it.
 */
public class UpdateNotifyListener implements Listener {

    private final CustomJukebox plugin;

    public UpdateNotifyListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UpdateChecker checker = plugin.getUpdateChecker();
        if (!player.isOp() || checker == null || !checker.isUpdateAvailable()) {
            return;
        }

        // Delay message by 2 seconds so it doesn't get lost in join spam
        SchedulerUtil.runPlayerTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            String text = plugin.getLanguageManager().getRawMessage("update-available")
                .replace("{current}", checker.getCurrentVersion())
                .replace("{latest}", String.valueOf(checker.getLatestVersion()));
            Component message = AdventureUtil.parseComponent(text)
                .append(Component.space())
                .append(downloadLink("update-link-modrinth", UpdateChecker.URL_MODRINTH))
                .append(Component.space())
                .append(downloadLink("update-link-curseforge", UpdateChecker.URL_CURSEFORGE));
            player.sendMessage(message);
        }, 40L);
    }

    private Component downloadLink(String labelKey, String url) {
        String hover = plugin.getLanguageManager().getRawMessage("update-link-hover").replace("{url}", url);
        return AdventureUtil.parseComponent(plugin.getLanguageManager().getRawMessage(labelKey))
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(AdventureUtil.parseComponent(hover)));
    }
}
