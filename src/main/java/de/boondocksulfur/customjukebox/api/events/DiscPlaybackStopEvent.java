package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when disc playback stops at a jukebox.
 */
public class DiscPlaybackStopEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CustomDisc disc;
    private final Location location;
    private final StopReason reason;

    /**
     * Reasons why playback was stopped.
     */
    public enum StopReason {
        /** Stopped via command or disc ejection */
        MANUAL,
        /** Song finished naturally */
        DURATION_END,
        /** Jukebox was broken */
        BLOCK_BREAK,
        /** Stopped programmatically (e.g., stopAllPlaybacks) */
        PLUGIN
    }

    public DiscPlaybackStopEvent(CustomDisc disc, Location location, StopReason reason) {
        this.disc = disc;
        this.location = location.clone();
        this.reason = reason;
    }

    /**
     * Gets the custom disc that was playing.
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
     * Gets the reason playback was stopped.
     * @return The stop reason
     */
    public StopReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
