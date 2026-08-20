package de.boondocksulfur.customjukebox.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One player's personal music settings.
 *
 * <p>Only players who actually changed something are persisted - a default
 * instance is dropped on save, so players.json stays proportional to the number
 * of players who used the settings rather than to the number who ever joined.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public class PlayerPreferences {

    /** Sentinel for {@link #volume}: follow the server-wide playback volume. */
    public static final float VOLUME_INHERIT = -1f;

    private volatile boolean musicEnabled = true;
    private volatile float volume = VOLUME_INHERIT;
    /** Insertion-ordered so the favourites list keeps the order they were added. */
    private final Set<String> favorites = new LinkedHashSet<>();

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public boolean inheritsVolume() {
        return volume < 0;
    }

    /**
     * @return the live favourites set; synchronize on it when iterating
     */
    public Set<String> getFavorites() {
        return favorites;
    }

    /**
     * Whether these preferences are all at their default values and therefore
     * need not be written to disk.
     * @return true if nothing deviates from the defaults
     */
    public boolean isDefault() {
        synchronized (favorites) {
            return musicEnabled && inheritsVolume() && favorites.isEmpty();
        }
    }
}
