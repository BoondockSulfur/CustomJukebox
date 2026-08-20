package de.boondocksulfur.customjukebox.utils;

import java.util.regex.Pattern;

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
    public static final int MAX_ZONE_ID_LENGTH = 32;
    public static final int MAX_DESCRIPTION_LENGTH = 256;
    public static final int MAX_LORE_LINE_LENGTH = 256;

    /**
     * Upper bound for CustomModelData. Kept in sync with the cap DiscManager
     * applies when deriving fragment model data, so a disc can never be created
     * with a value that would silently be capped later.
     */
    public static final int MAX_CUSTOM_MODEL_DATA = 1_000_000;

    // IDs are referenced via space-separated command arguments, so they must not
    // contain whitespace or exotic characters.
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    // Minecraft resource location: optional namespace ([a-z0-9_.-]) + key ([a-z0-9_./-]).
    // Accepts both "namespace:key" and vanilla-style "music_disc.cat".
    private static final Pattern SOUND_KEY_PATTERN =
        Pattern.compile("(?:[a-z0-9_.\\-]+:)?[a-z0-9_./\\-]+");

    /**
     * Validates input length against a maximum.
     * @param input Input to validate
     * @param maxLength Maximum allowed length
     * @return true if valid, false otherwise
     */
    public static boolean isValidLength(String input, int maxLength) {
        return input != null && input.length() <= maxLength;
    }

    private static boolean isValidId(String id, int maxLength) {
        return id != null && !id.isEmpty() && isValidLength(id, maxLength)
            && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Validates disc ID format and length.
     * Allowed characters: letters, digits, underscore, hyphen (no whitespace).
     * @param id Disc ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidDiscId(String id) {
        return isValidId(id, MAX_DISC_ID_LENGTH);
    }

    /**
     * Validates category ID format and length.
     * Allowed characters: letters, digits, underscore, hyphen (no whitespace).
     * @param id Category ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidCategoryId(String id) {
        return isValidId(id, MAX_CATEGORY_ID_LENGTH);
    }

    /**
     * Validates playlist ID format and length.
     * Allowed characters: letters, digits, underscore, hyphen (no whitespace).
     * @param id Playlist ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPlaylistId(String id) {
        return isValidId(id, MAX_PLAYLIST_ID_LENGTH);
    }

    /**
     * Validates ambient-zone ID format and length.
     * Allowed characters: letters, digits, underscore, hyphen (no whitespace).
     * @param id Zone ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidZoneId(String id) {
        return isValidId(id, MAX_ZONE_ID_LENGTH);
    }

    /**
     * Validates sound key format and length.
     * Accepts both namespace:key and vanilla music_disc.name formats
     * (lowercase resource-location characters only).
     * @param soundKey Sound key to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSoundKey(String soundKey) {
        return soundKey != null && !soundKey.isEmpty()
            && isValidLength(soundKey, MAX_SOUND_KEY_LENGTH)
            && SOUND_KEY_PATTERN.matcher(soundKey).matches();
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
