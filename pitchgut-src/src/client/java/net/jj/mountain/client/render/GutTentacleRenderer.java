package net.jj.mountain.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jj.mountain.entity.inside.GutTentacle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The gut tentacle: nine pieces bending in a chain, rising out of (and sinking back into) the floor. */
public class GutTentacleRenderer extends EntityRenderer<GutTentacle> {
    private static final float SCALE = 0.22f;
    private static final int SEGS = 9, SEG = 4;
    private final Matrix4f[] bones = new Matrix4f[SEGS];

    public GutTentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.6f;
        for (int i = 0; i < SEGS; i++) bones[i] = new Matrix4f();
    }

    @Override public ResourceLocation getTextureLocation(GutTentacle e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(GutTentacle e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        VoxelModel model = VoxelModel.TENTACLE;
        if (!model.ensureReady()) return;
        float tau = e.tickCount + partial;
        int phase = e.phase();
        float t = e.phaseTime() + partial;
        float dist = Mth.clamp(e.aimDist(), 1f, GutTentacle.REACH);
        // how far it has come up out of the floor
        float up = 1f;
        if (phase == GutTentacle.EMERGE) up = SmallVoxel.smooth(t / 34f);
        else if (phase == GutTentacle.SINK) up = 1f - SmallVoxel.smooth(t / 30f);
        if (e.deathTime > 0) up = 1f - SmallVoxel.smooth((e.deathTime + partial) / 24f);
        // bend per piece: + leans away from what it's after, - curls toward it (the side with the suckers)
        float bend = 0f, wrap = 0f;
        switch (phase) {
            case GutTentacle.LASH -> {
                float strike = -(0.12f + 0.12f * (1f - dist / GutTentacle.REACH));
                if (t < 15) bend = 0.13f * SmallVoxel.smooth(t / 14f);
                else if (t < 20) bend = Mth.lerp(SmallVoxel.smooth((t - 15f) / 4f), 0.13f, strike);
                else bend = Mth.lerp(SmallVoxel.smooth((t - 20f) / 14f), strike, 0f);
            }
            case GutTentacle.GRAB -> bend = -0.07f * SmallVoxel.smooth(t / 8f);
            case GutTentacle.HOLD -> { bend = -0.07f; wrap = 1f; }
            case GutTentacle.EMERGE -> bend = 0.05f * Mth.sin(tau * 0.6f);
            default -> {}
        }
        float squeeze = phase == GutTentacle.HOLD ? 0.05f * Mth.sin(tau * 0.5f) : 0f;
        Matrix4f parent = new Matrix4f();
        for (int k = 0; k < SEGS; k++) {
            float sway = 0.07f * Mth.sin(tau * 0.07f + k * 0.6f + e.getId()), side = 0.06f * Mth.sin(tau * 0.05f + k * 0.5f + 1f + e.getId());
            float bk = bend + (wrap > 0 && k >= 4 ? -0.33f - squeeze : 0f);
            float amp = phase == GutTentacle.LASH || phase == GutTentacle.HOLD ? 0.3f : 1f;
            bones[k].set(parent).translate(0, k * SEG, 0).rotateX(-bk * -1f + sway * amp).rotateZ(side * amp).translate(0, -k * SEG, 0);
            parent = bones[k];
        }
        Matrix4f base = new Matrix4f(ps.last().pose())
                .translate(0, -GutTentacle.HEIGHT * (1f - up), 0)
                .rotateY((180f - e.aim()) * Mth.DEG_TO_RAD)
                .scale(SCALE);
        float hurt = e.hurtTime > 0 ? 0.55f : 1f;
        SmallVoxel.draw(model, base, bones, SmallVoxel.lit(light), SmallVoxel.tint(SEGS, 1f, hurt, hurt));
        super.render(e, yaw, partial, ps, buf, light);
    }
}
