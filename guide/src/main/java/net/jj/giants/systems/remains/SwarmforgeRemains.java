package net.jj.giants.systems.remains;

/**
 * The Swarmforge is a machine, so no bones: his wrecked hull settles nose down and listing, his front left tread
 * thrown out, a crane arm slumped to the ground, a chimney fallen; his furnace mouth and core go cold, his eyes dark.
 * As he "rots" he rusts (iron to raw iron, copper greening) and his hull plates fall out until only his frame is
 * left: ribs and beams of his own hull with the machinery inside showing (columns, hanging chains, gears, a fallen
 * anvil), cobwebs in the end.
 */
final class SwarmforgeRemains extends Design {
    SwarmforgeRemains() {
        super("swarmforge", "swarmforge", "/swarmforge/swarmforge_model.bin", "/swarmforge/swarmforge_rig.json");
    }

    @Override
    void pose(GiantModel m, Pose p) {
        // the front left tread is thrown out and lies tilted
        p.turn("tread_fl", 0, 0, 1, -22).turn("tread_fl", 1, 0, 0, 8);
        // a crane slumps forward to the ground; the other swings aside
        p.turn("crane_l_lower", 1, 0, 0, 44).turn("crane_l_upper", 1, 0, 0, 30).turn("crane_l_claw", 1, 0, 0, 25);
        p.turn("crane_r_lower", 0, 1, 0, -38).turn("crane_r_lower", 1, 0, 0, 16).turn("crane_r_upper", 1, 0, 0, 34);
        // a chimney falls over; the cannon droops
        p.turn("chimney_1", 0, 0, 1, -58);
        p.turn("chimney_4", 0, 0, 1, 18);
        p.turn("cannon_barrel", 1, 0, 0, 20);
        // the doors hang open
        p.turn("door_l2", 0, 0, 1, -50).turn("door_r1", 0, 0, 1, 50);
    }

    @Override
    double[] lie(GiantModel m, Pose p) {
        double[] turn = Pose.then(turnAbout(0, 0, 1, -11, 0, 30, 0), turnAbout(1, 0, 0, 5, 0, 30, 0));
        return settle(m, p, turn, of(m, n -> n.startsWith("hull") || n.equals("plough")), 4);
    }

    @Override
    byte role(GiantModel m, int bone, String block) {
        String n = m.names[bone];
        if ((n.equals("eye_l") || n.equals("eye_r"))) return Carcass.EYE;
        if (block.contains("ladder")) return Carcass.SKIP;
        return Carcass.HARD;
    }

    @Override
    boolean frame(GiantModel m, int bone, int x, int y, int z) {
        String n = m.names[bone];
        if (!n.startsWith("hull") && !n.equals("face")) return true;
        // his hull's ribs and beams: plates between them can fall out
        int fx = Math.floorMod(x + 3, 16), fz = Math.floorMod(z + 5, 18);
        return fx <= 1 || fz <= 1 || (y <= 4 && n.equals("hull_lower"));
    }

    @Override
    String dead(String block) {
        return cold(block);
    }

    static String cold(String block) {
        String b = Blocks.base(block);
        return switch (b) {
            case "minecraft:magma_block" -> "minecraft:blackstone";
            case "minecraft:shroomlight" -> "minecraft:coal_block";
            case "minecraft:glowstone" -> "minecraft:black_terracotta";
            case "minecraft:glass", "minecraft:orange_stained_glass" -> "minecraft:tinted_glass";
            default -> lightsOut(block);
        };
    }

    private static final double[] HOLES = {0, 0.15, 0.42, 0.68, 0.78}, RUST = {0, 0.15, 0.35, 0.6, 0.65};

