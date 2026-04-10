package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Stops all active playbacks.
 * Usage: /cjb stop
 */
public class StopSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public StopSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getDescription() {
        return "Stop all active playbacks";
    }

    @Override
    public String getUsage() {
        return "/cjb stop";
    }

    @Override
    public String getPermission() {
        return "customjukebox.stop";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Stop all active playbacks
        plugin.getPlaybackManager().stopAllPlaybacks();

        // Send success message
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playback-all-stopped"));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
