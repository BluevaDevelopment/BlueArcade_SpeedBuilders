package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;

class SpeedBuildersItemService {

    void giveStructureItems(Player player, StructureTemplate structure) {
        if (structure == null || structure.getVoxels() == null) return;
        PlayerInventory inv = player.getInventory();
        inv.clear();
        Map<Material, Integer> items = new LinkedHashMap<>();
        for (StructureTemplate.Voxel voxel : structure.getVoxels()) {
            if (voxel.dy == 0 || isUpperHalf(voxel.mat)) continue;
            Material mat = materialFromStructureMat(voxel.mat);
            if (mat != null && mat != Material.AIR) {
                items.merge(mat, 1, Integer::sum);
            }
            if (voxel.mat != null && voxel.mat.contains("waterlogged=true")) {
                items.putIfAbsent(Material.WATER_BUCKET, 1);
            }
        }
        if (structure.getCreatures() != null) {
            for (StructureTemplate.Creature c : structure.getCreatures()) {
                try {
                    EntityType type = EntityType.valueOf(c.mob);
                    Material egg = spawnEggFor(type);
                    if (egg != null) {
                        items.merge(egg, 1, Integer::sum);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            giveExactAmount(inv, entry.getKey(), entry.getValue());
        }
    }

    private void giveExactAmount(PlayerInventory inventory, Material material, int amount) {
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            inventory.addItem(new ItemStack(material, stackAmount));
            remaining -= stackAmount;
        }
    }

    private Material materialFromStructureMat(String mat) {
        if (mat == null) return Material.AIR;
        String base = mat.split("\\[")[0].replace("minecraft:", "").toUpperCase();
        Material bucket = bucketForFluid(base, mat);
        if (bucket != null) {
            return bucket;
        }
        try {
            Material material = Material.valueOf(base);
            Material itemMaterial = wallAttachedToItem(material);
            if (itemMaterial != null) {
                return itemMaterial;
            }
            return material.isItem() ? material : Material.AIR;
        } catch (IllegalArgumentException e) {
            return Material.AIR;
        }
    }

    private boolean isUpperHalf(String blockData) {
        return blockData != null && blockData.contains("half=upper");
    }

    private Material bucketForFluid(String base, String blockData) {
        if (blockData != null && blockData.contains("level=") && !blockData.contains("level=0")) {
            return null;
        }
        return switch (base) {
            case "WATER" -> Material.WATER_BUCKET;
            case "LAVA" -> Material.LAVA_BUCKET;
            case "POWDER_SNOW" -> Material.POWDER_SNOW_BUCKET;
            default -> null;
        };
    }

    private Material wallAttachedToItem(Material mat) {
        return switch (mat) {
            case ACACIA_WALL_SIGN -> Material.ACACIA_SIGN;
            case BIRCH_WALL_SIGN -> Material.BIRCH_SIGN;
            case CRIMSON_WALL_SIGN -> Material.CRIMSON_SIGN;
            case DARK_OAK_WALL_SIGN -> Material.DARK_OAK_SIGN;
            case JUNGLE_WALL_SIGN -> Material.JUNGLE_SIGN;
            case OAK_WALL_SIGN -> Material.OAK_SIGN;
            case SPRUCE_WALL_SIGN -> Material.SPRUCE_SIGN;
            case WARPED_WALL_SIGN -> Material.WARPED_SIGN;
            case WALL_TORCH -> Material.TORCH;
            default -> null;
        };
    }

    private Material spawnEggFor(EntityType type) {
        return switch (type) {
            case PIG -> Material.PIG_SPAWN_EGG;
            case COW -> Material.COW_SPAWN_EGG;
            case SHEEP -> Material.SHEEP_SPAWN_EGG;
            case CHICKEN -> Material.CHICKEN_SPAWN_EGG;
            case RABBIT -> Material.RABBIT_SPAWN_EGG;
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case OCELOT -> Material.OCELOT_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case FOX -> Material.FOX_SPAWN_EGG;
            case PANDA -> Material.PANDA_SPAWN_EGG;
            case TURTLE -> Material.TURTLE_SPAWN_EGG;
            case DOLPHIN -> Material.DOLPHIN_SPAWN_EGG;
            case POLAR_BEAR -> Material.POLAR_BEAR_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case TRADER_LLAMA -> Material.TRADER_LLAMA_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG;
            case ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG;
            case BAT -> Material.BAT_SPAWN_EGG;
            case BEE -> Material.BEE_SPAWN_EGG;
            case STRIDER -> Material.STRIDER_SPAWN_EGG;
            case HOGLIN -> Material.HOGLIN_SPAWN_EGG;
            case PIGLIN -> Material.PIGLIN_SPAWN_EGG;
            case ZOGLIN -> Material.ZOGLIN_SPAWN_EGG;
            case AXOLOTL -> Material.AXOLOTL_SPAWN_EGG;
            case GLOW_SQUID -> Material.GLOW_SQUID_SPAWN_EGG;
            case GOAT -> Material.GOAT_SPAWN_EGG;
            default -> null;
        };
    }
}
