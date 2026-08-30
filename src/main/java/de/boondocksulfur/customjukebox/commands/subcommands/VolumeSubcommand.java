package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.VolumeUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Sets the global playback volume.
 * Usage: /cjb volume <0.0-4.0|preset> [restart]
 *
 * Presets:
 * - silent/mute: 0.0
 * - quiet/low: 0.5
 * - normal/default: 1.0
 * - loud/high: 2.0
 * - max/maximum: 4.0
 */
public class VolumeSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public VolumeSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "volume";
    }

    @Override
    public String getDescription() {
        return "Set the global playback volume";
    }

    @Override
    public String getUsage() {
        return "/cjb volume <0.0-4.0|preset> [restart]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.volume";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // If no args, show current volume
        if (args.length == 0) {
            float currentVolume = plugin.getConfigManager().getVolume();
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("volume-current")
                .replace("{volume}", String.format(Locale.ROOT, "%.2f", currentVolume)));
            return true;
        }

        // Parse volume argument: preset name, percentage, or plain number
        String presetName = VolumeUtil.parsePreset(args[0].toLowerCase(Locale.ROOT))
            != VolumeUtil.INVALID ? args[0].toLowerCase(Locale.ROOT) : null;
        float volume = VolumeUtil.parse(args[0]);
        if (volume == VolumeUtil.INVALID) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("volume-invalid"));
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("zone-volume-presets"));
            return true;
        }

        // Set volume
        plugin.getConfigManager().setVolume(volume);

        // Check for restart parameter
        boolean restart = false;
        if (args.length > 1) {
            String restartArg = args[1].toLowerCase();
            if (restartArg.equals("restart") || restartArg.equals("true") || restartArg.equals("yes")) {
                restart = true;
            }
        }

        // Restart active playbacks if requested
        if (restart) {
            plugin.getPlaybackManager().restartAllPlaybacks();
        }

        // Send success message
        String message;
        if (presetName != null) {
            message = plugin.getLanguageManager().getMessage("volume-set-preset")
                .replace("{preset}", presetName)
                .replace("{volume}", String.format(Locale.ROOT, "%.2f", volume));
        } else {
            message = plugin.getLanguageManager().getMessage("volume-set")
                .replace("{volume}", String.format(Locale.ROOT, "%.2f", volume));
        }

        if (restart) {
            message += " " + plugin.getLanguageManager().getMessage("volume-restarted");
        }

        MessageUtil.sendMessage(sender, message);

        return true;
    }

        @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            // Add preset names
            suggestions.addAll(Arrays.asList("silent", "quiet", "normal", "loud", "max"));

            // Add numeric values (0.0 to 4.0 in 0.1 increments)
            for (int i = 0; i <= 40; i++) {
                suggestions.add(String.format(Locale.ROOT, "%.1f", i / 10.0f));
            }

            return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Suggest restart option
            return Arrays.asList("restart")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
