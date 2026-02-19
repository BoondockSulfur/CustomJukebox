package de.boondocksulfur.customjukebox.utils;

/**
 * Validates user input for GUI chat interactions.
 * Provides centralized input length limits and validation methods.
 */
public class InputValidator {

    // Maximum input lengths for different fields
    public static final int MAX_DISC_ID_LENGTH = 64;
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;
    public static final int MAX_AUTHOR_LENGTH = 64;
    public static final int MAX_SOUND_KEY_LENGTH = 128;
    public static final int MAX_CATEGORY_ID_LENGTH = 32;
    public static final int MAX_CATEGORY_NAME_LENGTH = 64;
    public static final int MAX_PLAYLIST_ID_LENGTH = 32;
    public static final int MAX_PLAYLIST_NAME_LENGTH = 64;
    public static final int MAX_LORE_LINE_LENGTH = 256;

    /**
     * Validates input length against a maximum.
     * @param input Input to validate
     * @param maxLength Maximum allowed length
     * @return true if valid, false otherwise
     */
    public static boolean isValidLength(String input, int maxLength) {
        return input != null && input.length() <= maxLength;
    }

    /**
     * Validates disc ID format and length.
     * Removed strict validation - now only checks length and non-empty.
     * @param id Disc ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidDiscId(String id) {
        return id != null && !id.trim().isEmpty() && isValidLength(id, MAX_DISC_ID_LENGTH);
    }

    /**
     * Validates category ID format and length.
     * Removed strict validation - now only checks length and non-empty.
     * @param id Category ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidCategoryId(String id) {
        return id != null && !id.trim().isEmpty() && isValidLength(id, MAX_CATEGORY_ID_LENGTH);
    }

    /**
     * Validates playlist ID format and length.
     * Removed strict validation - now only checks length and non-empty.
     * @param id Playlist ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPlaylistId(String id) {
        return id != null && !id.trim().isEmpty() && isValidLength(id, MAX_PLAYLIST_ID_LENGTH);
    }

    /**
     * Validates sound key format and length.
     * Relaxed validation - accepts both namespace:key and music_disc.name formats.
     * @param soundKey Sound key to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSoundKey(String soundKey) {
        if (!isValidLength(soundKey, MAX_SOUND_KEY_LENGTH)) {
            return false;
        }
        // Accept both formats: namespace:key OR music_disc.name
        return soundKey != null && !soundKey.trim().isEmpty();
    }

    /**
     * Gets a user-friendly error message for length validation failure.
     * @param fieldName Name of the field
     * @param maxLength Maximum allowed length
     * @return Error message
     */
    public static String getLengthErrorMessage(String fieldName, int maxLength) {
        return "§c" + fieldName + " is too long! Maximum length: §e" + maxLength + " §ccharacters";
    }
}
