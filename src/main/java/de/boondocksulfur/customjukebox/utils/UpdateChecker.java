package de.boondocksulfur.customjukebox.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.boondocksulfur.customjukebox.CustomJukebox;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Checks for plugin updates via Modrinth API.
 */
public class UpdateChecker {

    private final CustomJukebox plugin;
    private final String projectId;
    private String latestVersion = null;
    private String downloadUrl = null;
    private boolean updateAvailable = false;

    public UpdateChecker(CustomJukebox plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
    }

    /**
     * Checks for updates asynchronously.
     * Folia-compatible: Uses SchedulerUtil for async tasks.
     */
    public CompletableFuture<Void> checkForUpdates() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        SchedulerUtil.runAsync(plugin, () -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                String currentVersion = plugin.getPluginMeta().getVersion();
                String mcVersion = Bukkit.getMinecraftVersion();

                // Modrinth API endpoint filtered by game version
                String gameVersionsParam = URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8);
                String apiUrl = "https://api.modrinth.com/v2/project/" + projectId
                    + "/version?game_versions=" + gameVersionsParam;

                URL url = java.net.URI.create(apiUrl).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "CustomJukebox/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("Failed to check for updates: HTTP " + responseCode);
                    future.complete(null);
                    return;
                }

                java.io.InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Parse JSON response
                JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();

                if (versions.isEmpty()) {
                    // No versions for this game version — nothing to update to
                    future.complete(null);
                    return;
                }

                // Get the latest version (first in array)
                JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                latestVersion = latestVersionObj.get("version_number").getAsString();

                // Get download URL
                JsonArray files = latestVersionObj.getAsJsonArray("files");
                if (files != null && files.size() > 0) {
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    downloadUrl = primaryFile.get("url").getAsString();
                }

                // Compare versions (semantic versioning)
                int comparison = compareVersions(currentVersion, latestVersion);

                if (comparison < 0) {
                    // Current version is older than latest
                    updateAvailable = true;
                    plugin.getLogger().info("====================================");
                    plugin.getLogger().info("UPDATE AVAILABLE!");
                    plugin.getLogger().info("Current version: " + currentVersion);
                    plugin.getLogger().info("Latest version: " + latestVersion);
                    plugin.getLogger().info("Download: https://modrinth.com/plugin/bs-customjukebox");
                    plugin.getLogger().info("====================================");
                } else if (comparison > 0) {
                    // Current version is newer than latest (development build)
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("You are running a development version (" + currentVersion + ")");
                    }
                } else {
                    // Versions are equal - no message needed (keeps logs clean)
                }

                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                future.completeExceptionally(e);
            } finally {
                // Properly close resources
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception e) {
                    // Ignore close errors
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });

        return future;
    }

    /**
     * Returns whether an update is available.
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Returns the latest version string.
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the download URL for the latest version.
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Returns the current version.
     */
    public String getCurrentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Compares two semantic version strings.
     *
     * @param version1 First version (e.g., "1.3.0")
     * @param version2 Second version (e.g., "1.0.1")
     * @return negative if version1 < version2, positive if version1 > version2, 0 if equal
     */
    private int compareVersions(String version1, String version2) {
        // Remove any non-numeric prefixes (like "v")
        version1 = version1.replaceAll("^[^0-9]+", "");
        version2 = version2.replaceAll("^[^0-9]+", "");

        // Split by dots
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        // Compare each part
        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }

        return 0; // Versions are equal
    }

    /**
     * Parses a version part, extracting only the numeric portion.
     * Handles versions like "1.3.0-SNAPSHOT" by ignoring suffixes.
     */
    private int parseVersionPart(String part) {
        try {
            // Extract only the numeric part before any non-numeric character
            String numericPart = part.split("[^0-9]")[0];
            return numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
