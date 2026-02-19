package de.boondocksulfur.customjukebox.model;

/**
 * Represents a category for custom discs.
 * Categories are used to organize discs by theme or genre.
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class DiscCategory {

    private final String id;
    private final String displayName;
    private final String description;

    public DiscCategory(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description != null ? description : "";
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
}
