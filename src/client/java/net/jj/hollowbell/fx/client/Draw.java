package net.jj.hollowbell.fx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Function;
import net.jj.hollowbell.fx.BigFx;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * How his moves are put on the screen beyond the pieces of {@link FxWorld}: soft textured ribbons, bands and sprites
 * in the world, added as light (glow) or laid over what's behind (lay). Everything is given in world blocks and drawn
 * from the camera; what lies past the far edge of the view is drawn nearer and smaller, so it isn't cut off.
 */
final class Draw {
    private Draw() {}

    static final ResourceLocation GLINTS = tex("glints"), BAND = tex("band"), WISP = tex("wisp"), POOL = tex("pool");

    /** the cells of the glints sheet */
    static final int DOT = 0, EMBER = 1, PUFF = 2, STREAK = 3;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BigFx.NS, "textures/fx/move/" + name + ".png");
    }

    static double cx, cy, cz;
    static final Vector3f left = new Vector3f(), up = new Vector3f();
    static float far, partial, time;
    /** how lit the open air is where you are, 0.25 at night to 1 by day (dust is dimmer at night) */
    static float daylight;

    private static MultiBufferSource.BufferSource buffers;
    private static float fogStart0, fogEnd0;

    static void begin(Camera cam, float pt, ClientLevel level) {
        var p = cam.getPosition();
        cx = p.x;
        cy = p.y;
        cz = p.z;
        left.set(cam.getLeftVector());
        up.set(cam.getUpVector());
        partial = pt;
        time = (level.getGameTime() % 240000L) + pt;
        far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8f;
        float dark = level.dimensionType().hasSkyLight() ? level.getSkyDarken(pt) : 0.6f;
        daylight = 0.25f + 0.75f * Mth.clamp((dark - 0.2f) / 0.8f, 0f, 1f);
        if (buffers == null) {
            // each look its own buffer; what's laid over goes first, then the light added on top
            SequencedMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
            for (ResourceLocation t : new ResourceLocation[] {GLINTS, BAND, WISP, POOL})
                fixed.put(Look.LAY.apply(t), new ByteBufferBuilder(1 << 16));
            for (ResourceLocation t : new ResourceLocation[] {GLINTS, BAND, WISP, POOL})
                fixed.put(Look.GLOW.apply(t), new ByteBufferBuilder(1 << 16));
            fixed.put(Look.OVER.apply(GLINTS), new ByteBufferBuilder(1 << 12));
            buffers = MultiBufferSource.immediateWithBuffers(fixed, new ByteBufferBuilder(1 << 12));
        }
        fogStart0 = RenderSystem.getShaderFogStart();
        fogEnd0 = RenderSystem.getShaderFogEnd();
        if (fogEnd0 > 64f) {
            RenderSystem.setShaderFogStart(Math.max(fogStart0, 0.7f * Math.max(fogEnd0, 640f)));
            RenderSystem.setShaderFogEnd(Math.max(fogEnd0, 640f));
        }
    }

    static void end() {
        buffers.endBatch();
        RenderSystem.setShaderFogStart(fogStart0);
        RenderSystem.setShaderFogEnd(fogEnd0);
    }

    /** light added to what's behind (his glow, sound rings, sunlight) */
    static VertexConsumer glow(ResourceLocation t) {
        return buffers.getBuffer(Look.GLOW.apply(t));
    }

    /** light over everything, him too, whatever is in front (a blinding flash) */
    static VertexConsumer over(ResourceLocation t) {
        return buffers.getBuffer(Look.OVER.apply(t));
    }

    /** laid over what's behind, see-through where the texture is (goo, dust, scorch) */
    static VertexConsumer lay(ResourceLocation t) {
        return buffers.getBuffer(Look.LAY.apply(t));
    }

    /** one corner at a spot in the world */
    static void v(
            VertexConsumer vc, double x, double y, double z, float r, float g, float b, float a, float u, float v) {
        double rx = x - cx, ry = y - cy, rz = z - cz;
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        float k = d > far ? (float) (far / d) : 1f;
        vc.addVertex((float) rx * k, (float) ry * k, (float) rz * k)
                .setColor(r, g, b, Mth.clamp(a, 0f, 1f))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0f, 1f, 0f);
    }

    /** a sprite of the glints sheet turned to face you: half its width, turned by rot (radians) */
    static void sprite(
            VertexConsumer vc,
            double x,
            double y,
            double z,
            float half,
            float rot,
            int cell,
            float r,
            float g,
            float b,
            float a) {
        if (a < 0.004f || half <= 0f) return;
        float cs = Mth.cos(rot) * half, sn = Mth.sin(rot) * half;
        float ax = left.x * cs + up.x * sn, ay = left.y * cs + up.y * sn, az = left.z * cs + up.z * sn;
        float bx = -left.x * sn + up.x * cs, by = -left.y * sn + up.y * cs, bz = -left.z * sn + up.z * cs;
        float u0 = (cell & 1) * 0.5f, v0 = (cell >> 1) * 0.5f;
        v(vc, x - ax - bx, y - ay - by, z - az - bz, r, g, b, a, u0 + 0.5f, v0 + 0.5f);
        v(vc, x + ax - bx, y + ay - by, z + az - bz, r, g, b, a, u0, v0 + 0.5f);
        v(vc, x + ax + bx, y + ay + by, z + az + bz, r, g, b, a, u0, v0);
        v(vc, x - ax + bx, y - ay + by, z - az + bz, r, g, b, a, u0 + 0.5f, v0);
    }

    /** a flat square lying on the ground, turned by rot: the whole texture on it */
    static void flat(
            VertexConsumer vc,
            double x,
            double y,
            double z,
            float half,
            float rot,
            float r,
            float g,
            float b,
            float a) {
        if (a < 0.004f || half <= 0f) return;
        float c = Mth.cos(rot) * half, s = Mth.sin(rot) * half;
        v(vc, x - c + s, y, z - s - c, r, g, b, a, 0f, 0f);
        v(vc, x - c - s, y, z - s + c, r, g, b, a, 0f, 1f);
        v(vc, x + c - s, y, z + s + c, r, g, b, a, 1f, 1f);
        v(vc, x + c + s, y, z + s - c, r, g, b, a, 1f, 0f);
    }

    /**
     * A ribbon through points (x y z each), turned to face you: half widths per point, colour, alpha per point, the
     * texture along it from v0 by dv a point (u runs across it).
     */
    static void ribbon(
            VertexConsumer vc, double[] p, int n, float[] w, float r, float g, float b, float[] a, float v0, float dv) {
        if (n < 2) return;
        double[] sx = new double[n], sy = new double[n], sz = new double[n];
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            double tx = p[i1 * 3] - p[i0 * 3], ty = p[i1 * 3 + 1] - p[i0 * 3 + 1], tz = p[i1 * 3 + 2] - p[i0 * 3 + 2];
            double vx = p[i * 3] - cx, vy = p[i * 3 + 1] - cy, vz = p[i * 3 + 2] - cz;
            double ox = ty * vz - tz * vy, oy = tz * vx - tx * vz, oz = tx * vy - ty * vx;
            double l = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (l < 1e-9) {
                ox = left.x;
                oy = left.y;
                oz = left.z;
                l = 1;
            }
            sx[i] = ox / l * w[i];
            sy[i] = oy / l * w[i];
            sz[i] = oz / l * w[i];
        }
        for (int i = 0; i + 1 < n; i++) {
            int j = i + 1;
            if (a[i] < 0.004f && a[j] < 0.004f) continue;
            float va = v0 + dv * i, vb = v0 + dv * j;
            v(vc, p[i * 3] - sx[i], p[i * 3 + 1] - sy[i], p[i * 3 + 2] - sz[i], r, g, b, a[i], 0f, va);
            v(vc, p[j * 3] - sx[j], p[j * 3 + 1] - sy[j], p[j * 3 + 2] - sz[j], r, g, b, a[j], 0f, vb);
            v(vc, p[j * 3] + sx[j], p[j * 3 + 1] + sy[j], p[j * 3 + 2] + sz[j], r, g, b, a[j], 1f, vb);
            v(vc, p[i * 3] + sx[i], p[i * 3 + 1] + sy[i], p[i * 3 + 2] + sz[i], r, g, b, a[i], 1f, va);
        }
    }

    /**
     * A band lying round a centre: from radius r0 at height y0 to radius r1 at height y1 (flat on the ground when
     * the heights match, an upright wall when the radii do). The band texture runs across it, its brightest line
     * halfway; a0 and a1 are the alpha at its two edges. waves bends its edge up and down (a ripple), by amp.
     */
    static void hoop(
            VertexConsumer vc,
            double x,
            double z,
            double y0,
            double y1,
            double r0,
            double r1,
            float r,
            float g,
            float b,
            float a0,
            float a1,
            int waves,
            float amp,
            float phase) {
        if ((a0 < 0.004f && a1 < 0.004f) || Math.max(r0, r1) <= 0) return;
        int n = Mth.clamp((int) (Math.max(r0, r1) * 0.8), 24, 96);
        for (int i = 0; i < n; i++) {
            double t0 = i * Math.PI * 2 / n, t1 = (i + 1) * Math.PI * 2 / n;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            double w0 = waves > 0 ? amp * Math.sin(t0 * waves + phase) : 0;
            double w1 = waves > 0 ? amp * Math.sin(t1 * waves + phase) : 0;
            v(vc, x + c0 * r0, y0 + w0, z + s0 * r0, r, g, b, a0, 0f, 0f);
            v(vc, x + c1 * r0, y0 + w1, z + s1 * r0, r, g, b, a0, 0f, 1f);
            v(vc, x + c1 * r1, y1 + w1, z + s1 * r1, r, g, b, a1, 1f, 1f);
            v(vc, x + c0 * r1, y1 + w0, z + s0 * r1, r, g, b, a1, 1f, 0f);
        }
    }

    /** a little over the ground, more the further off it is (so it doesn't flicker into it) */
    static double lift(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return 0.06 + Math.sqrt(dx * dx + dz * dz) * 0.002;
    }

    static float smooth(float k) {
        k = Mth.clamp(k, 0f, 1f);
        return k * k * (3 - 2 * k);
    }

    /** how his moves look drawn: added (glow) or laid over (lay), never culled, never hiding what's behind */
    private static final class Look extends RenderType {
        static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(t -> new Look(
                "hollowbell_move_glow",
                List.of(
                        RENDERTYPE_EYES_SHADER,
                        new TextureStateShard(t, true, false),
                        LIGHTNING_TRANSPARENCY,
                        LEQUAL_DEPTH_TEST,
                        NO_CULL,
                        COLOR_WRITE)));
        static final Function<ResourceLocation, RenderType> OVER = Util.memoize(t -> new Look(
                "hollowbell_move_over",
                List.of(
                        RENDERTYPE_EYES_SHADER,
                        new TextureStateShard(t, true, false),
                        LIGHTNING_TRANSPARENCY,
                        NO_DEPTH_TEST,
                        NO_CULL,
                        COLOR_WRITE)));
        static final Function<ResourceLocation, RenderType> LAY = Util.memoize(t -> new Look(
                "hollowbell_move_lay",
                List.of(
                        RENDERTYPE_EYES_SHADER,
                        new TextureStateShard(t, true, false),
                        TRANSLUCENT_TRANSPARENCY,
                        LEQUAL_DEPTH_TEST,
                        NO_CULL,
                        COLOR_WRITE)));

        private Look(String name, List<RenderStateShard> shards) {
            super(
                    name,
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    false,
                    () -> shards.forEach(RenderStateShard::setupRenderState),
                    () -> shards.forEach(RenderStateShard::clearRenderState));
        }
    }
}
