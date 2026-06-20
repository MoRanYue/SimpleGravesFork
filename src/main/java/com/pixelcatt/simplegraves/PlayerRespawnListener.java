package com.pixelcatt.simplegraves;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import com.pixelcatt.simplegraves.GraveManager.PendingGraveData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;


public class PlayerRespawnListener implements Listener {

    private final SimpleGraves plugin;
    private final GraveManager manager;
    private final Map<UUID, PendingGraveData> pendingGraves;


    public PlayerRespawnListener(SimpleGraves plugin, GraveManager manager,
                                  Map<UUID, PendingGraveData> pendingGraves) {
        this.plugin = plugin;
        this.manager = manager;
        this.pendingGraves = pendingGraves;
    }


    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if this player has a pending grave (no safe location at death)
        PendingGraveData data = pendingGraves.remove(uuid);
        if (data == null) {
            return;
        }

        // Try to place the grave at the respawn location
        Location respawnLoc = event.getRespawnLocation();

        // In Folia, the respawn location might be in a different region than the
        // player's current thread. Schedule the grave placement on the respawn
        // location's region to be safe.
        FoliaHelper.runAtLocation(plugin, respawnLoc, () -> {
            Location safeLoc = findSafeGraveLocation(respawnLoc);

            if (safeLoc != null) {
                manager.createGraveFromPendingData(player, safeLoc, data);
            } else {
                // Respawn point is also unsafe → drop items on the ground as fallback
                // Use runOnEntity because sendMessage must run on the player's entity thread
                FoliaHelper.runOnEntity(plugin, player, () -> {
                    plugin.getMessageManager().sendMessage(player, "death.no_safe_location");
                    plugin.getMessageManager().sendMessage(player, "death.keep_items");
                });
                dropItemsOnGround(player, respawnLoc, data);
            }
        });
    }


    // ------------------------------------------------------------ \\
    //  Fallback: Drop items from pending data on the ground
    // ------------------------------------------------------------ \\

    public void dropItemsOnGround(Player player, Location location, PendingGraveData data) {
        World world = location.getWorld();
        if (world == null) return;

        String serializedItems = data.getSerializedItems();
        if (serializedItems != null && !serializedItems.isEmpty()) {
            String[] base64Items = serializedItems.split("\\|");
            for (String base64 : base64Items) {
                if (base64.isEmpty()) continue;

                try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
                     BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {

                    ItemStack item = (ItemStack) ois.readObject();
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        world.dropItemNaturally(location, item);
                    }

                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        // Drop XP orbs based on saved XP
        double xpAmount = data.getXp();
        if (xpAmount > 0) {
            int xpOrbCount = (int) Math.min(Math.floor(xpAmount), 25);
            int xpPerOrb = xpOrbCount > 0 ? (int) Math.floor(xpAmount / xpOrbCount) : 0;
            for (int i = 0; i < xpOrbCount; i++) {
                world.spawn(location, org.bukkit.entity.ExperienceOrb.class).setExperience(xpPerOrb);
            }
        }
    }


    // ------------------------------------------------------------ \\
    //  Location Validation (mirrors PlayerDeathListener logic)
    // ------------------------------------------------------------ \\

    private Location findSafeGraveLocation(Location respawnLoc) {
        int baseX = respawnLoc.getBlockX();
        int baseY = respawnLoc.getBlockY();
        int baseZ = respawnLoc.getBlockZ();

        int[][] offsets = {
                {0, 0},      // center
                {0, -1},     // north
                {-1, 0},     // west
                {1, 0},      // east
                {0, 1},      // south
                {-1, -1},    // northwest
                {1, -1},     // northeast
                {-1, 1},     // southwest
                {1, 1}       // southeast
        };

        World world = respawnLoc.getWorld();
        if (world == null) return null;

        if (baseY > world.getMaxHeight()) baseY = world.getMaxHeight() - 1;
        if (baseY < world.getMinHeight()) baseY = world.getMinHeight() + 1;

        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];

            for (int y = baseY; y < world.getMaxHeight(); y++) {
                Location loc = new Location(world, x, y, z);
                if (isSafe(loc)) return loc;
            }

            for (int y = baseY; y > world.getMinHeight(); y--) {
                Location loc = new Location(world, x, y, z);
                if (isSafe(loc)) return loc;
            }
        }

        return null;
    }

    private boolean isSafe(Location loc) {
        org.bukkit.block.Block block = loc.getBlock();
        org.bukkit.Material type = block.getType();

        // Must not be a solid block that isn't a player head
        if (type.isSolid() && type != org.bukkit.Material.PLAYER_HEAD && type != org.bukkit.Material.PLAYER_WALL_HEAD) {
            return false;
        }

        // Check if a grave already exists at this location
        if (manager.graveExistsLoc(block.getLocation())) {
            return false;
        }

        // Check if the block below is solid (ground support for the head)
        org.bukkit.block.Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType().isAir()) {
            return false;
        }

        // Must be air or replaceable (non-solid) block
        return type == org.bukkit.Material.AIR
                || type == org.bukkit.Material.CAVE_AIR
                || type == org.bukkit.Material.VOID_AIR
                || type == org.bukkit.Material.WATER
                || type == org.bukkit.Material.LAVA
                || type == org.bukkit.Material.SNOW
                || !type.isSolid();
    }
}
