package de.boondocksulfur.customjukebox.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Writes the plugin's JSON config files off the server thread.
 *
 * <p>Saving used to run inline on whichever thread triggered it - which for GUI
 * clicks and commands is the main (or, on Folia, a region) thread. A single save
 * copies a backup, lists and prunes the backup directory, serialises the whole
 * document and moves it into place; on a busy or slow disk that is a stall the
 * server pays for.
 *
 * <p><b>How consistency is kept.</b> Callers hand over a snapshot taken on their
 * own thread ({@code JsonObject.deepCopy()}), so later edits cannot change what
 * is being written. Writes run on one worker thread, so they can never overtake
 * each other, and a file that is saved again while an earlier save is still
 * queued simply replaces the queued content - a burst of GUI clicks collapses
 * into far fewer writes instead of one per click.
 *
 * <p><b>Durability.</b> Every write goes to a temporary file that is then moved
 * into place atomically, so an interrupted write can never truncate the real
 * file. {@link #flush()} is called before any read and {@link #shutdown()} on
 * plugin disable, so no pending write is ever lost or read around.
 *
 * @author BoondockSulfur
 * @since 3.3.0
 */
public final class ConfigWriter {

    private static final long FLUSH_TIMEOUT_SECONDS = 15;

    private final Plugin plugin;
    private final Gson gson;
    private final ExecutorService worker;
    /** Latest queued content per file; a newer save replaces an unwritten older one. */
    private final Map<Path, PendingWrite> pending = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    /**
     * @param plugin owning plugin (used for logging and the thread name)
     */
    public ConfigWriter(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping() // Prevent & from becoming &amp; in JSON
            .create();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, plugin.getName() + "-config-writer");
            // Daemon: an explicit shutdown() on disable flushes everything; the
            // flag only prevents a stuck write from holding up JVM exit.
            thread.setDaemon(true);
            return thread;
        });
    }

    /** One queued save. */
    private record PendingWrite(JsonObject content, int maxBackups, long backupIntervalMillis) {
    }

    /**
     * Queues a file save.
     *
     * @param file target file
     * @param snapshot content to write; the caller must not mutate it afterwards
     *                 (pass a {@code deepCopy()})
     * @param maxBackups backups to keep, see {@link BackupUtil}
     * @param backupIntervalMillis backup throttle, see {@link BackupUtil}
     */
    public void save(File file, JsonObject snapshot, int maxBackups, long backupIntervalMillis) {
        Path path = file.toPath();
        pending.put(path, new PendingWrite(snapshot, maxBackups, backupIntervalMillis));

        if (stopped) {
            // Disable already ran - write inline rather than dropping the data
            drain(path);
            return;
        }
        try {
            worker.execute(() -> drain(path));
        } catch (Exception e) {
            plugin.getLogger().warning("Config writer unavailable, saving " + file.getName()
                + " inline: " + e.getMessage());
            drain(path);
        }
    }

    /**
     * Blocks until every queued write has completed.
     *
     * <p>Must be called before reading a config file back from disk, otherwise a
     * reload could read a version that a still-queued write is about to replace.
     */
    public void flush() {
        if (stopped) {
            return;
        }
        try {
            // The worker is single-threaded and FIFO, so an empty task completes
            // only once everything queued before it has been written.
            worker.submit(() -> null).get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Timed out waiting for pending config writes", e);
        }
    }

    /**
     * Flushes pending writes and stops the worker. Saves issued afterwards are
     * written inline, so late shutdown code cannot lose data.
     */
    public void shutdown() {
        if (stopped) {
            return;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Config writer did not finish within "
                    + FLUSH_TIMEOUT_SECONDS + "s - writing the remainder inline");
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stopped = true;

        // Anything the worker did not get to (or that was queued during shutdown)
        for (Path path : pending.keySet().toArray(new Path[0])) {
            drain(path);
        }
    }

    /** Writes the newest queued content for a file, if any is still outstanding. */
    private void drain(Path path) {
        PendingWrite write = pending.remove(path);
        if (write == null) {
            return; // A later save already wrote this file
        }
        try {
            writeAtomically(path, write);
        } catch (IOException e) {
            // The original file is untouched: content only ever reaches it via an
            // atomic move of a fully written temp file.
            plugin.getLogger().log(Level.SEVERE,
                "Failed to save " + path.getFileName() + " - the previous version is still intact", e);
        }
    }

    private void writeAtomically(Path path, PendingWrite write) throws IOException {
        File file = path.toFile();
        BackupUtil.createBackup(plugin, file, write.maxBackups(), write.backupIntervalMillis());

        Path temp = path.resolveSibling(file.getName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(write.content(), writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
