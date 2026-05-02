package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a custom disc is removed from the configuration.
 * Contains a snapshot of the disc before removal.
 */
public class DiscRemovedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String discId;
    private final CustomDisc disc;

    public DiscRemovedEvent(String discId, CustomDisc disc) {
        this.discId = discId;
        this.disc = disc;
    }

    /**
     * Gets the ID of the removed disc.
     * @return The disc ID
     */
    public String getDiscId() {
        return discId;
    }

    /**
     * Gets a snapshot of the disc before removal.
     * @return The custom disc (snapshot)
     */
    public CustomDisc getDisc() {
        return disc;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
