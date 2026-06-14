package net.blueva.arcade.modules.speed_builders.state;

import org.bukkit.Location;
public class BuildPlot {

    private final Location min;
    private final Location max;
    private final Location spawn;

    public BuildPlot(Location min, Location max, Location spawn) {
        this.min = min;
        this.max = max;
        this.spawn = spawn;
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public Location getSpawn() {
        return spawn;
    }

    public boolean isInside(Location location) {
        if (location == null || min == null || max == null) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= Math.min(min.getX(), max.getX()) && x <= Math.max(min.getX(), max.getX())
                && y >= Math.min(min.getY(), max.getY()) && y <= Math.max(min.getY(), max.getY())
                && z >= Math.min(min.getZ(), max.getZ()) && z <= Math.max(min.getZ(), max.getZ());
    }

    public boolean isInsideBlock(int bx, int by, int bz) {
        if (min == null || max == null) {
            return false;
        }
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }
}
