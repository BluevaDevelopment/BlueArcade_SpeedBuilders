package net.blueva.arcade.modules.speed_builders.setup;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.SetupContext;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class SpeedBuildersShowcaseSetup {

    private final ModuleConfigAPI moduleConfig;

    SpeedBuildersShowcaseSetup(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    boolean handleShowcase(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(0);
        if ("set".equalsIgnoreCase(action)) {
            return handleShowcaseSet(context);
        }
        if ("remove".equalsIgnoreCase(action)) {
            return handleShowcaseRemove(context);
        }
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("showcase.usage"));
        return true;
    }

    private boolean handleShowcaseSet(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("showcase.set.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int height = maxY - minY + 1;

        if (height < 10) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("showcase.set.too_shallow"));
            return true;
        }

        String basePath = "game.showcase_plot";
        context.getData().setRegionBounds(basePath + ".bounds", pos1, pos2);
        context.getData().setLocation(basePath + ".spawn", player.getLocation());
        context.getData().save();

        int x = Math.abs(maxX - minX) + 1;
        int y = height;
        int z = Math.abs(maxZ - minZ) + 1;
        int blocks = x * y * z;

        String msg = getSetupMessage("showcase.set.success")
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z));
        context.getMessagesAPI().sendRaw(player, msg);
        return true;
    }

    private boolean handleShowcaseRemove(SetupContext<Player, CommandSender, Location> context) {
        context.getData().remove("game.showcase_plot");
        context.getData().save();
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("showcase.remove.success"));
        return true;
    }

    private String getSetupMessage(String key) {
        String message = moduleConfig.getStringFrom("language.yml", "setup_messages." + key);
        if (message == null) {
            return "";
        }
        return message;
    }
}
