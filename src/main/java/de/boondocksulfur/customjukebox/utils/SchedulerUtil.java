package de.boondocksulfur.customjukebox.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler utility for Folia and Paper compatibility.
 * Automatically detects the server type and uses the appropriate scheduler.
 *
 * <p>This class provides a unified API for scheduling tasks that works on both
 * Folia (region-threaded) and Paper/Spigot (single-threaded) servers.
 *
 * @author BoondockSulfur
 * @version 1.4.0
 * @since 1.4.0
 */
public class SchedulerUtil {

    private static Boolean IS_FOLIA = null;

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

    /**
     * Runs a task later at a specific location.
     * On Folia: Uses region scheduler for the location.
     * On Paper: Uses global scheduler.
     *
     * @param plugin Plugin instance
     * @param location Location for the task (used for Folia region scheduling)
     * @param task Task to run
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     * @return Cancellable task (null on Folia)
     */
    public static BukkitTask runLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia()) {
            // Folia: Use region scheduler via reflection (to avoid compile-time dependency)
            try {
                // Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
                Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                regionScheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, java.util.function.Consumer.class, long.class)
                    .invoke(regionScheduler, plugin, location, (java.util.function.Consumer<Object>) scheduledTask -> task.run(), delayTicks);
                return null; // Folia doesn't return BukkitTask
            } catch (Exception e) {
                plugin.getLogger().warning("Folia scheduler failed, falling back to Bukkit scheduler: " + e.getMessage());
                // Fallback to global scheduler if reflection fails
                return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use global scheduler
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
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
            // Folia: Use region scheduler via reflection
            try {
                // Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
                Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, java.util.function.Consumer.class)
                    .invoke(regionScheduler, plugin, location, (java.util.function.Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia scheduler failed: " + e.getMessage());
                // Fallback to global scheduler if reflection fails
                Bukkit.getScheduler().runTask(plugin, task);
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
     * @return Cancellable task (null on Folia)
     */
    public static BukkitTask runPlayerTaskLater(Plugin plugin, Player player, Runnable task, long delayTicks) {
        if (isFolia()) {
            // Folia: Use entity scheduler via reflection
            try {
                // player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
                Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
                entityScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class)
                    .invoke(entityScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run(), null, delayTicks);
                return null;
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
                return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use global scheduler
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
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
            // Folia: Use entity scheduler via reflection
            try {
                // player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
                Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
                entityScheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(entityScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run(), null);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, task);
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
     * @return Cancellable task (null on Folia)
     */
    public static BukkitTask runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFolia()) {
            // Folia: Use entity scheduler via reflection
            try {
                // entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
                Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                entityScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class)
                    .invoke(entityScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run(), null, delayTicks);
                return null;
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
                return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use global scheduler
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
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
            // Folia: Use async scheduler
            try {
                // Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async scheduler failed: " + e.getMessage());
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
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
            // Folia: Use async scheduler with milliseconds
            try {
                // Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
                long delayMs = delayTicks * 50; // 1 tick = 50ms
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async scheduler failed: " + e.getMessage());
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            }
        } else {
            // Paper/Spigot: Use async scheduler
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * Cancels a task if it's not null.
     * On Folia: Does nothing (tasks can't be cancelled the same way).
     * On Paper: Cancels the task.
     *
     * @param task Task to cancel
     */
    public static void cancelTask(BukkitTask task) {
        if (task != null && !isFolia()) {
            task.cancel();
        }
    }
}
