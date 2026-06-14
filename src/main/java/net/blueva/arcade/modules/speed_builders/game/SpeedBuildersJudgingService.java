package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

class SpeedBuildersJudgingService {

    private final ModuleConfigAPI moduleConfig;
    private final GuardianService guardianService;
    private final SpeedBuildersWorldSupport worldSupport;
    private final SpeedBuildersScoreboardService scoreboardService;

    SpeedBuildersJudgingService(ModuleConfigAPI moduleConfig,
                                GuardianService guardianService,
                                SpeedBuildersWorldSupport worldSupport,
                                SpeedBuildersScoreboardService scoreboardService) {
        this.moduleConfig = moduleConfig;
        this.guardianService = guardianService;
        this.worldSupport = worldSupport;
        this.scoreboardService = scoreboardService;
    }

    void evaluateBuild(Player player,
                       Map<Player, Integer> playerArena,
                       Map<Integer, ArenaState> arenas,
                       BiConsumer<GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity>, ArenaState> startJudgingPhase) {
        Integer arenaId = playerArena.get(player);
        if (arenaId == null) return;
        ArenaState state = arenas.get(arenaId);
        if (state == null || state.isEnded()) return;
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = state.getContext();
        if (context == null) return;
        if (state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) return;
        if (state.getPerfectPlayers().contains(player.getUniqueId())) return;

        Location buildOrigin = worldSupport.getBuildOrigin(state, player);
        StructureTemplate structure = state.getCurrentStructure();
        if (buildOrigin == null || structure == null) return;

        float yaw = state.getPlayerPlotYaw(player.getUniqueId());
        BuildEvaluator.BuildScore score = BuildEvaluator.evaluateBuild(buildOrigin, structure, yaw, true, true);
        state.setLastScore(player.getUniqueId(), score.percentage());
        scoreboardService.updateScoreboard(context, state);

        if (score.perfect()) {
            state.addPerfectPlayer(player.getUniqueId());
            state.incrementPerfectBuilds(player.getUniqueId());

            String title = moduleConfig.getStringFrom("language.yml", "titles.perfect.title");
            String subtitle = moduleConfig.getStringFrom("language.yml", "titles.perfect.subtitle");
            if (title != null && subtitle != null) {
                context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 10);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            String broadcast = moduleConfig.getStringFrom("language.yml", "messages.perfect_build.broadcast");
            if (broadcast != null) {
                for (Player p : context.getPlayers()) {
                    if (p.isOnline()) {
                        context.getMessagesAPI().sendRaw(p, broadcast.replace("{player}", player.getName()));
                    }
                }
            }

            if (allAlivePlayersPerfect(context, state)) {
                String allPerfect = moduleConfig.getStringFrom("language.yml", "messages.all_perfect.broadcast");
                if (allPerfect != null) {
                    for (Player p : context.getPlayers()) {
                        if (p.isOnline()) {
                            context.getMessagesAPI().sendRaw(p, allPerfect);
                        }
                    }
                }
                context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());
                context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_speed_builders_all_perfect", () -> {
                    startJudgingPhase.accept(context, state);
                }, 60L);
            }
        }
    }

    private boolean allAlivePlayersPerfect(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                           ArenaState state) {
        for (Player player : context.getAlivePlayers()) {
            if (!state.getPerfectPlayers().contains(player.getUniqueId())) {
                return false;
            }
        }
        return !context.getAlivePlayers().isEmpty();
    }

    void evaluateBuildsAndEliminate(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state,
                                    BiConsumer<GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity>, ArenaState> startNextRound) {
        if (state.isEnded()) return;

        StructureTemplate structure = state.getCurrentStructure();
        if (structure == null) {
            endGameWithWinner(context);
            return;
        }

        Map<UUID, Double> scores = new HashMap<>();
        for (Player player : context.getAlivePlayers()) {
            Location buildOrigin = worldSupport.getBuildOrigin(state, player);
            if (buildOrigin == null) continue;
            float yaw = state.getPlayerPlotYaw(player.getUniqueId());
            BuildEvaluator.BuildScore score = BuildEvaluator.evaluateBuild(buildOrigin, structure, yaw, false, true);
            double pct = score.percentage();
            state.setLastScore(player.getUniqueId(), pct);
            scores.put(player.getUniqueId(), pct);
        }

        if (scores.isEmpty()) {
            endGameWithWinner(context);
            return;
        }

        int aliveCount = context.getAlivePlayers().size();
        boolean allAliveScored = scores.size() == aliveCount;
        boolean allScoredPlayersPerfect = allAliveScored && scores.values().stream().allMatch(this::isPerfectScore);
        if (allScoredPlayersPerfect) {
            continueAfterJudging(context, state, startNextRound);
            return;
        }

        UUID lowest = null;
        double lowestScore = 101.0;
        for (Map.Entry<UUID, Double> entry : scores.entrySet()) {
            if (!isPerfectScore(entry.getValue()) && entry.getValue() < lowestScore) {
                lowestScore = entry.getValue();
                lowest = entry.getKey();
            }
        }
        if (lowest != null) {
            Player eliminated = Bukkit.getPlayer(lowest);
            if (eliminated != null && eliminated.isOnline()) {
                guardianService.aimGuardianAtPlayerPlot(context, state, eliminated);
                guardianService.explodePlayerPlot(context, state, eliminated);
                eliminatePlayer(context, eliminated, lowestScore);
            }
        }

        continueAfterJudging(context, state, startNextRound);
    }

    private boolean isPerfectScore(double score) {
        return score >= 99.999;
    }

    private void continueAfterJudging(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      ArenaState state,
                                      BiConsumer<GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity>, ArenaState> startNextRound) {
        List<Player> alive = new ArrayList<>(context.getAlivePlayers());
        if (alive.size() <= 1) {
            if (alive.size() == 1) {
                context.setWinner(alive.get(0));
            }
            context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_speed_builders_end", () -> {
                context.endGame();
            }, 60L);
            return;
        }

        int betweenRounds = moduleConfig.getInt("time.between_rounds", 3);
        context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_speed_builders_next_round", () -> {
            startNextRound.accept(context, state);
        }, betweenRounds * 20L);
    }

    private void eliminatePlayer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Player player, double score) {
        context.eliminatePlayer(player, "lowest_score");
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();

        String title = moduleConfig.getStringFrom("language.yml", "titles.elimination.title");
        String subtitle = moduleConfig.getStringFrom("language.yml", "titles.elimination.subtitle");
        if (title != null && subtitle != null) {
            context.getTitlesAPI().sendRaw(player,
                    title,
                    subtitle.replace("{score}", String.format("%.0f", score)),
                    0, 30, 10);
        }

        String broadcast = moduleConfig.getStringFrom("language.yml", "messages.elimination.broadcast");
        if (broadcast != null) {
            for (Player p : context.getPlayers()) {
                if (p.isOnline()) {
                    context.getMessagesAPI().sendRaw(p,
                            broadcast.replace("{player}", player.getName())
                                    .replace("{score}", String.format("%.0f", score)));
                }
            }
        }
    }

    void endGameWithWinner(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> alive = new ArrayList<>(context.getAlivePlayers());
        if (alive.size() == 1) {
            Player winner = alive.get(0);
            context.setWinner(winner);
            for (Player p : context.getPlayers()) {
                if (!p.isOnline()) continue;
                String title = moduleConfig.getStringFrom("language.yml", "titles.winner.title");
                String subtitle = moduleConfig.getStringFrom("language.yml", "titles.winner.subtitle");
                if (title != null && subtitle != null) {
                    context.getTitlesAPI().sendRaw(p,
                            title,
                            subtitle.replace("{player}", winner.getName()),
                            0, 60, 20);
                }
            }
            String broadcast = moduleConfig.getStringFrom("language.yml", "messages.game_winner.broadcast");
            if (broadcast != null) {
                for (Player p : context.getPlayers()) {
                    if (p.isOnline()) {
                        context.getMessagesAPI().sendRaw(p, broadcast.replace("{player}", winner.getName()));
                    }
                }
            }
        }
        context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_speed_builders_end", () -> {
            context.endGame();
        }, 60L);
    }
}
