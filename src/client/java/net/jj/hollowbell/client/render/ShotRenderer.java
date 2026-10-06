package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jj.hollowbell.entity.Shot;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What he throws. A stinger is drawn as the item. An egg clump is drawn as the very clump that came off his strands,
 * at his size (never smaller than about three blocks across, so it can be seen from far off), glowing and tumbling
 * as it falls, with a ring on the ground where it will land that closes in as it comes down. Landed, it lies
 * squashed in a splat; a hatching one shakes and swells until the Belling comes out.
 */
public class ShotRenderer extends EntityRenderer<Shot> {
    private final ThrownItemRenderer<Shot> item;

    public ShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        item = new ThrownItemRenderer<>(ctx, 2.2f, false);
    }

    @Override public ResourceLocation getTextureLocation(Shot e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public boolean shouldRender(Shot e, Frustum f, double cx, double cy, double cz) {
        if (e.kind() != Shot.EGG) return item.shouldRender(e, f, cx, cy, cz);
        // the egg and its marker on the ground, which can be a long way under it
        Vec3 l = e.landing();
        double r = markerRadius(e) + 4;
        AABB box = new AABB(e.getX(), e.getY(), e.getZ(), l.x, l.y, l.z).inflate(r, 2, r).expandTowards(0, 6, 0);
        return f.isVisible(box);
    }

    @Override
    public void render(Shot e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.kind() != Shot.EGG) { item.render(e, yaw, partial, ps, buf, light); return; }
        if (e.stage() == Shot.FALLING) marker(e, partial, ps, buf);
        egg(e, partial, ps);
    }

    /** how wide the ring round the landing spot is, at the widest (just dropped) */
    static double markerRadius(Shot e) { return 2.5 + 3 * e.size() + 2.5 + 3.5 * e.size(); }

    /** the ring on the ground: wide and pale as it drops, tight and red as it's about to land */
    private void marker(Shot e, float partial, PoseStack ps, MultiBufferSource buf) {
        Vec3 l = e.landing();
        float k = e.fallen();
        double hit = 2.5 + 3 * e.size();
        double r = Mth.lerp(k, markerRadius(e), hit);
        Vec3 me = e.getPosition(partial);
        VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        var level = e.level();
        int n = 40;
        float w = (float) (0.35 + 0.1 * r);
        int red = (int) Mth.lerp(k, 170, 255), grn = (int) Mth.lerp(k, 255, 70), blu = (int) Mth.lerp(k, 90, 40);
        float flash = 0.75f + 0.25f * Mth.sin((e.tickCount + partial) * (0.4f + 1.2f * k));
        int a = (int) (200 * flash);
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double[] xs = {l.x + Math.cos(a0) * (r - w), l.x + Math.cos(a0) * (r + w), l.x + Math.cos(a1) * (r + w), l.x + Math.cos(a1) * (r - w)};
            double[] zs = {l.z + Math.sin(a0) * (r - w), l.z + Math.sin(a0) * (r + w), l.z + Math.sin(a1) * (r + w), l.z + Math.sin(a1) * (r - w)};
            for (int j = 0; j < 4; j++) {
                double y = groundY(level, xs[j], zs[j], l.y) + 0.08;
                vc.addVertex(m, (float) (xs[j] - me.x), (float) (y - me.y), (float) (zs[j] - me.z)).setColor(red, grn, blu, a);
            }
        }
        // and a faint spot in the middle, where it hits
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI * 2 / 16, a1 = (i + 1) * Math.PI * 2 / 16;
            double rr = hit * 0.35;
            double[] xs = {l.x, l.x + Math.cos(a0) * rr, l.x + Math.cos(a1) * rr, l.x};
            double[] zs = {l.z, l.z + Math.sin(a0) * rr, l.z + Math.sin(a1) * rr, l.z};
            for (int j = 0; j < 4; j++) {
                double y = groundY(level, xs[j], zs[j], l.y) + 0.07;
                vc.addVertex(m, (float) (xs[j] - me.x), (float) (y - me.y), (float) (zs[j] - me.z)).setColor(red, grn, blu, (int) (90 * flash));
            }
        }
    }

    static double groundY(net.minecraft.world.level.Level level, double x, double z, double near) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunkAt(new net.minecraft.core.BlockPos(bx, 0, bz))) return near;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return Math.abs(y - near) > 12 ? near : y;
    }

    /** the clump itself, drawn from his own blocks */
    private void egg(Shot e, float partial, PoseStack ps) {
        BellMeshes meshes = BellMeshes.INSTANCE;
        if (!meshes.ensureReady()) return;
        BellRig rig = BellRig.get();
        int i = Mth.clamp(e.egg(), 0, rig.eggs.length - 1);
        var E = rig.eggs[i];
        float radius = Math.max(1f, E.radius());
        float s = Math.max(e.size(), 1.6f / radius);
        float t = e.tickCount + partial;
        float sx = s, sy = s, sz = s, sink = 0f;
        float spin = 0f;
        if (e.stage() == Shot.FALLING) spin = t * 0.08f;
        else {
            float age = e.stageAge(partial);
            // squashed flat on landing, bouncing back a little
            float squash = 1f - 0.55f * (float) Math.exp(-age * 0.25f) * Mth.cos(age * 0.6f);
            sy = s * 0.45f * squash;
            sx = sz = s * (1.45f - 0.25f * (squash - 0.45f));
            if (e.stage() == Shot.HATCHING) {
                // it heaves and swells until it bursts
                float k = Mth.clamp(age / Shot.HATCH_TICKS, 0f, 1f);
                float heave = 1f + (0.12f + 0.25f * k) * Math.abs(Mth.sin(age * (0.8f + 0.8f * k)));
                sy *= heave * (1f + 0.5f * k);
                sx *= 1f + 0.08f * Mth.sin(age * 1.7f);
                sz *= 1f + 0.08f * Mth.cos(age * 1.9f);
            } else {
                // a splat fades down into the ground
                float fade = Mth.clamp((age - (Shot.SPLAT_TICKS - 20)) / 20f, 0f, 1f);
                sink = fade;
                sy *= 1f - 0.8f * fade;
            }
        }
        Vector3f c = E.centre();
        Matrix4f base = new Matrix4f(ps.last().pose());
        if (e.stage() != Shot.FALLING) base.translate(0, radius * sy * 0.9f - sink * radius * s * 0.3f, 0);
        base.rotateY(e.getId() * 1.7f + spin).rotateX(spin * 0.6f).scale(sx, sy, sz).translate(-c.x, -c.y, -c.z);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f mv = new Matrix4f();
        // it glows a little of its own, so it shows against the sky and in the dark
        float lit = e.stage() == Shot.FALLING ? 1f : 0.85f;
        for (int pass = 0; pass < 3; pass++) {
            RenderType rt = pass == 2 ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS) : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
            rt.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) { rt.clearRenderState(); continue; }
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            shader.apply();
            int kind = pass == 0 ? BellMeshes.SOLID : pass == 1 ? BellMeshes.GLOW : BellMeshes.CLEAR;
            float kk = pass == 1 ? 1.2f : lit;
            if (shader.COLOR_MODULATOR != null) { shader.COLOR_MODULATOR.set(kk, kk, kk, 1f); shader.COLOR_MODULATOR.upload(); }
            mv.set(view).mul(base);
            if (shader.MODEL_VIEW_MATRIX != null) { shader.MODEL_VIEW_MATRIX.set(mv); shader.MODEL_VIEW_MATRIX.upload(); }
            // (an egg clump or pod is one slice of the full meshes)
            BellMeshes.Mesh m = meshes.mesh(BellMeshes.FULL, kind, net.jj.hollowbell.rig.BellPieces.get().first[E.bone()]);
            if (m != null) { m.vb.bind(); m.vb.draw(); }
            shader.clear();
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            rt.clearRenderState();
        }
        Lighting.setupLevel();
    }

    /** the client's sparkle for an egg (see Shot.eggFx): a glowing trail as it falls, and the ring of motes round its mark */
    public static void fx(Shot e) {
        var level = e.level();
        var rnd = e.getRandom();
        if (e.stage() == Shot.FALLING) {
            double w = 0.6 + e.size() * 2;
            for (int i = 0; i < 2; i++)
                level.addAlwaysVisibleParticle(ParticleTypes.GLOW, true, e.getX() + (rnd.nextDouble() - 0.5) * w, e.getY() + (rnd.nextDouble() - 0.5) * w,
                        e.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.05, 0);
            if (e.tickCount % 3 == 0)
                level.addAlwaysVisibleParticle(ParticleTypes.ITEM_SLIME, true, e.getX(), e.getY() - w * 0.4, e.getZ(), 0, -0.1, 0);
            if (e.tickCount % 2 == 0) {
                Vec3 l = e.landing();
                float k = e.fallen();
                double r = Mth.lerp(k, markerRadius(e), 2.5 + 3 * e.size());
                var dust = new DustParticleOptions(new Vector3f(Mth.lerp(k, 0.65f, 1f), Mth.lerp(k, 1f, 0.3f), 0.25f), 1.8f);
                int n = 14;
                double off = e.tickCount * 0.05;
                for (int i = 0; i < n; i++) {
                    double a = off + i * Math.PI * 2 / n;
                    double x = l.x + Math.cos(a) * r, z = l.z + Math.sin(a) * r;
                    level.addAlwaysVisibleParticle(dust, true, x, groundY(level, x, z, l.y) + 0.3, z, 0, 0.02, 0);
                }
            }
        } else if (e.stage() == Shot.HATCHING && e.tickCount % 3 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.GLOW, true, e.getX() + (rnd.nextDouble() - 0.5) * 2, e.getY() + 0.5 + rnd.nextDouble(),
                    e.getZ() + (rnd.nextDouble() - 0.5) * 2, 0, 0.08, 0);
        }
    }
}
