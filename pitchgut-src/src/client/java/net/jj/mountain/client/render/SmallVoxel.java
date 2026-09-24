package net.jj.mountain.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws one of the small voxel creatures: each bone's mesh with its own matrix, lit like an entity. */
final class SmallVoxel {
    private SmallVoxel() {}
    private static final Vector3f L0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize(), L1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();

    static float lit(int packedLight) {
        float l = 0.3f + 0.7f * Math.max(LightTexture.block(packedLight), LightTexture.sky(packedLight)) / 15f;
        var lvl = Minecraft.getInstance().level;
        if (lvl != null) l = Math.max(l, 0.35f + 0.65f * lvl.dimensionType().ambientLight());      // his gut has its own dim glow
        return l;
    }

    /** base: camera-space matrix of the model origin (already scaled to model units); bones: each bone in model space */
    static void draw(VoxelModel model, Matrix4f base, Matrix4f[] bones, float lit, float[] tint) {
        RenderType rt = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f mv0 = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix3f rot = new Matrix3f();
        int n = Math.min(bones.length, model.boneCount());
        for (int b = 0; b < n; b++) {
            Matrix4f m = new Matrix4f(base).mul(bones[b]);
            m.get3x3(rot).normal().transpose();
            RenderSystem.setShaderLights(rot.transform(new Vector3f(L0)).normalize(), rot.transform(new Vector3f(L1)).normalize());
            Matrix4f view = new Matrix4f(mv0).mul(m);
            float r = tint[b * 3], g = tint[b * 3 + 1], bl = tint[b * 3 + 2];
            for (int pass = 0; pass < 2; pass++) {
                VertexBuffer vb = pass == 0 ? model.solid(b) : model.glow(b);
                if (vb == null) continue;
                float k = pass == 0 ? lit : 1f;
                RenderSystem.setShaderColor(r * k, g * k, bl * k, 1f);
                vb.bind();
                vb.drawWithShader(view, proj, shader);
            }
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        var lvl = Minecraft.getInstance().level;
        if (lvl != null && lvl.effects().constantAmbientLight()) Lighting.setupNetherLevel(); else Lighting.setupLevel();
        rt.clearRenderState();
    }

    static float[] tint(int bones, float r, float g, float b) {
        float[] t = new float[bones * 3];
        for (int i = 0; i < bones; i++) { t[i * 3] = r; t[i * 3 + 1] = g; t[i * 3 + 2] = b; }
        return t;
    }

    static float smooth(float t) { t = Math.max(0f, Math.min(1f, t)); return t * t * (3f - 2f * t); }
}
