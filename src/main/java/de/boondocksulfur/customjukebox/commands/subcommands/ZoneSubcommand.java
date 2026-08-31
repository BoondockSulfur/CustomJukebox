package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.AmbientZone;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.VolumeUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manages ambient music zones - areas that auto-play a looping playlist to
 * everyone inside them.
 *
 * Usage: /cjb zone &lt;list|info|create|delete|playlist|radius|center|pos1|pos2|
 *                     region|global|height|loop|shuffle|sync|playback|volume|
 *                     priority|enable|disable|edit|reload&gt; [args...]
 */
public class ZoneSubcommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList(
        "list", "info", "create", "delete", "playlist", "radius", "center",
        "pos1", "pos2", "region", "global", "height", "loop", "shuffle", "sync",
        "playback", "volume", "priority", "enable", "disable", "edit", "reload");

    private final CustomJukebox plugin;

    public ZoneSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "zone";
    }

    @Override
    public String getDescription() {
        return "Manage ambient music zones";
    }

    @Override
    public String getUsage() {
        return "/cjb zone <list|info|create|delete|playlist|radius|center|pos1|pos2|region|global|height|loop|shuffle|sync|playback|volume|priority|enable|disable|edit|reload> [args...]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.zone";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list":     return handleList(sender);
            case "info":     return handleInfo(sender, args);
            case "create":   return handleCreate(sender, args);
            case "delete":   return handleDelete(sender, args);
            case "playlist": return handlePlaylist(sender, args);
            case "radius":   return handleRadius(sender, args);
            case "center":   return handleCenter(sender, args);
            case "pos1":     return handlePos(sender, args, 1);
            case "pos2":     return handlePos(sender, args, 2);
            case "region":   return handleRegion(sender, args);
            case "global":   return handleGlobal(sender, args);
            case "shuffle":  return handleShuffle(sender, args);
            case "height":   return handleHeight(sender, args);
            case "loop":     return handleLoop(sender, args);
            case "sync":     return handleSync(sender, args);
            case "playback": return handlePlayback(sender, args);
            case "volume":   return handleVolume(sender, args);
            case "priority": return handlePriority(sender, args);
            case "enable":   return handleToggle(sender, args, true);
            case "disable":  return handleToggle(sender, args, false);
            case "edit":     return handleEdit(sender, args);
            case "reload":   return handleReload(sender);
            default:
                usage(sender);
                return true;
        }
    }

    private boolean handleList(CommandSender sender) {
        if (plugin.getAmbientZoneManager().getAllZones().isEmpty()) {
            msg(sender, "zone-none");
            return true;
        }
        msg(sender, "zone-list-header");
        for (AmbientZone zone : plugin.getAmbientZoneManager().getAllZones()) {
            String state = !zone.isEnabled() ? "&8[disabled]"
                : plugin.getAmbientZoneManager().isZoneActive(zone.getId()) ? "&a[active]" : "&e[idle]";
            MessageUtil.sendMessage(sender, "&7- &f" + zone.getId() + " " + state
                + " &7» playlist: &f" + (zone.getPlaylistId().isEmpty() ? "&8none" : zone.getPlaylistId()));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        MessageUtil.sendMessage(sender, "&6Zone &e" + zone.getId());
        MessageUtil.sendMessage(sender, "&7Enabled: &f" + zone.isEnabled()
            + "  &7Active: &f" + plugin.getAmbientZoneManager().isZoneActive(zone.getId()));
        MessageUtil.sendMessage(sender, "&7Type: &f" + zone.getType().name().toLowerCase(Locale.ROOT)
            + "  &7World: &f" + zone.getWorld());
        if (zone.getType() == AmbientZone.ZoneType.GLOBAL) {
            MessageUtil.sendMessage(sender, "&7Scope: &fentire server (radio)");
        } else if (zone.getType() == AmbientZone.ZoneType.WORLDGUARD) {
            MessageUtil.sendMessage(sender, "&7Region: &f" + (zone.getRegion().isEmpty() ? "&8unset" : zone.getRegion()));
        } else if (zone.getType() == AmbientZone.ZoneType.CUBOID) {
            MessageUtil.sendMessage(sender, "&7Pos1: &f" + (zone.isPos1Set()
                ? zone.getX1() + ", " + zone.getY1() + ", " + zone.getZ1() : "&8unset")
                + "  &7Pos2: &f" + (zone.isPos2Set()
                ? zone.getX2() + ", " + zone.getY2() + ", " + zone.getZ2() : "&8unset"));
        } else {
            MessageUtil.sendMessage(sender, String.format(Locale.ROOT, "&7Center: &f%.1f, %.1f, %.1f  &7Radius: &f%.1f",
                zone.getCenterX(), zone.getCenterY(), zone.getCenterZ(), zone.getRadius()));
        }
        MessageUtil.sendMessage(sender, "&7Height: &f" + (zone.isFullHeight() ? "full (any Y)" : "limited (bounded Y)"));
        MessageUtil.sendMessage(sender, "&7Playlist: &f" + (zone.getPlaylistId().isEmpty() ? "&8none" : zone.getPlaylistId())
            + "  &7Loop: &f" + zone.isLoop()
            + "  &7Shuffle: &f" + zone.isShuffle());
        MessageUtil.sendMessage(sender, "&7Playback: &f" + zone.getPlaybackMode().name().toLowerCase(Locale.ROOT)
            + "  &7Sync: &f" + zone.getSyncMode().name().toLowerCase(Locale.ROOT)
            + "  &7Volume: &f" + (zone.inheritsVolume() ? "inherit" : formatNumber(zone.getVolume()))
            + "  &7Priority: &f" + zone.getPriority());
        warnIfIdle(sender, zone);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "zone-usage-create");
            return true;
        }
        String id = args[1];
        // Zone ids are JSON keys and command arguments - same rules as disc,
        // category and playlist ids
        if (!InputValidator.isValidZoneId(id)) {
            msg(sender, "zone-invalid-id", "value", id);
            return true;
        }
        if (plugin.getAmbientZoneManager().getZone(id) != null) {
            msg(sender, "zone-exists", "zone", id);
            return true;
        }
        // Seed sensible defaults from the player's position when available;
        // the initializer runs before the zone is written, so creating a zone
        // costs a single file write instead of two.
        AmbientZone zone = plugin.getAmbientZoneManager().createZone(id, newZone -> {
            if (sender instanceof Player) {
                Location loc = ((Player) sender).getLocation();
                newZone.setWorld(loc.getWorld().getName());
                newZone.setCenter(loc.getX(), loc.getY(), loc.getZ());
            }
        });
        if (zone == null) {
            msg(sender, "zone-create-failed", "zone", id);
            return true;
        }
        msg(sender, "zone-created", "zone", id);
        msg(sender, "zone-created-hint", "zone", id);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "zone-usage-generic");
            return true;
        }
        boolean removed = plugin.getAmbientZoneManager().deleteZone(args[1]);
        msg(sender, removed ? "zone-deleted" : "zone-not-found", "zone", args[1]);
        return true;
    }

    private boolean handlePlaylist(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-playlist");
            return true;
        }
        String playlistId = args[2];
        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);
        if (playlist == null) {
            msg(sender, "playlist-not-found", "playlist", playlistId);
            return true;
        }
        zone.setPlaylistId(playlistId);
        return applied(sender, zone, "zone-playlist-set", "playlist", playlistId);
    }

    private boolean handleRadius(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-radius");
            return true;
        }
        Double radius = parseDouble(sender, args[2]);
        if (radius == null) {
            return true;
        }
        if (radius <= 0 || radius > 10000) {
            msg(sender, "zone-radius-range");
            return true;
        }
        zone.setType(AmbientZone.ZoneType.RADIUS);
        zone.setRadius(radius);
        return applied(sender, zone, "zone-radius-set", "value", formatNumber(radius));
    }

    private boolean handleCenter(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (!(sender instanceof Player)) {
            msg(sender, "command-only-players");
            return true;
        }
        Location loc = ((Player) sender).getLocation();
        zone.setType(AmbientZone.ZoneType.RADIUS);
        zone.setWorld(loc.getWorld().getName());
        zone.setCenter(loc.getX(), loc.getY(), loc.getZ());
        return applied(sender, zone, "zone-center-set");
    }

    private boolean handlePos(CommandSender sender, String[] args, int which) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (!(sender instanceof Player)) {
            msg(sender, "command-only-players");
            return true;
        }
        Location loc = ((Player) sender).getLocation();
        zone.setType(AmbientZone.ZoneType.CUBOID);
        zone.setWorld(loc.getWorld().getName());
        if (which == 1) {
            zone.setPos1(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } else {
            zone.setPos2(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
        String coords = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        applied(sender, zone, "zone-pos-set", "pos", String.valueOf(which), "coords", coords);
        if (!zone.hasBothCorners()) {
            msg(sender, "zone-pos-need-other", "zone", zone.getId());
        }
        return true;
    }

    private boolean handlePlayback(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-playback");
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("synced") || mode.equals("sync")) {
            zone.setPlaybackMode(AmbientZone.PlaybackMode.SYNCED);
        } else if (mode.equals("individual") || mode.equals("single")) {
            zone.setPlaybackMode(AmbientZone.PlaybackMode.INDIVIDUAL);
        } else {
            msg(sender, "zone-usage-playback");
            return true;
        }
        return applied(sender, zone, "zone-playback-set",
            "value", zone.getPlaybackMode().name().toLowerCase(Locale.ROOT));
    }

    private boolean handleRegion(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-region");
            return true;
        }
        if (!plugin.getIntegrationManager().isWorldGuardEnabled()) {
            msg(sender, "zone-worldguard-missing");
            return true;
        }
        zone.setType(AmbientZone.ZoneType.WORLDGUARD);
        zone.setRegion(args[2]);
        if (sender instanceof Player) {
            // A WorldGuard region is world-scoped; bind to the player's world.
            zone.setWorld(((Player) sender).getWorld().getName());
        }
        return applied(sender, zone, "zone-region-set", "value", args[2]);
    }

    /**
     * Turns a zone into a server-wide radio station: it reaches every player in
     * every world. A local zone with a higher priority still wins for players
     * standing inside it.
     */
    private boolean handleGlobal(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        zone.setType(AmbientZone.ZoneType.GLOBAL);
        return applied(sender, zone, "zone-global-set");
    }

    private boolean handleShuffle(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-generic");
            return true;
        }
        zone.setShuffle(parseBool(args[2]));
        return applied(sender, zone, "zone-shuffle-set", "value", String.valueOf(zone.isShuffle()));
    }

    private boolean handleHeight(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-height");
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("full") || mode.equals("unlimited") || mode.equals("column")) {
            zone.setFullHeight(true);
        } else if (mode.equals("limited") || mode.equals("bounded") || mode.equals("3d")) {
            zone.setFullHeight(false);
        } else {
            msg(sender, "zone-usage-height");
            return true;
        }
        return applied(sender, zone, "zone-height-set", "value", zone.isFullHeight() ? "full" : "limited");
    }

    private boolean handleLoop(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-generic");
            return true;
        }
        zone.setLoop(parseBool(args[2]));
        return applied(sender, zone, "zone-loop-set", "value", String.valueOf(zone.isLoop()));
    }

    private boolean handleSync(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-sync");
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("immediate")) {
            zone.setSyncMode(AmbientZone.SyncMode.IMMEDIATE);
        } else if (mode.equals("next_track") || mode.equals("next")) {
            zone.setSyncMode(AmbientZone.SyncMode.NEXT_TRACK);
        } else {
            msg(sender, "zone-usage-sync");
            return true;
        }
        return applied(sender, zone, "zone-sync-set",
            "value", zone.getSyncMode().name().toLowerCase(Locale.ROOT));
    }

    /**
     * {@code /cjb zone volume <zone> <inherit|preset|0-4|percent|dB> [norestart]}
     *
     * <p>Applies immediately by restarting the zone, because a resource-pack
     * sound keeps the volume it started with and would otherwise stay loud
     * until the current track ends.
     */
    private boolean handleVolume(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-volume");
            return true;
        }

        if (args[2].equalsIgnoreCase("inherit")) {
            zone.setVolume(AmbientZone.VOLUME_INHERIT);
        } else {
            float volume = VolumeUtil.parse(args[2]);
            if (volume == VolumeUtil.INVALID) {
                msg(sender, "zone-volume-range");
                MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                    .getMessage("zone-volume-presets"));
                return true;
            }
            zone.setVolume(volume);
        }

        boolean restart = !(args.length > 3 && args[3].equalsIgnoreCase("norestart"));
        String value = zone.inheritsVolume()
            ? "inherit"
            : VolumeUtil.describe(zone.getVolume());

        // The volume is part of the playback signature, so saving with
        // applyLive restarts the zone by itself. Suppressing that is the only
        // way `norestart` can mean anything.
        applied(sender, zone, restart, "zone-volume-set", "value", value);
        msg(sender, restart ? "zone-volume-restarted" : "zone-volume-next-track");
        return true;
    }

    private boolean handlePriority(CommandSender sender, String[] args) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        if (args.length < 3) {
            msg(sender, "zone-usage-generic");
            return true;
        }
        try {
            zone.setPriority(Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            msg(sender, "zone-not-a-number", "value", args[2]);
            return true;
        }
        return applied(sender, zone, "zone-priority-set", "value", String.valueOf(zone.getPriority()));
    }

    private boolean handleToggle(CommandSender sender, String[] args, boolean enabled) {
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        zone.setEnabled(enabled);
        return applied(sender, zone, enabled ? "zone-enabled" : "zone-disabled");
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            msg(sender, "command-only-players");
            return true;
        }
        AmbientZone zone = require(sender, args);
        if (zone == null) {
            return true;
        }
        plugin.getZoneEditorGUI().openEditor((Player) sender, zone);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getAmbientZoneManager().reload();
        msg(sender, "zone-reloaded");
        return true;
    }

    // ==================== HELPERS ====================

    /**
     * Persists an edited zone, confirms the change, and - this is the point -
     * tells the sender in-game when the zone still cannot play.
     *
     * <p>Previously every one of these subcommands reported plain success while
     * the zone silently stayed idle (empty playlist, discs without a duration,
     * missing WorldGuard region, ...) with nothing but a console warning.
     *
     * @param sender command sender
     * @param zone zone that was edited
     * @param key language key for the success message
     * @param pairs additional placeholder/value pairs ({@code zone} is added
     *              automatically)
     * @return always true (commands report their own errors)
     */
    private boolean applied(CommandSender sender, AmbientZone zone, String key, String... pairs) {
        return applied(sender, zone, true, key, pairs);
    }

    private boolean applied(CommandSender sender, AmbientZone zone, boolean applyLive,
                            String key, String... pairs) {
        plugin.getAmbientZoneManager().saveZone(zone, applyLive);

        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("zone", zone.getId());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage(key, placeholders));

        warnIfIdle(sender, zone);
        return true;
    }

    /**
     * Sends a warning describing why a zone is not playing, if it is not.
     */
    private void warnIfIdle(CommandSender sender, AmbientZone zone) {
        String reasonKey = plugin.getAmbientZoneManager().getIdleReasonKey(zone);
        if (reasonKey == null) {
            return;
        }
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("zone", zone.getId());
        placeholders.put("playlist", zone.getPlaylistId() == null ? "" : zone.getPlaylistId());
        placeholders.put("world", zone.getWorld());
        placeholders.put("region", zone.getRegion());
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage(reasonKey, placeholders));
    }

    /**
     * Formats a number without a trailing ".0" and locale-independently, so a
     * radius of 60 reads as "60" rather than "60.0" (or "60,0" on a German JVM).
     */
    private String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Resolves the zone named in {@code args[1]}, sending an error and returning
     * null when it's missing or not found.
     */
    private AmbientZone require(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "zone-usage-generic");
            return null;
        }
        AmbientZone zone = plugin.getAmbientZoneManager().getZone(args[1]);
        if (zone == null) {
            msg(sender, "zone-not-found", "zone", args[1]);
        }
        return zone;
    }

    private Double parseDouble(CommandSender sender, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            msg(sender, "zone-not-a-number", "value", raw);
            return null;
        }
    }

    private boolean parseBool(String raw) {
        return raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("yes") || raw.equalsIgnoreCase("on");
    }

    private void usage(CommandSender sender) {
        msg(sender, "zone-usage");
    }

    private void msg(CommandSender sender, String key) {
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage(key));
    }

    private void msg(CommandSender sender, String key, String p, String v) {
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage(key, p, v));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (action.equals("create") || action.equals("reload") || action.equals("list")) {
                return new ArrayList<>();
            }
            // All other actions take an existing zone id.
            return plugin.getAmbientZoneManager().getAllZones().stream()
                .map(AmbientZone::getId)
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3) {
            switch (action) {
                case "playlist":
                    return plugin.getDiscManager().getAllPlaylists().stream()
                        .map(DiscPlaylist::getId)
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                case "loop":
                case "shuffle":
                    return filter(args[2], "true", "false");
                case "sync":
                    return filter(args[2], "immediate", "next_track");
                case "playback":
                    return filter(args[2], "synced", "individual");
                case "height":
                    return filter(args[2], "full", "limited");
                case "volume":
                    // The quiet end has to be offered: suggesting only 1..4 was
                    // why "turn it down" ended at 1, which is full loudness.
                    return filter(args[2], "inherit", "silent", "quiet", "normal", "loud", "max",
                        "0.05", "0.1", "0.15", "0.2", "0.3", "0.4", "0.5", "0.75", "1",
                        "-20db", "-12db", "-6db", "-3db");
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 4 && action.equals("volume")) {
            return filter(args[3], "norestart");
        }

        return new ArrayList<>();
    }

    private List<String> filter(String prefix, String... options) {
        return Arrays.stream(options)
            .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
    }
}
