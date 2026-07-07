package net.blueva.arcade.modules.speed_builders;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.setup.SetupRequirement;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.speed_builders.game.SpeedBuildersGame;
import net.blueva.arcade.modules.speed_builders.listener.SpeedBuildersListener;
import net.blueva.arcade.modules.speed_builders.setup.SpeedBuildersSetup;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

public class SpeedBuildersModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsAPI statsAPI;
    private File dataFolder;
    private MenuAPI<Player, Material> menuAPI;
    private ItemAPI<Player, ItemStack, Material> itemAPI;

    private SpeedBuildersGame game;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("speed_builders");
        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for SpeedBuilders module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        statsAPI = ModuleAPI.getStatsAPI();

        registerConfigs();
        registerStats();
        registerAchievements();

        MenuAPI<Player, Material> menuAPI = ModuleAPI.getMenuAPI();
        this.menuAPI = menuAPI;
        @SuppressWarnings("unchecked")
        ItemAPI<Player, ItemStack, Material> itemAPI = (ItemAPI<Player, ItemStack, Material>) ModuleAPI.getItemAPI();
        this.itemAPI = itemAPI;

        Plugin ownerPlugin = Bukkit.getPluginManager().getPlugin("BlueArcade3");
        if (ownerPlugin == null) {
            throw new IllegalStateException("BlueArcade3 plugin not available for SpeedBuilders module");
        }

        dataFolder = moduleConfig.getDataFolder();
        game = new SpeedBuildersGame(moduleInfo, moduleConfig, coreConfig, statsAPI,
                dataFolder, SpeedBuildersModule.class, ownerPlugin.getLogger(), ownerPlugin);

        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new SpeedBuildersSetup(moduleConfig, dataFolder));

        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        if (moduleConfig != null && voteMenu != null) {
            String voteItemMaterial = moduleConfig.getString("menus.vote.item");
            Material material;
            try {
                material = Material.valueOf(voteItemMaterial.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                material = Material.BRICKS;
            }
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    material,
                    moduleConfig.getTranslation(null, "vote_menu.name"),
                    moduleConfig.getTranslationList(null, "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.startGame(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        game.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return false;
    }

    @Override
    public Set<SetupRequirement> getDisabledRequirements() {
        return Set.of(SetupRequirement.SPAWNS);
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.beginPlaying(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        game.finishGame(context);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (menuAPI != null && moduleInfo != null) {
            menuAPI.unregisterModuleMenuAPI(moduleInfo.getId());
        }
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new SpeedBuildersListener(game));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        if (game == null || player == null) {
            return new HashMap<>();
        }
        return game.getCustomPlaceholders(player);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    private void registerConfigs() {
        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");
        moduleConfig.register("store.yml");
    }

    private void registerStats() {
        if (statsAPI == null) {
            return;
        }
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"),
                        moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"),
                        moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("rounds_survived", moduleConfig.getTranslation(null, "stats.labels.rounds_survived"),
                        moduleConfig.getTranslation(null, "stats.descriptions.rounds_survived"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("perfect_builds", moduleConfig.getTranslation(null, "stats.labels.perfect_builds"),
                        moduleConfig.getTranslation(null, "stats.descriptions.perfect_builds"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("build_area", true, "Configure Build Area", "Configure the module-specific build area setup data.", List.of("/baa game <arena> speed_builders build_area"), "build area region"),
                        new ModuleSetupStep("plot", true, "Configure Plot", "Configure the module-specific plot setup data.", List.of("/baa game <arena> speed_builders plot"), "plot region and spawn"),
                        new ModuleSetupStep("showcase", true, "Configure Showcase", "Configure the module-specific showcase setup data.", List.of("/baa game <arena> speed_builders showcase"), "showcase region and spawn"),
                        new ModuleSetupStep("structure", true, "Configure Structure", "Configure the module-specific structure setup data.", List.of("/baa game <arena> speed_builders structure"), "saved structure")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("build_area", "/baa game <arena> speed_builders build_area", "Configure build area setup data.", true),
                        new ModuleSetupCommand("plot", "/baa game <arena> speed_builders plot", "Configure plot setup data.", true),
                        new ModuleSetupCommand("showcase", "/baa game <arena> speed_builders showcase", "Configure showcase setup data.", true),
                        new ModuleSetupCommand("structure", "/baa game <arena> speed_builders structure", "Configure structure setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("build_area", true, "Create at least one build area.", context -> context.getData().getInt("game.build_areas.total", 0) > 0),
                        new ModuleSetupStatusCheck<>("plot", true, "Create at least one plot.", context -> context.getData().getInt("game.plots.total", 0) > 0 || context.getData().has("game.plot.bounds.min.x")),
                        new ModuleSetupStatusCheck<>("showcase", true, "Set the showcase plot.", context -> context.getData().has("game.showcase_plot.bounds.min.x") && context.getData().has("game.showcase_plot.bounds.max.x")),
                        new ModuleSetupStatusCheck<>("structure", true, "Save at least one structure.", context -> {
                            File structuresFolder = dataFolder == null ? null : new File(dataFolder, "structures");
                            String[] files = structuresFolder == null ? null : structuresFolder.list((dir, name) -> name.endsWith(".json"));
                            return files != null && files.length > 0;
                        })
                );
            }
        };
    }

}
