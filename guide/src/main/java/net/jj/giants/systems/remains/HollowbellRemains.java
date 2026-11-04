package net.jj.giants.systems.remains;

/**
 * Hollowbell, the bell that floats: he falls to the ground and his copper bell settles tilted, its rim dug in, the
 * crown still on top; his eight arms and his bone strands fall out round him and lie over the ground like a
 * stranded jellyfish's. The bell is his bone: the copper stays (greening further), the glass of the dome, the
 * glowing spots and the pods dims and shatters, a little more each stage, until shards stand in the copper frame.
 */
final class HollowbellRemains extends Design {
    HollowbellRemains() {
        super("hollowbell", "hollowbell", "/hollowbell/hollowbell_model.bin", "/hollowbell/hollowbell_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // every arm and strand swings out from the rim and lies along the ground
        for (int b = 0; b < m.names.length; b++) {
            String n = m.names[b];
            boolean arm = n.matches("arm_\\d+_0"), strand = n.matches("strand_\\d+_0");
            if (!arm && !strand) continue;
            float[] pv = m.pivot[b];
            double len = Math.hypot(pv[0], pv[2]);
            if (len < 1e-3) continue;
            double rx = pv[0] / len, rz = pv[2] / len;
            // about the rim's own way round, so down swings out
            double[] tan = {-rz, 0, rx};
            double[] t = Pose.dir(Pose.rot(tan[0], tan[1], tan[2], 90), 0, -1, 0);
            double sign = t[0] * rx + t[2] * rz > 0 ? 1 : -1;
            int k = Integer.parseInt(n.split("_")[1]);
            double jitter = (Noise.hash(k, arm ? 1 : 2, 3, 11) - 0.5) * (arm ? 20 : 34);
            p.turn(b, 0, 1, 0, jitter);
            p.turn(b, tan[0], 0, tan[2], sign * (arm ? 78 : 86));
        }
        // each strand lies in a long easy curve
        for (int b = 0; b < m.names.length; b++) {
            String n = m.names[b];
            if (!n.matches("strand_\\d+_[1-9]\\d*")) continue;
            String[] q = n.split("_");
            int k = Integer.parseInt(q[1]);
            double bend = (Noise.hash(k, 7, 1, 13) - 0.5) * 26;
            p.turn(b, 0, 1, 0, bend);
        }
        p.drape(n -> n.startsWith("strand") || n.startsWith("pod"), 0.92);
        p.drape(n -> n.startsWith("arm"), 0.75);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        // down to the ground, tilted forward, the rim dug in
        double[] turn = Pose.then(turnAbout(1, 0, 0, 20, 0, 134, 0), turnAbout(0, 0, 1, 6, 0, 134, 0));
        return settle(m, p, turn, of(m, n -> n.startsWith("rim") || n.startsWith("bell")), 9);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        return Carcass.HARD;
    }

    static boolean glass(String b) {
        return b.endsWith("glass") || b.endsWith("_pane");
    }

    private static final double[] SHATTER = {0, 0.22, 0.45, 0.62, 0.66}, DENT = {0, 0.03, 0.07, 0.12, 0.13};

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        if (light(block)) {
            // his glowing spots and crown: dark glass now, then breaking
            if (stage > 0 && fine < SHATTER[stage]) return "minecraft:air";
            return switch (b) {
                case "minecraft:verdant_froglight" -> "minecraft:lime_stained_glass";
                case "minecraft:glowstone", "minecraft:ochre_froglight" -> "minecraft:yellow_stained_glass";
                case "minecraft:sea_lantern" -> "minecraft:white_stained_glass";
                case "minecraft:shroomlight" -> "minecraft:orange_stained_glass";
                default -> lightsOut(block);
            };
        }
        if (glass(b)) {
            if (stage > 0 && fine * 0.5 + n * 0.5 < SHATTER[stage]) return "minecraft:air";
            if (stage >= 2 && fine < 0.12 && !b.equals("minecraft:tinted_glass")) return "minecraft:tinted_glass";
            return block;
        }
        if (b.contains("copper")) {
            if (stage > 0 && n < DENT[stage]) return "minecraft:air";
            if (stage >= 1 && b.contains("weathered") && fine < 0.2 * stage)
                return block.replace("weathered", "oxidized");
            return block;
        }
        return lightsOut(block);
    }

    @Override
    void finish(Carcass c, int[][] st) {
        // broken glass: what is left beside a hole is a shard (a pane of the same glass)
        for (int k = 2; k < Carcass.STAGES; k++) {
            int[] s = st[k];
            int[] out = s.clone();
            for (int i = 0; i < c.n; i++) {
                if (s[i] == 0) continue;
                String b = Blocks.base(c.name(s[i]));
                if (!b.endsWith("stained_glass") && !b.equals("minecraft:glass")) continue;
                int x = c.cx[i], y = c.cy[i], z = c.cz[i];
                int open = 0;
                int[][] dd = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
                for (int[] d : dd) {
                    int j = c.find(x + d[0], y + d[1], z + d[2]);
                    if (j < 0 || s[j] == 0) open++;
                }
                if (open >= 2 && Noise.hash(x, y, z, 31) < 0.7) out[i] = c.mat(b + "_pane");
            }
            System.arraycopy(out, 0, s, 0, s.length);
        }
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        // his bones are his bell and his strands (his own model); nothing drawn
    }
}
