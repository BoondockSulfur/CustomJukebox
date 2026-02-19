package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unmutes all custom jukebox playback.
 * Usage: /cjb unmute [restart]
 */
public class UnmuteSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public UnmuteSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "unmute";
    }

    @Override
    public String getDescription() {
        return "Unmute all playback (restores previous volume)";
    }

    @Override
    public String getUsage() {
        return "/cjb unmute [restart]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.volume";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Check if muted
        if (!plugin.getConfigManager().isMuted()) {
            // Not muted
            float currentVolume = plugin.getConfigManager().getVolume();
            sender.sendMessage(plugin.getLanguageManager().getMessage("unmute-not-muted")
                .replace("{volume}", String.format("%.2f", currentVolume)));
            return true;
        }

        // Get volume before unmuting (for message)
        float restoredVolume = plugin.getConfigManager().getVolumeBeforeMute();

        // Try to unmute
        boolean success = plugin.getConfigManager().unmute();

        if (!success) {
            // This shouldn't happen, but just in case
            sender.sendMessage(plugin.getLanguageManager().getMessage("unmute-not-muted")
                .replace("{volume}", String.format("%.2f", plugin.getConfigManager().getVolume())));
            return true;
        }

        // Check for restart parameter
        boolean restart = false;
        if (args.length > 0) {
            String restartArg = args[0].toLowerCase();
            if (restartArg.equals("restart") || restartArg.equals("true") || restartArg.equals("yes")) {
                restart = true;
            }
        }

        // Restart active playbacks if requested
        if (restart) {
            plugin.getPlaybackManager().restartAllPlaybacks();
        }

        // Send success message
        String message = plugin.getLanguageManager().getMessage("unmute-success")
            .replace("{volume}", String.format("%.2f", restoredVolume));

        if (restart) {
            message += " " + plugin.getLanguageManager().getMessage("volume-restarted");
        }

        sender.sendMessage(message);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest restart option
            return Arrays.asList("restart")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
