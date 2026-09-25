package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** A Belling is a tiny copy of him, drawn from the smallest version of his meshes, pulsing as it drifts. */
public class BellingRenderer extends EntityRenderer<Belling> {
    private static final float SIZE = 0.0085f;

    public BellingRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0.3f; }

    @Override public ResourceLocation getTextureLocation(Belling e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(Belling e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        BellMeshes meshes = BellMeshes.INSTANCE;
        if (!meshes.ensureReady()) return;
        BellRig rig = BellRig.get();
        float t = e.tickCount + partial;
        float p = Mth.sin(t * 0.25f) * 0.5f + 0.5f;
        float sxz = SIZE * (1f - 0.12f * p), sy = SIZE * (1f + 0.08f * p);
        Matrix4f base = new Matrix4f(ps.last().pose()).rotateY(t * 0.01f).scale(sxz, sy, sxz);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f mv = new Matrix4f();
        float k = 0.3f + 0.7f * Math.max(LightTexture.block(light), LightTexture.sky(light)) / 15f;
        float hurt = e.hurtTime > 0 ? 0.35f : 0f;
        for (int pass = 0; pass < 3; pass++) {
            RenderType rt = pass == 2 ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS) : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
            rt.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) { rt.clearRenderState(); continue; }
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            shader.apply();
            int kind = pass == 0 ? BellMeshes.SOLID : pass == 1 ? BellMeshes.GLOW : BellMeshes.CLEAR;
            float kk = pass == 1 ? 1f : k;
            if (shader.COLOR_MODULATOR != null) { shader.COLOR_MODULATOR.set(kk, kk * (1 - hurt), kk * (1 - hurt), 1f); shader.COLOR_MODULATOR.upload(); }
            mv.set(view).mul(base);
            if (shader.MODEL_VIEW_MATRIX != null) { shader.MODEL_VIEW_MATRIX.set(mv); shader.MODEL_VIEW_MATRIX.upload(); }
            for (int b = 0; b < rig.boneCount(); b++) {
                if (rig.kind[b] == BellRig.Kind.THREAD) continue;
                BellMeshes.Mesh m = meshes.mesh(BellMeshes.TINY, kind, b);
                if (m == null) continue;
                m.vb.bind();
                m.vb.draw();
            }
            shader.clear();
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            rt.clearRenderState();
        }
        Lighting.setupLevel();
        super.render(e, yaw, partial, ps, buffers, light);
    }
}
