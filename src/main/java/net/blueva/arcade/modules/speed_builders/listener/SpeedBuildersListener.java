package net.blueva.arcade.modules.speed_builders.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.speed_builders.game.SpeedBuildersGame;
import net.blueva.arcade.modules.speed_builders.state.ArenaState;
import net.blueva.arcade.modules.speed_builders.state.BuildArea;
import net.blueva.arcade.modules.speed_builders.state.BuildPlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SpeedBuildersListener implements Listener {

    private final SpeedBuildersGame game;

    public SpeedBuildersListener(SpeedBuildersGame game) {
        this.game = game;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null) return;

        if (!context.getAlivePlayers().contains(player) || !shouldKeepInsidePlot(state)) {
            return;
        }

        BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
        if (plot == null || isInsidePlotHorizontally(event.getTo(), plot)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area != null) {
            if (!area.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                sendCantBuildHere(player, context);
                return;
            }
            if (isFloorLayer(event.getBlock().getLocation(), area)) {
                event.setCancelled(true);
                return;
            }
            breakIntoInventory(player, event.getBlock());
            event.setDropItems(false);
            event.setCancelled(true);
            scheduleEvaluate(context, player);
            return;
        } else {
            BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
            if (plot != null && !plot.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                sendCantBuildHere(player, context);
                return;
            }
        }
        breakIntoInventory(player, event.getBlock());
        event.setDropItems(false);
        event.setCancelled(true);
        scheduleEvaluate(context, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area != null && !area.isInside(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            sendCantBuildHere(player, context);
            return;
        }
        if (area != null && isFloorLayer(event.getClickedBlock().getLocation(), area)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        breakIntoInventory(player, event.getClickedBlock());
        scheduleEvaluate(context, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        BuildArea area = state.getPlayerBuildArea(player.getUniqueId());
        if (area != null) {
            if (!area.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                sendCantBuildHere(player, context);
                return;
            }
            event.setCancelled(false);
        } else {
            BuildPlot plot = state.getPlayerPlot(player.getUniqueId());
            if (plot != null && !plot.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                sendCantBuildHere(player, context);
                return;
            }
            event.setCancelled(false);
        }

        context.getSchedulerAPI().runLater(
                "arena_" + context.getArenaId() + "_speed_builders_eval_" + player.getUniqueId(),
                () -> game.evaluateBuild(player),
                1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().hasMetadata("SPEEDBUILDERS_EXPLOSION_SHARD")) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        event.setCancelled(true);
    }

    private void sendCantBuildHere(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        String msg = game.getModuleConfig().getStringFrom("language.yml", "messages.cant_build_here");
        if (msg != null) {
            context.getMessagesAPI().sendRaw(player, msg);
        }
    }

    private void breakIntoInventory(Player player, Block block) {
        if (block == null || block.getType().isAir()) {
            return;
        }
        Material material = itemMaterialFor(block.getType());
        block.setType(Material.AIR, false);
        if (material == null || !material.isItem()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, 1));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.updateInventory();
    }

    private Material itemMaterialFor(Material material) {
        return switch (material) {
            case ACACIA_WALL_SIGN -> Material.ACACIA_SIGN;
            case BIRCH_WALL_SIGN -> Material.BIRCH_SIGN;
            case CRIMSON_WALL_SIGN -> Material.CRIMSON_SIGN;
            case DARK_OAK_WALL_SIGN -> Material.DARK_OAK_SIGN;
            case JUNGLE_WALL_SIGN -> Material.JUNGLE_SIGN;
            case OAK_WALL_SIGN -> Material.OAK_SIGN;
            case SPRUCE_WALL_SIGN -> Material.SPRUCE_SIGN;
            case WARPED_WALL_SIGN -> Material.WARPED_SIGN;
            case WALL_TORCH -> Material.TORCH;
            default -> material.isItem() ? material : null;
        };
    }

    private void scheduleEvaluate(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  Player player) {
        context.getSchedulerAPI().runLater(
                "arena_" + context.getArenaId() + "_speed_builders_eval_" + player.getUniqueId(),
                () -> game.evaluateBuild(player),
                1L);
    }

    private boolean isFloorLayer(Location location, BuildArea area) {
        if (location == null || area == null || area.getMin() == null || area.getMax() == null) {
            return false;
        }
        int floorY = Math.min(area.getMin().getBlockY(), area.getMax().getBlockY());
        return location.getBlockY() == floorY;
    }

    private boolean shouldKeepInsidePlot(ArenaState state) {
        return state.getPhase() == net.blueva.arcade.modules.speed_builders.state.GamePhase.SHOWCASE
                || state.getPhase() == net.blueva.arcade.modules.speed_builders.state.GamePhase.BUILDING;
    }

    private boolean isInsidePlotHorizontally(Location location, BuildPlot plot) {
        if (location == null || plot == null || plot.getMin() == null || plot.getMax() == null) {
            return true;
        }
        if (plot.getMin().getWorld() != null && location.getWorld() != null
                && !plot.getMin().getWorld().equals(location.getWorld())) {
            return false;
        }
        int margin = Math.max(0, game.getModuleConfig().getInt("gameplay.plot_border_margin", 6));
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int minX = Math.min(plot.getMin().getBlockX(), plot.getMax().getBlockX()) - margin;
        int maxX = Math.max(plot.getMin().getBlockX(), plot.getMax().getBlockX()) + margin;
        int minZ = Math.min(plot.getMin().getBlockZ(), plot.getMax().getBlockZ()) - margin;
        int maxZ = Math.max(plot.getMin().getBlockZ(), plot.getMax().getBlockZ()) + margin;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
