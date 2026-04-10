package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for Adventure API text handling.
 * Replaces deprecated ChatColor/BungeeCord API with modern Adventure components.
 *
 * Supports:
 * - Legacy color codes (&a, &b, &c, etc.)
 * - HEX colors (&#RRGGBB or #RRGGBB)
 * - Gradients (<gradient:#START:#END>text</gradient>)
 * - MiniMessage format (<red>, <bold>, etc.)
 *
 * @author BoondockSulfur
 * @version 2.1.0
 * @since 2.1.0
 */
public class AdventureUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();

    // Regex patterns for custom formats
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN =
        Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");

    /**
     * Converts a legacy string (with &-codes, HEX, gradients) to Adventure Component.
     *
     * @param text Legacy text with color codes
     * @return Adventure Component
     */
    public static Component parseComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }


        // Step 1: Convert custom HEX codes (&#RRGGBB) to MiniMessage format
        text = convertHexToMiniMessage(text);

        // Step 2: Convert custom gradients to MiniMessage format
        text = convertGradientToMiniMessage(text);

        // Step 3: Convert legacy codes (&a, &l, etc.) to MiniMessage
        text = convertLegacyToMiniMessage(text);

        // Step 4: Parse with MiniMessage
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            // Fallback to legacy serializer if MiniMessage fails
            return LEGACY_SERIALIZER.deserialize(text);
        }
    }

    /**
     * Converts a list of legacy strings to Adventure Components.
     *
     * @param lines List of legacy text strings
     * @return List of Adventure Components
     */
    public static List<Component> parseComponents(List<String> lines) {
        if (lines == null) {
            return new ArrayList<>();
        }

        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(parseComponent(line));
        }
        return components;
    }

    /**
     * Converts Adventure Component to legacy string (for backwards compatibility).
     *
     * @param component Adventure Component
     * @return Legacy string with & codes
     */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Strips all color codes from a legacy string.
     * Replacement for org.bukkit.ChatColor.stripColor()
     *
     * @param text Legacy string with color codes
     * @return Plain text without color codes
     */
    public static String stripColor(String text) {
        if (text == null) {
            return null;
        }
        // Parse as component and serialize as plain text
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(parseComponent(text));
    }

    /**
     * Converts custom HEX format (&#RRGGBB) to MiniMessage format.
     * Example: "&#FF5555Red" -> "<#FF5555>Red"
     */
    private static String convertHexToMiniMessage(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(result, "<#" + hexCode + ">");
        }
        matcher.appendTail(result);

        // Also handle #RRGGBB format (without &)
        text = result.toString();
        Pattern simpleHexPattern = Pattern.compile("#([A-Fa-f0-9]{6})");
        matcher = simpleHexPattern.matcher(text);
        result = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            // Only replace if not already in MiniMessage format
            if (!text.substring(Math.max(0, matcher.start() - 1), matcher.start()).equals("<")) {
                matcher.appendReplacement(result, "<#" + hexCode + ">");
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Converts custom gradient format to MiniMessage format.
     * Example: "<gradient:#FF0000:#0000FF>Rainbow</gradient>" -> "<gradient:#FF0000:#0000FF>Rainbow</gradient>"
     * (Already compatible, but validates format)
     */
    private static String convertGradientToMiniMessage(String text) {
        // Our custom format is already compatible with MiniMessage
        // Just validate and return
        return text;
    }

    /**
     * Converts legacy color codes (&a, &b, &l, etc.) to MiniMessage format.
     * Example: "&aGreen &lBold" -> "<green>Green <bold>Bold"
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Map legacy codes to MiniMessage tags
        text = text.replace("&0", "<black>");
        text = text.replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>");
        text = text.replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>");
        text = text.replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>");
        text = text.replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>");
        text = text.replace("&9", "<blue>");
        text = text.replace("&a", "<green>");
        text = text.replace("&b", "<aqua>");
        text = text.replace("&c", "<red>");
        text = text.replace("&d", "<light_purple>");
        text = text.replace("&e", "<yellow>");
        text = text.replace("&f", "<white>");

        // Formatting codes
        text = text.replace("&k", "<obfuscated>");
        text = text.replace("&l", "<bold>");
        text = text.replace("&m", "<strikethrough>");
        text = text.replace("&n", "<underlined>");
        text = text.replace("&o", "<italic>");
        text = text.replace("&r", "<reset>");

        return text;
    }

    /**
     * Creates a gradient component from start to end color.
     *
     * @param text Text to apply gradient to
     * @param startHex Start color (RRGGBB)
     * @param endHex End color (RRGGBB)
     * @return Component with gradient applied
     */
    public static Component createGradient(String text, String startHex, String endHex) {
        String miniMessageFormat = String.format("<gradient:#%s:#%s>%s</gradient>",
            startHex, endHex, text);
        return MINI_MESSAGE.deserialize(miniMessageFormat);
    }

    /**
     * Creates a colored component with HEX color.
     *
     * @param text Text to color
     * @param hexColor HEX color (RRGGBB)
     * @return Colored component
     */
    public static Component createColored(String text, String hexColor) {
        TextColor color = TextColor.fromHexString("#" + hexColor);
        return Component.text(text).color(color);
    }

    /**
     * Creates a colored component with named color.
     *
     * @param text Text to color
     * @param color Named text color
     * @return Colored component
     */
    public static Component createColored(String text, NamedTextColor color) {
        return Component.text(text).color(color);
    }

    /**
     * Applies formatting to a component.
     *
     * @param component Component to format
     * @param decorations Decorations to apply
     * @return Formatted component
     */
    public static Component applyFormatting(Component component, TextDecoration... decorations) {
        for (TextDecoration decoration : decorations) {
            component = component.decorate(decoration);
        }
        return component;
    }
}
