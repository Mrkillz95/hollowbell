package net.jj.mountain.rig;

/** Everything the pose depends on. The server owns it and syncs it; the client blends it between ticks. */
public final class RigState {
    public float walkPhase, walkAmount, turn;
    public float bodyPitch, bodyRoll, bodyLift, burrow;
    /** extra up/down bend of each trunk piece (tail .. neck; the middle one uses bodyPitch) to follow the ground */
    public final float[] segBend = new float[5];
    public float time, death;
    /** 0 = lungs empty, 1 = full (he swells) */
    public float breath;
    /** 0 = the slit shut, 1 = the two halves of his face thrown wide open */
    public float mouthOpen, grind;
    public float headYaw, headPitch;
    /** model-space point every eye turns to, and how much */
    public float lookX, lookY, lookZ, lookAmt;
    /** the other things he is watching at the same time: his eyes split between all of them */
    public final org.joml.Vector3f[] looks = {new org.joml.Vector3f(), new org.joml.Vector3f(), new org.joml.Vector3f()};
    public int lookCount = 1;
    /** model-space point the arms near it reach for */
    public float reachX, reachY, reachZ, reachAmt;
    /** the arm holding something (-1 none), the arm it is being passed to, and how far along the hand-off is */
    public int holdArm = -1, holdNext = -1;
    public float holdT;
    /** one bit per eye */
    public final long[] popped = new long[4];
    /** one bit per leg and per arm: broken ones drag or hang */
    public long legsBroken, armsBroken0, armsBroken1;
    /** one bit per leg: knitted back once and never the same again, so it is drawn grey and dead */
    public long legsScarred;
    /** 0 = on his legs, 1 = knocked down on his belly (too many legs broken on one side) */
    public float down;
    /** the piece of him that was just hit and how hard it is still jerking (0..1) */
    public int flinchBone = -1;
    public float flinchAmt;
    public boolean legBroken(int k) { return k >= 0 && k < 64 && (legsBroken >>> k & 1L) != 0L; }
    public boolean legScarred(int k) { return k >= 0 && k < 64 && (legsScarred >>> k & 1L) != 0L; }
    public boolean armBroken(int k) { return k < 0 ? false : k < 64 ? (armsBroken0 >>> k & 1L) != 0L : (armsBroken1 >>> (k - 64) & 1L) != 0L; }
    /** client only (old): how far each foot has to come up (+) or reach down (-) to meet the ground, in model blocks */
    public float[] legLift;
    /** the attack he is making (see the constants), how far into it in ticks, its extra number (which tentacle),
     *  and the model-space point it is aimed at */
    public int attack, attackArg;
    public float attackT, atkX, atkY, atkZ;
    public static final int NONE = 0, ARTILLERY = 1, WHIP = 2, SLAM = 3, GAZE = 4, REAR = 5, VOMIT = 6, DARTS = 7, ERUPT = 8, SWARM = 9,
            SCREAM = 10, TONGUE = 11, STOMP = 12, GOO_STORM = 13, EYE_STORM = 14, DRAW = 15, SPLIT = 16;
    /** the last thing he does: never one he picks himself, so it sits outside LAST_ATTACK */
    public static final int UNMAKE = 20;
    public static final int LAST_ATTACK = SPLIT;
    /** 0 = up and about, 1 = lying flat asleep (legs sprawled, tail on the ground, arms limp) */
    public float sleep;
    /** 0 = every eye shut, 1 = all open; in between they open one after another */
    public float eyesOpen = 1f;
    /** model-space point each leg plants (x, y, z per leg), and each tentacle tip; null = play the plain walk cycle */
    public float[] feet, tips;

    public boolean isPopped(int eye) { return (popped[eye >> 6] >>> (eye & 63) & 1L) != 0; }
    public void setPopped(int eye, boolean v) { if (v) popped[eye >> 6] |= 1L << (eye & 63); else popped[eye >> 6] &= ~(1L << (eye & 63)); }

