package de.boondocksulfur.customjukebox.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scheduler utility for Folia and Paper compatibility.
 * Automatically detects the server type and uses the appropriate scheduler.
 *
 * <p>This class provides a unified API for scheduling tasks that works on both
 * Folia (region-threaded) and Paper/Spigot (single-threaded) servers.
 *
 * <p>Folia access happens via reflection so the plugin keeps loading on servers
 * without the Folia classes. All methods are resolved from Folia's public API
 * interfaces (not the runtime implementation classes, whose non-public types
 * would cause IllegalAccessException on invoke) and cached after first use.
 *
 * @author BoondockSulfur
 * @version 1.4.0
 * @since 1.4.0
 */
public class SchedulerUtil {

    private static Boolean IS_FOLIA = null;

    // Folia public API interfaces (resolved lazily, only on Folia)
    private static final String REGION_SCHEDULER_CLASS = "io.papermc.paper.threadedregions.scheduler.RegionScheduler";
    private static final String ENTITY_SCHEDULER_CLASS = "io.papermc.paper.threadedregions.scheduler.EntityScheduler";
    private static final String ASYNC_SCHEDULER_CLASS = "io.papermc.paper.threadedregions.scheduler.AsyncScheduler";
    private static final String SCHEDULED_TASK_CLASS = "io.papermc.paper.threadedregions.scheduler.ScheduledTask";

    // Cached reflection handles (playback hot path schedules/cancels frequently)
    private static volatile Object regionScheduler;
    private static volatile Method regionRunDelayed;
    private static volatile Method regionRun;
    private static volatile Method entityGetScheduler;
    private static volatile Method entityRunDelayed;
    private static volatile Method entityRun;
    private static volatile Method scheduledTaskCancel;

