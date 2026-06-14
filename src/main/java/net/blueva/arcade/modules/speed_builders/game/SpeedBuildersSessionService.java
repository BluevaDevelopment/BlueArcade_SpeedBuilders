package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

class SpeedBuildersSessionService {

    private final ModuleInfo moduleInfo;
    private final StatsAPI statsAPI;

    SpeedBuildersSessionService(ModuleInfo moduleInfo, StatsAPI statsAPI) {
        this.moduleInfo = moduleInfo;
        this.statsAPI = statsAPI;
    }

    void resetWorldDefaults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null || context.getArenaAPI() == null) return;
        World world = context.getArenaAPI().getWorld();
        if (world == null) return;
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    void resetPlayerStates(List<Player> players) {
        if (players == null) return;
        org.bukkit.attribute.Attribute maxHealthAttribute = maxHealthAttribute();
        for (Player player : players) {
            if (player == null) continue;
            player.setGameMode(GameMode.SURVIVAL);
            player.resetPlayerTime();
            player.resetPlayerWeather();
            player.setAllowFlight(false);
            player.setFlying(false);
            if (maxHealthAttribute != null && player.getAttribute(maxHealthAttribute) != null) {
                player.getAttribute(maxHealthAttribute).setBaseValue(20.0);
            }
            player.setHealth(Math.min(player.getHealth(), 20.0));
        }
    }

    void clearPlayerInventories(List<Player> players) {
        if (players == null) return;
        for (Player player : players) {
            if (player == null) continue;
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setExtraContents(null);
            player.updateInventory();
        }
    }

    void recordStats(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                     ArenaState state) {
        if (statsAPI == null || state == null) return;
        for (Player player : context.getPlayers()) {
            UUID uuid = player.getUniqueId();
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
            statsAPI.addModuleStat(player, moduleInfo.getId(), "rounds_survived", state.getRoundsSurvived(uuid));
            statsAPI.addModuleStat(player, moduleInfo.getId(), "perfect_builds", state.getPerfectBuilds(uuid));
        }
    }

    private org.bukkit.attribute.Attribute maxHealthAttribute() {
        org.bukkit.attribute.Attribute attribute = attributeConstant("MAX_HEALTH");
        return attribute != null ? attribute : attributeConstant("GENERIC_MAX_HEALTH");
    }

    private org.bukkit.attribute.Attribute attributeConstant(String fieldName) {
        try {
            Object value = org.bukkit.attribute.Attribute.class.getField(fieldName).get(null);
            return value instanceof org.bukkit.attribute.Attribute attr ? attr : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
