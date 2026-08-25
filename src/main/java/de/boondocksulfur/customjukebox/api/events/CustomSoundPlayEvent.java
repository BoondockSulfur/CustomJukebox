package de.boondocksulfur.customjukebox.api.events;

import de.boondocksulfur.customjukebox.model.CustomDisc;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired immediately before a custom disc sound is delivered to a single player.
 *
 * <p>This is the low-level delivery hook: every path that plays a custom sound —
 * jukebox playback, a listener joining a running playback, a player unmuting,
 * and ambient zones — passes through here exactly once per player. Companion
 * plugins that need to replace delivery for certain players (for example the
 * Bedrock extension, which speaks a different sound namespace) cancel this event
 * and play the sound themselves.
 *
 * <p>Cancelling only suppresses the sound packet for this one player. The player
 * stays a tracked listener of the playback, so progress display, skip and stop
 * handling continue to work for them.
 */
public class CustomSoundPlayEvent extends Event implements Cancellable {

    /** Where the sound delivery originated. */
    public enum Source {
        /** A jukebox playback (block or {@code /cjb play}). */
        JUKEBOX,
        /** An ambient zone track. */
        AMBIENT_ZONE
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CustomDisc disc;
    private final Location location;
    private final Source source;
    private final float volume;

    private boolean cancelled;

    public CustomSoundPlayEvent(Player player, CustomDisc disc, Location location, Source source, float volume) {
        this.player = player;
        this.disc = disc;
        this.location = location;
        this.source = source;
        this.volume = volume;
    }

    /**
     * @return The player the sound is about to be played to
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The disc being played
     */
    public CustomDisc getDisc() {
        return disc;
    }

    /**
     * @return Where the sound is anchored (jukebox position, or the player's own
     *         position for ambient zones)
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @return What triggered the delivery
     */
    public Source getSource() {
        return source;
    }

    /**
     * @return The resolved volume for this player, personal volume already applied
     */
    public float getVolume() {
        return volume;
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
