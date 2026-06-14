package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.BuildArea;
import net.blueva.arcade.modules.speed_builders.state.BuildPlot;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class GuardianService {

    private final ModuleConfigAPI moduleConfig;
    private final Plugin metadataPlugin;

    GuardianService(ModuleConfigAPI moduleConfig, Plugin metadataPlugin) {
        this.moduleConfig = moduleConfig;
        this.metadataPlugin = metadataPlugin;
    }

    void ensureGuardianAtShowcase(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                          ArenaState state) {
        Guardian existing = getGuardian(state);
        if (existing != null && existing.isValid()) {
            return;
        }

        BuildPlot showcase = state.getShowcasePlot();
        Location loc = getGuardianHomeLocation(state);
        if (showcase == null || loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;
        try {
            Entity guardian = world.spawnEntity(loc, EntityType.ELDER_GUARDIAN);
            if (guardian instanceof Guardian guardianEntity) {
                guardianEntity.setLaser(true);
                guardianEntity.setSilent(false);
                guardianEntity.setAI(false);
                guardianEntity.setAware(false);
                guardianEntity.setGravity(false);
                guardianEntity.setGlowing(true);
                guardianEntity.setInvulnerable(true);
                guardianEntity.setRemoveWhenFarAway(false);
                guardianEntity.setCollidable(false);
                guardianEntity.setFireTicks(0);
                guardianEntity.setMetadata("SPEEDBUILDERS_GUARDIAN", new FixedMetadataValue(metadataPlugin, true));
                state.setGuardianUuid(guardianEntity.getUniqueId());
            }
        } catch (IllegalArgumentException ignored) {

        }
    }

    void startGuardianWatchTask(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        ArenaState state) {
        String taskId = "arena_" + context.getArenaId() + "_speed_builders_guardian_watch";
        context.getSchedulerAPI().cancelTask(taskId);
        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }
            Guardian guardian = getGuardian(state);
            if (guardian == null || !guardian.isValid()) {
                ensureGuardianAtShowcase(context, state);
                guardian = getGuardian(state);
            }
            if (guardian == null || !guardian.isValid()) {
                return;
            }

            Guardian activeGuardian = guardian;
            context.getSchedulerAPI().runAtEntity(activeGuardian, () -> updateGuardianWatchLocation(context, state, activeGuardian));
        }, 1L, 1L);
    }

    private void updateGuardianWatchLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                             ArenaState state,
                                             Guardian guardian) {
        Location home = getGuardianHomeLocation(state);
        if (home == null || guardian == null || !guardian.isValid()) {
            return;
        }

        double angle = state.getGuardianOrbitAngle();
        if (state.getPhase() == net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) {
            angle += Math.toRadians(Math.max(0.1, moduleConfig.getInt("guardian.orbit_degrees_per_tick", 3)));
            state.setGuardianOrbitAngle(angle);
        }

        double radius = state.getPhase() == net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING
                ? Math.max(0.0, moduleConfig.getInt("guardian.orbit_radius", 4))
                : 0.0;
        Entity beamTarget = state.getGuardianBeamTargetUuid() != null ? Bukkit.getEntity(state.getGuardianBeamTargetUuid()) : null;
        Location target = beamTarget != null ? beamTarget.getLocation() : findGuardianLookTarget(context, state);
        if (beamTarget != null) {
            radius = 0.0;
        }
        Location next = home.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        if (target != null) {
            faceLocation(next, target);
        }

        guardian.teleport(next);
        guardian.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        guardian.setFallDistance(0.0f);
        guardian.setFireTicks(0);
    }

    private Location findGuardianLookTarget(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            ArenaState state) {
        List<Player> alive = context.getAlivePlayers();
        if (!alive.isEmpty()) {
            int index = (int) ((System.currentTimeMillis() / 1500L) % alive.size());
            Player player = alive.get(index);
            BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
            if (area != null) {
                return getRegionCenter(area.getMin(), area.getMax(), 2.0);
            }
            BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
            if (plot != null) {
                return getRegionCenter(plot.getMin(), plot.getMax(), 2.0);
            }
        }
        BuildPlot showcase = state.getShowcasePlot();
        return showcase != null ? getRegionCenter(showcase.getMin(), showcase.getMax(), 2.0) : null;
    }

    void showGuardianJudgingAnimation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Particle guardianAppearance = particleConstant("MOB_APPEARANCE", "ELDER_GUARDIAN");
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            if (!showNativeElderGuardianAnimation(player) && guardianAppearance != null) {
                player.spawnParticle(guardianAppearance, player.getLocation(), 1);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
        }
    }

    private boolean showNativeElderGuardianAnimation(Player player) {
        try {
            player.getClass().getMethod("showElderGuardian", boolean.class).invoke(player, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            player.getClass().getMethod("showElderGuardian").invoke(player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    void aimGuardianAtPlayerPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state,
                                         Player player) {
        Guardian guardian = getGuardian(state);
        BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
        if (guardian == null || plot == null) {
            return;
        }
        Location targetLocation = getRegionCenter(plot.getMin(), plot.getMax(), 2.0);
        if (targetLocation == null || targetLocation.getWorld() == null) {
            return;
        }
        context.getSchedulerAPI().runAtEntity(guardian, () -> {
            removeGuardianBeamTarget(context, state);
            Location facing = guardian.getLocation();
            faceLocation(facing, targetLocation);
            guardian.teleport(facing);
            guardian.playEffect(EntityEffect.GUARDIAN_TARGET);
            guardian.setLaser(true);
            ArmorStand target = spawnGuardianBeamTarget(targetLocation);
            if (target != null) {
                state.setGuardianBeamTargetUuid(target.getUniqueId());
                guardian.setTarget(target);
                guardian.attack(target);
            }
            startGuardianBeamParticles(context, state, targetLocation);
        });
    }

    void explodePlayerPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   ArenaState state,
                                   Player player) {
        BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
        if (plot == null || plot.getMin() == null || plot.getMax() == null) {
            return;
        }
        Location min = plot.getMin();
        Location max = plot.getMax();
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
        int maxShards = Math.max(1, moduleConfig.getInt("guardian.explosion_max_shards", 220));
        double horizontalPower = Math.max(0.0, moduleConfig.getInt("guardian.explosion_horizontal_power", 8)) / 10.0;
        double upwardPower = Math.max(0.0, moduleConfig.getInt("guardian.explosion_upward_power", 7)) / 10.0;
        List<org.bukkit.entity.FallingBlock> shards = new ArrayList<>();

        List<Block> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        blocks.add(block);
                    }
                }
            }
        }
        Collections.shuffle(blocks);
        int spawned = 0;
        Location center = getRegionCenter(min, max, 1.5);
        for (Block block : blocks) {
            if (spawned++ >= maxShards) {
                block.setType(Material.AIR, false);
                continue;
            }
            BlockData data = block.getBlockData();
            Location origin = block.getLocation().add(0.5, 0.1, 0.5);
            block.setType(Material.AIR, false);
            org.bukkit.entity.FallingBlock fallingBlock = world.spawnFallingBlock(origin, data);
            fallingBlock.setDropItem(false);
            fallingBlock.setHurtEntities(false);
            fallingBlock.setMetadata("SPEEDBUILDERS_EXPLOSION_SHARD", new FixedMetadataValue(metadataPlugin, true));
            org.bukkit.util.Vector direction = origin.toVector().subtract(center != null ? center.toVector() : origin.toVector());
            if (direction.lengthSquared() < 0.01) {
                direction = new org.bukkit.util.Vector(
                        ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                        0.0,
                        ThreadLocalRandom.current().nextDouble(-1.0, 1.0));
            }
            direction.setY(0.0);
            if (direction.lengthSquared() > 0.0) {
                direction.normalize();
            }
            direction.multiply(ThreadLocalRandom.current().nextDouble(horizontalPower * 0.35, horizontalPower));
            direction.setY(ThreadLocalRandom.current().nextDouble(upwardPower * 0.4, upwardPower));
            fallingBlock.setVelocity(direction);
            shards.add(fallingBlock);
        }
        Location explosionCenter = getRegionCenter(min, max, 1.0);
        if (explosionCenter != null) {
            playPlotExplosionSound(context, explosionCenter);
            Particle explosionParticle = particleConstant("EXPLOSION_LARGE", "EXPLOSION");
            if (explosionParticle != null) {
                world.spawnParticle(explosionParticle, explosionCenter, 8, 1.5, 1.0, 1.5, 0.0);
            }
        }

        int lifetime = Math.max(10, moduleConfig.getInt("guardian.explosion_shard_lifetime_ticks", 50));
        context.getSchedulerAPI().runLater(
                "arena_" + context.getArenaId() + "_speed_builders_plot_explosion_" + player.getUniqueId(),
                () -> shards.forEach(entity -> {
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                }),
                lifetime);
    }

    private void playPlotExplosionSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        Location explosionCenter) {
        if (explosionCenter == null || explosionCenter.getWorld() == null) {
            return;
        }
        explosionCenter.getWorld().playSound(explosionCenter, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.85f);
        for (Player viewer : context.getPlayers()) {
            if (viewer.isOnline()) {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.85f);
            }
        }
    }

    private ArmorStand spawnGuardianBeamTarget(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Entity entity = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        if (!(entity instanceof ArmorStand armorStand)) {
            entity.remove();
            return null;
        }
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        armorStand.setCollidable(false);
        armorStand.setMetadata("SPEEDBUILDERS_GUARDIAN_TARGET", new FixedMetadataValue(metadataPlugin, true));
        return armorStand;
    }

    private void startGuardianBeamParticles(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            ArenaState state,
                                            Location targetLocation) {
        String taskId = "arena_" + context.getArenaId() + "_speed_builders_guardian_beam";
        context.getSchedulerAPI().cancelTask(taskId);
        final int[] ticks = {0};
        int duration = Math.max(10, moduleConfig.getInt("guardian.beam_ticks", 50));
        context.getSchedulerAPI().runTimer(taskId, () -> {
            Guardian guardian = getGuardian(state);
            if (guardian == null || !guardian.isValid() || targetLocation == null || targetLocation.getWorld() == null || ticks[0]++ >= duration) {
                context.getSchedulerAPI().cancelTask(taskId);
                removeGuardianBeamTarget(context, state);
                if (guardian != null && guardian.isValid()) {
                    guardian.setTarget(null);
                }
                return;
            }
            drawParticleBeam(guardian.getEyeLocation(), targetLocation);
        }, 1L, 1L);
    }

    private void drawParticleBeam(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        double distance = from.distance(to);
        if (distance <= 0.0) {
            return;
        }
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector()).normalize();
        World world = from.getWorld();
        Particle beamParticle = particleConstant("END_ROD");
        if (beamParticle == null) {
            return;
        }
        for (double d = 0.0; d <= distance; d += 0.75) {
            Location point = from.clone().add(direction.clone().multiply(d));
            world.spawnParticle(beamParticle, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private Particle particleConstant(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private Guardian getGuardian(ArenaState state) {
        if (state == null || state.getGuardianUuid() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(state.getGuardianUuid());
        return entity instanceof Guardian guardian ? guardian : null;
    }

    private Location getGuardianHomeLocation(ArenaState state) {
        BuildPlot showcase = state.getShowcasePlot();
        if (showcase == null) {
            return null;
        }
        Location center = getRegionCenter(showcase.getMin(), showcase.getMax(), 0.0);
        if (center == null || center.getWorld() == null) {
            return null;
        }
        int maxY = Math.max(showcase.getMin().getBlockY(), showcase.getMax().getBlockY());
        double height = Math.max(1, moduleConfig.getInt("guardian.height_above_showcase", 5));
        center.setY(Math.min(center.getWorld().getMaxHeight() - 2.0, maxY + height));
        return center;
    }

    private Location getRegionCenter(Location min, Location max, double yOffsetFromFloor) {
        if (min == null || max == null) {
            return null;
        }
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        if (world == null) {
            return null;
        }
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        double centerX = minX + ((maxX - minX + 1) / 2.0);
        double centerZ = minZ + ((maxZ - minZ + 1) / 2.0);
        return new Location(world, centerX, minY + yOffsetFromFloor, centerZ);
    }

    void removeGuardian(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ArenaState state) {
        context.getSchedulerAPI().cancelTask("arena_" + context.getArenaId() + "_speed_builders_guardian_watch");
        context.getSchedulerAPI().cancelTask("arena_" + context.getArenaId() + "_speed_builders_guardian_beam");
        removeGuardianBeamTarget(context, state);
        World world = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)
                    && entity.hasMetadata("SPEEDBUILDERS_GUARDIAN")) {
                entity.remove();
            }
        }
        state.setGuardianUuid(null);
    }

    void removeGuardianBeamTarget(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                          ArenaState state) {
        Entity stored = state.getGuardianBeamTargetUuid() != null ? Bukkit.getEntity(state.getGuardianBeamTargetUuid()) : null;
        if (stored != null) {
            stored.remove();
        }
        state.setGuardianBeamTargetUuid(null);
        World world = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity.hasMetadata("SPEEDBUILDERS_GUARDIAN_TARGET")) {
                entity.remove();
            }
        }
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
}
