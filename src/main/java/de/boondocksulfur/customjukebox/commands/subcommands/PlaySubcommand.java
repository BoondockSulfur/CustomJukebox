package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plays a custom disc directly at the player's location.
 * Usage: /cjb play <disc> [loop] [global|world|<radius>]
 */
public class PlaySubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public PlaySubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String getDescription() {
        return "Play a custom disc at your location";
    }

    @Override
    public String getUsage() {
        return "/cjb play <disc> [loop] [global|world|<radius>]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.play";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-play"));
            return true;
        }

        Player player = (Player) sender;
        String discId = args[0];
        CustomDisc disc = plugin.getDiscManager().getDisc(discId);

        if (disc == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("invalid-disc"));
            return true;
        }

        // Parse optional parameters (loop and range) - order independent
        boolean loop = false;
        PlaybackRange range = new PlaybackRange(PlaybackRange.RangeType.NORMAL);
        boolean rangeSet = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase();

            // Check if it's a loop parameter
            if (arg.equals("loop") || arg.equals("true") || arg.equals("yes")) {
                loop = true;
                continue;
            }

            // Try to parse as range parameter (if not already set)
            PlaybackRange parsedRange = PlaybackRange.parse(arg);
            if (parsedRange != null) {
                if (rangeSet) {
                    // Range was already set, ignore duplicate
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().warning("Duplicate range parameter ignored: " + arg);
                    }
                } else {
                    range = parsedRange;
                    rangeSet = true;
                }
            } else {
                // Invalid parameter - not loop and not a valid range
                MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playback-invalid-parameter")
                    .replace("{parameter}", arg));
                MessageUtil.sendMessage(sender, "&7Valid parameters: &eloop&7, &eglobal&7, &eworld&7, &e<radius>");
                return true;
            }
        }

        // Start playback at player's block location (ensures consistent location key even if player moves slightly)
        // This prevents sound overlap when using the command multiple times
        org.bukkit.Location playLocation = player.getLocation().getBlock().getLocation();
        plugin.getPlaybackManager().startPlayback(playLocation, disc, loop, range);

        // Send success message
        String message = plugin.getLanguageManager().getMessage("playback-started")
            .replace("{disc}", disc.getDisplayName());

        if (loop) {
            message += " " + plugin.getLanguageManager().getMessage("playback-loop-enabled");
        }

        message += " " + plugin.getLanguageManager().getMessage("playback-range-info")
            .replace("{range}", range.toString());

        MessageUtil.sendMessage(sender, message);

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

        // For args 2 and beyond, suggest parameters that haven't been used yet
        if (args.length >= 2) {
            List<String> suggestions = new ArrayList<>();
            boolean hasLoop = false;
            boolean hasRange = false;

            // Check which parameters were already provided
            for (int i = 1; i < args.length - 1; i++) {
                String arg = args[i].toLowerCase();
                if (arg.equals("loop") || arg.equals("true") || arg.equals("yes")) {
                    hasLoop = true;
                } else if (PlaybackRange.parse(arg) != null) {
                    hasRange = true;
                }
            }

            // Add loop suggestions if not already set
            if (!hasLoop) {
                suggestions.addAll(Arrays.asList("loop", "true", "yes"));
            }

            // Add range suggestions if not already set
            if (!hasRange) {
                suggestions.addAll(Arrays.asList("global", "world", "50", "100", "200"));
            }

            // Filter suggestions based on current input
            String currentArg = args[args.length - 1];
            return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg.toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
