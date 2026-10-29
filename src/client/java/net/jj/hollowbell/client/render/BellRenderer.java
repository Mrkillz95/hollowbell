package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.jj.hollowbell.GiantsPerf;
import net.jj.hollowbell.Detail;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.mixin.FrustumAccess;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws Hollowbell from his baked meshes. The solid and glowing blocks are drawn straight away; the glass is kept
 * back and drawn after every other creature has been drawn (see {@link #drawGlass}), back to front, so whatever he
 * has caught shows through the dome in its stained colours.
 */
public class BellRenderer extends EntityRenderer<HollowbellEntity> {
    private static final Vector3f LIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(),
            LIGHT1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Vector3f NLIGHT1 = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();
    private final BellRig rig = BellRig.get();

    /** the glass waiting to be drawn this frame */
    private static final List<Glass> glass = new ArrayList<>();

    private record Glass(
            Matrix4f entity,
            Matrix4f view,
            Matrix4f proj,
            Matrix4f[] units,
            boolean sliced,
            int lod,
            float lit,
            float hurt,
            boolean red,
            float fogStart,
            float fogEnd,
            Vector3f l0,
            Vector3f l1,
            boolean[] shown) {}

    public BellRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(HollowbellEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** the view this frame, kept from shouldRender so each part of him can be left out when it's off the screen */
    private @Nullable Frustum frustum;

    @Override
    public boolean shouldRender(HollowbellEntity e, Frustum frustum, double x, double y, double z) {
        this.frustum = frustum;
        if (!e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z))) return false;
        double k = shrink(Math.sqrt(e.distanceToSqr(x, y, z)));
        return frustum.isVisible(
                k < 1 ? shrunk(e.getBoundingBoxForCulling(), x, y, z, k) : e.getBoundingBoxForCulling());
    }

    // far off

    /**
     * The game draws nothing past a far limit (four times your render distance). Past most of that he is drawn
     * smaller and nearer by the same amount, which looks exactly the same from where you stand, so he's never cut
     * off before far sight reaches.
     */
    public static double shrink(double dist) {
        double safe = Math.max(64, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8);
        return dist > safe ? safe / dist : 1;
    }

    /** a box pulled toward the camera by k, as the shrinking draws it */
    public static AABB shrunk(AABB b, double cx, double cy, double cz, double k) {
        return new AABB(
                cx + (b.minX - cx) * k,
                cy + (b.minY - cy) * k,
                cz + (b.minZ - cz) * k,
                cx + (b.maxX - cx) * k,
                cy + (b.maxY - cy) * k,
                cz + (b.maxZ - cz) * k);
    }

    /** while a stand-in is drawn: how faded in it is (1 = fully), or -1 for the real one */
    private static float ghostFade = -1;

    /** draws a far-off stand-in (see FarSightClient), faded by fog as it comes and goes */
    public void renderGhost(HollowbellEntity e, PoseStack ps, float partial, float fade, MultiBufferSource buffers) {
        ghostFade = Mth.clamp(fade, 0f, 1f);
        try {
            render(e, e.getYRot(), partial, ps, buffers, LightTexture.FULL_BRIGHT);
        } finally {
            ghostFade = -1;
        }
    }

    private final Map<HollowbellEntity, Matrix4f[][]> poses = new WeakHashMap<>();

    @Override
    public void render(
            HollowbellEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        if (!RenderStats.on) {
            drawHim(e, partial, ps);
            return;
        }
        long t0 = System.nanoTime(), a0 = RenderStats.allocated();
        drawHim(e, partial, ps);
        RenderStats.renderNs += System.nanoTime() - t0;
        RenderStats.allocBytes += RenderStats.allocated() - a0;
    }

    private void drawHim(HollowbellEntity e, float partial, PoseStack ps) {
        if (GiantsPerf.fastDrawing()) {
            if (!poses.isEmpty()) poses.clear();
            drawFast(e, partial, ps);
            return;
        }
        if (!fastKept.isEmpty()) fastKept.clear();
        BellMeshes meshes = BellMeshes.INSTANCE;
        if (!meshes.ensureReady()) return;
        BellPieces pieces = BellPieces.get();
        // (each one's own, kept from frame to frame: every matrix is written again each time, and his glass is drawn
        // after everything else, so two of him can't share them)
        Matrix4f[][] kept =
                poses.computeIfAbsent(e, x -> new Matrix4f[][] {rig.newPose(), pieces.newHang(), pieces.newPose()});
        Matrix4f[] draw = kept[0], hang = kept[1];
        e.fillState(partial);
        rig.computePose(e.state, draw, hang);
        float s = e.bellScale();
        Minecraft mc0 = Minecraft.getInstance();
        var cam0 = mc0.gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(e.distanceToSqr(cam0.x, cam0.y, cam0.z));
        float k = (float) shrink(dist);
        // shrunk toward the camera past the far limit; the pose stack is relative to the camera, so this is a plain
        // scale
        Matrix4f entity = new Matrix4f()
                .scaling(k)
                .mul(ps.last().pose())
                .rotateY(-e.getYRot() * Mth.DEG_TO_RAD)
                .scale(s);
        boolean ghost = ghostFade >= 0;
        Frustum fr = ghost || k < 1 ? null : frustum;
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Minecraft mc = Minecraft.getInstance();
        boolean nether = mc.level != null && mc.level.effects().constantAmbientLight();
        Vector3f l0w = LIGHT0, l1w = nether ? NLIGHT1 : LIGHT1;

        // how lit he is: brightest of a few points on him that are in loaded world, or the sky's own light
        boolean known = false;
        int bl = 0, sl = 0;
        for (Vec3 p : new Vec3[] {
            e.position().add(0, 2, 0),
            e.position().add(0, (rig.rimY + 30) * s, 0),
            e.position().add(0, rig.crownY * s + 2, 0)
        }) {
            BlockPos bp = BlockPos.containing(p);
            if (!e.level().hasChunkAt(bp)) continue;
            known = true;
            int l = LevelRenderer.getLightColor(e.level(), bp);
            bl = Math.max(bl, LightTexture.block(l));
            sl = Math.max(sl, LightTexture.sky(l));
        }
        if (!known) sl = Math.max(0, 15 - e.level().getSkyDarken());
        float lit = 0.3f + 0.7f * Math.max(bl, sl) / 15f;
        float hurt = e.hurtTime > 0 ? 0.3f : 0f;
        float dying = e.isDeadOrDying() ? Mth.clamp((e.deathTime + partial) / 200f, 0f, 1f) : 0f;
        lit *= 1f - 0.35f * dying;

        // far away, fewer, bigger blocks (a far-off stand-in always the simple one)
        int far = Detail.lod(dist, s);
        if (ghost) far = Math.max(far, Detail.FAR);
        int lod = switch (far) {
            case Detail.TINY -> BellMeshes.TINY;
            case Detail.FAR -> BellMeshes.FAR;
            default -> BellMeshes.FULL;
        };
        // up close his arms and strands are drawn in slices laid along a smooth curve (see BellPieces), so they bend
        // with no gaps; very far off a piece at a time
        boolean sliced = lod != BellMeshes.TINY;
        Matrix4f[] units = draw;
        if (sliced) {
            units = kept[2];
            pieces.pose(e.state, draw, hang, units);
        }
        int unitCount = sliced ? pieces.count : rig.boneCount();

        RenderType rt = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            rt.clearRenderState();
            return;
        }
        float wantFog = Math.min((float) Detail.renderDistance() * 1.2f, 420f * Math.max(0.2f, s) + 220f);
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        // far sight: he stays in the fog as a pale shape out to farSightBlocks and a bit past it, never gone before
        float farSight = Detail.farSightBlocks();
        float fe = Math.max(fogEnd0, Math.max(wantFog, farSight * 1.25f));
        float fs = Math.max(fogStart0, Math.min(wantFog * 0.85f, fe * 0.45f));
        // drawn shrunk, the fog is measured the same shrunk way; a stand-in coming or going fades into the fog
        fs *= k;
        fe *= k;
        if (ghost) {
            float dk = (float) dist * k;
            fs = Mth.lerp(ghostFade, dk * 0.5f, fs);
            fe = Mth.lerp(ghostFade, dk * 0.6f, fe);
        }
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, proj, mc.getWindow());
        shader.apply();
        boolean[] shown = new boolean[rig.boneCount()];
        // each part of him that's off the screen isn't drawn at all
        Matrix4f abs = e.modelToWorld(partial);
        Matrix4f boneAbs = new Matrix4f();
        Vector3f cc = new Vector3f();
        BellModel model = BellModel.get();
        for (int b = 0; b < shown.length; b++) {
            shown[b] = rig.shown(e.state, b);
            float[] bb = model.bounds[b];
            if (!shown[b] || bb == null || fr == null) continue;
            boneAbs.set(abs).mul(draw[b]);
            boneAbs.transformPosition(cc.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f));
            // (a slice of an arm or strand may lie a few blocks off its bone, where the curve takes a corner off)
            float r = (0.5f
                                            * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0])
                                                    + (bb[4] - bb[1]) * (bb[4] - bb[1])
                                                    + (bb[5] - bb[2]) * (bb[5] - bb[2]))
                                            * 1.35f
                                    + 8f)
                            * s
                    + 1f;
            if (!fr.isVisible(new AABB(cc.x - r, cc.y - r, cc.z - r, cc.x + r, cc.y + r, cc.z + r))) shown[b] = false;
        }
        Matrix4f mv = new Matrix4f(), boneWorld = new Matrix4f();
        Matrix3f rot = new Matrix3f();
        Vector3f l0 = new Vector3f(), l1 = new Vector3f();
        for (int pass = 0; pass < 2; pass++) {
            int kind = pass == 0 ? BellMeshes.SOLID : BellMeshes.GLOW;
            int lastBone = -1;
            for (int u = 0; u < unitCount; u++) {
                int b = sliced ? pieces.bone[u] : u;
                if (!shown[b]) continue;
                BellMeshes.Mesh m = meshes.mesh(lod, kind, u);
                if (m == null) continue;
                boneWorld.set(entity).mul(units[u]);
                mv.set(view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                // the light and the colour once a bone: its slices lie close enough to the same way
                if (b != lastBone) {
                    lastBone = b;
                    if (kind == BellMeshes.GLOW) glowLight(l0, l1);
                    else {
                        boneWorld.get3x3(rot).normal().transpose();
                        rot.transform(l0.set(l0w)).normalize();
                        rot.transform(l1.set(l1w)).normalize();
                    }
                    if (shader.LIGHT0_DIRECTION != null) {
                        shader.LIGHT0_DIRECTION.set(l0);
                        shader.LIGHT0_DIRECTION.upload();
                    }
                    if (shader.LIGHT1_DIRECTION != null) {
                        shader.LIGHT1_DIRECTION.set(l1);
                        shader.LIGHT1_DIRECTION.upload();
                    }
                    float[] c = tint(e, b, kind == BellMeshes.GLOW ? glow(e, b, lit, partial) : lit, hurt, dying);
                    if (shader.COLOR_MODULATOR != null) {
                        shader.COLOR_MODULATOR.set(c[0], c[1], c[2], 1f);
                        shader.COLOR_MODULATOR.upload();
                    }
                }
                m.vb.bind();
                m.vb.draw();
                if (RenderStats.on) RenderStats.draws++;
            }
        }
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (nether) Lighting.setupNetherLevel();
        else Lighting.setupLevel();
        rt.clearRenderState();
        glass.add(new Glass(
                entity,
                view,
                proj,
                units,
                sliced,
                lod,
                lit * (1f - 0.2f * dying),
                hurt,
                e.state.red,
                fs,
                fe,
                l0w,
                l1w,
                shown));
    }

    /**
     * The lights for a glowing block: one shining on it from each side, so every face of it is at full brightness
     * (lit like the rest of him, from above, the faces under a light were drawn dim, nearly black from below)
     */
    /** how bright a glowing part is: his glow, toned back toward the plain light by the Giants Guide's flashes level */
    private float glow(HollowbellEntity e, int b, float lit, float partial) {
        float g = glowLevel(e, b, lit, partial), k = GiantsPerf.f("fx.flashes");
        return k >= 1f ? g : lit + (g - lit) * k;
    }

    static void glowLight(Vector3f l0, Vector3f l1) {
        l0.set(5f, 5f, 5f);
        l1.set(-5f, -5f, -5f);
    }

    private static void upload(Uniform u, Matrix4f m) {
        if (u != null) {
            u.set(m);
            u.upload();
        }
    }

    /**
     * How bright a glowing block of him is: full, with a slow pulse (each part in its own time), pushed brighter
     * when a move flares his glow, and going out as he dies.
     */
    private float glowLevel(HollowbellEntity e, int b, float lit, float partial) {
        float t = e.state.time;
        float sl = e.state.sleep;
        // asleep: the glow sinks to a dim, slow breathing
        float pulse =
                0.88f + 0.12f * Mth.sin(t * Mth.lerp(sl, 0.045f, 0.02f) + rig.part[b] * 1.9f + rig.kind[b].ordinal());
        // each squeeze of the bell brightens him a little, each refill dims him
        float beat = Mth.clamp(e.state.squeeze, -0.4f, 1.6f);
        float k = (pulse + 0.18f * beat + 0.6f * e.state.glow) * (1f - 0.62f * sl);
        return Mth.lerp(e.state.death, k, lit * 0.5f);
    }

    /** a popped pod goes dark and stays dark; everything flashes red when he is hurt */
    private float[] tint(HollowbellEntity e, int b, float k, float hurt, float dying) {
        return tint(e, b, k, hurt, dying, new float[3]);
    }

    private float[] tint(HollowbellEntity e, int b, float k, float hurt, float dying, float[] out) {
        float r = 1f, g = 1f - hurt, bb = 1f - hurt;
        if (rig.kind[b] == BellRig.Kind.POD && e.isPodPopped(rig.part[b])) {
            r = 0.22f;
            g = 0.2f;
            bb = 0.26f;
        }
        if (dying > 0f && (rig.kind[b] == BellRig.Kind.SPOT || rig.kind[b] == BellRig.Kind.CROWN)) {
            r *= 1f - 0.6f * dying;
            g *= 1f - 0.6f * dying;
            bb *= 1f - 0.5f * dying;
        }
        out[0] = r * k;
        out[1] = g * k;
        out[2] = bb * k;
        return out;
    }

    /**
     * The see-through pass: every Hollowbell drawn this frame, glass sorted back to front (by bone, and inside each
     * bone by face, re-sorted off the render thread as you move). Called after the other creatures are drawn.
     */
    public static void drawGlass() {
        if (!RenderStats.on) {
            drawAllGlass();
            drawAllGlassFast();
            return;
        }
        long t0 = System.nanoTime(), a0 = RenderStats.allocated();
        drawAllGlass();
        drawAllGlassFast();
        RenderStats.glassNs += System.nanoTime() - t0;
        RenderStats.allocBytes += RenderStats.allocated() - a0;
        RenderStats.frames++;
    }

    private static void drawAllGlass() {
        if (glass.isEmpty()) return;
        BellMeshes meshes = BellMeshes.INSTANCE;
        BellRig rig = BellRig.get();
        BellModel model = BellModel.get();
        RenderType rt = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            rt.clearRenderState();
            glass.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        Matrix4f mv = new Matrix4f(), boneWorld = new Matrix4f(), inv = new Matrix4f();
        Matrix3f rot = new Matrix3f();
        Vector3f l0 = new Vector3f(), l1 = new Vector3f(), cam = new Vector3f(), c = new Vector3f();
        for (Glass gl : glass) {
            RenderSystem.setShaderFogStart(gl.fogStart);
            RenderSystem.setShaderFogEnd(gl.fogEnd);
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, gl.view, gl.proj, mc.getWindow());
            shader.apply();
            // which bones have glass, furthest first
            List<float[]> order = new ArrayList<>();
            BellPieces pieces = BellPieces.get();
            int unitCount = gl.sliced ? pieces.count : rig.boneCount();
            for (int u = 0; u < unitCount; u++) {
                int b = gl.sliced ? pieces.bone[u] : u;
                if (!gl.shown[b]) continue;
                boolean redLoops = gl.red && rig.kind[b] == BellRig.Kind.ARM;
                BellMeshes.Mesh m =
                        meshes.mesh(gl.lod, redLoops && gl.sliced ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, u);
                if (m == null) continue;
                float[] bb = gl.sliced ? pieces.bounds[u] : model.bounds[b];
                if (bb == null) continue;
                boneWorld.set(gl.entity).mul(gl.units[u]);
                boneWorld.transformPosition(c.set((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2));
                order.add(new float[] {c.lengthSquared(), u, redLoops && gl.sliced ? 1 : 0});
            }
            order.sort((a, b2) -> Float.compare(b2[0], a[0]));
            float unit = Math.max(1e-3f, gl.entity.getScale(new Vector3f()).x);
            for (float[] o : order) {
                int u = (int) o[1];
                BellMeshes.Mesh m = meshes.mesh(gl.lod, o[2] > 0 ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, u);
                boneWorld.set(gl.entity).mul(gl.units[u]);
                // the camera sits at 0 0 0 here: where is that in the bone's own blocks
                boneWorld.invert(inv);
                inv.transformPosition(cam.set(0, 0, 0));
                // (sorted again once the camera has moved 3 world blocks, more the further off it is: counted in his
                // own blocks, a small one sorted every glass mesh many times a second)
                meshes.keepSorted(m, cam, Math.max(3f, 0.03f * (float) Math.sqrt(o[0])) / unit);
                mv.set(gl.view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                boneWorld.get3x3(rot).normal().transpose();
                rot.transform(l0.set(gl.l0)).normalize();
                rot.transform(l1.set(gl.l1)).normalize();
                if (shader.LIGHT0_DIRECTION != null) {
                    shader.LIGHT0_DIRECTION.set(l0);
                    shader.LIGHT0_DIRECTION.upload();
                }
                if (shader.LIGHT1_DIRECTION != null) {
                    shader.LIGHT1_DIRECTION.set(l1);
                    shader.LIGHT1_DIRECTION.upload();
                }
                float k = gl.lit;
                float a = 1f;
                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(k, k * (1f - gl.hurt), k * (1f - gl.hurt), a);
                    shader.COLOR_MODULATOR.upload();
                }
                m.vb.bind();
                m.vb.draw();
                if (RenderStats.on) RenderStats.glassDraws++;
            }
        }
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        rt.clearRenderState();
        glass.clear();
    }

    /** a frame that never reached the glass pass (a screenshot, a paused frame) must not leave it for the next */
    public static void forgetFrame() {
        glass.clear();
        fastGlass.clear();
        BellMeshes.INSTANCE.frameStart();
    }

    // Fast drawing (the guide's Drawing: Fast): the same picture as drawHim and drawAllGlass, made with no new objects
    // each frame, and each one of him with his own back-to-front order for his glass. Every number is worked out just
    // as above, in the same order, so the picture is the same.

    /** each one's own matrices and what his glass pass needs, kept from frame to frame */
    private static final class Kept {
        final Matrix4f[] draw, hang, units;
        final boolean[] shown;
        /** each slice: on the screen (the solid and glowing blocks of one that isn't are not drawn) */
        final boolean[] unitOn;
        final Matrix4f entity = new Matrix4f(), view = new Matrix4f(), proj = new Matrix4f();
        final Vector3f l0w = new Vector3f(), l1w = new Vector3f();
        int who, lod;
        boolean sliced, red;
        float lit, hurt, fogStart, fogEnd;

        Kept(BellRig rig, BellPieces pieces) {
            draw = rig.newPose();
            hang = pieces.newHang();
            units = pieces.newPose();
            shown = new boolean[rig.boneCount()];
            unitOn = new boolean[Math.max(rig.boneCount(), pieces.count)];
        }
    }

    private final Map<HollowbellEntity, Kept> fastKept = new WeakHashMap<>();
    /** the glass waiting to be drawn this frame, Fast */
    private static final List<Kept> fastGlass = new ArrayList<>();

    // scratch for the render thread
    private final Matrix4f abs = new Matrix4f(), boneAbs = new Matrix4f(), mvS = new Matrix4f(), boneWorldS = new Matrix4f();
    private final Matrix3f rotS = new Matrix3f();
    private final Matrix4f clipM = new Matrix4f();
    private final FrustumIntersection clipS = new FrustumIntersection();
    private final Vector3f ccS = new Vector3f(), l0S = new Vector3f(), l1S = new Vector3f(), scaleS = new Vector3f();
    private final float[] tintS = new float[3];
    private final BlockPos.MutableBlockPos lightAt = new BlockPos.MutableBlockPos();

    private void drawFast(HollowbellEntity e, float partial, PoseStack ps) {
        BellMeshes meshes = BellMeshes.INSTANCE;
        if (!meshes.ensureReady()) return;
        BellPieces pieces = BellPieces.get();
        Kept kp = fastKept.get(e);
        if (kp == null) fastKept.put(e, kp = new Kept(rig, pieces));
        Matrix4f[] draw = kp.draw, hang = kp.hang;
        e.fillState(partial);
        rig.computePose(e.state, draw, hang);
        float s = e.bellScale();
        Minecraft mc0 = Minecraft.getInstance();
        var cam0 = mc0.gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(e.distanceToSqr(cam0.x, cam0.y, cam0.z));
        float k = (float) shrink(dist);
        Matrix4f entity = kp.entity
                .scaling(k)
                .mul(ps.last().pose())
                .rotateY(-e.getYRot() * Mth.DEG_TO_RAD)
                .scale(s);
        boolean ghost = ghostFade >= 0;
        Frustum fr = ghost || k < 1 ? null : frustum;
        Matrix4f view = kp.view.set(RenderSystem.getModelViewMatrix());
        Matrix4f proj = kp.proj.set(RenderSystem.getProjectionMatrix());
        Minecraft mc = mc0;
        boolean nether = mc.level != null && mc.level.effects().constantAmbientLight();
        Vector3f l0w = LIGHT0, l1w = nether ? NLIGHT1 : LIGHT1;

        boolean known = false;
        int bl = 0, sl = 0;
        double ex = e.getX(), ey = e.getY(), ez = e.getZ();
        for (int i = 0; i < 3; i++) {
            double up = i == 0 ? 2 : i == 1 ? (rig.rimY + 30) * s : rig.crownY * s + 2;
            lightAt.set(Mth.floor(ex), Mth.floor(ey + up), Mth.floor(ez));
            if (!e.level().hasChunkAt(lightAt)) continue;
            known = true;
            int l = LevelRenderer.getLightColor(e.level(), lightAt);
            bl = Math.max(bl, LightTexture.block(l));
            sl = Math.max(sl, LightTexture.sky(l));
        }
        if (!known) sl = Math.max(0, 15 - e.level().getSkyDarken());
        float lit = 0.3f + 0.7f * Math.max(bl, sl) / 15f;
        float hurt = e.hurtTime > 0 ? 0.3f : 0f;
        float dying = e.isDeadOrDying() ? Mth.clamp((e.deathTime + partial) / 200f, 0f, 1f) : 0f;
        lit *= 1f - 0.35f * dying;

        int far = Detail.lod(dist, s);
        if (ghost) far = Math.max(far, Detail.FAR);
        int lod = switch (far) {
            case Detail.TINY -> BellMeshes.TINY;
            case Detail.FAR -> BellMeshes.FAR;
            default -> BellMeshes.FULL;
        };
        boolean sliced = lod != BellMeshes.TINY;
        Matrix4f[] units = draw;
        if (sliced) {
            units = kp.units;
            pieces.pose(e.state, draw, hang, units);
        }
        int unitCount = sliced ? pieces.count : rig.boneCount();

        RenderType rt = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            rt.clearRenderState();
            return;
        }
        float wantFog = Math.min((float) Detail.renderDistance() * 1.2f, 420f * Math.max(0.2f, s) + 220f);
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        float farSight = Detail.farSightBlocks();
        float fe = Math.max(fogEnd0, Math.max(wantFog, farSight * 1.25f));
        float fs = Math.max(fogStart0, Math.min(wantFog * 0.85f, fe * 0.45f));
        fs *= k;
        fe *= k;
        if (ghost) {
            float dk = (float) dist * k;
            fs = Mth.lerp(ghostFade, dk * 0.5f, fs);
            fe = Mth.lerp(ghostFade, dk * 0.6f, fe);
        }
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, proj, mc.getWindow());
        shader.apply();
        boolean[] shown = kp.shown;
        double px = Mth.lerp(partial, e.xo, e.getX()),
                py = Mth.lerp(partial, e.yo, e.getY()),
                pz = Mth.lerp(partial, e.zo, e.getZ());
        Matrix4f abs = this.abs.identity()
                .translate((float) px, (float) py, (float) pz)
                .rotateY(-e.getYRot() * Mth.DEG_TO_RAD)
                .scale(s);
        Matrix4f boneAbs = this.boneAbs;
        Vector3f cc = ccS;
        BellModel model = BellModel.get();
        FrustumAccess plain = fr != null && fr.getClass() == Frustum.class ? (FrustumAccess) fr : null;
        for (int b = 0; b < shown.length; b++) {
            shown[b] = rig.shown(e.state, b);
            float[] bb = model.bounds[b];
            if (!shown[b] || bb == null || fr == null) continue;
            boneAbs.set(abs).mul(draw[b]);
            boneAbs.transformPosition(cc.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f));
            float r = (0.5f
                                            * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0])
                                                    + (bb[4] - bb[1]) * (bb[4] - bb[1])
                                                    + (bb[5] - bb[2]) * (bb[5] - bb[2]))
                                            * 1.35f
                                    + 8f)
                            * s
                    + 1f;
            boolean vis = plain != null
                    ? plain.hollowbell$cubeInFrustum(cc.x - r, cc.y - r, cc.z - r, cc.x + r, cc.y + r, cc.z + r)
                    : fr.isVisible(new AABB(cc.x - r, cc.y - r, cc.z - r, cc.x + r, cc.y + r, cc.z + r));
            if (!vis) shown[b] = false;
        }
        // and each slice on the screen: one wholly off it draws nothing, so it isn't sent. Tested against the very
        // matrices it is drawn with (so the shake and bob of the view count), with a ball round its box and a block to
        // spare; only the solid and glowing blocks (the glass keeps its order just as Classic keeps it)
        boolean[] unitOn = kp.unitOn;
        FrustumIntersection clip = clipS.set(clipM.set(proj).mul(view).mul(entity), true);
        for (int u = 0; u < unitCount; u++) {
            int b = sliced ? pieces.bone[u] : u;
            unitOn[u] = shown[b];
            float[] bb = sliced ? pieces.bounds[u] : model.bounds[b];
            if (!shown[b] || bb == null) continue;
            units[u].transformPosition(cc.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f));
            units[u].getScale(scaleS);
            float r = (0.5f
                                    * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0])
                                            + (bb[4] - bb[1]) * (bb[4] - bb[1])
                                            + (bb[5] - bb[2]) * (bb[5] - bb[2]))
                            + 1f)
                    * Math.max(scaleS.x, Math.max(scaleS.y, scaleS.z));
            if (!clip.testSphere(cc.x, cc.y, cc.z, r)) unitOn[u] = false;
        }
        Matrix4f mv = mvS, boneWorld = boneWorldS;
        Matrix3f rot = rotS;
        Vector3f l0 = l0S, l1 = l1S;
        for (int pass = 0; pass < 2; pass++) {
            int kind = pass == 0 ? BellMeshes.SOLID : BellMeshes.GLOW;
            int lastBone = -1;
            for (int u = 0; u < unitCount; u++) {
                int b = sliced ? pieces.bone[u] : u;
                if (!shown[b]) continue;
                BellMeshes.Mesh m = meshes.mesh(lod, kind, u);
                if (m == null) continue;
                // (a slice off the screen isn't drawn, but the light and colour of its bone are still worked out from
                // the bone's first slice, as Classic does)
                boolean on = unitOn[u];
                if (!on && b == lastBone) continue;
                boneWorld.set(entity).mul(units[u]);
                if (b != lastBone) {
                    lastBone = b;
                    if (kind == BellMeshes.GLOW) glowLight(l0, l1);
                    else {
                        boneWorld.get3x3(rot).normal().transpose();
                        rot.transform(l0.set(l0w)).normalize();
                        rot.transform(l1.set(l1w)).normalize();
                    }
                    if (shader.LIGHT0_DIRECTION != null) {
                        shader.LIGHT0_DIRECTION.set(l0);
                        shader.LIGHT0_DIRECTION.upload();
                    }
                    if (shader.LIGHT1_DIRECTION != null) {
                        shader.LIGHT1_DIRECTION.set(l1);
                        shader.LIGHT1_DIRECTION.upload();
                    }
                    float[] c = tint(
                            e, b, kind == BellMeshes.GLOW ? glow(e, b, lit, partial) : lit, hurt, dying, tintS);
                    if (shader.COLOR_MODULATOR != null) {
                        shader.COLOR_MODULATOR.set(c[0], c[1], c[2], 1f);
                        shader.COLOR_MODULATOR.upload();
                    }
                }
                if (!on) continue;
                mv.set(view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                m.vb.bind();
                m.vb.draw();
                if (RenderStats.on) RenderStats.draws++;
            }
        }
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (nether) Lighting.setupNetherLevel();
        else Lighting.setupLevel();
        rt.clearRenderState();
        kp.who = e.getId();
        kp.sliced = sliced;
        kp.lod = lod;
        kp.lit = lit * (1f - 0.2f * dying);
        kp.hurt = hurt;
        kp.red = e.state.red;
        kp.fogStart = fs;
        kp.fogEnd = fe;
        kp.l0w.set(l0w);
        kp.l1w.set(l1w);
        fastGlass.add(kp);
    }

    // the glass pass's scratch: the units with glass, how far each is, sorted furthest first
    private static float[] gKey = new float[1024];
    private static int[] gUnit = new int[1024], gIdx = new int[1024];
    private static boolean[] gRed = new boolean[1024];
    private static final Matrix4f gMv = new Matrix4f(), gBoneWorld = new Matrix4f(), gInv = new Matrix4f();
    private static final Matrix3f gRot = new Matrix3f();
    private static final Vector3f gL0 = new Vector3f(), gL1 = new Vector3f(), gCam = new Vector3f(), gC = new Vector3f(),
            gScale = new Vector3f();

    private static void drawAllGlassFast() {
        if (fastGlass.isEmpty()) return;
        BellMeshes meshes = BellMeshes.INSTANCE;
        BellRig rig = BellRig.get();
        BellModel model = BellModel.get();
        RenderType rt = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            rt.clearRenderState();
            fastGlass.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        Matrix4f mv = gMv, boneWorld = gBoneWorld, inv = gInv;
        Matrix3f rot = gRot;
        Vector3f l0 = gL0, l1 = gL1, cam = gCam, c = gC;
        BellPieces pieces = BellPieces.get();
        for (Kept gl : fastGlass) {
            RenderSystem.setShaderFogStart(gl.fogStart);
            RenderSystem.setShaderFogEnd(gl.fogEnd);
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, gl.view, gl.proj, mc.getWindow());
            shader.apply();
            Matrix4f[] units = gl.sliced ? gl.units : gl.draw;
            int unitCount = gl.sliced ? pieces.count : rig.boneCount();
            if (gKey.length < unitCount) {
                gKey = new float[unitCount];
                gUnit = new int[unitCount];
                gIdx = new int[unitCount];
                gRed = new boolean[unitCount];
            }
            int n = 0;
            for (int u = 0; u < unitCount; u++) {
                int b = gl.sliced ? pieces.bone[u] : u;
                if (!gl.shown[b]) continue;
                boolean redLoops = gl.red && rig.kind[b] == BellRig.Kind.ARM;
                BellMeshes.Mesh m =
                        meshes.mesh(gl.lod, redLoops && gl.sliced ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, u);
                if (m == null) continue;
                float[] bb = gl.sliced ? pieces.bounds[u] : model.bounds[b];
                if (bb == null) continue;
                boneWorld.set(gl.entity).mul(units[u]);
                boneWorld.transformPosition(c.set((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2));
                gKey[n] = c.lengthSquared();
                gUnit[n] = u;
                gRed[n] = redLoops && gl.sliced;
                gIdx[n] = n;
                n++;
            }
            // furthest first; equal ones keep their order (a stable sort, as the list sort was)
            final float[] key = gKey;
            IntArrays.mergeSort(gIdx, 0, n, (a, b2) -> Float.compare(key[b2], key[a]));
            float unit = Math.max(1e-3f, gl.entity.getScale(gScale).x);
            for (int i = 0; i < n; i++) {
                int o = gIdx[i];
                int u = gUnit[o];
                BellMeshes.Mesh m = meshes.mesh(gl.lod, gRed[o] ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, u);
                boneWorld.set(gl.entity).mul(units[u]);
                boneWorld.invert(inv);
                inv.transformPosition(cam.set(0, 0, 0));
                mv.set(gl.view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                boneWorld.get3x3(rot).normal().transpose();
                rot.transform(l0.set(gl.l0w)).normalize();
                rot.transform(l1.set(gl.l1w)).normalize();
                if (shader.LIGHT0_DIRECTION != null) {
                    shader.LIGHT0_DIRECTION.set(l0);
                    shader.LIGHT0_DIRECTION.upload();
                }
                if (shader.LIGHT1_DIRECTION != null) {
                    shader.LIGHT1_DIRECTION.set(l1);
                    shader.LIGHT1_DIRECTION.upload();
                }
                float k = gl.lit;
                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(k, k * (1f - gl.hurt), k * (1f - gl.hurt), 1f);
                    shader.COLOR_MODULATOR.upload();
                }
                meshes.drawSorted(m, gl.who, cam, Math.max(3f, 0.03f * (float) Math.sqrt(gKey[o])) / unit);
                if (RenderStats.on) RenderStats.glassDraws++;
            }
        }
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        rt.clearRenderState();
        fastGlass.clear();
    }
}
