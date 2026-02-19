package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InfoSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public InfoSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Show detailed information about a disc";
    }

    @Override
    public String getUsage() {
        return "/cjb info <disc>";
    }

    @Override
    public String getPermission() {
        return "customjukebox.info";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-info"));
            return true;
        }

        String discId = args[0];
        CustomDisc disc = plugin.getDiscManager().getDisc(discId);

        if (disc == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("invalid-disc"));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("info-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("info-id", "value", disc.getId()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("info-display-name", "value", disc.getDisplayName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("info-author", "value", disc.getAuthor()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("info-type", "value", disc.getDiscType().name()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("info-custom-model-data", "value", String.valueOf(disc.getCustomModelData())));
        sender.sendMessage("");

        if (disc.getDescription() != null && !disc.getDescription().isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-description", "value", disc.getDescription()));
            sender.sendMessage("");
        }

        if (disc.hasCustomSound()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-sound-header"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-sound-key", "value", disc.getSoundKey()));

            java.util.Map<String, String> durationPlaceholders = new java.util.HashMap<>();
            durationPlaceholders.put("seconds", String.valueOf(disc.getDurationSeconds()));
            durationPlaceholders.put("ticks", String.valueOf(disc.getDurationTicks()));
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-duration", durationPlaceholders));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-sound-vanilla"));
        }

        sender.sendMessage("");

        if (disc.hasFragments()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-fragment-header"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-fragment-required", "amount", String.valueOf(disc.getFragmentCount())));
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-fragment-enabled"));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("info-fragment-disabled"));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Disc IDs and display names (without color codes for better matching)
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();

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
