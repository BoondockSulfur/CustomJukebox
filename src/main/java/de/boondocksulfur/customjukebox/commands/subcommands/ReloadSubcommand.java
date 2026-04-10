package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ReloadSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public ReloadSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }

    @Override
    public String getUsage() {
        return "/cjb reload";
    }

    @Override
    public String getPermission() {
        return "customjukebox.reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reload();
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("plugin-reloaded"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
