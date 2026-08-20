package de.boondocksulfur.customjukebox.utils;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Shared timestamped-backup handling for the plugin's JSON config files
 * (config.json, disc.json, zones.json).
 *
 * <p><b>Why the throttle.</b> Every edit made through a GUI or command saves the
 * file it belongs to. Backing up on literally every save meant a short editing
 * session rotated the whole retained history away within a handful of clicks -
 * exactly the state a user would want to roll back to was the first one deleted.
 * A new backup is therefore only taken when the newest existing one is older
 * than {@code minIntervalMillis}, so the retained set spans real history instead
 * of the last few seconds.
 *
 * @author BoondockSulfur
 * @since 3.3.0
 */
public final class BackupUtil {

    private static final String BACKUP_MARKER = "_backup_";

    private BackupUtil() {
    }

    /**
     * Creates a timestamped backup of a file and prunes old ones.
     *
     * @param plugin            plugin (for logging)
     * @param file              file to back up (no-op if it does not exist)
     * @param maxBackups        how many backups to keep; {@code 0} disables backups
     *                          and prunes any that already exist
     * @param minIntervalMillis minimum age of the newest existing backup before a
     *                          new one is taken; {@code 0} backs up on every save
     */
    public static void createBackup(Plugin plugin, File file, int maxBackups, long minIntervalMillis) {
        if (file == null || !file.exists()) {
            return;
        }

        if (maxBackups <= 0) {
            // Backups disabled - also prune any that already exist
            cleanupOldBackups(file, 0);
            return;
        }

        try {
            if (minIntervalMillis > 0) {
                File newest = getLatestBackup(file);
                if (newest != null && (System.currentTimeMillis() - newest.lastModified()) < minIntervalMillis) {
                    // A recent backup already covers this editing session; still
                    // enforce the retention limit in case it was lowered.
                    cleanupOldBackups(file, maxBackups);
                    return;
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupFile = new File(file.getParentFile(), backupName(file, timestamp));
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            cleanupOldBackups(file, maxBackups);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create backup for " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Returns the most recent backup of a file, or null if there is none.
     *
     * @param file original file
     * @return newest backup file or null
     */
    public static File getLatestBackup(File file) {
        File[] backups = listBackups(file);
        if (backups == null || backups.length == 0) {
            return null;
        }
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        return backups[0];
    }

    /**
     * Deletes the oldest backups so that at most {@code maxBackups} remain.
     *
     * @param file       original file
     * @param maxBackups number of backups to keep (0 deletes all)
     */
    public static void cleanupOldBackups(File file, int maxBackups) {
        File[] backups = listBackups(file);
        if (backups == null || backups.length <= maxBackups) {
            return;
        }
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        int toDelete = backups.length - maxBackups;
        for (int i = 0; i < toDelete; i++) {
            backups[i].delete();
        }
    }

    private static File[] listBackups(File file) {
        File parent = file.getParentFile();
        if (parent == null || !parent.exists()) {
            return null;
        }
        String baseName = stripExtension(file.getName());
        return parent.listFiles((dir, name) ->
            name.startsWith(baseName + BACKUP_MARKER) && name.endsWith(".json"));
    }

    private static String backupName(File file, String timestamp) {
        return stripExtension(file.getName()) + BACKUP_MARKER + timestamp + ".json";
    }

    /**
     * Strips a trailing ".json" only at the end of the name - the previous
     * implementations used {@code replace(".json", ...)}, which also mangled the
     * name of a file that contained ".json" somewhere in the middle.
     */
    private static String stripExtension(String fileName) {
        return fileName.endsWith(".json")
            ? fileName.substring(0, fileName.length() - ".json".length())
            : fileName;
    }
}
