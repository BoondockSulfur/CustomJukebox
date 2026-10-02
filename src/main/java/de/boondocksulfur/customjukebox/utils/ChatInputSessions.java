package de.boondocksulfur.customjukebox.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending "type it in chat" prompts of a GUI, one per player, that expire.
 *
 * <p>A prompt used to wait until the player's next chat line, however much
 * later that came. An admin who clicked "Display Name", never answered and
 * came back to the menu an hour later had their next public message swallowed
 * and saved as a disc name. Prompts now lapse after {@link #TIMEOUT_MILLIS},
 * and every GUI clears all pending prompts of a player when it opens a menu or
 * starts a new prompt (see {@code CustomJukebox#cancelChatInput}), so at most
 * one prompt is ever waiting and it is always the one the player just saw.
 *
 * @param <T> what the GUI needs to remember about the prompt
 */
public final class ChatInputSessions<T> {

    /** How long a prompt waits for its answer. */
    public static final long TIMEOUT_MILLIS = 3 * 60 * 1000L;

    private record Pending<T>(T value, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<UUID, Pending<T>> pending = new ConcurrentHashMap<>();

    /**
     * Starts waiting for a player's answer.
     * @param playerId player
     * @param value what the answer is for
     */
    public void start(UUID playerId, T value) {
        pending.put(playerId, new Pending<>(value, System.currentTimeMillis() + TIMEOUT_MILLIS));
    }

    /**
     * Takes the pending prompt, if it has not expired. Removal is atomic, so
     * two chat lines arriving at once cannot both answer the same prompt.
     * @param playerId player
     * @return what the answer is for, or null if nothing (valid) is pending
     */
    public T take(UUID playerId) {
        Pending<T> entry = pending.remove(playerId);
        return entry == null || entry.expired() ? null : entry.value();
    }

    /**
     * @param playerId player
     * @return true if a prompt is waiting and has not expired
     */
    public boolean isActive(UUID playerId) {
        Pending<T> entry = pending.get(playerId);
        if (entry == null) {
            return false;
        }
        if (entry.expired()) {
            pending.remove(playerId, entry);
            return false;
        }
        return true;
    }

    /**
     * Drops a player's pending prompt.
     * @param playerId player
     */
    public void cancel(UUID playerId) {
        pending.remove(playerId);
    }
}
