package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws Hollowbell from his baked meshes. The solid and glowing blocks are drawn straight away; the glass is kept
 * back and drawn after every other creature has been drawn (see {@link #drawGlass}), back to front, so whatever he
 * has caught shows through the dome in its stained colours.
 */
public class BellRenderer extends EntityRenderer<HollowbellEntity> {
    private static final Vector3f LIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(), LIGHT1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Vector3f NLIGHT1 = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();
    private final BellRig rig = BellRig.get();

    /** the glass waiting to be drawn this frame */
    private static final List<Glass> glass = new ArrayList<>();
    private record Glass(Matrix4f entity, Matrix4f view, Matrix4f proj, Matrix4f[] bones, int lod, float lit, float hurt, boolean red,
                         float fogStart, float fogEnd, Vector3f l0, Vector3f l1, boolean[] shown) {}

    public BellRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override public ResourceLocation getTextureLocation(HollowbellEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    /** the view this frame, kept from shouldRender so each part of him can be left out when it's off the screen */
    private @org.jetbrains.annotations.Nullable Frustum frustum;

    @Override
    public boolean shouldRender(HollowbellEntity e, Frustum frustum, double x, double y, double z) {
        this.frustum = frustum;
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(HollowbellEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        BellMeshes meshes = BellMeshes.INSTANCE;
        if (!meshes.ensureReady()) return;
        Matrix4f[] draw = rig.newPose();
        e.fillState(partial);
        rig.computePose(e.state, draw);
        float s = e.bellScale();
        Matrix4f entity = new Matrix4f(ps.last().pose()).rotateY(-e.getYRot() * Mth.DEG_TO_RAD).scale(s);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Minecraft mc = Minecraft.getInstance();
        boolean nether = mc.level != null && mc.level.effects().constantAmbientLight();
        Vector3f l0w = LIGHT0, l1w = nether ? NLIGHT1 : LIGHT1;

        // how lit he is: brightest of a few points on him that are in loaded world, or the sky's own light
        boolean known = false;
        int bl = 0, sl = 0;
        for (Vec3 p : new Vec3[]{e.position().add(0, 2, 0), e.position().add(0, (rig.rimY + 30) * s, 0), e.position().add(0, rig.crownY * s + 2, 0)}) {
            BlockPos bp = BlockPos.containing(p);
            if (!e.level().hasChunkAt(bp)) continue;
            known = true;
            int l = LevelRenderer.getLightColor(e.level(), bp);
            bl = Math.max(bl, LightTexture.block(l)); sl = Math.max(sl, LightTexture.sky(l));
        }
        if (!known) sl = Math.max(0, 15 - e.level().getSkyDarken());
        float lit = 0.3f + 0.7f * Math.max(bl, sl) / 15f;
        float hurt = e.hurtTime > 0 ? 0.3f : 0f;
        float dying = e.isDeadOrDying() ? Mth.clamp((e.deathTime + partial) / 200f, 0f, 1f) : 0f;
        lit *= 1f - 0.35f * dying;

        // far away, fewer, bigger blocks
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(e.distanceToSqr(cam.x, cam.y, cam.z));
        int lod = BellMeshes.FULL;
        if (HollowbellConfig.V.simpleFarAway && dist > HollowbellConfig.V.simpleFarAwayAt * Math.max(0.25f, s)) lod = BellMeshes.FAR;
        if (s < 0.06f && dist > 40 || dist > HollowbellConfig.V.simpleFarAwayAt * 4 * Math.max(0.25f, s)) lod = BellMeshes.TINY;

        RenderType rt = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) { rt.clearRenderState(); return; }
        float wantFog = Math.min(HollowbellConfig.V.renderDistance * 1.2f, 420f * Math.max(0.2f, s) + 220f);
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        float fs = Math.max(fogStart0, wantFog * 0.85f), fe = Math.max(fogEnd0, wantFog);
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
            if (!shown[b] || bb == null || frustum == null) continue;
            boneAbs.set(abs).mul(draw[b]);
            boneAbs.transformPosition(cc.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f));
            float r = 0.5f * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0]) + (bb[4] - bb[1]) * (bb[4] - bb[1]) + (bb[5] - bb[2]) * (bb[5] - bb[2])) * s * 1.35f + 1f;
            if (!frustum.isVisible(new net.minecraft.world.phys.AABB(cc.x - r, cc.y - r, cc.z - r, cc.x + r, cc.y + r, cc.z + r))) shown[b] = false;
        }
        Matrix4f mv = new Matrix4f(), boneWorld = new Matrix4f();
        Matrix3f rot = new Matrix3f();
        Vector3f l0 = new Vector3f(), l1 = new Vector3f();
        for (int pass = 0; pass < 2; pass++) {
            int kind = pass == 0 ? BellMeshes.SOLID : BellMeshes.GLOW;
            for (int b = 0; b < rig.boneCount(); b++) {
                if (!shown[b]) continue;
                BellMeshes.Mesh m = meshes.mesh(lod, kind, b);
                if (m == null) continue;
                boneWorld.set(entity).mul(draw[b]);
                mv.set(view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                boneWorld.get3x3(rot).normal().transpose();
                rot.transform(l0.set(l0w)).normalize();
                rot.transform(l1.set(l1w)).normalize();
                if (shader.LIGHT0_DIRECTION != null) { shader.LIGHT0_DIRECTION.set(l0); shader.LIGHT0_DIRECTION.upload(); }
                if (shader.LIGHT1_DIRECTION != null) { shader.LIGHT1_DIRECTION.set(l1); shader.LIGHT1_DIRECTION.upload(); }
                float[] c = tint(e, b, kind == BellMeshes.GLOW ? glowLevel(e, b, lit, partial) : lit, hurt, dying);
                if (shader.COLOR_MODULATOR != null) { shader.COLOR_MODULATOR.set(c[0], c[1], c[2], 1f); shader.COLOR_MODULATOR.upload(); }
                m.vb.bind();
                m.vb.draw();
            }
        }
        shader.clear();
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (nether) Lighting.setupNetherLevel(); else Lighting.setupLevel();
        rt.clearRenderState();
        glass.add(new Glass(entity, view, proj, draw, lod, lit * (1f - 0.2f * dying), hurt, e.state.red, fs, fe, l0w, l1w, shown));
    }

    private static void upload(Uniform u, Matrix4f m) { if (u != null) { u.set(m); u.upload(); } }

    /**
     * How bright a glowing block of him is: full, with a slow pulse (each part in its own time), pushed brighter
     * when a move flares his glow, and going out as he dies.
     */
    private float glowLevel(HollowbellEntity e, int b, float lit, float partial) {
        float t = e.state.time;
        float pulse = 0.88f + 0.12f * Mth.sin(t * 0.045f + rig.part[b] * 1.9f + rig.kind[b].ordinal());
        float k = pulse + 0.6f * e.state.glow;
        return Mth.lerp(e.state.death, k, lit * 0.5f);
    }

    /** a popped pod goes dark and stays dark; everything flashes red when he is hurt */
    private float[] tint(HollowbellEntity e, int b, float k, float hurt, float dying) {
        float r = 1f, g = 1f - hurt, bb = 1f - hurt;
        if (rig.kind[b] == BellRig.Kind.POD && e.isPodPopped(rig.part[b])) { r = 0.22f; g = 0.2f; bb = 0.26f; }
        if (dying > 0f && (rig.kind[b] == BellRig.Kind.SPOT || rig.kind[b] == BellRig.Kind.CROWN)) { r *= 1f - 0.6f * dying; g *= 1f - 0.6f * dying; bb *= 1f - 0.5f * dying; }
        return new float[]{r * k, g * k, bb * k};
    }

    /**
     * The see-through pass: every Hollowbell drawn this frame, glass sorted back to front (by bone, and inside each
     * bone by face, re-sorted off the render thread as you move). Called after the other creatures are drawn.
     */
    public static void drawGlass() {
        if (glass.isEmpty()) return;
        BellMeshes meshes = BellMeshes.INSTANCE;
        BellRig rig = BellRig.get();
        BellModel model = BellModel.get();
        RenderType rt = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) { rt.clearRenderState(); glass.clear(); return; }
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
            for (int b = 0; b < rig.boneCount(); b++) {
                if (!gl.shown[b]) continue;
                boolean redLoops = gl.red && rig.kind[b] == BellRig.Kind.ARM;
                BellMeshes.Mesh m = meshes.mesh(gl.lod, redLoops && gl.lod == BellMeshes.FULL ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, b);
                if (m == null) continue;
                float[] bb = model.bounds[b];
                if (bb == null) continue;
                boneWorld.set(gl.entity).mul(gl.bones[b]);
                boneWorld.transformPosition(c.set((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2));
                order.add(new float[]{c.lengthSquared(), b, redLoops && gl.lod == BellMeshes.FULL ? 1 : 0});
            }
            order.sort((a, b2) -> Float.compare(b2[0], a[0]));
            for (float[] o : order) {
                int b = (int) o[1];
                BellMeshes.Mesh m = meshes.mesh(gl.lod, o[2] > 0 ? BellMeshes.CLEAR_RED : BellMeshes.CLEAR, b);
                boneWorld.set(gl.entity).mul(gl.bones[b]);
                // the camera sits at 0 0 0 here: where is that in the bone's own blocks
                boneWorld.invert(inv);
                inv.transformPosition(cam.set(0, 0, 0));
                meshes.keepSorted(m, cam, 3f);
                mv.set(gl.view).mul(boneWorld);
                upload(shader.MODEL_VIEW_MATRIX, mv);
                boneWorld.get3x3(rot).normal().transpose();
                rot.transform(l0.set(gl.l0)).normalize();
                rot.transform(l1.set(gl.l1)).normalize();
                if (shader.LIGHT0_DIRECTION != null) { shader.LIGHT0_DIRECTION.set(l0); shader.LIGHT0_DIRECTION.upload(); }
                if (shader.LIGHT1_DIRECTION != null) { shader.LIGHT1_DIRECTION.set(l1); shader.LIGHT1_DIRECTION.upload(); }
                float k = gl.lit;
                float a = 1f;
                if (shader.COLOR_MODULATOR != null) { shader.COLOR_MODULATOR.set(k, k * (1f - gl.hurt), k * (1f - gl.hurt), a); shader.COLOR_MODULATOR.upload(); }
                m.vb.bind();
                m.vb.draw();
            }
        }
        shader.clear();
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        rt.clearRenderState();
        glass.clear();
    }

    /** a frame that never reached the glass pass (a screenshot, a paused frame) must not leave it for the next */
    public static void forgetFrame() { glass.clear(); }
}
