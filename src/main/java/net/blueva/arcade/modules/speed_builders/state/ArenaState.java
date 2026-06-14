package net.blueva.arcade.modules.speed_builders.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final List<BuildPlot> plots = new ArrayList<>();
    private final List<BuildArea> buildAreas = new ArrayList<>();
    private BuildPlot showcasePlot;
    private final Map<UUID, BuildPlot> playerPlot = new HashMap<>();
    private final Map<UUID, BuildArea> playerBuildArea = new HashMap<>();
    private final Map<UUID, Location> playerPlotSpawn = new HashMap<>();
    private final Map<UUID, Float> playerPlotYaw = new HashMap<>();
    private final Set<UUID> perfectPlayers = new HashSet<>();
    private final Map<UUID, Integer> roundsSurvived = new HashMap<>();
    private final Map<UUID, Integer> perfectBuilds = new HashMap<>();
    private final Map<UUID, Double> lastScore = new HashMap<>();
    private UUID guardianUuid;
    private UUID guardianBeamTargetUuid;
    private double guardianOrbitAngle;

    private GamePhase phase = GamePhase.SHOWCASE;
    private StructureTemplate currentStructure;
    private int currentRound = 0;
    private int timeLeft = 0;
    private boolean ended = false;

    public ArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.context = context;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public List<BuildPlot> getPlots() {
        return plots;
    }

    public void addPlot(BuildPlot plot) {
        plots.add(plot);
    }

    public List<BuildArea> getBuildAreas() {
        return buildAreas;
    }

    public void addBuildArea(BuildArea area) {
        buildAreas.add(area);
    }

    public BuildPlot getShowcasePlot() {
        return showcasePlot;
    }

    public void setShowcasePlot(BuildPlot showcasePlot) {
        this.showcasePlot = showcasePlot;
    }

    public void setPlayerPlot(UUID uuid, BuildPlot plot) {
        playerPlot.put(uuid, plot);
    }

    public BuildPlot getPlayerPlot(UUID uuid) {
        return playerPlot.get(uuid);
    }

    public void setPlayerBuildArea(UUID uuid, BuildArea area) {
        playerBuildArea.put(uuid, area);
    }

    public BuildArea getPlayerBuildArea(UUID uuid) {
        return playerBuildArea.get(uuid);
    }

    public void setPlayerPlotSpawn(UUID uuid, Location loc) {
        playerPlotSpawn.put(uuid, loc);
    }

    public Location getPlayerPlotSpawn(UUID uuid) {
        return playerPlotSpawn.get(uuid);
    }

    public void setPlayerPlotYaw(UUID uuid, float yaw) {
        playerPlotYaw.put(uuid, yaw);
    }

    public float getPlayerPlotYaw(UUID uuid) {
        return playerPlotYaw.getOrDefault(uuid, 0.0f);
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public StructureTemplate getCurrentStructure() {
        return currentStructure;
    }

    public void setCurrentStructure(StructureTemplate structure) {
        this.currentStructure = structure;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int round) {
        this.currentRound = round;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }

    public void decrementTime() {
        this.timeLeft--;
    }

    public boolean isEnded() {
        return ended;
    }

    public void markEnded() {
        this.ended = true;
    }

    public Set<UUID> getPerfectPlayers() {
        return perfectPlayers;
    }

    public void addPerfectPlayer(UUID uuid) {
        perfectPlayers.add(uuid);
    }

    public void clearPerfectPlayers() {
        perfectPlayers.clear();
    }

    public int getRoundsSurvived(UUID uuid) {
        return roundsSurvived.getOrDefault(uuid, 0);
    }

    public void incrementRoundsSurvived(UUID uuid) {
        roundsSurvived.put(uuid, roundsSurvived.getOrDefault(uuid, 0) + 1);
    }

    public int getPerfectBuilds(UUID uuid) {
        return perfectBuilds.getOrDefault(uuid, 0);
    }

    public void incrementPerfectBuilds(UUID uuid) {
        perfectBuilds.put(uuid, perfectBuilds.getOrDefault(uuid, 0) + 1);
    }

    public double getLastScore(UUID uuid) {
        return lastScore.getOrDefault(uuid, 0.0);
    }

    public void setLastScore(UUID uuid, double score) {
        lastScore.put(uuid, score);
    }

    public UUID getGuardianUuid() {
        return guardianUuid;
    }

    public void setGuardianUuid(UUID guardianUuid) {
        this.guardianUuid = guardianUuid;
    }

    public UUID getGuardianBeamTargetUuid() {
        return guardianBeamTargetUuid;
    }

    public void setGuardianBeamTargetUuid(UUID guardianBeamTargetUuid) {
        this.guardianBeamTargetUuid = guardianBeamTargetUuid;
    }

    public double getGuardianOrbitAngle() {
        return guardianOrbitAngle;
    }

    public void setGuardianOrbitAngle(double guardianOrbitAngle) {
        this.guardianOrbitAngle = guardianOrbitAngle;
    }

    public void initializePlayer(UUID uuid) {
        roundsSurvived.put(uuid, 0);
        perfectBuilds.put(uuid, 0);
        lastScore.put(uuid, 0.0);
    }
}
