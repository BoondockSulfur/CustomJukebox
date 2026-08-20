package de.boondocksulfur.customjukebox.manager;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.model.NowPlaying;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows each player a boss bar with the track they are currently hearing and how
 * far into it they are.
 *
 * <p>The elapsed time is the plugin's own bookkeeping: a resource-pack sound is
 * handed to the client with a single play packet and the server never learns the
 * real playhead position, so this is "time since we started the track". That is
 * exact for anyone present when the track began.
 *
 * <p>The bar is driven by one global timer that dispatches each player's update
 * to that player's region thread, so it is Folia-safe.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class NowPlayingManager {

    private final CustomJukebox plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    /** What each bar currently shows, so unchanged bars are not resent. */
    private final Map<UUID, String> lastRendered = new ConcurrentHashMap<>();

    private volatile SchedulerUtil.TaskHandle task;
    private volatile boolean running;

    public NowPlayingManager(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the update timer, unless the feature is disabled in config.
     */
    public void start() {
        if (running || !plugin.getConfigManager().isProgressBarEnabled()) {
            return;
        }
        running = true;
        long interval = plugin.getConfigManager().getProgressUpdateTicks();
        task = SchedulerUtil.runGlobalTimer(plugin, this::tick, interval, interval);
        if (task == null) {
            plugin.getLogger().warning("Progress bar timer could not be scheduled - the bar stays hidden");
            running = false;
        }
    }

    /**
     * Stops the timer and hides every bar.
     */
    public void stop() {
        running = false;
        SchedulerUtil.cancelTask(task);
        task = null;

        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                hideOn(player, entry.getValue());
            }
        }
        bars.clear();
        lastRendered.clear();
    }

    /**
     * Restarts with freshly read config values.
     */
    public void reload() {
        stop();
        start();
    }

    private void tick() {
        if (!running) {
            return;
        }
        boolean folia = SchedulerUtil.isFolia();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (folia) {
                // Reading the player's location and sending packets belongs on
                // their own region thread
                SchedulerUtil.runPlayerTask(plugin, player, () -> update(player));
            } else {
                update(player);
            }
        }
    }

    private void update(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();

        if (!plugin.getPlayerPreferencesManager().isMusicEnabled(uuid)) {
            hide(player);
            return;
        }

        // An ambient zone the player stands in wins over a distant jukebox:
        // it is the music that follows them around.
        NowPlaying nowPlaying = plugin.getAmbientZoneManager().getNowPlaying(player);
        if (nowPlaying == null) {
            nowPlaying = plugin.getPlaybackManager().getNowPlaying(player);
        }

        if (nowPlaying == null || nowPlaying.disc() == null) {
            hide(player);
            return;
        }

        String text = render(nowPlaying);
        BossBar bar = bars.get(uuid);
        if (bar == null) {
            bar = BossBar.bossBar(AdventureUtil.parseComponent(text), progressOf(nowPlaying),
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            bars.put(uuid, bar);
            lastRendered.put(uuid, text);
            player.showBossBar(bar);
            return;
        }

        // Only resend the name when the text actually changed; the progress
        // value moves every tick anyway.
        if (!text.equals(lastRendered.get(uuid))) {
            bar.name(AdventureUtil.parseComponent(text));
            lastRendered.put(uuid, text);
        }
        bar.progress(progressOf(nowPlaying));
        player.showBossBar(bar); // No-op if already visible
    }

    private float progressOf(NowPlaying nowPlaying) {
        // A disc without a configured duration has no measurable progress -
        // show a full bar rather than a stuck empty one.
        return nowPlaying.disc().getDurationTicks() > 0 ? nowPlaying.progress() : 1f;
    }

    private String render(NowPlaying nowPlaying) {
        StringBuilder text = new StringBuilder("&b♪ &f").append(nowPlaying.disc().getDisplayName());

        String sourceKey = switch (nowPlaying.source()) {
            case ZONE -> "nowplaying-source-zone";
            case RADIO -> "nowplaying-source-radio";
            default -> null;
        };
        if (sourceKey != null) {
            text.append(" &8").append(plugin.getLanguageManager().getMessage(sourceKey));
        }

        if (nowPlaying.disc().getDurationTicks() > 0) {
            text.append(" &7").append(NowPlaying.formatTicks(nowPlaying.elapsedTicks()))
                .append(" / ").append(NowPlaying.formatTicks(nowPlaying.disc().getDurationTicks()));
        }
        return text.toString();
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        lastRendered.remove(player.getUniqueId());
        if (bar != null) {
            hideOn(player, bar);
        }
    }

    private void hideOn(Player player, BossBar bar) {
        try {
            player.hideBossBar(bar);
        } catch (Exception ignored) {
            // Player may be disconnecting - nothing to clean up then
        }
    }

    /**
     * Drops a player's bar on logout.
     * @param player the player leaving
     */
    public void cleanup(Player player) {
        bars.remove(player.getUniqueId());
        lastRendered.remove(player.getUniqueId());
    }
}
