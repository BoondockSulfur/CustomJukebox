package de.boondocksulfur.customjukebox.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;

/**
 * Helpers for keeping JSON config files in sync with the plugin's bundled
 * defaults. When a new plugin version adds config keys, existing user files
 * are missing them; {@link #mergeDefaults} fills those in without touching
 * values the user already set.
 *
 * @author BoondockSulfur
 * @since 3.2.0
 */
public class JsonConfigUtil {

    private JsonConfigUtil() {
    }

    /**
     * Recursively adds keys that exist in {@code defaults} but are missing from
     * {@code target}. Existing values in {@code target} are never overwritten.
     *
     * <p>Top-level keys listed in {@code protectedSections} are treated as
     * user-owned content maps (e.g. the {@code discs} section of disc.json):
     * they are only created as an empty object when entirely absent, and their
     * contents are never populated from the defaults — so example entries a
     * user deleted do not reappear.
     *
     * @param target            the user's config (modified in place)
     * @param defaults          the bundled default config
     * @param protectedSections top-level keys to treat as user content (may be empty)
     * @return true if any key was added (caller should persist the file)
     */
    public static boolean mergeDefaults(JsonObject target, JsonObject defaults, Set<String> protectedSections) {
        return merge(target, defaults, protectedSections, true);
    }

    private static boolean merge(JsonObject target, JsonObject defaults, Set<String> protectedSections, boolean topLevel) {
        boolean changed = false;

        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            String key = entry.getKey();
            JsonElement defaultValue = entry.getValue();
            boolean isProtected = topLevel && protectedSections != null && protectedSections.contains(key);

            if (!target.has(key) || target.get(key).isJsonNull()) {
                // Missing key: add it. Protected sections are created empty so
                // user content maps don't get seeded with default examples.
                if (isProtected && defaultValue.isJsonObject()) {
                    target.add(key, new JsonObject());
                } else {
                    target.add(key, defaultValue.deepCopy());
                }
                changed = true;
                continue;
            }

            // Key present in both: recurse into nested objects, but never into
            // protected user-content sections.
            if (!isProtected && defaultValue.isJsonObject() && target.get(key).isJsonObject()) {
                changed |= merge(target.getAsJsonObject(key), defaultValue.getAsJsonObject(), protectedSections, false);
            }
        }

        return changed;
    }
}
