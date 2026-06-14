package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class SpeedBuildersScoreboardService {

    void updateScoreboard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          ArenaState state) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("arena", String.valueOf(context.getArenaId()));
        placeholders.put("round", String.valueOf(state.getCurrentRound()));
        placeholders.put("time", String.valueOf(state.getTimeLeft()));

        String phaseKey = switch (state.getPhase()) {
            case SHOWCASE -> "scoreboard.showcase";
            case BUILDING -> "scoreboard.build";
            case JUDGING -> "scoreboard.judging";
            default -> "scoreboard.build";
        };

        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                placeholders.put("percentage", formatPercentage(state.getLastScore(player.getUniqueId())));
                context.getScoreboardAPI().showScoreboard(player, phaseKey);
                context.getScoreboardAPI().update(player, phaseKey, placeholders);
            }
        }
    }

    Map<String, String> getCustomPlaceholders(Player player, ArenaState state) {
        Map<String, String> placeholders = new HashMap<>();
        if (state == null) return placeholders;
        placeholders.put("round", String.valueOf(state.getCurrentRound()));
        placeholders.put("time", String.valueOf(state.getTimeLeft()));
        placeholders.put("percentage", formatPercentage(state.getLastScore(player.getUniqueId())));
        return placeholders;
    }

    String formatPercentage(double percentage) {
        return String.format(Locale.US, "%.0f%%", percentage);
    }
}
