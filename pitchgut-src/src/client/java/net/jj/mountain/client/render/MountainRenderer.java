package net.jj.mountain.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jj.mountain.MountainConfig;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.rig.MountainRig;
import net.jj.mountain.rig.RigState;
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
 * Draws the Mountain from his baked voxel meshes: nearly a thousand bones, so the shader is set up once and each
 * bone only swaps its matrix, light direction and tint before its draw.
 */
public class MountainRenderer extends EntityRenderer<MountainEntity> {
    private static final Vector3f LIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(), LIGHT1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Vector3f NLIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(), NLIGHT1 = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();
    private final MountainRig rig = MountainRig.get();
    private final RigState st = new RigState();
    private final Matrix4f[] bones = rig.newPoseArray(), draw = rig.newPoseArray();
    private final int[] eyeOfBone;
    /** which leg each bone belongs to, so a leg that knitted back wrong can be drawn as the dead thing it is */
    private final int[] legOfBone;
    private static final boolean DEBUG = Boolean.getBoolean("mountain.debug");
    private int dbg;

    public MountainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        eyeOfBone = new int[rig.boneCount()];
        java.util.Arrays.fill(eyeOfBone, -1);
        for (MountainRig.EyeDef E : rig.eyes) eyeOfBone[E.bone] = E.k;
        legOfBone = new int[rig.boneCount()];
        java.util.Arrays.fill(legOfBone, -1);
        for (MountainRig.LegDef L : rig.legs) for (int b : L.bones) if (b >= 0 && b < legOfBone.length) legOfBone[b] = L.k;
    }

    @Override
    public ResourceLocation getTextureLocation(MountainEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public boolean shouldRender(MountainEntity e, Frustum frustum, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(MountainEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        VoxelModel model = VoxelModel.MOUNTAIN;
        if (!model.ensureReady()) return;
        e.renderState(partial, st);
        rig.computePose(st, bones, draw);

        float s = e.mountainScale();
        float bodyYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        Matrix4f entity = new Matrix4f(ps.last().pose()).rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD).scale(s);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = RenderSystem.getProjectionMatrix();

        boolean nether = Minecraft.getInstance().level != null && Minecraft.getInstance().level.effects().constantAmbientLight();
        Vector3f l0w = nether ? NLIGHT0 : LIGHT0, l1w = nether ? NLIGHT1 : LIGHT1;

        // how lit he is: the meshes carry full skylight, darkness comes from this (brightest of a few points on him)
        // Light points inside a piece of world you have not loaded read as pitch black, and big he stands across
        // more of those than you have loaded, so he used to turn into a black shape. Only lit blocks count, and if
        // none of him is in loaded world he is simply given the daylight of the sky.
        boolean known = e.level().hasChunkAt(e.blockPosition());
        int bl = known ? LightTexture.block(packedLight) : 0, sl = known ? LightTexture.sky(packedLight) : 0;
        if (e.clientPoseReady()) {
            List<Vec3> pts = new ArrayList<>();
            pts.add(e.clientWorld(rig.segCenter[2], rig.segments[2]));
            pts.add(e.clientWorld(rig.headCenter, rig.head));
            pts.add(e.position().add(0, 2 + 120 * s, 0));
            for (Vec3 p : pts) {
                BlockPos bp = BlockPos.containing(p);
                if (!e.level().hasChunkAt(bp)) continue;
                known = true;
                int l = LevelRenderer.getLightColor(e.level(), bp);
                bl = Math.max(bl, LightTexture.block(l)); sl = Math.max(sl, LightTexture.sky(l));
            }
        }
        if (!known) sl = Math.max(0, 15 - e.level().getSkyDarken());
        float lit = 0.25f + 0.75f * Math.max(bl, sl) / 15f;
        float hurt = e.hurtTime > 0 ? 0.35f : 0f;
        float dying = e.isDeadOrDying() ? Mth.clamp((e.deathTime + partial - 150f) / 70f, 0f, 1f) : 0f;
        int nEyes = rig.eyes.length;
        // all his eyes burn red while he stares you down
        float gaze = st.attack == RigState.GAZE ? Mth.clamp(st.attackT / 50f, 0f, 1f) * Mth.clamp((130f - st.attackT) / 20f, 0f, 1f)
                : st.attack == RigState.EYE_STORM ? Mth.clamp(st.attackT / 22f, 0f, 1f) * Mth.clamp((120f - st.attackT) / 16f, 0f, 1f)
                : 0f;
        // torn up, his eyes sit red even between attacks
        gaze = Math.max(gaze, e.phase() == 2 ? 0.34f : e.phase() == 1 ? 0.15f : 0f);

        // far off, draw him with the merged blocks: a quarter of the faces and you cannot tell at that distance
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean far = net.jj.mountain.MountainConfig.V.simpleFarAway
                && e.distanceToSqr(cam.x, cam.y, cam.z) > Mth.square(net.jj.mountain.MountainConfig.V.simpleFarAwayAt * Math.max(0.25f, s));

        RenderType rt = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) { rt.clearRenderState(); return; }
        // The game fades everything out at the edge of your render distance. He is bigger than that: at a large size
        // you cannot back off far enough to see all of him without standing past the fog, and he would go white and
        // disappear while every part of him was still being sent to you. His own fog is pushed out to his own size.
        float wantFog = Math.min(MountainConfig.V.renderDistance * 1.2f, 300f * Math.max(0.2f, s) + 220f);
        float fogStart0 = RenderSystem.getShaderFogStart(), fogEnd0 = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Math.max(fogStart0, wantFog * 0.85f));
        RenderSystem.setShaderFogEnd(Math.max(fogEnd0, wantFog));
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, proj, Minecraft.getInstance().getWindow());
        shader.apply();
        Uniform uMV = shader.MODEL_VIEW_MATRIX, uCol = shader.COLOR_MODULATOR, uL0 = shader.LIGHT0_DIRECTION, uL1 = shader.LIGHT1_DIRECTION;
        Matrix4f mv = new Matrix4f(), boneWorld = new Matrix4f();
        Matrix3f rot = new Matrix3f();
        Vector3f l0 = new Vector3f(), l1 = new Vector3f();
        for (int pass = 0; pass < 2; pass++) {
            boolean glowPass = pass == 1;
            for (int b = 0; b < rig.boneCount(); b++) {
                VertexBuffer vb = glowPass ? model.glow(b, far) : model.solid(b, far);
                if (vb == null) continue;
                boneWorld.set(entity).mul(draw[b]);
                mv.set(view).mul(boneWorld);
                if (uMV != null) { uMV.set(mv); uMV.upload(); }
                // the shader lights by the raw normals in the mesh, so turn the light into the bone's own frame
                boneWorld.get3x3(rot).normal().transpose();
                rot.transform(l0.set(l0w)).normalize();
                rot.transform(l1.set(l1w)).normalize();
                if (uL0 != null) { uL0.set(l0); uL0.upload(); }
                if (uL1 != null) { uL1.set(l1); uL1.upload(); }
                float r = 1f, g = 1f - hurt, bb = 1f - hurt;
                // a leg he has had broken once and knitted back is grey right through, and stays that way
                int leg = legOfBone[b];
                if (leg >= 0 && st.legScarred(leg)) {
                    float grey = (r + g + bb) / 3f * 0.52f;
                    r = Mth.lerp(0.92f, r, grey * 0.98f);
                    g = Mth.lerp(0.92f, g, grey * 1.00f);
                    bb = Mth.lerp(0.92f, bb, grey * 1.10f);   // a cold cast, so it reads as dead from a long way off
                }
                int eye = eyeOfBone[b];
                if (eye >= 0) {
                    boolean shut = st.isPopped(eye) || (dying > 0f && eye < dying * nEyes * 1.05f);
                    if (shut) { r = 0.42f; g = 0.13f; bb = 0.16f; }
                    else if (gaze > 0f) { r = 1f + 0.7f * gaze; g = 1f - 0.6f * gaze; bb = 1f - 0.65f * gaze; }
                    else {
                        // asleep: a lid of his skin over the eye, drawn back as it opens
                        float lid = 1f - MountainRig.eyeOpenFor(eye, st.eyesOpen);
                        if (lid > 0f) { lid = Math.min(1f, lid * 1.5f); r = Mth.lerp(lid, r, 0.93f); g = Mth.lerp(lid, g, 0.62f); bb = Mth.lerp(lid, bb, 0.60f); }
                    }
                }
                float k = glowPass ? 1f : lit;
                if (eye >= 0 && gaze > 0f) k = Math.max(k, 0.5f + 0.5f * gaze);
                if (uCol != null) { uCol.set(r * k, g * k, bb * k, 1f); uCol.upload(); }
                vb.bind();
                vb.draw();
            }
        }
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
        if (DEBUG && (dbg++ % 200) == 0) net.jj.mountain.MountainMod.LOG.info("render mountain at {} scale {} lit {} bone0 {}", e.position(), s, lit, draw[0].getTranslation(new Vector3f()));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (nether) Lighting.setupNetherLevel(); else Lighting.setupLevel();
        rt.clearRenderState();
        // goo streams, the pull of his breath, the blast: batched and drawn later with the other translucent things
        Matrix4f rel = new Matrix4f().rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD).scale(s);
        MountainFX.render(e, rel, bones, st, partial, ps, buffers, packedLight);
    }
}
