package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * The parts of the bell glass set that stand out from the body, built from his own blocks: bone ribs and a glowing
 * knob on the helmet with two short strands down the back, pods on the back of the chestplate and bone caps on the
 * shoulders with tendrils, a skirt of glass strands round the leggings, copper bands on the boots. The glow breathes
 * the way his spots do, and the strands sway with the wearer's walk and drift a little when still.
 * All sizes are in model pixels (a sixteenth of a block), in the space of the body part they hang from.
 */
final class BellArmorBits {
    private BellArmorBits() {}

    private static TextureAtlasSprite bone, calcite, copper, froglight, glass;

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.withDefaultNamespace("block/" + name));
    }

    private static void sprites() {
        bone = sprite("bone_block_side"); calcite = sprite("calcite");
        copper = sprite("oxidized_copper"); froglight = sprite("verdant_froglight_side"); glass = sprite("lime_stained_glass");
    }

    static void render(PoseStack pose, MultiBufferSource buf, LivingEntity e, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> m) {
        sprites();                        // looked up each time: cheap, and right after a resource reload
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float t = e.tickCount + pt;
        float breath = 0.5f + 0.5f * Mth.sin(t * 0.07f + e.getId());               // the slow glow, like his spots
        float walk = Mth.clamp(e.walkAnimation.speed(pt), 0f, 1f);
        float step = e.walkAnimation.position(pt);
        VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        VertexConsumer see = buf.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        int glow = LightTexture.FULL_BRIGHT;
        int glowCol = shade(0xFFFFFF, 0.7f + 0.3f * breath);
        switch (slot) {
            case HEAD -> {
                pose.pushPose();
                m.head.translateAndRotate(pose);
                // ribs over the crown of the helmet, like his dome
                for (int i = 0; i < 4; i++) {
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotationDegrees(45 + i * 90));
                    box(pose, solid, bone, -0.6f, -9.7f, -0.6f, 0.6f, -9.1f, 6.4f, 0xFFFFFF, light);
                    box(pose, solid, bone, -0.6f, -9.4f, 5.8f, 0.6f, -2.5f, 6.6f, 0xFFFFFF, light);
                    pose.popPose();
                }
                // the copper band round the rim
                ring(pose, solid, copper, 5.25f, -2.6f, -1.6f, 0xFFFFFF, light);
                // the glowing knob on top, swelling a little as it breathes
                pose.pushPose();
                pose.translate(0, -10.6f / 16f, 0);
                float k = 1f + 0.12f * breath;
                pose.scale(k, k, k);
                box(pose, see, froglight, -1.6f, -1.2f, -1.6f, 1.6f, 1.0f, 1.6f, glowCol, glow);
                pose.popPose();
                // two glowing spots at the temples
                box(pose, see, froglight, -5.35f, -6.5f, -2.6f, -5.05f, -4.8f, -0.9f, glowCol, glow);
                box(pose, see, froglight, 5.05f, -6.5f, -2.6f, 5.35f, -4.8f, -0.9f, glowCol, glow);
                // two short strands down the back of the neck
                for (int s = -1; s <= 1; s += 2)
                    strand(pose, see, solid, s * 2.2f, -2.2f, 5.3f, 3, 3.2f, 12f, -s * 4f, t, s * 1.3f, walk * 18f * Mth.sin(step * 0.66f), light, glowCol);
                pose.popPose();
            }
            case CHEST -> {
                pose.pushPose();
                m.body.translateAndRotate(pose);
                // bone ribs across the chest with a spot in the middle
                box(pose, solid, bone, -4.6f, 2.4f, -3.4f, 4.6f, 3.3f, -2.9f, 0xFFFFFF, light);
                box(pose, solid, bone, -4.2f, 6.0f, -3.4f, 4.2f, 6.9f, -2.9f, 0xFFFFFF, light);
                box(pose, see, froglight, -1.1f, 3.8f, -3.6f, 1.1f, 5.6f, -3.0f, glowCol, glow);
                // two pods on the back: glass with a glowing heart, breathing
                for (int s = -1; s <= 1; s += 2) {
                    pose.pushPose();
                    pose.translate(s * 2.3f / 16f, 5.2f / 16f, 3.6f / 16f);
                    float b = 1f + 0.07f * Mth.sin(t * 0.09f + s);
                    pose.scale(b, b, b);
                    box(pose, see, froglight, -0.8f, -1.2f, -0.2f, 0.8f, 1.0f, 1.2f, glowCol, glow);
                    box(pose, see, glass, -1.6f, -2.2f, -0.6f, 1.6f, 1.9f, 2.0f, 0xC8FFFFFF, light);
                    pose.popPose();
                }
                pose.popPose();
                // shoulders: bone caps with tendrils hanging off the outside
                shoulder(pose, see, solid, m, true, t, walk, step, light, glowCol);
                shoulder(pose, see, solid, m, false, t, walk, step, light, glowCol);
            }
            case LEGS -> {
                pose.pushPose();
                m.body.translateAndRotate(pose);
                ring(pose, solid, copper, 4.7f, 10.6f, 11.8f, 0xFFFFFF, light);
                // a skirt of glass strands round the waist; the front and back ones swing with the legs
                float legSwing = (m.rightLeg.xRot - m.leftLeg.xRot) * Mth.RAD_TO_DEG * 0.45f;
                for (int i = 0; i < 10; i++) {
                    float a = i * Mth.TWO_PI / 10f + 0.31f;
                    float x = Mth.sin(a) * 4.8f, z = Mth.cos(a) * 2.9f;
                    float out = 10f + 6f * Math.abs(Mth.cos(a));
                    float swing = Mth.cos(a) * legSwing;
                    strand(pose, see, solid, x, 11.6f, z, 2, 2.6f, out * Math.signum(Mth.cos(a) + 1e-3f), -Mth.sin(a) * 10f, t, i * 0.9f, swing, light, glowCol);
                }
                pose.popPose();
            }
            case FEET -> {
                for (int s = 0; s < 2; s++) {
                    pose.pushPose();
                    (s == 0 ? m.rightLeg : m.leftLeg).translateAndRotate(pose);
                    ring(pose, solid, copper, 2.75f, 8.2f, 9.3f, 0xFFFFFF, light);
                    // a bone spur at the heel and a small glow at the ankle
                    box(pose, solid, bone, -0.7f, 10.0f, 2.6f, 0.7f, 11.4f, 3.8f, 0xFFFFFF, light);
                    box(pose, see, froglight, (s == 0 ? -2.95f : 2.65f), 8.4f, -0.6f, (s == 0 ? -2.65f : 2.95f), 9.1f, 0.6f, glowCol, glow);
                    pose.popPose();
                }
            }
            default -> {}
        }
    }

    private static void shoulder(PoseStack pose, VertexConsumer see, VertexConsumer solid, HumanoidModel<LivingEntity> m, boolean right,
                                 float t, float walk, float step, int light, int glowCol) {
        pose.pushPose();
        (right ? m.rightArm : m.leftArm).translateAndRotate(pose);
        float s = right ? -1 : 1;          // outward
        float x0 = right ? -4.4f : -0.6f, x1 = right ? 0.6f : 4.4f;
        box(pose, solid, bone, x0, -3.2f, -3.2f, x1, -1.6f, 3.2f, 0xFFFFFF, light);
        box(pose, solid, calcite, x0 + (right ? -0.3f : 0.6f), -2.2f, -2.2f, x1 + (right ? -0.6f : 0.3f), -1.2f, 2.2f, 0xFFFFFF, light);
        for (int i = -1; i <= 1; i++)
            strand(pose, see, solid, s * 3.9f, -1.6f, i * 2.2f, 2, 2.4f, i * 6f, s * -14f, t, i * 1.7f + (right ? 0 : 3), walk * 10f * Mth.sin(step * 0.66f + i), light, glowCol);
        pose.popPose();
    }

    /**
     * A hanging strand: a few glass segments, each bending a little more than the last, with a glowing tip. Its sway
     * is a slow drift in time plus whatever the walk adds; the lower segments lag behind (follow-through).
     */
    private static void strand(PoseStack pose, VertexConsumer see, VertexConsumer solid, float x, float y, float z, int segs, float len,
                               float tiltX, float tiltZ, float t, float phase, float swing, int light, int glowCol) {
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(tiltX + swing));
        pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        for (int i = 0; i < segs; i++) {
            float lag = i * 0.6f;
            float drift = 6f * Mth.sin(t * 0.08f + phase - lag) + 3f * Mth.sin(t * 0.13f + phase * 1.7f - lag);
            pose.mulPose(Axis.XP.rotationDegrees(drift + swing * 0.35f * i));
            pose.mulPose(Axis.ZP.rotationDegrees(0.6f * drift));
            float w = 0.55f - 0.1f * i;
            box(pose, see, glass, -w, 0, -w, w, len, w, 0xD0FFFFFF, light);
            pose.translate(0, len / 16f, 0);
        }
        box(pose, see, froglight, -0.45f, -0.2f, -0.45f, 0.45f, 0.7f, 0.45f, glowCol, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** a flat band round a part: four thin boxes, from y0 to y1, half-width r */
    private static void ring(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, float r, float y0, float y1, int col, int light) {
        float th = 0.45f;
        box(pose, vc, s, -r, y0, -r - th, r, y1, -r, col, light);
        box(pose, vc, s, -r, y0, r, r, y1, r + th, col, light);
        box(pose, vc, s, -r - th, y0, -r, -r, y1, r, col, light);
        box(pose, vc, s, r, y0, -r, r + th, y1, r, col, light);
    }

    private static int shade(int rgb, float k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k), g = (int) Math.min(255, ((rgb >> 8) & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** a box in model pixels; the texture is cut to the face's size so it isn't stretched. col is ARGB (no alpha = opaque) */
    static void box(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, float x0, float y0, float z0, float x1, float y1, float z1, int col, int light) {
        if ((col >>> 24) == 0) col |= 0xFF000000;
        float a = x0 / 16f, b = y0 / 16f, c = z0 / 16f, d = x1 / 16f, e = y1 / 16f, f = z1 / 16f;
        float sx = Math.min(1f, (x1 - x0) / 16f), sy = Math.min(1f, (y1 - y0) / 16f), sz = Math.min(1f, (z1 - z0) / 16f);
        PoseStack.Pose p = pose.last();
        // -y (model up is -y) ... each face: 4 corners, counter-clockwise from outside
        quad(vc, p, s, col, light, 0, -1, 0, sx, sz, a, b, c, d, b, c, d, b, f, a, b, f);
        quad(vc, p, s, col, light, 0, 1, 0, sx, sz, a, e, f, d, e, f, d, e, c, a, e, c);
        quad(vc, p, s, col, light, 0, 0, -1, sx, sy, d, b, c, a, b, c, a, e, c, d, e, c);
        quad(vc, p, s, col, light, 0, 0, 1, sx, sy, a, b, f, d, b, f, d, e, f, a, e, f);
        quad(vc, p, s, col, light, -1, 0, 0, sz, sy, a, b, c, a, b, f, a, e, f, a, e, c);
        quad(vc, p, s, col, light, 1, 0, 0, sz, sy, d, b, f, d, b, c, d, e, c, d, e, f);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int col, int light, float nx, float ny, float nz,
                             float su, float sv, float... v) {
        float u0 = s.getU(0), u1 = s.getU(su), v0 = s.getV(0), v1 = s.getV(sv);
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++)
            vc.addVertex(p, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(col).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
