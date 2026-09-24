package net.jj.mountain.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jj.mountain.entity.inside.WatcherEye;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** A loose eye: it turns to look at you, its four nerves trailing and twitching underneath. */
public class WatcherEyeRenderer extends EntityRenderer<WatcherEye> {
    private static final float SCALE = 0.1f, EYE_Y = 15f;
    private final Matrix4f[] bones = new Matrix4f[9];

    public WatcherEyeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f;
        for (int i = 0; i < 9; i++) bones[i] = new Matrix4f();
    }

    @Override public ResourceLocation getTextureLocation(WatcherEye e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(WatcherEye e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        VoxelModel model = VoxelModel.WATCHER;
        if (!model.ensureReady()) return;
        float tau = e.tickCount + partial;
        float pitch = Mth.lerp(partial, e.xRotO, e.getXRot()) * Mth.DEG_TO_RAD;
        float charge = e.charge();
        bones[0].identity().translate(0, EYE_Y, 0).rotateX(pitch).rotateZ(0.05f * Mth.sin(tau * 0.05f)).translate(0, -EYE_Y, 0);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            float bx = (float) Math.cos(a) * 4f, bz = (float) Math.sin(a) * 4f, by = EYE_Y - 7f + 1.5f;
            float mx = bx + (float) Math.cos(a) * 2.5f, my = by - 6.5f, mz = bz + (float) Math.sin(a) * 2.5f;
            float ax = (float) Math.sin(a), az = (float) -Math.cos(a);
            float w1 = 0.3f * Mth.sin(tau * 0.08f + k * 1.7f) + 0.25f * charge * Mth.sin(tau * 0.9f + k);
            float w2 = 0.45f * Mth.sin(tau * 0.11f + k * 2.3f + 1f);
            bones[1 + 2 * k].identity().translate(bx, by, bz).rotate(w1, ax, 0f, az).rotateZ(0.08f * Mth.sin(tau * 0.06f + k)).translate(-bx, -by, -bz);
            bones[2 + 2 * k].set(bones[1 + 2 * k]).translate(mx, my, mz).rotate(w2, ax, 0f, az).translate(-mx, -my, -mz);
        }
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        Matrix4f base = new Matrix4f(ps.last().pose()).translate(0, 0.1f * Mth.sin(tau * 0.07f), 0).rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD);
        if (e.deathTime > 0) base.rotateZ(Math.min(1f, (e.deathTime + partial) / 15f) * 1.4f);
        base.scale(SCALE * (1f + 0.06f * charge));
        float hurt = e.hurtTime > 0 || e.deathTime > 0 ? 0.55f : 1f;
        float[] tint = SmallVoxel.tint(9, 1f, hurt, hurt);
        tint[0] = 1f + 0.6f * charge; tint[1] = hurt * (1f - 0.5f * charge); tint[2] = hurt * (1f - 0.55f * charge);
        SmallVoxel.draw(model, base, bones, Math.max(SmallVoxel.lit(light), 0.45f + 0.55f * charge), tint);
        super.render(e, yaw, partial, ps, buf, light);
    }
}
