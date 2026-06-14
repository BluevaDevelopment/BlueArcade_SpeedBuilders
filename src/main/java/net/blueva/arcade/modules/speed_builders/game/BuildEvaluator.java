package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class BuildEvaluator {

    private static final Set<Material> ALWAYS_MATCH = Set.of(Material.END_PORTAL_FRAME);
    private static final Set<Material> CONNECTION_SENSITIVE = Set.of(
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE, Material.DARK_OAK_FENCE,
            Material.CRIMSON_FENCE, Material.WARPED_FENCE, Material.NETHER_BRICK_FENCE,
            Material.IRON_BARS,
            Material.GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
            Material.COBBLESTONE_WALL, Material.MOSSY_COBBLESTONE_WALL,
            Material.BRICK_WALL, Material.PRISMARINE_WALL, Material.RED_SANDSTONE_WALL,
            Material.MOSSY_STONE_BRICK_WALL, Material.GRANITE_WALL, Material.STONE_BRICK_WALL,
            Material.NETHER_BRICK_WALL, Material.ANDESITE_WALL, Material.RED_NETHER_BRICK_WALL,
            Material.SANDSTONE_WALL, Material.END_STONE_BRICK_WALL, Material.DIORITE_WALL,
            Material.BLACKSTONE_WALL, Material.POLISHED_BLACKSTONE_BRICK_WALL,
            Material.POLISHED_BLACKSTONE_WALL, Material.COBBLED_DEEPSLATE_WALL,
            Material.POLISHED_DEEPSLATE_WALL, Material.DEEPSLATE_BRICK_WALL,
            Material.DEEPSLATE_TILE_WALL
    );

    public static BuildScore evaluateBuild(Location origin, StructureTemplate structure, float yaw,
                                            boolean countPerfect, boolean evaluateMobs) {
        if (origin == null || structure == null) {
            return new BuildScore(0, 0, 0, 0, false);
        }

        yaw = (yaw % 360.0f + 360.0f) % 360.0f;
        int rotationSteps = Math.round(yaw / 90.0f) % 4;

        int cx = 0;
        int cy = 0;
        int cz = 0;

        int totalBlocks = 0;
        int correctBlocks = 0;
        int totalMobs = 0;
        int correctMobs = 0;

        for (StructureTemplate.Voxel voxel : structure.getVoxels()) {
            if (voxel.dy != 0 && (voxel.mat == null || !voxel.mat.contains("half=upper"))) {
                totalBlocks++;
                int[] rot = rotateBlockCoords(voxel.dx - cx, voxel.dz - cz, rotationSteps);
                int rx = rot[0];
                int rz = rot[1];

                World world = origin.getWorld();
                Block worldBlock = world.getBlockAt(
                        origin.getBlockX() + rx,
                        origin.getBlockY() + (voxel.dy - cy),
                        origin.getBlockZ() + rz);

                boolean ok = matchBlock(voxel.mat, worldBlock, rotationSteps);
                if (ok) {
                    correctBlocks++;
                }
            }
        }

        if (evaluateMobs && structure.getCreatures() != null && !structure.getCreatures().isEmpty()) {
            World w = origin.getWorld();
            for (StructureTemplate.Creature c : structure.getCreatures()) {
                totalMobs++;
                int[] rot = rotateBlockCoords(
                        (int) Math.floor(c.dx) - cx,
                        (int) Math.floor(c.dz) - cz,
                        rotationSteps);
                int rx = rot[0];
                int rz = rot[1];
                double checkX = origin.getBlockX() + rx + 0.5;
                double checkY = origin.getBlockY() + (c.dy - cy);
                double checkZ = origin.getBlockZ() + rz + 0.5;

                boolean found = false;
                for (Entity near : w.getNearbyEntities(new Location(w, checkX, checkY, checkZ), 0.4, 0.6, 0.4)) {
                    if (near instanceof LivingEntity && !(near instanceof Player)
                            && near.getType().name().equalsIgnoreCase(c.mob)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    correctMobs++;
                }
            }
        }

        boolean perfect = totalBlocks == correctBlocks && totalMobs == correctMobs;
        return new BuildScore(totalBlocks, correctBlocks, totalMobs, correctMobs, perfect);
    }

    private static boolean matchBlock(String templateMat, Block worldBlock, int rotationSteps) {
        try {
            String rotatedMat = rotateBlockData(templateMat, rotationSteps);
            rotatedMat = stripWaterlogged(rotatedMat);
            BlockData expected = Bukkit.createBlockData(rotatedMat);
            BlockData actual = worldBlock.getBlockData();
            Material eMat = expected.getMaterial();
            Material aMat = actual.getMaterial();

            Material ne = wallAttachedToItem(eMat);
            if (ne != null) {
                eMat = ne;
            }
            Material na = wallAttachedToItem(aMat);
            if (na != null) {
                aMat = na;
            }

            if (bucketForFluid(eMat.name()) != null) {
                return bucketForFluid(aMat.name()) != null && isSourceFluid(worldBlock);
            }

            if (eMat != aMat) {
                return false;
            }

            if (ALWAYS_MATCH.contains(eMat)) {
                return true;
            }

            if (isConnectionSensitive(eMat)) {
                return eMat == aMat;
            }

            return expected.matches(actual) || actual.matches(expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String stripWaterlogged(String data) {
        if (data == null || !data.contains("waterlogged=true")) {
            return data;
        }
        String[] parts = data.split("\\[", 2);
        if (parts.length < 2) {
            return data;
        }
        String base = parts[0];
        String props = parts[1].substring(0, parts[1].length() - 1);
        String[] pairs = props.isEmpty() ? new String[0] : props.split(",");
        List<String> kept = new ArrayList<>();
        for (String p : pairs) {
            if (!p.trim().startsWith("waterlogged")) {
                kept.add(p.trim());
            }
        }
        if (kept.isEmpty()) {
            return base;
        }
        return base + "[" + String.join(",", kept) + "]";
    }

    private static Material wallAttachedToItem(Material mat) {
        return switch (mat) {
            case ACACIA_WALL_SIGN -> Material.ACACIA_SIGN;
            case BIRCH_WALL_SIGN -> Material.BIRCH_SIGN;
            case CRIMSON_WALL_SIGN -> Material.CRIMSON_SIGN;
            case DARK_OAK_WALL_SIGN -> Material.DARK_OAK_SIGN;
            case JUNGLE_WALL_SIGN -> Material.JUNGLE_SIGN;
            case OAK_WALL_SIGN -> Material.OAK_SIGN;
            case SPRUCE_WALL_SIGN -> Material.SPRUCE_SIGN;
            case WARPED_WALL_SIGN -> Material.WARPED_SIGN;
            default -> null;
        };
    }

    private static Material bucketForFluid(String name) {
        return switch (name.toUpperCase()) {
            case "WATER", "WATER_BUCKET" -> Material.WATER_BUCKET;
            case "LAVA", "LAVA_BUCKET" -> Material.LAVA_BUCKET;
            case "POWDER_SNOW", "POWDER_SNOW_BUCKET" -> Material.POWDER_SNOW_BUCKET;
            default -> null;
        };
    }

    private static boolean isSourceFluid(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Levelled levelled) {
                return levelled.getLevel() == 0;
            }
        }
        if (type == Material.POWDER_SNOW) {
            return true;
        }
        return false;
    }

    private static boolean isConnectionSensitive(Material mat) {
        return CONNECTION_SENSITIVE.contains(mat);
    }

    public static int[] rotateBlockCoords(int x, int z, int steps) {
        int rx = x;
        int rz = z;
        for (int i = 0; i < steps; i++) {
            int temp = rx;
            rx = -rz;
            rz = temp;
        }
        return new int[]{rx, rz};
    }

    public static String rotateBlockData(String data, int rotationSteps) {
        if (data == null || !data.contains("[")) {
            return data;
        }
        rotationSteps = ((rotationSteps % 4) + 4) % 4;
        if (rotationSteps == 0) {
            return data;
        }

        String[] parts = data.split("\\[", 2);
        String base = parts[0];
        String properties = parts[1].substring(0, parts[1].length() - 1);
        Map<String, String> map = new LinkedHashMap<>();
        if (!properties.isEmpty()) {
            String[] props = properties.split(",");
            for (String prop : props) {
                String[] kv = prop.split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        if (map.containsKey("facing")) {
            map.put("facing", rotateFacingCardinal(map.get("facing"), rotationSteps));
        }

        if (map.containsKey("rotation")) {
            try {
                int rot = Integer.parseInt(map.get("rotation"));
                rot = (rot + rotationSteps * 4) % 16;
                map.put("rotation", String.valueOf(rot));
            } catch (NumberFormatException ignored) {
            }
        }

        if (map.containsKey("axis") && rotationSteps % 2 == 1) {
            String v = map.get("axis");
            if ("x".equals(v)) {
                map.put("axis", "z");
            } else if ("z".equals(v)) {
                map.put("axis", "x");
            }
        }

        if (map.containsKey("shape")) {
            map.put("shape", rotateRailShape(map.get("shape"), rotationSteps));
        }

        if (map.containsKey("north") || map.containsKey("east") || map.containsKey("south") || map.containsKey("west")) {
            String n = map.getOrDefault("north", "false");
            String e = map.getOrDefault("east", "false");
            String s = map.getOrDefault("south", "false");
            String w = map.getOrDefault("west", "false");
            for (int i = 0; i < rotationSteps; i++) {
                String tmp = n;
                n = w;
                w = s;
                s = e;
                e = tmp;
            }
            map.put("north", n);
            map.put("east", e);
            map.put("south", s);
            map.put("west", w);
        }

        StringBuilder sb = new StringBuilder(base).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    private static String rotateFacingCardinal(String v, int steps) {
        steps = (steps % 4 + 4) % 4;
        if (v == null) return v;
        return switch (steps) {
            case 1 -> switch (v) {
                case "north" -> "east";
                case "east" -> "south";
                case "south" -> "west";
                case "west" -> "north";
                default -> v;
            };
            case 2 -> switch (v) {
                case "north" -> "south";
                case "east" -> "west";
                case "south" -> "north";
                case "west" -> "east";
                default -> v;
            };
            case 3 -> switch (v) {
                case "north" -> "west";
                case "west" -> "south";
                case "south" -> "east";
                case "east" -> "north";
                default -> v;
            };
            default -> v;
        };
    }

    private static String rotateRailShape(String v, int steps) {
        steps = (steps % 4 + 4) % 4;
        if (v == null || steps == 0) {
            return v;
        }
        for (int i = 0; i < steps; i++) {
            v = switch (v) {
                case "north_south" -> "east_west";
                case "east_west" -> "north_south";
                case "ascending_north" -> "ascending_east";
                case "ascending_east" -> "ascending_south";
                case "ascending_south" -> "ascending_west";
                case "ascending_west" -> "ascending_north";
                case "south_east" -> "south_west";
                case "south_west" -> "north_west";
                case "north_west" -> "north_east";
                case "north_east" -> "south_east";
                default -> v;
            };
        }
        return v;
    }

    public record BuildScore(int totalBlocks, int correctBlocks, int totalMobs, int correctMobs, boolean perfect) {
        public double percentage() {
            int total = totalBlocks + totalMobs;
            if (total == 0) {
                return 100.0;
            }
            return ((double) (correctBlocks + correctMobs) / (double) total) * 100.0;
        }
    }
}
