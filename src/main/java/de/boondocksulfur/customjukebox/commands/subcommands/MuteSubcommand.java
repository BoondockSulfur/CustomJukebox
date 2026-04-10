package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mutes all custom jukebox playback.
 * Usage: /cjb mute [restart]
 */
public class MuteSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public MuteSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "mute";
    }

    @Override
    public String getDescription() {
        return "Mute all playback (sets volume to 0)";
    }

    @Override
    public String getUsage() {
        return "/cjb mute [restart]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.volume";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Try to mute
        boolean success = plugin.getConfigManager().mute();

        if (!success) {
            // Already muted
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("mute-already-muted"));
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
        String message = plugin.getLanguageManager().getMessage("mute-success");

        if (restart) {
            message += " " + plugin.getLanguageManager().getMessage("volume-restarted");
        }

        MessageUtil.sendMessage(sender, message);

        // Warn about vanilla discs
        MessageUtil.sendMessage(sender, "&7&oNote: Vanilla music discs cannot be muted (Minecraft limitation)");
        MessageUtil.sendMessage(sender, "&7&oOnly custom discs are affected by mute/volume settings");

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
