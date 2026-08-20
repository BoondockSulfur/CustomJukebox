package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.DiscPlaylist;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import de.boondocksulfur.customjukebox.model.RepeatMode;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
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
 * A player's personal favourite discs.
 *
 * <p>Favourites are an ad-hoc playlist that lives per player, so
 * {@code /cjb favorite play} builds a throwaway {@link DiscPlaylist} from them
 * and hands it to the normal playlist machinery - shuffle and repeat included.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class FavoriteSubcommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("add", "remove", "toggle", "list", "play", "clear");
    /** Reserved playlist id for the ad-hoc favourites playlist. */
    private static final String FAVORITES_PLAYLIST_ID = "__favorites__";
    /** Flags {@code favorite play} accepts, in any order. */
    private static final List<String> PLAY_FLAGS = Arrays.asList(
        "shuffle", "loop", "repeat-one", "off", "global", "world", "50", "100", "200");

    private final CustomJukebox plugin;

    public FavoriteSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "favorite";
    }

    @Override
    public String getDescription() {
        return "Manage and play your favourite discs";
    }

    @Override
    public String getUsage() {
        return "/cjb favorite <add|remove|toggle|list|play|clear> [disc] [shuffle] [loop] [global|world|<radius>]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.favorite";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add":
                return change(player, args, true);
            case "remove":
                return change(player, args, false);
            case "toggle":
                return handleToggle(player, args);
            case "list":
                return handleList(player);
            case "play":
                return handlePlay(player, args);
            case "clear":
                return handleClear(player);
            default:
                MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("favorite-usage"));
                return true;
        }
    }

    private CustomDisc resolve(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("favorite-usage-disc"));
            return null;
        }
        CustomDisc disc = plugin.getDiscManager().getDisc(args[1]);
        if (disc == null) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("invalid-disc"));
            return null;
        }
        return disc;
    }

    private boolean change(Player player, String[] args, boolean add) {
        CustomDisc disc = resolve(player, args);
        if (disc == null) {
            return true;
        }
        boolean changed = add
            ? plugin.getPlayerPreferencesManager().addFavorite(player.getUniqueId(), disc.getId())
            : plugin.getPlayerPreferencesManager().removeFavorite(player.getUniqueId(), disc.getId());

        String key = changed
            ? (add ? "favorite-added" : "favorite-removed")
            : (add ? "favorite-already" : "favorite-not-in-list");
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage(key, "disc", disc.getDisplayName()));
        return true;
    }

    private boolean handleToggle(Player player, String[] args) {
        CustomDisc disc = resolve(player, args);
        if (disc == null) {
            return true;
        }
        boolean nowFavorite = plugin.getPlayerPreferencesManager()
            .toggleFavorite(player.getUniqueId(), disc.getId());
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage(nowFavorite ? "favorite-added" : "favorite-removed", "disc", disc.getDisplayName()));
        return true;
    }

    private boolean handleList(Player player) {
        List<CustomDisc> discs = favoriteDiscs(player);
        if (discs.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("favorite-none"));
            return true;
        }
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage("favorite-list-header", "count", String.valueOf(discs.size())));
        for (CustomDisc disc : discs) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("id", disc.getId());
            placeholders.put("disc", disc.getDisplayName());
            placeholders.put("author", disc.getAuthor());
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("favorite-list-entry", placeholders));
        }
        return true;
    }

    private boolean handleClear(Player player) {
        List<String> favorites = plugin.getPlayerPreferencesManager().getFavorites(player.getUniqueId());
        if (favorites.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("favorite-none"));
            return true;
        }
        for (String discId : favorites) {
            plugin.getPlayerPreferencesManager().removeFavorite(player.getUniqueId(), discId);
        }
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage("favorite-cleared", "count", String.valueOf(favorites.size())));
        return true;
    }

    private boolean handlePlay(Player player, String[] args) {
        List<CustomDisc> discs = favoriteDiscs(player);
        if (discs.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getLanguageManager().getMessage("favorite-none"));
            return true;
        }

        boolean shuffle = false;
        RepeatMode repeatMode = RepeatMode.OFF;
        // Same flag vocabulary as /cjb playlist play, including the range
        PlaybackRange range = new PlaybackRange(PlaybackRange.RangeType.NORMAL);
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("shuffle") || arg.equals("random")) {
                shuffle = true;
                continue;
            }
            RepeatMode parsedMode = RepeatMode.parse(arg);
            if (parsedMode != null) {
                repeatMode = parsedMode;
                continue;
            }
            PlaybackRange parsedRange = PlaybackRange.parse(arg);
            if (parsedRange != null) {
                range = parsedRange;
                continue;
            }
            MessageUtil.sendMessage(player, plugin.getLanguageManager()
                .getMessage("favorite-play-invalid-option", "value", args[i]));
            return true;
        }

        List<String> ids = discs.stream().map(CustomDisc::getId).collect(Collectors.toList());
        DiscPlaylist adHoc = new DiscPlaylist(FAVORITES_PLAYLIST_ID,
            plugin.getLanguageManager().getRawMessage("favorite-playlist-name"), "", ids);

        plugin.getPlaybackManager().startPlaylistPlayback(
            player.getLocation().getBlock().getLocation(), adHoc, repeatMode, shuffle, range);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(discs.size()));
        placeholders.put("repeat", repeatMode.display());
        placeholders.put("shuffle", String.valueOf(shuffle));
        MessageUtil.sendMessage(player, plugin.getLanguageManager()
            .getMessage("favorite-play-started", placeholders));
        return true;
    }

    /** Favourites resolved to discs, silently skipping ones that no longer exist. */
    private List<CustomDisc> favoriteDiscs(Player player) {
        List<CustomDisc> discs = new ArrayList<>();
        for (String discId : plugin.getPlayerPreferencesManager().getFavorites(player.getUniqueId())) {
            CustomDisc disc = plugin.getDiscManager().getDisc(discId);
            if (disc != null) {
                discs.add(disc);
            }
        }
        return discs;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && !(sender instanceof Player)) {
            return new ArrayList<>();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("play")) {
                return PLAY_FLAGS.stream()
                    .filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
            }
            if (action.equals("remove")) {
                // Only suggest what the player actually has
                return plugin.getPlayerPreferencesManager().getFavorites(((Player) sender).getUniqueId()).stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
            }
            if (action.equals("add") || action.equals("toggle")) {
                return plugin.getDiscManager().getAllDiscs().stream()
                    .map(CustomDisc::getId)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
            }
        }
        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("play")) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return PLAY_FLAGS.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
