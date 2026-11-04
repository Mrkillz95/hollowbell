package net.jj.giants.systems.remains;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Furrowmaw, the burrowing worm: he dies half sunk in the ground, his long body coiled round once from his head (on
 * the outside, facing his heading) in to his tail, his legs flat out on the ground. His armour plates crumble away;
 * under them a long chain of vertebrae with a ring of rib round each segment (open underneath), his maw a ring of
 * jaw with teeth pointing in, his mandibles and the pincers at his tail tip kept.
 */
final class FurrowmawRemains extends Design {
    FurrowmawRemains() {
        super("furrowmaw", "furrowmaw", "/furrowmaw/furrowmaw_model.bin", "/furrowmaw/furrowmaw_rig.json");
    }

    /** the coil: radius at the start, how much it closes per turn */
    static final double R0 = 128, TIGHT = 72 / (2 * Math.PI);

    /** his centre line on the ground, by distance s back from his head: x z, heading (radians), and the way back */
    double[][] path;
    double step = 1;

    void layout(double length) {
        int n = (int) Math.ceil(length / step) + 2;
        path = new double[n][];
        double x = 0, z = 0, a = Math.PI; // going back from the head: toward -z
        for (int i = 0; i < n; i++) {
            path[i] = new double[] {x, z, a};
            double s = i * step;
            double r = Math.sqrt(Math.max(30 * 30, R0 * R0 - 2 * TIGHT * s));
            a += step / r;
            x += Math.sin(a) * step;
            z += Math.cos(a) * step;
        }
    }

    /** a frame on his centre line s back from the head, his centre this high: rest model space -> lying */
    double[] frame(double s, double height, double roll) {
        int i = Math.max(0, Math.min(path.length - 1, (int) Math.round(s / step)));
        double[] q = path[i];
        double tx = Math.sin(q[2]), tz = Math.cos(q[2]);
        // local z (his front) = back along the path's way reversed; local y = up (rolled a little); x = y cross z
        double[] zz = {-tx, 0, -tz};
        double cr = Math.cos(Math.toRadians(roll)), sr = Math.sin(Math.toRadians(roll));
        double[] side = {zz[2], 0, -zz[0]}; // up x z
        double[] yy = {side[0] * sr, cr, side[2] * sr};
        double[] xx = Carcass.cross(yy, zz);
        return new double[] {xx[0], yy[0], zz[0], xx[1], yy[1], zz[1], xx[2], yy[2], zz[2], q[0], height, q[1]};
    }

    double radius(JsonArray segs, int i) {
        return segs.get(Math.min(i, segs.size() - 1)).getAsJsonObject().get("radius").getAsDouble();
    }

    @Override
    void pose(GiantModel m, Pose p) {
        JsonObject rig = m.rig;
        JsonArray segs = rig.getAsJsonArray("segments");
        double headLen = rig.getAsJsonObject("head").get("length").getAsDouble();
        double total = 0;
        for (int i = 0; i < segs.size(); i++) total += segs.get(i).getAsJsonObject().get("length").getAsDouble();
        layout(total + 80);
        double r0 = radius(segs, 0);
        // the head: half sunk, its maw on the ground
        double[] head = frame(0, r0 * 0.42, 0);
        head = Pose.then(Pose.about(Pose.rot(1, 0, 0, 8), 0, 0, 0), head);
        int hb = m.bone("head");
        p.place(hb, head);
        for (int b = 1; b <= 9; b++) p.place(b, head);
        double s = 2;
        for (int i = 0; i < segs.size(); i++) {
            JsonObject g = segs.get(i).getAsJsonObject();
            double r = g.get("radius").getAsDouble();
            double wave = Math.sin(s / 46) * 4 * r / 29;
            double[] f = frame(s, r * 0.45 + wave, Math.sin(s / 70) * 10);
            p.place(g.get("bone").getAsInt(), f);
            for (String side : new String[] {"L", "R"}) {
                JsonArray hip = g.getAsJsonArray("hip" + side);
                double sg = side.equals("L") ? 1 : -1;
                // legs out flat on the ground
                double[] leg = Pose.about(Pose.rot(0, 0, 1, sg * 44), 0, 0, 0);
                leg[9] += hip.get(0).getAsDouble();
                leg[10] += hip.get(1).getAsDouble();
                leg[11] += hip.get(2).getAsDouble();
                p.place(g.get("leg" + side).getAsInt(), Pose.then(leg, f));
            }
            s += g.get("length").getAsDouble();
        }
        // the pincers at his tail tip
        double rl = radius(segs, segs.size() - 1);
        double[] tail = frame(s, rl * 0.5, 0);
        for (var e : rig.getAsJsonArray("pincers"))
            p.place(e.getAsJsonObject().get("bone").getAsInt(), tail);
        lastS = s;
    }

    double lastS;

