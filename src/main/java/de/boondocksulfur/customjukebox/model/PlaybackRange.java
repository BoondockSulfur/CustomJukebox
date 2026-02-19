package de.boondocksulfur.customjukebox.model;

/**
 * Defines the range/scope for sound playback.
 */
public class PlaybackRange {

    public enum RangeType {
        NORMAL,         // Standard range based on volume
        GLOBAL,         // All players on the server
        WORLD,          // All players in the same world
        CUSTOM_RADIUS   // Custom radius in blocks
    }

    private final RangeType type;
    private final double customRadius;

    public PlaybackRange(RangeType type) {
        this(type, 0);
    }

    public PlaybackRange(RangeType type, double customRadius) {
        this.type = type;
        this.customRadius = customRadius;
    }

    public RangeType getType() {
        return type;
    }

    public double getCustomRadius() {
        return customRadius;
    }

    /**
     * Parses a range string into a PlaybackRange.
     * @param rangeStr Range string (e.g., "global", "world", "100")
     * @return PlaybackRange or null if invalid
     */
    public static PlaybackRange parse(String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) {
            return new PlaybackRange(RangeType.NORMAL);
        }

        String lower = rangeStr.toLowerCase();

        switch (lower) {
            case "global":
            case "server":
            case "all":
                return new PlaybackRange(RangeType.GLOBAL);

            case "world":
            case "dimension":
                return new PlaybackRange(RangeType.WORLD);

            case "normal":
            case "default":
                return new PlaybackRange(RangeType.NORMAL);

            default:
                // Try to parse as custom radius
                try {
                    double radius = Double.parseDouble(rangeStr);
                    if (radius > 0) {
                        return new PlaybackRange(RangeType.CUSTOM_RADIUS, radius);
                    }
                } catch (NumberFormatException e) {
                    // Invalid format
                }
                return null;
        }
    }

    @Override
    public String toString() {
        switch (type) {
            case GLOBAL:
                return "global";
            case WORLD:
                return "world";
            case CUSTOM_RADIUS:
                return String.format("%.0f blocks", customRadius);
            case NORMAL:
            default:
                return "normal";
        }
    }
}
