package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
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
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-header"));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-total", "amount", String.valueOf(plugin.getDiscManager().getAllDiscs().size())));
        MessageUtil.sendMessage(sender, "");

        for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-disc-format", "disc", disc.getId()));
            MessageUtil.sendMessage(sender, "  &7Name: &f" + disc.getDisplayName());
            MessageUtil.sendMessage(sender, "  &7Author: &f" + disc.getAuthor());

            if (disc.hasCustomSound()) {
                MessageUtil.sendMessage(sender, "  &7Sound: &b" + disc.getSoundKey());
                MessageUtil.sendMessage(sender, "  &7Duration: &b" + disc.getDurationSeconds() + "s");
            } else {
                MessageUtil.sendMessage(sender, "  &7Sound: &8(vanilla)");
            }

            if (disc.hasFragments()) {
                MessageUtil.sendMessage(sender, "  &7Fragments: &a" + disc.getFragmentCount() + " required");
            } else {
                MessageUtil.sendMessage(sender, "  &7Fragments: &8(none)");
            }

            MessageUtil.sendMessage(sender, "");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