    @Override
    double[] lie(GiantModel m, Pose p) {
        return Pose.ident();
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        if (n.startsWith("mandible") || n.startsWith("pincer")) return Carcass.HARD;
        if (Blocks.base(block).equals("minecraft:bone_block") && !n.startsWith("leg")) return Carcass.HARD;
        return Carcass.FLESH;
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (r < 0.3) return "minecraft:cracked_nether_bricks";
        if (r < 0.45) return "minecraft:nether_wart_block";
        if (r < 0.6) return "minecraft:coarse_dirt";
        if (r < 0.75) return "minecraft:rooted_dirt";
        if (r < 0.88) return "minecraft:brown_terracotta";
        return "minecraft:mud";
    }

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        return lightsOut(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        JsonObject rig = m.rig;
        JsonArray segs = rig.getAsJsonArray("segments");
        // vertebrae and a ring of rib round each segment
        double s = 2;
        double[] last = null;
        for (int i = 0; i < segs.size(); i++) {
            JsonObject g = segs.get(i).getAsJsonObject();
            double r = g.get("radius").getAsDouble(), len = g.get("length").getAsDouble();
            double[] f = p.abs[g.get("bone").getAsInt()];
            double[] v = Pose.apply(f, 0, r * 0.12, -len * 0.5);
            if (last != null) c.line(last, v, r * 0.12, r * 0.12, "@bone:{axis}");
            c.ball(v, r * 0.2, "@bone");
            c.line(v, Pose.apply(f, 0, r * 0.62, -len * 0.5), r * 0.07, r * 0.05, "@bone:{axis}");
            last = v;
            // thin leg bones out over the ground
            for (String side : new String[] {"L", "R"}) {
                int lb = g.get("leg" + side).getAsInt();
                JsonArray foot = g.getAsJsonArray("foot");
                double sg = side.equals("L") ? 1 : -1;
                double[] hip = Pose.apply(p.abs[lb], 0, 0, 0);
                double[] knee = Pose.apply(p.abs[lb], sg * foot.get(0).getAsDouble() * 0.55, -4, 0);
                double[] toe = Pose.apply(p.abs[lb], sg * foot.get(0).getAsDouble(), foot.get(1).getAsDouble(),
                        foot.get(2).getAsDouble());
                c.line(hip, knee, r * 0.05, r * 0.045, "@bone:{axis}");
                c.line(knee, toe, r * 0.045, r * 0.03, "@bone:{axis}");
            }
            // the ring: round from one side over the top to the other (the ground hides the rest)
            double rr = r * 0.82;
            double[] prev = null;
            for (int a = -150; a <= 150; a += 6) {
                double t = Math.toRadians(a);
                double[] q = Pose.apply(f, Math.sin(t) * rr, Math.cos(t) * rr + r * 0.12, -len * 0.5);
                if (prev != null) c.line(prev, q, r * 0.055, r * 0.055, "@bone:{axis}");
                prev = q;
            }
            s += len;
        }
        // the head: three rings, bars along it, and the maw's jaw ring with teeth pointing in
        int hb = m.bone("head");
        double[] hf = p.abs[hb];
        JsonObject head = rig.getAsJsonObject("head");
        double hr = head.get("radius").getAsDouble();
        double[][] rings = {{-2, 0.9}, {16, 0.95}, {34, 0.85}};
        double[] tail = Pose.apply(hf, 0, hr * 0.12, -4);
        if (last != null) c.line(tail, Pose.apply(p.abs[segs.get(0).getAsJsonObject().get("bone").getAsInt()], 0,
                radius(segs, 0) * 0.12, -2), hr * 0.12, hr * 0.12, "@bone:{axis}");
        for (double[] rg : rings) {
            double[] prev = null;
            for (int a = -160; a <= 160; a += 5) {
                double t = Math.toRadians(a);
                double[] q = Pose.apply(hf, Math.sin(t) * hr * rg[1], Math.cos(t) * hr * rg[1], rg[0]);
                if (prev != null) c.line(prev, q, 1.3, 1.3, "@bone:{axis}");
                prev = q;
            }
        }
        for (int k = 0; k < 8; k++) {
            double t = Math.toRadians(-140 + k * 40);
            double[] a = Pose.apply(hf, Math.sin(t) * hr * 0.9, Math.cos(t) * hr * 0.9, -2);
            double[] b = Pose.apply(hf, Math.sin(t) * hr * 0.85, Math.cos(t) * hr * 0.85, 46);
            c.line(a, b, 1.1, 1.1, "@bone:{axis}");
        }
        // the maw: a thick jaw ring, teeth inward
        double mz = 48, mr = hr * 0.78;
        double[] prev = null;
        for (int a = 0; a <= 360; a += 4) {
            double t = Math.toRadians(a);
            double[] q = Pose.apply(hf, Math.sin(t) * mr, Math.cos(t) * mr, mz);
            if (prev != null) c.line(prev, q, 2.6, 2.6, "@bone:{axis}");
            prev = q;
        }
        for (int k = 0; k < 18; k++) {
            double t = Math.toRadians(k * 20);
            double[] a = Pose.apply(hf, Math.sin(t) * mr, Math.cos(t) * mr, mz);
            double[] b = Pose.apply(hf, Math.sin(t) * mr * 0.55, Math.cos(t) * mr * 0.55, mz + 7);
            c.line(a, b, 1.6, 0.5, "minecraft:calcite");
        }
    }
}
