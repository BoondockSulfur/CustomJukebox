package de.boondocksulfur.customjukebox.manager;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages multi-language support for the plugin.
 * Supports: en (English), de (German), es (Spanish), it (Italian) like JEXT.
 *
 * Features:
 * - YAML-based language files
 * - Placeholder replacement ({player}, {disc}, etc.)
 * - Fallback to English if translation missing
 * - Color code support (&)
 */
public class LanguageManager {

    private final CustomJukebox plugin;
    private final Map<String, FileConfiguration> languages;
    private String currentLanguage;
    private FileConfiguration currentConfig;

    // Supported languages (same as JEXT)
    private static final String[] SUPPORTED_LANGUAGES = {"en", "de", "es", "it"};
    private static final String DEFAULT_LANGUAGE = "en";

    public LanguageManager(CustomJukebox plugin) {
        this.plugin = plugin;
        this.languages = new HashMap<>();
        loadLanguages();
    }

    /**
     * Loads all language files.
     */
    private void loadLanguages() {
        // Create languages folder
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Load each supported language
        for (String lang : SUPPORTED_LANGUAGES) {
            loadLanguage(lang);
        }

        // Set current language from config
        String configLang = plugin.getConfigManager().getLanguage();
        setLanguage(configLang);
    }

    /**
     * Loads a specific language file.
     * @param langCode Language code (en, de, es, it)
     */
    private void loadLanguage(String langCode) {
        File langFile = new File(plugin.getDataFolder(), "languages/" + langCode + ".yml");

        // Save default if doesn't exist
        if (!langFile.exists()) {
            plugin.saveResource("languages/" + langCode + ".yml", false);
        }

        // Load file
        FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("languages/" + langCode + ".yml");
        if (defaultStream != null) {
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        languages.put(langCode, config);

        plugin.getLogger().info("Loaded language: " + langCode);
    }

    /**
     * Sets the current language.
     * @param langCode Language code
     */
    public void setLanguage(String langCode) {
        if (!languages.containsKey(langCode)) {
            plugin.getLogger().warning("Language '" + langCode + "' not found! Falling back to English.");
            langCode = DEFAULT_LANGUAGE;
        }

        this.currentLanguage = langCode;
        this.currentConfig = languages.get(langCode);

        plugin.getLogger().info("Using language: " + langCode);
    }

    /**
     * Gets a message in the current language.
     * @param key Message key
     * @return Translated message with color codes
     */
    public String getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }

    /**
     * Gets a message with placeholder replacement.
     * @param key Message key
     * @param placeholders Map of placeholders (e.g. "player" -> "Steve")
     * @return Translated message with placeholders replaced and color codes
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getRawMessage(key);

        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Apply color codes
        return colorize(message);
    }

    /**
     * Gets a message with single placeholder.
     * @param key Message key
     * @param placeholder Placeholder name
     * @param value Placeholder value
     * @return Translated message
     */
    public String getMessage(String key, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(key, placeholders);
    }

    /**
     * Gets raw message without color codes.
     * @param key Message key
     * @return Raw message
     */
    public String getRawMessage(String key) {
        if (currentConfig == null) {
            return key; // Fallback to key itself
        }

        String message = currentConfig.getString(key);

        // Fallback to English if not found
        if (message == null && !currentLanguage.equals(DEFAULT_LANGUAGE)) {
            FileConfiguration englishConfig = languages.get(DEFAULT_LANGUAGE);
            if (englishConfig != null) {
                message = englishConfig.getString(key);
            }
        }

        // Final fallback
        if (message == null) {
            plugin.getLogger().warning("Missing translation key: " + key);
            return key;
        }

        return message;
    }

    /**
     * Gets the prefix message.
     * @return Prefix with color codes
     */
    public String getPrefix() {
        return getMessage("prefix");
    }

    /**
     * Gets a message with prefix.
     * @param key Message key
     * @return Prefixed message
     */
    public String getMessageWithPrefix(String key) {
        return getPrefix() + getMessage(key);
    }

    /**
     * Gets a message with prefix and placeholders.
     * @param key Message key
     * @param placeholders Placeholders
     * @return Prefixed message with placeholders
     */
    public String getMessageWithPrefix(String key, Map<String, String> placeholders) {
        return getPrefix() + getMessage(key, placeholders);
    }

    /**
     * Reloads all language files.
     */
    public void reload() {
        languages.clear();
        loadLanguages();
    }

    /**
     * Gets the current language code.
     * @return Language code (e.g. "en", "de")
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Checks if a language is supported.
     * @param langCode Language code
     * @return true if supported
     */
    public boolean isLanguageSupported(String langCode) {
        for (String lang : SUPPORTED_LANGUAGES) {
            if (lang.equalsIgnoreCase(langCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all supported languages.
     * @return Array of language codes
     */
    public String[] getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Applies color codes to a message.
     * Supports legacy codes, HEX colors, and gradients.
     * @param message Message with color codes
     * @return Message with Minecraft color codes
     */
    private String colorize(String message) {
        return AdventureUtil.toLegacy(AdventureUtil.parseComponent(message));
    }
}
