package net.blueva.arcade.modules.speed_builders.setup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SpeedBuildersSetup implements GameSetupHandler {

    private static final int RECOMMENDED_BUILD_AREA_WIDTH = 7;
    private static final int RECOMMENDED_BUILD_AREA_HEIGHT = 10;
    private static final int RECOMMENDED_BUILD_AREA_LENGTH = 7;

    private final ModuleConfigAPI moduleConfig;
    private final File dataFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SpeedBuildersPlotSetup plotSetup;
    private final SpeedBuildersShowcaseSetup showcaseSetup;

    public SpeedBuildersSetup(ModuleConfigAPI moduleConfig, File dataFolder) {
        this.moduleConfig = moduleConfig;
        this.dataFolder = dataFolder;
        this.plotSetup = new SpeedBuildersPlotSetup(moduleConfig);
        this.showcaseSetup = new SpeedBuildersShowcaseSetup(moduleConfig);
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);
        if ("plot".equalsIgnoreCase(subcommand)) {
            return handlePlot(context);
        }
        if ("build_area".equalsIgnoreCase(subcommand)) {
            return handleBuildArea(context);
        }
        if ("showcase".equalsIgnoreCase(subcommand)) {
            return handleShowcase(context);
        }
        if ("structure".equalsIgnoreCase(subcommand)) {
            return handleStructure(context);
        }
        return false;
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);

        if ("plot".equalsIgnoreCase(subcommand)) {
            if (context.getRelativeArgIndex() == 0) {
                return TabCompleteResult.of("add", "set", "remove", "spawn", "build_area");
            }
            String handlerArg0 = context.getArg(context.getStartIndex());
            if ("spawn".equalsIgnoreCase(handlerArg0)) {
                if (context.getRelativeArgIndex() == 1) {
                    return TabCompleteResult.of("set");
                }
                return TabCompleteResult.empty();
            }
            if ("build_area".equalsIgnoreCase(handlerArg0)) {
                if (context.getRelativeArgIndex() == 1) {
                    return TabCompleteResult.of("set", "remove");
                }
                return TabCompleteResult.empty();
            }
            if (("set".equalsIgnoreCase(handlerArg0) || "remove".equalsIgnoreCase(handlerArg0))
                    && context.getRelativeArgIndex() == 1) {
                return TabCompleteResult.empty();
            }
        }

        if ("build_area".equalsIgnoreCase(subcommand)) {
            if (context.getRelativeArgIndex() == 0) {
                return TabCompleteResult.of("add", "set", "remove");
            }
            if (("set".equalsIgnoreCase(context.getArg(context.getStartIndex()))
                    || "remove".equalsIgnoreCase(context.getArg(context.getStartIndex())))
                    && context.getRelativeArgIndex() == 1) {
                return TabCompleteResult.empty();
            }
        }

        if ("showcase".equalsIgnoreCase(subcommand)) {
            if (context.getRelativeArgIndex() == 0) {
                return TabCompleteResult.of("set", "remove");
            }
            return TabCompleteResult.empty();
        }

        if ("structure".equalsIgnoreCase(subcommand)) {
            if (context.getRelativeArgIndex() == 0) {
                return TabCompleteResult.of("create", "remove", "list");
            }
            if ("remove".equalsIgnoreCase(context.getArg(context.getStartIndex()))
                    && context.getRelativeArgIndex() == 1) {
                return TabCompleteResult.empty();
            }
            if ("create".equalsIgnoreCase(context.getArg(context.getStartIndex()))
                    && context.getRelativeArgIndex() == 1) {
                return TabCompleteResult.empty();
            }
        }

        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return List.of("plot", "build_area", "showcase", "structure");
    }

    private boolean handlePlot(SetupContext<Player, CommandSender, Location> context) {
        return plotSetup.handlePlot(context);
    }

    private boolean handleBuildArea(SetupContext<Player, CommandSender, Location> context) {
        return plotSetup.handleBuildArea(context);
    }

    private boolean handleShowcase(SetupContext<Player, CommandSender, Location> context) {
        return showcaseSetup.handleShowcase(context);
    }

    private boolean handleStructure(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(0);
        if ("create".equalsIgnoreCase(action)) {
            return handleStructureCreate(context);
        }
        if ("remove".equalsIgnoreCase(action)) {
            return handleStructureRemove(context);
        }
        if ("list".equalsIgnoreCase(action)) {
            return handleStructureList(context);
        }
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("structure.usage"));
        return true;
    }

    private boolean handleStructureCreate(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        String idArg = context.getHandlerArg(1);
        if (idArg == null || idArg.isBlank()) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("structure.create.usage"));
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("structure.create.must_use_stick"));
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

        World world = pos1.getWorld() != null ? pos1.getWorld() : pos2.getWorld();
        if (world == null) {
            return true;
        }

        int cx = (minX + maxX) / 2;
        int cy = minY;
        int cz = (minZ + maxZ) / 2;

        List<StructureTemplate.Voxel> voxels = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR) {
                        continue;
                    }
                    String blockData = block.getBlockData().getAsString(false);
                    voxels.add(new StructureTemplate.Voxel(x - cx, y - cy, z - cz, blockData));
                }
            }
        }

        StructureTemplate structure = new StructureTemplate(
                idArg.toLowerCase().replace(" ", "_"),
                idArg.replace("_", " "),
                new StructureTemplate.Center(0, 0, 0),
                voxels,
                new ArrayList<>()
        );

        File moduleFolder = new File(dataFolder, "structures");
        if (!moduleFolder.exists()) {
            moduleFolder.mkdirs();
        }
        File outFile = new File(moduleFolder, idArg.toLowerCase().replace(" ", "_") + ".json");
        try (FileWriter writer = new FileWriter(outFile)) {
            gson.toJson(structure, writer);
        } catch (IOException e) {
            context.getMessagesAPI().sendRaw(player, "<red>Failed to save structure: " + e.getMessage() + "</red>");
            return true;
        }

        String msg = getSetupMessage("structure.create.saved")
                .replace("{id}", structure.getId())
                .replace("{voxels}", String.valueOf(voxels.size()));
        context.getMessagesAPI().sendRaw(player, msg);
        return true;
    }

    private boolean handleStructureRemove(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        String idArg = context.getHandlerArg(1);
        if (idArg == null || idArg.isBlank()) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("structure.remove.usage"));
            return true;
        }

        File moduleFolder = new File(dataFolder, "structures");
        File file = new File(moduleFolder, idArg.toLowerCase().replace(" ", "_") + ".json");
        if (!file.exists()) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("structure.remove.not_found"));
            return true;
        }

        if (file.delete()) {
            String msg = getSetupMessage("structure.remove.success").replace("{id}", idArg);
            context.getMessagesAPI().sendRaw(player, msg);
        } else {
            context.getMessagesAPI().sendRaw(player, "<red>Failed to delete structure file.</red>");
        }
        return true;
    }

    private boolean handleStructureList(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        File moduleFolder = new File(dataFolder, "structures");
        File[] files = moduleFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("structure.list.empty"));
            return true;
        }

        StringBuilder sb = new StringBuilder(getSetupMessage("structure.list.header"));
        for (File file : files) {
            String name = file.getName().replace(".json", "");
            sb.append("\n<gray>- </gray><yellow>").append(name).append("</yellow>");
        }
        context.getMessagesAPI().sendRaw(player, sb.toString());
        return true;
    }

    private String getSetupMessage(String key) {
        String message = moduleConfig.getStringFrom("language.yml", "setup_messages." + key);
        if (message == null) {
            return "";
        }
        return message;
    }

    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return context;
    }
}
