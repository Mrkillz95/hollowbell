package net.jj.hollowbell.solid;

/**
 * The sit gesture's counter: three distinct crouch presses within two seconds. Each press is counted once (holding the
 * key is one press); a press more than two seconds after the first of the run starts the run again. The client feeds it
 * only presses made standing on him, not riding anything.
 */
public final class TripleCrouch {
    public static final long WINDOW_MS = 2000;
    public static final int PRESSES = 3;

    private final long[] at = new long[PRESSES];
    private int n;

    /** one press at time t (ms): true when it is the third within the window (the run then starts again) */
    public boolean press(long t) {
        // (presses that are too old drop out of the run)
        int keep = 0;
        for (int i = 0; i < n; i++) if (t - at[i] < WINDOW_MS) at[keep++] = at[i];
        n = keep;
        at[n++] = t;
        if (n >= PRESSES) {
            n = 0;
            return true;
        }
        return false;
    }

    public void reset() {
        n = 0;
    }

    /** presses in the run so far */
    public int count() {
        return n;
    }
}
