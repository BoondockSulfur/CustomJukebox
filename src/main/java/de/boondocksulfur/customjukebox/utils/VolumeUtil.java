package de.boondocksulfur.customjukebox.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Shared parsing and formatting for every volume the plugin exposes, so the
 * global volume and a zone's volume understand the same words and print the
 * same way.
 *
 * <p>The scale is Minecraft's: 1.0 is the sound at full loudness, and anything
 * above it only widens the audible radius rather than making the sound louder
 * at its source. That surprises people - 1 looks like a low number on a 0..4
 * scale - which is why {@link #describe(float)} always states the percentage.
 */
public final class VolumeUtil {

    public static final float MIN = 0.0f;
    public static final float MAX = 4.0f;
    public static final float INVALID = -1f;

    /**
     * Full loudness. Above this Minecraft widens the audible radius
     * ({@code volume * 16} blocks) instead of raising the gain, so 4.0 and 1.0
     * sound identical standing next to the source.
     */
    public static final float FULL = 1.0f;

    public static final List<String> PRESETS =
        Arrays.asList("silent", "quiet", "normal", "loud", "max");

    private VolumeUtil() {
    }

    /**
     * Accepts a preset name, a percentage such as {@code 30%}, or a plain
     * number.
     *
     * @return the volume, or {@link #INVALID} if the input is not usable
     */
    public static float parse(String input) {
        if (input == null || input.isEmpty()) {
            return INVALID;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);

        float preset = parsePreset(value);
        if (preset != INVALID) {
            return preset;
        }

        // Decibels give even steps to the ear where the linear scale does not:
        // 0.5 is not "half as loud", but -6 dB always sounds like one step down.
        if (value.endsWith("db")) {
            try {
                float db = Float.parseFloat(value.substring(0, value.length() - 2).trim());
                if (db > 0) {
                    return INVALID; // above 1.0 only widens the radius, so cap at 0 dB
                }
                float gain = (float) Math.pow(10, db / 20.0);
                return Math.max(MIN, Math.min(MAX, gain));
            } catch (NumberFormatException e) {
                return INVALID;
            }
        }

        boolean percent = value.endsWith("%");
        if (percent) {
            value = value.substring(0, value.length() - 1).trim();
        }
        try {
            float number = Float.parseFloat(value);
            if (percent) {
                number /= 100f;
            }
            // The negated comparison also rejects NaN
            return (number >= MIN && number <= MAX) ? number : INVALID;
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }

    public static float parsePreset(String preset) {
        switch (preset) {
            case "silent":
            case "mute":
            case "off":
                return 0.0f;
            case "quiet":
            case "low":
            case "soft":
                return 0.5f;
            case "normal":
            case "default":
            case "medium":
                return 1.0f;
            case "loud":
            case "high":
                return 2.0f;
            case "max":
            case "maximum":
            case "full":
                return 4.0f;
            default:
                return INVALID;
        }
    }

    /**
     * e.g. {@code 0.30 (30%, -10.5 dB)} - the percentage is what people read,
     * the decibels are what lets them judge the next step.
     */
    public static String describe(float volume) {
        if (volume <= 0f) {
            return "0.00 (silent)";
        }
        return String.format(Locale.ROOT, "%.2f (%d%%, %.1f dB)",
            volume, Math.round(volume * 100), 20 * Math.log10(volume));
    }

    public static String format(float volume) {
        return String.format(Locale.ROOT, "%.2f", volume);
    }
}
