package net.jj.giants.systems.remains;

/**
 * Tickmoor, the clockwork mantis, run down: his four legs give way and splay flat over the ground, his clock tower
 * topples forward so his head lies before him, its dials staring, his forelegs (the clock hands) flung out to either
 * side, one wing torn askew, his long abdomen sagging. A machine, so no flesh: his lights go out
 * (the hourglass sand goes dull), and as he "rots" he weathers: plates fall out of his tower, thorax and abdomen
 * between the ribs of his frame, copper greens, iron rusts and his glass breaks, until only the frame is left with his
 * works showing inside: gear trains in the tower, the pendulum hanging still on its chain, and his two dials, stopped.
 */
final class TickmoorRemains extends Design {
    TickmoorRemains() {
        super("tickmoor", "tickmoor", "/tickmoor/tickmoor_model.bin", "/tickmoor/tickmoor_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // the legs give way: each femur drops flat, each tibia folds out level with it (left side +x, right side -x)
        for (String leg : new String[] {"mid", "hind"}) {
            p.turn(leg + "_l_femur", 0, 0, 1, -30).turn(leg + "_l_tibia", 0, 0, 1, leg.equals("mid") ? 104 : 100);
            p.turn(leg + "_r_femur", 0, 0, 1, 30).turn(leg + "_r_tibia", 0, 0, 1, leg.equals("mid") ? -104 : -100);
        }
        p.turn("mid_l_foot", 0, 0, 1, -30).turn("mid_r_foot", 0, 0, 1, 30);
        p.turn("hind_l_foot", 0, 0, 1, -30).turn("hind_r_foot", 0, 0, 1, 30);
        // the tower topples forward; his head turns up so the dials face the sky
        p.turn("prothorax", 1, 0, 0, 52);
        p.turn("head", 1, 0, 0, -48).turn("head", 0, 0, 1, 14);
        // the forelegs (his clock hands) flung out to the sides, the minute one further
        p.turn("hour_coxa", 0, 0, 1, 55).turn("hour_coxa", 0, 1, 0, -20).turn("hour_lower", 1, 0, 0, 30);
        p.turn("minute_coxa", 0, 0, 1, -68).turn("minute_coxa", 0, 1, 0, 26).turn("minute_lower", 1, 0, 0, 18);
        // one wing slumps over his back, the other is torn askew
        p.turn("wing_l", 0, 0, 1, -16);
        p.turn("wing_r", 0, 0, 1, 34).turn("wing_r", 0, 1, 0, 22);
        // the abdomen sags to the ground; the antennae droop
        p.turn("abdomen", 1, 0, 0, -10);
        p.turn("antenna_l", 1, 0, 0, 50).turn("antenna_r", 1, 0, 0, 36).turn("antenna_r", 0, 0, 1, -20);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        // a little rolled onto his right side, resting on his belly
        double[] turn = turnAbout(0, 0, 1, -6, 0, 40, 0);
        return settle(m, p, turn, of(m, n -> n.equals("thorax") || n.equals("abdomen")), 3);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        // (his small hands are left off: they sit apart from his dials in the plain model)
        if (n.startsWith("eye_") && (n.endsWith("_hour") || n.endsWith("_min"))) return Carcass.SKIP;
        // (his dials are hard: they stay, stopped, to the very end)
        return Carcass.HARD;
    }

    @Override
    boolean frame(GiantModel m, int bone, int x, int y, int z) {
        String n = m.names[bone];
        // only the big plated parts lose plates; legs, arms, head, dials and works stay whole
        if (!n.equals("thorax") && !n.equals("prothorax") && !n.equals("abdomen") && !n.startsWith("wing")) return true;
        if (n.startsWith("wing")) return Math.floorMod(x + z, 7) == 0 || Math.floorMod(y, 6) == 0;
        // his frame: rings round the tower and the body, and long ribs along them
        int fy = Math.floorMod(y + 2, 9), fz = Math.floorMod(z + 4, 10), fx = Math.floorMod(x + 3, 12);
        return fy <= 1 && n.equals("prothorax") || fz <= 1 || fx <= 1 && !n.equals("prothorax");
    }

    @Override
    String dead(String block) {
        return dull(block);
    }

    /** his lights gone out: the hourglass sand dull, the lamps cold */
    static String dull(String block) {
        String b = Blocks.base(block);
        return switch (b) {
            case "minecraft:glowstone" -> "minecraft:yellow_terracotta";
            case "minecraft:ochre_froglight" -> "minecraft:orange_terracotta";
            case "minecraft:shroomlight" -> "minecraft:brown_terracotta";
            default -> lightsOut(block);
        };
    }

    /** how much of his plating is gone, and how much has weathered, by stage */
    private static final double[] HOLES = {0, 0.12, 0.4, 0.66, 0.76}, WEAR = {0, 0.2, 0.42, 0.65, 0.7};

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        if (stage == 0) return dull(block);
        if (n < HOLES[stage]) return "minecraft:air";
        if (b.endsWith("glass")) return stage >= 2 ? "minecraft:air" : fine < 0.5 ? "minecraft:air" : block;
        if (fine < WEAR[stage]) {
            switch (b) {
                case "minecraft:waxed_exposed_cut_copper":
                    return stage >= 3 ? "minecraft:oxidized_cut_copper" : "minecraft:weathered_cut_copper";
                case "minecraft:waxed_weathered_cut_copper":
                    return "minecraft:oxidized_cut_copper";
                case "minecraft:polished_deepslate":
                    return fine < WEAR[stage] * 0.4 ? "minecraft:cracked_deepslate_tiles" : "minecraft:cobbled_deepslate";
                case "minecraft:deepslate_tiles":
                    return "minecraft:cracked_deepslate_tiles";
                case "minecraft:polished_blackstone":
                    return fine < WEAR[stage] * 0.5 ? "minecraft:cracked_polished_blackstone_bricks" : block;
                case "minecraft:tuff":
                    return stage >= 3 && fine < 0.12 ? "minecraft:moss_block" : "minecraft:cobblestone";
                case "minecraft:gold_block":
                    return stage >= 3 ? "minecraft:raw_gold_block" : block;
                case "minecraft:iron_block":
                    return "minecraft:raw_iron_block";
                case "minecraft:yellow_terracotta", "minecraft:glowstone":
                    return "minecraft:brown_terracotta";
                default:
            }
        }
        return dull(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        int pro = m.bone("prothorax"), thorax = m.bone("thorax");
        // his works, inside the tower: a train of gears up its middle, and an axle through them
        double[] base = p.at(pro, -0.5, 56, 10), top = p.at(pro, -0.5, 104, 44);
        c.line(base, top, 1.4, 1.2, "minecraft:polished_basalt[axis={axis}]");
        for (int i = 0; i < 6; i++) {
            double f = (i + 0.5) / 6;
            double[] at = Carcass.lerp(base, top, f);
            double[] w = c.w(at);
            int gx = Carcass.floor(w[0]), gy = Carcass.floor(w[1]), gz = Carcass.floor(w[2]);
            int rad = Math.max(1, (int) Math.round((i % 2 == 0 ? 6 : 4) * c.s));
            gear(c, gx, gy, gz, rad, i);
        }
        // the pendulum, stopped, hanging on its chain from the top of the lantern storey
        int pend = m.bone("pendulum");
        if (pend >= 0) {
            double[] hang = c.w(p.at(pro, -0.5, 84, 16)), bob = c.w(p.at(pend, m.pivot[pend]));
            int hx = Carcass.floor(bob[0]), hz = Carcass.floor(bob[2]);
            int y0 = Carcass.floor(bob[1]) + 1, y1 = Math.max(y0, Carcass.floor(hang[1]));
            for (int y = y0; y <= y1; y++) c.put(hx, y, hz, "minecraft:chain[axis=y]");
        }
        // a cross-brace through his thorax, where his legs meet it
        c.line(p.at(thorax, 18, 34, -6), p.at(thorax, -19, 34, -6), 1.6, 1.6, "minecraft:iron_block");
        c.line(p.at(thorax, 18, 33, -23), p.at(thorax, -19, 33, -23), 1.6, 1.6, "minecraft:iron_block");
        c.line(p.at(thorax, -0.5, 36, -26), p.at(thorax, -0.5, 36, 0), 1.4, 1.4, "minecraft:iron_block");
    }

    /** a gear in the gear train: a ring of copper with teeth, upright, its hub on the axle (shows as plates fall) */
    private static void gear(Carcass c, int gx, int gy, int gz, int rad, int salt) {
        String rim = salt % 2 == 0 ? "minecraft:waxed_weathered_cut_copper" : "minecraft:waxed_exposed_cut_copper";
        int teeth = Math.max(8, rad * 6);
        for (int i = 0; i < teeth * 2; i++) {
            double t = i * Math.PI / teeth;
            double rr = rad + ((i / 2) % 2 == 0 ? 0.6 : 0);
            int x = gx + (int) Math.round(Math.cos(t) * rr), y = gy + (int) Math.round(Math.sin(t) * rr);
            if (y < 0) continue;
            c.put(x, y, gz, rim);
        }
        String hub = "minecraft:waxed_copper_block";
        if (gy >= 0) c.put(gx, gy, gz, hub);
    }
}
