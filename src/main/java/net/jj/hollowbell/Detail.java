package net.jj.hollowbell;

/**
 * How finely he's drawn at a distance. The Giants Guide's model detail decides: at 100% (the default, Highest) he's
 * always drawn in full, at every distance. Under 100% he's drawn with bigger, merged blocks once he's far away, and
 * simpler still when he's very far or very small, so he runs faster: the lower it is, the sooner. His own detail
 * switch (config/hollowbell.json, simpleFarAway) is kept for older configs.
 */
public final class Detail {
    private Detail() {}

    public static final int FULL = 0, FAR = 1, TINY = 2;

    /** where this player's game is looking from (set by the client; null on a server) */
    public static volatile java.util.function.Supplier<net.minecraft.world.phys.Vec3> viewer = () -> null;

    /** how far off his far-sight stand-ins show on this game: farSightBlocks, times the guide's levels */
    public static int farSightBlocks() {
        if (!GiantsPerf.on("detail.farSight")) return 0;
        int b = Math.max(0, Math.min(4096, BellConfig.V.farSightBlocks));
        float k = GiantsPerf.f("detail.farSightDistance");
        return k >= 1f ? b : (int) (b * k);
    }

    /** how far off he is still drawn on this game: renderDistance, times the guide's level */
    public static double renderDistance() {
        float k = GiantsPerf.f("detail.renderDistance");
        return k >= 1f ? BellConfig.V.renderDistance : BellConfig.V.renderDistance * k;
    }

    /**
     * How many ticks between settling what lies on the ground (the guide's animation detail): 4 in full; with
     * "simpler far off" 12 once he's far enough to be drawn simpler, with "simpler" always 12.
     */
    public static int settleEvery(net.minecraft.world.phys.Vec3 at, float scale) {
        int c = GiantsPerf.choice("detail.animation");
        if (c <= 0) return 4;
        if (c >= 2) return 12;
        var v = viewer.get();
        double near = BellConfig.V.simpleFarAwayAt * Math.max(0.25f, scale);
        return v != null && v.distanceTo(at) > near ? 12 : 4;
    }

    public static boolean on() {
        return BellConfig.V.simpleFarAway;
    }

    public static void set(boolean on) {
        BellConfig.V.simpleFarAway = on;
        BellConfig.save();
    }

    /** which version of him to draw, dist blocks from the camera, at size scale */
    public static int lod(double dist, float scale) {
        // (the Giants Guide's model detail: at 100%, the default, always the full model, however far off; under 100%
        // the simpler drawing starts sooner the lower it is, even with detail off)
        float perf = GiantsPerf.f("detail.model");
        if (perf >= 0.999f) return FULL;
        double at = BellConfig.V.simpleFarAwayAt * perf * Math.max(0.25f, scale);
        if ((scale < 0.06f && dist > 40) || dist > at * 4) return TINY;
        if (dist > at) return FAR;
        return FULL;
    }
}
