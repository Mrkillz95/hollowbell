package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Hollowbell's den: a high roost. A stone stem wound with copper rises from a rocky, crystal-studded mound to a
 * round landing deck made like him: green glass panes between copper and bone ribs, a glowing ring and core, a copper
 * lip; bone ribs fan down under it to the stem like a goblet, and strands hang from its edge (chains, green glass,
 * glowing tips and lanterns). Clouds drift round it at different heights, and amethyst and green glass crystals grow
 * out of the mound below.
 */
final class Roost extends Plan {
    private final int up, mound;
    private final double deck, stem, moundR;
    private final int spokes;

    Roost(int r, double h) {
        super(r, h);
        up = Mth.clamp((int) Math.round(h * 0.25), 10, 56);
        deck = Math.max(5, r * 0.6);
        stem = Math.max(1.5, 2.0 * u);
        moundR = Math.max(4, r * 0.32);
        mound = sc(3, 1);
        spokes = Mth.clamp((int) Math.round(deck * 2 * Math.PI / Math.max(3.5, 4 * u)), 8, 32);
    }

    @Override
    public int reach() {
        return r + 2;
    }

    private int hill(double d, int dx, int dz) {
        if (d > moundR) return 0;
        double t = 1 - d / moundR;
        return (int) Math.round(mound * Math.sqrt(t) + (noise(dx, dz, 3, 120) - 0.5) * 1.5 * t);
    }

