package net.jj.hollowbell.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Bell Hollows worked out from nothing but the ground's seed, its centre and where a block is. No world is
 * touched in here: {@link HomeGround} asks this what goes in a chunk and puts it there, and the ground lab draws
 * pictures from the very same answers. Because every hill, bowl, shard and spire comes from the seed and the
 * block's own x/z, a feature bigger than a chunk comes out whole whichever of its chunks is made first.
 *
 * The land: soft rolling hills scooped with many round hollows, pale calcite and bone with mint grass between,
 * banded where a slope cuts through it. On it: fallen shards of an old glass bell, tendril reefs, spore gardens
 * and still pools in the hollows, calcite spires, fallen ribs, drifting lights on thin posts. In the middle,
 * his den: a colossal glass bell half sunk in the ground, broken open on one side, lights hanging inside.
 */
public final class BellPlan {

    /** how high the land is at a column, as the world's own generator makes it (never loads anything) */
    public interface Ground { int height(int x, int z); }

    /** what a block of his ground is made of; the painter turns these into real blocks, the lab into colours */
    public enum Mat {
        KEEP(0, 0),
        CALCITE(0xDDDEDA, 1), DIORITE(0xBCBCBD, 1), POLISHED_DIORITE(0xC6C7C8, 1), END_STONE(0xDBDE9E, 1),
        BONE(0xE2DCC6, 1), SMOOTH_STONE(0xA0A0A0, 1), MOSS(0x5C7430, 1), GRASS(0x9BD4AE, 1),
        VERDANT(0xDDF0CF, 1), PEARL(0xF3E6E8, 1), OCHRE(0xF7EDB8, 1),
        LIME_GLASS(0x86CE2A, 2), WHITE_GLASS(0xECF2EE, 2), GRAY_GLASS(0x9DA3A0, 2), GREEN_GLASS(0x6B8A3A, 2),
        MOSS_CARPET(0x62792F, 3), GLOW_LICHEN(0x7C9A86, 3), DRIPLEAF_LOW(0x5E8A3A, 3), DRIPLEAF_HIGH(0x74A84A, 3),
        END_ROD(0xF6F0EA, 3), CHAIN(0x3F4550, 3), WATER(0x74CDBB, 4),
        // his own blocks (see ModBlocks): pale veined calcite, glowing tendril glass, fallen bell glass, spore moss
        BELL_CALCITE(0xE4EBDF, 1), TENDRIL_GLASS(0x9CE0A0, 2), BELL_SHARD(0xB9DCC4, 2), SPORE_MOSS(0x7FA257, 3);

        public final int rgb;
        /** 1 solid, 2 glass, 3 small or thin (plants, rods, chains), 4 water */
        public final int kind;
        Mat(int rgb, int kind) { this.rgb = rgb; this.kind = kind; }
        public boolean glows() { return this == VERDANT || this == PEARL || this == OCHRE || this == END_ROD || this == TENDRIL_GLASS || this == SPORE_MOSS; }
    }

    public static final int OUT = 0, FRINGE = 1, MIDDLE = 2, CORE = 3;

    /** the dome of his den: half as wide as it is across, and how high it stands over the ground */
    public static final int DOME_R = 25, DOME_H = 23;
    /** the den's flattened ground reaches this far, then blends back into the hills */
    public static final int DEN_FLAT = 46, DEN_AREA = 82;
    /** how far a column's ground may be lowered or raised */
    public static final int MAX_DOWN = 8, MAX_UP = 14;
    /** per column: at most this many ground layers and this many blocks above the ground */
    public static final int MAXL = 24, MAXA = 72;
    /** how deep the world lays his ground (the top and three under it); /giants paint may ask for more */
    public static final int MADE_DEPTH = 4;
    /** the most layers a column can be given: a painted depth of 64 under land raised up to 14 or so, and room over */
    public static final int LAYERS = 104;

    /**
     * How deep /giants paint lays his ground, counting the new top block as the first (0: as the world lays it,
     * MADE_DEPTH deep, and two blocks of bed under water). Only a painter's own plan sets it: the plans the world's
     * generation uses are shared and keep 0.
     */
    public int paintDepth = 0;

    public int depth() { return paintDepth > 0 ? paintDepth : MADE_DEPTH; }

    /**
     * The block k down from a column's top (k = 0 the top, at y), with topMat its top block and y0 the old ground:
     * the first MADE_DEPTH exactly what the world lays (lime glass has froglight under it, grass keeps the land's own
     * block under it, the fringe keeps what it had), and deeper (only a painting goes deeper) his own rock.
     */
    public Mat layer(int x, int y, int z, int k, Mat topMat, int y0) {
        if (k == 0) return topMat;
        Mat m = strata(x, y, z);
        if (k >= MADE_DEPTH && paintDepth > 0) return m;
        if (k == 1 && topMat == Mat.LIME_GLASS) m = Mat.VERDANT;
        if (topMat == Mat.KEEP && y <= y0) m = Mat.KEEP;
        if (k == 1 && topMat == Mat.GRASS && y <= y0) m = Mat.KEEP;
        return m;
    }

    public final long seed;
    public final int cx, cz, radius, sea, minY, maxY;
    private final Ground ground;
    private final double breakAngle, ribPhase;
    private int denY = Integer.MIN_VALUE;
    /** false for ground painted by an admin's command: the same land and things on it, but no den in the middle */
    public boolean withDen = true;

    public BellPlan(long seed, int cx, int cz, int radius, int sea, int minY, int maxY, Ground ground) {
        this.seed = seed; this.cx = cx; this.cz = cz; this.radius = radius;
        this.sea = sea; this.minY = minY; this.maxY = maxY; this.ground = ground;
        this.breakAngle = h01(seed, 1, 2, 900) * Math.PI * 2 - Math.PI;
        this.ribPhase = h01(seed, 3, 4, 901) * Math.PI / 4;
    }

    // ================================================================== hashing and noise (pure)

