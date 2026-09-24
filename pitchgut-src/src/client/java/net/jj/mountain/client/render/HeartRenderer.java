package net.jj.mountain.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.platform.Lighting;
import net.jj.mountain.entity.HeartEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** His heart: a voxel heart that beats (a double thump), swelling from the bottom up. */
public class HeartRenderer extends EntityRenderer<HeartEntity> {
    private static final Vector3f L0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(), L1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();

    private static final float SIZE = 1.45f;
    public HeartRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0f; }

    @Override public ResourceLocation getTextureLocation(HeartEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    public static float beat(float t) {
        float p = (t % 24f) / 24f;
        float a = Math.max(0f, 1f - Math.abs(p - 0.08f) / 0.08f), b = Math.max(0f, 1f - Math.abs(p - 0.28f) / 0.1f) * 0.6f;
        return a * a + b * b;
    }

    @Override
    public void render(HeartEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        VoxelModel model = VoxelModel.HEART;
        if (!model.ensureReady()) return;
        float t = e.tickCount + partial;
        float k = SIZE * (1f + 0.085f * beat(t) + (e.hurtTime > 0 ? 0.04f : 0f));
        Matrix4f m = new Matrix4f(ps.last().pose()).rotateY(t * 0.004f).translate(0, 8.5f, 0).scale(k, k * 0.97f + 0.03f * SIZE, k).translate(0, -6f, 0);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(m);
        float lit = 1.0f;
        float red = e.hurtTime > 0 ? 0.6f : 1f;
        RenderType rt = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        Matrix3f rot = new Matrix3f();
        m.get3x3(rot).normal().transpose();
        RenderSystem.setShaderLights(rot.transform(new Vector3f(L0)).normalize(), rot.transform(new Vector3f(L1)).normalize());
        for (int b = 0; b < model.boneCount(); b++) {
            for (int pass = 0; pass < 2; pass++) {
                VertexBuffer vb = pass == 0 ? model.solid(b) : model.glow(b);
                if (vb == null) continue;
                float c = pass == 0 ? lit : 1f;
                RenderSystem.setShaderColor(c, c * red, c * red, 1f);
                vb.bind();
                vb.drawWithShader(view, RenderSystem.getProjectionMatrix(), shader);
            }
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Lighting.setupLevel();
        rt.clearRenderState();
    }
}
