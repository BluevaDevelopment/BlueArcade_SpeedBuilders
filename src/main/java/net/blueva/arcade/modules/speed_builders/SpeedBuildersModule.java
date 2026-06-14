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

public class SpeedBuildersModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsAPI statsAPI;
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

        File dataFolder = moduleConfig.getDataFolder();
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
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
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
        moduleConfig.register("language.yml", 1);
        moduleConfig.register("settings.yml", 1);
        moduleConfig.register("achievements.yml", 1);
        moduleConfig.register("store.yml", 1);
    }

    private void registerStats() {
        if (statsAPI == null) {
            return;
        }
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins"),
                        moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played"),
                        moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("rounds_survived", moduleConfig.getStringFrom("language.yml", "stats.labels.rounds_survived"),
                        moduleConfig.getStringFrom("language.yml", "stats.descriptions.rounds_survived"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("perfect_builds", moduleConfig.getStringFrom("language.yml", "stats.labels.perfect_builds"),
                        moduleConfig.getStringFrom("language.yml", "stats.descriptions.perfect_builds"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }
}
