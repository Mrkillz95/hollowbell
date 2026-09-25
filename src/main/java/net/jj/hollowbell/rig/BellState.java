package net.jj.hollowbell.rig;

/**
 * Everything the pose needs to know about him at one moment. Filled in by the entity (server side from its own
 * state, client side from the synced state plus smoothing) and handed to {@link BellRig#computePose}.
 * All positions are in model space: blocks at size 1, y 0 at the ground under his strands.
 */
public final class BellState {
    /** ticks, with the partial tick */
    public float time;
    /** how squeezed the bell is: 0 relaxed, 1 a normal pulse, up to 1.6 for the pulse wave */
    public float pulse;
    /** how he is drifting, in model blocks per tick (x, z) */
    public float driftX, driftZ;
    /** how far the whole of him hangs lower than normal, and how far he leans (radians about x and z) */
    public float lower, tiltX, tiltZ;
    /** the drop: 0 floating, 1 the bell right down on the ground */
    public float drop;
    /** dying: 0 alive, 1 folded down on the ground; then sink is how far into the ground he has gone (0-1) */
    public float death, sink;
    /** the sweep: how far the strands are swung (-1..1) and the way they swing (radians) */
    public float sweep, sweepDir;
    /** the curtain: how far the strands have closed in, and on what */
    public float curtain, curtainX, curtainZ;
    /** how far his arms swing with each pulse */
    public float armSwing = 1f;
    /** arm slam: which arm (-1 none) and how far through it (0-1) */
    public int slamArm = -1;
    public float slamT;
    /** arm wrap: which arm (-1 none), how far curled (0-1), and round what */
    public int wrapArm = -1;
    public float wrapAmt, wrapX, wrapY, wrapZ;
    /** a strand grabbing: which (-1 none), how far it has reached (0-1), how far it has pulled up (0-1), and at what */
    public int grabStrand = -1;
    public float grabReach, grabLift, grabX, grabY, grabZ;
    /** below half health: the lime loops on his arms turn red */
    public boolean red;
    /** per pod: popped */
    public boolean[] podPopped;
    /** per egg clump: gone (shed) */
    public boolean[] eggGone;
    /** per thread: how much of it there is, 0 cut, 1 whole */
    public float[] threadGrowth;

    public BellState(BellRig rig) {
        podPopped = new boolean[rig.pods.length];
        eggGone = new boolean[rig.eggs.length];
        threadGrowth = new float[rig.threads.length];
        java.util.Arrays.fill(threadGrowth, 1f);
    }
}
