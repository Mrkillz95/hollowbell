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
import java.util.HashMap;
import java.util.Map;

/**
 * Hollowbell's skeleton, read from hollowbell_rig.json (made by tools/convert.js), and the pose maths shared by the
 * server (hits, where he holds things, where his arm lands) and the client (drawing).
 * Model space: 1 unit = 1 block at size 1, y = 0 is the ground under his strands, x and z are centred on the crown.
 */
public final class BellRig {
    private static BellRig instance;

    public static synchronized BellRig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public enum Kind { BELL, RIM, CROWN, SPOT, ARM, STRAND, POD, EGG, THREAD }

    public final String[] boneNames;
    public final int[] parent;
    public final Vector3f[] pivot;
    public final Kind[] kind;
    /** which arm / strand / pod / egg / thread / spot the bone belongs to, and which segment of it */
    public final int[] part, seg;
    public final Map<String, Integer> index = new HashMap<>();

    public final int crownY, rimY, threadTop, fadeFrom;
    public final ArmDef[] arms;
    public final StrandDef[] strands;
    public final BlobDef[] pods, eggs, spots;
    public final BlobDef crown;
    public final ThreadDef[] threads;
    /** the dome, one row per height from the rim up: y, inner wall radius, outer radius */
    public final float[][] dome;
    public final int bellBone, rimBone, crownBone;

    public record ArmDef(int k, int[] bones, Vector3f[] joints, Vector3f tip, Vector3f centre, float angle) {}
    public record StrandDef(int k, int[] bones, Vector3f[] joints, Vector3f bottom, float top, float low) {}
    public record BlobDef(int k, int bone, Vector3f centre, float radius) {}
    public record ThreadDef(int k, int[] bones, Vector3f base, float top, int ring, float angle) {}

