package net.jj.hollowbell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jj.hollowbell.net.GlassBellPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The Glass Bell drawn: a pale dome of his glass over where it stands, glinting, fading in and out. */
public final class GlassBellClient {
    private GlassBellClient() {}

    private static final ResourceLocation GLASS = ResourceLocation.withDefaultNamespace("textures/block/glass.png");
    private static final int RINGS = 10, SIDES = 28;

    private record Dome(Vec3 at, float radius, long from, long until) {}

    private static final List<Dome> domes = new ArrayList<>();

    public static void receive(GlassBellPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        domes.removeIf(d -> d.at().distanceToSqr(at) < 0.01);
        long now = mc.level.getGameTime();
        if (p.ticks() > 0) domes.add(new Dome(at, p.radius(), now, now + p.ticks()));
    }

    public static void clear() {
        domes.clear();
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (domes.isEmpty() || mc.level == null) return;
        long now = mc.level.getGameTime();
        domes.removeIf(d -> now > d.until());
        if (domes.isEmpty()) return;
        MultiBufferSource buffers = ctx.consumers();
        if (buffers == null) return;
        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.camera().getPosition();
        PoseStack ps = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLASS));
        for (Dome d : domes) {
            float t = now - d.from() + pt, left = d.until() - now - pt;
            // it grows up out of the ground, then fades at the end
            float grow = Mth.clamp(t / 8f, 0f, 1f);
            float alpha = 0.45f * Mth.clamp(Math.min(t / 8f, left / 20f), 0f, 1f);
            float shine = 0.85f + 0.15f * Mth.sin(t * 0.15f);
            ps.pushPose();
            ps.translate(d.at().x - cam.x, d.at().y - cam.y, d.at().z - cam.z);
            dome(ps.last(), vc, d.radius() * (0.3f + 0.7f * grow), alpha, shine);
            ps.popPose();
        }
    }

    /** the top half of a sphere, one tile of the glass texture on each piece (about a block across) */
    private static void dome(PoseStack.Pose pose, VertexConsumer vc, float r, float a, float shine) {
        int col = ((int) (a * 255) << 24)
                | ((int) (184 * shine) << 16)
                | ((int) (240 * shine) << 8)
                | (int) (200 * shine);
        for (int i = 0; i < RINGS; i++) {
            float v0 = (float) i / RINGS, v1 = (float) (i + 1) / RINGS;
            float p0 = v0 * Mth.HALF_PI, p1 = v1 * Mth.HALF_PI;
            for (int j = 0; j < SIDES; j++) {
                float t0 = j * Mth.TWO_PI / SIDES, t1 = (j + 1) * Mth.TWO_PI / SIDES;
                corner(pose, vc, r, p0, t0, 0, 0, col);
                corner(pose, vc, r, p0, t1, 1, 0, col);
                corner(pose, vc, r, p1, t1, 1, 1, col);
                corner(pose, vc, r, p1, t0, 0, 1, col);
            }
        }
    }

    /** one corner: pitch up from the ground, turn round the middle */
    private static void corner(
            PoseStack.Pose pose, VertexConsumer vc, float r, float pitch, float turn, float u, float v, int col) {
        float nx = Mth.cos(pitch) * Mth.cos(turn), ny = Mth.sin(pitch), nz = Mth.cos(pitch) * Mth.sin(turn);
        vc.addVertex(pose, nx * r, ny * r, nz * r)
                .setColor(col)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
