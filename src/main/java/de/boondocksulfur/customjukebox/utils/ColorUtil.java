package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for advanced color code processing.
 * Supports:
 * - Legacy color codes (&a, &b, &c, etc.)
 * - HEX color codes (&#RRGGBB or #RRGGBB)
 * - Gradients (<gradient:#START:#END>text</gradient>)
 *
 * <p><b>DEPRECATION NOTICE (v2.1.0):</b></p>
 * <ul>
 *   <li>This class uses deprecated Bukkit/BungeeCord APIs (ChatColor, BungeeCord ChatColor)</li>
 *   <li><b>All internal uses have been migrated to {@link AdventureUtil}</b></li>
 *   <li>This class is kept ONLY for backwards API compatibility</li>
 *   <li><b>NEW CODE SHOULD USE {@link AdventureUtil} INSTEAD</b></li>
 * </ul>
 *
 * <p><b>Migration Guide:</b></p>
 * <pre>
 * // Old (deprecated):
 * String colored = ColorUtil.colorize("&aGreen text");
 *
 * // New (recommended):
 * String colored = AdventureUtil.toLegacy(AdventureUtil.parseComponent("&aGreen text"));
 * </pre>
 *
 * @author BoondockSulfur
 * @version 2.1.0
 * @since 1.3.1
 * @deprecated Use {@link AdventureUtil} for new code. This class is kept for backwards API compatibility only.
 */
@Deprecated
public class ColorUtil {

    // Pattern for HEX colors: &#RRGGBB or #RRGGBB (but not &#RRGGBB for ALT pattern)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Negative lookbehind (?<!&) ensures we don't match &#RRGGBB (which was already processed)
    private static final Pattern HEX_PATTERN_ALT = Pattern.compile("(?<!&)#([A-Fa-f0-9]{6})");

    // Pattern for gradients: <gradient:#START:#END>text</gradient>
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");

    /**
     * Translates all color codes in a message.
     * Supports legacy codes (&a-&f), HEX colors, and gradients.
     *
     * @param message The message to colorize
     * @return The colorized message
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Process gradients first (they contain other color codes)
        message = processGradients(message);

        // Process HEX colors
        message = processHexColors(message);

        // Process legacy color codes (&a, &b, etc.)
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    /**
     * Processes HEX color codes in format &#RRGGBB or #RRGGBB.
     */
    private static String processHexColors(String message) {
        // Process &#RRGGBB format
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        message = buffer.toString();

        // Process #RRGGBB format (only if not already processed as &#)
        matcher = HEX_PATTERN_ALT.matcher(message);
        buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Processes gradient color codes in format <gradient:#START:#END>text</gradient>.
     */
    private static String processGradients(String message) {
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);

            String gradientText = applyGradient(text, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Applies a color gradient to text.
     *
     * @param text The text to apply gradient to
     * @param startHex Starting color in hex (without #)
     * @param endHex Ending color in hex (without #)
     * @return The text with gradient applied
     */
    private static String applyGradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Remove existing color codes from text to get clean length
        String cleanText = ChatColor.stripColor(text.replace("&", "§"));
        if (cleanText == null || cleanText.isEmpty()) {
            return text;
        }

        int length = cleanText.length();
        if (length == 1) {
            // Single character, just use start color
            return net.md_5.bungee.api.ChatColor.of("#" + startHex) + text;
        }

        // Parse start and end colors
        int startRed = Integer.parseInt(startHex.substring(0, 2), 16);
        int startGreen = Integer.parseInt(startHex.substring(2, 4), 16);
        int startBlue = Integer.parseInt(startHex.substring(4, 6), 16);

        int endRed = Integer.parseInt(endHex.substring(0, 2), 16);
        int endGreen = Integer.parseInt(endHex.substring(2, 4), 16);
        int endBlue = Integer.parseInt(endHex.substring(4, 6), 16);

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < length; i++) {
            // Calculate color for this character
            float ratio = (float) i / (float) (length - 1);

            int red = (int) (startRed + ratio * (endRed - startRed));
            int green = (int) (startGreen + ratio * (endGreen - startGreen));
            int blue = (int) (startBlue + ratio * (endBlue - startBlue));

            // Convert to hex
            String hexColor = String.format("#%02X%02X%02X", red, green, blue);

            // Apply color to character
            result.append(net.md_5.bungee.api.ChatColor.of(hexColor));
            result.append(cleanText.charAt(i));
        }

        return result.toString();
    }

    /**
     * Strips all color codes (legacy, HEX, and formatting codes) from a message.
     *
     * @param message The message to strip colors from
     * @return The message without any color codes
     */
    public static String stripColor(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Remove gradient tags first
        message = message.replaceAll("<gradient:#[A-Fa-f0-9]{6}:#[A-Fa-f0-9]{6}>(.*?)</gradient>", "$1");

        // Remove HEX colors
        message = message.replaceAll("&#[A-Fa-f0-9]{6}", "");
        message = message.replaceAll("#[A-Fa-f0-9]{6}", "");

        // Remove legacy colors
        message = ChatColor.stripColor(message);

        return message;
    }

    /**
     * Returns a formatted example string showing all supported color formats.
     *
     * @return Example string with color format documentation
     */
    public static String getColorFormatHelp() {
        return "§7Supported color formats:\n" +
               "§8• §7Legacy codes: §e&a, &b, &c, &l, &o, etc.\n" +
               "§8• §7HEX colors: §e&#FF5555 §7or §e#FF5555\n" +
               "§8• §7Gradients: §e<gradient:#FF0000:#0000FF>text</gradient>\n" +
               "§8• §7Formats: §e&l§7(bold), §e&o§7(italic), §e&n§7(underline), §e&m§7(strike)";
    }

    /**
     * Converts a legacy string to Adventure Component.
     * This is a convenience method that delegates to AdventureUtil.
     *
     * @param text Legacy text with color codes
     * @return Adventure Component
     */
    public static Component toComponent(String text) {
        return AdventureUtil.parseComponent(text);
    }
}
