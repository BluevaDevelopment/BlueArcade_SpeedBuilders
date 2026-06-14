package net.blueva.arcade.modules.speed_builders.setup;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.SetupContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class SpeedBuildersPlotSetup {

    private static final int RECOMMENDED_BUILD_AREA_WIDTH = 7;
    private static final int RECOMMENDED_BUILD_AREA_HEIGHT = 10;
    private static final int RECOMMENDED_BUILD_AREA_LENGTH = 7;

    private final ModuleConfigAPI moduleConfig;

    SpeedBuildersPlotSetup(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    boolean handlePlot(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(0);
        if ("add".equalsIgnoreCase(action)) {
            return handlePlotAdd(context);
        }
        if ("set".equalsIgnoreCase(action)) {
            return handlePlotSet(context);
        }
        if ("remove".equalsIgnoreCase(action)) {
            return handlePlotRemove(context);
        }
        if ("spawn".equalsIgnoreCase(action)) {
            return handlePlotSpawn(context);
        }
        if ("build_area".equalsIgnoreCase(action)) {
            return handlePlotBuildArea(context);
        }
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.usage"));
        return true;
    }

    boolean handleBuildArea(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(0);
        if ("add".equalsIgnoreCase(action)) {
            return handleBuildAreaAdd(context);
        }
        if ("set".equalsIgnoreCase(action)) {
            return handleBuildAreaSet(context);
        }
        if ("remove".equalsIgnoreCase(action)) {
            return handleBuildAreaRemove(context);
        }
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("build_area.usage"));
        return true;
    }


    private boolean handlePlotAdd(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("plot.add.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int height = maxY - minY + 1;

        int totalPlots = context.getData().getInt("game.plots.total", 0);
        int newPlotNumber = totalPlots + 1;
        String basePath = "game.plots.list.p" + newPlotNumber;

        context.getData().setRegionBounds(basePath + ".bounds", pos1, pos2);
        context.getData().setLocation(basePath + ".spawn", player.getLocation());
        context.getData().setInt("game.plots.total", newPlotNumber);
        context.getData().save();

        int x = Math.abs(maxX - minX) + 1;
        int y = height;
        int z = Math.abs(maxZ - minZ) + 1;
        int blocks = x * y * z;

        String msg = getSetupMessage("plot.add.set")
                .replace("{index}", String.valueOf(newPlotNumber))
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z));
        context.getMessagesAPI().sendRaw(player, msg);

        String spawnMsg = getSetupMessage("plot.add.spawn_set");
        if (spawnMsg != null && !spawnMsg.isEmpty()) {
            context.getMessagesAPI().sendRaw(player, spawnMsg.replace("{index}", String.valueOf(newPlotNumber)));
        }
        return true;
    }

    private boolean handlePlotSet(SetupContext<Player, CommandSender, Location> context) {
        String idArg = context.getHandlerArg(1);
        if (idArg == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.set.usage"));
            return true;
        }

        int plotId = parsePlotId(context, idArg);
        if (plotId == -1) {
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("plot.add.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int height = maxY - minY + 1;

        String basePath = "game.plots.list.p" + plotId;
        context.getData().setRegionBounds(basePath + ".bounds", pos1, pos2);
        context.getData().setLocation(basePath + ".spawn", player.getLocation());
        context.getData().save();

        int x = Math.abs(maxX - minX) + 1;
        int y = height;
        int z = Math.abs(maxZ - minZ) + 1;
        int blocks = x * y * z;

        String msg = getSetupMessage("plot.set.success")
                .replace("{index}", String.valueOf(plotId))
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z));
        context.getMessagesAPI().sendRaw(player, msg);
        return true;
    }

    private boolean handlePlotRemove(SetupContext<Player, CommandSender, Location> context) {
        String idArg = context.getHandlerArg(1);
        if (idArg == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.remove.usage"));
            return true;
        }

        int plotId = parsePlotId(context, idArg);
        if (plotId == -1) {
            return true;
        }

        int totalPlots = context.getData().getInt("game.plots.total", 0);
        context.getData().remove("game.plots.list.p" + plotId);

        for (int i = plotId + 1; i <= totalPlots; i++) {
            String oldPath = "game.plots.list.p" + i;
            String newPath = "game.plots.list.p" + (i - 1);

            Location oldMin = context.getData().getLocation(oldPath + ".bounds.min");
            Location oldMax = context.getData().getLocation(oldPath + ".bounds.max");
            Location oldSpawn = context.getData().getLocation(oldPath + ".spawn");

            if (oldMin != null && oldMax != null) {
                context.getData().setRegionBounds(newPath + ".bounds", oldMin, oldMax);
            }
            if (oldSpawn != null) {
                context.getData().setLocation(newPath + ".spawn", oldSpawn);
            }
            context.getData().remove(oldPath);
        }

        context.getData().setInt("game.plots.total", totalPlots - 1);
        context.getData().save();

        String msg = getSetupMessage("plot.remove.success").replace("{index}", String.valueOf(plotId));
        context.getMessagesAPI().sendRaw(context.getPlayer(), msg);
        return true;
    }

    private boolean handlePlotSpawn(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(1);
        if (!"set".equalsIgnoreCase(action)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.spawn.usage"));
            return true;
        }

        String idArg = context.getHandlerArg(2);
        if (idArg == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.spawn.usage"));
            return true;
        }

        int plotId = parsePlotId(context, idArg);
        if (plotId == -1) {
            return true;
        }

        Location loc = context.getPlayer().getLocation();
        String basePath = "game.plots.list.p" + plotId;
        context.getData().setLocation(basePath + ".spawn", loc);
        context.getData().save();

        String msg = getSetupMessage("plot.spawn.set").replace("{index}", String.valueOf(plotId));
        context.getMessagesAPI().sendRaw(context.getPlayer(), msg);
        return true;
    }

    private boolean handleBuildAreaAdd(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        int totalPlots = context.getData().getInt("game.plots.total", 0);
        if (totalPlots == 0) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.requires_plot_first"));
            return true;
        }

        int totalAreas = context.getData().getInt("game.build_areas.total", 0);
        int newAreaNumber = totalAreas + 1;
        return saveSelectedBuildArea(context, player, newAreaNumber, newAreaNumber, true, "build_area.add.set");
    }

    private boolean handleBuildAreaSet(SetupContext<Player, CommandSender, Location> context) {
        String idArg = context.getHandlerArg(1);
        if (idArg == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("build_area.set.usage"));
            return true;
        }

        int areaId = parseAreaId(context, idArg);
        if (areaId == -1) {
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        return saveSelectedBuildArea(context, player, areaId, areaId, false, "build_area.set.success");
    }

    private boolean handlePlotBuildArea(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(1);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.build_area.usage"));
            return true;
        }

        if ("set".equalsIgnoreCase(action) || "add".equalsIgnoreCase(action)) {
            String idArg = context.getHandlerArg(2);
            if (idArg == null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.build_area.usage"));
                return true;
            }

            int plotId = parsePlotId(context, idArg);
            if (plotId == -1) {
                return true;
            }

            Player player = context.getPlayer();
            if (player == null) {
                return true;
            }

            return saveSelectedBuildArea(context, player, plotId, plotId, true, "plot.build_area.set");
        }

        if ("remove".equalsIgnoreCase(action)) {
            String idArg = context.getHandlerArg(2);
            if (idArg == null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.build_area.usage"));
                return true;
            }

            int plotId = parsePlotId(context, idArg);
            if (plotId == -1) {
                return true;
            }

            context.getData().remove("game.build_areas.list.a" + plotId);
            trimBuildAreaTotal(context);
            context.getData().save();

            String msg = getSetupMessage("plot.build_area.remove").replace("{index}", String.valueOf(plotId));
            context.getMessagesAPI().sendRaw(context.getPlayer(), msg);
            return true;
        }

        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.build_area.usage"));
        return true;
    }

    private boolean saveSelectedBuildArea(SetupContext<Player, CommandSender, Location> context,
                                          Player player,
                                          int areaId,
                                          int plotId,
                                          boolean updateTotal,
                                          String successMessageKey) {
        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int height = maxY - minY + 1;

        if (height < 2) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.too_shallow"));
            return true;
        }

        World world = pos1.getWorld() != null ? pos1.getWorld() : pos2.getWorld();
        if (world == null) {
            return true;
        }

        int floorLayers = countFloorLayers(world, minX, maxX, minY, maxY, minZ, maxZ);
        if (floorLayers == 0) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.no_floor"));
            return true;
        }
        if (floorLayers > 2) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.too_many_floor_layers"));
            return true;
        }

        Material floorMaterial = detectFloorMaterial(world, minX, maxX, minY, minZ, maxZ);

        if (!isBuildAreaInsidePlot(context, plotId, minX, maxX, minY, maxY, minZ, maxZ)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("build_area.add.outside_plot"));
            return true;
        }

        String basePath = "game.build_areas.list.a" + areaId;
        context.getData().setRegionBounds(basePath, pos1, pos2);
        context.getData().setString(basePath + ".floor", floorMaterial.name());
        if (updateTotal) {
            int totalAreas = context.getData().getInt("game.build_areas.total", 0);
            if (areaId > totalAreas) {
                context.getData().setInt("game.build_areas.total", areaId);
            }
        }
        context.getData().save();

        int x = Math.abs(maxX - minX) + 1;
        int y = height;
        int z = Math.abs(maxZ - minZ) + 1;
        int blocks = x * y * z;

        String msg = getSetupMessage(successMessageKey)
                .replace("{index}", String.valueOf(areaId))
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{floor}", floorMaterial.name());
        context.getMessagesAPI().sendRaw(player, msg);
        sendBuildAreaSizeWarning(context, player, x, y, z);
        return true;
    }

    private boolean isBuildAreaInsidePlot(SetupContext<Player, CommandSender, Location> context,
                                          int plotId,
                                          int minX,
                                          int maxX,
                                          int minY,
                                          int maxY,
                                          int minZ,
                                          int maxZ) {
        int totalPlots = context.getData().getInt("game.plots.total", 0);
        if (plotId < 1 || plotId > totalPlots) {
            return true;
        }

        String plotPath = "game.plots.list.p" + plotId + ".bounds";
        Location plotMin = context.getData().getLocation(plotPath + ".min");
        Location plotMax = context.getData().getLocation(plotPath + ".max");
        if (plotMin == null || plotMax == null) {
            return true;
        }

        int plotMinX = Math.min(plotMin.getBlockX(), plotMax.getBlockX());
        int plotMaxX = Math.max(plotMin.getBlockX(), plotMax.getBlockX());
        int plotMinY = Math.min(plotMin.getBlockY(), plotMax.getBlockY());
        int plotMaxY = Math.max(plotMin.getBlockY(), plotMax.getBlockY());
        int plotMinZ = Math.min(plotMin.getBlockZ(), plotMax.getBlockZ());
        int plotMaxZ = Math.max(plotMin.getBlockZ(), plotMax.getBlockZ());
        return minX >= plotMinX && maxX <= plotMaxX
                && minY >= plotMinY && maxY <= plotMaxY
                && minZ >= plotMinZ && maxZ <= plotMaxZ;
    }

    private void trimBuildAreaTotal(SetupContext<Player, CommandSender, Location> context) {
        int totalAreas = context.getData().getInt("game.build_areas.total", 0);
        while (totalAreas > 0) {
            Location min = context.getData().getLocation("game.build_areas.list.a" + totalAreas + ".min");
            Location max = context.getData().getLocation("game.build_areas.list.a" + totalAreas + ".max");
            if (min != null && max != null) {
                break;
            }
            totalAreas--;
        }
        context.getData().setInt("game.build_areas.total", totalAreas);
    }

    private boolean handleBuildAreaRemove(SetupContext<Player, CommandSender, Location> context) {
        String idArg = context.getHandlerArg(1);
        if (idArg == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("build_area.remove.usage"));
            return true;
        }

        int areaId = parseAreaId(context, idArg);
        if (areaId == -1) {
            return true;
        }

        int totalAreas = context.getData().getInt("game.build_areas.total", 0);
        context.getData().remove("game.build_areas.list.a" + areaId);

        for (int i = areaId + 1; i <= totalAreas; i++) {
            String oldPath = "game.build_areas.list.a" + i;
            String newPath = "game.build_areas.list.a" + (i - 1);

            Location oldMin = context.getData().getLocation(oldPath + ".min");
            Location oldMax = context.getData().getLocation(oldPath + ".max");
            String oldFloor = context.getData().getString(oldPath + ".floor");

            if (oldMin != null && oldMax != null) {
                context.getData().setRegionBounds(newPath, oldMin, oldMax);
            }
            if (oldFloor != null) {
                context.getData().setString(newPath + ".floor", oldFloor);
            }
            context.getData().remove(oldPath);
        }

        context.getData().setInt("game.build_areas.total", totalAreas - 1);
        context.getData().save();

        String msg = getSetupMessage("build_area.remove.success").replace("{index}", String.valueOf(areaId));
        context.getMessagesAPI().sendRaw(context.getPlayer(), msg);
        return true;
    }


    private void sendBuildAreaSizeWarning(SetupContext<Player, CommandSender, Location> context,
                                          Player player,
                                          int width,
                                          int height,
                                          int length) {
        if (width >= RECOMMENDED_BUILD_AREA_WIDTH
                && height >= RECOMMENDED_BUILD_AREA_HEIGHT
                && length >= RECOMMENDED_BUILD_AREA_LENGTH) {
            return;
        }

        String warning = getSetupMessage("build_area.add.below_recommended_size")
                .replace("{x}", String.valueOf(width))
                .replace("{y}", String.valueOf(height))
                .replace("{z}", String.valueOf(length))
                .replace("{recommended_x}", String.valueOf(RECOMMENDED_BUILD_AREA_WIDTH))
                .replace("{recommended_y}", String.valueOf(RECOMMENDED_BUILD_AREA_HEIGHT))
                .replace("{recommended_z}", String.valueOf(RECOMMENDED_BUILD_AREA_LENGTH));
        if (!warning.isEmpty()) {
            context.getMessagesAPI().sendRaw(player, warning);
        }
    }

    private int parsePlotId(SetupContext<Player, CommandSender, Location> context, String idArg) {
        try {
            int plotId = Integer.parseInt(idArg);
            int totalPlots = context.getData().getInt("game.plots.total", 0);
            if (plotId < 1 || plotId > totalPlots) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.not_found"));
                return -1;
            }
            return plotId;
        } catch (NumberFormatException e) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("plot.invalid_id"));
            return -1;
        }
    }

    private int parseAreaId(SetupContext<Player, CommandSender, Location> context, String idArg) {
        try {
            int areaId = Integer.parseInt(idArg);
            int totalAreas = context.getData().getInt("game.build_areas.total", 0);
            if (areaId < 1 || areaId > totalAreas) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("build_area.not_found"));
                return -1;
            }
            return areaId;
        } catch (NumberFormatException e) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("build_area.invalid_id"));
            return -1;
        }
    }

    private int countFloorLayers(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int layers = 0;
        for (int y = minY; y <= maxY; y++) {
            boolean hasBlock = false;
            for (int x = minX; x <= maxX && !hasBlock; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        hasBlock = true;
                        break;
                    }
                }
            }
            if (hasBlock) {
                layers++;
            } else {
                break;
            }
        }
        return layers;
    }

    private Material detectFloorMaterial(World world, int minX, int maxX, int minY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Material material = world.getBlockAt(x, minY, z).getType();
                if (material != Material.AIR) {
                    return material;
                }
            }
        }
        return Material.GRASS_BLOCK;
    }


    private String getSetupMessage(String key) {
        String message = moduleConfig.getStringFrom("language.yml", "setup_messages." + key);
        if (message == null) {
            return "";
        }
        return message;
    }
}
