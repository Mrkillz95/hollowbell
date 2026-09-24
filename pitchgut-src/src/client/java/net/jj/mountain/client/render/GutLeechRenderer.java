package net.jj.mountain.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jj.mountain.entity.inside.GutLeech;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The leech: four pieces that ripple side to side as it crawls, and curl round whatever it has latched onto. */
public class GutLeechRenderer extends EntityRenderer<GutLeech> {
    private static final float SCALE = 0.09f;
    private final Matrix4f[] bones = {new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private static final float[] PIVOT = {-6f, -6f, 0f, 6f};

    public GutLeechRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0.35f; }

    @Override public ResourceLocation getTextureLocation(GutLeech e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(GutLeech e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        VoxelModel model = VoxelModel.LEECH;
        if (!model.ensureReady()) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (e.getVehicle() == mc.player && mc.options.getCameraType().isFirstPerson()) return;     // it's on the back of your neck: you can't see it
        float tau = e.tickCount + partial;
        float move = Math.min(1f, e.walkAnimation.speed(partial) * 3f);
        boolean latched = e.isPassenger();
        float wig = latched ? 0.15f : 0.12f + 0.3f * move;
        float spd = latched ? 0.25f : 0.2f + 0.5f * move;
        // head swings against the rest of the body
        bones[0].identity().translate(0, 3, PIVOT[0]).rotateY(-0.5f * wig * Mth.sin(tau * spd)).translate(0, -3, -PIVOT[0]);
        Matrix4f parent = new Matrix4f();
        for (int i = 1; i < 4; i++) {
            float curl = latched ? 0.38f : 0.04f * Mth.sin(tau * 0.3f + i);
            bones[i].set(parent).translate(0, 3, PIVOT[i]).rotateY(wig * Mth.sin(tau * spd - i * 1.2f)).rotateX(curl).translate(0, -3, -PIVOT[i]);
            parent = bones[i];
        }
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        Matrix4f base = new Matrix4f(ps.last().pose()).rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD);
        if (latched) base.translate(0, 0.1f, 0).rotateX(0.9f);
        if (e.deathTime > 0) base.rotateZ(Math.min(1f, (e.deathTime + partial) / 12f) * 1.6f);
        base.scale(SCALE);
        float hurt = e.hurtTime > 0 || e.deathTime > 0 ? 0.55f : 1f;
        SmallVoxel.draw(model, base, bones, SmallVoxel.lit(light), SmallVoxel.tint(4, 1f, hurt, hurt));
        super.render(e, yaw, partial, ps, buf, light);
    }
}
