package net.jj.mountain.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Mountain's skeleton, read from mountain_rig.json: 978 bones (five body segments, the head and the two halves
 * of his face, 58 legs, 70 arms with every finger, 210 eyes, 26 strings of goo), plus the pose maths shared by the
 * server (hitboxes, hands, mouth) and the client (drawing).
 * Model space: 1 unit = 1 block at full size, y = 0 is the ground under his feet, he faces -Z, +X is his right.
 */
public final class MountainRig {
    private static MountainRig instance;

    public static synchronized MountainRig get() {
        if (instance == null) instance = load();
        return instance;
    }

    // ---- bones
    public final String[] boneNames;
    public final int[] parent;
    public final Vector3f[] pivot;
    public final Map<String, Integer> index = new HashMap<>();
    // ---- trunk
    public final int[] segments = new int[5];
    public final Vector3f[] segCenter = new Vector3f[5], segTangent = new Vector3f[5];
    public final int head, cheekR, cheekL;
    public final Vector3f headCenter, mouth, throat, mouthTop, mouthBottom, hingeR, hingeL;
    public final List<BodySample> body = new ArrayList<>();
    // ---- limbs, eyes, goo
    public final LegDef[] legs;
    public final ArmDef[] arms;
    public final EyeDef[] eyes;
    public final GooDef[] goo;
    public final List<PartDef> parts = new ArrayList<>();
    /** arms from the tail to the head, for passing things along */
    public final int[] armsTailToHead;
    public final Vector3f extentMin, extentMax;
    /** per bone: breath swell of its own mesh (trunk only), and the outward push it gets from its swelling parent */
    private final float[] swell;
    private final Vector3f[] breathPush;
    private final Matrix4f[] swellFrame;          // local frame of each trunk bone for the swell (null for others)

    /** a point on his spine: where, how thick, which body piece, and how high the top of his back is across his width
     *  (one value per model block from TOP_HALF to his left to TOP_HALF to his right, above p; NaN = nothing there) */
    public record BodySample(float t, Vector3f p, float r, int bone, float[] top) {}
    public static final int TOP_HALF = 60;
    public record PartDef(int bone, Vector3f center, float width, float height, boolean solid, String kind, int leg) {}

    public static final class LegDef {
        public final int k, kind, side;            // kind 0 crab, 1 spider, 2 tentacle; side +1 = his right (+x)
        public final float u, reach, thick;        // u: 0 at the tail, 1 at the head
        /** how far the toes spread round the foot */
        public float pad = 3f;
        public final int[] bones;
        public final Vector3f[] joints;            // base, knee, mid, foot
        final float stepAmp, radius;
        // ---- IK: the leg bends in the upright plane through its hip and the end it reaches with.
        // Crab legs bend at their high first knee, spider legs at their high second knee; a tentacle reaches with
        // the point where it meets the ground and its last piece lies along the ground to the tip.
        final int kneeJ, endJ, boneA, boneB;
        final Vector3f ed = new Vector3f(), n0 = new Vector3f(), tipRest;
        final float A, B, aRest, gRest, sig, minD, maxD;
        /** how far above the ground the reaching end sits when he stands on flat ground */
        public final float endY;
        LegDef(int k, int kind, int side, float u, float reach, float thick, int[] bones, Vector3f[] joints) {
            this.k = k; this.kind = kind; this.side = side; this.u = u; this.reach = reach; this.thick = thick; this.bones = bones; this.joints = joints;
            Vector3f f = new Vector3f(joints[3]).sub(joints[0]);
            radius = (float) Math.hypot(f.x, f.z);
            stepAmp = Math.max(0.05f, Math.min(0.42f, STRIDE / (2f * Math.max(8f, Math.abs(f.x)))));
            kneeJ = kind == 1 ? 2 : 1;
            endJ = kind == 2 ? 2 : 3;
            boneA = bones[0];
            boneB = bones[kneeJ];
            Vector3f j0 = joints[0], kn = joints[kneeJ], e = joints[endJ];
            Vector3f v = new Vector3f(e).sub(j0);
            ed.set(v.x, 0f, v.z).normalize();
            n0.set(ed).cross(0f, 1f, 0f).normalize();
            float x1 = new Vector3f(kn).sub(j0).dot(ed), y1 = kn.y - j0.y, xe = v.dot(ed), ye = v.y;
            A = (float) Math.hypot(x1, y1);
            B = (float) Math.hypot(xe - x1, ye - y1);
            aRest = (float) Math.atan2(y1, x1);
            gRest = (float) Math.atan2(ye - y1, xe - x1);
            float sg = Math.signum(xe * y1 - ye * x1);
            sig = sg == 0f ? 1f : sg;
            float dRest = (float) Math.hypot(xe, ye);
            maxD = Math.max(dRest, (A + B) * 0.995f);
            minD = Math.min(dRest, Math.abs(A - B) * 1.02f + 0.5f);
            tipRest = new Vector3f(joints[3]).sub(joints[2]).normalize();
            endY = e.y;
            // crab legs can also fold at the lower joint when the foot has to come in close (a hill under it)
            Vector3f j2 = joints[2], j3 = joints[3];
            x2 = new Vector3f(j2).sub(j0).dot(ed); y2 = j2.y - j0.y;
            float l1 = (float) Math.hypot(x2 - x1, y2 - y1), l2 = (float) Math.hypot(xe - x2, ye - y2);
            lowA = l1; lowB = l2;
            float ux = x1 - x2, uy = y1 - y2, vx = xe - x2, vy = ye - y2;
            lowRest = (float) Math.acos(Math.max(-1f, Math.min(1f, (ux * vx + uy * vy) / Math.max(1e-4f, l1 * l2))));
            float fs = Math.signum(vx * uy - vy * ux);
            lowSign = fs == 0f ? 1f : fs;
            kx1 = x1; ky1 = y1;
            footR = Math.max(1.5f, thick * 0.5f);
            // crab legs normally keep the foot piece upright (the ankle only turns to undo the knees), so the
            // pad stays flat on the ground: that chain is hip -> high knee -> ankle
            ankle = new Vector3f(j3).sub(j2);
            float ax = x2, ay = y2;
            cA = A;
            cB = (float) Math.hypot(ax - x1, ay - y1);
            cgRest = (float) Math.atan2(ay - y1, ax - x1);
            float cs = Math.signum(ax * y1 - ay * x1);
            csig = cs == 0f ? 1f : cs;
            float cd = (float) Math.hypot(ax, ay);
            cMinD = Math.min(cd, Math.abs(cA - cB) * 1.02f + 0.5f);
            cMaxD = Math.max(cd, (cA + cB) * 0.995f);
            // lying down: with the hip that much lower, a foot this far out keeps the leg long and low
            float rest = (float) Math.hypot(e.x - j0.x, e.z - j0.z);
            float dy = (j0.y - SLEEP_DROP) - e.y, reachFlat = (float) Math.sqrt(Math.max(0f, sq(0.9f * maxD) - dy * dy));
            float h = rest + SPRAWL * Math.max(0f, reachFlat - rest);
            sprawlEnd.set(j0.x + ed.x * h, e.y, j0.z + ed.z * h);
            sprawlTip.set(joints[3]).add(sprawlEnd.x - e.x, 0f, sprawlEnd.z - e.z);
        }
        private static float sq(float v) { return v * v; }
        final float x2, y2, lowA, lowB, lowRest, lowSign, kx1, ky1, footR;
        final Vector3f ankle;
        /** where the planted end rests while he lies asleep: pushed out from the hip, so the legs sprawl */
        final Vector3f sprawlEnd = new Vector3f(), sprawlTip = new Vector3f();
        private final Vector3f tmpEnd = new Vector3f(), tmpTip = new Vector3f();
        /** the planted end for a given amount of sprawl (0 standing, 1 lying flat) */
        public Vector3f endAt(float sprawl) { return sprawl <= 0f ? end() : tmpEnd.set(end()).lerp(sprawlEnd, sprawl); }
        /** a tentacle's tip for a given amount of sprawl */
        public Vector3f tipAt(float sprawl) { return sprawl <= 0f ? joints[3] : tmpTip.set(joints[3]).lerp(sprawlTip, sprawl); }
        final float cA, cB, cgRest, csig, cMinD, cMaxD;
        public Vector3f foot() { return joints[3]; }
        /** the point this leg plants: the foot, or for a tentacle where it meets the ground */
        public Vector3f end() { return joints[endJ]; }
    }

