package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
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

    @Override
    public String getPermission() {
        return "customjukebox.playlist";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist"));
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
                sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist"));
                return true;
        }
    }

    private boolean handleList(CommandSender sender) {
        if (plugin.getDiscManager().getAllPlaylists().isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-none-available"));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-list-header"));
        for (DiscPlaylist playlist : plugin.getDiscManager().getAllPlaylists()) {
            String message = plugin.getLanguageManager().getMessage("playlist-list-entry")
                .replace("{id}", playlist.getId())
                .replace("{name}", playlist.getDisplayName())
                .replace("{count}", String.valueOf(playlist.getDiscCount()));
            sender.sendMessage(message);
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-info"));
            return true;
        }

        String playlistId = args[1];
        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);

        if (playlist == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", playlistId));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-info-header")
            .replace("{name}", playlist.getDisplayName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-info-id")
            .replace("{id}", playlist.getId()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-info-description")
            .replace("{description}", playlist.getDescription()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-info-count")
            .replace("{count}", String.valueOf(playlist.getDiscCount())));

        sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-info-discs-header"));
        List<CustomDisc> discs = plugin.getDiscManager().getDiscsFromPlaylist(playlistId);
        for (int i = 0; i < discs.size(); i++) {
            CustomDisc disc = discs.get(i);
            sender.sendMessage("  " + (i + 1) + ". " + disc.getDisplayName() + " §7- " + disc.getAuthor());
        }

        return true;
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-play"));
            return true;
        }

        Player player = (Player) sender;
        String playlistId = args[1];
        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);

        if (playlist == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", playlistId));
            return true;
        }

        if (playlist.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-empty")
                .replace("{playlist}", playlist.getDisplayName()));
            return true;
        }

        // Check for loop parameter
        boolean loop = args.length > 2 && (args[2].equalsIgnoreCase("loop") ||
                                           args[2].equalsIgnoreCase("true") ||
                                           args[2].equalsIgnoreCase("yes"));

        // Start playlist playback
        plugin.getPlaybackManager().startPlaylistPlayback(player.getLocation(), playlist, loop);

        String message = plugin.getLanguageManager().getMessage("playlist-started")
            .replace("{playlist}", playlist.getDisplayName())
            .replace("{count}", String.valueOf(playlist.getDiscCount()));

        if (loop) {
            message += " " + plugin.getLanguageManager().getMessage("playlist-loop-enabled");
        }

        sender.sendMessage(message);

        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-create"));
            return true;
        }

        String id = args[1];
        String displayName = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
        String description = "";

        boolean success = plugin.getDiscManager().createPlaylist(id, displayName, description);
        if (success) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-created")
                .replace("{playlist}", id));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-already-exists")
                .replace("{playlist}", id));
        }

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-delete"));
            return true;
        }

        String id = args[1];
        boolean success = plugin.getDiscManager().deletePlaylist(id);

        if (success) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-deleted")
                .replace("{playlist}", id));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-not-found")
                .replace("{playlist}", id));
        }

        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-add"));
            return true;
        }

        String playlistId = args[1];
        String discId = args[2];

        boolean success = plugin.getDiscManager().addDiscToPlaylist(playlistId, discId);

        if (success) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-disc-added")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-disc-add-failed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-remove"));
            return true;
        }

        String playlistId = args[1];
        String discId = args[2];

        boolean success = plugin.getDiscManager().removeDiscFromPlaylist(playlistId, discId);

        if (success) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-disc-removed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-disc-remove-failed")
                .replace("{disc}", discId)
                .replace("{playlist}", playlistId));
        }

        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-rename"));
            return true;
        }

        String oldId = args[1];
        String newId = args[2];

        boolean success = plugin.getDiscManager().renamePlaylist(oldId, newId);

        if (success) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-renamed")
                .replace("{old}", oldId)
                .replace("{new}", newId));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("playlist-rename-failed")
                .replace("{old}", oldId)
                .replace("{new}", newId));
        }

        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("command-usage-playlist-edit"));
            return true;
        }

        Player player = (Player) sender;
        String playlistId = args[1];

        DiscPlaylist playlist = plugin.getDiscManager().getPlaylist(playlistId);
        if (playlist == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("playlist-not-found")
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

            // Play command: suggest loop
            if (action.equals("play")) {
                return Arrays.asList("loop", "true", "yes").stream()
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

        return new ArrayList<>();
    }
}
