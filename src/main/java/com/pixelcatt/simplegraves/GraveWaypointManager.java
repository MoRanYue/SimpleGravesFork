package com.pixelcatt.simplegraves;

import io.github.fajzu.Waypoint;
import io.github.fajzu.WaypointBuilder;
import io.github.fajzu.WaypointProvider;
import io.github.fajzu.WaypointStyle;
import io.papermc.paper.math.Position;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages grave waypoints on the Player Locator Bar using the
 * <a href="https://github.com/fajzu1/Waypoint-API">Waypoint-API</a> library.
 * <p>
 * A periodic task checks each grave owner's distance to their graves;
 * waypoints are only shown when the owner is within the configured range.
 */
public class GraveWaypointManager implements Runnable {

    private final SimpleGraves plugin;
    private final GraveManager manager;

    /** The Waypoint-API provider instance. */
    private WaypointProvider provider;

    /** Whether the feature is enabled. */
    private boolean enabled = true;

    /** Maximum distance (blocks) to show a grave waypoint. */
    private int range = 50;

    /** The configured waypoint style. */
    private WaypointStyle style = WaypointStyle.DEFAULT;

    /** The configured waypoint colour. */
    private Color color = Color.fromRGB(0xFFAA00);

    /** Grave location → waypoint instance. Keyed by normalised block-centre. */
    private final Map<Location, Waypoint> graveWaypoints = new ConcurrentHashMap<>();

    /** Grave location → owner UUID. */
    private final Map<Location, UUID> graveOwners = new ConcurrentHashMap<>();

    /**
     * Tracks which players currently have a waypoint shown.
     * Used to avoid spamming track/hide on every tick.
     */
    private final Set<String> activeWaypoints = new HashSet<>();


    public GraveWaypointManager(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    // ------------------------------------------------------------ \\
    //  Initialisation
    // ------------------------------------------------------------ \\

    public void init() {
        try {
            provider = WaypointProvider.init(plugin);
            plugin.getLogger().info("Grave Waypoint Manager initialised using Waypoint-API");
            // Start the periodic range-check task (every 20 ticks = 1 second)
            FoliaHelper.runGlobalTimer(plugin, this, 40L, 20L);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialise Waypoint-API. Is PacketEvents installed?");
            plugin.getLogger().severe("Disabling grave waypoint feature.");
            enabled = false;
        }
    }


    // ------------------------------------------------------------ \\
    //  Configuration
    // ------------------------------------------------------------ \\

    public boolean isEnabled() {
        return enabled && provider != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = Math.max(range, 0);
    }

    public void setStyle(Key key) {
        this.style = WaypointStyle.of(key.namespace(), key.value());
    }

    public void setColor(Color color) {
        this.color = color;
    }


    // ------------------------------------------------------------ \\
    //  Periodic range-check (Runnable)
    // ------------------------------------------------------------ \\

    @Override
    public void run() {
        if (!isEnabled()) return;

        for (Map.Entry<Location, Waypoint> entry : graveWaypoints.entrySet()) {
            Location graveLoc = entry.getKey();
            Waypoint waypoint = entry.getValue();
            UUID ownerUuid = graveOwners.get(graveLoc);
            if (ownerUuid == null) continue;

            Player owner = Bukkit.getPlayer(ownerUuid);
            String key = ownerUuid + "@" + graveLoc.hashCode();

            if (owner != null && owner.isOnline() && isInRange(owner, graveLoc)) {
                // Within range — show waypoint if not already shown
                if (!activeWaypoints.contains(key)) {
                    provider.track(owner, waypoint);
                    activeWaypoints.add(key);
                }
            } else if (activeWaypoints.contains(key)) {
                // Out of range or player offline — hide waypoint if currently shown
                // Only call hide() if the player is still online (null would crash PacketEvents)
                if (owner != null && owner.isOnline()) {
                    provider.hide(owner, waypoint);
                }
                activeWaypoints.remove(key);
            }
        }
    }

    /**
     * Check if the player is within range of the grave and in the same world.
     */
    private boolean isInRange(Player player, Location graveLoc) {
        Location playerLoc = player.getLocation();
        World graveWorld = graveLoc.getWorld();
        if (graveWorld == null || !graveWorld.equals(playerLoc.getWorld())) {
            return false;
        }
        if (range <= 0) return true; // no limit
        return playerLoc.distanceSquared(graveLoc) <= (double) range * range;
    }


    // ------------------------------------------------------------ \\
    //  Public API
    // ------------------------------------------------------------ \\

    /**
     * Register a grave waypoint (without immediately showing it).
     * The periodic task will show it when the owner is in range.
     */
    public void createWaypoint(Location graveLoc, UUID ownerUuid) {
        if (!isEnabled()) return;
        if (graveLoc == null || graveLoc.getWorld() == null) return;

        Location normalised = normalize(graveLoc);
        if (graveWaypoints.containsKey(normalised)) return;

        Waypoint waypoint = buildWaypoint(normalised);
        graveWaypoints.put(normalised, waypoint);
        graveOwners.put(normalised, ownerUuid);
    }

    /**
     * Remove the waypoint associated with the given grave location.
     */
    public void removeWaypoint(Location graveLoc) {
        if (graveLoc == null) return;

        Location normalised = normalize(graveLoc);
        Waypoint waypoint = graveWaypoints.remove(normalised);
        graveOwners.remove(normalised);

        if (waypoint == null) return;

        // Clean up active-waypoint tracking keys for this location
        activeWaypoints.removeIf(k -> k.endsWith("@" + normalised.hashCode()));

        // Hide from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            provider.hide(player, waypoint);
        }
    }

    /**
     * Load all graves from the database and register their waypoints.
     */
    public void reloadAll() {
        if (!isEnabled()) return;

        graveWaypoints.clear();
        graveOwners.clear();
        activeWaypoints.clear();

        manager.getEveryGraveLocationWithOwner().thenAccept(graveData -> {
            for (GraveManager.GraveLocationWithOwner data : graveData) {
                Location normalised = normalize(data.location());
                if (graveWaypoints.containsKey(normalised)) continue;

                Waypoint waypoint = buildWaypoint(normalised);
                graveWaypoints.put(normalised, waypoint);
                graveOwners.put(normalised, data.ownerUuid());
            }
        });
    }

    /**
     * Hide all waypoints and clear all tracking data.
     */
    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Waypoint waypoint : graveWaypoints.values()) {
                provider.hide(player, waypoint);
            }
        }
        graveWaypoints.clear();
        graveOwners.clear();
        activeWaypoints.clear();
    }


    // ------------------------------------------------------------ \\
    //  Helpers
    // ------------------------------------------------------------ \\

    private Waypoint buildWaypoint(Location normalised) {
        Position position = Position.block(
                normalised.getBlockX(),
                normalised.getBlockY(),
                normalised.getBlockZ()
        );
        return WaypointBuilder.builder()
                .position(position)
                .color(color)
                .style(style)
                .build();
    }

    private static Location normalize(Location loc) {
        return new Location(loc.getWorld(),
                Math.floor(loc.getX()) + 0.5,
                Math.floor(loc.getY()) + 0.5,
                Math.floor(loc.getZ()) + 0.5);
    }
}
