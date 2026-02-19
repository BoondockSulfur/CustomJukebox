package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscFragment;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FragmentSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public FragmentSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "fragment";
    }

    @Override
    public String getDescription() {
        return "Give disc fragments to a player";
    }

    @Override
    public String getUsage() {
        return "/cjb fragment <player> <disc> [amount]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.give";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-fragment"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player-not-found"));
            return true;
        }

        String discId = args[1];
        DiscFragment fragment = plugin.getDiscManager().getFragment(discId);

        if (fragment == null) {
            String message = plugin.getLanguageManager().getMessage("no-fragments");
            sender.sendMessage(message.replace("{disc}", discId));
            sender.sendMessage(plugin.getLanguageManager().getMessage("fragments-help"));
            return true;
        }

        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
                amount = Math.max(1, Math.min(64, amount));
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("error-invalid-amount"));
            }
        }

        target.getInventory().addItem(fragment.createItemStack(amount));

        String message = plugin.getLanguageManager().getMessage("fragment-given");
        message = message.replace("{amount}", String.valueOf(amount))
                        .replace("{disc}", discId)
                        .replace("{player}", target.getName());
        sender.sendMessage(message);

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
            // Disc IDs and display names (only discs with fragments)
            List<String> suggestions = new ArrayList<>();
            String input = args[1].toLowerCase();

            for (CustomDisc disc : plugin.getDiscManager().getAllDiscs()) {
                if (!disc.hasFragments()) continue;

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
