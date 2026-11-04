package net.jj.giants.systems.remains;

/** (remains) Smooth and rough noise for rot patches, the same every time for the same spot and seed. */
final class Noise {
    private Noise() {}

    /** 0-1 for one spot */
    static double hash(int x, int y, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** 0-1, smooth between whole numbers */
    static double at(double x, double y, double z, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = fade(x - ix), fy = fade(y - iy), fz = fade(z - iz);
        double v = 0;
        for (int a = 0; a < 2; a++)
            for (int b = 0; b < 2; b++)
                for (int c = 0; c < 2; c++) {
                    double w = (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fy : fy) * (c == 0 ? 1 - fz : fz);
                    v += w * hash(ix + a, iy + b, iz + c, seed);
                }
        // (eight blends crowd round the middle: spread back out to about even)
        return Math.max(0, Math.min(0.999, 0.5 + (v - 0.5) * 2.4));
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }
}
