package net.jj.giants.systems.remains;

/**
 * Wreckback, the crab with a wrecked ship for a shell: he settles where he fell, listing to port, legs splayed, claws
 * open on the ground, eye stalks and feelers drooping, the main mast snapped and hanging over the side. A crab's
 * skeleton is his shell: it stays, bleaching from red to pale as he rots, a few plates broken out; the soft pink body
 * in the ship and the tendrils rot away; the ship itself goes on rotting (planks gone, moss, sails in tatters).
 */
final class WreckbackRemains extends Design {
    WreckbackRemains() {
        super("wreckback", "wreckback", "/wreckback/wreckback_model.bin", "/wreckback/wreckback_rig.json");
    }

    static boolean crab(String n) {
        return n.equals("body") || n.startsWith("eye") || n.startsWith("antenn") || n.startsWith("mouth")
                || n.startsWith("claw") || n.startsWith("leg");
    }

    static boolean ship(String n) {
        return n.startsWith("hull") || n.startsWith("deck") || n.startsWith("stern") || n.startsWith("mast")
                || n.startsWith("cannon") || n.startsWith("anchor");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // eye stalks and feelers droop
        p.turn("eye_l", 1, 0, 0, 30).turn("eye_r", 1, 0, 0, 34);
        p.turn("antenna_l_0", 1, 0, 0, 30).turn("antenna_r_0", 1, 0, 0, 26);
        p.turn("antenna_l_1", 1, 0, 0, 28).turn("antenna_r_1", 1, 0, 0, 32);
        p.drape(n -> n.startsWith("antenna") && !n.endsWith("_0"), 0.6);
        // the big claw falls open on the ground, the small one turns out
        p.turn("claw_big_arm", 0, 1, 0, -12).turn("claw_big_finger", 0, 1, 0, -22);
        p.turn("claw_small_arm", 0, 1, 0, 14).turn("claw_small_finger", 0, 1, 0, 18);
        // the small legs curl
        p.turnAll(n -> n.matches("leg_l[34]_upper"), 0, 0, 1, 25);
        p.turnAll(n -> n.matches("leg_r[34]_upper"), 0, 0, 1, -25);
        // the main mast snaps a third of the way up and hangs over the port side; the mizzen leans
        int main = m.bone("mast_main");
        p.snap(main, 0, 1, 0, 118, 0, 118, -36, 0, 0, 1, -128);
        p.turn("mast_mizzen", 1, 0, 0, -14);
        p.turn("mast_fore", 0, 0, 1, 8);
        // the tendrils hang slack to the ground
        p.drape(n -> n.startsWith("tendril"), 0.85);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        double[] turn = Pose.then(turnAbout(0, 0, 1, -9, 0, 40, 0), turnAbout(1, 0, 0, 3, 0, 40, 0));
        return settle(m, p, turn, of(m, n -> n.equals("body") || n.startsWith("hull")), 5);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        if ((n.equals("eye_l") || n.equals("eye_r") || n.startsWith("eye_extra") || n.startsWith("eye_deck"))
                && (block.contains("black_concrete") || block.contains("sea_lantern"))) return Carcass.EYE;
        if (crab(n) || ship(n)) return Carcass.HARD;
        return Carcass.FLESH;
    }

    @Override
    String dead(String block) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:sea_lantern")) return "minecraft:light_gray_concrete";
        return lightsOut(block);
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (r < 0.3) return "minecraft:brown_terracotta";
        if (r < 0.5) return "minecraft:pink_terracotta";
        if (r < 0.7) return "minecraft:mud";
        if (r < 0.85) return "minecraft:packed_mud";
        return "minecraft:brown_mushroom_block";
    }

    /** how far the shell has bleached and the ship rotted, by stage (0 fresh .. 4 left for good) */
    private static final double[] BLEACH = {0, 0.25, 0.55, 0.85, 0.9}, HOLES = {0, 0.03, 0.07, 0.11, 0.12},
            PLANK = {0, 0.06, 0.12, 0.18, 0.2}, SAIL = {0, 0.25, 0.45, 0.6, 0.6}, MOSS = {0, 0.05, 0.12, 0.2, 0.22};

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        String l = lightsOut(block);
        if (stage == 0) return l;
        if (b.endsWith("_wool")) return n < SAIL[stage] ? "minecraft:air" : block;
        if (b.endsWith("glass") || b.endsWith("_pane")) return "minecraft:air";
        boolean wood = b.endsWith("_planks") || b.endsWith("_log") || b.endsWith("_slab") || b.endsWith("_stairs")
                || b.endsWith("_fence") || b.endsWith("_wood");
        if (wood) {
            if (n < PLANK[stage]) return "minecraft:air";
            if (n > 1 - MOSS[stage]) return fine < 0.5 ? "minecraft:moss_block" : "minecraft:mossy_cobblestone";
            if (fine < 0.1 * stage && b.endsWith("_planks")) return "minecraft:stripped_dark_oak_log[axis=y]";
            return l;
        }
        // the crab's own shell: bleaching, a few plates broken out
        if (n < HOLES[stage]) return "minecraft:air";
        if (fine < BLEACH[stage]) {
            if (b.equals("minecraft:red_terracotta") || b.equals("minecraft:orange_terracotta"))
                return fine < BLEACH[stage] * 0.5 ? "minecraft:white_terracotta" : "minecraft:pink_terracotta";
            if (b.equals("minecraft:brown_terracotta") || b.equals("minecraft:red_nether_bricks"))
                return fine < BLEACH[stage] * 0.5 ? "minecraft:terracotta" : "minecraft:white_terracotta";
            if (b.equals("minecraft:white_terracotta")) return "minecraft:calcite";
        }
        return l;
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        // a crab's bones are his shell (his model, kept): nothing drawn inside but frayed rigging down from the masts
        int[] masts = {m.bone("mast_fore"), m.bone("mast_mizzen")};
        for (int mb : masts) {
            if (mb < 0) continue;
            float[] bx = m.box(mb);
            double[] top = p.at(mb, (bx[0] + bx[3]) / 2, bx[4] - 6, (bx[2] + bx[5]) / 2);
            for (int sd = -1; sd <= 1; sd += 2) {
                double[] rail = p.at(mb, sd * 26, 66, (bx[2] + bx[5]) / 2 + 10);
                rope(c, c.w(top), c.w(rail));
            }
        }
    }

    /** rigging: a chain of blocks between two world spots, each turned along the line */
    static void rope(Carcass c, double[] a, double[] b) {
        double len = Carcass.dist(a, b);
        int n = (int) Math.ceil(len * 1.5);
        for (int i = 0; i <= n; i++) {
            double[] q = Carcass.lerp(a, b, i / (double) n);
            int x = Carcass.floor(q[0]), y = Carcass.floor(q[1]), z = Carcass.floor(q[2]);
            if (c.find(x, y, z) >= 0 && c.bone[c.find(x, y, z)] == 0 && c.model[c.find(x, y, z)] != 0) continue;
            double dx = Math.abs(b[0] - a[0]), dy = Math.abs(b[1] - a[1]), dz = Math.abs(b[2] - a[2]);
            String ax = dy >= dx && dy >= dz ? "y" : dx >= dz ? "x" : "z";
            String ch = "minecraft:chain[axis=" + ax + "]";
            // the rigging frays: gone in places as the wreck rots
            boolean frays = Noise.hash(x, y, z, 77) < 0.5;
            c.extra(x, y, z, ch, ch, frays ? null : ch, frays ? null : ch, frays ? null : ch);
        }
    }
}
