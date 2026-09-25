package net.jj.hollowbell;

/**
 * How finely he's drawn at a distance. With detail on (the default) he's drawn with bigger, merged blocks when
 * he's far away, and simpler still when he's very far or very small, so he runs faster. With detail off he's
 * always drawn in full, at every distance. Set with /hollowbell detail on|off (a setting on your own computer,
 * kept in config/hollowbell.json as simpleFarAway).
 */
public final class Detail {
    private Detail() {}

    public static final int FULL = 0, FAR = 1, TINY = 2;

    public static boolean on() { return HollowbellConfig.V.simpleFarAway; }

    public static void set(boolean on) {
        HollowbellConfig.V.simpleFarAway = on;
        HollowbellConfig.save();
    }

    /** which version of him to draw, dist blocks from the camera, at size scale */
    public static int lod(double dist, float scale) {
        if (!on()) return FULL;
        double at = HollowbellConfig.V.simpleFarAwayAt * Math.max(0.25f, scale);
        if ((scale < 0.06f && dist > 40) || dist > at * 4) return TINY;
        if (dist > at) return FAR;
        return FULL;
    }
}
