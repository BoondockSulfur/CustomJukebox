package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            sender.sendMessage(plugin.getLanguageManager().getMessage("volume-current")
                .replace("{volume}", String.format("%.2f", currentVolume)));
            return true;
        }

        // Parse volume argument (number or preset)
        float volume;
        String presetName = null;

        try {
            // Try parsing as number first
            volume = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            // Try parsing as preset
            volume = parseVolumePreset(args[0].toLowerCase());
            if (volume == -1) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("volume-invalid"));
                sender.sendMessage("§7Available presets: §esilent§7, §equiet§7, §enormal§7, §eloud§7, §emax");
                return true;
            }
            presetName = args[0].toLowerCase();
        }

        // Validate range (0.0 to 4.0)
        if (volume < 0.0f || volume > 4.0f) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("volume-invalid-range"));
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
                .replace("{volume}", String.format("%.2f", volume));
        } else {
            message = plugin.getLanguageManager().getMessage("volume-set")
                .replace("{volume}", String.format("%.2f", volume));
        }

        if (restart) {
            message += " " + plugin.getLanguageManager().getMessage("volume-restarted");
        }

        sender.sendMessage(message);

        return true;
    }

    /**
     * Parses volume presets.
     * @param preset Preset name (e.g. "low", "normal", "high")
     * @return Volume value or -1 if invalid
     */
    private float parseVolumePreset(String preset) {
        switch (preset) {
            case "silent":
            case "mute":
            case "off":
                return 0.0f;
            case "quiet":
            case "low":
            case "soft":
                return 0.5f;
            case "normal":
            case "default":
            case "medium":
                return 1.0f;
            case "loud":
            case "high":
                return 2.0f;
            case "max":
            case "maximum":
            case "full":
                return 4.0f;
            default:
                return -1;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            // Add preset names
            suggestions.addAll(Arrays.asList("silent", "quiet", "normal", "loud", "max"));

            // Add numeric values (0.0 to 4.0 in 0.1 increments)
            for (int i = 0; i <= 40; i++) {
                suggestions.add(String.format("%.1f", i / 10.0f));
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
