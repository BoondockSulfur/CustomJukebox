package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GiveSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public GiveSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getDescription() {
        return "Give a custom disc to a player";
    }

    @Override
    public String getUsage() {
        return "/cjb give <player> <disc> [amount]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.give";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-give"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("player-not-found"));
            return true;
        }

        String discId = args[1];
        CustomDisc disc = plugin.getDiscManager().getDisc(discId);

        if (disc == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("invalid-disc"));
            return true;
        }

        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
                amount = Math.max(1, Math.min(64, amount));
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("error-invalid-amount"));
                return true;
            }
        }

        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(disc.createItemStack());
        }

        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("disc-given")
            .replace("{disc}", disc.getDisplayName())
            .replace("{player}", target.getName()));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Disc IDs and display names (without color codes for better matching)
            List<String> suggestions = new ArrayList<>();
            String input = args[1].toLowerCase();

            for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
                // Add disc ID if it matches
                if (disc.getId().toLowerCase().startsWith(input)) {
                    suggestions.add(disc.getId());
                }
                // Also suggest if display name matches (strip color codes)
                String strippedName = de.boondocksulfur.customjukebox.utils.AdventureUtil.stripColor(disc.getDisplayName());
                if (strippedName != null && strippedName.toLowerCase().startsWith(input)) {
                    // Use ID in suggestion but user can type display name
                    if (!suggestions.contains(disc.getId())) {
                        suggestions.add(disc.getId());
                    }
                }
            }

            return suggestions;
        }

        return new ArrayList<>();
    }
}
