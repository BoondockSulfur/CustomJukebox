package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.NowPlaying;
import de.boondocksulfur.customjukebox.model.PlayerPreferences;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.VolumeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A player's own music settings: turn plugin music on or off, set a personal
 * volume, and see what is currently playing.
 *
 * <p>Both settings persist across sessions. A personal volume replaces the
 * server volume for that player rather than scaling it, and - like every volume
 * change with resource-pack audio - only takes effect from the next track,
 * because a running sound cannot be adjusted, only restarted.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class MusicSubcommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("on", "off", "toggle", "volume", "status");

    private final CustomJukebox plugin;

    public MusicSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "music";
    }

    @Override
    public String getDescription() {
        return "Your own music settings (on/off, volume, status)";
    }

    @Override
    public String getUsage() {
        return "/cjb music <on|off|toggle|volume <0-4|reset>|status>";
    }

    @Override
    public String getPermission() {
        return "customjukebox.music";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "on":
                return setEnabled(player, true);
            case "off":
                return setEnabled(player, false);
            case "toggle":
                return setEnabled(player,
                    !plugin.getPlayerPreferencesManager().isMusicEnabled(player.getUniqueId()));
            case "volume":
                return handleVolume(player, args);
            case "status":
                return handleStatus(player);
            default:
                MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-usage"));
                return true;
        }
    }

    private boolean setEnabled(Player player, boolean enabled) {
        plugin.getPlayerPreferencesManager().setMusicEnabled(player.getUniqueId(), enabled);

        boolean resumed = false;
        if (enabled) {
            // Re-attach now rather than waiting for the next track to start.
            // A jukebox/playlist listener set is only filled when a track
            // begins, so without this the player would stay silent until then.
            resumed = plugin.getAmbientZoneManager().resumeSoundFor(player);
            resumed |= plugin.getPlaybackManager().resumeSoundFor(player) > 0;
        } else {
            // Silence the current track at once instead of at its end
            plugin.getPlaybackManager().stopSoundFor(player);
            plugin.getAmbientZoneManager().stopSoundFor(player);
        }

        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage(enabled ? "music-enabled" : "music-disabled"));
        if (resumed) {
            // Be honest about the offset: the track restarts for this player
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("music-resumed-from-start"));
        }
        return true;
    }

    private boolean handleVolume(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-usage-volume"));
            return true;
        }

        String raw = args[1].toLowerCase(Locale.ROOT);
        if (raw.equals("reset") || raw.equals("inherit") || raw.equals("default")) {
            plugin.getPlayerPreferencesManager()
                .setPersonalVolume(player.getUniqueId(), PlayerPreferences.VOLUME_INHERIT);
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-volume-reset"));
            return true;
        }

        // Same vocabulary as the server and zone commands: presets, percent, dB
        float volume = VolumeUtil.parse(raw);
        if (volume == VolumeUtil.INVALID) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("music-volume-invalid", "value", args[1]));
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("zone-volume-presets"));
            return true;
        }

        plugin.getPlayerPreferencesManager().setPersonalVolume(player.getUniqueId(), volume);
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage("music-volume-set", "value", VolumeUtil.describe(volume)));
        if (volume >= VolumeUtil.FULL) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("volume-at-full",
                    java.util.Map.of("value", VolumeUtil.format(volume),
                        "range", String.format(java.util.Locale.ROOT, "%.0f", volume * 16))));
        }

        // A resource pack sound keeps the volume it started with, so without
        // restarting this player's track the setting did nothing until the
        // current one ended - minutes, on a looping zone. Music on/off already
        // re-attached immediately; this now behaves the same way.
        applyVolumeNow(player);
        return true;
    }

    /** Restarts only what this player is hearing, leaving everyone else alone. */
    private void applyVolumeNow(Player player) {
        plugin.getPlaybackManager().stopSoundFor(player);
        plugin.getAmbientZoneManager().stopSoundFor(player);

        boolean resumed = plugin.getAmbientZoneManager().resumeSoundFor(player);
        resumed |= plugin.getPlaybackManager().resumeSoundFor(player) > 0;

        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage(resumed ? "music-volume-applied" : "music-volume-next-track"));
    }

    private boolean handleStatus(Player player) {
        var prefs = plugin.getPlayerPreferencesManager();
        boolean enabled = prefs.isMusicEnabled(player.getUniqueId());
        float personal = prefs.getPersonalVolume(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("state", plugin.getLanguageManager()
            .getRawMessage(enabled ? "music-state-on" : "music-state-off"));
        placeholders.put("volume", personal < 0
            ? plugin.getLanguageManager().getRawMessage("music-volume-inherit")
            : String.format(Locale.ROOT, "%.1f", personal));
        placeholders.put("favorites", String.valueOf(prefs.getFavorites(player.getUniqueId()).size()));
        MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-status", placeholders));

        NowPlaying nowPlaying = plugin.getAmbientZoneManager().getNowPlaying(player);
        if (nowPlaying == null) {
            nowPlaying = plugin.getPlaybackManager().getNowPlaying(player);
        }
        if (nowPlaying == null) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-nothing-playing"));
            return true;
        }

        Map<String, String> playing = new HashMap<>();
        playing.put("disc", nowPlaying.disc().getDisplayName());
        playing.put("author", nowPlaying.disc().getAuthor());
        playing.put("elapsed", NowPlaying.formatTicks(nowPlaying.elapsedTicks()));
        playing.put("duration", nowPlaying.disc().getDurationTicks() > 0
            ? NowPlaying.formatTicks(nowPlaying.disc().getDurationTicks())
            : "?");
        MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("music-now-playing", playing));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("volume")) {
            return Arrays.asList("reset", "0.5", "1", "2", "3", "4").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