    /**
     * Platform-independent handle for a scheduled task.
     * Wraps BukkitTask on Paper and Folia's ScheduledTask (via reflection),
     * so delayed tasks are cancellable on both platforms.
     */
    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }

    /**
     * Checks if the server is running Folia.
     * Result is cached after first call.
     *
     * @return true if running on Folia, false otherwise
     */
    public static boolean isFolia() {
        if (IS_FOLIA == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                IS_FOLIA = true;
            } catch (ClassNotFoundException e) {
                IS_FOLIA = false;
            }
        }
        return IS_FOLIA;
    }

    private static Object getRegionScheduler() throws Exception {
        Object scheduler = regionScheduler;
        if (scheduler == null) {
            scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            regionScheduler = scheduler;
        }
        return scheduler;
    }

    private static Method regionRunDelayedMethod() throws Exception {
        Method method = regionRunDelayed;
        if (method == null) {
            method = Class.forName(REGION_SCHEDULER_CLASS)
                .getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            regionRunDelayed = method;
        }
        return method;
    }

    private static Method regionRunMethod() throws Exception {
        Method method = regionRun;
        if (method == null) {
            method = Class.forName(REGION_SCHEDULER_CLASS)
                .getMethod("run", Plugin.class, Location.class, Consumer.class);
            regionRun = method;
        }
        return method;
    }

    private static Object getEntityScheduler(Entity entity) throws Exception {
        Method method = entityGetScheduler;
        if (method == null) {
            // getScheduler() lives on the public Entity interface on Folia/Paper
            method = Entity.class.getMethod("getScheduler");
            entityGetScheduler = method;
        }
        return method.invoke(entity);
    }

    private static Method entityRunDelayedMethod() throws Exception {
        Method method = entityRunDelayed;
        if (method == null) {
            method = Class.forName(ENTITY_SCHEDULER_CLASS)
                .getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            entityRunDelayed = method;
        }
        return method;
    }

    private static Method entityRunMethod() throws Exception {
        Method method = entityRun;
        if (method == null) {
            method = Class.forName(ENTITY_SCHEDULER_CLASS)
                .getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRun = method;
        }
        return method;
    }

    /**
     * Wraps a Folia ScheduledTask (obtained via reflection) into a TaskHandle.
     */
    private static TaskHandle wrapFoliaTask(Object scheduledTask) {
        if (scheduledTask == null) return null;
        return () -> {
            try {
                Method cancel = scheduledTaskCancel;
                if (cancel == null) {
                    // Resolve from the public ScheduledTask interface - the runtime
                    // implementation class may not be accessible for reflection
                    cancel = Class.forName(SCHEDULED_TASK_CLASS).getMethod("cancel");
                    scheduledTaskCancel = cancel;
                }
                cancel.invoke(scheduledTask);
            } catch (Exception ignored) {
                // Task may already have run or been retired - nothing to do
            }
        };
    }

    /**
     * Best-effort fallback when the Folia reflection path fails: try the Bukkit
     * scheduler (works on hybrid forks); on real Folia this throws and we give up.
     */
    private static TaskHandle fallbackRunLater(Plugin plugin, Runnable task, long delayTicks) {
        try {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        } catch (Exception fallbackException) {
            plugin.getLogger().severe("Fallback scheduler also failed: " + fallbackException.getMessage());
            return null;
        }
    }

    /**
     * Runs a task later at a specific location.
     * On Folia: Uses region scheduler for the location.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param location Location for the task (used for Folia region scheduling)
     * @param task Task to run
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     * @return Cancellable task handle (null only if scheduling failed)
     */
    public static TaskHandle runLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                // Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
                Object scheduledTask = regionRunDelayedMethod()
                    .invoke(getRegionScheduler(), plugin, location, (Consumer<Object>) st -> task.run(), delayTicks);
                return wrapFoliaTask(scheduledTask);
            } catch (Exception e) {
                // Critical error - Folia API might have changed
                plugin.getLogger().severe("CRITICAL: Folia scheduler API has changed! Please update CustomJukebox plugin.");
                plugin.getLogger().severe("Error details: " + e.getMessage());
                plugin.getLogger().severe("The plugin may not function correctly on this Folia version.");
                return fallbackRunLater(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use global scheduler
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
    }

    /**
     * Runs a task immediately at a specific location.
     * On Folia: Uses region scheduler for the location.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param location Location for the task
     * @param task Task to run
     */
    public static void run(Plugin plugin, Location location, Runnable task) {
        if (isFolia()) {
            try {
                // Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
                regionRunMethod().invoke(getRegionScheduler(), plugin, location,
                    (Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                plugin.getLogger().severe("CRITICAL: Folia scheduler API has changed! Error: " + e.getMessage());
                // Last resort fallback
                try {
                    Bukkit.getScheduler().runTask(plugin, task);
                } catch (Exception ignored) {}
            }
        } else {
            // Paper/Spigot: Use global scheduler
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a player-specific task later.
     * On Folia: Uses entity scheduler for the player.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param player Player for the task
     * @param task Task to run
     * @param delayTicks Delay in ticks
     * @return Cancellable task handle (null only if scheduling failed)
     */
    public static TaskHandle runPlayerTaskLater(Plugin plugin, Player player, Runnable task, long delayTicks) {
        return runEntityTaskLater(plugin, player, task, delayTicks);
    }

    /**
     * Runs a player-specific task immediately.
     * On Folia: Uses entity scheduler for the player.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param player Player for the task
     * @param task Task to run
     */
    public static void runPlayerTask(Plugin plugin, Player player, Runnable task) {
        if (isFolia()) {
            try {
                // player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
                entityRunMethod().invoke(getEntityScheduler(player), plugin,
                    (Consumer<Object>) scheduledTask -> task.run(), null);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
                try {
                    Bukkit.getScheduler().runTask(plugin, task);
                } catch (Exception ignored) {}
            }
        } else {
            // Paper/Spigot: Use global scheduler
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs an entity-specific task later.
     * On Folia: Uses entity scheduler.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param entity Entity for the task
     * @param task Task to run
     * @param delayTicks Delay in ticks
     * @return Cancellable task handle (null only if scheduling failed)
     */
    public static TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                // entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
                Object scheduledTask = entityRunDelayedMethod()
                    .invoke(getEntityScheduler(entity), plugin, (Consumer<Object>) st -> task.run(), null, delayTicks);
                return wrapFoliaTask(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
                return fallbackRunLater(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use global scheduler
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
    }

    /**
     * Runs an async task (safe on both Folia and Paper).
     * On Folia: Uses async scheduler.
     * On Paper: Uses async scheduler.
     *
     * @param plugin Plugin instance
     * @param task Task to run asynchronously
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                // Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                Class.forName(ASYNC_SCHEDULER_CLASS)
                    .getMethod("runNow", Plugin.class, Consumer.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async scheduler failed: " + e.getMessage());
                try {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
                } catch (Exception ignored) {}
            }
        } else {
            // Paper/Spigot: Use async scheduler
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs an async task later (safe on both Folia and Paper).
     *
     * @param plugin Plugin instance
     * @param task Task to run asynchronously
     * @param delayTicks Delay in ticks
     */
    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                // Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
                long delayMs = delayTicks * 50; // 1 tick = 50ms
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                Class.forName(ASYNC_SCHEDULER_CLASS)
                    .getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async scheduler failed: " + e.getMessage());
                try {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
                } catch (Exception ignored) {}
            }
        } else {
            // Paper/Spigot: Use async scheduler
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * Cancels a task if it's not null. Works on both Paper and Folia.
     *
     * @param task Task handle to cancel
     */
    public static void cancelTask(TaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }
}