    private BellRig(JsonObject j) {
        crownY = j.get("crownY").getAsInt();
        rimY = j.get("rimY").getAsInt();
        threadTop = j.get("threadTop").getAsInt();
        fadeFrom = j.get("fadeFrom").getAsInt();
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
                case "thread" -> Kind.THREAD;
                default -> throw new IllegalStateException("unknown bone " + n);
            };
            part[i] = w.length > 1 ? Integer.parseInt(w[1]) : 0;
            seg[i] = w.length > 2 ? Integer.parseInt(w[2]) : 0;
        }
        bellBone = index.get("bell"); rimBone = index.get("rim"); crownBone = index.get("crown");

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
        a = j.getAsJsonArray("threads");
        threads = new ThreadDef[a.size()];
        for (int i = 0; i < threads.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            threads[i] = new ThreadDef(i, ints(o.getAsJsonArray("bones")), vec(o.getAsJsonArray("base")), o.get("top").getAsFloat(),
                    o.get("ring").getAsInt(), o.get("angle").getAsFloat());
        }
        a = j.getAsJsonArray("dome");
        dome = new float[a.size()][];
        for (int i = 0; i < dome.length; i++) {
            JsonArray r = a.get(i).getAsJsonArray();
            dome[i] = new float[]{r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat()};
        }
    }

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

    // ------------------------------------------------------------------ the pose

    /** is the bone drawn / hittable at all right now */
    public boolean shown(BellState st, int b) {
        return switch (kind[b]) {
            case EGG -> !st.eggGone[part[b]];
            case THREAD -> seg[b] == 0 ? st.threadGrowth[part[b]] > 0.001f : st.threadGrowth[part[b]] >= 0.999f;
            default -> true;
        };
    }

    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        Quaternionf[] rot;
        final Quaternionf q = new Quaternionf(), q2 = new Quaternionf();
        final Vector3f v = new Vector3f(), axis = new Vector3f();
        float[] stretch;
    }

    private static final Vector3f UP = new Vector3f(0, 1, 0);

    /** a turn that swings whatever hangs below the pivot toward direction (dx, dz) by angle radians */
    private static Quaternionf swing(Quaternionf out, float dx, float dz, float angle) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6f || Math.abs(angle) < 1e-6f) return out.identity();
        // axis = dir x up; a positive turn about it carries a point below the pivot toward dir
        return out.identity().rotateAxis(angle, -dz / len, 0f, dx / len);
    }

    /**
     * Fills pose[b] with each bone's transform from its rest position to where it is now, in model space.
     */
    public void computePose(BellState st, Matrix4f[] pose) {
        Scratch s = scratch.get();
        int nb = boneCount();
        if (s.rot == null || s.rot.length != nb) {
            s.rot = new Quaternionf[nb];
            for (int i = 0; i < nb; i++) s.rot[i] = new Quaternionf();
            s.stretch = new float[nb];
        }
        Quaternionf[] rot = s.rot;
        float t = st.time;
        float fold = Math.max(st.drop, st.death);

        // the whole of him: hung lower, leaning, sunk
        float lower = st.lower + fold * (rimY - 12f) + st.sink * (crownY + 10f);
        Quaternionf tilt = new Quaternionf().rotateX(st.tiltX).rotateZ(st.tiltZ);
        if (st.death > 0f) tilt.rotateZ(0.22f * st.death);
        Vector3f centre = new Vector3f(0, rimY + 30f, 0);
        Matrix4f body = new Matrix4f().translate(0, -lower, 0).translate(centre).rotate(tilt).translate(-centre.x, -centre.y, -centre.z);

        // the bell squeezes in and stretches up with each pulse, round the crown
        float p = st.pulse;
        float sxz = 1f - 0.075f * p, sy = 1f + 0.035f * p;
        // dying, the bell slumps flat
        sxz *= 1f + 0.08f * st.death; sy *= 1f - 0.18f * st.death;
        Matrix4f bell = new Matrix4f(body).translate(0, crownY, 0).scale(sxz, sy, sxz).translate(0, -crownY, 0);

        for (int b = 0; b < nb; b++) {
            Kind k = kind[b];
            if (k == Kind.BELL || k == Kind.RIM || k == Kind.CROWN || k == Kind.SPOT) {
                pose[b].set(bell);
                if (k == Kind.SPOT && part[b] == 4) {
                    // the glowing vase throbs a little on its own
                    float th = 1f + 0.02f * (float) Math.sin(t * 0.21f);
                    pose[b].translate(0, crownY, 0).scale(th, 1f, th).translate(0, -crownY, 0);
                }
                rot[b].set(tilt);
                continue;
            }
            int par = parent[b];
            Quaternionf local = s.q.identity();
            float stretchY = 1f;
            Vector3f pv = pivot[b];
            switch (k) {
                case ARM -> armLocal(st, b, local, fold);
                case STRAND -> {
                    strandLocal(st, b, local, fold, t);
                    if (part[b] == st.grabStrand) stretchY = 1f - 0.8f * st.grabLift;
                }
                case POD, EGG -> {
                    float ph = part[b] * 1.7f + (k == Kind.EGG ? 0.5f : 0f);
                    float a = 0.05f + 0.04f * Math.abs(p);
                    local.rotateX(a * (float) Math.sin(t * 0.17f + ph)).rotateZ(a * (float) Math.cos(t * 0.13f + ph * 1.3f));
                }
                case THREAD -> {
                    ThreadDef T = threads[part[b]];
                    if (seg[b] == 0) {
                        // straight up whatever way he leans: they hang from the sky, not from him
                        local.set(tilt).conjugate();
                        float lean = -Mth.clamp((float) Math.sqrt(st.driftX * st.driftX + st.driftZ * st.driftZ) * 0.9f, 0f, 0.12f);
                        Quaternionf q2 = swing(s.q2, st.driftX, st.driftZ, lean);
                        local.premul(q2);
                        float sw = 0.012f + 0.004f * T.ring;
                        local.premul(s.q2.identity().rotateX(sw * (float) Math.sin(t * 0.05f + T.angle * 3f))
                                .rotateZ(sw * (float) Math.cos(t * 0.043f + T.angle * 5f)));
                        stretchY = Math.max(0.06f, st.threadGrowth[T.k()]);
                    } else {
                        float sw = 0.02f;
                        local.rotateX(sw * (float) Math.sin(t * 0.07f + T.angle * 2f)).rotateZ(sw * (float) Math.cos(t * 0.061f + T.angle * 4f));
                    }
                }
                default -> {}
            }
            // where the pivot is carried to by the parent, and the rotation handed down
            Vector3f at = pose[par].transformPosition(pv, s.v);
            rot[b].set(rot[par]).mul(local);
            // a strand segment under a stretched one: stretch too
            if (k == Kind.STRAND && kind[par] == Kind.STRAND) stretchY = s.stretch[par];
            s.stretch[b] = stretchY;
            pose[b].identity().translate(at).rotate(rot[b]);
            if (stretchY != 1f) pose[b].scale(1f, stretchY, 1f);
            pose[b].translate(-pv.x, -pv.y, -pv.z);
        }
    }

    private void armLocal(BellState st, int b, Quaternionf local, float fold) {
        ArmDef A = arms[part[b]];
        int sg = seg[b];
        float dx = (float) Math.cos(A.angle()), dz = (float) Math.sin(A.angle());
        // swimming: each pulse swings them out a little, the lower segments later and more
        float swing = st.armSwing * (0.05f + 0.03f * sg) * (st.pulse - 0.35f);
        float out = swing;
        // folded out when the bell comes down
        if (sg == 0) out += fold * 0.85f; else out -= fold * 0.25f;
        if (A.k() == st.slamArm) out += slam(st.slamT, sg);
        if (A.k() == st.wrapArm) {
            // reach out toward it, then the lower segments curl in round it
            float tx = st.wrapX - A.joints()[0].x, tz = st.wrapZ - A.joints()[0].z;
            float w = st.wrapAmt;
            if (sg == 0) {
                Quaternionf toward = swing(new Quaternionf(), tx, tz, 0.45f * w);
                local.mul(toward);
            } else out -= 1.05f * w;
        }
        local.mul(swing(new Quaternionf(), dx, dz, out));
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

    private void strandLocal(BellState st, int b, Quaternionf local, float fold, float t) {
        StrandDef S = strands[part[b]];
        int sg = seg[b], nseg = S.bones().length;
        float ph = S.k() * 2.39f;
        // sway, a little more at the bottom
        float amp = 0.018f + 0.006f * sg;
        local.rotateX(amp * (float) Math.sin(t * 0.045f + ph + sg * 0.7f)).rotateZ(amp * (float) Math.cos(t * 0.039f + ph * 1.3f + sg * 0.6f));
        // trailing behind as he drifts
        float sp = (float) Math.sqrt(st.driftX * st.driftX + st.driftZ * st.driftZ);
        if (sp > 1e-4f) local.premul(swing(new Quaternionf(), -st.driftX, -st.driftZ, Math.min(0.5f, sp * 1.6f) / nseg * (sg == 0 ? 0.6f : 1f)));
        Vector3f top = S.joints()[0];
        float ox = top.x, oz = top.z;
        if (ox * ox + oz * oz < 1f) { ox = (float) Math.cos(ph); oz = (float) Math.sin(ph); }
        // buckling under him when the bell comes down: zig zag, out
        if (fold > 0f) {
            float z = (sg % 2 == 0 ? 1f : -1.6f) * fold * 1.05f;
            if (sg == 0) z = fold * 0.7f;
            local.premul(swing(new Quaternionf(), ox, oz, z));
        }
        // the sweep: everything swung one way
        if (st.sweep != 0f) {
            float dx = (float) Math.cos(st.sweepDir), dz = (float) Math.sin(st.sweepDir);
            local.premul(swing(new Quaternionf(), dx, dz, st.sweep * (sg == 0 ? 0.45f : 0.18f)));
        }
        // the curtain: the bottoms drawn in round the one inside
        if (st.curtain > 0f && sg == 0) {
            float tx = st.curtainX - top.x, tz = st.curtainZ - top.z;
            float d = (float) Math.sqrt(tx * tx + tz * tz);
            float h = Math.max(20f, top.y);
            float want = (float) Math.atan2(Math.max(0f, d - 6f), h);
            local.premul(swing(new Quaternionf(), tx, tz, want * st.curtain));
        }
        // grabbing: the strand reaches for it
        if (S.k() == st.grabStrand && sg == 0) {
            float tx = st.grabX - top.x, tz = st.grabZ - top.z;
            float d = (float) Math.sqrt(tx * tx + tz * tz);
            float h = Math.max(10f, top.y - st.grabY);
            local.premul(swing(new Quaternionf(), tx, tz, (float) Math.atan2(d, h) * st.grabReach));
        }
    }

    private static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

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
            float r = 0.5f * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0]) + (bb[4] - bb[1]) * (bb[4] - bb[1]) + (bb[5] - bb[2]) * (bb[5] - bb[2])) * 1.2f + 1f;
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
