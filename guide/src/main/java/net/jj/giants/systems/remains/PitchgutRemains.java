package net.jj.giants.systems.remains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.IntPredicate;

/**
 * Pitchgut, the walking mountain of flesh: he sinks down where he fell, his many legs splayed flat, his many arms
 * fallen out over the ground, his great head slumped forward, the pitch that dripped from his mouth spread in black
 * pools. His pink flesh rots away; his eyes go milky, then empty, leaving their bony rims as dozens of sockets; his
 * teeth and claws stay. Under it all: a huge ribcage along his back with tall spines, the long bones of every arm
 * with their finger bones, his legs' bones, and his skull with the jaws hanging open.
 */
final class PitchgutRemains extends Design {
    PitchgutRemains() {
        super("pitchgut", "mountain_breathes", "/mountain_breathes/mountain_model.bin",
                "/mountain_breathes/mountain_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        for (int b = 0; b < m.names.length; b++) {
            String n = m.names[b];
            float[] pv = m.pivot[b];
            if (n.matches("leg_\\d+_0")) {
                double side = pv[0] >= 0 ? 1 : -1;
                p.turn(b, 0, 0, 1, side * 52);
            } else if (n.matches("arm_\\d+_0")) {
                int k = Integer.parseInt(n.split("_")[1]);
                double side = Math.abs(pv[0]) > 3 ? Math.signum(pv[0]) : (k % 2 == 0 ? 1 : -1);
                double jit = (Noise.hash(k, 4, 2, 9) - 0.5) * 30;
                p.turn(b, 0, 1, 0, jit);
                p.turn(b, 0, 0, 1, -side * (100 + jit));
            }
        }
        p.drape(n -> n.startsWith("leg"), 0.45);
        p.drape(n -> n.startsWith("arm"), 0.85);
        p.drape(n -> n.startsWith("goo"), 0.96);
        // his head slumps (he faces -z)
        p.turn("head", 1, 0, 0, -14);
        p.turn("cheek_r", 0, 1, 0, -6).turn("cheek_l", 0, 1, 0, 6);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        double[] turn = Pose.then(turnAbout(0, 0, 1, 4, 0, 60, 60), yaw(180));
        return settle(m, p, turn, of(m, n -> n.startsWith("body")), 5);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        String b = Blocks.base(block);
        if (n.startsWith("goo")) return Carcass.HARD;
        if (n.startsWith("eye")) return Carcass.EYE;
        // teeth (bone and white in his head and jaws) stay; the bony bits of his skin go with it
        if ((n.equals("head") || n.startsWith("cheek"))
                && (b.equals("minecraft:white_concrete") || b.equals("minecraft:bone_block"))) return Carcass.HARD;
        return Carcass.FLESH;
    }

    @Override
    String dead(String block) {
        String b = Blocks.base(block);
        return switch (b) {
            case "minecraft:white_concrete" -> "minecraft:white_terracotta";
            case "minecraft:light_blue_concrete", "minecraft:light_blue_terracotta" -> "minecraft:light_gray_terracotta";
            case "minecraft:light_blue_stained_glass" -> "minecraft:gray_stained_glass";
            default -> lightsOut(block);
        };
    }

