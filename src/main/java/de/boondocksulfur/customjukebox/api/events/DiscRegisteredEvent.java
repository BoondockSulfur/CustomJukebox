package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a new custom disc is registered (created via GUI or config).
 */
public class DiscRegisteredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CustomDisc disc;

    public DiscRegisteredEvent(CustomDisc disc) {
        this.disc = disc;
    }

    /**
     * Gets the newly registered custom disc.
     * @return The custom disc
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
