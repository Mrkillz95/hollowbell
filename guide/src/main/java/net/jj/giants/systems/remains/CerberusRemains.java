package net.jj.giants.systems.remains;

import java.util.function.IntPredicate;

/**
 * Cerberus, the stone hound of fire and ice: he lies on his belly, forelegs reaching forward, hind legs splayed, his
 * three necks stretched out so the three heads rest on the ground side by side. His flesh is stone (fire side
 * netherrack and blackstone, ice side packed ice and snow); it crumbles and melts away. His skeleton: one spine and
 * ribcage, three necks of vertebrae, three skulls (the fire skull charred with magma in its sockets, the ice skull
 * frosted with blue ice in its sockets, the middle one plain stone-grey), four legs, a long tail; his teeth stay.
 */
final class CerberusRemains extends Design {
    CerberusRemains() {
        super("cerberus", "fire_ice_cerberus", "/fire_ice_cerberus/cerberus_model.bin",
                "/fire_ice_cerberus/cerberus_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // forelegs fold forward under the chest and reach out (he faces -z)
        for (String s : new String[] {"fire", "ice"}) {
            double side = s.equals("fire") ? 1 : -1;
            p.turn("fore_" + s + "_upper", 1, 0, 0, 28).turn("fore_" + s + "_upper", 0, 0, 1, side * 12);
            p.turn("fore_" + s + "_lower", 1, 0, 0, 58);
            p.turn("fore_" + s + "_foot", 1, 0, 0, 10);
            // hind legs splay out to the side and back
            p.turn("hind_" + s + "_upper", 0, 0, 1, side * 62).turn("hind_" + s + "_upper", 1, 0, 0, -30);
            p.turn("hind_" + s + "_lower", 1, 0, 0, -40);
            p.turn("hind_" + s + "_foot", 1, 0, 0, 30);
            // the side necks bow down and out
            p.turn("neck_" + s + "_1", 1, 0, 0, -60).turn("neck_" + s + "_1", 0, 1, 0, side * 14);
            p.turn("neck_" + s + "_2", 1, 0, 0, -16);
            p.turn("head_" + s, 1, 0, 0, 50);
            p.turn("jaw_" + s, 1, 0, 0, -16);
        }
        p.turn("neck_mid_1", 1, 0, 0, -56).turn("neck_mid_2", 1, 0, 0, -18);
        p.turn("head_mid", 1, 0, 0, 48).turn("jaw_mid", 1, 0, 0, -12);
        // the tail lies out behind
        p.turn("tail_1", 1, 0, 0, -38).turn("tail_2", 1, 0, 0, 0).turn("tail_3", 1, 0, 0, 14).turn("tail_4", 1, 0, 0, 12);
        p.turn("tail_3", 0, 1, 0, 25).turn("tail_4", 0, 1, 0, 30);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        // face +z (turned round), his middle over the spot, his belly on the ground
        double[] turn = Pose.then(Pose.about(Pose.ident(), 0, 0, 0), yaw(180));
        turn[9] += 5;
        return settle(m, p, turn, of(m, n -> n.equals("body") || n.equals("chest") || n.equals("hips")), 3);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        boolean headish = n.startsWith("head") || n.startsWith("jaw");
        if (headish && (block.contains("quartz"))) return Carcass.HARD;
        if (n.startsWith("head") && (block.contains("redstone_lamp") || (n.endsWith("ice")
                && block.contains("sea_lantern")))) return Carcass.EYE;
        return Carcass.FLESH;
    }

    static boolean fire(String b) {
        return b.contains("netherrack") || b.contains("red_") || b.contains("crimson") || b.contains("magma")
                || b.contains("nether_wart") || b.contains("shroomlight") || b.contains("brown_terracotta");
    }

    static boolean ice(String b) {
        return b.contains("ice") || b.contains("snow") || b.contains("light_blue") || b.contains("sea_lantern")
                || b.contains("pearlescent");
    }

    @Override
    String dead(String block) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:sea_lantern")) return "minecraft:light_blue_terracotta";
        if (b.equals("minecraft:pearlescent_froglight")) return "minecraft:snow_block";
        return lightsOut(block);
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (fire(block)) {
            if (r < 0.3) return "minecraft:netherrack";
            if (r < 0.55) return "minecraft:blackstone";
            if (r < 0.75) return "minecraft:basalt[axis=y]";
            return stage >= 2 ? "minecraft:soul_soil" : "minecraft:red_terracotta";
        }
        if (ice(block)) {
            if (r < 0.4) return "minecraft:ice";
            if (r < 0.65) return "minecraft:snow_block";
            if (r < 0.85) return "minecraft:packed_ice";
            return "minecraft:light_gray_terracotta";
        }
        if (r < 0.35) return "minecraft:cobblestone";
        if (r < 0.55) return "minecraft:cobbled_deepslate";
        if (r < 0.7) return "minecraft:tuff";
        if (r < 0.85) return "minecraft:mossy_cobblestone";
        return "minecraft:andesite";
    }

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        return "minecraft:calcite";
    }

    @Override
    String token(String token, double t) {
        if (token.startsWith("@fire")) {
            if (t < 0.18) return "minecraft:blackstone";
            if (t < 0.28) return "minecraft:basalt[axis=y]";
            return super.token(token.replace("@fire", "@bone"), (t - 0.28) / 0.72);
        }
        if (token.startsWith("@ice")) {
            if (t < 0.16) return "minecraft:packed_ice";
            if (t < 0.24) return "minecraft:snow_block";
            return super.token(token.replace("@ice", "@bone"), (t - 0.24) / 0.76);
        }
        if (token.startsWith("@stone")) {
            if (t < 0.14) return "minecraft:tuff";
            return super.token(token.replace("@stone", "@bone"), (t - 0.14) / 0.86);
        }
        return super.token(token, t);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        int body = m.bone("body"), chest = m.bone("chest"), hips = m.bone("hips");
        IntPredicate torso = b -> b == body || b == chest || b == hips;
        // the spine from the hips over the back to the chest
        double[][] back = backLine(m, new int[] {body, chest, hips}, 98, -52, 16, 12, 12);
        double[][] spine = new double[back.length][];
        for (int i = 0; i < back.length; i++) {
            double z = back[i][2];
            int b = z > 38 ? hips : z < -2 ? chest : body;
            spine[i] = p.at(b, back[i][0] + 5, back[i][1], z);
        }
        double[] up = Pose.dir(p.abs[body], 0, 1, 0);
        c.ribs(spine, up, torso, 9, 3, 168, 1.2, 4, 7, "@stone:{axis}", "@stone:{axis}");
        // shoulders and pelvis
        for (String s : new String[] {"fire", "ice"}) {
            double[] fore = piv(m, p, "fore_" + s + "_upper"), hind = piv(m, p, "hind_" + s + "_upper");
            c.line(p.at(chest, 5, 150, -18), fore, 5, 3.5, "@stone:{axis}");
            c.line(p.at(hips, 5, 146, 72), hind, 5, 4, "@stone:{axis}");
            c.ball(hind, 6.5, "@stone");
            String tone = s.equals("fire") ? "@fire" : "@ice";
            double[][] fj = {fore, piv(m, p, "fore_" + s + "_lower"), piv(m, p, "fore_" + s + "_foot"),
                p.at(m.bone("fore_" + s + "_foot"), tip(m, m.bone("fore_" + s + "_foot")))};
            limb(c, fj, new double[] {5, 4, 3, 2.5}, tone + ":{axis}");
            double[][] hj = {hind, piv(m, p, "hind_" + s + "_lower"), piv(m, p, "hind_" + s + "_foot"),
                p.at(m.bone("hind_" + s + "_foot"), tip(m, m.bone("hind_" + s + "_foot")))};
            limb(c, hj, new double[] {6, 4.5, 3.2, 2.5}, tone + ":{axis}");
            claws(m, p, c, "fore_" + s + "_foot");
            claws(m, p, c, "hind_" + s + "_foot");
        }
        // the tail
        double[] last = spine[0];
        for (int t = 1; t <= 5; t++) {
            int b = m.bone("tail_" + t);
            double[] q = p.at(b, m.pivot[b]);
            c.line(last, q, 3.2 - t * 0.4, 3 - t * 0.4, "@stone:{axis}");
            c.ball(q, 3.8 - t * 0.4, "@stone");
            last = q;
        }
        int t5 = m.bone("tail_5");
        c.line(last, p.at(t5, tip(m, t5)), 1.5, 0.8, "@stone:{axis}");
        // three necks and three skulls
        for (String s : new String[] {"mid", "fire", "ice"}) {
            String tone = s.equals("fire") ? "@fire" : s.equals("ice") ? "@ice" : "@stone";
            int n1 = m.bone("neck_" + s + "_1"), n2 = m.bone("neck_" + s + "_2"), hd = m.bone("head_" + s);
            double[] a = p.at(n1, m.pivot[n1]), b = p.at(n2, m.pivot[n2]), h = p.at(hd, m.pivot[hd]);
            double[] base = p.at(chest, m.pivot[n1][0], m.pivot[n1][1] - 8, m.pivot[n1][2] + 14);
            double[][] chain = {base, a, Carcass.lerp(a, b, 0.5), b, Carcass.lerp(b, h, 0.5), h};
            for (int i = 1; i < chain.length; i++) {
                c.line(chain[i - 1], chain[i], 4.2, 4, tone + ":{axis}");
                c.ball(chain[i], 5.6, tone);
            }
            c.skull(new int[] {hd, m.bone("jaw_" + s)}, 0.78, 0.82, 0.95, blk -> blk.contains("redstone_lamp")
                    || (s.equals("ice") && blk.contains("sea_lantern")) ? null : tone);
            // the nose
            com.google.gson.JsonObject hk = m.rig.getAsJsonObject("heads").getAsJsonObject(s);
            com.google.gson.JsonArray nz = hk.getAsJsonArray("nose");
            c.socket(p.at(hd, nz.get(0).getAsDouble(), nz.get(1).getAsDouble() - 2, nz.get(2).getAsDouble() + 6), 4);
            // sockets where his eyes burned, and what is left in them
            for (double[] e : eyes(m, p, hd)) {
                c.socket(e, 7.5);
                double[] w = c.w(e);
                int x = Carcass.floor(w[0]), y = Carcass.floor(w[1]), z = Carcass.floor(w[2]);
                String in = s.equals("fire") ? "minecraft:magma_block" : s.equals("ice") ? "minecraft:blue_ice"
                        : "minecraft:obsidian";
                c.extra(x, y - 1, z, in, in, in, in, in);
            }
        }
    }

    /** where a head's eyes are: the middle of its eye blocks each side of the head's middle */
    static double[][] eyes(GiantModel m, Pose p, int hd) {
        if (m.names[hd].endsWith("fire")) {
            // his fire head has no eye blocks of its own: the ice head's eyes, mirrored across his middle (x 5)
            int ice = m.bone("head_ice");
            double[][] o = new double[2][];
            int k = 0;
            for (double[] e : eyesAtRest(m, ice)) o[k++] = p.at(hd, 10 - e[0], e[1], e[2]);
            return o;
        }
        double[][] r = eyesAtRest(m, hd);
        double[][] o = new double[r.length][];
        for (int i = 0; i < r.length; i++) o[i] = p.at(hd, r[i][0], r[i][1], r[i][2]);
        return o;
    }

    static double[][] eyesAtRest(GiantModel m, int hd) {
        double[] sum = new double[6];
        int nl = 0, nr = 0;
        float[] cen = m.centre(hd);
        for (int i = 0; i < m.x[hd].length; i++) {
            String b = m.palette[m.pal[hd][i]];
            if (!(b.contains("redstone_lamp") || (m.names[hd].endsWith("ice") && b.contains("sea_lantern")))) continue;
            // left and right of the head's own middle line
            boolean left = m.x[hd][i] + 0.5 > cen[0];
            int o = left ? 0 : 3;
            sum[o] += m.x[hd][i] + 0.5;
            sum[o + 1] += m.y[hd][i] + 0.5;
            sum[o + 2] += m.z[hd][i] + 0.5;
            if (left) nl++;
            else nr++;
        }
        java.util.List<double[]> o = new java.util.ArrayList<>();
        if (nl > 0) o.add(new double[] {sum[0] / nl, sum[1] / nl, sum[2] / nl});
        if (nr > 0) o.add(new double[] {sum[3] / nr, sum[4] / nr, sum[5] / nr});
        return o.toArray(new double[0][]);
    }

    /** dripstone claws at a paw's front (pointing down onto the ground) */
    static void claws(GiantModel m, Pose p, Carcass c, String foot) {
        int b = m.bone(foot);
        float[] tp = tip(m, b);
        double[] w = c.w(p.at(b, tp));
        int x = Carcass.floor(w[0]), z = Carcass.floor(w[2]);
        if (c.s < 0.3) return;
        for (int k = -1; k <= 1; k++) {
            int y = Math.max(0, Carcass.floor(w[1]));
            c.put(x + k * 2, y, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]");
        }
    }
}