    @Override
    String rotten(String block, double r, int stage) {
        if (r < 0.25) return "minecraft:magenta_terracotta";
        if (r < 0.45) return "minecraft:brown_terracotta";
        if (r < 0.6) return "minecraft:nether_wart_block";
        if (r < 0.75) return "minecraft:mud";
        if (r < 0.88) return "minecraft:packed_mud";
        return "minecraft:crimson_nylium";
    }

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        if (b.equals("minecraft:crying_obsidian")) return stage == 0 ? block : "minecraft:obsidian";
        if (b.equals("minecraft:white_concrete")) return "minecraft:calcite";
        return lightsOut(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        JsonObject rig = m.rig;
        // the ribcage along his back: his body's own centre line
        JsonArray pts = rig.getAsJsonObject("body").getAsJsonArray("points");
        int[] body = bones(m, n -> n.startsWith("body"));
        IntPredicate torso = of(m, n -> n.startsWith("body"));
        int b0 = m.bone("body_2");
        double[][] spine = new double[pts.size()][];
        for (int i = 0; i < pts.size(); i++) {
            JsonArray q = pts.get(i).getAsJsonArray();
            // the spine runs high in his back: a little over the centre line
            JsonArray radii = rig.getAsJsonObject("body").getAsJsonArray("radii");
            double r = radii.get(i).getAsDouble();
            spine[i] = p.at(nearest(m, body, q), q.get(0).getAsDouble(), q.get(1).getAsDouble() + r * 0.45,
                    q.get(2).getAsDouble());
        }
        double[] up = Pose.dir(p.abs[b0], 0, 1, 0);
        c.ribs(spine, up, torso, 17, 5, 140, 1.25, 6, 26, "@bone:{axis}", "@bone:{axis}");
        // arms: long bones, a knob at the elbow, finger bones splayed
        int ai = 0;
        for (JsonElement e : rig.getAsJsonArray("arms")) {
            JsonObject a = e.getAsJsonObject();
            // (all seventy would run together over his flanks: a third of them, a sixth when small)
            if ((ai++ % (c.s < 0.5 ? 6 : 3)) != 0) continue;
            JsonArray bs = a.getAsJsonArray("bones");
            int ab = bs.get(0).getAsInt(), eb = bs.get(1).getAsInt(), wb = bs.get(2).getAsInt();
            double[] base = p.at(ab, m.pivot[ab]), elbow = p.at(eb, m.pivot[eb]), wrist = p.at(wb, m.pivot[wb]);
            c.lineB(ab, base, eb, elbow, 1.1, 0.9, "@bone:{axis}");
            c.ballB(eb, elbow, 1.8, "@bone");
            c.lineB(eb, elbow, wb, wrist, 0.9, 0.7, "@bone:{axis}");
            if (c.s >= 0.4) for (JsonElement f : a.getAsJsonArray("fingers")) {
                int fb = f.getAsInt();
                c.lineB(wb, wrist, fb, p.at(fb, tip(m, fb)), 1, 0.6, "@bone:{axis}");
            }
            int th = a.get("thumb").getAsInt();
            if (th >= 0 && c.s >= 0.4) c.lineB(wb, wrist, th, p.at(th, tip(m, th)), 1, 0.6, "@bone:{axis}");
        }
        // legs
        int li = 0;
        for (JsonElement e : rig.getAsJsonArray("legs")) {
            JsonArray bs = e.getAsJsonObject().getAsJsonArray("bones");
            if (c.s < 0.5 && (li++ % 2) == 1) continue;
            int n = bs.size();
            double[][] j = new double[n + 1][];
            int[] jb = new int[n + 1];
            for (int i = 0; i < n; i++) {
                jb[i] = bs.get(i).getAsInt();
                j[i] = p.at(jb[i], m.pivot[jb[i]]);
            }
            jb[n] = jb[n - 1];
            j[n] = p.at(jb[n], tip(m, jb[n]));
            double[] r = {1.3, 1.1, 0.9, 0.8, 0.7};
            for (int i = 1; i <= n; i++) {
                c.lineB(jb[i - 1], j[i - 1], jb[i], j[i], r[Math.min(4, i - 1)] * 0.8, r[Math.min(4, i)] * 0.8,
                        "@bone:{axis}");
                c.ballB(jb[i - 1], j[i - 1], r[Math.min(4, i - 1)], "@bone");
            }
        }
        // the skull: his head and both jaws (cheeks), pulled in a little; teeth are his own (kept)
        c.skull(new int[] {m.bone("head")}, 0.8, 0.8, 0.86, blk -> "@skull");
        c.skull(new int[] {m.bone("cheek_r")}, 0.8, 0.86, 0.86, blk -> "@skull");
        c.skull(new int[] {m.bone("cheek_l")}, 0.8, 0.86, 0.86, blk -> "@skull");
        // his great mouth gapes open through the skull, the teeth round it his own
        JsonObject hd = rig.getAsJsonObject("head");
        JsonArray mo = hd.getAsJsonArray("mouth");
        int hb = m.bone("head");
        c.socket(p.at(hb, mo.get(0).getAsDouble(), mo.get(1).getAsDouble(), mo.get(2).getAsDouble() + 8), 34);
        c.socket(p.at(hb, mo.get(0).getAsDouble(), mo.get(1).getAsDouble() + 30, mo.get(2).getAsDouble() + 14), 18);
        // every eye leaves its socket: a ring of bone where it was (those in his head, a hole through the skull)
        for (JsonElement e : rig.getAsJsonArray("eyes")) {
            JsonObject o = e.getAsJsonObject();
            int eb = o.get("bone").getAsInt();
            JsonArray ce = o.getAsJsonArray("center"), dir = o.getAsJsonArray("dir");
            double r = o.get("radius").getAsDouble();
            // (head eyes always; the body's, half of them, and only big enough to be a ring)
            boolean head = m.names[m.parent[eb] < 0 ? eb : m.parent[eb]].startsWith("head")
                    || m.names[m.parent[eb] < 0 ? eb : m.parent[eb]].startsWith("cheek");
            if (r * 1.15 * c.s < 1.4 || !head && Noise.hash(eb, 1, 1, 61) < 0.5) {
                JsonArray cc = o.getAsJsonArray("center");
                c.socket(p.at(eb, cc.get(0).getAsDouble(), cc.get(1).getAsDouble(), cc.get(2).getAsDouble()), r * 1.1);
                continue;
            }
            double[] cen = {ce.get(0).getAsDouble(), ce.get(1).getAsDouble(), ce.get(2).getAsDouble()};
            double[] d = Carcass.unit(new double[] {dir.get(0).getAsDouble(), dir.get(1).getAsDouble(),
                dir.get(2).getAsDouble()});
            c.socket(p.at(eb, cen[0], cen[1], cen[2]), r * 1.1);
            // two ways across the eye's face
            double[] u = Carcass.unit(Carcass.cross(d, Math.abs(d[1]) < 0.9 ? new double[] {0, 1, 0}
                    : new double[] {1, 0, 0}));
            double[] v = Carcass.cross(d, u);
            double rr = r * 1.15;
            double[] prev = null;
            for (int a = 0; a <= 360; a += 15) {
                double t = Math.toRadians(a);
                double[] q = {cen[0] + (u[0] * Math.cos(t) + v[0] * Math.sin(t)) * rr,
                    cen[1] + (u[1] * Math.cos(t) + v[1] * Math.sin(t)) * rr,
                    cen[2] + (u[2] * Math.cos(t) + v[2] * Math.sin(t)) * rr};
                double[] pq = p.at(eb, q[0], q[1], q[2]);
                if (prev != null) c.lineB(eb, prev, eb, pq, 0.7, 0.7, "@bone:{axis}");
                prev = pq;
            }
        }
    }

    /** his skull: old bone, broken through in patches (so it reads as bone, not a dome) */
    @Override
    String token(String token, double t) {
        if (token.startsWith("@skull")) {
            if (t < 0.22) return null;
            return super.token("@bone", (t - 0.22) / 0.78);
        }
        return super.token(token, t);
    }

    /** the body bone nearest a point at rest */
    static int nearest(GiantModel m, int[] bs, JsonArray q) {
        int best = bs[0];
        double bd = Double.MAX_VALUE;
        for (int b : bs) {
            float[] c = m.centre(b);
            double dx = c[0] - q.get(0).getAsDouble(), dy = c[1] - q.get(1).getAsDouble(),
                    dz = c[2] - q.get(2).getAsDouble();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }
}
