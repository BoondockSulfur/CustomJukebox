package de.boondocksulfur.customjukebox.model;

import java.util.Locale;

/**
 * How a playlist behaves when a track ends.
 *
 * @author BoondockSulfur
 * @since 3.4.0
 */
public enum RepeatMode {

    /** Play through once, then stop. */
    OFF,
    /** Loop the whole playlist back to its first track. */
    ALL,
    /** Repeat the current track indefinitely. */
    ONE;

    /**
     * Parses a user-supplied mode name, accepting the aliases used by the
     * commands ({@code loop} for ALL, {@code repeat-one}/{@code one} for ONE).
     *
     * @param raw user input, may be null
     * @return the matching mode, or null if the input is not a repeat mode
     */
    public static RepeatMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "off":
            case "none":
            case "false":
                return OFF;
            // Deliberately no "all" here: PlaybackRange.parse uses that word for
            // server-wide range, and the play commands accept both kinds of
            // flag. "loop" covers repeat-all unambiguously.
            case "loop":
            case "true":
            case "yes":
                return ALL;
            case "one":
            case "single":
            case "repeat-one":
            case "repeat_one":
                return ONE;
            default:
                return null;
        }
    }

    /** @return the lowercase name used in messages and tab completion */
    public String display() {
        return name().toLowerCase(Locale.ROOT);
    }
}