    public void set(RigState o) {
        walkPhase = o.walkPhase; walkAmount = o.walkAmount; turn = o.turn;
        bodyPitch = o.bodyPitch; bodyRoll = o.bodyRoll; bodyLift = o.bodyLift; burrow = o.burrow;
        System.arraycopy(o.segBend, 0, segBend, 0, 5);
        time = o.time; death = o.death; sleep = o.sleep; eyesOpen = o.eyesOpen; breath = o.breath; mouthOpen = o.mouthOpen; grind = o.grind;
        headYaw = o.headYaw; headPitch = o.headPitch;
        lookX = o.lookX; lookY = o.lookY; lookZ = o.lookZ; lookAmt = o.lookAmt;
        for (int i = 0; i < looks.length; i++) looks[i].set(o.looks[i]);
        lookCount = o.lookCount;
        reachX = o.reachX; reachY = o.reachY; reachZ = o.reachZ; reachAmt = o.reachAmt;
        holdArm = o.holdArm; holdNext = o.holdNext; holdT = o.holdT;
        attack = o.attack; attackArg = o.attackArg; attackT = o.attackT; atkX = o.atkX; atkY = o.atkY; atkZ = o.atkZ;
        System.arraycopy(o.popped, 0, popped, 0, 4);
        legsBroken = o.legsBroken; armsBroken0 = o.armsBroken0; armsBroken1 = o.armsBroken1; down = o.down;
        legsScarred = o.legsScarred;
        flinchBone = o.flinchBone; flinchAmt = o.flinchAmt;
        feet = o.feet == null ? null : o.feet.clone(); tips = o.tips == null ? null : o.tips.clone();
        if (o.legLift != null) {
            if (legLift == null || legLift.length != o.legLift.length) legLift = new float[o.legLift.length];
            System.arraycopy(o.legLift, 0, legLift, 0, legLift.length);
        }
    }

    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }

    /** this = blend of a and b (walk phase unwrapped so it never spins backwards). */
    public void lerp(float t, RigState a, RigState b) {
        float wp = b.walkPhase;
        if (wp - a.walkPhase > 500f) wp -= 1000f; else if (a.walkPhase - wp > 500f) wp += 1000f;
        walkPhase = lerp(t, a.walkPhase, wp);
        walkAmount = lerp(t, a.walkAmount, b.walkAmount); turn = lerp(t, a.turn, b.turn);
        bodyPitch = lerp(t, a.bodyPitch, b.bodyPitch); bodyRoll = lerp(t, a.bodyRoll, b.bodyRoll); bodyLift = lerp(t, a.bodyLift, b.bodyLift);
        burrow = lerp(t, a.burrow, b.burrow);
        for (int i = 0; i < 5; i++) segBend[i] = lerp(t, a.segBend[i], b.segBend[i]);
        time = lerp(t, a.time, b.time); death = lerp(t, a.death, b.death);
        sleep = lerp(t, a.sleep, b.sleep); eyesOpen = lerp(t, a.eyesOpen, b.eyesOpen);
        breath = lerp(t, a.breath, b.breath); mouthOpen = lerp(t, a.mouthOpen, b.mouthOpen); grind = lerp(t, a.grind, b.grind);
        headYaw = lerp(t, a.headYaw, b.headYaw); headPitch = lerp(t, a.headPitch, b.headPitch);
        lookX = lerp(t, a.lookX, b.lookX); lookY = lerp(t, a.lookY, b.lookY); lookZ = lerp(t, a.lookZ, b.lookZ); lookAmt = lerp(t, a.lookAmt, b.lookAmt);
        lookCount = b.lookCount;
        for (int i = 0; i < looks.length; i++) looks[i].set(a.looks[i]).lerp(b.looks[i], t);
        reachX = lerp(t, a.reachX, b.reachX); reachY = lerp(t, a.reachY, b.reachY); reachZ = lerp(t, a.reachZ, b.reachZ); reachAmt = lerp(t, a.reachAmt, b.reachAmt);
        legsBroken = b.legsBroken; armsBroken0 = b.armsBroken0; armsBroken1 = b.armsBroken1; down = lerp(t, a.down, b.down);
        legsScarred = b.legsScarred;
        flinchBone = b.flinchBone; flinchAmt = a.flinchBone == b.flinchBone ? lerp(t, a.flinchAmt, b.flinchAmt) : b.flinchAmt;
        holdArm = b.holdArm; holdNext = b.holdNext;
        holdT = (a.holdArm == b.holdArm && a.holdNext == b.holdNext) ? lerp(t, a.holdT, b.holdT) : b.holdT;
        attack = b.attack; attackArg = b.attackArg;
        attackT = a.attack == b.attack && b.attackT >= a.attackT ? lerp(t, a.attackT, b.attackT) : b.attackT;
        atkX = lerp(t, a.atkX, b.atkX); atkY = lerp(t, a.atkY, b.atkY); atkZ = lerp(t, a.atkZ, b.atkZ);
        System.arraycopy(b.popped, 0, popped, 0, 4);
        feet = b.feet; tips = b.tips;
        if (b.legLift != null) {
            if (legLift == null || legLift.length != b.legLift.length) legLift = new float[b.legLift.length];
            for (int i = 0; i < legLift.length; i++) legLift[i] = a.legLift != null && a.legLift.length == legLift.length ? lerp(t, a.legLift[i], b.legLift[i]) : b.legLift[i];
        }
    }
}
