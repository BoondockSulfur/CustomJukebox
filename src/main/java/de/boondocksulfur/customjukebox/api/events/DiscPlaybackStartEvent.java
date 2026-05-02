package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.model.PlaybackRange;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Fired when a custom disc starts playing at a jukebox.
 * Can be cancelled to prevent playback.
 * Listeners can modify the set of players who will hear the sound.
 */
public class DiscPlaybackStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CustomDisc disc;
    private final Location location;
    private final Set<Player> listeners;
    private final boolean loop;
    private final PlaybackRange range;
    private boolean cancelled;

    public DiscPlaybackStartEvent(CustomDisc disc, Location location, Set<Player> listeners,
                                  boolean loop, PlaybackRange range) {
        this.disc = disc;
        this.location = location.clone();
        this.listeners = listeners;
        this.loop = loop;
        this.range = range;
        this.cancelled = false;
    }

    /**
     * Gets the custom disc being played.
     * @return The custom disc
     */
    public CustomDisc getDisc() {
        return disc;
    }

    /**
     * Gets the jukebox location.
     * @return The playback location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Gets the mutable set of players who will hear the sound.
     * Companion plugins can add or remove players from this set.
     * @return Mutable set of listening players
     */
    public Set<Player> getListeners() {
        return listeners;
    }

    /**
     * Whether the playback is set to loop.
     * @return true if looping
     */
    public boolean isLoop() {
        return loop;
    }

    /**
     * Gets the playback range.
     * @return The playback range
     */
    public PlaybackRange getRange() {
        return range;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
