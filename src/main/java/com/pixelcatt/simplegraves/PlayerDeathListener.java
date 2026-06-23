package com.pixelcatt.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.pixelcatt.simplegraves.GraveManager.PendingGraveData;


public class PlayerDeathListener implements Listener {

    private final SimpleGraves plugin;
    private final GraveManager manager;
    private final Map<UUID, PendingGraveData> pendingGraves = new ConcurrentHashMap<>();
    private volatile boolean pollingStarted = false;
    private static final long POLLING_INTERVAL = 10L; // ticks (0.5s)
    private static final long PENDING_TIMEOUT = 6000L; // ticks (5 min)


    public PlayerDeathListener(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    /**
     * Get the pending graves map, used by PlayerRespawnListener.
     */
    public Map<UUID, PendingGraveData> getPendingGraves() {
        return pendingGraves;
    }


    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof Player) {
            Player player = (Player) entity;

            manager.saveOfflinePlayer(player.getUniqueId(), player.getName());

            Location graveLocation = getValidGraveLocation(player.getLocation());
            if (graveLocation == null) {
                // No safe location at death point → save items for respawn placement
                PendingGraveData data = manager.savePlayerInventory(player);
                manager.clearPlayer(player);
                pendingGraves.put(player.getUniqueId(), data);
                ensurePollingStarted();

                // Cancel vanilla drops (keepInventory is no longer forced)
                event.getDrops().clear();
                event.setDroppedExp(0);

                plugin.getMessageManager().sendMessage(player, "death.lucky_day");
                plugin.getMessageManager().sendMessage(player, "death.no_safe_location");
                plugin.getMessageManager().sendMessage(player, "death.grave_at_respawn");
            } else {
                // Cancel vanilla drops (keepInventory is no longer forced)
                event.getDrops().clear();
                event.setDroppedExp(0);

                manager.createGrave(player, graveLocation);
            }
        }
    }

    /**
     * Start a repeating task that polls for respawned players.
     * <p>
     * This is the primary mechanism on Folia (where {@code PlayerRespawnEvent}
     * is broken), and also works on non-Folia servers.
     * <p>
     * The task runs on the global scheduler every 0.5 seconds and checks
     * each pending player.  When a player's health becomes > 0 they have
     * respawned, and the grave is placed.
     */
    private void ensurePollingStarted() {
        if (pollingStarted) return;
        pollingStarted = true;

        FoliaHelper.runGlobalTimer(plugin, () -> {
            long deadline = System.currentTimeMillis() + PENDING_TIMEOUT * 50L;

            for (UUID uuid : pendingGraves.keySet()) {
                PendingGraveData data = pendingGraves.get(uuid);
                if (data == null) continue;

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    // Player still offline — check timeout
                    continue;
                }

                // Must check health on the player's entity thread
                FoliaHelper.runOnEntity(plugin, player, () -> {
                    // Re-check after we're on the correct thread
                    PendingGraveData stillPending = pendingGraves.get(uuid);
                    if (stillPending == null) return;

                    // Health > 0 means the player has respawned
                    if (player.getHealth() > 0) {
                        pendingGraves.remove(uuid);
                        processRespawn(player, stillPending);
                    }
                });
            }
        }, 20L, POLLING_INTERVAL);
    }

    /**
     * Process a respawned player's pending grave.
     */
    private void processRespawn(Player player, PendingGraveData data) {
        Location respawnLoc = player.getLocation();

        // Must place the block on the respawn location's region thread
        FoliaHelper.runAtLocation(plugin, respawnLoc, () -> {
            Location safeLoc = getValidGraveLocation(respawnLoc);

            if (safeLoc != null) {
                manager.createGraveFromPendingData(player, safeLoc, data);
            } else {
                // Respawn point also unsafe → drop items on ground
                FoliaHelper.runOnEntity(plugin, player, () -> {
                    plugin.getMessageManager().sendMessage(player, "death.no_safe_location");
                    plugin.getMessageManager().sendMessage(player, "death.keep_items");
                });
                // Drop items at respawn location
                new PlayerRespawnListener(plugin, manager, pendingGraves)
                        .dropItemsOnGround(player, respawnLoc, data);
            }
        });
    }

    // ------------------------------------------------------------ \\
    //  Location Validation (public for reuse by PlayerRespawnListener)
    // ------------------------------------------------------------ \\

    public Location getValidGraveLocation(Location graveLocation) {
        int baseX = graveLocation.getBlockX();
        int baseY = graveLocation.getBlockY();
        int baseZ = graveLocation.getBlockZ();

        List<int[]> offsets = Arrays.asList(
                new int[]{0, 0},      // center
                new int[]{0, -1},     // north
                new int[]{-1, 0},     // west
                new int[]{1, 0},      // east
                new int[]{0, 1},      // south
                new int[]{-1, -1},    // northwest
                new int[]{1, -1},     // northeast
                new int[]{-1, 1},     // southwest
                new int[]{1, 1}       // southeast
        );

        World world = graveLocation.getWorld();

        if (world == null) {
            return null;
        }

        if (baseY > world.getMaxHeight()) {
            baseY = world.getMaxHeight() - 1;
        }

        if (baseY < world.getMinHeight()) {
            baseY = world.getMinHeight() + 1;
        }

        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];

            for (int y = baseY; y < world.getMaxHeight(); y++) {
                Location Loc = new Location(world, x, y, z);
                if (isSafeGraveLocation(Loc)) {
                    return Loc;
                }
            }

            for (int y = baseY; y > world.getMinHeight(); y--) {
                Location Loc = new Location(world, x, y, z);
                if (isSafeGraveLocation(Loc)) {
                    return Loc;
                }
            }
        }

        return null;
    }

    public boolean isSafeGraveLocation(Location loc) {
        Block block = loc.getBlock();
        Material type = block.getType();

        List<Material> UNSAFE_BLOCKS = new ArrayList<>(Arrays.asList(
                Material.WHITE_BED,
                Material.LIGHT_GRAY_BED,
                Material.GRAY_BED,
                Material.BLACK_BED,
                Material.BROWN_BED,
                Material.RED_BED,
                Material.ORANGE_BED,
                Material.YELLOW_BED,
                Material.LIME_BED,
                Material.GREEN_BED,
                Material.CYAN_BED,
                Material.LIGHT_BLUE_BED,
                Material.BLUE_BED,
                Material.PURPLE_BED,
                Material.MAGENTA_BED,
                Material.PINK_BED,
                Material.NOTE_BLOCK,
                Material.JUKEBOX,
                Material.ENCHANTING_TABLE,
                Material.GOLD_BLOCK,
                Material.IRON_BLOCK,
                Material.DIAMOND_BLOCK,
                Material.EMERALD_BLOCK,
                Material.LAPIS_BLOCK,
                Material.NETHERITE_BLOCK,
                Material.BARREL,
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.DECORATED_POT,
                Material.ENDER_CHEST,
                Material.SHULKER_BOX,
                Material.WHITE_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX,
                Material.GRAY_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX,
                Material.RED_SHULKER_BOX,
                Material.ORANGE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX,
                Material.LIME_SHULKER_BOX,
                Material.GREEN_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX,
                Material.PURPLE_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX,
                Material.PINK_SHULKER_BOX,
                Material.FURNACE,
                Material.BLAST_FURNACE,
                Material.SMOKER,
                Material.CAMPFIRE,
                Material.SOUL_CAMPFIRE,
                Material.BREWING_STAND,
                Material.PLAYER_HEAD,
                Material.PLAYER_WALL_HEAD,
                Material.ZOMBIE_HEAD,
                Material.ZOMBIE_WALL_HEAD,
                Material.CREEPER_HEAD,
                Material.CREEPER_WALL_HEAD,
                Material.SKELETON_SKULL,
                Material.SKELETON_WALL_SKULL,
                Material.WITHER_SKELETON_SKULL,
                Material.WITHER_SKELETON_WALL_SKULL,
                Material.PIGLIN_HEAD,
                Material.PIGLIN_WALL_HEAD,
                Material.DRAGON_HEAD,
                Material.DRAGON_WALL_HEAD,
                Material.HEAVY_CORE,
                Material.END_PORTAL,
                Material.END_PORTAL_FRAME,
                Material.END_GATEWAY,
                Material.DRAGON_EGG,
                Material.BEACON,
                Material.SPAWNER,
                Material.TRIAL_SPAWNER,
                Material.CREAKING_HEART,
                Material.NETHER_PORTAL,
                Material.OBSIDIAN,
                Material.BEDROCK,
                Material.COMMAND_BLOCK,
                Material.REPEATING_COMMAND_BLOCK,
                Material.CHAIN_COMMAND_BLOCK,
                Material.LIGHT,
                Material.STRUCTURE_BLOCK,
                Material.JIGSAW,
                Material.BARRIER,
                Material.STRUCTURE_VOID,
                Material.TEST_INSTANCE_BLOCK,
                Material.TEST_BLOCK
        ));

        if (UNSAFE_BLOCKS.contains(type) || manager.graveExistsLoc(block.getLocation())) return false;

        // The target block must be air, cave_air, void_air or a
        // non-solid / replaceable block (grass, water, snow, etc.).
        // Solid blocks (END_STONE, DIRT, STONE, etc.) would cause the
        // head to be embedded inside the terrain.
        if (type.isSolid()) {
            return false;
        }

        // Reject blocks that are NOT replaceable — placing a head here
        // would destroy them (rails, redstone, torches, buttons, etc.).
        if (!type.isAir() && !block.getBlockData().isReplaceable()) {
            return false;
        }

        // Check if the block below is solid (ground support for the head)
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType().isAir()) {
            return false;
        }

        boolean isNearEndPortalFrame = false;
        for (int dx = -3; dx <= 3 && !isNearEndPortalFrame; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Block checkBlock = loc.getWorld().getBlockAt(loc.getBlockX() + dx, loc.getBlockY(), loc.getBlockZ() + dz);
                if (checkBlock.getType() == Material.END_PORTAL_FRAME) {
                    isNearEndPortalFrame = true;
                    break;
                }
            }
        }

        boolean mayBeInEndFountainOrOnEndPlatform = false;
        if ("world_the_end".equals(loc.getWorld().getName())) {
            for (int dx = -3; dx <= 3 && !mayBeInEndFountainOrOnEndPlatform; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block checkBlock = loc.getWorld().getBlockAt(loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz);
                        if (checkBlock.getType() == Material.BEDROCK || checkBlock.getType() == Material.OBSIDIAN) {
                            mayBeInEndFountainOrOnEndPlatform = true;
                            break;
                        }
                    }
                }
            }
        }

        return !(isNearEndPortalFrame || mayBeInEndFountainOrOnEndPlatform);
    }
}
