package net.jj.giants.fun;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Pitchgut's den: a scorched crater. The bowl darkens from basalt at the lip to blackstone and goo stains at the
 * bottom, with magma cracks winding through it; pools of goo lie in the bottom (black tar under glass, amber honey, a
 * green-black slime bog and one lit red from magma under it), black goo runs down the sides into them, half-buried
 * ribcages and skulls with glowing sockets lie on the slopes, his eyes (pale, a yellow glowing iris, a black pupil)
 * stare up out of the ground, steaming vents smoke, and a jagged basalt rim stands round it all with a ring of burnt
 * ground outside.
 */
final class Crater extends Plan {
    private final int deep, rimW, rimH, scorch;
    /** the pools: x, z, radius, level, kind (0 tar, 1 honey, 2 slime bog, 3 magma) */
    private final List<double[]> pools = new ArrayList<>();

    Crater(int r, double h) {
        super(r, h);
        deep = Mth.clamp((int) Math.round(r * 0.2), 3, 24);
        rimW = sc(4, 2);
        rimH = sc(2.5, 1);
        scorch = sc(5, 2);
    }

    @Override
    public int reach() {
        return r + rimW + scorch + 1;
    }

    /** the bowl's height, before the pools */
    private int bowl(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= r) {
            double q = d / r;
            double bump = (noise(dx, dz, Math.max(4, 6 * u), 31) - 0.5) * Math.min(2.5, u);
            return (int) Math.round(-deep * (1 - q * q) + bump * (1 - q));
        }
        if (d <= r + rimW) {
            double t = (d - r) / rimW;
            double jag = noise(dx, dz, Math.max(2, 2.5 * u), 32);
            return (int) Math.round(rimH * Math.sin(t * Math.PI) * (0.6 + jag * 0.8));
        }
        return 0;
    }

    private double[] pool(int dx, int dz) {
        for (double[] p : pools) {
            double e = Math.hypot(dx - p[0], dz - p[1]) / p[2];
            if (e + (noise(dx, dz, 3, 33) - 0.5) * 0.35 < 1) return p;
        }
        return null;
    }

    @Override
    int top(int dx, int dz) {
        double[] p = pool(dx, dz);
        return p != null ? (int) p[3] : bowl(dx, dz);
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > reach()) return;
        o.support = b(Blocks.BASALT);
        if (d > r + rimW) {
            // burnt ground outside, thinning out
            double t = (d - r - rimW) / scorch;
            double k = rnd(dx, dz, 34) + (noise(dx, dz, 4, 35) - 0.5) * 0.5;
            BlockState s = k > t * 1.1 + 0.15 ? pick(rnd(dx, dz, 36), b(Blocks.BASALT), b(Blocks.BLACKSTONE),
                    b(Blocks.COARSE_DIRT), b(Blocks.TUFF)) : k > t * 0.8 ? b(Blocks.COARSE_DIRT) : null;
            if (s == null) {
                o.support = null;
                return;
            }
            o.set(f, s);
            o.clearAbove(f + 1);
            return;
        }
        int y = bowl(dx, dz);
        double[] p = pool(dx, dz);
        if (p != null) {
            int lv = (int) p[3];
            double e = Math.hypot(dx - p[0], dz - p[1]) / p[2];
            int kind = (int) p[4];
            BlockState top, under;
            if (kind == 0) {
                top = e > 0.85 && rnd(dx, dz, 37) < 0.5 ? b(Blocks.BLACK_CONCRETE) : b(Blocks.BLACK_STAINED_GLASS);
                under = b(Blocks.BLACK_CONCRETE);
            } else if (kind == 1) {
                // amber goo: honey under glass
                top = e > 0.8 ? b(Blocks.HONEYCOMB_BLOCK) : b(Blocks.ORANGE_STAINED_GLASS);
                under = rnd(dx, dz, 38) < 0.5 ? b(Blocks.HONEYCOMB_BLOCK) : b(Blocks.ORANGE_TERRACOTTA);
            } else if (kind == 2) {
                // a slime bog: green under glass, mud round it
                top = e > 0.75 ? b(Blocks.MUD) : rnd(dx, dz, 38) < 0.4 ? b(Blocks.LIME_STAINED_GLASS)
                        : b(Blocks.GREEN_STAINED_GLASS);
                under = rnd(dx, dz, 39) < 0.5 ? b(Blocks.GREEN_CONCRETE) : b(Blocks.LIME_TERRACOTTA);
            } else {
                top = e > 0.8 ? b(Blocks.BLACKSTONE) : b(Blocks.RED_STAINED_GLASS);
                under = e > 0.8 ? b(Blocks.BLACKSTONE) : b(Blocks.MAGMA_BLOCK);
            }
            for (int yy = Math.min(y, lv - 2); yy <= lv - 2; yy++) o.set(f + yy, b(Blocks.BLACKSTONE));
            o.set(f + lv - 1, under);
            o.set(f + lv, top);
            o.clearAbove(f + lv + 1);
            return;
        }
        double q = d <= r ? 1 - (d / r) * (d / r) : 0;
        double mix = q + (noise(dx, dz, Math.max(3, 4 * u), 39) - 0.5) * 0.45;
        double k = rnd(dx, dz, 40);
        BlockState s;
        double crack = Math.abs(noise(dx, dz, Math.max(5, 7 * u), 41) - 0.5);
        if (d <= r && q > 0.15 && crack < 0.012 + 0.008 * q) s = b(Blocks.MAGMA_BLOCK);
        else if (mix > 0.72) s = k < 0.18 ? b(Blocks.BLACK_CONCRETE) : k < 0.3 ? b(Blocks.COAL_BLOCK)
                : k < 0.65 ? b(Blocks.BLACKSTONE) : b(Blocks.BASALT);
        else if (mix > 0.38) s = k < 0.3 ? b(Blocks.SMOOTH_BASALT) : k < 0.55 ? b(Blocks.DEEPSLATE)
                : k < 0.75 ? b(Blocks.COBBLED_DEEPSLATE) : k < 0.85 ? b(Blocks.BLACKSTONE) : b(Blocks.TUFF);
        else s = k < 0.45 ? b(Blocks.BASALT) : k < 0.7 ? b(Blocks.BLACKSTONE) : k < 0.85 ? b(Blocks.SMOOTH_BASALT)
                : b(Blocks.TUFF);
        if (s.is(Blocks.BASALT)) s = log(Blocks.BASALT, Direction.Axis.Y);
        if (y > 0) for (int i = 0; i < y; i++) o.set(f + i, i == 0 ? b(Blocks.BLACKSTONE) : b(Blocks.BASALT));
        o.set(f + y, s);
        o.set(f + y - 1, s.is(Blocks.MAGMA_BLOCK) ? b(Blocks.MAGMA_BLOCK) : b(Blocks.BASALT));
        o.clearAbove(f + y + 1);
    }

    @Override
    void plan() {
        // the pools in the bottom: tar in the middle, others round it
        int n = Mth.clamp((int) Math.round(1 + u * 1.2), 2, 5);
        double main = Math.max(2.2, r * 0.22);
        pools.add(new double[] {0, 0, main, bowl(0, 0) + 1, 0});
        for (var s : ring(n - 1 + 1, r * 0.38, 0.5, 42)) {
            if (pools.size() >= n + 1) break;
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            int kind = pools.size() % 4 == 0 ? 0 : pools.size() % 4;
            pools.add(new double[] {x, z, Math.max(1.6, r * 0.1), bowl(x, z) + 1, kind});
        }
        for (double[] p : pools) {
            double lo = 99;
            for (int a = 0; a < 16; a++) lo = Math.min(lo, bowl((int) (p[0] + Math.cos(a) * p[2]),
                    (int) (p[1] + Math.sin(a) * p[2])));
            p[3] = Math.min(p[3], lo);
        }
        // goo running down the sides into the tar
        int runs = Mth.clamp((int) Math.round(3 * u), 2, 10);
        for (var s : ring(runs, r, 0.6, 43)) {
            double a = s[2];
            double w = Math.max(0.6, u * 0.8);
            double wig = rnd(runs, (int) (a * 100), 44) * 6;
            for (double t = r + rimW * 0.6; t > main * 0.8; t -= 0.5) {
                double aa = a + Math.sin(t / Math.max(3, 4 * u) + wig) * 0.06;
                double cx = Math.cos(aa) * t, cz = Math.sin(aa) * t;
                double ww = w * (0.6 + 0.6 * (t / r));
                for (int x = (int) Math.floor(cx - ww); x <= Math.ceil(cx + ww); x++)
                    for (int z = (int) Math.floor(cz - ww); z <= Math.ceil(cz + ww); z++)
                        if (Math.hypot(x - cx, z - cz) <= ww && pool(x, z) == null) {
                            int y = top(x, z);
                            put(x, y, z, rnd(x, z, 45) < 0.2 ? b(Blocks.BLACK_STAINED_GLASS)
                                    : b(Blocks.BLACK_CONCRETE));
                        }
            }
        }
        // his eyes in the ground, staring up
        int eyes = Mth.clamp((int) Math.round(3 * u * u), 3, 48);
        for (int i = 0; i < eyes; i++) {
            double a = rnd(i, 1, 46) * Math.PI * 2, t = r * (0.3 + rnd(i, 2, 46) * 0.62);
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            if (pool(x, z) != null) continue;
            eye(x, z, u >= 1.4 && rnd(i, 3, 46) < 0.6 ? 2 : 1);
        }
        // ribcages and skulls, half buried in the slopes
        int cages = Mth.clamp((int) Math.round(1.2 * u), 1, 6);
        for (var s : ring(cages, r * 0.62, 0.7, 47)) ribcage(s[0], s[1], s[2] + Math.PI / 2 + rnd((int) s[0], 0, 48) - 0.5);
        int skulls = Mth.clamp((int) Math.round(1.5 * u), 1, 7);
        for (var s : ring(skulls, r * 0.45, 0.8, 49)) skull((int) Math.round(s[0]), (int) Math.round(s[1]),
                Math.max(1.6, 2.2 * u), s[2]);
        // loose bones
        int loose = Mth.clamp((int) Math.round(6 * u * u), 3, 80);
        for (int i = 0; i < loose; i++) {
            double a = rnd(i, 4, 50) * Math.PI * 2, t = r * (0.2 + rnd(i, 5, 50) * 0.8);
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            if (pool(x, z) != null) continue;
            int len = 1 + (int) (rnd(i, 6, 50) * Math.max(1, 3 * u));
            boolean ax = rnd(i, 7, 50) < 0.5;
            for (int k = 0; k < len; k++) {
                int bx = ax ? x + k : x, bz = ax ? z : z + k;
                put(bx, top(bx, bz) + (k == 0 || k == len - 1 ? 1 : 0), bz,
                        log(Blocks.BONE_BLOCK, ax ? Direction.Axis.X : Direction.Axis.Z));
            }
        }
        // steaming vents: a little basalt cone round a smoking fire
        int vents = Mth.clamp((int) Math.round(3 * u), 2, 14);
        for (var s : ring(vents, r * 0.72, 0.9, 51)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            if (pool(x, z) != null) continue;
            int y = top(x, z), ch = sc(1.5, 1);
            for (int dx = -ch - 1; dx <= ch + 1; dx++)
                for (int dz = -ch - 1; dz <= ch + 1; dz++) {
                    double e = Math.hypot(dx, dz);
                    int hh = (int) Math.round(ch + 1 - e);
                    if (e < 0.5 || hh < 1) continue;
                    for (int k = 1; k <= hh; k++) put(x + dx, y + k, z + dz, rnd(x + dx, k, z + dz, 52) < 0.25
                            ? b(Blocks.MAGMA_BLOCK) : log(Blocks.BASALT, Direction.Axis.Y));
                }
            for (int k = 0; k < ch; k++) put(x, y + k, z, b(Blocks.HAY_BLOCK));
            put(x, y + ch, z, with(lit(Blocks.CAMPFIRE), BlockStateProperties.SIGNAL_FIRE, true));
        }
        // the jagged rim: basalt spires
        int spires = Mth.clamp((int) Math.round(2 * Math.PI * r / Math.max(4, 5 * u)), 8, 120);
        for (var s : ring(spires, r + rimW * 0.5, 0.8, 53)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            int y = top(x, z), tall = 1 + (int) (rnd(x, z, 54) * sc(3.5, 2));
            for (int k = 1; k <= tall; k++) put(x, y + k, z, rnd(x, k, z, 55) < 0.2 ? b(Blocks.BLACKSTONE)
                    : log(Blocks.BASALT, Direction.Axis.Y));
            if (u >= 1.5 && tall > 2) for (Direction d : Direction.Plane.HORIZONTAL)
                if (rnd(x, d.ordinal(), z, 56) < 0.5) for (int k = 1; k <= tall / 2; k++)
                    put(x + d.getStepX(), y + k, z + d.getStepZ(), log(Blocks.BASALT, Direction.Axis.Y));
        }
        // burnt logs and dead bushes outside
        int logs = Mth.clamp((int) Math.round(2 * u), 1, 10);
        for (var s : ring(logs, r + rimW + scorch * 0.5, 0.9, 57)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            boolean ax = rnd(x, z, 58) < 0.5;
            for (int k = 0; k < sc(3, 2); k++)
                put(ax ? x + k : x, 1, ax ? z : z + k, log(Blocks.STRIPPED_DARK_OAK_LOG, ax ? Direction.Axis.X
                        : Direction.Axis.Z));
        }
        for (int i = 0; i < Mth.clamp((int) (10 * u * u), 4, 120); i++) {
            double a = rnd(i, 8, 59) * Math.PI * 2, t = r + rimW + rnd(i, 9, 59) * scorch;
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            soft(x, 1, z, b(Blocks.DEAD_BUSH));
        }
    }

    /** an eye: a black pupil, a glowing yellow iris, pale round it, a little sunk with a fleshy lid */
    private void eye(int x, int z, int size) {
        int y = top(x, z);
        int out = size + 1;
        for (int dx = -out; dx <= out; dx++)
            for (int dz = -out; dz <= out; dz++) {
                double e = Math.hypot(dx, dz);
                int yy = top(x + dx, z + dz);
                if (e < 0.5) put(x + dx, yy, z + dz, b(Blocks.BLACK_CONCRETE));
                else if (e < size + 0.5) put(x + dx, yy, z + dz, b(Blocks.OCHRE_FROGLIGHT));
                else if (e < out + 0.4) put(x + dx, yy, z + dz, rnd(x + dx, z + dz, 60) < 0.7
                        ? b(Blocks.WHITE_TERRACOTTA) : b(Blocks.PINK_TERRACOTTA));
            }
        // the lid: a raised fold of flesh on one side
        for (int dx = -out; dx <= out; dx++) {
            int zz = z - out - 1;
            if (Math.abs(dx) <= out - (size > 1 ? 0 : 1)) put(x + dx, top(x + dx, zz) + 1, zz,
                    b(Blocks.PINK_TERRACOTTA));
        }
    }

    /** a ribcage along a line through (x, z) at angle a, half sunk into the ground */
    private void ribcage(double x, double z, double a) {
        int len = sc(11, 5), rr = sc(3.5, 2);
        double ax = Math.cos(a), az = Math.sin(a), px = -az, pz = ax;
        Direction.Axis along = Math.abs(ax) > Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
        double sink = rr * 0.35;
        for (int i = -len / 2; i <= len / 2; i++) {
            double sx = x + ax * i, sz = z + az * i;
            int gy = top((int) Math.round(sx), (int) Math.round(sz));
            put((int) Math.round(sx), gy, (int) Math.round(sz), log(Blocks.BONE_BLOCK, along));
            if (Math.floorMod(i, 2) != 0 || Math.abs(i) > len / 2 - 1) continue;
            double taper = 1 - Math.abs(i) / (double) (len / 2 + 2) * 0.5;
            for (double t = 0; t <= Math.PI; t += Math.PI / (6 + 4 * rr)) {
                double ox = Math.cos(t) * rr * taper, oy = Math.sin(t) * rr * 1.2 * taper - sink;
                if (oy < -0.4) continue;
                int bx = (int) Math.round(sx + px * ox), bz = (int) Math.round(sz + pz * ox);
                int by = gy + (int) Math.round(oy);
                if (by <= top(bx, bz) - 1) continue;
                // broken ribs: some tops missing
                if (t > Math.PI * 0.35 && t < Math.PI * 0.65 && rnd(i, (int) (x * 7 + z), 61) < 0.35) continue;
                put(bx, by, bz, oy > rr * 0.6 ? log(Blocks.BONE_BLOCK, along == Direction.Axis.X ? Direction.Axis.Z
                        : Direction.Axis.X) : log(Blocks.BONE_BLOCK, Direction.Axis.Y));
            }
        }
    }

    /** a skull: a dome of bone with two glowing sockets, half sunk, looking the way a points */
    private void skull(int x, int z, double s, double a) {
        int gy = top(x, z);
        double cy = gy + s * 0.4;
        double fx = Math.cos(a), fz = Math.sin(a);
        ball(x, cy, z, s, s * 0.85, s, (bx, by, bz) -> by < gy ? null : b(Blocks.BONE_BLOCK));
        ball(x, cy, z, s - 1, s * 0.85 - 1, s - 1, (bx, by, bz) -> by < gy ? null : b(Blocks.BLACK_CONCRETE));
        // the sockets, toward the front
        double px = -fz, pz = fx;
        for (int side = -1; side <= 1; side += 2) {
            double ex = x + fx * (s - 0.3) + px * side * s * 0.42, ez = z + fz * (s - 0.3) + pz * side * s * 0.42;
            double ey = cy + s * 0.3, er = Math.max(0.7, s * 0.3);
            ball(ex, ey, ez, er, er, er, (bx, by, bz) -> b(Blocks.AIR));
            put((int) Math.round(ex - fx * er), (int) Math.round(ey), (int) Math.round(ez - fz * er), b(Blocks.SHROOMLIGHT));
        }
        // teeth along the jaw
        for (int k = -1; k <= 1; k++) {
            int tx = (int) Math.round(x + fx * s + px * k * s * 0.3), tz = (int) Math.round(z + fz * s + pz * k * s * 0.3);
            put(tx, gy, tz, b(Blocks.CALCITE));
        }
    }
}
