package com.pixelcatt.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for Folia-compatible scheduling.
 * <p>
 * Folia uses regionized threading, so the traditional {@code Bukkit.getScheduler().runTask()}
 * is not available. This helper detects Folia at runtime and delegates to the appropriate
 * scheduler API:
 * <ul>
 *   <li>{@code Entity.getScheduler()} for entity-bound tasks</li>
 *   <li>{@code RegionScheduler} for location-bound tasks</li>
 *   <li>{@code GlobalRegionScheduler} for server-wide tasks</li>
 * </ul>
 * On non-Folia servers it falls back to {@code Bukkit.getScheduler().runTask()}.
 */
public final class FoliaHelper {

    private static final boolean FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            // Not running on Folia
        }
        FOLIA = folia;
    }

    // ------------------------------------------------------------
    //  Detection
    // ------------------------------------------------------------

    /**
     * @return {@code true} if the server is running Folia
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    // ------------------------------------------------------------
    //  Global scheduler  (no specific entity / location)
    // ------------------------------------------------------------

    /**
     * Schedule a task on the global region scheduler (Folia) or the main
     * thread scheduler (non-Folia). Use this when you do <b>not</b> have
     * a specific player or location context.
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ------------------------------------------------------------
    //  Entity scheduler  (player / entity bound)
    // ------------------------------------------------------------

    /**
     * Schedule a task on the region thread that owns the given entity.
     * Use this for player-specific work such as sending messages,
     * teleporting, or inventory manipulation.
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Convenience overload for {@link Player}.
     */
    public static void runOnEntity(Plugin plugin, Player player, Runnable task) {
        runOnEntity(plugin, (Entity) player, task);
    }

    // ------------------------------------------------------------
    //  Location / Region scheduler  (world / chunk bound)
    // ------------------------------------------------------------

    /**
     * Schedule a task on the region thread that owns the given location.
     * Use this for block manipulation, item drops, or any world-altering
     * operation at a specific location.
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ------------------------------------------------------------
    //  Console command execution
    // ------------------------------------------------------------

    /**
     * Execute a console command on the appropriate scheduler. In Folia
     * this is scheduled via the global region scheduler; on non-Folia
     * it is scheduled on the main thread.
     */
    public static void executeConsoleCommand(Plugin plugin, String command) {
        Runnable cmdTask = () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> cmdTask.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, cmdTask);
        }
    }

    // Private constructor – utility class
    private FoliaHelper() {}
}
