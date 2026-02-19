package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ListSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public ListSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List all custom discs";
    }

    @Override
    public String getUsage() {
        return "/cjb list";
    }

    @Override
    public String getPermission() {
        return "customjukebox.list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("list-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("list-total", "amount", String.valueOf(plugin.getDiscManager().getAllDiscs().size())));
        sender.sendMessage("");

        for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("list-disc-format", "disc", disc.getId()));
            sender.sendMessage("  §7Name: §f" + disc.getDisplayName());
            sender.sendMessage("  §7Author: §f" + disc.getAuthor());

            if (disc.hasCustomSound()) {
                sender.sendMessage("  §7Sound: §b" + disc.getSoundKey());
                sender.sendMessage("  §7Duration: §b" + disc.getDurationSeconds() + "s");
            } else {
                sender.sendMessage("  §7Sound: §8(vanilla)");
            }

            if (disc.hasFragments()) {
                sender.sendMessage("  §7Fragments: §a" + disc.getFragmentCount() + " required");
            } else {
                sender.sendMessage("  §7Fragments: §8(none)");
            }

            sender.sendMessage("");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
