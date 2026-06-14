package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.BuildArea;
import net.blueva.arcade.modules.speed_builders.state.BuildPlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlotService {

    void loadPlots(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                   ArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }
        Integer totalPlotsObj = context.getDataAccess().getGameData("game.plots.total", Integer.class);
        int totalPlots = totalPlotsObj != null ? totalPlotsObj : 0;
        Integer totalAreasObj = context.getDataAccess().getGameData("game.build_areas.total", Integer.class);
        int totalAreas = totalAreasObj != null ? totalAreasObj : 0;

        World arenaWorld = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;

        for (int i = 1; i <= totalPlots; i++) {
            String basePath = "game.plots.list.p" + i;
            Location min = context.getDataAccess().getGameLocation(basePath + ".bounds.min");
            Location max = context.getDataAccess().getGameLocation(basePath + ".bounds.max");
            if (min == null || max == null) {
                continue;
            }
            if (min.getWorld() == null && arenaWorld != null) {
                min = new Location(arenaWorld, min.getX(), min.getY(), min.getZ());
            }
            if (max.getWorld() == null && arenaWorld != null) {
                max = new Location(arenaWorld, max.getX(), max.getY(), max.getZ());
            }

            Location spawn = context.getDataAccess().getGameLocation(basePath + ".spawn");
            if (spawn == null) {
                spawn = calculatePlotCenter(min, max);
            }
            BuildPlot plot = new BuildPlot(min, max, spawn);
            state.addPlot(plot);
        }

        for (int i = 1; i <= totalAreas; i++) {
            String basePath = "game.build_areas.list.a" + i;
            Location min = context.getDataAccess().getGameLocation(basePath + ".min");
            Location max = context.getDataAccess().getGameLocation(basePath + ".max");
            String floorName = context.getDataAccess().getGameData(basePath + ".floor", String.class);

            if (min == null || max == null) {
                state.addBuildArea(null);
                continue;
            }
            if (min.getWorld() == null && arenaWorld != null) {
                min = new Location(arenaWorld, min.getX(), min.getY(), min.getZ());
            }
            if (max.getWorld() == null && arenaWorld != null) {
                max = new Location(arenaWorld, max.getX(), max.getY(), max.getZ());
            }

            Material floorMaterial = Material.GRASS_BLOCK;
            if (floorName != null) {
                try {
                    floorMaterial = Material.valueOf(floorName.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }

            BuildArea area = new BuildArea(min, max, floorMaterial);
            state.addBuildArea(area);
        }


        Location scMin = context.getDataAccess().getGameLocation("game.showcase_plot.bounds.min");
        Location scMax = context.getDataAccess().getGameLocation("game.showcase_plot.bounds.max");
        if (scMin != null && scMax != null) {
            if (scMin.getWorld() == null && arenaWorld != null) {
                scMin = new Location(arenaWorld, scMin.getX(), scMin.getY(), scMin.getZ());
            }
            if (scMax.getWorld() == null && arenaWorld != null) {
                scMax = new Location(arenaWorld, scMax.getX(), scMax.getY(), scMax.getZ());
            }
            Location scSpawn = calculateBuildOrigin(scMin, scMax);
            BuildPlot showcase = new BuildPlot(scMin, scMax, scSpawn);
            state.setShowcasePlot(showcase);
        }
    }

    void assignPlayersToPlotsAndAreas(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      ArenaState state) {
        List<Player> players = new ArrayList<>(context.getPlayers());
        List<BuildPlot> plots = state.getPlots();
        List<BuildArea> areas = state.getBuildAreas();

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            BuildPlot plot = i < plots.size() ? plots.get(i) : (!plots.isEmpty() ? plots.get(i % plots.size()) : null);
            BuildArea area = i < areas.size() ? areas.get(i) : (!areas.isEmpty() ? areas.get(i % areas.size()) : null);

            if (plot != null) {
                state.setPlayerPlotSpawn(player.getUniqueId(), plot.getSpawn());
                state.setPlayerPlot(player.getUniqueId(), plot);
                state.setPlayerPlotYaw(player.getUniqueId(), plot.getSpawn().getYaw());
            }
            if (area != null) {
                state.setPlayerBuildArea(player.getUniqueId(), area);
            }
        }
    }

    void regenerateBuildAreaFloors(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   ArenaState state) {
        if (context.getBlocksAPI() == null) {
            return;
        }
        for (BuildArea area : state.getBuildAreas()) {
            if (area != null) {
                regenerateBuildAreaFloor(context, area);
            }
        }
    }

    void clearBuildAreas(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                         ArenaState state) {
        if (context.getBlocksAPI() == null) {
            return;
        }
        for (BuildArea area : state.getBuildAreas()) {
            if (area == null || area.getMin() == null || area.getMax() == null) {
                continue;
            }
            context.getBlocksAPI().setRegion(area.getMin(), area.getMax(), Material.AIR);
        }
    }

    void clearShowcasePlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ArenaState state) {
        if (context.getBlocksAPI() == null) {
            return;
        }
        BuildPlot showcase = state.getShowcasePlot();
        if (showcase == null || showcase.getMin() == null || showcase.getMax() == null) {
            return;
        }
        Location min = showcase.getMin().clone();
        Location max = showcase.getMax().clone();
        int floorY = Math.min(min.getBlockY(), max.getBlockY());
        int topY = Math.max(min.getBlockY(), max.getBlockY());
        if (floorY >= topY) {
            return;
        }
        if (min.getBlockY() == floorY) {
            min.setY(floorY + 1);
        } else {
            max.setY(floorY + 1);
        }
        context.getBlocksAPI().setRegion(min, max, Material.AIR);
    }

    void teleportToPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        ArenaState state,
                        Player player) {
        Location plot = state.getPlayerPlotSpawn(player.getUniqueId());
        if (plot == null || plot.getWorld() == null) {
            return;
        }
        context.getSchedulerAPI().runAtEntity(player, () -> player.teleport(centerLocation(plot)));
    }

    void regenerateBuildAreaFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  BuildArea area) {
        if (context.getBlocksAPI() == null || area == null || area.getFloorMaterial() == null || area.getMin() == null || area.getMax() == null) {
            return;
        }
        int minX = Math.min(area.getMin().getBlockX(), area.getMax().getBlockX());
        int minY = Math.min(area.getMin().getBlockY(), area.getMax().getBlockY());
        int minZ = Math.min(area.getMin().getBlockZ(), area.getMax().getBlockZ());
        int maxX = Math.max(area.getMin().getBlockX(), area.getMax().getBlockX());
        int maxZ = Math.max(area.getMin().getBlockZ(), area.getMax().getBlockZ());

        Location floorMin = new Location(area.getMin().getWorld(), minX, minY, minZ);
        Location floorMax = new Location(area.getMax().getWorld(), maxX, minY, maxZ);
        context.getBlocksAPI().setRegion(floorMin, floorMax, area.getFloorMaterial());
    }

    static Location centerLocation(Location loc) {
        double centeredX = Math.floor(loc.getX()) + 0.5;
        double centeredZ = Math.floor(loc.getZ()) + 0.5;
        return new Location(loc.getWorld(), centeredX, loc.getY(), centeredZ, loc.getYaw(), loc.getPitch());
    }

    private Location calculatePlotCenter(Location min, Location max) {
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;
        double centerY = Math.min(min.getY(), max.getY()) + 1.0;
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        return new Location(world, centerX, centerY, centerZ);
    }

    private Location calculateBuildOrigin(Location min, Location max) {
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        int originX = minX + ((maxX - minX + 1) / 2);
        int originZ = minZ + ((maxZ - minZ + 1) / 2);
        return new Location(world, originX, minY, originZ);
    }





    int[] getMaxBuildDimensions(ArenaState state) {
        int maxW = 0, maxH = 0, maxL = 0;
        List<BuildArea> areas = state.getBuildAreas();
        if (areas.isEmpty()) {

            for (BuildPlot plot : state.getPlots()) {
                if (plot.getMin() == null || plot.getMax() == null) continue;
                int w = Math.abs(plot.getMax().getBlockX() - plot.getMin().getBlockX()) + 1;
                int h = Math.abs(plot.getMax().getBlockY() - plot.getMin().getBlockY()) + 1;
                int l = Math.abs(plot.getMax().getBlockZ() - plot.getMin().getBlockZ()) + 1;
                maxW = Math.max(maxW, w);
                maxH = Math.max(maxH, h);
                maxL = Math.max(maxL, l);
            }
        } else {
            for (BuildArea area : areas) {
                if (area == null || area.getMin() == null || area.getMax() == null) continue;
                int w = Math.abs(area.getMax().getBlockX() - area.getMin().getBlockX()) + 1;
                int h = Math.abs(area.getMax().getBlockY() - area.getMin().getBlockY()) + 1;
                int l = Math.abs(area.getMax().getBlockZ() - area.getMin().getBlockZ()) + 1;
                maxW = Math.max(maxW, w);
                maxH = Math.max(maxH, h);
                maxL = Math.max(maxL, l);
            }
        }
        return new int[]{maxW, maxH, maxL};
    }
}
