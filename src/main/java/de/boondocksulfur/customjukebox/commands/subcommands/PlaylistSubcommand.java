package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import de.boondocksulfur.customjukebox.model.RepeatMode;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Playlist command for managing and playing playlists.
 * Usage: /cjb playlist <list|info|play> [args...]
 */
public class PlaylistSubcommand implements SubCommand {

    /** Flags {@code playlist play} accepts, in any order. */
    private static final List<String> PLAY_FLAGS = Arrays.asList(
        "shuffle", "loop", "repeat-one", "off", "global", "world", "50", "100", "200");

    private final CustomJukebox plugin;

    public PlaylistSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "playlist";
    }

    @Override
    public String getDescription() {
        return "Manage and play disc playlists";
    }

    @Override
    public String getUsage() {
        return "/cjb playlist <list|info|play|create|delete|add|remove|rename|edit> [args...]";
    }

    /**
     * Parses the order-independent flags accepted by {@code playlist play}:
     * {@code loop}/{@code repeat-one}/{@code off} for the repeat mode,
     * {@code shuffle} for random order, and {@code global}/{@code world}/a
     * radius for the playback range.
     *
     * <p>The range is stored on the queue, so every following track of the
     * playlist inherits it - not just the first one.
     */
    private static final class PlayOptions {
        RepeatMode repeatMode = RepeatMode.OFF;
        boolean shuffle;
        PlaybackRange range = new PlaybackRange(PlaybackRange.RangeType.NORMAL);
        boolean rangeSet;
        String invalidArgument;

        static PlayOptions parse(String[] args, int from) {
            PlayOptions options = new PlayOptions();
            for (int i = from; i < args.length; i++) {
                String arg = args[i].toLowerCase(java.util.Locale.ROOT);
                if (arg.equals("shuffle") || arg.equals("random")) {
                    options.shuffle = true;
                    continue;
                }
                RepeatMode parsedMode = RepeatMode.parse(arg);
                if (parsedMode != null) {
                    options.repeatMode = parsedMode;
                    continue;
                }
                // Same vocabulary as /cjb play: global|world|<radius>
                PlaybackRange parsedRange = PlaybackRange.parse(arg);
                if (parsedRange != null) {
                    options.range = parsedRange;
                    options.rangeSet = true;
                    continue;
                }
                options.invalidArgument = args[i];
                return options;
            }
            return options;
        }
    }

    @Override
    public String getPermission() {
        return "customjukebox.playlist";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist"));
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "play":
                return handlePlay(sender, args);
            case "create":
                return handleCreate(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "rename":
                return handleRename(sender, args);
            case "edit":
                return handleEdit(sender, args);
            default:
                MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist"));
                return true;
        }
    }

    private boolean handleList(CommandSender sender) {
        if (plugin.getDiscManager().getAllPlaylists().isEmpty()) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-none-available"));
            return true;
        }

        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-list-header"));
        for (DiscPlaylist playlist : plugin.getDiscManager().getAllPlaylists()) {
            String message = plugin.getLanguageManager().getMessage("playlist-list-entry")
                .replace("{id}", playlist.getId())
                .replace("{name}", playlist.getDisplayName())
                .replace("{count}", String.valueOf(playlist.getDiscCount()));
            MessageUtil.sendMessage(sender, message);
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-info"));
            return true;
        }

        String playlistId = args[1];
        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);

        if (playlist == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", playlistId));
            return true;
        }

        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-info-header")
            .replace("{name}", playlist.getDisplayName()));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-info-id")
            .replace("{id}", playlist.getId()));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-info-description")
            .replace("{description}", playlist.getDescription()));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-info-count")
            .replace("{count}", String.valueOf(playlist.getDiscCount())));

        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-info-discs-header"));
        List<CustomDisc> discs = plugin.getDiscManager().getDiscsFromPlaylist(playlistId);
        for (int i = 0; i < discs.size(); i++) {
            CustomDisc disc = discs.get(i);
            MessageUtil.sendMessage(sender, "  " + (i + 1) + ". " + disc.getDisplayName() + " &7- " + disc.getAuthor());
        }

        return true;
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-play"));
            return true;
        }

        Player player = (Player) sender;
        String playlistId = args[1];
        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);

        if (playlist == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", playlistId));
            return true;
        }

        if (playlist.isEmpty()) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-empty")
                .replace("{playlist}", playlist.getDisplayName()));
            return true;
        }

        PlayOptions options = PlayOptions.parse(args, 2);
        if (options.invalidArgument != null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                .getMessage("playlist-play-invalid-option", "value", options.invalidArgument));
            return true;
        }

        // Block coordinates keep the location key stable if the player shifts
        plugin.getPlaybackManager().startPlaylistPlayback(
            player.getLocation().getBlock().getLocation(), playlist,
            options.repeatMode, options.shuffle, options.range);

        String message = plugin.getLanguageManager().getMessage("playlist-started")
            .replace("{playlist}", playlist.getDisplayName())
            .replace("{count}", String.valueOf(playlist.getDiscCount()));

        if (options.repeatMode != RepeatMode.OFF) {
            message += " " + plugin.getLanguageManager()
                .getMessage("playlist-repeat-mode", "mode", options.repeatMode.display());
        }
        if (options.shuffle) {
            message += " " + plugin.getLanguageManager().getMessage("playlist-shuffle-enabled");
        }
        if (options.rangeSet) {
            message += " " + plugin.getLanguageManager()
                .getMessage("playback-range-info", "range", options.range.toString());
        }

        MessageUtil.sendMessage(sender, message);

        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-create"));
            return true;
        }

        String id = args[1];
        // Same rule the admin GUI applies - a playlist id is a JSON key and a
        // command argument, so it must not contain spaces or exotic characters
        if (!InputValidator.isValidPlaylistId(id)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                .getMessage("playlist-invalid-id", "value", id));
            return true;
        }
        String displayName = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
        String description = "";

        boolean success = plugin.getDiscManager().createPlaylist(id, displayName, description);
        if (success) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-created")
                .replace("{playlist}", id));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-already-exists")
                .replace("{playlist}", id));
        }

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-delete"));
            return true;
        }

        String id = args[1];
        boolean success = plugin.getDiscManager().deletePlaylist(id);

        if (success) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-deleted")
                .replace("{playlist}", id));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", id));
        }

        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-add"));
            return true;
        }

        String playlistId = args[1];
        String discId = args[2];

        boolean success = plugin.getDiscManager().addDiscToPlaylist(playlistId, discId);

        if (success) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-disc-added")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-disc-add-failed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-remove"));
            return true;
        }

        String playlistId = args[1];
        String discId = args[2];

        boolean success = plugin.getDiscManager().removeDiscFromPlaylist(playlistId, discId);

        if (success) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-disc-removed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-disc-remove-failed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        }

        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-rename"));
            return true;
        }

        String oldId = args[1];
        String newId = args[2];

        if (!InputValidator.isValidPlaylistId(newId)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                .getMessage("playlist-invalid-id", "value", newId));
            return true;
        }

        boolean success = plugin.getDiscManager().renamePlaylist(oldId, newId);

        if (success) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-renamed")
                .replace("{old}", oldId)
                .replace("{new}", newId));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("playlist-rename-failed")
                .replace("{old}", oldId)
                .replace("{new}", newId));
        }

        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-usage-playlist-edit"));
            return true;
        }

        Player player = (Player) sender;
        String playlistId = args[1];

        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);
        if (playlist == null) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", playlistId));
            return true;
        }

        // Open GUI editor (will be implemented next)
        openPlaylistEditorGUI(player, playlist);

        return true;
    }

    /**
     * Opens the playlist editor GUI.
     */
    private void openPlaylistEditorGUI(Player player, DiscPlaylist playlist) {
        plugin.getPlaylistEditorGUI().openEditor(player, playlist);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest all actions
            return Arrays.asList("list", "info", "play", "create", "delete", "add", "remove", "rename", "edit").stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase();

            // Actions that need playlist ID
            if (action.equals("info") || action.equals("play") || action.equals("delete") ||
                action.equals("add") || action.equals("remove") || action.equals("edit") || action.equals("rename")) {
                return plugin.getDiscManager().getAllPlaylists().stream()
                    .map(DiscPlaylist::getId)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String action = args[0].toLowerCase();

            // Play command: repeat mode, shuffle and range flags
            if (action.equals("play")) {
                return PLAY_FLAGS.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }

            // Add/Remove commands: suggest disc IDs
            if (action.equals("add") || action.equals("remove")) {
                return plugin.getDiscManager().getAllDiscs().stream()
                    .map(de.boondocksulfur.customjukebox.model.CustomDisc::getId)
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length >= 4 && args.length <= 5 && args[0].equalsIgnoreCase("play")) {
            String prefix = args[args.length - 1].toLowerCase();
            return PLAY_FLAGS.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