    @Override
    int top(int dx, int dz) {
        return hill(Math.sqrt(dx * dx + dz * dz), dx, dz);
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 1) return;
        int y = hill(d, dx, dz);
        o.support = b(Blocks.STONE);
        double k = rnd(dx, dz, 121), n = noise(dx, dz, Math.max(3, 4 * u), 122);
        BlockState s;
        double m = noise(dx, dz, Math.max(2.5, 2.5 * u), 125);
        if (d < moundR) s = m < 0.35 ? (k < 0.7 ? b(Blocks.STONE) : b(Blocks.COBBLESTONE)) : m < 0.6
                ? (k < 0.7 ? b(Blocks.ANDESITE) : b(Blocks.STONE)) : m < 0.8 ? b(Blocks.MOSS_BLOCK)
                : (k < 0.6 ? b(Blocks.CALCITE) : b(Blocks.DIORITE));
        else s = n > 0.6 ? (k < 0.75 ? b(Blocks.MOSS_BLOCK) : b(Blocks.MOSSY_COBBLESTONE)) : n < 0.22
                ? (k < 0.7 ? b(Blocks.STONE) : b(Blocks.ANDESITE)) : k < 0.92 ? b(Blocks.GRASS_BLOCK) : b(Blocks.MOSS_BLOCK);
        for (int i = 0; i < y; i++) o.set(f + i, i == 0 ? b(Blocks.STONE) : rnd(dx, i, dz, 123) < 0.5
                ? b(Blocks.STONE) : b(Blocks.ANDESITE));
        o.set(f + y, s);
        o.clearAbove(f + y + 1);
    }

    @Override
    void plan() {
        // the stem: stone, wound round with two copper bands, flaring at the top
        double flareFrom = up * 0.72;
        for (int y = 0; y <= up; y++) {
            double rr = stem * (1 + 0.15 * (1 - (double) y / up));
            if (y > flareFrom) rr += Math.pow((y - flareFrom) / (up - flareFrom), 2) * (deck * 0.35);
            for (int x = (int) -Math.ceil(rr + 1); x <= Math.ceil(rr + 1); x++)
                for (int z = (int) -Math.ceil(rr + 1); z <= Math.ceil(rr + 1); z++) {
                    double e = Math.hypot(x, z);
                    if (e > rr) continue;
                    if (y > flareFrom && e < rr - 1.5) continue;
                    double a = Math.atan2(z, x);
                    double turn = Mth.positiveModulo(a * 2 - y * 0.35 / Math.max(0.6, u * 0.5), Math.PI * 2);
                    boolean band = turn < 0.7 && e > rr - 1.1;
                    double k = rnd(x, y, z, 124);
                    put(x, y, z, band ? (k < 0.7 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER)
                            : b(Blocks.WAXED_WEATHERED_CUT_COPPER)) : k < 0.12 ? b(Blocks.CRACKED_STONE_BRICKS)
                            : k < 0.22 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
                }
        }
        // the deck: spokes of copper and bone, green glass between, a glowing ring and core, a copper lip
        for (int x = (int) -Math.ceil(deck); x <= Math.ceil(deck); x++)
            for (int z = (int) -Math.ceil(deck); z <= Math.ceil(deck); z++) {
                double e = Math.hypot(x, z);
                if (e > deck) continue;
                double a = Mth.positiveModulo(Math.atan2(z, x), Math.PI * 2);
                double sp = a / (Math.PI * 2) * spokes;
                double off = Math.abs(sp - Math.round(sp)) * (Math.PI * 2 / spokes) * e;
                boolean spoke = off < 0.55;
                BlockState s;
                if (e < Math.max(1.6, stem * 0.8)) s = b(Blocks.SEA_LANTERN);
                else if (e < Math.max(2.6, stem * 1.4)) s = b(Blocks.CALCITE);
                else if (Math.abs(e - deck * 0.55) < 0.6) s = b(Blocks.VERDANT_FROGLIGHT);
                else if (e > deck - 1.1) s = b(Blocks.WAXED_OXIDIZED_COPPER);
                else if (spoke) s = Math.round(sp) % 2 == 0 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER) : b(Blocks.BONE_BLOCK);
                else s = Math.abs(e - deck * 0.3) < 0.5 || Math.abs(e - deck * 0.8) < 0.5
                        ? b(Blocks.GREEN_STAINED_GLASS) : b(Blocks.LIME_STAINED_GLASS);
                put(x, up, z, s);
                if (e > deck - 1.1) {
                    boolean gap = Math.floorMod((int) Math.round(sp * 2), 2) == 0;
                    put(x, up + 1, z, gap ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB) : b(Blocks.WAXED_OXIDIZED_COPPER_GRATE));
                }
            }
        // bone ribs fanning down under it to the stem
        for (int i = 0; i < spokes; i += Math.max(1, spokes / 12)) {
            double a = (i + 0.0) / spokes * Math.PI * 2;
            double ax = Math.cos(a), az = Math.sin(a);
            double x0 = ax * (deck - 1.5), z0 = az * (deck - 1.5);
            double x1 = ax * stem, z1 = az * stem;
            int steps = 24;
            for (int k = 0; k < steps; k++) {
                double t0 = (double) k / steps, t1 = (double) (k + 1) / steps;
                double ya = up - 1 - Math.pow(t0, 1.6) * (up - flareFrom), yb = up - 1 - Math.pow(t1, 1.6) * (up - flareFrom);
                line(Mth.lerp(t0, x0, x1), ya, Mth.lerp(t0, z0, z1), Mth.lerp(t1, x0, x1), yb, Mth.lerp(t1, z0, z1),
                        u >= 2 ? 0.6 : 0, (x, y, z) -> b(Blocks.BONE_BLOCK));
            }
        }
        // strands hanging from the edge: chains, green glass, glowing tips, a few lanterns
        int strands = Mth.clamp((int) Math.round(deck * 2 * Math.PI / Math.max(2, 2.5 * u)), 10, 120);
        for (int i = 0; i < strands; i++) {
            double a = (i + rnd(i, 1, 125) * 0.4) / strands * Math.PI * 2;
            int x = (int) Math.round(Math.cos(a) * (deck - 0.6)), z = (int) Math.round(Math.sin(a) * (deck - 0.6));
            int len = 2 + (int) ((0.25 + 0.75 * Math.pow(rnd(i, 2, 125), 1.3)) * up * 0.7);
            int kind = (int) (rnd(i, 3, 125) * 4);
            for (int k = 1; k <= len; k++) {
                BlockState s = kind == 0 ? (k % 4 == 0 ? b(Blocks.GREEN_STAINED_GLASS) : b(Blocks.LIME_STAINED_GLASS))
                        : kind == 1 && k % 3 == 0 ? b(Blocks.LIME_STAINED_GLASS) : kind == 2 && k % 5 == 0
                        ? b(Blocks.BONE_BLOCK) : b(Blocks.CHAIN);
                put(x, up - k, z, s);
            }
            int end = up - len - 1;
            if (kind == 3) put(x, end, z, with(b(Blocks.LANTERN), BlockStateProperties.HANGING, true));
            else if (kind == 2) put(x, end, z, b(Blocks.VERDANT_FROGLIGHT));
            else put(x, end, z, facing(Blocks.END_ROD, Direction.DOWN));
        }
        // amethyst on the deck's edge
        int crystals = Mth.clamp((int) Math.round(3 * u), 2, 16);
        for (var s : ring(crystals, deck - 2.5, 0.6, 126)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            put(x, up + 1, z, b(Blocks.AMETHYST_BLOCK));
            put(x, up + 2, z, facing(Blocks.AMETHYST_CLUSTER, Direction.UP));
        }
        // crystals on the mound: amethyst clumps and leaning green glass shards
        int clumps = Mth.clamp((int) Math.round(4 * u), 3, 24);
        for (var s : ring(clumps, moundR * 0.8 + stem, 0.9, 127)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            int y = top(x, z);
            if (rnd(x, z, 128) < 0.5) {
                double cr = Math.max(1, 1.3 * u);
                ball(x, y, z, cr, cr, cr, (bx, by, bz) -> by <= top(bx, bz) - 1 ? null : b(Blocks.AMETHYST_BLOCK));
                for (Direction d : Direction.values())
                    if (d != Direction.DOWN) {
                        int c = (int) Math.ceil(cr) + 1;
                        soft(x + d.getStepX() * c, y + (d == Direction.UP ? c : 1), z + d.getStepZ() * c,
                                facing(rnd(x, d.ordinal(), z, 129) < 0.5 ? Blocks.AMETHYST_CLUSTER
                                        : Blocks.LARGE_AMETHYST_BUD, d));
                    }
            } else {
                int len = sc(5, 3);
                double lx = (rnd(x, z, 130) - 0.5) * 0.8, lz = (rnd(x, z, 131) - 0.5) * 0.8;
                line(x, y, z, x + lx * len, y + len, z + lz * len, u >= 1.5 ? 0.7 : 0,
                        (bx, by, bz) -> rnd(bx, by, bz, 132) < 0.6 ? b(Blocks.LIME_STAINED_GLASS)
                                : b(Blocks.GREEN_STAINED_GLASS));
            }
        }
        // clouds round it at different heights
        int clouds = Mth.clamp((int) Math.round(2 + 1.5 * u), 3, 9);
        for (var s : ring(clouds, Math.min(deck + Math.max(4, r * 0.25), r - Math.max(2, 3.2 * u) * 1.6), 0.7, 133)) {
            int i = (int) Math.round(s[2] * 100);
            double cy = up * (0.45 + rnd(i, 1, 134) * 0.7);
            double cs = Math.max(2, 3.2 * u) * (0.7 + rnd(i, 2, 134) * 0.6);
            int puffs = 3 + (int) (rnd(i, 3, 134) * 3);
            for (int p = 0; p < puffs; p++) {
                double px = s[0] + (rnd(i, p, 135) - 0.5) * cs * 2.2, pz = s[1] + (rnd(i, p, 136) - 0.5) * cs * 1.6;
                double pr = cs * (0.55 + rnd(i, p, 137) * 0.5);
                double base = cy - pr * 0.25;
                ball(px, cy, pz, pr, pr * 0.45, pr, (x, y, z) -> y < base ? null : y <= base + 0.6
                        ? b(Blocks.LIGHT_GRAY_WOOL) : rnd(x, y, z, 138) < 0.75 ? b(Blocks.WHITE_WOOL)
                        : b(Blocks.WHITE_CONCRETE));
            }
        }
    }
}
