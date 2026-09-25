package net.jj.hollowbell.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hollowbell's skeleton, read from hollowbell_rig.json (made by tools/convert.js), and the pose maths shared by the
 * server (hits, where he holds things, where his arm lands) and the client (drawing).
 * Model space: 1 unit = 1 block at size 1, y = 0 is the ground under his strands, x and z are centred on the crown.
 *
 * Since 1.1 the dome is cut into bands by height (it squeezes in more at the rim than at the top) and the rim into
 * sectors (a ripple runs round it). The arms and strands are chains: where each joint is comes from {@link BellAnim},
 * which swings them with weight, and the pose here just lays each segment along its piece of the chain.
 */
public final class BellRig {
    private static BellRig instance;

    public static synchronized BellRig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public enum Kind { BELL, RIM, CROWN, SPOT, ARM, STRAND, POD, EGG }

    public final String[] boneNames;
    public final int[] parent;
    public final Vector3f[] pivot;
    public final Kind[] kind;
    /** which band / sector / arm / strand / pod / egg / spot the bone belongs to, and which segment of it */
    public final int[] part, seg;
    public final Map<String, Integer> index = new HashMap<>();

    public final int crownY, rimY;
    public final ArmDef[] arms;
    public final StrandDef[] strands;
    public final BlobDef[] pods, eggs, spots;
    public final BlobDef crown;
    public final BandDef[] bands;
    public final SectorDef[] sectors;
    /** every arm and strand as a chain of joints, parents before the chains that hang from them */
    public final Chain[] chains;
    /** per bone: its chain (-1 if none) */
    public final int[] chainOf;
    /** the dome, one row per height from the rim up: y, inner wall radius, outer radius */
    public final float[][] dome;
    public final int crownBone;
    /** a bone of the dome, for tests and the like: the top band */
    public final int bellBone;

    public record ArmDef(int k, int[] bones, Vector3f[] joints, Vector3f tip, Vector3f centre, float angle) {}
    public record StrandDef(int k, int[] bones, Vector3f[] joints, Vector3f bottom, float top, float low) {}
    public record BlobDef(int k, int bone, Vector3f centre, float radius) {}
    public record BandDef(int bone, float top, float bottom) { public float mid() { return (top + bottom) * 0.5f; } }
    public record SectorDef(int bone, float angle) {}

    /**
     * A chain: bones b[0..m-1] laid between joints j[0..m] (j[m] is the tip), hanging from bone parentBone.
     * arm is true for an arm, index is which arm or strand.
     */
    public static final class Chain {
        public final int id, index, parentBone;
        public final boolean arm;
        public final int[] bones;
        public final Vector3f[] joints;
        public final float[] restLen;
        /** the chain this one hangs from (a branch strand), or -1, the segment, and how far along it */
        public int parentChain = -1, parentSeg = -1;
        public float parentFrac;
        Chain(int id, boolean arm, int index, int[] bones, Vector3f[] joints, int parentBone) {
            this.id = id; this.arm = arm; this.index = index; this.bones = bones; this.joints = joints; this.parentBone = parentBone;
            restLen = new float[bones.length];
            for (int i = 0; i < bones.length; i++) restLen[i] = Math.max(0.5f, joints[i].distance(joints[i + 1]));
        }
        public int points() { return joints.length; }
    }

