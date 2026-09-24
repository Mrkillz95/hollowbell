package net.jj.mountain.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jj.mountain.MountainMod;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.rig.MountainRig;
import net.jj.mountain.rig.RigState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Effects drawn at his scale (vanilla particles are a tenth of a block and vanish at his size):
 * the black goo pouring from his mouth to the ground, the air streaming into his mouth when he breathes in,
 * the blast when he breathes out, and the shock ring of his last breath.
 */
public final class MountainFX {
    private MountainFX() {}
    public static final ResourceLocation GOO_TEX = ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "textures/entity/goo_stream.png");
    public static final ResourceLocation TONGUE_TEX = ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "textures/entity/tongue.png");

    // ---- everything his eye storm has hold of, as the server last named it: one beam each, however many
    private static int stormFor = -1;
    private static int[] stormIds = new int[0];
    private static long stormAt;

    /** leaving a world: nothing is still being burned anywhere */
    public static void forgetStorm() { stormFor = -1; stormIds = new int[0]; }

    public static void storm(int mountain, int[] targets) {
        stormFor = mountain;
        stormIds = targets;
        stormAt = System.currentTimeMillis();
    }

    /** the list is only good for a moment: if the server has gone quiet, the beams go out */
    private static int[] stormNow(MountainEntity e) {
        if (e.getId() != stormFor || System.currentTimeMillis() - stormAt > 2500) return null;
        return stormIds.length == 0 ? null : stormIds;
    }

    public static void render(MountainEntity e, Matrix4f rel, Matrix4f[] bones, RigState st, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        MountainRig rig = MountainRig.get();
        float s = e.mountainScale();
        float time = e.tickCount + partial;
        PoseStack.Pose pose = ps.last();
        boolean dying = e.isDeadOrDying();
        int stage = e.stage();
        float stT = e.stageTime() + partial;
        boolean strong = e.strongBreathSynced();
        Matrix4f head = new Matrix4f(rel).mul(bones[rig.head]);
        Vector3f mouth = head.transformPosition(new Vector3f(rig.mouth));
        Vector3f fwd = head.transformDirection(new Vector3f(0, 0, -1)).normalize();

        // ---- goo pouring from his mouth all the way to the ground
        if ((!dying || e.deathTime < 150) && e.gooOn() && e.sleepAmount() < 0.5f) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(GOO_TEX));
            for (MountainRig.GooDef g : rig.goo) {
                if (g.k() % 3 == 1) continue;                  // not every string pours at once
                Vector3f tip = new Matrix4f(rel).mul(bones[g.bone()]).transformPosition(new Vector3f(g.tip()).add(0.5f, 0.5f, 0.5f));
                int gx = Mth.floor(e.getX() + tip.x), gz = Mth.floor(e.getZ() + tip.z);
                float ground = e.groundAt(gx, gz) - (float) e.getY();
                float len = tip.y - ground;
                if (len <= 0.3f) continue;
                float r = (0.16f + 0.52f * s) * (0.55f + 0.8f * ((g.k() * 37) % 10) / 10f);
                stream(pose, vc, tip, len, r, time, g.k(), light);
            }
        }

        // ---- the tongue, out to wherever its tip is
        if (st.attack == RigState.TONGUE && st.attackT > 11f) {
            double ex = Mth.lerp(partial, e.xOld, e.getX()), ey = Mth.lerp(partial, e.yOld, e.getY()), ez = Mth.lerp(partial, e.zOld, e.getZ());
            Vector3f tip = new Vector3f((float) (st.atkX - ex), (float) (st.atkY - ey), (float) (st.atkZ - ez));
            if (tip.distance(mouth) > 1.5f)
                tongue(pose, buf.getBuffer(RenderType.entityCutoutNoCull(TONGUE_TEX)), mouth, tip, 2.0f + 5.0f * s, time, light);
        }

        // ---- breathing in: air streaming into his mouth
        if (!dying && stage == MountainEntity.INHALE || dying && e.deathTime < 60) {
            float k = dying ? 1f : strong ? 1f : 0.35f;
            float in = Mth.clamp(stT / 12f, 0f, 1f);
            VertexConsumer glow = buf.getBuffer(RenderType.lightning());
            float range = (strong || dying ? 95 : 45) * s + 10;
            int n = strong || dying ? 34 : 14;
            Vector3f up = Math.abs(fwd.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f u = new Vector3f(fwd).cross(up).normalize(), v = new Vector3f(u).cross(fwd).normalize();
            for (int i = 0; i < n; i++) {
                float a = i * 2.39996f, spread = 0.25f + 0.55f * ((i * 53) % 17) / 17f;
                Vector3f dir = new Vector3f(fwd).add(new Vector3f(u).mul(Mth.cos(a) * spread)).add(new Vector3f(v).mul(Mth.sin(a) * spread * 0.7f)).normalize();
                float f = (time * (0.018f + 0.01f * ((i * 7) % 5)) + i * 0.137f) % 1f;
                float d0 = range * (1f - f), d1 = Math.max(0f, d0 - (6f + 14f * s) * (0.6f + f));
                Vector3f p0 = new Vector3f(dir).mul(d0).add(mouth), p1 = new Vector3f(dir).mul(d1).add(mouth);
                float alpha = 0.55f * k * in * Mth.sin((float) Math.PI * f);
                streak(pose, glow, p0, p1, 0.2f + 1.1f * s * (1f - f), 0xE4EEF4, alpha);
            }
        }

        // ---- breathing out: the blast
        if (!dying && stage == MountainEntity.EXHALE && stT < 22 || dying && e.deathTime >= 80 && e.deathTime < 104) {
            float t = dying ? (e.deathTime + partial - 80f) : stT;
            boolean big = strong || dying;
            float grow = Mth.clamp(t / 5f, 0f, 1f), fade = Mth.clamp((22f - t) / 10f, 0f, 1f);
            float len = ((big ? 115 : 50) * s + (big ? 14 : 6)) * grow;
            VertexConsumer beam = buf.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
            Vector3f up = Math.abs(fwd.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f u = new Vector3f(fwd).cross(up).normalize(), v = new Vector3f(u).cross(fwd).normalize();
            tube(pose, beam, mouth, fwd, u, v, len, 3f + 16f * s, (float) Math.tan(Math.toRadians(big ? 24 : 14)), time, 0xC9CDD2, (big ? 0.55f : 0.3f) * fade, 12, 14);
            tube(pose, beam, mouth, fwd, u, v, len * 0.8f, 2f + 9f * s, (float) Math.tan(Math.toRadians(big ? 12 : 7)), time * 1.3f, 0x140C18, (big ? 0.7f : 0.35f) * fade, 12, 12);
        }
        // ---- the gaze: a burning line from many of his eyes to whatever he stares at
        if (!dying && st.attack == RigState.GAZE && st.attackT > 48f && st.attackT < 114f) {
            float fade = Mth.clamp((st.attackT - 48f) / 4f, 0f, 1f) * Mth.clamp((114f - st.attackT) / 6f, 0f, 1f);
            Vector3f at = new Matrix4f(rel).transformPosition(new Vector3f(st.lookX, st.lookY, st.lookZ));
            VertexConsumer glow = buf.getBuffer(RenderType.lightning());
            for (MountainRig.EyeDef E : rig.eyes) {
                if (E.k % 7 != 0 || st.isPopped(E.k)) continue;
                Vector3f eye = new Matrix4f(rel).mul(bones[E.bone]).transformPosition(new Vector3f(E.center));
                float flick = 0.65f + 0.35f * Mth.sin(time * 0.9f + E.k * 1.3f);
                beam(pose, glow, eye, at, (0.1f + 0.45f * s) * flick, 0xFF3020, 0.75f * fade, 0.35f * fade);
                beam(pose, glow, eye, at, (0.04f + 0.15f * s) * flick, 0xFFE0C0, 0.9f * fade, 0.5f * fade);
            }
        }

        // ---- the storm: one heavy beam out of one eye onto each of them, not a hail of thin ones
        if (!dying && st.attack == RigState.EYE_STORM && st.attackT > 28f && st.attackT < 116f) {
            float fade = Mth.clamp((st.attackT - 28f) / 5f, 0f, 1f) * Mth.clamp((116f - st.attackT) / 8f, 0f, 1f);
            Vector3f[] marks;
            int[] ids = stormNow(e);
            if (ids != null && net.minecraft.client.Minecraft.getInstance().level != null) {
                // the whole list, straight off the server: fifty things standing there means fifty beams
                var lvl = net.minecraft.client.Minecraft.getInstance().level;
                java.util.List<Vector3f> got = new java.util.ArrayList<>(ids.length);
                for (int id : ids) {
                    var t = lvl.getEntity(id);
                    if (t == null || !t.isAlive()) continue;
                    var mp = e.worldToModelPoint(t.getEyePosition());
                    got.add(new Matrix4f(rel).transformPosition(new Vector3f((float) mp.x, (float) mp.y, (float) mp.z)));
                }
                marks = got.toArray(new Vector3f[0]);
            } else {
                marks = new Vector3f[0];
            }
            if (marks.length == 0) {                       // nothing named yet: fall back on where he is looking
                int k = Math.max(1, Math.min(4, st.lookCount));
                marks = new Vector3f[k];
                marks[0] = new Matrix4f(rel).transformPosition(new Vector3f(st.lookX, st.lookY, st.lookZ));
                for (int i = 1; i < k; i++)
                    marks[i] = new Matrix4f(rel).transformPosition(new Vector3f(st.looks[i - 1]));
            }
            int n = marks.length;
            VertexConsumer glow = buf.getBuffer(RenderType.lightning());
            int eyes = rig.eyes.length;
            for (int i = 0; i < n; i++) {
                // one eye of his own for each of them, well apart from the others, and not one that has burst
                MountainRig.EyeDef E = null;
                int step = Math.max(1, eyes / Math.max(1, n));
                for (int k = 0; k < eyes; k++) {
                    MountainRig.EyeDef c2 = rig.eyes[(i * step + k) % eyes];
                    if (!st.isPopped(c2.k)) { E = c2; break; }
                }
                if (E == null) continue;
                Vector3f eye = new Matrix4f(rel).mul(bones[E.bone]).transformPosition(new Vector3f(E.center));
                float pulse = 0.85f + 0.15f * Mth.sin(time * 2.1f + i * 2.4f);
                float w = (0.7f + 2.6f * s) * pulse * (n > 8 ? (float) Math.sqrt(8.0 / n) : 1f);
                beam(pose, glow, eye, marks[i], w * 1.5f, 0xB01000, 0.45f * fade, 0.22f * fade);
                beam(pose, glow, eye, marks[i], w, 0xFF4010, 0.85f * fade, 0.45f * fade);
                beam(pose, glow, eye, marks[i], w * 0.42f, 0xFFF4E0, 1.0f * fade, 0.7f * fade);
            }
        }

        // ---- the flood: a torrent of goo from his split face down onto the ground in front
        if (!dying && st.attack == RigState.VOMIT && st.attackT > 12f && st.attackT < 80f) {
            float grow = Mth.clamp((st.attackT - 12f) / 8f, 0f, 1f) * Mth.clamp((80f - st.attackT) / 6f, 0f, 1f);
            Vector3f flat = new Vector3f(fwd.x, 0, fwd.z);
            if (flat.lengthSquared() < 1e-4f) flat.set(0, 0, -1);
            flat.normalize();
            int gx = Mth.floor(e.getX() + mouth.x + flat.x * 20 * s), gz = Mth.floor(e.getZ() + mouth.z + flat.z * 20 * s);
            float groundY = e.groundAt(gx, gz) - (float) e.getY();
            float drop = Math.max(2f, mouth.y - groundY), land = 14f + 40f * s;
            int N = 14;
            Vector3f[] pts = new Vector3f[N + 1];
            for (int i = 0; i <= N; i++) {
                float u = (float) i / N;
                pts[i] = new Vector3f(mouth).add(new Vector3f(flat).mul(land * u)).add(0, -drop * u * u, 0);
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(GOO_TEX));
            flow(pose, vc, pts, (1.5f + 9f * s) * grow, time, light);
        }

        // ---- the body slam: a shock ring running out from where his head came down
        if (!dying && st.attack == RigState.REAR && st.attackT > 63f && st.attackT < 93f) {
            float f = st.attackT - 63f;
            Vector3f hm = head.transformPosition(new Vector3f(rig.mouth));
            int gx = Mth.floor(e.getX() + hm.x), gz = Mth.floor(e.getZ() + hm.z);
            float gy = e.groundAt(gx, gz) - (float) e.getY() + 0.3f;
            float r = 4f * s + 2f + f * (3.2f * s + 1.2f);
            ring(pose, buf, new Vector3f(hm.x, gy, hm.z), r, 3f + 8f * s, 2f + 7f * s, 0xD9C7B0, 1f - f / 30f, time);
        }

        if (dying && e.deathTime >= 80 && e.deathTime < 110) {
            float f = (e.deathTime + partial - 80f) / 30f;
            ring(pose, buf, new Vector3f(0, 0.5f, 0), (90 * s + 16) * f, 4f + 10f * s, 5f + 14f * s, 0xE6DDE8, 1f - f, time);
        }
    }

    private static int argb(int rgb, float a) { return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    /** A falling rope of goo: a wobbling tube with the goo texture sliding down it. */
    private static void stream(PoseStack.Pose pose, VertexConsumer vc, Vector3f top, float len, float r, float time, int seed, int light) {
        int K = 8, N = Math.max(3, Math.min(16, (int) (len / 4f)));
        Vector3f[][] ring = new Vector3f[N + 1][K + 1];
        for (int i = 0; i <= N; i++) {
            float f = (float) i / N;
            float y = -len * f;
            float rr = r * (1f + 0.25f * f + 0.18f * Mth.sin(time * 0.35f + i * 1.3f + seed));
            float sway = 0.35f * r * f * Mth.sin(time * 0.06f + seed * 1.7f);
            for (int k = 0; k <= K; k++) {
                float a = (float) (k * Math.PI * 2 / K);
                ring[i][k] = new Vector3f(top.x + Mth.cos(a) * rr + sway, top.y + y, top.z + Mth.sin(a) * rr);
            }
        }
        float scroll = time * 0.08f + seed * 0.37f;
        int c = 0xFFFFFFFF;
        for (int i = 0; i < N; i++) {
            float v0 = (float) i / N * len / (r * 4f) - scroll, v1 = (float) (i + 1) / N * len / (r * 4f) - scroll;
            for (int k = 0; k < K; k++) {
                Vector3f a = ring[i][k], b = ring[i][k + 1], cc = ring[i + 1][k + 1], d = ring[i + 1][k];
                float u0 = (float) k / K, u1 = (float) (k + 1) / K;
                float nx = Mth.cos((float) ((k + 0.5) * Math.PI * 2 / K)), nz = Mth.sin((float) ((k + 0.5) * Math.PI * 2 / K));
                evert(pose, vc, a, c, u0, v0, light, nx, nz);
                evert(pose, vc, b, c, u1, v0, light, nx, nz);
                evert(pose, vc, cc, c, u1, v1, light, nx, nz);
                evert(pose, vc, d, c, u0, v1, light, nx, nz);
            }
        }
    }

    /** His tongue: a flattened, tapering tube from his mouth to its tip, arching a little and rippling. */
    private static void tongue(PoseStack.Pose pose, VertexConsumer vc, Vector3f from, Vector3f to, float r, float time, int light) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        Vector3f fwd = new Vector3f(d).div(len);
        Vector3f side = new Vector3f(fwd).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-4f) side.set(1, 0, 0);
        side.normalize();
        Vector3f up = new Vector3f(side).cross(fwd).normalize();
        int K = 10, N = Math.max(6, Math.min(40, (int) (len / 2.5f)));
        Vector3f[][] ring = new Vector3f[N + 1][K + 1];
        Vector3f[] nrm = new Vector3f[K + 1];
        for (int i = 0; i <= N; i++) {
            float f = (float) i / N;
            // a slight arch, a ripple running out along it, and a rounded tip
            float arch = 0.10f * len * Mth.sin((float) Math.PI * f);
            float wave = 0.25f * r * Mth.sin(time * 0.5f - f * 9f) * f;
            float taper = (1f - 0.55f * f) * (f > 0.92f ? Mth.sqrt(Math.max(0f, (1f - f) / 0.08f)) : 1f);
            Vector3f c = new Vector3f(from).lerp(to, f).add(new Vector3f(up).mul(arch)).add(new Vector3f(side).mul(wave));
            for (int k = 0; k <= K; k++) {
                float a = (float) (k * Math.PI * 2 / K);
                float ca = Mth.cos(a), sa = Mth.sin(a);
                ring[i][k] = new Vector3f(c).add(new Vector3f(side).mul(ca * r * taper)).add(new Vector3f(up).mul(sa * r * 0.42f * taper));
                if (i == 0) nrm[k] = new Vector3f(side).mul(ca).add(new Vector3f(up).mul(sa)).normalize();
            }
        }
        int col = 0xFFFFFFFF;
        for (int i = 0; i < N; i++) {
            float v0 = (float) i / N * len / (r * 3f), v1 = (float) (i + 1) / N * len / (r * 3f);
            for (int k = 0; k < K; k++) {
                float u0 = (float) k / K, u1 = (float) (k + 1) / K;
                Vector3f n = nrm[k];
                tv(pose, vc, ring[i][k], col, u0, v0, light, n);
                tv(pose, vc, ring[i][k + 1], col, u1, v0, light, n);
                tv(pose, vc, ring[i + 1][k + 1], col, u1, v1, light, n);
                tv(pose, vc, ring[i + 1][k], col, u0, v1, light, n);
            }
        }
    }

    private static void tv(PoseStack.Pose pose, VertexConsumer vc, Vector3f p, int argb, float u, float v, int light, Vector3f n) {
        vc.addVertex(pose, p.x, p.y, p.z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
    }

    private static void evert(PoseStack.Pose pose, VertexConsumer vc, Vector3f p, int argb, float u, float v, int light, float nx, float nz) {
        vc.addVertex(pose, p.x, p.y, p.z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, 0.3f, nz);
    }

    /** a thin glowing line from a to b, as two crossed quads */
    private static void streak(PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, float w, int rgb, float alpha) {
        if (alpha <= 0.003f) return;
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1e-4f) return;
        Vector3f up = Math.abs(d.y) < 0.9f * d.length() ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s1 = new Vector3f(d).cross(up).normalize().mul(w), s2 = new Vector3f(d).cross(s1).normalize().mul(w);
        int ca = argb(rgb, alpha), cb = argb(rgb, 0f);
        for (Vector3f side : new Vector3f[]{s1, s2}) {
            Vector3f a0 = new Vector3f(a).add(side), a1 = new Vector3f(a).sub(side), b0 = new Vector3f(b).add(side), b1 = new Vector3f(b).sub(side);
            quad(pose, vc, false, a0, a1, b1, b0, cb, cb, ca, ca, 0, 0, 0, 0);
            quad(pose, vc, false, b0, b1, a1, a0, ca, ca, cb, cb, 0, 0, 0, 0);
        }
    }

    /** a glowing line from a to b, as two crossed quads, with its own alpha at each end */
    private static void beam(PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, float w, int rgb, float alphaA, float alphaB) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1e-4f || alphaA + alphaB <= 0.003f) return;
        Vector3f up = Math.abs(d.y) < 0.9f * d.length() ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s1 = new Vector3f(d).cross(up).normalize().mul(w), s2 = new Vector3f(d).cross(s1).normalize().mul(w);
        int ca = argb(rgb, alphaA), cb = argb(rgb, alphaB);
        for (Vector3f side : new Vector3f[]{s1, s2}) {
            Vector3f a0 = new Vector3f(a).add(side), a1 = new Vector3f(a).sub(side), b0 = new Vector3f(b).add(side), b1 = new Vector3f(b).sub(side);
            quad(pose, vc, false, a0, a1, b1, b0, ca, ca, cb, cb, 0, 0, 0, 0);
            quad(pose, vc, false, b0, b1, a1, a0, cb, cb, ca, ca, 0, 0, 0, 0);
        }
    }

    /** a thick rope of goo along a curve, the texture sliding along it */
    private static void flow(PoseStack.Pose pose, VertexConsumer vc, Vector3f[] pts, float r, float time, int light) {
        if (r < 0.05f) return;
        int K = 10, N = pts.length - 1;
        Vector3f[][] ring = new Vector3f[N + 1][K + 1];
        for (int i = 0; i <= N; i++) {
            Vector3f t = new Vector3f(pts[Math.min(N, i + 1)]).sub(pts[Math.max(0, i - 1)]).normalize();
            Vector3f up = Math.abs(t.y) < 0.95f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f u = new Vector3f(t).cross(up).normalize(), v = new Vector3f(u).cross(t).normalize();
            float f = (float) i / N;
            float rr = r * (0.7f + 0.6f * f) * (1f + 0.15f * Mth.sin(time * 0.5f + i * 1.1f));
            for (int k = 0; k <= K; k++) {
                float a = (float) (k * Math.PI * 2 / K);
                ring[i][k] = new Vector3f(pts[i]).add(new Vector3f(u).mul(Mth.cos(a) * rr)).add(new Vector3f(v).mul(Mth.sin(a) * rr));
            }
        }
        float scroll = time * 0.12f;
        int c = 0xFFFFFFFF;
        for (int i = 0; i < N; i++) {
            float v0 = (float) i / N * 4f - scroll, v1 = (float) (i + 1) / N * 4f - scroll;
            for (int k = 0; k < K; k++) {
                float u0 = (float) k / K, u1 = (float) (k + 1) / K;
                float nx = Mth.cos((float) ((k + 0.5) * Math.PI * 2 / K)), nz = Mth.sin((float) ((k + 0.5) * Math.PI * 2 / K));
                evert(pose, vc, ring[i][k], c, u0, v0, light, nx, nz);
                evert(pose, vc, ring[i][k + 1], c, u1, v0, light, nx, nz);
                evert(pose, vc, ring[i + 1][k + 1], c, u1, v1, light, nx, nz);
                evert(pose, vc, ring[i + 1][k], c, u0, v1, light, nx, nz);
            }
        }
    }

    /** A widening tube along dir, wobbling and scrolling outward, drawn both sides. */
    private static void tube(PoseStack.Pose pose, VertexConsumer vc, Vector3f base, Vector3f dir, Vector3f u, Vector3f v, float len, float r0, float spread,
                             float time, int rgb, float alpha, int K, int N) {
        if (len < 0.5f || alpha <= 0.003f) return;
        Vector3f[][] ring = new Vector3f[N + 1][K + 1];
        float[] alphaAt = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            float d = len * i / N;
            float r = r0 + d * spread;
            float wob = 1f + 0.14f * Mth.sin(time * 0.9f + i * 1.7f) + 0.08f * Mth.sin(time * 1.7f + i * 0.6f);
            Vector3f c = new Vector3f(dir).mul(d).add(base);
            for (int k = 0; k <= K; k++) {
                float a = (float) (k * Math.PI * 2 / K) + time * 0.03f;
                ring[i][k] = new Vector3f(u).mul(Mth.cos(a) * r * wob).add(new Vector3f(v).mul(Mth.sin(a) * r * wob)).add(c);
            }
            alphaAt[i] = alpha * (i == 0 ? 0.3f : 1f) * (1f - (float) i / N * 0.8f);
        }
        float scroll = -time * 0.1f;
        for (int i = 0; i < N; i++) for (int k = 0; k < K; k++) {
            Vector3f a = ring[i][k], b = ring[i][k + 1], c = ring[i + 1][k + 1], d = ring[i + 1][k];
            float v0 = scroll + (float) i / N * 3f, v1 = scroll + (float) (i + 1) / N * 3f, u0 = (float) k / K, u1 = (float) (k + 1) / K;
            int c0 = argb(rgb, alphaAt[i]), c1 = argb(rgb, alphaAt[i + 1]);
            quad(pose, vc, true, a, b, c, d, c0, c0, c1, c1, u0, u1, v0, v1);
            quad(pose, vc, true, d, c, b, a, c1, c1, c0, c0, u0, u1, v1, v0);
        }
    }

    private static void ring(PoseStack.Pose pose, MultiBufferSource buf, Vector3f c, float r, float width, float height, int rgb, float alpha, float time) {
        if (r < 0.5f) return;
        int K = 72;
        float ri = Math.max(0.1f, r - width);
        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        for (int k = 0; k < K; k++) {
            float a0 = (float) (k * Math.PI * 2 / K), a1 = (float) ((k + 1) * Math.PI * 2 / K);
            Vector3f o0 = new Vector3f(Mth.cos(a0) * r, 0, Mth.sin(a0) * r).add(c), o1 = new Vector3f(Mth.cos(a1) * r, 0, Mth.sin(a1) * r).add(c);
            Vector3f i0 = new Vector3f(Mth.cos(a0) * ri, 0, Mth.sin(a0) * ri).add(c), i1 = new Vector3f(Mth.cos(a1) * ri, 0, Mth.sin(a1) * ri).add(c);
            int ca = argb(rgb, alpha * 0.45f), cb = argb(rgb, 0f);
            quad(pose, glow, false, i0, o0, o1, i1, cb, ca, ca, cb, 0, 0, 0, 0);
            quad(pose, glow, false, i1, o1, o0, i0, cb, ca, ca, cb, 0, 0, 0, 0);
        }
        VertexConsumer wall = buf.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        for (int k = 0; k < K; k++) {
            float a0 = (float) (k * Math.PI * 2 / K), a1 = (float) ((k + 1) * Math.PI * 2 / K);
            Vector3f o0 = new Vector3f(Mth.cos(a0) * r, 0, Mth.sin(a0) * r).add(c), o1 = new Vector3f(Mth.cos(a1) * r, 0, Mth.sin(a1) * r).add(c);
            Vector3f t0 = new Vector3f(o0).add(0, height, 0), t1 = new Vector3f(o1).add(0, height, 0);
            int cw = argb(rgb, alpha * 0.7f), ct = argb(rgb, 0f);
            float u0 = (float) k / K * 8f, u1 = (float) (k + 1) / K * 8f, vs = -time * 0.1f;
            quad(pose, wall, true, o0, o1, t1, t0, cw, cw, ct, ct, u0, u1, vs, vs + 1);
            quad(pose, wall, true, t0, t1, o1, o0, ct, ct, cw, cw, u0, u1, vs + 1, vs);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, boolean textured, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             int ca, int cb, int cc, int cd, float u0, float u1, float v0, float v1) {
        vert(pose, vc, textured, a, ca, u0, v0);
        vert(pose, vc, textured, b, cb, u1, v0);
        vert(pose, vc, textured, c, cc, u1, v1);
        vert(pose, vc, textured, d, cd, u0, v1);
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc, boolean textured, Vector3f p, int argb, float u, float v) {
        if (textured) vc.addVertex(pose, p.x, p.y, p.z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0, 1, 0);
        else vc.addVertex(pose, p.x, p.y, p.z).setColor(argb);
    }
}