    public static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b;
        h ^= h >>> 32; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        return h;
    }

    public static long hash(long seed, int a, int b, int c) {
        return mix(mix(seed, a), ((long) b << 32) ^ (c & 0xffffffffL));
    }

    /** 0..1 */
    public static double h01(long seed, int a, int b, int c) {
        return (hash(seed, a, b, c) >>> 11) * 0x1.0p-53;
    }

    private double h(int a, int b, int c) { return h01(seed, a, b, c); }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** gradient noise, about 0..1 with 0.5 the middle */
    private double perlin(int salt, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double n00 = grad(salt, x0, z0, fx, fz), n10 = grad(salt, x0 + 1, z0, fx - 1, fz);
        double n01 = grad(salt, x0, z0 + 1, fx, fz - 1), n11 = grad(salt, x0 + 1, z0 + 1, fx - 1, fz - 1);
        double u = fade(fx), v = fade(fz);
        double a = n00 + (n10 - n00) * u, b = n01 + (n11 - n01) * u;
        double r = a + (b - a) * v;
        return Math.max(0, Math.min(1, 0.5 + r * 0.72));
    }

    private double grad(int salt, int ix, int iz, double dx, double dz) {
        double a = h(ix, iz, salt) * Math.PI * 2;
        return Math.cos(a) * dx + Math.sin(a) * dz;
    }

    private double fbm(int salt, double x, double z, int oct) {
        double sum = 0, amp = 1, tot = 0, f = 1;
        for (int o = 0; o < oct; o++) {
            sum += amp * perlin(salt + o * 131, x * f, z * f);
            tot += amp; amp *= 0.5; f *= 2.03;
        }
        return sum / tot;
    }

    /** smooth hash noise round the circle, 0..1 */
    private double ring(int salt, double theta, int freq, int oct) {
        double sum = 0, amp = 0.5, total = 0;
        for (int o = 0; o < oct; o++) {
            double t = theta / (Math.PI * 2) * freq;
            int i0 = (int) Math.floor(t);
            double f = t - i0;
            double v0 = h(salt + o, Math.floorMod(i0, freq), 7), v1 = h(salt + o, Math.floorMod(i0 + 1, freq), 7);
            double s = f * f * (3 - 2 * f);
            sum += amp * (v0 + (v1 - v0) * s);
            total += amp; amp *= 0.5; freq *= 2;
        }
        return sum / total;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    // ================================================================== the outline and the zones

    /**
     * How far out a column is: 0 at the centre, 1 at the edge. The edge wobbles with the angle, swells into a few
     * big lobes, and a broad noise pushes it in and out so there are bays and headlands, not a circle.
     */
    public double dn(int x, int z) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= radius) return 2;
        double th = Math.atan2(dz, dx) + Math.PI;
        double edge = radius * (0.58 + 0.16 * ring(20, th, 5, 3) + 0.12 * ring(40, th, 3, 1));
        double warp = (fbm(11, x / 170.0, z / 170.0, 2) - 0.5) * 0.36 * smooth(0, radius * 0.4, d);
        return d / edge + warp;
    }

    public static int zoneOf(double dn) { return dn < 0.25 ? CORE : dn < 0.8 ? MIDDLE : dn < 1 ? FRINGE : OUT; }

    public int zone(int x, int z) { return zoneOf(dn(x, z)); }

    /** is this column his? All of the middle is; the fringe thins out in patches to nothing. */
    public boolean painted(int x, int z) { return paintedAt(x, z, dn(x, z)); }

    private boolean paintedAt(int x, int z, double dn) {
        if (dn < 0.8) return true;
        if (dn >= 1) return false;
        double p = 1 - smooth(0.8, 1.0, dn);
        double n = 0.95 * fbm(12, x / 46.0, z / 46.0, 3) + 0.05 * h(x, z, 13);
        return p > (n - 0.5) * 1.6 + 0.5;
    }

    /** a chunk that may hold any of his ground at all */
    public boolean near(int chunkX, int chunkZ) {
        double dx = (chunkX << 4) + 8 - cx, dz = (chunkZ << 4) + 8 - cz;
        return dx * dx + dz * dz < (radius + 24.0) * (radius + 24.0);
    }

    private double dist(int x, int z) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ================================================================== the shape of the land

    /** the height of the land at a column as the world's generator makes it, before caves and ravines cut it */
    public int worldHeight(int x, int z) { return ground.height(x, z); }

    /** the den sits at the height the world's generator gives its centre */
    public int denY() {
        if (denY == Integer.MIN_VALUE) denY = ground.height(cx, cz);
        return denY;
    }

    private static final int BOWL_CELL = 21;

    /** one round hollow */
    private static final class Bowl {
        int ci, cj, px, pz; double r, depth; boolean garden, pool;
    }

    private final Map<Long, Bowl> bowls = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Bowl> e) { return size() > 4096; }
    };
    private static final Bowl NONE = new Bowl();

    private Bowl bowl(int ci, int cj) {
        long key = ((long) ci << 32) ^ (cj & 0xffffffffL);
        Bowl b = bowls.get(key);
        if (b != null) return b == NONE ? null : b;
        b = makeBowl(ci, cj);
        bowls.put(key, b == null ? NONE : b);
        return b;
    }

    private Bowl makeBowl(int ci, int cj) {
        int px = ci * BOWL_CELL + 3 + (int) (h(ci, cj, 100) * (BOWL_CELL - 6));
        int pz = cj * BOWL_CELL + 3 + (int) (h(ci, cj, 101) * (BOWL_CELL - 6));
        if (withDen && dist(px, pz) < DEN_AREA + 6) return null;
        double dn = dn(px, pz);
        int zone = zoneOf(dn);
        // the hollows come in fields: whole stretches pitted with them, open meadow between
        double field = smooth(0.36, 0.62, fbm(60, px / 260.0, pz / 260.0, 2));
        double chance = (zone == CORE ? 0.55 : zone == MIDDLE ? 0.9 : zone == FRINGE ? 0.5 : 0) * (0.12 + 0.88 * field);
        if (h(ci, cj, 102) >= chance) return null;
        Bowl b = new Bowl();
        b.ci = ci; b.cj = cj; b.px = px; b.pz = pz;
        b.r = 4 + 8 * Math.pow(h(ci, cj, 103), 1.25);
        b.depth = Math.max(2, Math.min(7, 1.2 + (b.r - 4) * 0.55 + h(ci, cj, 104) * 1.6));
        double g = h(ci, cj, 105);
        b.garden = g < (zone == MIDDLE ? 0.3 : zone == CORE ? 0.18 : 0.2);
        b.pool = h(ci, cj, 106) < (zone == FRINGE ? 0.15 : 0.34);
        return b;
    }

    /** what the hollows do to one column: how deep, which bowl it is in and how far across it (t, 0 = middle) */
    private static final class BowlHit { double v; Bowl in; double t = 9; }

    private void bowlsAt(int x, int z, BowlHit out) {
        out.v = 0; out.in = null; out.t = 9;
        int ci = Math.floorDiv(x, BOWL_CELL), cj = Math.floorDiv(z, BOWL_CELL);
        double deepest = 0, second = 0, lip = 0;
        for (int i = ci - 1; i <= ci + 1; i++) for (int j = cj - 1; j <= cj + 1; j++) {
            Bowl b = bowl(i, j);
            if (b == null) continue;
            double dx = x + 0.5 - (b.px + 0.5), dz = z + 0.5 - (b.pz + 0.5);
            double t = Math.sqrt(dx * dx + dz * dz) / b.r;
            if (t < 1) {
                double v = -b.depth * Math.pow(1 - t * t, 0.75);
                if (v < deepest) { second = deepest; deepest = v; }
                else if (v < second) second = v;
                if (t < out.t) { out.t = t; out.in = b; }
            } else if (t < 1.4) {
                lip = Math.max(lip, Math.min(1.4, b.depth * 0.25) * Math.sin(Math.PI * (t - 1) / 0.4));
            }
        }
        out.v = deepest < 0 ? deepest + 0.35 * second : lip;
    }

    /** the rolling hills under the hollows */
    private double hills(int x, int z) {
        return 12.5 * (fbm(21, x / 150.0, z / 150.0, 3) - 0.38) + 7 * (perlin(22, x / 430.0, z / 430.0) - 0.5);
    }

    /** where the ground wants to be at this column, before the safety limits */
    private double target(int x, int z, int y0, double dn, BowlHit b) {
        double off = hills(x, z) + b.v;
        off = Math.max(-MAX_DOWN, Math.min(MAX_UP, off));
        off *= 1 - smooth(0.70, 0.93, dn);
        // low land by the water (banks, beaches) is hardly shaped, so the hills never stand as walls over a river
        // (land lying far under the sea line with no sea on it, like a flat world, is shaped as usual)
        if (y0 >= sea - 4) off *= smooth(sea + 1, sea + 9, y0);
        double t = y0 + off;
        double d = dist(x, z);
        if (withDen && d < DEN_AREA) {
            double w = d <= DEN_FLAT ? 1 : 1 - smooth(DEN_FLAT, DEN_AREA, d);
            t = t + (denFloor(d) - t) * w;
        }
        return t;
    }

    /** the den's ground: level all round the bell, a shallow bowl inside it */
    private double denFloor(double d) {
        int y = denY();
        if (d >= DOME_R - 1.5) return y;
        double f = d / (DOME_R - 1.5);
        return y - 1 - 3.2 * (1 - f * f);
    }

    /** the height a feature standing here is measured from: the land as the generator makes it, shaped */
    private final Map<Long, Integer> anchors = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Integer> e) { return size() > 4096; }
    };

    private int anchor(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Integer a = anchors.get(key);
        if (a != null) return a;
        int y0 = ground.height(x, z);
        BowlHit b = new BowlHit();
        bowlsAt(x, z, b);
        int v = (int) Math.round(target(x, z, y0, dn(x, z), b));
        anchors.put(key, v);
        return v;
    }

    // ================================================================== what the ground is made of

    /** layered rock, so a slope or a cut shows bands of calcite, diorite, end stone and bone */
    public Mat strata(int x, int y, int z) {
        int phase = (int) Math.floor(6 * perlin(56, x / 70.0, z / 70.0));
        switch (Math.floorMod(y + phase, 11)) {
            case 2: case 3: return Mat.DIORITE;
            case 6: return Mat.END_STONE;
            case 9: return Mat.BONE;
            default: return Mat.CALCITE;
        }
    }

    /** the top block of an ordinary column: broad patches, never salt and pepper */
    private Mat surface(int x, int z, int y, int zone, BowlHit b) {
        if (zone == FRINGE) {
            double n = fbm(51, x / 30.0, z / 30.0, 2);
            return n < 0.47 ? Mat.KEEP : n < 0.64 ? Mat.CALCITE : n < 0.72 ? Mat.DIORITE : Mat.GRASS;
        }
        if (b.in != null && b.t < 1) {
            if (b.t < 0.13 && !b.in.pool && !b.in.garden) return Mat.VERDANT;          // the old glow at the bottom
            if (b.in.garden && b.t < 0.8) return Mat.MOSS;
            if (b.t < 0.55) return fbm(57, x / 7.0, z / 7.0, 2) < 0.45 ? Mat.END_STONE : Mat.CALCITE;
            return strata(x, y, z);
        }
        double p1 = fbm(52, x / 40.0, z / 40.0, 3), p2 = fbm(53, x / 13.0, z / 13.0, 2);
        if (zone == CORE) {
            if (Math.abs(fbm(55, x / 60.0, z / 60.0, 2) - 0.5) < 0.007 && fbm(58, x / 90.0, z / 90.0, 1) > 0.5) return Mat.VERDANT;
            if (fbm(54, x / 5.0, z / 5.0, 1) > 0.78 && p1 > 0.45) return Mat.TENDRIL_GLASS;       // old glass, lit under
            if (p1 > 0.64) return Mat.BONE;
            if (p1 < 0.33) return p2 > 0.55 ? Mat.GRASS : Mat.MOSS;
            return p2 > 0.62 ? Mat.END_STONE : p2 < 0.36 ? Mat.DIORITE : Mat.CALCITE;
        }
        if (p1 < 0.46) return p2 > 0.72 ? Mat.MOSS : Mat.GRASS;                         // mint meadows
        if (fbm(54, x / 5.0, z / 5.0, 1) > 0.8 && p1 > 0.55) return Mat.LIME_GLASS;
        if (p1 > 0.66) return p2 > 0.5 ? Mat.SMOOTH_STONE : Mat.BONE;
        return p2 > 0.64 ? Mat.END_STONE : p2 < 0.34 ? Mat.DIORITE : Mat.CALCITE;
    }

    // ================================================================== the bigger things standing on it

    /** anything bigger than one column: it knows the box it can reach and answers for a column inside it */
    private abstract static class Feature {
        int minX, maxX, minZ, maxZ;
        boolean touches(int x0, int z0) { return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15; }
        abstract void apply(BellPlan p, Out o, int i, int x, int z);
    }

    /** a curved piece of an old glass bell, fallen and stuck in the ground, maybe leaning */
    private static final class Shard extends Feature {
        double ax, az, ra, a0, half, tilt; int hmax, px, pz; boolean rim; int glassSalt;
        @Override void apply(BellPlan p, Out o, int i, int x, int z) {
            double qx = x + 0.5 - ax, qz = z + 0.5 - az;
            double q = Math.sqrt(qx * qx + qz * qz);
            double da = wrap(Math.atan2(qz, qx) - a0);
            if (Math.abs(da) > half) return;
            double s = da / half;
            double prof = Math.pow(1 - s * s, 0.5) * (s > 0.35 ? 1 - (s - 0.35) * 0.55 : 1);   // one end broken lower
            int hs = (int) Math.round(hmax * prof + (p.h(x, z, glassSalt) - 0.5) * 2.4);
            int base = p.anchor(px, pz) - 2;
            double u = da * ra;
            boolean rib = Math.floorMod((int) Math.round(u + 0.5 + glassSalt), 5) == 0;
            for (int k = 0; k <= hs; k++) {
                if (Math.abs(q - (ra + tilt * k)) >= 0.62) continue;
                Mat m;
                if (k <= 2) m = Mat.CALCITE;
                else if (rib || (rim && k == hs)) m = Mat.BELL_CALCITE;
                else if (k == hs - 1 && p.h(x, z, glassSalt + 1) < 0.3) m = Mat.WHITE_GLASS;
                else m = (k + glassSalt) % 7 == 0 ? Mat.GRAY_GLASS : (k + glassSalt) % 3 == 0 ? Mat.LIME_GLASS : Mat.BELL_SHARD;
                o.put(i, base + k, m);
            }
        }
    }

    /** a thin calcite spire, banded, now and then with a glowing eye near the top */
    private static final class Spire extends Feature {
        int px, pz, hs, phase; double rb; boolean eye;
        @Override void apply(BellPlan p, Out o, int i, int x, int z) {
            double dx = x + 0.5 - (px + 0.5), dz = z + 0.5 - (pz + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= rb + 0.5) return;
            double f = d / (rb + 0.5);
            int top = p.anchor(px, pz) + (int) Math.round(hs * Math.pow(1 - f, 1.35));
            if (d < 0.5) top = p.anchor(px, pz) + hs;
            int from = o.top[i] + 1;
            for (int y = from; y <= top; y++) {
                Mat m;
                if (y - from < 1) m = Mat.BONE;
                else if (Math.floorMod(y + phase, 5) == 0) m = Mat.DIORITE;
                else m = Mat.CALCITE;
                if (eye && d < 0.5 && y == top - Math.max(2, hs / 4)) m = Mat.VERDANT;
                o.put(i, y, m);
            }
        }
    }

    /** a fallen rib: a bone arch out of the ground, sometimes broken through */
    private static final class Arch extends Feature {
        double px, pz, ux, uz, len, ha; int gapA = -1, gapB = -1, ax, az;
        @Override void apply(BellPlan p, Out o, int i, int x, int z) {
            double rx = x + 0.5 - px, rz = z + 0.5 - pz;
            double along = rx * ux + rz * uz + len / 2, perp = -rx * uz + rz * ux;
            if (Math.abs(perp) > 0.8 || along < 0 || along > len) return;
            if (along >= gapA && along < gapB) return;
            int base = p.anchor(ax, az) - 2;
            double y1 = arc(along - 0.5), y2 = arc(along + 0.5);
            int lo = (int) Math.floor(Math.min(y1, y2)), hi = (int) Math.ceil(Math.max(y1, y2));
            if (hi - lo < 1) lo = hi - 1;
            for (int y = lo; y <= hi; y++) {
                Mat m = along < 2.5 || along > len - 2.5 ? Mat.CALCITE : Mat.BONE;
                o.put(i, base + y, m);
            }
        }
        double arc(double a) {
            double t = Math.max(-1, Math.min(1, (a - len / 2) / (len / 2)));
            return ha * Math.sqrt(1 - t * t);
        }
    }

    /** a few thin calcite posts with an end rod glowing on top: drifting lights */
    private static final class Lights extends Feature {
        int[] xs, zs, hs;
        @Override void apply(BellPlan p, Out o, int i, int x, int z) {
            for (int k = 0; k < xs.length; k++) {
                if (xs[k] != x || zs[k] != z) continue;
                if (o.bowlT[i] < 1.05 || o.reef[i]) return;
                int y = o.top[i];
                for (int s = 1; s <= hs[k]; s++) o.put(i, y + s, Mat.CALCITE);
                o.put(i, y + hs[k] + 1, Mat.END_ROD);
            }
        }
    }

    private static final int SHARD_CELL = 76, SPIRE_CELL = 46, ARCH_CELL = 104, LIGHT_CELL = 29;

    private final List<Feature> found = new ArrayList<>();

    /** every feature that reaches into this chunk */
    private List<Feature> collect(int x0, int z0) {
        found.clear();
        cells(x0, z0, SHARD_CELL, 22, this::shard);
        cells(x0, z0, SPIRE_CELL, 10, this::spires);
        cells(x0, z0, ARCH_CELL, 16, this::arch);
        cells(x0, z0, LIGHT_CELL, 4, this::lights);
        if (withDen) denSpires();
        found.removeIf(f -> !f.touches(x0, z0));
        return found;
    }

    private interface CellMaker { void make(int ci, int cj); }

    private void cells(int x0, int z0, int cell, int reach, CellMaker m) {
        for (int ci = Math.floorDiv(x0 - reach, cell); ci <= Math.floorDiv(x0 + 15 + reach, cell); ci++)
            for (int cj = Math.floorDiv(z0 - reach, cell); cj <= Math.floorDiv(z0 + 15 + reach, cell); cj++)
                m.make(ci, cj);
    }

    /** a spot in a cell, and whether it stands clear of the hollows and the den */
    private int[] spotIn(int ci, int cj, int cell, int salt, double denKeep) {
        int px = ci * cell + 2 + (int) (h(ci, cj, salt) * (cell - 4));
        int pz = cj * cell + 2 + (int) (h(ci, cj, salt + 1) * (cell - 4));
        if (withDen && dist(px, pz) < denKeep) return null;
        BowlHit b = new BowlHit();
        bowlsAt(px, pz, b);
        if (b.t < 1.1) return null;
        return new int[]{px, pz};
    }

    private void shard(int ci, int cj) {
        int[] s = spotIn(ci, cj, SHARD_CELL, 200, DEN_AREA - 14);
        if (s == null) return;
        int zone = zone(s[0], s[1]);
        double chance = zone == CORE ? 0.85 : zone == MIDDLE ? 0.3 : 0;
        if (h(ci, cj, 202) >= chance) return;
        Shard f = new Shard();
        f.px = s[0]; f.pz = s[1];
        f.ra = 9 + 9 * h(ci, cj, 203);
        double arcLen = 9 + 10 * h(ci, cj, 204);
        f.half = arcLen / 2 / f.ra;
        f.a0 = h(ci, cj, 205) * Math.PI * 2;
        f.ax = f.px + 0.5 - f.ra * Math.cos(f.a0);
        f.az = f.pz + 0.5 - f.ra * Math.sin(f.a0);
        f.hmax = 8 + (int) (8 * h(ci, cj, 206)) + (zone == CORE ? 2 : 0);
        f.tilt = (h(ci, cj, 207) - 0.5) * 0.7;
        f.rim = h(ci, cj, 208) < 0.5;
        f.glassSalt = 300 + (int) (h(ci, cj, 209) * 50);
        int reach = (int) Math.ceil(arcLen / 2 + Math.abs(f.tilt) * f.hmax + 3);
        f.minX = f.px - reach; f.maxX = f.px + reach; f.minZ = f.pz - reach; f.maxZ = f.pz + reach;
        found.add(f);
    }

    private void spires(int ci, int cj) {
        int[] s = spotIn(ci, cj, SPIRE_CELL, 220, DEN_AREA + 4);
        if (s == null) return;
        int zone = zone(s[0], s[1]);
        double chance = zone == CORE ? 0.6 : zone == MIDDLE ? 0.33 : zone == FRINGE ? 0.12 : 0;
        if (h(ci, cj, 222) >= chance) return;
        int n = 1 + (int) (h(ci, cj, 223) * (zone == FRINGE ? 1.5 : 3.2));
        for (int k = 0; k < n; k++) {
            Spire f = new Spire();
            double a = h(ci, cj, 230 + k) * Math.PI * 2, r = k == 0 ? 0 : 2.5 + 3.5 * h(ci, cj, 240 + k);
            f.px = s[0] + (int) Math.round(Math.cos(a) * r);
            f.pz = s[1] + (int) Math.round(Math.sin(a) * r);
            f.rb = (k == 0 ? 1.6 : 1.0) + 1.4 * h(ci, cj, 250 + k);
            int tall = zone == FRINGE ? 5 + (int) (4 * h(ci, cj, 260 + k)) : 9 + (int) (12 * h(ci, cj, 260 + k));
            f.hs = k == 0 ? tall + (zone == CORE ? 4 : 0) : (int) (tall * 0.65);
            f.phase = (int) (h(ci, cj, 270 + k) * 5);
            f.eye = h(ci, cj, 280 + k) < 0.35 && f.hs > 10;
            int reach = (int) Math.ceil(f.rb) + 1;
            f.minX = f.px - reach; f.maxX = f.px + reach; f.minZ = f.pz - reach; f.maxZ = f.pz + reach;
            found.add(f);
        }
    }

    private void arch(int ci, int cj) {
        int[] s = spotIn(ci, cj, ARCH_CELL, 310, DEN_AREA + 10);
        if (s == null) return;
        int zone = zone(s[0], s[1]);
        double chance = zone == CORE ? 0.55 : zone == MIDDLE ? 0.42 : 0;
        if (h(ci, cj, 312) >= chance) return;
        Arch f = new Arch();
        f.ax = s[0]; f.az = s[1];
        f.px = s[0] + 0.5; f.pz = s[1] + 0.5;
        double a = h(ci, cj, 313) * Math.PI * 2;
        f.ux = Math.cos(a); f.uz = Math.sin(a);
        f.len = 14 + 14 * h(ci, cj, 314);
        f.ha = f.len * (0.42 + 0.18 * h(ci, cj, 315)) + 2;
        if (h(ci, cj, 316) < 0.4) {
            f.gapA = (int) (f.len * (0.3 + 0.35 * h(ci, cj, 317)));
            f.gapB = f.gapA + 2 + (int) (2 * h(ci, cj, 318));
        }
        int reach = (int) Math.ceil(f.len / 2) + 2;
        f.minX = s[0] - reach; f.maxX = s[0] + reach; f.minZ = s[1] - reach; f.maxZ = s[1] + reach;
        found.add(f);
    }

    private void lights(int ci, int cj) {
        int[] s = spotIn(ci, cj, LIGHT_CELL, 330, DEN_AREA);
        if (s == null) return;
        int zone = zone(s[0], s[1]);
        double chance = zone == CORE ? 0.45 : zone == MIDDLE ? 0.42 : zone == FRINGE ? 0.22 : 0;
        if (h(ci, cj, 332) >= chance) return;
        int n = 1 + (int) (h(ci, cj, 333) * 2.99);
        Lights f = new Lights();
        f.xs = new int[n]; f.zs = new int[n]; f.hs = new int[n];
        for (int k = 0; k < n; k++) {
            double a = h(ci, cj, 340 + k) * Math.PI * 2, r = k == 0 ? 0 : 2 + 1.5 * h(ci, cj, 350 + k);
            f.xs[k] = s[0] + (int) Math.round(Math.cos(a) * r);
            f.zs[k] = s[1] + (int) Math.round(Math.sin(a) * r);
            f.hs[k] = 2 + (int) (h(ci, cj, 360 + k) * 3);
        }
        f.minX = s[0] - 4; f.maxX = s[0] + 4; f.minZ = s[1] - 4; f.maxZ = s[1] + 4;
        found.add(f);
    }

    /** a ring of tall spires stands round the den, leaving the way in open */
    private void denSpires() {
        int n = 7;
        for (int k = 0; k < n; k++) {
            double a = breakAngle + Math.PI / n + k * Math.PI * 2 / n + (h(k, 0, 400) - 0.5) * 0.35;
            if (Math.abs(wrap(a - breakAngle)) < 0.5) continue;
            double r = 52 + 10 * h(k, 0, 401);
            Spire f = new Spire();
            f.px = cx + (int) Math.round(Math.cos(a) * r);
            f.pz = cz + (int) Math.round(Math.sin(a) * r);
            f.rb = 2.0 + 1.2 * h(k, 0, 402);
            f.hs = 16 + (int) (10 * h(k, 0, 403));
            f.phase = k;
            f.eye = true;
            int reach = (int) Math.ceil(f.rb) + 1;
            f.minX = f.px - reach; f.maxX = f.px + reach; f.minZ = f.pz - reach; f.maxZ = f.pz + reach;
            found.add(f);
        }
    }

    // ================================================================== one chunk at a time

    /** what goes into each of a chunk's 256 columns (index lz * 16 + lx) */
    public static final class Out {
        public final boolean[] paint = new boolean[256];
        public final int[] zone = new int[256];
        /** the new height of the ground */
        public final int[] top = new int[256];
        /** the ground's blocks from the top down: layer[i][k] is at y = top - k */
        public final Mat[][] layer = new Mat[256][LAYERS];
        public final int[] layers = new int[256];
        /** how many of the layers are his ground as laid (any more are a cut filled in under it) */
        public final int[] laid = new int[256];
        /** each column's top block as planned (before a raised fringe is given grass) */
        public Mat topMat(int i) { return topMat[i]; }
        /** blocks set above the ground, bottom to top is not promised */
        public final int[][] ay = new int[256][MAXA];
        public final Mat[][] am = new Mat[256][MAXA];
        public final int[] an = new int[256];
        final double[] bowlT = new double[256];
        final boolean[] reef = new boolean[256];
        final boolean[] pool = new boolean[256];
        final Mat[] topMat = new Mat[256];

        /** a block above the ground, unless something that matters more already took that spot */
        void put(int i, int y, Mat m) {
            if (y <= top[i]) return;
            for (int k = 0; k < an[i]; k++) if (ay[i][k] == y) return;
            if (an[i] >= MAXA) return;
            ay[i][an[i]] = y; am[i][an[i]] = m; an[i]++;
        }

        public Mat above(int i, int y) {
            for (int k = 0; k < an[i]; k++) if (ay[i][k] == y) return am[i][k];
            return null;
        }
    }

    /**
     * Works out one chunk. y0 is each column's ground as it is now (the top block), ok says the column may be
     * changed at all, wet that its top is water, lowest how far down its ground may be cut (all plain ground
     * down to there). Nothing outside the chunk is read; everything bigger comes from the seed.
     */
    public void chunk(int chunkX, int chunkZ, int[] y0, boolean[] ok, boolean[] wet, int[] lowest, Out o) {
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        List<Feature> feats = near(chunkX, chunkZ) ? collect(x0, z0) : List.of();
        BowlHit b = new BowlHit();
        double[] dns = new double[256];
        Bowl[] inBowl = new Bowl[256];
        // ---- the ground's new height, column by column
        for (int i = 0; i < 256; i++) {
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            o.an[i] = 0; o.layers[i] = 0; o.reef[i] = false; o.pool[i] = false; o.topMat[i] = null;
            o.top[i] = y0[i]; o.bowlT[i] = 9;
            double dn = dn(x, z);
            dns[i] = dn;
            o.zone[i] = zoneOf(dn);
            o.paint[i] = ok[i] && dn < 1 && paintedAt(x, z, dn);
            if (!o.paint[i] || wet[i]) continue;
            bowlsAt(x, z, b);
            o.bowlT[i] = b.t; inBowl[i] = b.t < 1 ? b.in : null;
            int t = (int) Math.round(target(x, z, y0[i], dn, b));
            t = Math.max(y0[i] - MAX_DOWN, Math.min(y0[i] + MAX_UP, t));
            t = Math.min(t, maxY - 40);
            if (t < y0[i]) {
                int floor = Math.max(lowest[i], y0[i] > sea + 1 ? sea + 1 : y0[i]);
                // never below water standing next to it in this chunk
                int lx = i & 15, lz = i >> 4;
                int[][] nb = {{lx - 1, lz}, {lx + 1, lz}, {lx, lz - 1}, {lx, lz + 1}};
                for (int[] n : nb) {
                    if (n[0] < 0 || n[0] > 15 || n[1] < 0 || n[1] > 15) continue;
                    int j = n[1] * 16 + n[0];
                    if (wet[j]) floor = Math.max(floor, Math.min(y0[i], y0[j]));
                }
                t = Math.max(t, floor);
            }
            o.top[i] = t;
            b.in = inBowl[i];
            o.topMat[i] = surface(x, z, t, o.zone[i], b);
        }
        // ---- still pools in the bottoms of hollows, only where the whole pool and its rim sit in this chunk
        pools(x0, z0, y0, wet, lowest, inBowl, o);
        // ---- the den, the reefs, the gardens, then everything standing on the ground
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i] || wet[i]) continue;
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            double d = dist(x, z);
            if (withDen && d < DEN_AREA + 2) den(o, i, x, z, d);
            if (!o.pool[i] && (!withDen || d > DEN_AREA + 3) && o.zone[i] >= MIDDLE) reef(o, i, x, z);
        }
        for (Feature f : feats)
            for (int i = 0; i < 256; i++) {
                if (!o.paint[i] || wet[i] || o.pool[i]) continue;
                int x = x0 + (i & 15), z = z0 + (i >> 4);
                if (x < f.minX || x > f.maxX || z < f.minZ || z > f.maxZ) continue;
                f.apply(this, o, i, x, z);
            }
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i] || wet[i] || o.pool[i]) continue;
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            Bowl bw = inBowl[i];
            boolean inDen = withDen && dist(x, z) < DOME_R - 2;
            if ((bw != null && bw.garden && o.bowlT[i] < 0.8) || inDen) garden(o, i, x, z, inDen);
        }
        // ---- the layers of ground
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i]) continue;
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            if (wet[i]) {                        // under water: only the bed is turned, the water stays
                o.layers[i] = 0;
                o.laid[i] = 0;
                continue;
            }
            int top = o.top[i];
            // the top and depth-1 under it (raised land is filled right through), never down into the world's floor
            int bottom = Math.max(Math.min(y0[i], top) - (depth() - 1), minY + 1);
            int n = Math.max(0, Math.min(paintDepth > 0 ? LAYERS : MAXL, top - bottom + 1));
            o.layers[i] = n;
            o.laid[i] = n;
            for (int k = 0; k < n; k++) o.layer[i][k] = layer(x, top - k, z, k, o.topMat[i], y0[i]);
            if (o.topMat[i] == Mat.KEEP && top > y0[i]) o.layer[i][0] = Mat.GRASS;  // raised fringe: grass on top, as it was
        }
    }

    /** a winding low wall of calcite, lime glass and glowing nodes */
    private void reef(Out o, int i, int x, int z) {
        if (o.bowlT[i] < 1.0) return;
        if (perlin(35, x / 170.0, z / 170.0) < 0.56) return;
        double wx = x + 18 * (perlin(33, x / 60.0, z / 60.0) - 0.5), wz = z + 18 * (perlin(34, x / 60.0, z / 60.0) - 0.5);
        double n = fbm(31, wx / 36.0, wz / 36.0, 2);
        if (Math.abs(n - 0.5) >= 0.021) return;
        o.reef[i] = true;
        int hgt = 1 + (int) Math.min(2, 3.4 * perlin(32, x / 9.0, z / 9.0) - 0.4);
        boolean node = h(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 36) < 0.14;
        int y = o.top[i];
        o.topMat[i] = Mat.CALCITE;
        for (int k = 1; k <= hgt; k++) {
            Mat m = k == 1 ? Mat.CALCITE : Mat.TENDRIL_GLASS;
            if (k == hgt && node) m = Mat.VERDANT;
            o.put(i, y + k, m);
        }
    }

    /** moss underfoot, carpets of it, small dripleaf and a little glow lichen */
    private void garden(Out o, int i, int x, int z, boolean inDen) {
        if (o.topMat[i] != Mat.MOSS) return;
        int y = o.top[i];
        double r = h(x, z, 41);
        if (r < 0.07) { o.put(i, y + 1, Mat.DRIPLEAF_LOW); o.put(i, y + 2, Mat.DRIPLEAF_HIGH); }
        else if (r < 0.13) o.put(i, y + 1, Mat.GLOW_LICHEN);
        else if (perlin(42, x / 4.0, z / 4.0) > 0.6) o.put(i, y + 1, perlin(43, x / 9.0, z / 9.0) > 0.45 ? Mat.SPORE_MOSS : Mat.MOSS_CARPET);
    }

    /** the pools: a bowl's own, and one in the den under the bell */
    private void pools(int x0, int z0, int[] y0, boolean[] wet, int[] lowest, Bowl[] inBowl, Out o) {
        List<int[]> spots = new ArrayList<>();     // x, z, radius*10, glow
        for (int i = 0; i < 256; i++) {
            Bowl bw = inBowl[i];
            if (bw == null || !bw.pool) continue;
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            if (x != bw.px || z != bw.pz) continue;
            spots.add(new int[]{x, z, (int) (Math.min(4.5, bw.r * 0.45) * 10), 1});
        }
        if (withDen && (cx >> 4) == (x0 >> 4) && (cz >> 4) == (z0 >> 4)) spots.add(new int[]{x0 + 8, z0 + 8, 45, 1});
        for (int[] s : spots) {
            double rp = s[2] / 10.0;
            if (rp < 1.6) continue;
            int lx = s[0] - x0, lz = s[1] - z0;
            int reach = (int) Math.ceil(rp + 1.5);
            if (lx - reach < 0 || lx + reach > 15 || lz - reach < 0 || lz + reach > 15) continue;
            int w = Integer.MAX_VALUE;
            boolean fine = true;
            List<Integer> in = new ArrayList<>();
            for (int dz = -reach; dz <= reach && fine; dz++) for (int dx = -reach; dx <= reach; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= rp + 1.5) continue;
                int j = (lz + dz) * 16 + lx + dx;
                if (!o.paint[j] || wet[j] || o.pool[j]) { fine = false; break; }
                if (d < rp) in.add(j);
                else w = Math.min(w, o.top[j]);
            }
            if (!fine || in.isEmpty() || w == Integer.MAX_VALUE || w <= sea) continue;
            for (int j : in) {
                double d = Math.sqrt(Math.pow((j & 15) - lx, 2) + Math.pow((j >> 4) - lz, 2));
                int depth = d < rp * 0.55 ? 2 : 1;
                if (Math.min(o.top[j], w - depth) < lowest[j]) { fine = false; break; }
            }
            if (!fine) continue;
            for (int j : in) {
                double d = Math.sqrt(Math.pow((j & 15) - lx, 2) + Math.pow((j >> 4) - lz, 2));
                int depth = d < rp * 0.55 ? 2 : 1;
                o.top[j] = Math.min(o.top[j], w - depth);
                o.pool[j] = true;
                o.topMat[j] = d < 0.6 && s[3] == 1 ? Mat.VERDANT : Mat.CALCITE;
                for (int y = o.top[j] + 1; y <= w; y++) o.put(j, y, Mat.WATER);
            }
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < rp || d >= rp + 1.2) continue;
                int j = (lz + dz) * 16 + lx + dx;
                if (o.topMat[j] != Mat.VERDANT) o.topMat[j] = Mat.CALCITE;
            }
        }
    }

    // ================================================================== his den: the great bell

    /** the top of the bell's shell over a column, or MIN_VALUE outside it */
    private int shellTop(int x, int z) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r >= DOME_R) return Integer.MIN_VALUE;
        double f = r / DOME_R;
        return denY() + (int) Math.floor(DOME_H * Math.sqrt(Math.max(0, 1 - Math.pow(f, 2.5))));
    }

    /** is this part of the bell broken away? */
    private boolean broken(int x, int z, int y, double theta) {
        double da = Math.abs(wrap(theta - breakAngle));
        double hw = 0.62;
        if (da >= hw) return false;
        double bt = denY() + DOME_H * (0.74 - 0.52 * Math.pow(da / hw, 1.5)) + (h(x, z, 910) - 0.5) * 3.0;
        return y < bt;
    }

    private void den(Out o, int i, int x, int z, double d) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double th = Math.atan2(dz, dx);
        int dy = denY();
        int top = o.top[i];
        // ---- the bell
        int hi = shellTop(x, z);
        if (hi != Integer.MIN_VALUE) {
            int nMin = Integer.MAX_VALUE;
            int[] n = {shellTop(x - 1, z), shellTop(x + 1, z), shellTop(x, z - 1), shellTop(x, z + 1)};
            for (int v : n) nMin = Math.min(nMin, v);
            int lo = nMin == Integer.MIN_VALUE ? top + 1 : Math.max(top + 1, Math.min(nMin + 1, hi));
            for (int y = lo; y <= hi; y++) {
                if (broken(x, z, y, th)) continue;
                o.put(i, y, domeMat(x, y, z, d, th, hi));
            }
            // lights hanging inside on chains
            if (d < DOME_R - 4) {
                int ci = Math.floorDiv(x - cx, 5), cj = Math.floorDiv(z - cz, 5);
                int hx = cx + ci * 5 + 1 + (int) (h(ci, cj, 920) * 3), hz = cz + cj * 5 + 1 + (int) (h(ci, cj, 921) * 3);
                if (hx == x && hz == z && h(ci, cj, 922) < 0.62 && !broken(x, z, hi, th)) {
                    int len = 2 + (int) (h(ci, cj, 923) * (d < 10 ? 10 : 7));
                    int end = hi - 1 - len;
                    if (end > top + 3) {
                        for (int y = hi - 1; y > end; y--) o.put(i, y, Mat.CHAIN);
                        double c = h(ci, cj, 924);
                        o.put(i, end, c < 0.6 ? Mat.VERDANT : c < 0.85 ? Mat.PEARL : Mat.OCHRE);
                    }
                }
            }
        }
        // ---- the bell's mouth, a thick rim just out of the ground
        if (d >= DOME_R - 0.6 && d < DOME_R + 1.4) {
            boolean door = Math.abs(wrap(th - breakAngle)) < 0.36;
            if (!door) for (int y = top + 1; y <= dy + 1; y++) o.put(i, y, d < DOME_R + 0.4 ? Mat.BELL_CALCITE : Mat.BONE);
        }
        // ---- inside: moss and pale stone
        if (d < DOME_R - 1.5) {
            if (!o.pool[i]) o.topMat[i] = perlin(960, x / 7.0, z / 7.0) < 0.62 ? Mat.MOSS : Mat.CALCITE;
        }
        // ---- outside: the way in, lit, and the pieces the bell lost
        if (d >= DOME_R + 1.4) {
            double ux = Math.cos(breakAngle), uz = Math.sin(breakAngle);
            double along = dx * ux + dz * uz, perp = Math.abs(-dx * uz + dz * ux);
            double width = 1.6 + 0.8 * perlin(961, along / 6.0, 3.3);
            if (along > 0 && along < DEN_AREA - 6 && perp < width) {
                o.topMat[i] = perp < 0.9 ? Mat.POLISHED_DIORITE : Mat.CALCITE;
            } else if (d < DEN_FLAT + 4) {
                double p = perlin(962, x / 9.0, z / 9.0);
                o.topMat[i] = p < 0.35 ? Mat.GRASS : p < 0.7 ? Mat.CALCITE : Mat.BONE;
            }
            for (int k = 0; k < 9; k++) for (int side = -1; side <= 1; side += 2) {
                double a = DOME_R + 5 + 7 * k;
                if (a > DEN_AREA - 8) continue;
                double px = cx + ux * a - uz * 3.6 * side, pz = cz + uz * a + ux * 3.6 * side;
                if ((int) Math.floor(px) == x && (int) Math.floor(pz) == z) {
                    int hp = 3;
                    for (int s = 1; s <= hp; s++) o.put(i, top + s, Mat.CALCITE);
                    o.put(i, top + hp + 1, Mat.END_ROD);
                }
            }
            // rubble in a fan out of the break
            double da = Math.abs(wrap(th - breakAngle));
            if (da < 0.85 && d < 54 && perp >= width + 0.5) {
                int ci = Math.floorDiv(x, 4), cj = Math.floorDiv(z, 4);
                double rx = ci * 4 + 0.5 + 3 * h(ci, cj, 930), rz = cj * 4 + 0.5 + 3 * h(ci, cj, 931);
                double chance = 0.7 * (1 - smooth(DOME_R, 54, d)) * (1 - da / 0.85);
                double pd = Math.hypot(x + 0.5 - rx, z + 0.5 - rz);
                if (h(ci, cj, 932) < chance && pd < 0.5 + 0.9 * h(ci, cj, 933)) {
                    int hh = 1 + (int) (h(x, z, 934) * 2.6);
                    boolean glass = h(ci, cj, 935) < 0.62;
                    for (int s = 1; s <= hh; s++)
                        o.put(i, top + s, glass ? (s == hh && h(x, z, 936) < 0.3 ? Mat.WHITE_GLASS : h(x, z, 937) < 0.6 ? Mat.BELL_SHARD : Mat.LIME_GLASS) : Mat.CALCITE);
                }
            }
        }
    }

    private Mat domeMat(int x, int y, int z, double r, double th, int hi) {
        int dy = denY();
        int apex = dy + DOME_H;
        if (r < 6.2 && y >= apex - 2) {                       // his crown on the top of the bell
            if (r < 2.2) return Mat.PEARL;
            if (r < 3.9) return Mat.WHITE_GLASS;
            double seg = Math.PI * 2 / 5, a = wrap(th - ribPhase);
            double m = Math.abs(a - Math.round(a / seg) * seg);
            return m * r < 1.0 ? Mat.VERDANT : Mat.CALCITE;
        }
        if (r > 2.5) {
            double seg = Math.PI / 4;
            double a = wrap(th - ribPhase);
            double m = Math.abs(a - Math.round(a / seg) * seg);
            if (m * r < 0.85) return Mat.BELL_CALCITE;
        }
        int b1 = dy + Math.round(DOME_H * 0.28f), b2 = dy + Math.round(DOME_H * 0.64f);
        if (y == b1 || y == b2) return Mat.BELL_CALCITE;
        if (y <= dy + 1) return Mat.BONE;
        double streak = perlin(970, th * 6.0, y / 5.0);
        if (streak > 0.7) return Mat.WHITE_GLASS;
        if (y < b1) return Mat.GREEN_GLASS;
        return Mat.LIME_GLASS;
    }

    // ================================================================== for the painter

    /** the column that marks his den's apex, so the tests can find it */
    public int apexY() { return denY() + DOME_H; }

    public double breakAngle() { return breakAngle; }
}
