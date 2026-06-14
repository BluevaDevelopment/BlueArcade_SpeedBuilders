package net.blueva.arcade.modules.speed_builders.state;

import java.util.List;

public class StructureTemplate {

    private String id;
    private String name;
    private Center center;
    private List<Voxel> voxels;
    private List<Creature> creatures;

    public StructureTemplate() {
    }

    public StructureTemplate(String id, String name, Center center, List<Voxel> voxels, List<Creature> creatures) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.voxels = voxels;
        this.creatures = creatures;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Center getCenter() {
        return center;
    }

    public List<Voxel> getVoxels() {
        return voxels;
    }

    public List<Creature> getCreatures() {
        return creatures;
    }

    public static class Center {
        public int cx;
        public int cy;
        public int cz;

        public Center() {
        }

        public Center(int cx, int cy, int cz) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }
    }

    public static class Voxel {
        public int dx;
        public int dy;
        public int dz;
        public String mat;

        public Voxel() {
        }

        public Voxel(int dx, int dy, int dz, String mat) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.mat = mat;
        }
    }

    public static class Creature {
        public String mob;
        public int dx;
        public int dy;
        public int dz;

        public Creature() {
        }

        public Creature(String mob, int dx, int dy, int dz) {
            this.mob = mob;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