    private BellRig(JsonObject j) {
        crownY = j.get("crownY").getAsInt();
        rimY = j.get("rimY").getAsInt();
        JsonArray bs = j.getAsJsonArray("bones");
        int nb = bs.size();
        boneNames = new String[nb]; parent = new int[nb]; pivot = new Vector3f[nb]; kind = new Kind[nb];
        part = new int[nb]; seg = new int[nb];
        for (int i = 0; i < nb; i++) {
            JsonObject b = bs.get(i).getAsJsonObject();
            boneNames[i] = b.get("name").getAsString();
            index.put(boneNames[i], i);
        }
        for (int i = 0; i < nb; i++) {
            JsonObject b = bs.get(i).getAsJsonObject();
            JsonElement p = b.get("parent");
            parent[i] = p == null || p.isJsonNull() ? -1 : index.get(p.getAsString());
            if (parent[i] >= i) throw new IllegalStateException("bone " + boneNames[i] + " comes before its parent");
            pivot[i] = vec(b.getAsJsonArray("pivot"));
            String n = boneNames[i];
            String[] w = n.split("_");
            kind[i] = switch (w[0]) {
                case "bell" -> Kind.BELL;
                case "rim" -> Kind.RIM;
                case "crown" -> Kind.CROWN;
                case "spot" -> Kind.SPOT;
                case "arm" -> Kind.ARM;
                case "strand" -> Kind.STRAND;
                case "pod" -> Kind.POD;
                case "egg" -> Kind.EGG;
                default -> throw new IllegalStateException("unknown bone " + n);
            };
            part[i] = w.length > 1 ? Integer.parseInt(w[1]) : 0;
            seg[i] = w.length > 2 ? Integer.parseInt(w[2]) : 0;
        }
        crownBone = index.get("crown");
        bellBone = index.get("bell_0");

        JsonArray a = j.getAsJsonArray("arms");
        arms = new ArmDef[a.size()];
        for (int i = 0; i < arms.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            arms[i] = new ArmDef(i, ints(o.getAsJsonArray("bones")), vecs(o.getAsJsonArray("joints")), vec(o.getAsJsonArray("tip")),
                    vec(o.getAsJsonArray("centre")), o.get("angle").getAsFloat());
        }
        a = j.getAsJsonArray("strands");
        strands = new StrandDef[a.size()];
        for (int i = 0; i < strands.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            strands[i] = new StrandDef(i, ints(o.getAsJsonArray("bones")), vecs(o.getAsJsonArray("joints")), vec(o.getAsJsonArray("bottom")),
                    o.get("top").getAsFloat(), o.get("low").getAsFloat());
        }
        pods = blobs(j.getAsJsonArray("pods"));
        eggs = blobs(j.getAsJsonArray("eggs"));
        spots = blobs(j.getAsJsonArray("spots"));
        JsonObject c = j.getAsJsonObject("crown");
        crown = new BlobDef(0, c.get("bone").getAsInt(), vec(c.getAsJsonArray("centre")), c.get("radius").getAsFloat());
        a = j.getAsJsonArray("dome");
        dome = new float[a.size()][];
        for (int i = 0; i < dome.length; i++) {
            JsonArray r = a.get(i).getAsJsonArray();
            dome[i] = new float[]{r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat()};
        }
        a = j.getAsJsonArray("bands");
        bands = new BandDef[a.size()];
        for (int i = 0; i < bands.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            bands[i] = new BandDef(o.get("bone").getAsInt(), o.get("top").getAsFloat(), o.get("bottom").getAsFloat());
        }
        a = j.getAsJsonArray("sectors");
        sectors = new SectorDef[a.size()];
        for (int i = 0; i < sectors.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            sectors[i] = new SectorDef(o.get("bone").getAsInt(), o.get("angle").getAsFloat());
        }

        // the chains, in the order their first bones come (so a branch comes after the strand it hangs from)
        List<Chain> cs = new ArrayList<>();
        for (ArmDef A : arms) {
            Vector3f[] jt = new Vector3f[A.joints().length + 1];
            System.arraycopy(A.joints(), 0, jt, 0, A.joints().length);
            jt[jt.length - 1] = A.tip();
            cs.add(new Chain(0, true, A.k(), A.bones(), jt, parent[A.bones()[0]]));
        }
        for (StrandDef S : strands) {
            Vector3f[] jt = new Vector3f[S.joints().length + 1];
            System.arraycopy(S.joints(), 0, jt, 0, S.joints().length);
            jt[jt.length - 1] = S.bottom();
            cs.add(new Chain(0, false, S.k(), S.bones(), jt, parent[S.bones()[0]]));
        }
        cs.sort((x, y) -> Integer.compare(x.bones[0], y.bones[0]));
        chains = new Chain[cs.size()];
        chainOf = new int[nb];
        java.util.Arrays.fill(chainOf, -1);
        for (int i = 0; i < chains.length; i++) {
            Chain o = cs.get(i);
            chains[i] = new Chain(i, o.arm, o.index, o.bones, o.joints, o.parentBone);
            for (int b : o.bones) chainOf[b] = i;
        }
        for (Chain ch : chains) {
            int pb = ch.parentBone;
            if (pb >= 0 && chainOf[pb] >= 0) {
                ch.parentChain = chainOf[pb];
                ch.parentSeg = seg[pb];
                Chain pc = chains[ch.parentChain];
                Vector3f a0 = pc.joints[ch.parentSeg], a1 = pc.joints[ch.parentSeg + 1];
                Vector3f d = new Vector3f(a1).sub(a0);
                float l2 = d.lengthSquared();
                ch.parentFrac = l2 < 1e-6f ? 0f : Mth.clamp(new Vector3f(ch.joints[0]).sub(a0).dot(d) / l2, 0f, 1f);
                if (ch.parentChain >= ch.id) throw new IllegalStateException("chain " + ch.id + " hangs from a later one");
            }
        }
        armChain = new int[arms.length];
        strandChain = new int[strands.length];
        for (Chain ch : chains) { if (ch.arm) armChain[ch.index] = ch.id; else strandChain[ch.index] = ch.id; }
    }

