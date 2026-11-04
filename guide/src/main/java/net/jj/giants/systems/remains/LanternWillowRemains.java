package net.jj.giants.systems.remains;

/**
 * The Lantern Willow dies as a tree does, standing, but falling: he leans hard over to his right, his root legs on the
 * high side torn up off the ground, his strands still hanging straight down. His wood is his bone: the bark peels
 * (stripped wood) and rot holes open in the hollow trunk; his two great eyes and the small ones go dark and become
 * empty sockets; the red of his mouth rots away round the bone teeth. His leaves wither to bare twigs (mangrove
 * roots), whole strands drop off, and the lanterns on them break, leaving cracked orange glass; the angler's lantern
 * over his mouth hangs broken on its stalk.
 */
final class LanternWillowRemains extends Design {
    static final double LEAN = 30;

    LanternWillowRemains() {
        super("lanternwillow", "lanternwillow", "/lanternwillow/lanternwillow_model.bin",
                "/lanternwillow/lanternwillow_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // the strands keep hanging straight down when he leans
        p.turnAll(n -> n.matches("strand_\\d+_0"), 0, 0, 1, -LEAN);
        // the jaw hangs open
        p.turn("jaw", 1, 0, 0, 10);
        // the roots on the high side lift and curl
        for (int b = 0; b < m.names.length; b++) {
            String n = m.names[b];
            if (!n.matches("root_\\d+_0")) continue;
            float[] pv = m.pivot[b];
            if (pv[0] > 10) p.turn(b, 0, 0, 1, 14);
        }
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        double[] turn = Pose.then(turnAbout(0, 0, 1, LEAN, -20, 0, 0), turnAbout(1, 0, 0, -4, 0, 0, 0));
        return settle(m, p, turn, of(m, n -> n.equals("trunk_0") || n.equals("trunk_1")), 4);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        if (n.startsWith("eye")) return Carcass.EYE;
        String b = Blocks.base(block);
        if (b.contains("red_nether_bricks") || b.contains("nether_wart") || b.contains("crimson")
                || b.contains("honey")) return Carcass.FLESH;
        return Carcass.HARD;
    }

    @Override
    String dead(String block) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:pearlescent_froglight")) return "minecraft:white_terracotta";
        return lightsOut(block);
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (r < 0.3) return "minecraft:nether_wart_block";
        if (r < 0.5) return "minecraft:brown_terracotta";
        if (r < 0.7) return "minecraft:mud";
        if (r < 0.85) return "minecraft:brown_mushroom_block";
        return "minecraft:rooted_dirt";
    }

    private static final double[] FALL = {0, 0.35, 0.75, 0.95, 0.96}, STRAND = {0, 0.25, 0.6, 0.88, 0.9},
            PEEL = {0, 0.1, 0.25, 0.4, 0.42}, HOLE = {0, 0, 0.04, 0.08, 0.09};

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        boolean leaves = b.endsWith("_leaves");
        if (leaves) {
            if (stage == 0) return block;
            // a whole hanging strand (one column) drops at once; the rest withers to bare twigs
            double col = Noise.hash(x, 0, z, 404);
            if (col < STRAND[stage]) return "minecraft:air";
            // what is left hangs on in whole strands
            if (stage >= 3) return fine < 0.25 ? "minecraft:air" : fine < 0.92 ? "minecraft:mangrove_roots"
                    : "minecraft:hanging_roots";
            if (fine < FALL[stage] * 0.7) return "minecraft:air";
            if (stage >= 2) return fine < 0.93 ? "minecraft:mangrove_roots" : "minecraft:hanging_roots";
            return fine < 0.5 ? "minecraft:spruce_leaves[persistent=true]" : block;
        }
        if (light(block)) {
            // the lanterns: dark, then broken (a few shards of orange glass)
            if (stage == 0) return lightsOut(block);
            if (stage == 1) return fine < 0.5 ? "minecraft:orange_stained_glass" : lightsOut(block);
            if (fine < (stage >= 3 ? 0.97 : 0.75)) return "minecraft:air";
            return fine < 0.95 ? "minecraft:orange_stained_glass" : "minecraft:chain[axis=y]";
        }
        if (stage == 0) return lightsOut(block);
        if (b.endsWith("_log") || b.endsWith("_wood") || b.equals("minecraft:brown_terracotta")
                || b.equals("minecraft:black_terracotta")) {
            if (n < HOLE[stage]) return "minecraft:air";
            if (fine < PEEL[stage]) {
                if (b.equals("minecraft:dark_oak_log")) return block.replace("dark_oak_log", "stripped_dark_oak_log");
                if (b.equals("minecraft:mangrove_log")) return block.replace("mangrove_log", "stripped_mangrove_log");
                if (b.equals("minecraft:spruce_log")) return block.replace("spruce_log", "stripped_spruce_log");
                if (b.equals("minecraft:brown_terracotta")) return "minecraft:dead_bubble_coral_block";
            }
            if (stage >= 3 && fine > 0.94) return "minecraft:moss_block";
        }
        if (b.equals("minecraft:sculk") && stage >= 2) return "minecraft:black_terracotta";
        return lightsOut(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        // the angler's lantern hangs broken from its stalk: a chain and its cage
        com.google.gson.JsonArray lures = m.rig.getAsJsonArray("lures");
        if (lures == null) return;
        for (var e : lures) {
            var o = e.getAsJsonObject();
            if (!o.get("angler").getAsBoolean()) continue;
            int b = o.get("bone").getAsInt();
            var ce = o.getAsJsonArray("centre");
            double r = o.get("radius").getAsDouble();
            double[] cen = p.at(b, ce.get(0).getAsDouble(), ce.get(1).getAsDouble(), ce.get(2).getAsDouble());
            double[] w = c.w(cen);
            int x = Carcass.floor(w[0]), y = Carcass.floor(w[1]), z = Carcass.floor(w[2]);
            int R = Math.max(1, (int) Math.round(r * c.s * 0.75));
            // the cage: iron bars round it, left once the glass has gone
            for (int dy = -R; dy <= R; dy++)
                for (int dx = -R; dx <= R; dx++)
                    for (int dz = -R; dz <= R; dz++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > R * R || d2 < (R - 1) * (R - 1)) continue;
                        if (Math.floorMod(dx + dz, 3) != 0 && Math.abs(dy) != R - 1) continue;
                        c.put(x + dx, y + dy, z + dz, "minecraft:iron_bars");
                    }
            for (int dy = R; dy < R + Math.max(2, (int) (14 * c.s)); dy++)
                c.put(x, y + dy, z, "minecraft:chain[axis=y]");
        }
    }
}
