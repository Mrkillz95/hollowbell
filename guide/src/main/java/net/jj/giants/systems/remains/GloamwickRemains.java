package net.jj.giants.systems.remains;

import java.util.function.IntPredicate;

/**
 * Gloamwick, the soul moose: he lies on his right side, legs stretched out over the ground, his neck bent so his
 * head rests upright with the great antlers standing. Flesh of blackstone and coal; antlers and obsidian hooves stay.
 * His skeleton: a long spine with tall withers spikes, ribs following his own deep chest, shoulder blades and pelvis,
 * four long legs, his skull with soul fire burning low in the eye sockets, and soul lanterns hanging in the ribs that
 * go out as he rots (a few stay with the skeleton).
 */
final class GloamwickRemains extends Design {
    GloamwickRemains() {
        super("gloamwick", "gloamwick", "/gloamwick/gloamwick_model.bin", "/gloamwick/gloamwick_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // legs: out over the ground (the upper ones droop onto the lower), front ones reaching forward, hind back
        p.turn("leg_fl_upper", 0, 0, 1, -28).turn("leg_bl_upper", 0, 0, 1, -28);
        p.turn("leg_fr_upper", 0, 0, 1, -6).turn("leg_br_upper", 0, 0, 1, -6);
        p.turn("leg_fl_upper", 1, 0, 0, -34).turn("leg_fr_upper", 1, 0, 0, -22);
        p.turn("leg_bl_upper", 1, 0, 0, 30).turn("leg_br_upper", 1, 0, 0, 18);
        p.turn("leg_fl_lower", 1, 0, 0, 26).turn("leg_fr_lower", 1, 0, 0, 14);
        p.turn("leg_bl_lower", 1, 0, 0, -24).turn("leg_br_lower", 1, 0, 0, -12);
        // the neck bends down to the ground; the head turns back upright, antlers up
        p.turn("neck", 0, 1, 0, -16);
        p.turn("head", 0, 0, 1, -82);
        p.turn("tail", 0, 0, 1, -25);
        p.turn("jaw", 1, 0, 0, 8);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        // onto his right side (his left, +x, turns up), about his middle
        double[] turn = turnAbout(0, 0, 1, 90, 0, 109, -38);
        return settle(m, p, turn, of(m, n -> n.equals("body")), 2);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        if (n.startsWith("hoof") || n.startsWith("antler") || n.equals("bell")) return Carcass.HARD;
        if (n.equals("head") && (block.contains("sea_lantern") || block.contains("glazed"))) return Carcass.EYE;
        if (block.contains("soul_lantern") || block.contains("chain") || block.contains("hanging_roots"))
            return Carcass.SKIP;
        return Carcass.FLESH;
    }

    @Override
    String dead(String block) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:sea_lantern")) return "minecraft:light_blue_terracotta";
        if (b.equals("minecraft:light_blue_stained_glass")) return "minecraft:cyan_terracotta";
        return lightsOut(block);
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (r < 0.35) return "minecraft:soul_soil";
        if (r < 0.55) return "minecraft:black_terracotta";
        if (r < 0.7) return "minecraft:mud";
        if (r < 0.85) return "minecraft:gray_terracotta";
        return stage >= 2 ? "minecraft:soul_sand" : "minecraft:sculk";
    }

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:sea_lantern")) return stage == 0 ? "minecraft:light_blue_terracotta" : "minecraft:calcite";
        // antlers bleach a little with time
        if (stage >= 3 && fine < 0.35) {
            if (b.equals("minecraft:tuff") || b.equals("minecraft:gray_terracotta")) return "minecraft:calcite";
            if (b.equals("minecraft:coal_block") || b.equals("minecraft:blackstone")) return "minecraft:tuff";
        }
        return lightsOut(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        int body = m.bone("body"), neck = m.bone("neck");
        IntPredicate torso = b -> b == body;
        // the spine: from the rump over the withers into the neck
        double[][] back = backLine(m, new int[] {body}, -118, 30, 16, 8, 11);
        double[][] spine = new double[back.length][];
        for (int i = 0; i < back.length; i++) spine[i] = p.at(body, back[i][0], back[i][1], back[i][2]);
        double[] up = Pose.dir(p.abs[body], 0, 1, 0);
        c.ribs(spine, up, torso, 9, 2.5, 150, 1.3, 3.5, 7, "@bone:{axis}", "@bone:{axis}");
        // the neck's vertebrae up to the skull
        double[][] nk = {p.at(body, 0, 140, 28), p.at(neck, 0, 136, 50), p.at(neck, 0, 132, 66),
            at(m, p, "head", 0, 126, 82)};
        for (int i = 1; i < nk.length; i++) {
            c.line(nk[i - 1], nk[i], 3.2, 3.0, "@bone:{axis}");
            c.ball(nk[i], 4.2, "@bone");
        }
        // the tail
        int tail = m.bone("tail");
        c.line(p.at(body, back[0][0], back[0][1], back[0][2]), p.at(tail, m.pivot[tail]), 2.5, 2, "@bone:{axis}");
        c.line(p.at(tail, m.pivot[tail]), p.at(tail, tip(m, tail)), 2, 1, "@bone:{axis}");
        // shoulder blades and the pelvis
        for (int sd = -1; sd <= 1; sd += 2) {
            double[] withers = p.at(body, 0, 150, 2), hip = p.at(body, 0, 140, -100);
            double[] fore = piv(m, p, sd > 0 ? "leg_fl_upper" : "leg_fr_upper");
            double[] hind = piv(m, p, sd > 0 ? "leg_bl_upper" : "leg_br_upper");
            c.line(withers, fore, 4.5, 3, "@bone:{axis}");
            c.line(p.at(body, sd * 12, 142, 8), fore, 3, 3, "@bone:{axis}");
            c.line(hip, hind, 5, 3.5, "@bone:{axis}");
            c.line(p.at(body, sd * 14, 132, -88), hind, 3.5, 3, "@bone:{axis}");
            c.ball(hind, 6, "@bone");
        }
        // the legs
        for (String lg : new String[] {"fl", "fr", "bl", "br"}) {
            double[][] j = {piv(m, p, "leg_" + lg + "_upper"), piv(m, p, "leg_" + lg + "_lower"),
                piv(m, p, "hoof_" + lg)};
            limb(c, j, new double[] {4, 3, 2.4}, "@bone:{axis}");
        }
        // the skull, from his head's own shape; the eyes open sockets
        c.skull(new int[] {m.bone("head"), m.bone("jaw")}, 0.9, blk -> blk.contains("glazed") ? null : "@bone");
        int head = m.bone("head");
        double[][] eyes = {p.at(head, 15.4, 125, 97.9), p.at(head, -15.4, 125, 97.9)};
        for (double[] e : eyes) c.socket(e, 7);
        // soul fire burning low in each socket
        for (double[] e : eyes) {
            double[] w = c.w(e);
            int x = Carcass.floor(w[0]), y = Carcass.floor(w[1]), z = Carcass.floor(w[2]);
            String fire = "minecraft:soul_fire", soil = "minecraft:soul_soil";
            c.extra(x, y - 1, z, soil, soil, soil, soil, soil);
            c.extra(x, y, z, fire, fire, fire, null, fire);
        }
        // soul lanterns hanging in his ribs
        int k = 0;
        for (int i = 2; i < spine.length - 2; i += 2, k++) {
            double[] sp = c.w(spine[i]);
            double[] down = c.wdir(new double[] {-up[0], -up[1], -up[2]});
            double depth = 30 * c.s;
            double[] mid = Carcass.add(sp, Carcass.mul(down, depth));
            boolean stays = k % 3 == 0;
            hang(c, mid, Math.max(2, (int) (12 * c.s)), new boolean[] {true, true, k % 2 == 0, stays, stays});
        }
    }

    /** a soul lantern on a chain, hanging from the first bone above a spot (or this long) */
    static void hang(Carcass c, double[] at, int len, boolean[] when) {
        int x = Carcass.floor(at[0]), y = Carcass.floor(at[1]), z = Carcass.floor(at[2]);
        int top = y;
        for (int dy = 1; dy <= len; dy++) {
            int j = c.find(x, y + dy, z);
            top = y + dy;
            if (j >= 0 && c.bone[j] != 0) break;
        }
        String[] chain = new String[Carcass.STAGES], lantern = new String[Carcass.STAGES];
        for (int s = 0; s < Carcass.STAGES; s++) {
            chain[s] = when[s] ? "minecraft:chain[axis=y]" : null;
            lantern[s] = when[s] ? "minecraft:soul_lantern[hanging=true]" : null;
        }
        for (int yy = y + 1; yy < top; yy++) c.extra(x, yy, z, chain);
        c.extra(x, y, z, lantern);
    }
}
