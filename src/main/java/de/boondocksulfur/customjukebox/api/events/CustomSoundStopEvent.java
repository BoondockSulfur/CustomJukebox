package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired immediately before a custom disc sound is stopped for a single player.
 *
 * <p>The counterpart to {@link CustomSoundPlayEvent}: a companion plugin that
 * cancelled delivery and played its own sound cancels this event as well and
 * stops that sound itself.
 */
public class CustomSoundStopEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CustomDisc disc;
    private final CustomSoundPlayEvent.Source source;

    private boolean cancelled;

    public CustomSoundStopEvent(Player player, CustomDisc disc, CustomSoundPlayEvent.Source source) {
        this.player = player;
        this.disc = disc;
        this.source = source;
    }

    /**
     * @return The player the sound is being stopped for
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The disc whose sound is being stopped
     */
    public CustomDisc getDisc() {
        return disc;
    }

    /**
     * @return What triggered the stop
     */
    public CustomSoundPlayEvent.Source getSource() {
        return source;
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
