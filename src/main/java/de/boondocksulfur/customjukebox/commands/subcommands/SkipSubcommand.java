package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.JukeboxPlayback;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Skips the track the player is currently hearing.
 *
 * <p>Resolves the source the same way the progress bar does: an ambient zone the
 * player stands in takes precedence over a jukebox they are merely in range of.
 * At a jukebox only a playlist can be skipped; a single disc has no next track,
 * and ending it needs {@code customjukebox.stop}.
 *
 * <p>There is no "previous track" counterpart on purpose - it would be identical
 * to skipping forward through the whole playlist, since a resource-pack sound
 * can only be started from its beginning.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class SkipSubcommand implements SubCommand {

    private final CustomJukebox plugin;

    public SkipSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String getDescription() {
        return "Skip the track you are currently hearing";
    }

    @Override
    public String getUsage() {
        return "/cjb skip";
    }

    @Override
    public String getPermission() {
        return "customjukebox.skip";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-only-players"));
            return true;
        }

        // Zone first - that is the music following the player around
        String zoneId = plugin.getAmbientZoneManager().getZoneIdFor(player);
        if (zoneId != null) {
            CustomDisc next = plugin.getAmbientZoneManager().skipTrack(zoneId, player);
            report(sender, next);
            return true;
        }

        // Resolved by earshot, not by listener membership: a player who just
        // re-enabled their music, or walked up mid-track, is in range without
        // being a listener yet and would otherwise be told nothing is playing.
        JukeboxPlayback playback = plugin.getPlaybackManager().getAudiblePlaybackFor(player);
        if (playback == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("skip-nothing-playing"));
            return true;
        }

        // Only a playlist has a next track. Ending a single disc is a stop, and
        // stopping someone else's music is an admin permission - otherwise
        // everyone could end a server-wide playback just by being in range.
        if (!plugin.getPlaybackManager().hasPlaylist(playback.getJukeboxLocation())) {
            if (!player.hasPermission("customjukebox.stop")) {
                MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("skip-no-playlist"));
                return true;
            }
            plugin.getPlaybackManager().stopPlayback(playback.getJukeboxLocation());
            report(sender, null);
            return true;
        }

        CustomDisc next = plugin.getPlaybackManager().skipToNext(playback.getJukeboxLocation());
        report(sender, next);
        return true;
    }

    private void report(CommandSender sender, CustomDisc next) {
        if (next == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("skip-stopped"));
        } else {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                .getMessage("skip-success", "disc", next.getDisplayName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
