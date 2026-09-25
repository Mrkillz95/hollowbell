package net.jj.hollowbell.rig;

/**
 * Everything the pose needs to know about him at one moment. Filled in by {@link BellAnim} (the smoothed body and
 * the chain points) and handed to {@link BellRig#computePose}. All positions are in model space: blocks at size 1,
 * y 0 at the bottom of his strands.
 *
 * The move fields (grab, slam, sweep and the rest) are what the moves ask of his arms and strands this tick; the
 * chains follow them with weight, so nothing ever jumps to them.
 */
public final class BellState {
    /** ticks, with the partial tick */
    public float time;

    // ---- the body, smoothed
    /** how far the whole of him is lowered (the drop, sinking, dying) */
    public float lower;
    /** how he leans: a turn about (tiltX, 0, tiltZ), its length the angle in radians. The dive flips him right over */
    public float tiltX, tiltZ;
    /** how far he has turned about his middle (the whirlpool) */
    public float spin;
    /** the bell: above 0 squeezed in narrow and tall (a pulse), below 0 opened out wide and flat (sinking) */
    public float squeeze;
    /** how strongly the ripple runs round the rim */
    public float ripple;
    /** dying: 0 alive, 1 the glow out and the bell folded */
    public float death;
    /** worn out after a heavy move: the bell sags */
    public float droop;
    /** how bright the glowing parts are pushed (a flash, a wind-up), 0 normal */
    public float glow;
    /** the pods swelling (pod burst), the egg clumps shaking (spore cloud, egg rain) */
    public float podSwell, eggShake;
    /** below half health: the lime loops on his arms turn red */
    public boolean red;

    // ---- his parts
    public boolean[] podPopped;
    public float[] podGrowth;
    public boolean[] eggGone;

    /** the arms and strands: per chain, its joint points x y z (tip last), in model space */
    public final float[][] chain;

    // ---- what the moves ask of the arms and strands this tick (the targets the chains swing toward)
    /** how he is moving, in model blocks per tick (smoothed) */
    public float driftX, driftY, driftZ;
    /** climbing hard (0-1): the strands stream down in a bundle. Sinking (0-1): they float up and spread */
    public float climb, sink;
    /** how far his arms swing with each pulse */
    public float armSwing = 1f;
    /** the bell's pulse, raw, for the arms' swimming stroke */
    public float pulse;
    /** folded down (drop, sunk, dying): the strands buckle and the arms spread on the ground */
    public float fold;
    public float sweep, sweepDir;
    public float curtain, curtainX, curtainZ;
    public int slamArm = -1;
    public float slamT, slamX, slamY, slamZ;
    public int wrapArm = -1;
    public float wrapAmt, wrapX, wrapY, wrapZ;
    public int grabStrand = -1;
    public float grabReach, grabLift, grabX, grabY, grabZ;
    /** a strand lashing sideways: which, how far through (0-1), and which way */
    public int lashStrand = -1;
    public float lashT, lashDir;
    /** every strand flicked up and back (sting volley, stinger storm), 0-1 */
    public float flick;
    /** the strands swirled round (whirlpool), 0-1 */
    public float swirl;
    /** per arm: raised up (1) or slammed down past its rest (below 0), for the arm storm */
    public final float[] armRaise = new float[8];
    /** the arms and strands spread out wide (deep toll, sun lances), 0-1 */
    public float spread;

    public BellState(BellRig rig) {
        podPopped = new boolean[rig.pods.length];
        podGrowth = new float[rig.pods.length];
        java.util.Arrays.fill(podGrowth, 1f);
        eggGone = new boolean[rig.eggs.length];
        chain = new float[rig.chains.length][];
        for (int c = 0; c < chain.length; c++) {
            BellRig.Chain ch = rig.chains[c];
            float[] p = new float[3 * ch.points()];
            for (int i = 0; i < ch.points(); i++) { p[3 * i] = ch.joints[i].x; p[3 * i + 1] = ch.joints[i].y; p[3 * i + 2] = ch.joints[i].z; }
            chain[c] = p;
        }
    }

    /** clears what the moves ask for, before the move of the moment fills it in again */
    public void clearMoves() {
        sweep = 0f; curtain = 0f; slamArm = -1; wrapArm = -1; grabStrand = -1; lashStrand = -1;
        flick = 0f; swirl = 0f; spread = 0f;
        java.util.Arrays.fill(armRaise, 0f);
    }
}