    /** which chain is arm a / strand s */
    public final int[] armChain, strandChain;

    private static BlobDef[] blobs(JsonArray a) {
        BlobDef[] out = new BlobDef[a.size()];
        for (int i = 0; i < out.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            out[i] = new BlobDef(i, o.get("bone").getAsInt(), vec(o.getAsJsonArray("centre")), o.get("radius").getAsFloat());
        }
        return out;
    }

    private static Vector3f vec(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()); }
    private static Vector3f[] vecs(JsonArray a) { Vector3f[] v = new Vector3f[a.size()]; for (int i = 0; i < v.length; i++) v[i] = vec(a.get(i).getAsJsonArray()); return v; }
    private static int[] ints(JsonArray a) { int[] v = new int[a.size()]; for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsInt(); return v; }

    private static BellRig load() {
        try (Reader r = new InputStreamReader(BellRig.class.getResourceAsStream("/hollowbell/hollowbell_rig.json"), StandardCharsets.UTF_8)) {
            return new BellRig(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            throw new RuntimeException("Hollowbell: could not read hollowbell_rig.json", e);
        }
    }

    public int boneCount() { return boneNames.length; }
    public Matrix4f[] newPose() { Matrix4f[] m = new Matrix4f[boneCount()]; for (int i = 0; i < m.length; i++) m[i] = new Matrix4f(); return m; }

    /** the radius of the dome's outside and inside at a height (model space), 0 above the crown */
    public float domeOuter(float y) { float[] r = domeRow(y); return r == null ? 0f : r[2]; }
    public float domeInner(float y) { float[] r = domeRow(y); return r == null ? 0f : r[1]; }
    private float @Nullable [] domeRow(float y) {
        int i = Math.round(y - dome[0][0]);
        return i < 0 || i >= dome.length ? null : dome[i];
    }

    // ------------------------------------------------------------------ the body

    /** is the bone drawn / hittable at all right now */
    public boolean shown(BellState st, int b) {
        return kind[b] != Kind.EGG || !st.eggGone[part[b]];
    }

    /** where the whole of him leans, dips and turns about: the middle of the bell */
    public Vector3f centre() { return new Vector3f(0, rimY + 30f, 0); }

    /** the whole of him: lowered, leaning (and flipped, in the dive), turned */
    public Matrix4f body(BellState st, Matrix4f out) {
        Vector3f c = centre();
        return out.identity().translate(0, -st.lower, 0).translate(c).rotate(bodyRotation(st, new Quaternionf())).translate(-c.x, -c.y, -c.z);
    }

    public Quaternionf bodyRotation(BellState st, Quaternionf out) {
        float tx = st.tiltX, tz = st.tiltZ;
        float a = (float) Math.sqrt(tx * tx + tz * tz);
        out.identity();
        if (a > 1e-6f) out.rotateAxis(a, tx / a, 0f, tz / a);
        if (st.spin != 0f) out.rotateY(st.spin);
        return out;
    }

    /** how far down the dome a height is: 0 at the crown, 1 at the rim */
    private float down(float y) { return Mth.clamp((crownY - y) / (float) (crownY - rimY), 0f, 1f); }

    /** the bell's squeeze: how much narrower (1 = as built) at a height */
    public float squeezeAt(BellState st, float y) {
        float w = down(y);
        float prof = 0.18f + 0.82f * w * (float) Math.sqrt(w);
        float s = 1f - 0.13f * st.squeeze * prof;
        s *= 1f + 0.10f * st.death * w + 0.03f * st.droop * w;
        return s;
    }

    /** how much taller the bell is (1 = as built) */
    public float stretchY(BellState st) {
        float sy = 1f + 0.06f * st.squeeze;
        sy *= 1f - 0.32f * st.death;
        sy *= 1f - 0.05f * st.droop;
        return sy;
    }

    /** a part of the dome at height y: squeezed and stretched round the crown */
    public Matrix4f band(BellState st, float y, Matrix4f body, Matrix4f out) {
        float sxz = squeezeAt(st, y), sy = stretchY(st);
        return out.set(body).translate(0, crownY, 0).scale(sxz, sy, sxz).translate(0, -crownY, 0);
    }

    /** a sector of the rim: the band at the rim, with the ripple running round it */
    public Matrix4f sector(BellState st, int i, Matrix4f body, Matrix4f out) {
        band(st, rimY + 6, body, out);
        float a = sectors[i].angle();
        float wave = (float) Math.sin(3 * a - st.time * 0.11f) * 0.7f + (float) Math.sin(5 * a + st.time * 0.07f) * 0.3f;
        float dy = 0.9f * st.ripple * wave;
        float r = 1f + 0.010f * st.ripple * wave;
        return out.translate(0, dy, 0).scale(r, 1f, r);
    }

    /**
     * Fills pose[b] with each bone's transform from its rest position to where it is now, in model space.
     * The arms and strands are laid along the chain points in st.chain.
     */
    public void computePose(BellState st, Matrix4f[] pose) {
        Scratch s = scratch.get();
        Matrix4f body = body(st, s.body);
        Quaternionf brot = bodyRotation(st, s.brot);
        int nb = boneCount();
        for (int b = 0; b < nb; b++) {
            switch (kind[b]) {
                case BELL -> band(st, bands[part[b]].mid(), body, pose[b]);
                case RIM -> sector(st, part[b], body, pose[b]);
                case CROWN -> band(st, crownY - 3, body, pose[b]);
                case SPOT -> {
                    if (part[b] == 4) {
                        // the glowing vase hangs from the crown and throbs a little on its own
                        band(st, crownY - 3, body, pose[b]);
                        float th = 1f + 0.02f * (float) Math.sin(st.time * 0.21f) + 0.02f * st.glow;
                        pose[b].translate(0, crownY, 0).scale(th, 1f, th).translate(0, -crownY, 0);
                    } else band(st, 182, body, pose[b]);
                }
                case ARM, STRAND -> {
                    int c = chainOf[b];
                    segmentPose(chains[c], seg[b], st.chain[c], brot, pose[b], s);
                }
                case POD, EGG -> {
                    Vector3f pv = pivot[b];
                    Quaternionf local = s.q.identity();
                    boolean pod = kind[b] == Kind.POD;
                    int k = part[b];
                    float ph = k * 1.7f + (pod ? 0f : 0.5f);
                    // pods bob and swing slowly, egg clumps wobble quicker
                    float amp = pod ? 0.06f + 0.03f * Math.abs(st.squeeze) : 0.05f + 0.05f * st.eggShake;
                    float sp = pod ? 1f : 1.6f + 1.5f * st.eggShake;
                    local.rotateX(amp * (float) Math.sin(st.time * 0.11f * sp + ph)).rotateZ(amp * (float) Math.cos(st.time * 0.09f * sp + ph * 1.3f));
                    float grow = 1f;
                    if (pod) {
                        grow = 0.3f + 0.7f * st.podGrowth[k];
                        if (!st.podPopped[k]) grow *= 1f + 0.03f * (float) Math.sin(st.time * 0.13f + ph) + 0.28f * st.podSwell;
                    } else grow = 1f + 0.12f * st.eggShake * (float) Math.max(0, Math.sin(st.time * 0.9f + ph));
                    pose[b].set(pose[parent[b]]).translate(pv).rotate(local);
                    if (grow != 1f) pose[b].scale(grow);
                    pose[b].translate(-pv.x, -pv.y, -pv.z);
                }
            }
        }
    }

    /**
     * One segment of a chain laid between its two points: turned from its built direction to the way it points
     * now (the least turn, on top of the way his body is turned), stretched or squashed along it to fit.
     */
    private void segmentPose(Chain c, int i, float[] pts, Quaternionf brot, Matrix4f out, Scratch s) {
        Vector3f j0 = c.joints[i], j1 = c.joints[i + 1];
        Vector3f d0 = s.v.set(j1).sub(j0);
        float l0 = d0.length();
        brot.transform(d0);
        Vector3f d = s.v2.set(pts[3 * i + 3] - pts[3 * i], pts[3 * i + 4] - pts[3 * i + 1], pts[3 * i + 5] - pts[3 * i + 2]);
        float l = d.length();
        Quaternionf r = s.q2;
        if (l0 > 1e-4f && l > 1e-4f) r.rotationTo(d0.x / l0, d0.y / l0, d0.z / l0, d.x / l, d.y / l, d.z / l);
        else r.identity();
        r.mul(brot);
        out.identity().translate(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]).rotate(r);
        float k = l0 > 1e-4f ? l / l0 : 1f;
        if (Math.abs(k - 1f) > 0.01f) {
            // squash or stretch along the segment's own built direction
            Vector3f u = s.v.set(j1).sub(j0).div(l0);
            Matrix4f sc = s.m.identity();
            float e = k - 1f;
            sc.m00(1 + e * u.x * u.x).m01(e * u.x * u.y).m02(e * u.x * u.z)
              .m10(e * u.y * u.x).m11(1 + e * u.y * u.y).m12(e * u.y * u.z)
              .m20(e * u.z * u.x).m21(e * u.z * u.y).m22(1 + e * u.z * u.z);
            out.mul(sc);
        }
        out.translate(-j0.x, -j0.y, -j0.z);
    }

    /** the matrix a chain hangs from right now: its rim sector, the vase, the dome, or the strand it grows from */
    public Matrix4f anchor(BellState st, Chain c, Matrix4f body, Matrix4f out) {
        int pb = c.parentBone;
        if (pb < 0) return out.set(body);
        return switch (kind[pb]) {
            case RIM -> sector(st, part[pb], body, out);
            case BELL -> band(st, bands[part[pb]].mid(), body, out);
            case SPOT -> {
                band(st, crownY - 3, body, out);
                yield out;
            }
            case ARM, STRAND -> {
                // a branch: the spot on the strand it grows from, carried along it (no turn, so it can never flip)
                Chain pc = chains[c.parentChain];
                float[] pp = st.chain[c.parentChain];
                int i = c.parentSeg;
                float f = c.parentFrac;
                float ax = pp[3 * i] + (pp[3 * i + 3] - pp[3 * i]) * f, ay = pp[3 * i + 1] + (pp[3 * i + 4] - pp[3 * i + 1]) * f, az = pp[3 * i + 2] + (pp[3 * i + 5] - pp[3 * i + 2]) * f;
                Vector3f j0 = pc.joints[i], j1 = pc.joints[i + 1];
                float rx = j0.x + (j1.x - j0.x) * f, ry = j0.y + (j1.y - j0.y) * f, rz = j0.z + (j1.z - j0.z) * f;
                Quaternionf q = bodyRotation(st, scratch.get().brot2);
                // translate so that the rest spot on the parent lands where it is now; the body's turn round it
                Vector3f rest = new Vector3f(rx, ry, rz);
                Vector3f turned = q.transform(new Vector3f(rest));
                yield out.identity().translate(ax - turned.x, ay - turned.y, az - turned.z).rotate(q);
            }
            default -> out.set(body);
        };
    }

    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final Quaternionf q = new Quaternionf(), q2 = new Quaternionf(), brot = new Quaternionf(), brot2 = new Quaternionf();
        final Vector3f v = new Vector3f(), v2 = new Vector3f();
        final Matrix4f body = new Matrix4f(), m = new Matrix4f();
    }

    // ------------------------------------------------------------------ points on him

    /** a rest-space point of a bone, where it is now */
    public Vector3f at(Matrix4f[] pose, int bone, Vector3f rest) { return pose[bone].transformPosition(rest, new Vector3f()); }

    /** the bottom end of a strand, now */
    public Vector3f strandTip(Matrix4f[] pose, int strand) {
        StrandDef S = strands[strand];
        return at(pose, S.bones()[S.bones().length - 1], S.bottom());
    }

    /** the tip of an arm, now */
    public Vector3f armTip(Matrix4f[] pose, int arm) {
        ArmDef A = arms[arm];
        return at(pose, A.bones()[A.bones().length - 1], A.tip());
    }

    /** the slam: up and out, a long hold at the top, then down hard and back */
    public static float slam(float t, int seg) {
        float up = seg == 0 ? 1.25f : seg == 1 ? 0.35f : 0.25f;
        float down = seg == 0 ? -0.08f : seg == 1 ? -0.25f : -0.2f;
        if (t < 0.5f) return up * smooth(t / 0.5f);
        if (t < 0.62f) return Mth.lerp(smooth((t - 0.5f) / 0.12f), up, down);
        return down * (1f - smooth((t - 0.62f) / 0.38f));
    }

    /** the moment in the slam when the arm hits the ground */
    public static final float SLAM_HIT = 0.62f;

    static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    // ------------------------------------------------------------------ hits

    public record Hit(int bone, float t, int vx, int vy, int vz) {}

    /**
     * The first block of him along a ray (model space; dir need not be unit, t is in its units), up to maxT.
     */
    public @Nullable Hit raycast(BellState st, Matrix4f[] pose, Vector3f from, Vector3f dir, float maxT) {
        BellModel m = BellModel.get();
        Hit best = null;
        float bestT = maxT;
        Matrix4f inv = new Matrix4f();
        Vector3f o = new Vector3f(), d = new Vector3f(), c = new Vector3f();
        for (int b = 0; b < boneCount(); b++) {
            float[] bb = m.bounds[b];
            if (bb == null || !shown(st, b)) continue;
            // quick: the bone's box as a ball, where it is now
            c.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f);
            float r = 0.5f * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0]) + (bb[4] - bb[1]) * (bb[4] - bb[1]) + (bb[5] - bb[2]) * (bb[5] - bb[2])) * 1.3f + 1f;
            pose[b].transformPosition(c);
            float dl2 = dir.lengthSquared();
            float tc = ((c.x - from.x) * dir.x + (c.y - from.y) * dir.y + (c.z - from.z) * dir.z) / dl2;
            float tt = Mth.clamp(tc, 0f, bestT);
            float px = from.x + dir.x * tt - c.x, py = from.y + dir.y * tt - c.y, pz = from.z + dir.z * tt - c.z;
            if (px * px + py * py + pz * pz > r * r) continue;
            pose[b].invert(inv);
            inv.transformPosition(from, o);
            inv.transformDirection(dir, d);
            Hit h = march(m, b, o, d, bb, bestT);
            if (h != null && h.t() < bestT) { best = h; bestT = h.t(); }
        }
        return best;
    }

    /** walks the ray through the bone's blocks (in its rest space) and returns the first one it enters */
    private static @Nullable Hit march(BellModel m, int b, Vector3f o, Vector3f d, float[] bb, float maxT) {
        // clip to the bone's box
        float t0 = 0f, t1 = maxT;
        float[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1e-9f) { if (oo[i] < bb[i] || oo[i] > bb[i + 3]) return null; continue; }
            float a = (bb[i] - oo[i]) / dd[i], c = (bb[i + 3] - oo[i]) / dd[i];
            if (a > c) { float s = a; a = c; c = s; }
            t0 = Math.max(t0, a); t1 = Math.min(t1, c);
            if (t0 > t1) return null;
        }
        float px = oo[0] + dd[0] * t0, py = oo[1] + dd[1] * t0, pz = oo[2] + dd[2] * t0;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int sx = dd[0] > 0 ? 1 : -1, sy = dd[1] > 0 ? 1 : -1, sz = dd[2] > 0 ? 1 : -1;
        float tdx = Math.abs(dd[0]) < 1e-9f ? Float.MAX_VALUE : Math.abs(1f / dd[0]);
        float tdy = Math.abs(dd[1]) < 1e-9f ? Float.MAX_VALUE : Math.abs(1f / dd[1]);
        float tdz = Math.abs(dd[2]) < 1e-9f ? Float.MAX_VALUE : Math.abs(1f / dd[2]);
        float tmx = Math.abs(dd[0]) < 1e-9f ? Float.MAX_VALUE : t0 + ((sx > 0 ? (x + 1 - px) : (px - x)) * tdx);
        float tmy = Math.abs(dd[1]) < 1e-9f ? Float.MAX_VALUE : t0 + ((sy > 0 ? (y + 1 - py) : (py - y)) * tdy);
        float tmz = Math.abs(dd[2]) < 1e-9f ? Float.MAX_VALUE : t0 + ((sz > 0 ? (z + 1 - pz) : (pz - z)) * tdz);
        float t = t0;
        for (int guard = 0; guard < 2048 && t <= t1; guard++) {
            if (m.has(b, x, y, z)) return new Hit(b, t, x, y, z);
            if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += sx; }
            else if (tmy < tmz) { t = tmy; tmy += tdy; y += sy; }
            else { t = tmz; tmz += tdz; z += sz; }
        }
        return null;
    }

    /** is a model-space point inside (or within pad blocks of) one of this bone's blocks */
    public boolean touches(Matrix4f[] pose, int b, Vector3f p, int pad, Matrix4f invScratch) {
        BellModel m = BellModel.get();
        float[] bb = m.bounds[b];
        if (bb == null) return false;
        pose[b].invert(invScratch);
        Vector3f q = invScratch.transformPosition(p, new Vector3f());
        if (q.x < bb[0] - pad || q.y < bb[1] - pad || q.z < bb[2] - pad || q.x > bb[3] + pad || q.y > bb[4] + pad || q.z > bb[5] + pad) return false;
        int x = (int) Math.floor(q.x), y = (int) Math.floor(q.y), z = (int) Math.floor(q.z);
        for (int a = -pad; a <= pad; a++) for (int c = -pad; c <= pad; c++) for (int e = -pad; e <= pad; e++)
            if (m.has(b, x + a, y + c, z + e)) return true;
        return false;
    }
}