    public static final class ArmDef {
        public final int k, extra;                 // extra: 1 a mouth in the palm, 2 eyes on the knuckles
        public final float u, scale;
        public final int upper, fore, hand, thumb;
        public final int[] fingers;
        public final Vector3f base, elbow, wrist, handPoint, normal, dir, side, palm, thumbDir;
        public final Vector3f[] fingerDirs;
        final Vector3f restVec, idleA, idleB, elbowAxis;
        final Vector3f[] fingerAxis;
        final Vector3f thumbAxis;
        ArmDef(JsonObject o) {
            k = o.get("k").getAsInt(); extra = o.get("extra").getAsInt(); u = o.get("u").getAsFloat(); scale = o.get("scale").getAsFloat();
            JsonArray b = o.getAsJsonArray("bones");
            upper = b.get(0).getAsInt(); fore = b.get(1).getAsInt(); hand = b.get(2).getAsInt();
            JsonArray f = o.getAsJsonArray("fingers");
            fingers = new int[f.size()];
            for (int i = 0; i < fingers.length; i++) fingers[i] = f.get(i).getAsInt();
            thumb = o.get("thumb").getAsInt();
            base = vec(o.get("base")); elbow = vec(o.get("elbow")); wrist = vec(o.get("wrist")); handPoint = vec(o.get("hand"));
            normal = vec(o.get("normal")).normalize(); dir = vec(o.get("dir")).normalize(); side = vec(o.get("side")).normalize(); palm = vec(o.get("palm")).normalize();
            JsonArray fd = o.getAsJsonArray("fingerDirs");
            fingerDirs = new Vector3f[fd.size()];
            for (int i = 0; i < fingerDirs.length; i++) fingerDirs[i] = vec(fd.get(i)).normalize();
            thumbDir = vec(o.get("thumbDir")).normalize();
            restVec = new Vector3f(handPoint).sub(base);
            Vector3f a = new Vector3f(restVec).normalize();
            Vector3f up = Math.abs(a.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            idleA = new Vector3f(a).cross(up).normalize();
            idleB = new Vector3f(idleA).cross(a).normalize();
            Vector3f fa = new Vector3f(wrist).sub(elbow).normalize();
            Vector3f ea = new Vector3f(fa).cross(up);
            elbowAxis = ea.lengthSquared() < 1e-6 ? new Vector3f(1, 0, 0) : ea.normalize();
            fingerAxis = new Vector3f[fingerDirs.length];
            for (int i = 0; i < fingerDirs.length; i++) {
                Vector3f ax = new Vector3f(fingerDirs[i]).cross(palm);
                fingerAxis[i] = ax.lengthSquared() < 1e-6 ? new Vector3f(side) : ax.normalize();
            }
            Vector3f ta = new Vector3f(thumbDir).cross(palm);
            thumbAxis = ta.lengthSquared() < 1e-6 ? new Vector3f(dir) : ta.normalize();
        }
    }

    public static final class EyeDef {
        public final int k, bone, radius, parentBone;
        public final boolean slit;
        public final Vector3f center, dir;
        EyeDef(JsonObject o) {
            k = o.get("k").getAsInt(); bone = o.get("bone").getAsInt(); radius = o.get("radius").getAsInt(); parentBone = o.get("parent").getAsInt();
            slit = o.get("slit").getAsBoolean();
            center = vec(o.get("center")); dir = vec(o.get("dir")).normalize();
        }
    }

