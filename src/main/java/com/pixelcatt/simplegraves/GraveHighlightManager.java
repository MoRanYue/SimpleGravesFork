package com.pixelcatt.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically checks whether players are near their own graves and
 * highlights them with a glowing invisible armor stand.
 * <p>
 * Thread-safe for Folia: the repeating task runs on the global region
 * scheduler, and entity spawning is delegated to the respective location
 * regions via {@link FoliaHelper#runAtLocation}.
 */
public class GraveHighlightManager {

    private final SimpleGraves plugin;
    private final GraveManager manager;

    // -- config --
    private boolean enabled = true;
    private int range = 10;

    // -- runtime state --
    private final Map<Location, ArmorStand> activeStands = new ConcurrentHashMap<>();
    private final Map<UUID, List<Location>> graveCache = new ConcurrentHashMap<>();
    private long lastCacheRefresh = 0L;
    private static final long CACHE_TTL_MS = 300_000L; // 5 minutes

    // ------------------------------------------------------------ \\
    //  Construction & lifecycle
    // ------------------------------------------------------------ \\

    public GraveHighlightManager(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Reload config values and (re)start the highlight task.
     */
    public void reload(boolean enabled, int range) {
        this.enabled = enabled;
        this.range = Math.max(1, range);
    }

    /**
     * Start the repeating highlight task (every 2 seconds).
     */
    public void start() {
        FoliaHelper.runGlobalTimer(plugin, this::tick, 60L, 40L);
    }

    /**
     * Remove all active glow stands (called on plugin disable).
     */
    public void shutdown() {
        for (ArmorStand stand : activeStands.values()) {
            if (stand.isValid()) {
                stand.remove();
            }
        }
        activeStands.clear();
        graveCache.clear();
    }

    /**
     * Remove the glow stand at a specific location (called when a grave is broken).
     */
    public void removeGlowAt(Location loc) {
        ArmorStand stand = activeStands.remove(normalize(loc));
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    // ------------------------------------------------------------ \\
    //  Core tick
    // ------------------------------------------------------------ \\

    private void tick() {
        if (!enabled) return;

        refreshCacheIfStale();

        // Collect all grave locations that are currently in range of their owner
        Set<Location> inRange = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Location> graves = graveCache.get(player.getUniqueId());
            if (graves == null || graves.isEmpty()) continue;

            Location playerLoc = player.getLocation();

            for (Location graveLoc : graves) {
                if (!sameWorld(graveLoc, playerLoc)) continue;
                if (graveLoc.distanceSquared(playerLoc) > range * range) continue;

                Location key = normalize(graveLoc);
                inRange.add(key);

                // Spawn if not already present
                if (!activeStands.containsKey(key)) {
                    spawnGlowStand(graveLoc);
                }
            }
        }

        // Despawn stands no longer in range
        Iterator<Map.Entry<Location, ArmorStand>> it = activeStands.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, ArmorStand> entry = it.next();
            if (!inRange.contains(entry.getKey())) {
                ArmorStand stand = entry.getValue();
                if (stand.isValid()) {
                    stand.remove();
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ \\
    //  Glow stand management
    // ------------------------------------------------------------ \\

    private void spawnGlowStand(Location graveLoc) {
        // Must run on the grave location's region thread in Folia
        FoliaHelper.runAtLocation(plugin, graveLoc, () -> {
            // Double-check after we're on the right thread
            Location key = normalize(graveLoc);
            if (activeStands.containsKey(key)) return;

            // Place the armor stand at the center of the head block
            // graveLoc is already at block center (X.5, Y.5, Z.5)
            World world = graveLoc.getWorld();
            if (world == null) return;

            ArmorStand stand = world.spawn(graveLoc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setGlowing(true);
                as.setSmall(true);          // compact hitbox ≈ 1 block tall
                as.setGravity(false);
                as.setSilent(true);
                as.setInvulnerable(true);
                as.setPersistent(false);
                as.setCollidable(false);    // no push interaction
                as.setCanPickupItems(false);
            });

            activeStands.put(key, stand);
        });
    }

    // ------------------------------------------------------------ \\
    //  Cache
    // ------------------------------------------------------------ \\

    private void refreshCacheIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh < CACHE_TTL_MS) return;
        lastCacheRefresh = now;

        // Rebuild cache asynchronously
        manager.getEveryGraveLocation().thenAccept(locations -> {
            Map<UUID, List<Location>> newCache = new HashMap<>();
            for (Location loc : locations) {
                UUID owner = manager.getGraveOwnerUUID(loc);
                if (owner == null) continue;
                newCache.computeIfAbsent(owner, k -> new ArrayList<>()).add(loc);
            }
            graveCache.clear();
            graveCache.putAll(newCache);
        });
    }

    // ------------------------------------------------------------ \\
    //  Helpers
    // ------------------------------------------------------------ \\

    private static Location normalize(Location loc) {
        return new Location(loc.getWorld(),
                Math.floor(loc.getX()) + 0.5,
                Math.floor(loc.getY()) + 0.5,
                Math.floor(loc.getZ()) + 0.5);
    }

    private static boolean sameWorld(Location a, Location b) {
        World wa = a.getWorld();
        World wb = b.getWorld();
        return wa != null && wb != null && wa.equals(wb);
    }
}
