package de.boondocksulfur.customjukebox.model;

/**
 * What a specific player is currently hearing, and how far into it they are.
 *
 * <p>The elapsed time is bookkeeping, not a reading from the client: the server
 * sends a play-sound packet and the client owns playback from there, so this is
 * "time since we started the track" rather than an observed position. It is
 * accurate for a player who was present when the track started, which is the
 * normal case; a listener who joined a {@code synced} zone mid-track under
 * {@code immediate} sync hears an offset copy and will see the zone's shared
 * position instead of their own.
 *
 * @param disc the disc being played
 * @param elapsedTicks ticks since the track started
 * @param source where the music comes from, for display
 * @author BoondockSulfur
 * @since 3.4.0
 */
public record NowPlaying(CustomDisc disc, long elapsedTicks, Source source) {

    /** Where a track a player hears originates from. */
    public enum Source {
        /** A jukebox or a {@code /cjb play} / playlist playback at a location. */
        JUKEBOX,
        /** An ambient zone the player is standing in. */
        ZONE,
        /** A server-wide radio zone. */
        RADIO
    }

    /**
     * Progress through the track, 0.0-1.0. Returns 0 for discs without a
     * configured duration, where no progress can be derived.
     * @return fraction of the track elapsed
     */
    public float progress() {
        int duration = disc.getDurationTicks();
        if (duration <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, (float) elapsedTicks / duration));
    }

    /**
     * Remaining ticks, or -1 when the disc has no configured duration.
     * @return ticks left, or -1
     */
    public long remainingTicks() {
        int duration = disc.getDurationTicks();
        if (duration <= 0) {
            return -1;
        }
        return Math.max(0, duration - elapsedTicks);
    }

    /**
     * Formats a tick count as {@code m:ss}.
     * @param ticks tick count
     * @return formatted time
     */
    public static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0, ticks) / 20;
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }
}
