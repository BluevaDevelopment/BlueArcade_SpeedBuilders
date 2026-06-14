package net.blueva.arcade.modules.speed_builders.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.BuildArea;
import net.blueva.arcade.modules.speed_builders.state.BuildPlot;
import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SpeedBuildersGame {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final Logger logger;

    private final Map<Integer, ArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArena = new ConcurrentHashMap<>();
    private final StructureService structureService;
    private final PlotService plotService = new PlotService();
    private final GuardianService guardianService;
    private final SpeedBuildersWorldSupport worldSupport;
    private final SpeedBuildersItemService itemService;
    private final SpeedBuildersScoreboardService scoreboardService;
    private final SpeedBuildersSessionService sessionService;
    private final SpeedBuildersJudgingService judgingService;
    private final Set<Integer> notificationSeconds;

    public SpeedBuildersGame(ModuleInfo moduleInfo,
                             ModuleConfigAPI moduleConfig,
                             CoreConfigAPI coreConfig,
                             StatsAPI statsAPI,
                             File moduleDataFolder,
                             Class<?> resourceAnchor,
                             Logger logger,
                             Plugin metadataPlugin) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.logger = logger;
        this.structureService = new StructureService(moduleDataFolder, resourceAnchor, logger);
        this.guardianService = new GuardianService(moduleConfig, metadataPlugin);
        this.worldSupport = new SpeedBuildersWorldSupport(logger, metadataPlugin);
        this.itemService = new SpeedBuildersItemService();
        this.scoreboardService = new SpeedBuildersScoreboardService();
        this.sessionService = new SpeedBuildersSessionService(moduleInfo, statsAPI);
        this.judgingService = new SpeedBuildersJudgingService(moduleConfig, guardianService, worldSupport, scoreboardService);
        this.notificationSeconds = loadNotificationSeconds(coreConfig);
    }

    private static Set<Integer> loadNotificationSeconds(CoreConfigAPI coreConfig) {
        Set<Integer> seconds = new HashSet<>();
        List<String> raw = coreConfig.getSettingsStringList("game.global.countdown_notifications");
        if (raw != null) {
            for (String s : raw) {
                try {
                    seconds.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return seconds;
    }

    public void startGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = new ArenaState(context);
        arenas.put(arenaId, state);

        if (!structureService.hasStructures()) {
            for (Player p : context.getPlayers()) {
                if (p.isOnline()) {
                    context.getMessagesAPI().sendRaw(p, "<red>No structures loaded for Speed Builders!</red>");
                }
            }
            context.endGame();
            return;
        }

        plotService.loadPlots(context, state);

        if (state.getBuildAreas().isEmpty()) {
            for (Player p : context.getPlayers()) {
                if (p.isOnline()) {
                    context.getMessagesAPI().sendRaw(p, "<red>Speed Builders requires at least one build area per plot. Use /baa game <arena> speed_builders build_area add</red>");
                }
            }
            context.endGame();
            return;
        }

        plotService.assignPlayersToPlotsAndAreas(context, state);
        plotService.regenerateBuildAreaFloors(context, state);

        for (Player player : context.getPlayers()) {
            playerArena.put(player, arenaId);
            state.initializePlayer(player.getUniqueId());
            if (player.isOnline()) {
                plotService.teleportToPlot(context, state, player);
            }
        }
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));
            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));
            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));
            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void beginPlaying(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null) return;

        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;
            player.setGameMode(GameMode.SURVIVAL);
            enableGameFlight(player, true);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
        }


        for (Player player : context.getAlivePlayers()) {
            if (state.getPlayerBuildArea(player.getUniqueId()) == null) {
                for (Player p : context.getPlayers()) {
                    if (p.isOnline()) {
                        context.getMessagesAPI().sendRaw(p, "<red>Not enough build areas configured for all players.</red>");
                    }
                }
                context.endGame();
                return;
            }
        }


        int[] dims = plotService.getMaxBuildDimensions(state);
        if (dims[0] > 0 && dims[1] > 0 && dims[2] > 0) {
            structureService.filterStructuresByMaxSize(dims[0], dims[1], dims[2]);
        }

        if (!structureService.hasStructures()) {
            for (Player p : context.getPlayers()) {
                if (p.isOnline()) {
                    context.getMessagesAPI().sendRaw(p, "<red>No structures fit in the configured plot/build area sizes.</red>");
                }
            }
            context.endGame();
            return;
        }

        structureService.reshuffleOrder();
        startNextRound(context, state);
    }

    private void startNextRound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ArenaState state) {
        if (state.isEnded()) return;

        worldSupport.removeStructureMobs(context, state);
        guardianService.removeGuardian(context, state);

        state.clearPerfectPlayers();
        state.setCurrentRound(state.getCurrentRound() + 1);
        StructureTemplate structure = structureService.pickNextStructure();
        state.setCurrentStructure(structure);

        if (structure == null) {
            judgingService.endGameWithWinner(context);
            return;
        }


        for (Player player : context.getAlivePlayers()) {
            if (!player.isOnline()) continue;
            enableGameFlight(player, true);
            Location buildOrigin = worldSupport.getBuildOrigin(state, player);
            BuildArea buildArea = state.getPlayerBuildArea(player.getUniqueId());
            if (buildOrigin != null) {
                worldSupport.moveOutOfBuildAreaIfInside(context, state, player);
                context.getSchedulerAPI().runAtLocation(buildOrigin, () -> {
                    worldSupport.resetBuildAreaDirect(buildArea);
                    int placed = worldSupport.pasteStructure(buildOrigin, structure, worldSupport.getPlayerRotationSteps(state, player));
                    logger.info("[SpeedBuilders] Pasted reference for " + player.getName()
                            + " in arena " + context.getArenaId()
                            + " at " + worldSupport.formatLocation(buildOrigin) + " with " + placed + " block(s).");
                });
            }
        }
        BuildPlot showcase = state.getShowcasePlot();
        if (showcase != null && showcase.getSpawn() != null) {
            context.getSchedulerAPI().runAtLocation(showcase.getSpawn(),
                    () -> worldSupport.clearRegionAboveFloorDirect(showcase.getMin(), showcase.getMax()));
            guardianService.ensureGuardianAtShowcase(context, state);
            guardianService.startGuardianWatchTask(context, state);
        }

        String structureName = structure.getName() != null ? structure.getName() : "Unknown";
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;
            String title = moduleConfig.getStringFrom("language.yml", "titles.showcase.title");
            String subtitle = moduleConfig.getStringFrom("language.yml", "titles.showcase.subtitle");
            if (title != null && subtitle != null) {
                context.getTitlesAPI().sendRaw(player,
                        title.replace("{structure}", structureName),
                        subtitle.replace("{structure}", structureName),
                        0, 40, 20);
            }

            String broadcast = moduleConfig.getStringFrom("language.yml", "messages.showcase_started.broadcast");
            if (broadcast != null) {
                context.getMessagesAPI().sendRaw(player, broadcast.replace("{structure}", structureName));
            }
        }

        state.setPhase(net.blueva.arcade.modules.speed_builders.state.GamePhase.SHOWCASE);
        enableShowcaseFlight(context);
        int showcaseTime = moduleConfig.getInt("time.showcase", 10);
        state.setTimeLeft(showcaseTime);
        scoreboardService.updateScoreboard(context, state);
        startShowcaseTimer(context, state);
    }

    private void startShowcaseTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_speed_builders_showcase_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded() || state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.SHOWCASE) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.decrementTime();
            int timeLeft = state.getTimeLeft();
            enableShowcaseFlight(context);
            scoreboardService.updateScoreboard(context, state);

            if (timeLeft <= 0) {
                context.getSchedulerAPI().cancelTask(taskId);
                startBuildPhase(context, state);
                return;
            }

            if (notificationSeconds.contains(timeLeft)) {
                for (Player player : context.getPlayers()) {
                    if (player.isOnline()) {
                        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.countdown"));
                    }
                }
            }
        }, 20L, 20L);
    }

    private void enableShowcaseFlight(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player.isOnline() && context.getAlivePlayers().contains(player)) {
                enableGameFlight(player, true);
            }
        }
    }

    private void startBuildPhase(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 ArenaState state) {
        if (state.isEnded()) return;

        state.setPhase(net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING);
        BuildPlot showcase = state.getShowcasePlot();
        if (showcase != null && showcase.getSpawn() != null) {
            context.getSchedulerAPI().runAtLocation(showcase.getSpawn(),
                    () -> worldSupport.clearRegionAboveFloorDirect(showcase.getMin(), showcase.getMax()));
        }

        StructureTemplate structure = state.getCurrentStructure();
        for (Player player : context.getAlivePlayers()) {
            if (!player.isOnline()) continue;
            Location buildOrigin = worldSupport.getBuildOrigin(state, player);
            BuildArea buildArea = state.getPlayerBuildArea(player.getUniqueId());
            if (buildOrigin != null && buildArea != null) {
                context.getSchedulerAPI().runAtLocation(buildOrigin,
                        () -> worldSupport.clearRegionAboveFloorDirect(buildArea.getMin(), buildArea.getMax()));
            }
        }

        for (Player player : context.getAlivePlayers()) {
            if (!player.isOnline()) continue;
            enableGameFlight(player, true);
            itemService.giveStructureItems(player, structure);
            state.incrementRoundsSurvived(player.getUniqueId());

            String title = moduleConfig.getStringFrom("language.yml", "titles.build_start.title");
            String subtitle = moduleConfig.getStringFrom("language.yml", "titles.build_start.subtitle");
            if (title != null && subtitle != null) {
                context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 10);
            }

            String broadcast = moduleConfig.getStringFrom("language.yml", "messages.build_started.broadcast");
            if (broadcast != null) {
                context.getMessagesAPI().sendRaw(player, broadcast);
            }
        }

        Integer buildTime = getCoreBuildTime(context);
        if (buildTime == null) {
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    context.getMessagesAPI().sendRaw(player, "<red>Speed Builders requires the arena Core time setting.</red>");
                }
            }
            context.endGame();
            return;
        }
        state.setTimeLeft(buildTime);
        scoreboardService.updateScoreboard(context, state);
        startBuildTimer(context, state);
    }

    private void startBuildTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 ArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_speed_builders_build_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded() || state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.decrementTime();
            int timeLeft = state.getTimeLeft();
            scoreboardService.updateScoreboard(context, state);

            if (timeLeft <= 0) {
                context.getSchedulerAPI().cancelTask(taskId);
                startJudgingPhase(context, state);
                return;
            }

            if (notificationSeconds.contains(timeLeft)) {
                for (Player player : context.getPlayers()) {
                    if (player.isOnline()) {
                        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.countdown"));
                    }
                }
            }
        }, 20L, 20L);
    }

    private Integer getCoreBuildTime(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Integer coreTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        return coreTime != null && coreTime > 0 ? coreTime : null;
    }

    private void startJudgingPhase(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   ArenaState state) {
        if (state.isEnded()) return;
        state.setPhase(net.blueva.arcade.modules.speed_builders.state.GamePhase.JUDGING);
        guardianService.showGuardianJudgingAnimation(context);
        guardianService.ensureGuardianAtShowcase(context, state);

        StructureTemplate structure = state.getCurrentStructure();
        BuildPlot showcase = state.getShowcasePlot();
        if (showcase != null && showcase.getSpawn() != null && structure != null) {
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    worldSupport.moveOutOfRegionIfInside(context, player, showcase.getMin(), showcase.getMax());
                }
            }
            context.getSchedulerAPI().runAtLocation(showcase.getSpawn(), () -> {
                worldSupport.clearRegionAboveFloorDirect(showcase.getMin(), showcase.getMax());
                int placed = worldSupport.pasteStructure(showcase.getSpawn(), structure, 0);
                logger.info("[SpeedBuilders] Pasted showcase for arena " + context.getArenaId()
                        + " at " + worldSupport.formatLocation(showcase.getSpawn()) + " with " + placed + " block(s).");
            });
        } else {
            logger.warning("[SpeedBuilders] Could not paste showcase for arena " + context.getArenaId()
                    + ": showcase=" + (showcase != null)
                    + ", spawn=" + (showcase != null && showcase.getSpawn() != null)
                    + ", structure=" + (structure != null));
        }
        int judgingDelay = Math.max(1, moduleConfig.getInt("time.elimination_delay", 4));
        state.setTimeLeft(judgingDelay);
        scoreboardService.updateScoreboard(context, state);


        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;
            player.getInventory().clear();
            enableGameFlight(player);
            String title = moduleConfig.getStringFrom("language.yml", "titles.judging.title");
            String subtitle = moduleConfig.getStringFrom("language.yml", "titles.judging.subtitle");
            if (title != null && subtitle != null) {
                context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 30, 10);
            }
            String broadcast = moduleConfig.getStringFrom("language.yml", "messages.judging_started.broadcast");
            if (broadcast != null) {
                context.getMessagesAPI().sendRaw(player, broadcast);
            }
        }

        context.getSchedulerAPI().runLater("arena_" + context.getArenaId() + "_speed_builders_judging", () -> {
            judgingService.evaluateBuildsAndEliminate(context, state, this::startNextRound);
        }, judgingDelay * 20L);
    }

    public void evaluateBuild(Player player) {
        judgingService.evaluateBuild(player, playerArena, arenas, this::startJudgingPhase);
    }

    public void finishGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = arenas.get(arenaId);
        if (state != null) {
            plotService.clearBuildAreas(context, state);
            plotService.clearShowcasePlot(context, state);
            worldSupport.removeStructureMobs(context, state);
            guardianService.removeGuardian(context, state);
            plotService.regenerateBuildAreaFloors(context, state);
        }
        arenas.remove(arenaId);
        sessionService.resetWorldDefaults(context);
        sessionService.resetPlayerStates(context.getPlayers());
        sessionService.clearPlayerInventories(context.getPlayers());
        removePlayersFromArena(arenaId, context.getPlayers());

        sessionService.recordStats(context, state);
    }

    public void shutdown() {
        Set<ArenaState> states = Set.copyOf(arenas.values());
        for (ArenaState state : states) {
            state.getContext().getSchedulerAPI().cancelModuleTasks("speed_builders");
            sessionService.resetWorldDefaults(state.getContext());
            sessionService.resetPlayerStates(state.getContext().getPlayers());
            sessionService.clearPlayerInventories(state.getContext().getPlayers());
        }
        arenas.clear();
        playerArena.clear();
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(Player player) {
        Integer arenaId = playerArena.get(player);
        if (arenaId == null) {
            for (ArenaState state : arenas.values()) {
                if (state.getContext() != null && state.getContext().getPlayers().contains(player)) {
                    arenaId = state.getContext().getArenaId();
                    playerArena.put(player, arenaId);
                    break;
                }
            }
        }
        if (arenaId == null) return null;
        ArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public ArenaState getArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) return null;
        return arenas.get(context.getArenaId());
    }

    public void removePlayersFromArena(int arenaId, List<Player> players) {
        for (Player player : players) {
            playerArena.remove(player);
        }
    }

    public void enableGameFlight(Player player) {
        enableGameFlight(player, false);
    }

    private void enableGameFlight(Player player, boolean startFlying) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setAllowFlight(true);
        if (startFlying) {
            player.setFlying(true);
        }
        player.setFallDistance(0.0f);
    }


    public Map<String, String> getCustomPlaceholders(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getContext(player);
        if (context == null) return new HashMap<>();
        ArenaState state = getArenaState(context);
        return scoreboardService.getCustomPlaceholders(player, state);
    }

    public StructureService getStructureService() {
        return structureService;
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }
}
