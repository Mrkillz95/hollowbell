package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.StingerHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** the thrown strand end: the stinger itself, point first, and the pale strand back to the hand that threw it */
public class StingerHookRenderer extends EntityRenderer<StingerHook> {
    private final ItemRenderer items;
    private final ItemStack look = new ItemStack(ModItems.STINGER);

    public StingerHookRenderer(EntityRendererProvider.Context ctx) { super(ctx); items = ctx.getItemRenderer(); }

    @Override public ResourceLocation getTextureLocation(StingerHook e) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(StingerHook e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        Entity owner = e.getOwner();
        Vec3 me = e.getPosition(pt);
        Vec3 hand = owner == null ? me : handPos(owner, pt);
        // point it away from the hand
        Vec3 d = me.subtract(hand);
        pose.pushPose();
        if (d.lengthSqr() > 1e-4) {
            float ry = (float) Math.toDegrees(Math.atan2(d.x, d.z));
            float rx = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            pose.mulPose(Axis.YP.rotationDegrees(ry));
            pose.mulPose(Axis.XP.rotationDegrees(-rx));
        }
        // the item is drawn point up-right; turn it to point along the throw
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.ZP.rotationDegrees(-45));
        pose.scale(1.1f, 1.1f, 1.1f);
        items.renderStatic(look, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buf, e.level(), e.getId());
        pose.popPose();
        if (owner != null) strand(pose, buf, hand.subtract(me), e.state() == StingerHook.FLYING, e.tickCount + pt);
        super.render(e, yaw, pt, pose, buf, light);
    }

    /** a sagging pale green strand, tight once it has caught */
    private static void strand(PoseStack pose, MultiBufferSource buf, Vec3 to, boolean slack, float t) {
        VertexConsumer vc = buf.getBuffer(RenderType.lineStrip());
        Matrix4f m = pose.last().pose();
        int n = 24;
        double len = to.length();
        double sag = slack ? Math.min(1.5, len * 0.06) : Math.min(0.4, len * 0.015);
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            double wob = slack ? 0 : Math.sin(f * Math.PI * 3 + t * 0.9) * 0.05 * Math.sin(f * Math.PI);
            float x = (float) (to.x * f), y = (float) (to.y * f - sag * 4 * f * (1 - f) + wob), z = (float) (to.z * f);
            float nx = (float) to.x, ny = (float) to.y, nz = (float) to.z;
            float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-4) l = 1;
            int g = 200 + (int) (40 * Math.sin(f * 12 + t * 0.3));
            vc.addVertex(m, x, y, z).setColor(170, Mth.clamp(g, 0, 255), 180, 255).setNormal(pose.last(), nx / l, ny / l, nz / l);
        }
    }

    /** about where the throwing hand is */
    private Vec3 handPos(Entity owner, float pt) {
        Minecraft mc = Minecraft.getInstance();
        float body = Mth.lerp(pt, owner instanceof Player p ? p.yBodyRotO : owner.yRotO, owner instanceof Player p2 ? p2.yBodyRot : owner.getYRot());
        int side = owner instanceof Player p && p.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 base = owner.getPosition(pt);
        if (owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            // first person: from just below and right of the eye, along the look
            Vec3 look = owner.getViewVector(pt), eye = owner.getEyePosition(pt);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize().scale(0.35 * side);
            return eye.add(look.scale(0.6)).add(right).add(0, -0.3, 0);
        }
        float r = body * Mth.DEG_TO_RAD;
        double sx = -Mth.cos(r) * 0.38 * side, sz = -Mth.sin(r) * 0.38 * side;
        return base.add(sx, owner.getBbHeight() * 0.62, sz);
    }
}
