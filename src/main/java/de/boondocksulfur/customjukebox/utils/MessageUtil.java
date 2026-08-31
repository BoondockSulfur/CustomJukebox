package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility class for sending properly formatted messages to players.
 * Handles both Adventure API (Paper) and Legacy (Spigot) formats.
 */
public class MessageUtil {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
        LegacyComponentSerializer.legacyAmpersand();

    /**
     * Sends a message to a CommandSender with proper color formatting.
     * Automatically handles Adventure API for Paper servers.
     *
     * @param sender The command sender (player or console)
     * @param message The message with & color codes
     */
    /**
     * Sends a fixed prefix followed by a value the user wrote themselves,
     * rendered the way the game will render it.
     *
     * <p>{@link #sendMessage} understands only {@code &} codes, so echoing a
     * name back through it printed hex colours and gradients as raw markup -
     * the item came out right while the confirmation said
     * {@code <gradient:#ff0000:#0000ff>Name</gradient>}. The prefix keeps the
     * simple codes; only the value goes through the full parser.
     */
    public static void sendWithValue(CommandSender sender, String prefix, String value) {
        sendWithValues(sender, prefix, value);
    }

    /**
     * Alternating fixed text and user values: {@code text, value, text, value, ...}
     * starting with text. Only the values go through the full parser.
     */
    public static void sendWithValues(CommandSender sender, String... parts) {
        if (sender == null || parts.length == 0) {
            return;
        }
        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i] == null ? "" : parts[i];
            builder.append(i % 2 == 0
                ? LEGACY_AMPERSAND.deserialize(part)
                : AdventureUtil.parseComponent(part));
        }
        sender.sendMessage(builder.build());
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        // Parse the message with & codes directly to Adventure Component
        Component component = LEGACY_AMPERSAND.deserialize(message);

        // Send using Adventure API (Paper will handle this correctly)
        sender.sendMessage(component);
    }

    /**
     * Sends a message to a player with proper color formatting.
     *
     * @param player The player
     * @param message The message with & color codes
     */
    public static void sendMessage(Player player, String message) {
        sendMessage((CommandSender) player, message);
    }

    /**
     * Converts legacy & codes to a properly formatted string for display.
     * This method ensures color codes work correctly on Paper 1.21+.
     *
     * @param message Message with & color codes
     * @return Properly formatted message
     */
    public static String formatMessage(String message) {
        if (message == null) {
            return "";
        }

        // For Paper 1.21+, we keep the legacy format as-is
        // The server will handle the conversion when displaying
        return message;
    }

    /**
     * Strips all color codes from a message.
     *
     * @param message Message with color codes
     * @return Plain text without color codes
     */
    public static String stripColors(String message) {
        if (message == null) {
            return null;
        }

        Component component = LEGACY_AMPERSAND.deserialize(message);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText()
            .serialize(component);
    }
}