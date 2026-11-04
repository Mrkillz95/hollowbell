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

    private static final double[] SHATTER = {0, 0.25, 0.55, 0.85, 0.9}, DENT = {0, 0.03, 0.08, 0.14, 0.16};

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
            // dents knocked through, the green of old copper spreading over him
            if (stage > 0 && n < DENT[stage]) return "minecraft:air";
            if (stage >= 2 && b.contains("weathered")) return block.replace("weathered", "oxidized");
            if (stage == 1 && b.contains("weathered") && fine < 0.4) return block.replace("weathered", "oxidized");
            return block;
        }
        return lightsOut(block);
    }

    @Override
    void finish(Carcass c, int[][] st) {
        GiantModel m = c.m;
        for (int i = 0; i < c.n; i++) {
            int o = c.owner[i];
            if (o < 0) continue;
            String n = m.names[o];
            if (n.equals("crown")) {
                // the crown topples off (it lies beside him from stage 2: bones)
                for (int k = 2; k < Carcass.STAGES; k++) st[k][i] = 0;
            } else if (n.startsWith("strand_")) {
                // strands break: past a point of its own each strand is gone, more of them as he rots
                String[] q = n.split("_");
                int s = Integer.parseInt(q[1]), j = Integer.parseInt(q[2]);
                double h = Noise.hash(s, 3, 5, 77);
                int cut = 1 + (int) (Noise.hash(s, 9, 1, 78) * 4);
                double gap = Noise.hash(c.cx[i], c.cy[i], c.cz[i], 79);
                for (int k = 1; k < Carcass.STAGES; k++) {
                    boolean gone = k == 1 ? h < 0.3 && j >= cut + 1
                            : k == 2 ? (h < 0.65 && j >= cut) || gap < 0.08
                            : (j >= cut && h < 0.8) || h < 0.2 || gap < 0.14;
                    if (gone) st[k][i] = 0;
                }
            } else if (n.startsWith("arm_")) {
                // the arms break apart at the last joints
                String[] q = n.split("_");
                int a = Integer.parseInt(q[1]), j = Integer.parseInt(q[2]);
                double h = Noise.hash(a, 4, 4, 81);
                for (int k = 2; k < Carcass.STAGES; k++)
                    if (j >= 3 && h < (k == 2 ? 0.4 : 0.75) || j >= 2 && k >= 3 && h < 0.35) st[k][i] = 0;
            }
        }
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

    private static final String[] SHARDS = {"minecraft:lime_stained_glass_pane", "minecraft:yellow_stained_glass_pane",
        "minecraft:white_stained_glass_pane", "minecraft:glass_pane", "minecraft:light_gray_stained_glass_pane",
        "minecraft:cyan_stained_glass_pane"};

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        // his bones are his bell and his strands (his own model); what is drawn is what falls off him:
        // the crown, toppled onto the ground beside the bell, and glass shards scattered round him
        double[] mid = c.w(new double[] {0, 134, 0});
        double[] fwd = c.wdir(new double[] {0, 0, 1});
        double rim = 88 * c.s;
        int crown = m.bone("crown");
        if (crown >= 0 && m.x[crown].length > 0) {
            float[] cc = m.centre(crown);
            double[] cw = c.w(p.at(crown, cc));
            double tx = cw[0] + fwd[0] * rim * 1.15, tz = cw[2] + fwd[2] * rim * 1.15;
            double ty = Math.max(1, 15 * c.s);
            java.util.Map<Long, String> placed = new java.util.HashMap<>();
            for (int i = 0; i < m.x[crown].length; i++) {
                // its own shape, tipped over on its side (up becomes forward)
                double dx = (m.x[crown][i] + 0.5 - cc[0]) * c.s, dy = (m.y[crown][i] + 0.5 - cc[1]) * c.s,
                        dz = (m.z[crown][i] + 0.5 - cc[2]) * c.s;
                int x = Carcass.floor(tx + dx), y = Carcass.floor(ty + dz), z = Carcass.floor(tz + dy);
                String b = hard(m.palette[m.pal[crown][i]], 0, 1, 1, x, y, z);
                if (b == null || b.endsWith(":air")) continue;
                placed.putIfAbsent(Carcass.key(x, y, z), b);
            }
            for (var e : placed.entrySet()) {
                long k = e.getKey();
                int x = (int) ((k >>> 42) & ((1 << 21) - 1)) - (1 << 20), y = (int) ((k >>> 21) & ((1 << 21) - 1))
                        - (1 << 20), z = (int) (k & ((1 << 21) - 1)) - (1 << 20);
                String b = e.getValue();
                String late = Noise.hash(x, y, z, 55) < 0.3 ? null : b;
                c.extra(x, y, z, null, null, b, late, late);
            }
        }
        // shards of his glass on the ground round him
        int n = (int) Math.max(12, 260 * c.s * c.s);
        for (int k = 0; k < n; k++) {
            double a = Noise.hash(k, 1, 2, 91) * Math.PI * 2, r = rim * (0.75 + Noise.hash(k, 3, 4, 92) * 0.7);
            int x = Carcass.floor(mid[0] + Math.cos(a) * r), z = Carcass.floor(mid[2] + Math.sin(a) * r);
            if (c.find(x, 0, z) >= 0) continue;
            String g = SHARDS[(int) (Noise.hash(k, 5, 6, 93) * SHARDS.length)];
            c.extra(x, 0, z, null, k % 3 == 0 ? g : null, g, g, g);
        }
    }
}
