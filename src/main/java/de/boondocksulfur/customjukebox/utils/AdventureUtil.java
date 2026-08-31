package de.boondocksulfur.customjukebox.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
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

    /**
     * MiniMessage restricted to pure formatting tags.
     *
     * <p>Everything routed through {@link #parseComponent(String)} - disc and
     * category names, lore, GUI titles - originates from user input (chat
     * wizards, config files). The full tag set would let that input carry
     * {@code <click:run_command:...>}, {@code <hover:...>}, {@code <insert:...>}
     * and data tags into items other players see. Only colour/decoration tags
     * are needed here, so the rest are not registered and stay literal text.
     */
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
        .tags(TagResolver.builder()
            .resolver(StandardTags.color())
            .resolver(StandardTags.decorations())
            .resolver(StandardTags.gradient())
            .resolver(StandardTags.rainbow())
            .resolver(StandardTags.transition())
            .resolver(StandardTags.font())
            .resolver(StandardTags.reset())
            .resolver(StandardTags.newline())
            .build())
        .build();
    // hexColors(): serialize hex colors as &#rrggbb instead of downsampling
    // them to the nearest &-code (which would break round-trips)
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

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
     * Shortens a name to a visible length, measured after formatting is
     * resolved.
     *
     * <p>An inventory title is drawn into a fixed-width frame and simply runs
     * past it when it is too long. The raw string is no guide to how wide it
     * will be: {@code <gradient:#ff0000:#0000ff>Hi</gradient>} is 41 characters
     * of markup around two visible ones, and cutting the raw string would slice
     * a tag in half. So the visible text decides, and a name that has to be cut
     * loses its formatting rather than its closing tags.
     *
     * @param text        the name, with or without formatting
     * @param maxVisible  how many characters may show
     * @return the name unchanged if it fits, otherwise plain and shortened
     */
    public static String fit(String text, int maxVisible) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String plain = stripColor(text);
        if (plain.length() <= maxVisible) {
            return text;
        }
        return plain.substring(0, Math.max(1, maxVisible - 1)) + "\u2026";
    }

    /**
     * Visible characters an inventory title can show before it runs past the
     * frame. Measured from a screenshot of an overflowing title: 36 bold
     * characters spanned 220 of the 160 usable pixels, so about 26 fit.
     */
    public static final int TITLE_BUDGET = 26;

    /**
     * The part of an id worth showing.
     *
     * <p>A namespace-style prefix distinguishes nothing and eats a third of a
     * title that has no room to spare. Pass the prefix the server's ids
     * actually share where it is known; without one this falls back to
     * dropping everything up to the last dot.
     *
     * @param id the full id
     * @return the id without its prefix
     */
    public static String shortId(String id) {
        return shortId(id, null);
    }

    /**
     * @param prefix the prefix the server's ids actually share, or null to fall
     *               back to stripping whatever namespace-style prefix this one
     *               id happens to carry
     */
    public static String shortId(String id, String prefix) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        if (prefix != null && !prefix.isEmpty()) {
            return id.startsWith(prefix) && id.length() > prefix.length()
                ? id.substring(prefix.length())
                : id;
        }
        if (prefix != null) {
            return id; // a known-empty shared prefix: nothing to strip
        }
        int dot = id.lastIndexOf('.');
        return dot >= 0 && dot < id.length() - 1 ? id.substring(dot + 1) : id;
    }

    /**
     * Builds an inventory title whose variable part cannot push it past the
     * frame, counting the fixed prefix against the same budget.
     *
     * @param prefix fixed leading text, colour codes included
     * @param name   the disc, zone, playlist or category name to append
     */
    public static String fitTitle(String prefix, String name) {
        String visiblePrefix = stripColor(prefix);
        return prefix + fit(name, Math.max(4, TITLE_BUDGET - visiblePrefix.length()));
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

        // Also handle #RRGGBB format (without &).
        // Skip codes that are already part of a MiniMessage tag: directly after
        // '<' (e.g. <#FF5555>) or after ':' (e.g. <gradient:#FF0000:#0000FF>).
        text = result.toString();
        Pattern simpleHexPattern = Pattern.compile("(?<![<:])#([A-Fa-f0-9]{6})");
        matcher = simpleHexPattern.matcher(text);
        result = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(result, "<#" + hexCode + ">");
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
    private static final char LEGACY_CHAR = '\u00a7';

    private static final java.util.Map<Character, String> LEGACY_TAGS = java.util.Map.ofEntries(
        java.util.Map.entry('0', "<black>"),
        java.util.Map.entry('1', "<dark_blue>"),
        java.util.Map.entry('2', "<dark_green>"),
        java.util.Map.entry('3', "<dark_aqua>"),
        java.util.Map.entry('4', "<dark_red>"),
        java.util.Map.entry('5', "<dark_purple>"),
        java.util.Map.entry('6', "<gold>"),
        java.util.Map.entry('7', "<gray>"),
        java.util.Map.entry('8', "<dark_gray>"),
        java.util.Map.entry('9', "<blue>"),
        java.util.Map.entry('a', "<green>"),
        java.util.Map.entry('b', "<aqua>"),
        java.util.Map.entry('c', "<red>"),
        java.util.Map.entry('d', "<light_purple>"),
        java.util.Map.entry('e', "<yellow>"),
        java.util.Map.entry('f', "<white>"),
        java.util.Map.entry('k', "<obfuscated>"),
        java.util.Map.entry('l', "<bold>"),
        java.util.Map.entry('m', "<strikethrough>"),
        java.util.Map.entry('n', "<underlined>"),
        java.util.Map.entry('o', "<italic>"),
        java.util.Map.entry('r', "<reset>"));

    private static String convertLegacyToMiniMessage(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Both marker characters, in either case. Leaving a section sign in
            // place made MiniMessage throw, and the catch fell back to the
            // legacy serializer - which printed every converted tag as visible
            // text. That is why "&e" in an author or lore line showed up as
            // "<yellow>" in game while unformatted text looked fine.
            if ((c == '&' || c == LEGACY_CHAR) && i + 1 < text.length()) {
                String tag = LEGACY_TAGS.get(Character.toLowerCase(text.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
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
        if (color == null) {
            // Invalid hex color, return uncolored text
            return Component.text(text);
        }
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