    @Override
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        String b = Blocks.base(block);
        if (stage == 0) {
            // his furnace's last embers
            if (b.equals("minecraft:magma_block") && fine < 0.35) return block;
            return cold(block);
        }
        if (n < HOLES[stage]) return "minecraft:air";
        if (b.endsWith("glass")) return stage >= 2 ? "minecraft:air" : "minecraft:tinted_glass";
        if (fine < RUST[stage]) {
            switch (b) {
                case "minecraft:iron_block":
                    return fine < RUST[stage] * 0.45 ? "minecraft:raw_iron_block" : "minecraft:brown_terracotta";
                case "minecraft:cut_copper":
                    return stage >= 3 ? "minecraft:oxidized_cut_copper" : "minecraft:weathered_cut_copper";
                case "minecraft:exposed_copper", "minecraft:weathered_copper":
                    return "minecraft:oxidized_copper";
                case "minecraft:yellow_concrete":
                    return "minecraft:yellow_terracotta";
                case "minecraft:polished_blackstone":
                    return fine < 0.1 ? "minecraft:cracked_polished_blackstone_bricks" : block;
                case "minecraft:deepslate_tiles":
                    return "minecraft:cracked_deepslate_tiles";
                case "minecraft:deepslate_bricks":
                    return "minecraft:cracked_deepslate_bricks";
                default:
            }
        }
        return cold(block);
    }

    @Override
    void bones(GiantModel m, Pose p, Carcass c) {
        int hull = m.bone("hull_lower"), upper = m.bone("hull_upper");
        // the machinery inside: columns, a deck of beams over them, chains hanging, gears, an anvil fallen
        int k = 0;
        for (int x = -42; x <= 42; x += 28)
            for (int z = -84; z <= 76; z += 32, k++) {
                double[] bot = p.at(hull, x, 6, z), top = p.at(upper, x, 98, z);
                c.line(bot, top, 1.6, 1.6, "minecraft:polished_basalt[axis={axis}]");
                if (x < 42) c.line(top, p.at(upper, x + 28, 98, z), 1.2, 1.2, "minecraft:iron_block");
                if (z < 76) c.line(top, p.at(upper, x, 98, z + 32), 1.2, 1.2, "minecraft:iron_block");
                double[] mid = p.at(hull, x, 46, z);
                double[] wm = c.w(mid);
                if (c.s >= 0.35) {
                    int gx = Carcass.floor(wm[0]), gy = Carcass.floor(wm[1]), gz = Carcass.floor(wm[2]);
                    c.put(gx + 2, gy, gz, "minecraft:grindstone[face=wall,facing=east]");
                    c.put(gx - 2, gy, gz, "minecraft:grindstone[face=wall,facing=west]");
                }
                // chains down from the beams between the columns
                if (k % 2 == 0) {
                    double[] h = c.w(p.at(upper, x + 14, 97, z + 16));
                    int hx = Carcass.floor(h[0]), hy = Carcass.floor(h[1]), hz = Carcass.floor(h[2]);
                    int len = Math.max(2, (int) (34 * c.s));
                    for (int i = 0; i < len; i++) {
                        String ch = "minecraft:chain[axis=y]";
                        c.extra(hx, hy - i, hz, ch, ch, ch, ch, ch);
                    }
                }
            }
        double[] an = c.w(p.at(hull, 18, 9, -20));
        c.put(Carcass.floor(an[0]), Math.max(0, Carcass.floor(an[1])), Carcass.floor(an[2]),
                "minecraft:damaged_anvil[facing=north]");
        double[] an2 = c.w(p.at(hull, -24, 9, 30));
        c.put(Carcass.floor(an2[0]), Math.max(0, Carcass.floor(an2[1])), Carcass.floor(an2[2]),
                "minecraft:chipped_anvil[facing=east]");
    }

    @Override
    void finish(Carcass c, int[][] st) {
        // cobwebs in the gutted frame, at the very end
        int web = c.mat("minecraft:cobweb");
        for (int i = 0; i < c.n; i++) {
            if (st[3][i] != 0 || st[4][i] != 0) continue;
            int x = c.cx[i], y = c.cy[i], z = c.cz[i];
            if (Noise.hash(x, y, z, 91) > 0.012) continue;
            int up = c.find(x, y + 1, z);
            if (up >= 0 && st[4][up] != 0 && st[0][i] != 0) {
                st[3][i] = web;
                st[4][i] = web;
            }
        }
    }
}
