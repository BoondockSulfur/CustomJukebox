package de.boondocksulfur.customjukebox.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a playlist of custom discs.
 * Playlists group multiple discs together for sequential playback.
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class DiscPlaylist {

    private final String id;
    private final String displayName;
    private final String description;
    private final List<String> discIds;

    public DiscPlaylist(String id, String displayName, String description, List<String> discIds) {
        this.id = id;
        this.displayName = displayName;
        this.description = description != null ? description : "";
        this.discIds = discIds != null ? new ArrayList<>(discIds) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getDiscIds() {
        return new ArrayList<>(discIds);
    }

    public int getDiscCount() {
        return discIds.size();
    }

    public boolean isEmpty() {
        return discIds.isEmpty();
    }

    public boolean contains(String discId) {
        return discIds.contains(discId);
    }
}
