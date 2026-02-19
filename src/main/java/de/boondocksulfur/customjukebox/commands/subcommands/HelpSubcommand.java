package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class HelpSubcommand implements SubCommand {

    private final CustomJukebox plugin;
    private final List<SubCommand> subcommands;

    public HelpSubcommand(CustomJukebox plugin, List<SubCommand> subcommands) {
        this.plugin = plugin;
        this.subcommands = subcommands;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Show help for all commands";
    }

    @Override
    public String getUsage() {
        return "/cjb help";
    }

    @Override
    public String getPermission() {
        return null; // No permission required for help
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-title"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-footer"));
        sender.sendMessage("");
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-version", "version", plugin.getPluginMeta().getVersion()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-loaded-discs", "amount", String.valueOf(plugin.getDiscManager().getAllDiscs().size())));
        sender.sendMessage("");

        for (SubCommand subcommand : subcommands) {
            // Skip help command itself in the list
            if (subcommand instanceof HelpSubcommand) {
                continue;
            }

            String permission = subcommand.getPermission();
            boolean hasPermission = permission == null || sender.hasPermission(permission);

            if (hasPermission) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("help-command-format", "usage", subcommand.getUsage()));
                sender.sendMessage(plugin.getLanguageManager().getMessage("help-description-format", "description", subcommand.getDescription()));
            }
        }

        sender.sendMessage("");
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-help-line"));
        sender.sendMessage("");
        sender.sendMessage(plugin.getLanguageManager().getMessage("help-tip"));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