    public record GooDef(int k, int bone, Vector3f top, Vector3f tip) {}

    private static Vector3f vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static MountainRig load() {
        try (Reader r = new InputStreamReader(MountainRig.class.getResourceAsStream("/mountain_breathes/mountain_rig.json"), StandardCharsets.UTF_8)) {
            return new MountainRig(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            throw new RuntimeException("The Mountain That Breathes: could not read the rig", e);
        }
    }

    private MountainRig(JsonObject j) {
        JsonArray bs = j.getAsJsonArray("bones");
        int n = bs.size();
        boneNames = new String[n]; parent = new int[n]; pivot = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            JsonObject b = bs.get(i).getAsJsonObject();
            boneNames[i] = b.get("name").getAsString();
            index.put(boneNames[i], i);
            pivot[i] = vec(b.get("pivot"));
        }
        for (int i = 0; i < n; i++) {
            JsonElement p = bs.get(i).getAsJsonObject().get("parent");
            parent[i] = (p == null || p.isJsonNull()) ? -1 : index.get(p.getAsString());
            if (parent[i] >= i) throw new IllegalStateException("bones must be listed parent first: " + boneNames[i]);
        }
        // ---- body
        JsonObject bo = j.getAsJsonObject("body");
        JsonArray segs = bo.getAsJsonArray("segments");
        for (int i = 0; i < 5; i++) segments[i] = index.get(segs.get(i).getAsString());
        for (JsonElement e : bo.getAsJsonArray("samples")) {
            JsonObject o = e.getAsJsonObject();
            float[] top = new float[TOP_HALF * 2 + 1];
            Arrays.fill(top, Float.NaN);
            if (o.has("top")) {
                JsonArray ta = o.getAsJsonArray("top");
                for (int q = 0; q < Math.min(top.length, ta.size()); q++) if (!ta.get(q).isJsonNull()) top[q] = ta.get(q).getAsFloat();
            }
            body.add(new BodySample(o.get("t").getAsFloat(), vec(o.get("p")), o.get("r").getAsFloat(), index.get(o.get("bone").getAsString()), top));
        }
        for (int s = 0; s < 5; s++) {
            Vector3f c = new Vector3f(); int cnt = 0; Vector3f first = null, last = null;
            for (BodySample b : body) if (b.bone == segments[s]) { c.add(b.p); cnt++; if (first == null) first = b.p; last = b.p; }
            segCenter[s] = cnt > 0 ? c.div(cnt) : new Vector3f(pivot[segments[s]]);
            Vector3f t = (first != null && last != first) ? new Vector3f(last).sub(first) : new Vector3f(0, 0, -1);
            segTangent[s] = t.normalize();
        }
        // ---- head
        JsonObject h = j.getAsJsonObject("head");
        head = index.get("head"); cheekR = index.get("cheek_r"); cheekL = index.get("cheek_l");
        headCenter = vec(h.get("center")); mouth = vec(h.get("mouth")); throat = vec(h.get("throat"));
        mouthTop = vec(h.get("mouthTop")); mouthBottom = vec(h.get("mouthBottom"));
        hingeR = vec(h.get("hingeR")); hingeL = vec(h.get("hingeL"));
        // ---- legs
        JsonArray ls = j.getAsJsonArray("legs");
        legs = new LegDef[ls.size()];
        for (int i = 0; i < legs.length; i++) {
            JsonObject o = ls.get(i).getAsJsonObject();
            String kind = o.get("kind").getAsString();
            JsonArray b = o.getAsJsonArray("bones"), jt = o.getAsJsonArray("joints");
            Vector3f[] joints = new Vector3f[4];
            for (int q = 0; q < 4; q++) joints[q] = vec(jt.get(q));
            legs[i] = new LegDef(o.get("k").getAsInt(), kind.equals("crab") ? 0 : kind.equals("spider") ? 1 : 2, o.get("side").getAsInt(),
                    o.get("u").getAsFloat(), o.get("reach").getAsFloat(), o.get("thick").getAsFloat(),
                    new int[]{b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()}, joints);
            if (o.has("pad")) legs[i].pad = o.get("pad").getAsFloat();
        }
        // ---- arms
        JsonArray as = j.getAsJsonArray("arms");
        arms = new ArmDef[as.size()];
        for (int i = 0; i < arms.length; i++) arms[i] = new ArmDef(as.get(i).getAsJsonObject());
        Integer[] ord = new Integer[arms.length];
        for (int i = 0; i < ord.length; i++) ord[i] = i;
        Arrays.sort(ord, (a, b) -> Float.compare(arms[a].u, arms[b].u));
        armsTailToHead = new int[ord.length];
        for (int i = 0; i < ord.length; i++) armsTailToHead[i] = ord[i];
        // ---- eyes, goo
        JsonArray es = j.getAsJsonArray("eyes");
        eyes = new EyeDef[es.size()];
        for (int i = 0; i < eyes.length; i++) eyes[i] = new EyeDef(es.get(i).getAsJsonObject());
        JsonArray gs = j.getAsJsonArray("goo");
        goo = new GooDef[gs.size()];
        for (int i = 0; i < goo.length; i++) {
            JsonObject o = gs.get(i).getAsJsonObject();
            goo[i] = new GooDef(o.get("k").getAsInt(), o.get("bone").getAsInt(), vec(o.get("top")), vec(o.get("tip")));
        }
        // ---- hitboxes
        for (JsonElement e : j.getAsJsonArray("parts")) {
            JsonObject o = e.getAsJsonObject();
            parts.add(new PartDef(index.get(o.get("bone").getAsString()), vec(o.get("center")), o.get("w").getAsFloat(), o.get("h").getAsFloat(),
                    o.get("solid").getAsBoolean(), o.get("kind").getAsString(), o.has("leg") ? o.get("leg").getAsInt() : -1));
        }
        JsonObject ext = j.getAsJsonObject("extent");
        extentMin = vec(ext.get("min")); extentMax = vec(ext.get("max"));

        // ---- breathing: which bones swell, and how far each child is pushed out by its parent's swelling
        swell = new float[n]; breathPush = new Vector3f[n]; swellFrame = new Matrix4f[n];
        for (int s = 0; s < 5; s++) {
            int b = segments[s];
            swell[b] = BODY_SWELL;
            Vector3f t = segTangent[s];
            Vector3f a = Math.abs(t.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f n1 = new Vector3f(a).cross(t).normalize(), n2 = new Vector3f(t).cross(n1).normalize();
            // columns n1, n2, t: scale the first two
            swellFrame[b] = new Matrix4f().set(new Matrix3f(n1, n2, t)).setTranslation(segCenter[s]);
        }
        for (int b : new int[]{head, cheekR, cheekL}) {
            swell[b] = HEAD_SWELL;
            swellFrame[b] = new Matrix4f().translation(headCenter);
        }
        for (int i = 0; i < n; i++) {
            int p = parent[i];
            if (p < 0 || swell[p] == 0f || swell[i] != 0f) continue;
            Vector3f pv = pivot[i];
            Vector3f push;
            int seg = segIndex(p);
            if (seg >= 0) {
                Vector3f d = new Vector3f(pv).sub(segCenter[seg]);
                Vector3f t = segTangent[seg];
                push = d.sub(new Vector3f(t).mul(d.dot(t)));
            } else push = new Vector3f(pv).sub(headCenter);
            breathPush[i] = push.mul(swell[p]);
        }
        prepareIK();
    }

    /** per bone: the leg whose hip it is (-1 if none), and the tentacle whose tip piece it is */
    private int[] legOfHip, tentacleOfTip;

    private void prepareIK() {
        legOfHip = new int[boneCount()]; tentacleOfTip = new int[boneCount()];
        Arrays.fill(legOfHip, -1); Arrays.fill(tentacleOfTip, -1);
        for (LegDef L : legs) {
            legOfHip[L.boneA] = L.k;
            if (L.kind == 2) tentacleOfTip[L.bones[2]] = L.k;
        }
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    /**
     * Bends leg L so its end lands on the model-space point (tx, ty, tz). parentM is the finished matrix of the
     * body piece the hip hangs from. w fades the IK in and out (1 = fully planted). The result is laid over
     * whatever rotation the two bones already have.
     */
    // dev only (-Dmountain.legdebug): why legs snap, counted on the render thread
    public static final boolean LEGDBG = Boolean.getBoolean("mountain.legdebug");
    public static final boolean OLD_BODY = Boolean.getBoolean("mountain.oldbody");   // dev: compare with the old way
    public static final int[] dbgBranch = new int[64], dbgSwitch = new int[64], dbgFar = new int[64], dbgNear = new int[64], dbgPsi = new int[64];
    private static void dbg(LegDef L, int branch, float dRaw, float minD, float maxD, float psiRaw) {
        if (!LEGDBG || !"Render thread".equals(Thread.currentThread().getName())) return;
        int k = L.k;
        if (dbgBranch[k] != 0 && dbgBranch[k] != branch) dbgSwitch[k]++;
        dbgBranch[k] = branch;
        if (dRaw > maxD) dbgFar[k]++;
        if (dRaw < minD) dbgNear[k]++;
        if (Math.abs(psiRaw) > 1.3f) dbgPsi[k]++;
    }
    private void solveLeg(LegDef L, float tx, float ty, float tz, Matrix4f parentM, float br, float w, Quaternionf[] rot) {
        Matrix4f inv = new Matrix4f(parentM).invertAffine();
        Vector3f V = inv.transformPosition(new Vector3f(tx, ty, tz));
        Vector3f push = breathPush[L.boneA];
        V.sub(L.joints[0]);
        if (push != null) V.sub(push.x * br, push.y * br, push.z * br);
        // turn about the hip to face the target
        float psi = 0f;
        if (Math.hypot(V.x, V.z) > 1e-3f) psi = (float) Math.atan2(L.ed.z * V.x - L.ed.x * V.z, L.ed.x * V.x + L.ed.z * V.z);
        float psiRaw = psi;
        psi = Math.max(-1.3f, Math.min(1.3f, psi));
        Quaternionf qy = new Quaternionf().rotationY(psi);
        Vector3f Vp = new Quaternionf(qy).conjugate().transform(new Vector3f(V));
        float x = Vp.dot(L.ed), y = Vp.y;
        if (L.kind == 0) {
            // crab: put the ankle where the upright foot piece reaches the target
            float axx = x - L.ankle.dot(L.ed), ayy = y - L.ankle.y;
            float dA = (float) Math.hypot(axx, ayy);
            if (dA > L.cMinD && dA < L.cMaxD) {
                dbg(L, 1, dA, L.cMinD, L.cMaxD, psiRaw);
                float[] q = bend2(L.cA, L.cB, L.aRest, L.cgRest, L.csig, axx, ayy);
                float d1 = q[0], d2 = q[1];
                Quaternionf qa = new Quaternionf().rotationY(psi * w).rotateAxis(d1 * w, L.n0.x, L.n0.y, L.n0.z);
                rot[L.boneA] = qa.mul(rot[L.boneA]);
                rot[L.bones[1]] = new Quaternionf().rotationAxis(d2 * w, L.n0.x, L.n0.y, L.n0.z).mul(rot[L.bones[1]]);
                rot[L.bones[2]] = new Quaternionf().rotationAxis(-(d1 + d2) * w, L.n0.x, L.n0.y, L.n0.z).mul(rot[L.bones[2]]);
                return;
            }
        }
        float[] r = bend(L, x, y);
        dbg(L, 2, (float) Math.hypot(x, y), L.minD, L.maxD, psiRaw);
        if (L.kind != 2) {
            // a tilted foot pad digs its lower edge in: lift the target by that much and bend again
            float tilt = r[0] + r[1] + (L.kind == 0 ? r[2] : 0f);
            float lift = Math.max(L.footR, 0.6f * L.pad) * (float) Math.abs(Math.sin(tilt));
            if (lift > 0.05f) r = bend(L, x, y + lift);
        }
        float d1 = r[0], d2 = r[1], d3 = r[2];
        Quaternionf qa = new Quaternionf().rotationY(psi * w).rotateAxis(d1 * w, L.n0.x, L.n0.y, L.n0.z);
        rot[L.boneA] = qa.mul(rot[L.boneA]);
        rot[L.boneB] = new Quaternionf().rotationAxis(d2 * w, L.n0.x, L.n0.y, L.n0.z).mul(rot[L.boneB]);
        if (d3 != 0f) rot[L.bones[2]] = new Quaternionf().rotationAxis(d3 * w, L.n0.x, L.n0.y, L.n0.z).mul(rot[L.bones[2]]);
    }

    /** the planar bend: {turn at the hip, turn at the knee, fold at the crab's lower joint} to reach (x, y) */
    private static float[] bend(LegDef L, float x, float y) {
        float A = L.A, B = L.B, gRest = L.gRest, d3 = 0f;
        float dRaw = (float) Math.hypot(x, y);
        if (L.kind == 0 && dRaw < B - 0.8f * A) {
            // too close for the long lower leg: fold it at the lower joint until it fits
            float want = Math.max(Math.abs(L.lowA - L.lowB) + 2f, dRaw + 0.8f * A);
            float c = (L.lowA * L.lowA + L.lowB * L.lowB - want * want) / (2f * L.lowA * L.lowB);
            float ang = (float) Math.acos(Math.max(-1f, Math.min(1f, c)));
            d3 = Math.max(0f, Math.min(1.3f, L.lowRest - ang)) * L.lowSign;
            // where the foot ends up in the leg's rest frame after that fold
            float cs = (float) Math.cos(d3), sn = (float) Math.sin(d3);
            float ex = L.x2 + (xe(L) - L.x2) * cs - (ye(L) - L.y2) * sn;
            float ey = L.y2 + (xe(L) - L.x2) * sn + (ye(L) - L.y2) * cs;
            B = (float) Math.hypot(ex - L.kx1, ey - L.ky1);
            gRest = (float) Math.atan2(ey - L.ky1, ex - L.kx1);
        }
        float minD = Math.min(L.minD, Math.abs(A - B) * 1.02f + 0.5f), maxD = L.maxD;
        float D = Math.max(minD, Math.min(maxD, dRaw));
        if (dRaw > 1e-4f) { x *= D / dRaw; y *= D / dRaw; }
        float cosPhi = (A * A + D * D - B * B) / (2f * A * D);
        float phi = (float) Math.acos(Math.max(-1f, Math.min(1f, cosPhi)));
        float th1 = (float) Math.atan2(y, x) + L.sig * phi;
        float d1 = wrap(th1 - L.aRest);
        float kx = A * (float) Math.cos(th1), ky = A * (float) Math.sin(th1);
        float gN = (float) Math.atan2(y - ky, x - kx);
        float d2 = wrap(gN - gRest - d1);
        return new float[]{d1, d2, d3};
    }

    /** plain two-bone bend (no fold): {turn at the hip, turn at the knee} */
    private static float[] bend2(float A, float B, float aRest, float gRest, float sig, float x, float y) {
        float D = (float) Math.hypot(x, y);
        float cosPhi = (A * A + D * D - B * B) / (2f * A * D);
        float phi = (float) Math.acos(Math.max(-1f, Math.min(1f, cosPhi)));
        float th1 = (float) Math.atan2(y, x) + sig * phi;
        float d1 = wrap(th1 - aRest);
        float kx = A * (float) Math.cos(th1), ky = A * (float) Math.sin(th1);
        float d2 = wrap((float) Math.atan2(y - ky, x - kx) - gRest - d1);
        return new float[]{d1, d2};
    }

    private static float xe(LegDef L) { return new Vector3f(L.end()).sub(L.joints[0]).dot(L.ed); }
    private static float ye(LegDef L) { return L.end().y - L.joints[0].y; }

    /** Points a tentacle's last piece at its tip target (it lies along the ground). */
    private void aimTip(LegDef L, float tx, float ty, float tz, Matrix4f parentM, float w, Quaternionf[] rot) {
        Matrix4f inv = new Matrix4f(parentM).invertAffine();
        Vector3f want = inv.transformPosition(new Vector3f(tx, ty, tz)).sub(L.joints[2]);
        if (want.lengthSquared() < 1e-4f) return;
        want.normalize();
        Quaternionf q = new Quaternionf().rotationTo(L.tipRest, want);
        q = new Quaternionf().slerp(q, w);
        rot[L.bones[2]] = q.mul(rot[L.bones[2]]);
    }

    private int segIndex(int bone) { for (int s = 0; s < 5; s++) if (segments[s] == bone) return s; return -1; }

    public int boneCount() { return boneNames.length; }

    /** the point the body leans and turns about (the middle of his body), in model space */
    public Vector3f bodyPivot() { return new Vector3f(pivot[segments[2]]); }
    /** where trunk piece i (0 tail .. 4 neck) turns, in model space */
    public Vector3f segmentPivot(int i) { return new Vector3f(pivot[segments[i]]); }
    /** which trunk piece (0 tail .. 4 neck) leg L hangs from */
    public int legSegment(LegDef L) { return segIndex(parent[L.boneA]); }
    private float hipY = Float.NaN;
    private void initHipY() {
        float sum = 0f; int c = 0;
        for (LegDef L : legs) { sum += L.joints[0].y; c++; }
        hipY = c > 0 ? sum / c : 50f;
    }

    public Matrix4f[] newPoseArray() {
        Matrix4f[] m = new Matrix4f[boneCount()];
        for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        return m;
    }

    // ------------------------------------------------------------------ pose
    public static final float STRIDE = 14f, DUTY = 0.62f;
    /** Horizontal distance his body covers in one full walk cycle at full size, so the planted feet do not slide. */
    public static final float CYCLE_LENGTH = STRIDE / DUTY;
    static final float BODY_SWELL = 0.05f, HEAD_SWELL = 0.03f, CHEEK_OPEN = 0.44f, EYE_TURN = 0.62f;

    /** How far up he has reared for the body slam (0 on all fours, 1 at the top). */
    public static float rearAmount(int attack, float t) {
        if (attack == RigState.UNMAKE) {                     // right up on end, held there far longer, then down
            if (t < 120f) return smooth(t / 120f);
            if (t < 390f) return 1f;
            if (t < 400f) { float u = (t - 390f) / 10f; return 1f - u * u; }
            return 0f;
        }
        if (attack != RigState.REAR) return 0f;
        if (t < 45f) return smooth(t / 45f);
        if (t < 55f) return 1f;
        if (t < 63f) { float u = (t - 55f) / 8f; return 1f - u * u; }
        return 0f;
    }
    public static final float REAR_ANGLE = 0.36f, TAIL_Z = 165f;
    /** lying down: how far his body comes down (model blocks), how far out the feet go (0..1 of the way to flat) */
    public static final float SLEEP_DROP = 44f, SPRAWL = 0.55f;

    /** how open eye k is when the eyes overall are this open (they open one after another, not all at once) */
    public static float eyeOpenFor(int k, float eyesOpen) {
        if (eyesOpen >= 1f) return 1f;
        if (eyesOpen <= 0f) return 0f;
        float order = ((k * 2654435761L) >>> 8 & 0xffff) / 65535f;        // a fixed shuffle
        float t = (eyesOpen * 1.6f - order * 0.6f) / 1.0f;
        return Math.max(0f, Math.min(1f, t));
    }

    private static float smooth(float t) { t = Math.max(0f, Math.min(1f, t)); return t * t * (3f - 2f * t); }
    private static float sin(double a) { return (float) Math.sin(a); }

    /**
     * Fills out[i] with each bone's model-space matrix. If draw is not null it also gets the matrix to draw each
     * bone's own mesh with (the same, plus the breathing swell on the body and head).
     */
    public void computePose(RigState s, Matrix4f[] out, Matrix4f[] draw) {
        if (Float.isNaN(hipY)) initHipY();
        int n = boneCount();
        Quaternionf[] rot = new Quaternionf[n];
        Vector3f[] off = new Vector3f[n];
        for (int i = 0; i < n; i++) rot[i] = new Quaternionf();
        float w = s.walkPhase, a = s.walkAmount, tt = s.time, dth = s.death, br = s.breath;
        float sl = smooth(Math.max(s.sleep, s.down));      // asleep, or knocked off his legs
        float twoPi = (float) (Math.PI * 2);
        float rear = rearAmount(s.attack, s.attackT);
        float rearLift = (float) Math.sin(REAR_ANGLE * rear) * (TAIL_Z - pivot[segments[2]].z);

        // ---- trunk: a slow travelling wave down the ridge while he walks, bending into turns, a nod with each breath
        for (int sgi = 0; sgi < 5; sgi++) {
            int b = segments[sgi];
            float ph = sgi * 0.9f;
            float yaw = a * 0.02f * sin(twoPi * w + ph) + s.turn * 0.045f * (sgi >= 2 ? 1f : -1f);
            float pit = a * 0.018f * sin(2 * twoPi * w + ph * 1.2f) + 0.006f * sin(tt * 0.013f + sgi);
            // leaning and bending with the ground turns about a point at hip height under the pivot, not about the
            // pivot itself (high up in his back): turning up there would swing his hips sideways off their feet
            Quaternionf terr = sgi == 2 ? new Quaternionf().rotateZ(s.bodyRoll).rotateX(s.bodyPitch) : new Quaternionf().rotateX(s.segBend[sgi]);
            Vector3f down = new Vector3f(0f, hipY - pivot[b].y, 0f);
            if (!OLD_BODY) off[b] = new Vector3f(down).sub(terr.transform(new Vector3f(down)));
            rot[b].mul(terr);
            if (sgi == 2) rot[b].rotateZ(0.05f * dth).rotateX(REAR_ANGLE * rear);
            rot[b].rotateY(yaw).rotateX(pit);
            // lying down his tail sags to the ground (a slope you can walk up) and his neck droops
            if (sl > 0f) rot[b].rotateX(sl * (sgi == 0 ? 0.28f : sgi == 1 ? 0.12f : sgi == 4 ? -0.06f : 0f));
        }
        rot[head].rotateY(s.headYaw).rotateX(s.headPitch - 0.05f * br - 0.12f * dth - 0.14f * sl);
        // the two halves of his face swing apart on their hinges; grinding shears them up and down
        float open = s.mouthOpen * CHEEK_OPEN;
        rot[cheekR].rotateY(-open);
        rot[cheekL].rotateY(open);
        off[cheekR] = new Vector3f(0, s.grind, 0);
        off[cheekL] = new Vector3f(0, -s.grind, 0);

        // ---- legs. With foot targets (the normal case) the legs are bent to reach them in the compose loop
        // below and only the folding as he dies is set here; without them they play a plain walk cycle.
        boolean ik = s.feet != null && s.feet.length >= legs.length * 3 && s.tips != null;
        float ikW = ik ? 1f - smooth(dth * 1.25f) : 0f;
        for (LegDef L : legs) {
            int b0 = L.bones[0], b1 = L.bones[1], b2 = L.bones[2];
            if (ik) {
                if (dth > 0f) {
                    rot[b0].rotateZ(L.side * (L.kind == 2 ? 0.45f : -0.30f) * dth);
                    rot[b1].rotateZ(L.side * (L.kind == 2 ? 0.05f : 0.55f) * dth);
                    rot[b2].rotateZ(L.side * (L.kind == 2 ? 0f : 0.35f) * dth);
                }
                continue;
            }
            float lft = 0f;
            if (s.legLift != null && L.k < s.legLift.length) lft = (float) Math.atan2(s.legLift[L.k], Math.max(8f, L.radius));
            lft = Math.max(-0.5f, Math.min(0.5f, lft));
            if (L.kind == 2) {
                // tentacles drag and writhe
                float ph = w * twoPi + L.u * 7f + (L.side > 0 ? 0f : 1.3f);
                float idle = tt * 0.035f + L.k * 1.7f;
                float amp = 0.35f + 0.65f * a;
                rot[b0].rotateY(amp * 0.09f * sin(ph) + 0.05f * sin(idle)).rotateZ(L.side * (0.04f + 0.04f * sin(idle * 0.7f) + lft + 0.45f * dth));
                rot[b1].rotateY(amp * 0.16f * sin(ph + 0.9f) + 0.08f * sin(idle + 0.8f)).rotateZ(L.side * 0.05f * sin(idle * 1.3f));
                rot[b2].rotateY(amp * 0.26f * sin(ph + 1.8f) + 0.12f * sin(idle + 1.6f));
                continue;
            }
            float p = w + L.u * 2.3f + (L.side > 0 ? 0f : 0.5f);
            p -= (float) Math.floor(p);
            float swing, lift = 0f, flex = 0f;
            if (p < DUTY) { float t = p / DUTY; swing = L.stepAmp * (1f - 2f * t); }
            else { float t = (p - DUTY) / (1f - DUTY); swing = -L.stepAmp + 2f * L.stepAmp * smooth(t); lift = sin(Math.PI * t); flex = lift; }
            swing *= a; lift *= a; flex *= a;
            float liftAng = (L.kind == 0 ? 0.20f : 0.16f) * lift;
            // a fidget while he stands: now and then one leg shifts its weight
            float fid = (1f - a) * Math.max(0f, sin(tt * 0.02f + L.k * 2.3f) - 0.85f) * 2.5f;
            rot[b0].rotateY(L.side * swing).rotateZ(L.side * (liftAng + 0.12f * fid + lft - 0.30f * dth));
            rot[b1].rotateZ(L.side * ((L.kind == 0 ? 0.28f : -0.22f) * flex + 0.55f * dth));
            rot[b2].rotateZ(L.side * ((L.kind == 0 ? -0.18f : 0.20f) * flex + 0.35f * dth));
        }

        // ---- arms
        int hold = s.holdArm, next = s.holdNext;
        Vector3f holdPt = null;
        if (hold >= 0 && hold < arms.length) {
            Vector3f ha = arms[hold].handPoint, hb = next >= 0 && next < arms.length ? arms[next].handPoint : mouth;
            holdPt = new Vector3f(ha).lerp(hb, smooth(s.holdT)).add(0f, 12f + 6f * sin(Math.PI * s.holdT), 0f);
        }
        Vector3f reach = new Vector3f(s.reachX, s.reachY, s.reachZ);
        Vector3f tmp = new Vector3f();
        for (ArmDef A : arms) {
            float ph = A.k * 1.37f;
            float calm = (1f - Math.min(1f, s.reachAmt)) * (1f - 0.8f * sl);
            Quaternionf q0 = new Quaternionf()
                    .rotateAxis(0.16f * sin(tt * 0.021f + ph) * calm, A.idleA.x, A.idleA.y, A.idleA.z)
                    .rotateAxis(0.13f * sin(tt * 0.017f + ph * 1.9f) * calm, A.idleB.x, A.idleB.y, A.idleB.z);
            float grip;                     // -0.2 open, 0.5 working, 1.1 fist
            Vector3f aim = null; float aimW = 0f;
            if (A.k == hold && holdPt != null) { aim = holdPt; aimW = 1f; grip = 1.15f; }
            else if (A.k == next && holdPt != null) { aim = holdPt; aimW = smooth(s.holdT * 1.4f); grip = -0.2f + 0.6f * s.holdT; }
            else if (s.reachAmt > 0.01f) {
                float d = tmp.set(A.handPoint).distance(reach);
                aimW = s.reachAmt * Math.max(0f, Math.min(1f, 1f - (d - 25f) / 60f));
                aim = reach; grip = 0.45f - 0.7f * aimW;
            } else grip = 0.45f + 0.35f * sin(tt * 0.09f + A.k * 0.7f);
            if (dth > 0f) { aim = null; grip = 0.9f * dth + grip * (1f - dth); }
            if (aim != null && aimW > 0f) {
                Vector3f want = new Vector3f(aim).sub(A.base);
                if (want.lengthSquared() > 1e-4f) {
                    Quaternionf qa = new Quaternionf().rotationTo(new Vector3f(A.restVec).normalize(), want.normalize());
                    q0 = new Quaternionf().slerp(qa, aimW).mul(q0);
                }
            }
            if (dth > 0f) q0.rotateAxis(-0.9f * dth, A.idleA.x, A.idleA.y, A.idleA.z);     // arms flop down
            if (sl > 0f) { q0.rotateAxis(-0.75f * sl, A.idleA.x, A.idleA.y, A.idleA.z); grip = grip * (1f - sl) + 0.3f * sl; }   // and lie limp while he sleeps
            rot[A.upper].set(q0);
            rot[A.fore].rotateAxis(0.22f * sin(tt * 0.033f + ph) * calm + 0.25f * dth, A.elbowAxis.x, A.elbowAxis.y, A.elbowAxis.z);
            rot[A.hand].rotateAxis(0.15f * sin(tt * 0.041f + ph * 0.6f), A.side.x, A.side.y, A.side.z);
            for (int fi = 0; fi < A.fingers.length; fi++) {
                Vector3f ax = A.fingerAxis[fi];
                float c = grip + 0.18f * sin(tt * 0.13f + fi * 0.9f + ph);
                rot[A.fingers[fi]].rotateAxis(c * 0.8f, ax.x, ax.y, ax.z);
            }
            rot[A.thumb].rotateAxis((grip + 0.15f * sin(tt * 0.11f + ph)) * 0.6f, A.thumbAxis.x, A.thumbAxis.y, A.thumbAxis.z);
            // a broken arm hangs: it flops over at the shoulder, the elbow folds and the fingers go slack
            if (s.armBroken(A.k)) {
                rot[A.upper].rotateAxis(0.85f, A.side.x, A.side.y, A.side.z);
                rot[A.fore].rotateAxis(1.15f, A.elbowAxis.x, A.elbowAxis.y, A.elbowAxis.z);
                for (int fi = 0; fi < A.fingers.length; fi++) {
                    Vector3f ax = A.fingerAxis[fi];
                    rot[A.fingers[fi]].rotateAxis(-0.35f, ax.x, ax.y, ax.z);
                }
            }
        }

        // ---- the jerk where he was just hit: that piece (and everything on it) snaps back and shakes
        if (s.flinchBone >= 0 && s.flinchBone < n && s.flinchAmt > 0.001f) {
            float jerk = s.flinchAmt * s.flinchAmt * 0.30f * sin(tt * 1.9f);
            rot[s.flinchBone].rotateX(jerk).rotateZ(jerk * 0.55f);
        }

        // ---- eyes: they split between everything he is watching (all on one thing when he stares); popped ones roll back
        Vector3f look = new Vector3f(s.lookX, s.lookY, s.lookZ);
        Vector3f[] watching = new Vector3f[1 + s.looks.length];
        watching[0] = look;
        for (int i = 0; i < s.looks.length; i++) watching[i + 1] = s.looks[i];
        int nWatch = s.attack == RigState.GAZE ? 1 : Math.max(1, Math.min(watching.length, s.lookCount));
        for (EyeDef E : eyes) {
            look = watching[nWatch == 1 ? 0 : (E.k * 7 + E.k / 3) % nWatch];
            Quaternionf q = rot[E.bone];
            if (s.isPopped(E.k)) {
                Vector3f ax = new Vector3f(E.dir).cross(0, 1, 0);
                if (ax.lengthSquared() < 1e-4f) ax.set(1, 0, 0);
                ax.normalize();
                q.rotateAxis(2.1f, ax.x, ax.y, ax.z);
                continue;
            }
            float wobble = 0.05f * sin(tt * 0.05f + E.k * 2.9f);
            if (s.lookAmt > 0.01f) {
                Vector3f want = new Vector3f(look).sub(E.center);
                if (want.lengthSquared() > 1e-3f) {
                    want.normalize();
                    float ang = (float) Math.acos(Math.max(-1f, Math.min(1f, E.dir.dot(want))));
                    Quaternionf qa = new Quaternionf().rotationTo(E.dir, want);
                    float turn = s.attack == RigState.GAZE ? 1.2f : EYE_TURN;
                    float lim = ang > turn ? turn / ang : 1f;
                    q.set(new Quaternionf().slerp(qa, lim * s.lookAmt));
                }
            }
            q.rotateY(wobble * (1f - s.lookAmt));
            float shut = 1f - eyeOpenFor(E.k, s.eyesOpen);
            if (shut > 0f) {                                // asleep: rolled back under the lid
                Vector3f ax = new Vector3f(E.dir).cross(0, 1, 0);
                if (ax.lengthSquared() < 1e-4f) ax.set(1, 0, 0);
                ax.normalize();
                q.rotateAxis(2.1f * smooth(shut), ax.x, ax.y, ax.z);
            }
            if (dth > 0f) {                                 // they sink back as he dies
                Vector3f ax = new Vector3f(E.dir).cross(0, 1, 0);
                if (ax.lengthSquared() > 1e-4f) { ax.normalize(); q.rotateAxis(1.4f * dth, ax.x, ax.y, ax.z); }
            }
        }

        // ---- strings of goo sway under the mouth
        for (GooDef g : goo) {
            rot[g.bone()].rotateX(0.05f * sin(tt * 0.061f + g.k() * 1.3f) + 0.08f * s.mouthOpen).rotateZ(0.045f * sin(tt * 0.047f + g.k() * 2.1f));
        }

        // ---- compose: M = parent * T(p + push) * R * T(-p)
        float bob = a * 0.9f * sin(4.0 * Math.PI * w);
        float lift = s.bodyLift + bob + 1.4f * br + rearLift - SLEEP_DROP * sl;
        for (int i = 0; i < n; i++) {
            if (ikW > 0f && parent[i] >= 0) {
                int li = legOfHip[i];
                if (li >= 0) solveLeg(legs[li], s.feet[li * 3], s.feet[li * 3 + 1], s.feet[li * 3 + 2], out[parent[i]], br, ikW, rot);
                int ti = tentacleOfTip[i];
                if (ti >= 0) aimTip(legs[ti], s.tips[ti * 3], s.tips[ti * 3 + 1], s.tips[ti * 3 + 2], out[parent[i]], ikW, rot);
            }
            Vector3f p = pivot[i];
            Matrix4f m = out[i];
            if (parent[i] < 0) m.identity().translate(0f, lift, 0f);
            else m.set(out[parent[i]]);
            float px = p.x, py = p.y, pz = p.z;
            if (off[i] != null) { px += off[i].x; py += off[i].y; pz += off[i].z; }
            if (breathPush[i] != null) { px += breathPush[i].x * br; py += breathPush[i].y * br; pz += breathPush[i].z * br; }
            m.translate(px, py, pz).rotate(rot[i]).translate(-p.x, -p.y, -p.z);
        }
        if (draw != null) {
            for (int i = 0; i < n; i++) {
                Matrix4f d = draw[i].set(out[i]);
                if (swell[i] != 0f && br != 0f) {
                    float k = 1f + swell[i] * br;
                    Matrix4f F = swellFrame[i];
                    boolean uniform = i == head || i == cheekR || i == cheekL;
                    Matrix4f S = new Matrix4f(F).scale(k, k, uniform ? k : 1f).mul(new Matrix4f(F).invert());
                    d.mul(S);
                }
            }
        }
    }
}
