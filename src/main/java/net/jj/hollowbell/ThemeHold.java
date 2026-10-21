package net.jj.hollowbell;

/**
 * When his fight theme is wanted, tick by tick: while you're within his music range of an awake one (by the flat
 * distance, as he can be far overhead), and for eight seconds after. Once in, you're let go only 16 blocks past the
 * range, so walking along its edge doesn't start and stop it.
 */
public final class ThemeHold {
    public static final int MIN_RANGE = 128, LET_GO_MARGIN = 16, HOLD_TICKS = 160;

    private boolean inRange;
    private int hold;

    /** his music range at this size: 96 blocks past his rim (he is about 200 blocks across at full size) */
    public static double range(float scale) {
        return Math.max(MIN_RANGE, 96 + 100 * scale);
    }

    /** is this flat distance within the range (with the margin, once you're in) */
    public boolean near(double flatDist, double range) {
        return flatDist < range + (inRange ? LET_GO_MARGIN : 0);
    }

    /** one tick: whether any one was near; returns whether the theme is wanted */
    public boolean tick(boolean near) {
        inRange = near;
        if (near) hold = HOLD_TICKS;
        else if (hold > 0) hold--;
        return hold > 0;
    }

    public void reset() {
        inRange = false;
        hold = 0;
    }
}
