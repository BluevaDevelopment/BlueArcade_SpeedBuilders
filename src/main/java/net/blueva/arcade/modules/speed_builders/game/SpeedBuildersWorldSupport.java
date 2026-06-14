package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.BuildArea;
import net.blueva.arcade.modules.speed_builders.state.BuildPlot;
import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

class SpeedBuildersWorldSupport {

    private final Logger logger;
    private final Plugin metadataPlugin;

    SpeedBuildersWorldSupport(Logger logger, Plugin metadataPlugin) {
        this.logger = logger;
        this.metadataPlugin = metadataPlugin;
    }

    private void teleportToSafeBuildView(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state,
                                         Player player) {
        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area == null || area.getMin() == null || area.getMax() == null) {
            return;
        }
        Location safe = findSafeViewLocation(area.getMin(), area.getMax());
        context.getSchedulerAPI().runAtEntity(player, () -> {
            enableFlight(player, true);
            player.teleport(safe);
        });
    }

    void moveOutOfBuildAreaIfInside(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            ArenaState state,
                                            Player player) {
        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area == null || !isInsideRegion(player.getLocation(), area.getMin(), area.getMax())) {
            return;
        }
        teleportToSafeBuildView(context, state, player);
    }

    void moveOutOfRegionIfInside(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         Player player,
                                         Location min,
                                         Location max) {
        if (!isInsideRegion(player.getLocation(), min, max)) {
            return;
        }
        Location target = findSafeViewLocation(min, max);
        context.getSchedulerAPI().runAtEntity(player, () -> {
            enableFlight(player, true);
            player.teleport(target);
        });
    }

    private boolean isInsideRegion(Location location, Location min, Location max) {
        if (location == null || min == null || max == null) {
            return false;
        }
        if (location.getWorld() != null && min.getWorld() != null && !location.getWorld().equals(min.getWorld())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= Math.min(min.getBlockX(), max.getBlockX()) && x <= Math.max(min.getBlockX(), max.getBlockX())
                && y >= Math.min(min.getBlockY(), max.getBlockY()) && y <= Math.max(min.getBlockY(), max.getBlockY())
                && z >= Math.min(min.getBlockZ(), max.getBlockZ()) && z <= Math.max(min.getBlockZ(), max.getBlockZ());
    }

    private Location findSafeViewLocation(Location min, Location max) {
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        if (world == null) {
            return min;
        }

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        double centerX = minX + ((maxX - minX + 1) / 2.0);
        double centerZ = minZ + ((maxZ - minZ + 1) / 2.0);
        Location lookAt = new Location(world, centerX, minY + 1.0, centerZ);

        int topY = Math.min(world.getMaxHeight() - 2, maxY + 3);
        for (int y = topY; y < world.getMaxHeight() - 1; y++) {
            Location above = new Location(world, centerX, y, centerZ);
            if (isSafeForPlayer(above)) {
                return faceLocation(above, lookAt);
            }
        }

        List<Location> sideCandidates = List.of(
                new Location(world, centerX, topY, minZ - 2.0),
                new Location(world, centerX, topY, maxZ + 2.0),
                new Location(world, minX - 2.0, topY, centerZ),
                new Location(world, maxX + 2.0, topY, centerZ)
        );
        for (Location candidate : sideCandidates) {
            Location safe = firstSafeAtOrAbove(candidate);
            if (safe != null) {
                return faceLocation(safe, lookAt);
            }
        }

        return faceLocation(new Location(world, centerX, topY, centerZ), lookAt);
    }

    private Location firstSafeAtOrAbove(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        int startY = Math.max(world.getMinHeight() + 1, location.getBlockY());
        int maxY = world.getMaxHeight() - 2;
        for (int y = startY; y <= maxY; y++) {
            Location candidate = new Location(world, location.getX(), y, location.getZ(), location.getYaw(), location.getPitch());
            if (isSafeForPlayer(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafeForPlayer(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
        return feet.getType().isAir() && head.getType().isAir();
    }

    private Location faceLocation(Location location, Location target) {
        if (location == null || target == null) {
            return location;
        }
        double dx = target.getX() - location.getX();
        double dy = target.getY() - location.getY();
        double dz = target.getZ() - location.getZ();
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distanceXZ));
        location.setYaw(yaw);
        location.setPitch(pitch);
        return location;
    }

    Location getBuildOrigin(ArenaState state, Player player) {
        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area != null && area.getMin() != null && area.getMax() != null) {
            World world = area.getMin().getWorld() != null ? area.getMin().getWorld() : area.getMax().getWorld();
            if (world == null) {
                return null;
            }
            int minX = Math.min(area.getMin().getBlockX(), area.getMax().getBlockX());
            int maxX = Math.max(area.getMin().getBlockX(), area.getMax().getBlockX());
            int minY = Math.min(area.getMin().getBlockY(), area.getMax().getBlockY());
            int minZ = Math.min(area.getMin().getBlockZ(), area.getMax().getBlockZ());
            int maxZ = Math.max(area.getMin().getBlockZ(), area.getMax().getBlockZ());
            int originX = minX + ((maxX - minX + 1) / 2);
            int originZ = minZ + ((maxZ - minZ + 1) / 2);
            return new Location(world, originX, minY, originZ);
        }

        BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
        if (plot == null || plot.getSpawn() == null) return null;
        return plot.getSpawn();
    }

    int getPlayerRotationSteps(ArenaState state, Player player) {
        float yaw = state.getPlayerPlotYaw(player.getUniqueId());
        yaw = (yaw % 360.0f + 360.0f) % 360.0f;
        return Math.round(yaw / 90.0f) % 4;
    }

    int pasteStructure(Location origin, StructureTemplate structure, int rotationSteps) {
        if (origin == null || structure == null || origin.getWorld() == null) return 0;
        World world = origin.getWorld();
        int cx = 0;
        int cy = 0;
        int cz = 0;
        int placed = 0;

        for (StructureTemplate.Voxel voxel : structure.getVoxels()) {
            int[] rot = BuildEvaluator.rotateBlockCoords(voxel.dx - cx, voxel.dz - cz, rotationSteps);
            int x = origin.getBlockX() + rot[0];
            int y = origin.getBlockY() + (voxel.dy - cy);
            int z = origin.getBlockZ() + rot[1];
            Block block = world.getBlockAt(x, y, z);
            String rotatedMat = BuildEvaluator.rotateBlockData(voxel.mat, rotationSteps);
            try {
                BlockData data = Bukkit.createBlockData(rotatedMat);
                block.setBlockData(data, false);
                placed++;
            } catch (IllegalArgumentException e) {
                logger.warning("[SpeedBuilders] Invalid block data: " + rotatedMat);
            }
        }

        if (structure.getCreatures() != null) {
            for (StructureTemplate.Creature c : structure.getCreatures()) {
                int[] rot = BuildEvaluator.rotateBlockCoords((int) Math.floor(c.dx) - cx, (int) Math.floor(c.dz) - cz, rotationSteps);
                double ex = origin.getBlockX() + rot[0] + 0.5;
                double ey = origin.getBlockY() + (c.dy - cy);
                double ez = origin.getBlockZ() + rot[1] + 0.5;
                try {
                    EntityType type = EntityType.valueOf(c.mob);
                    if (type.isAlive()) {
                        Entity spawned = world.spawnEntity(new Location(world, ex, ey, ez), type);
                        if (spawned instanceof LivingEntity living) {
                            living.setSilent(true);
                            living.setAI(false);
                            living.setRemoveWhenFarAway(false);
                            living.setCollidable(false);
                            living.setFireTicks(0);
                            living.setMetadata("SPEEDBUILDERS_MOB", new FixedMetadataValue(metadataPlugin, true));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return placed;
    }

    private void clearRegionDirect(Location min, Location max) {
        if (min == null || max == null) {
            return;
        }
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        if (world == null) {
            return;
        }
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    void clearRegionAboveFloorDirect(Location min, Location max) {
        if (min == null || max == null) {
            return;
        }
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        if (world == null) {
            return;
        }
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY()) + 1;
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    void resetBuildAreaDirect(BuildArea area) {
        if (area == null || area.getMin() == null || area.getMax() == null) {
            return;
        }
        clearRegionAboveFloorDirect(area.getMin(), area.getMax());
        if (area.getFloorMaterial() == null) {
            return;
        }

        World world = area.getMin().getWorld() != null ? area.getMin().getWorld() : area.getMax().getWorld();
        if (world == null) {
            return;
        }
        int minX = Math.min(area.getMin().getBlockX(), area.getMax().getBlockX());
        int maxX = Math.max(area.getMin().getBlockX(), area.getMax().getBlockX());
        int minY = Math.min(area.getMin().getBlockY(), area.getMax().getBlockY());
        int minZ = Math.min(area.getMin().getBlockZ(), area.getMax().getBlockZ());
        int maxZ = Math.max(area.getMin().getBlockZ(), area.getMax().getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, minY, z).setType(area.getFloorMaterial(), false);
            }
        }
    }

    String formatLocation(Location location) {
        if (location == null) {
            return "null";
        }
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "null-world";
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    void removeStructureMobs(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state) {
        World world = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)
                    && entity.hasMetadata("SPEEDBUILDERS_MOB")) {
                entity.remove();
            }
        }
    }

    private void enableFlight(Player player, boolean startFlying) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setAllowFlight(true);
        if (startFlying) {
            player.setFlying(true);
        }
        player.setFallDistance(0.0f);
    }
}
