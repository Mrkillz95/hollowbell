package net.jj.hollowbell.entity;

/**
 * "Follow me" (JJ, Nov 3: the same for every giant): he sets off after the one he follows once they're a little past
 * his own body, and stops a little past it again, so he follows close and never walks onto them. Both grow with his
 * body: a small one keeps close, a big one proportionally further. {@code reach} is how far his body reaches out from
 * his middle at his size now, in blocks. The same numbers in every giant's mod.
 */
public final class FollowGap {
    private FollowGap() {}

    /** how near he comes before he stops: just clear of his body */
    public static double stop(double reach) {
        return reach * 1.05 + 5;
    }

    /** how far off they get before he sets off after them (a bit more than stop, so he doesn't stop and start) */
    public static double start(double reach) {
        return reach * 1.2 + 12;
    }
}
