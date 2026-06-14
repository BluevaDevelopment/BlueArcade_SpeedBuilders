package net.blueva.arcade.modules.speed_builders.game;

import com.google.gson.Gson;
import net.blueva.arcade.modules.speed_builders.state.StructureTemplate;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public class StructureService {

    private final Class<?> resourceAnchor;
    private final File structuresFolder;
    private final Gson gson = new Gson();
    private final Random random = new Random();
    private final Logger logger;

    private final List<StructureTemplate> structures = new ArrayList<>();
    private final List<Integer> pickOrder = new ArrayList<>();
    private int pickIndex = 0;

    public StructureService(File moduleDataFolder, Class<?> resourceAnchor, Logger logger) {
        this.resourceAnchor = resourceAnchor;
        this.logger = logger;
        this.structuresFolder = new File(moduleDataFolder, "structures");
        if (!structuresFolder.exists()) {
            structuresFolder.mkdirs();
        }
        loadStructures();
    }

    public void loadStructures() {
        structures.clear();
        pickOrder.clear();
        pickIndex = 0;

        Map<String, StructureTemplate> loaded = new LinkedHashMap<>();


        Set<String> defaultIds = listResourceFiles("files/structures");
        for (String resourcePath : defaultIds) {
            try (InputStream in = resourceAnchor.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) continue;
                StructureTemplate structure = gson.fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8),
                        StructureTemplate.class);
                if (structure != null && structure.getId() != null) {
                    loaded.put(structure.getId(), structure);
                }
            } catch (Exception e) {
                logger.warning("[SpeedBuilders] Failed to load default structure from JAR: " + resourcePath + " - " + e.getMessage());
            }
        }


        File[] files = structuresFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                    StructureTemplate structure = gson.fromJson(reader, StructureTemplate.class);
                    if (structure != null && structure.getId() != null) {
                        loaded.put(structure.getId(), structure);
                    }
                } catch (Exception e) {
                    logger.warning("[SpeedBuilders] Failed to load structure: " + file.getName() + " - " + e.getMessage());
                }
            }
        }

        structures.addAll(loaded.values());
        reshuffleOrder();
    }

    private Set<String> listResourceFiles(String basePath) {
        Set<String> result = new HashSet<>();
        try {

            java.net.URL url = resourceAnchor.getClassLoader().getResource(basePath);
            if (url != null) {
                java.nio.file.Path path = java.nio.file.Paths.get(url.toURI());
                if (java.nio.file.Files.isDirectory(path)) {
                    try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(path)) {
                        stream.filter(p -> p.toString().endsWith(".json"))
                                .forEach(p -> result.add(basePath + "/" + p.getFileName().toString()));
                    }
                }
            }
        } catch (Exception e) {


            try {
                java.net.URL jarUrl = resourceAnchor.getProtectionDomain().getCodeSource().getLocation();
                if (jarUrl != null) {
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new File(jarUrl.toURI()))) {
                        jar.stream()
                                .filter(entry -> entry.getName().startsWith(basePath + "/") && entry.getName().endsWith(".json"))
                                .forEach(entry -> result.add(entry.getName()));
                    }
                }
            } catch (Exception ex) {
                logger.warning("[SpeedBuilders] Could not list default structures from JAR: " + ex.getMessage());
            }
        }
        return result;
    }

    public List<StructureTemplate> getStructures() {
        return Collections.unmodifiableList(structures);
    }

    public boolean hasStructures() {
        return !structures.isEmpty();
    }

    public void reshuffleOrder() {
        pickOrder.clear();
        for (int i = 0; i < structures.size(); i++) {
            pickOrder.add(i);
        }
        Collections.shuffle(pickOrder, random);
        pickIndex = 0;
    }

    public StructureTemplate pickNextStructure() {
        if (structures.isEmpty()) {
            return null;
        }
        if (structures.size() == 1) {
            return structures.get(0);
        }
        if (pickOrder.size() != structures.size() || pickIndex >= pickOrder.size()) {
            reshuffleOrder();
        }
        int idx = pickOrder.get(pickIndex++);
        return structures.get(idx);
    }




    public static int[] getStructureSize(StructureTemplate structure) {
        List<StructureTemplate.Voxel> voxels = structure.getVoxels();
        if (voxels == null || voxels.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureTemplate.Voxel v : voxels) {
            minX = Math.min(minX, v.dx);
            maxX = Math.max(maxX, v.dx);
            minY = Math.min(minY, v.dy);
            maxY = Math.max(maxY, v.dy);
            minZ = Math.min(minZ, v.dz);
            maxZ = Math.max(maxZ, v.dz);
        }
        return new int[]{maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }





    public void filterStructuresByMaxSize(int maxWidth, int maxHeight, int maxLength) {
        int before = structures.size();
        structures.removeIf(s -> {
            int[] size = getStructureSize(s);
            return size[0] > maxWidth || size[1] > maxHeight || size[2] > maxLength;
        });
        int removed = before - structures.size();
        if (removed > 0) {
            logger.info("[SpeedBuilders] Filtered " + removed + " structures that exceed max size "
                    + maxWidth + "x" + maxHeight + "x" + maxLength);
        }
        pickOrder.clear();
        pickIndex = 0;
        if (!structures.isEmpty()) {
            reshuffleOrder();
        }
    }

    public File getStructuresFolder() {
        return structuresFolder;
    }

    public Gson getGson() {
        return gson;
    }
}
