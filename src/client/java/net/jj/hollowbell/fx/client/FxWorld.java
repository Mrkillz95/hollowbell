package net.jj.hollowbell.fx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jj.hollowbell.fx.BigFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The big-moment kit, client half: everything flying, rolling and fading after a slam, drawn by the game itself
 * (no particles: these are big, last long and are seen from far off). Each piece moves 20 times a second and is
 * drawn smoothly between. There are caps on how many of each there can be, so a lot at once never slows the game.
 */
public final class FxWorld {
    private FxWorld() {}

    static final ResourceLocation PUFF = ResourceLocation.fromNamespaceAndPath(BigFx.NS, "textures/fx/puff.png");
    static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(BigFx.NS, "textures/fx/ring.png");
    static final ResourceLocation CRACK = ResourceLocation.fromNamespaceAndPath(BigFx.NS, "textures/fx/crack.png");

    static final int MAX_PUFFS = 1600, MAX_CHUNKS = 700, MAX_SPARKS = 900, MAX_RINGS = 48, MAX_CRACKS = 220, MAX_WAVES = 16, MAX_FLASHES = 24, MAX_EMITTERS = 40;

    static final List<Puff> puffs = new ArrayList<>();
    static final List<Chunk> chunks = new ArrayList<>();
    static final List<Spark> sparks = new ArrayList<>();
    static final List<Ring> rings = new ArrayList<>();
    static final List<Crack> cracks = new ArrayList<>();
    static final List<Wave> waves = new ArrayList<>();
    static final List<Flash> flashes = new ArrayList<>();
    static final List<Emitter> emitters = new ArrayList<>();
    private static Level level;
    private static MultiBufferSource.BufferSource buffers;

    // ------------------------------------------------------------------ the pieces

    /** a soft round puff turned to face you: dust, smoke, spray, foam, a coloured cloud */
    static final class Puff {
        double x, y, z, px, py, pz, vx, vy, vz;
        float size, grow, rot, rotV, r, g, b, a, drag, grav;
        int life, max, variant, light;
        boolean glow, flat;
        /** settles on the ground or water and stops falling there */
        double floor = -1e9;
    }

    /** a tumbling chunk of the ground, its own block's texture on it */
    static final class Chunk {
        double x, y, z, px, py, pz, vx, vy, vz;
        float half, ax, ay, az, ang, pang, spin;
        int life, max, bounces, light;
        TextureAtlasSprite top, side;
        int topTint = -1, sideTint = -1;
        double groundFallback;
        net.minecraft.sounds.SoundEvent hit;
        boolean resting;
    }

    /** a spark (a short bright streak) or an ember (a slow glowing speck) */
    static final class Spark {
        double x, y, z, px, py, pz, vx, vy, vz;
        float w, heat, wiggle;
        /** its own colour (ice, magic), or -1: hot (white-yellow to red as it cools) */
        float cr = -1f, cg, cb;
        int life, max;
        boolean ember;
    }

    /** a ring lying on the ground or the water, running out: a shockwave, a splash ring, the dark eye of a whirl */
    static final class Ring {
        double x, y, z;
        float r0, r1, width, r, g, b, a;
        int life, max, light;
        boolean water, glow, inward;
        float ease = 2f;
        float radius(float k) { float e = inward ? k : 1f - (float) Math.pow(1f - k, ease); return Mth.lerp(e, r0, r1); }
    }

    /** a crack in the ground, growing out from the middle, then fading */
    static final class Crack {
        double[] xs, zs;
        float[] ws;
        double y0;
        int life, max, grow, light;
    }

    /** a wave wall: rises, rolls out, pitches forward and breaks */
    static final class Wave {
        double x, y, z;
        float r0, r1, height, dir, arc;
        int life, max, colour, light;
        TextureAtlasSprite water;
        float lastBreak;
        long seed;
        float radius(float k) { return Mth.lerp(1f - (1f - k) * (1f - k), r0, r1); }
        /** how high the wall stands: up fast, held, then down as it breaks */
        float tall(float k) { return height * (k < 0.18f ? smooth(k / 0.18f) : k < 0.62f ? 1f : 1f - smooth((k - 0.62f) / 0.38f) * 0.92f); }
        /** how far the crest leans out over its foot (it curls, then pitches over) */
        float lean(float k) { return height * (0.25f + 0.9f * smooth((k - 0.35f) / 0.4f)); }
    }

    /** a burst of light */
    static final class Flash {
        double x, y, z;
        float size, r, g, b;
        int life, max;
    }

    /** something that keeps giving off pieces for a while (a whirl, embers, a cloud, a rolling dust ring) */
    interface Emitter { boolean tick(); }

    /** gives off pieces each tick for that many ticks (each gets how many ticks it has been going) */
    static Emitter emitter(int ticks, java.util.function.IntConsumer each) {
        int[] age = {0};
        return () -> { if (age[0] >= ticks) return false; each.accept(age[0]++); return true; };
    }

    // ------------------------------------------------------------------ adding

    static boolean room(List<?> l, int cap) { return l.size() < cap; }

    static Puff puff(double x, double y, double z, float size, int life) {
        if (!room(puffs, MAX_PUFFS)) return null;
        Puff p = new Puff();
        p.x = p.px = x; p.y = p.py = y; p.z = p.pz = z;
        p.size = size; p.max = Math.max(2, life); p.drag = 0.94f; p.r = p.g = p.b = 1f; p.a = 1f;
        p.light = LightTexture.FULL_BRIGHT;
        puffs.add(p);
        return p;
    }

    static Spark spark(double x, double y, double z, double vx, double vy, double vz, float w, int life, boolean ember) {
        if (!room(sparks, MAX_SPARKS)) return null;
        Spark s = new Spark();
        s.x = s.px = x; s.y = s.py = y; s.z = s.pz = z; s.vx = vx; s.vy = vy; s.vz = vz;
        s.w = w; s.max = Math.max(2, life); s.ember = ember; s.heat = 1f;
        sparks.add(s);
        return s;
    }

    static Ring ring(double x, double y, double z, float r0, float r1, float width, int life) {
        if (!room(rings, MAX_RINGS)) return null;
        Ring r = new Ring();
        r.x = x; r.y = y; r.z = z; r.r0 = r0; r.r1 = r1; r.width = width; r.max = Math.max(2, life);
        r.r = r.g = r.b = 1f; r.a = 0.8f; r.light = LightTexture.FULL_BRIGHT;
        rings.add(r);
        return r;
    }

    static Flash flash(double x, double y, double z, float size, int life) {
        if (!room(flashes, MAX_FLASHES)) return null;
        Flash f = new Flash();
        f.x = x; f.y = y; f.z = z; f.size = size; f.max = Math.max(2, life); f.r = 1f; f.g = 0.95f; f.b = 0.8f;
        flashes.add(f);
        return f;
    }

    static void emit(Emitter e) { if (room(emitters, MAX_EMITTERS)) emitters.add(e); }

    /** a chunk of whatever the ground is made of at (bx, bz) */
    static Chunk chunk(Level lv, BlockPos at, double x, double y, double z, float half, int life) {
        return chunk(lv, at, null, x, y, z, half, life);
    }

    /** a chunk of that block (or, with none given, of whatever the ground is made of at `at`) */
    static Chunk chunk(Level lv, BlockPos at, @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.state.BlockState given, double x, double y, double z, float half, int life) {
        if (!room(chunks, MAX_CHUNKS)) return null;
        var state = given != null ? given : lv.getBlockState(at);
        if (state.isAir() || !state.getFluidState().isEmpty()) state = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        Minecraft mc = Minecraft.getInstance();
        var shaper = mc.getBlockRenderer().getBlockModelShaper();
        Chunk c = new Chunk();
        c.side = shaper.getParticleIcon(state);
        c.top = c.side;
        c.hit = state.getSoundType().getHitSound();
        try {
            var quads = shaper.getBlockModel(state).getQuads(state, net.minecraft.core.Direction.UP, RandomSource.create(42));
            if (!quads.isEmpty()) {
                c.top = quads.get(0).getSprite();
                if (quads.get(0).isTinted()) c.topTint = mc.getBlockColors().getColor(state, lv, at, quads.get(0).getTintIndex());
            }
            if (state.is(net.minecraft.tags.BlockTags.LEAVES)) c.sideTint = c.topTint = mc.getBlockColors().getColor(state, lv, at, 0);
        } catch (Exception ignored) {}
        c.x = c.px = x; c.y = c.py = y; c.z = c.pz = z;
        c.half = half; c.max = life;
        c.groundFallback = y;
        c.light = lightAt(lv, x, at.getY(), z);
        chunks.add(c);
        return c;
    }

    static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    // ------------------------------------------------------------------ the ground

    /** the top of the ground (or water) at x z, or the fallback where this game has no ground loaded */
    static double ground(double x, double z, double fallback) {
        Level lv = Minecraft.getInstance().level;
        if (lv == null) return fallback;
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!lv.getChunkSource().hasChunk(ix >> 4, iz >> 4)) return fallback;
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
        if (h <= lv.getMinBuildHeight() + 1) return fallback;
        // (far above or below where it happened: a tree, a cave roof, an overhang; keep near the spot instead)
        if (Math.abs(h - fallback) > 40) return fallback;
        return h;
    }

    /** the top of the ground (or water) under a point high up, or y itself where there's none loaded within 300 blocks */
    static double groundBelow(double x, double z, double y) {
        Level lv = Minecraft.getInstance().level;
        if (lv == null) return y;
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!lv.getChunkSource().hasChunk(ix >> 4, iz >> 4)) return y;
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
        return h <= lv.getMinBuildHeight() + 1 || h > y + 4 || h < y - 300 ? y : h;
    }

    /** the light on the ground at x z (taken in the open air over it, so a spot under the surface isn't pitch black) */
    static int lightAt(Level lv, double x, double y, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        int at = Mth.floor(y) + 1;
        if (lv.getChunkSource().hasChunk(ix >> 4, iz >> 4)) {
            int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
            if (h > lv.getMinBuildHeight() + 1 && Math.abs(h - y) < 60) at = Math.max(at, h);
        }
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(lv, new BlockPos(ix, at, iz));
    }

    static boolean waterAt(double x, double y, double z) {
        Level lv = Minecraft.getInstance().level;
        if (lv == null) return false;
        BlockPos p = BlockPos.containing(x, y - 0.5, z);
        return lv.getFluidState(p).is(FluidTags.WATER);
    }

    // ------------------------------------------------------------------ moving

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        if (level == null || mc.isPaused()) return;
        for (int i = emitters.size() - 1; i >= 0; i--) {
            Emitter e = emitters.get(i);
            if (!e.tick()) emitters.remove(i);
        }
        tickPuffs();
        tickChunks();
        tickSparks();
        rings.removeIf(r -> ++r.life >= r.max);
        cracks.removeIf(c -> ++c.life >= c.max);
        flashes.removeIf(f -> ++f.life >= f.max);
        for (int i = waves.size() - 1; i >= 0; i--) if (tickWave(waves.get(i))) waves.remove(i);
    }

    private static void tickPuffs() {
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff p = puffs.get(i);
            if (++p.life >= p.max) { removeAt(puffs, i); continue; }
            p.px = p.x; p.py = p.y; p.pz = p.z;
            p.vy -= p.grav;
            p.vx *= p.drag; p.vy *= p.drag; p.vz *= p.drag;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            if (p.y < p.floor) { p.y = p.floor; if (p.vy < 0) p.vy = 0; p.vx *= 0.8; p.vz *= 0.8; }
            p.size += p.grow;
            p.rot += p.rotV;
        }
    }

    private static void tickChunks() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = chunks.size() - 1; i >= 0; i--) {
            Chunk c = chunks.get(i);
            if (++c.life >= c.max) { removeAt(chunks, i); continue; }
            c.px = c.x; c.py = c.y; c.pz = c.z; c.pang = c.ang;
            if (c.resting) continue;
            c.vy -= 0.08 + 0.02 * Math.min(4, c.half);
            c.vx *= 0.985; c.vy *= 0.985; c.vz *= 0.985;
            c.x += c.vx; c.y += c.vy; c.z += c.vz;
            c.ang += c.spin;
            double g = ground(c.x, c.z, c.groundFallback);
            if (c.y - c.half < g && c.vy < 0) {
                if (waterAt(c.x, g, c.z)) {
                    // into the water: a little splash, and gone
                    Puff p = puff(c.x, g + 0.2, c.z, c.half * 2.2f, 18);
                    if (p != null) { p.vy = 0.12 + c.half * 0.05; p.grav = 0.02f; p.grow = c.half * 0.08f; p.a = 0.8f; p.variant = i & 3; }
                    removeAt(chunks, i);
                    continue;
                }
                c.y = g + c.half;
                c.bounces++;
                if (c.bounces >= 3 || Math.abs(c.vy) < 0.18) {
                    c.resting = true; c.vx = c.vy = c.vz = 0; c.spin = 0;
                    c.max = Math.min(c.max, c.life + 50 + (i % 40));
                } else {
                    c.vy = -c.vy * 0.38; c.vx *= 0.55; c.vz *= 0.55; c.spin *= 0.6f;
                    // a puff of dust where it hits (the big ones)
                    if (c.bounces == 1 && c.half > 0.6f) {
                        Puff p = puff(c.x, g + c.half * 0.5, c.z, c.half * 2.5f, 30);
                        if (p != null) { p.vy = 0.05; p.grow = c.half * 0.06f; p.a = 0.55f; p.r = 0.62f; p.g = 0.56f; p.b = 0.48f; p.light = c.light; p.variant = i & 3; }
                        BigFxClient.landed(c);
                    }
                }
            }
        }
    }

    private static void tickSparks() {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            if (++s.life >= s.max) { removeAt(sparks, i); continue; }
            s.px = s.x; s.py = s.y; s.pz = s.z;
            if (s.ember) {
                // a slow drift up, swaying
                s.wiggle += 0.3f;
                s.vx = s.vx * 0.96 + Mth.sin(s.wiggle) * 0.006 * s.w;
                s.vz = s.vz * 0.96 + Mth.cos(s.wiggle * 0.7f) * 0.006 * s.w;
                s.vy = s.vy * 0.97 + 0.004;
            } else {
                s.vy -= 0.045;
                s.vx *= 0.97; s.vy *= 0.97; s.vz *= 0.97;
            }
            s.x += s.vx; s.y += s.vy; s.z += s.vz;
            s.heat = 1f - (float) s.life / s.max;
            if (!s.ember && s.vy < 0) {
                double g = ground(s.x, s.z, -1e9);
                if (s.y < g) { s.y = g + 0.05; s.vy = -s.vy * 0.3; s.vx *= 0.5; s.vz *= 0.5; }
            }
        }
    }

    /** true when it's done */
    private static boolean tickWave(Wave w) {
        w.life++;
        float k = (float) w.life / w.max;
        if (k >= 1f) return true;
        // foam along the crest, and spray thrown forward as it breaks
        RandomSource rnd = RandomSource.create(w.seed + w.life);
        float r = w.radius(k), h = w.tall(k), lean = w.lean(k);
        int n = Math.max(1, (int) (w.arc / (Math.PI * 2) * 26 * BigFxClient.quality));
        boolean breaking = k > 0.55f;
        for (int i = 0; i < n; i++) {
            float a = w.dir + (rnd.nextFloat() - 0.5f) * w.arc;
            double cx = w.x + Mth.cos(a) * (r + lean), cz = w.z + Mth.sin(a) * (r + lean);
            Puff p = puff(cx, w.y + h * 0.9f, cz, Math.max(1.2f, h * (breaking ? 0.6f : 0.4f)), breaking ? 34 : 20);
            if (p == null) break;
            p.variant = rnd.nextInt(4);
            p.a = 0.85f;
            p.r = 0.93f; p.g = 0.97f; p.b = 1f;
            p.light = w.light;
            double out = breaking ? 0.04 + 0.06 * rnd.nextFloat() : 0.02;
            p.vx = Mth.cos(a) * out * h; p.vz = Mth.sin(a) * out * h;
            p.vy = breaking ? 0.02 * h : 0.01 * h;
            p.grav = breaking ? 0.01f * Math.max(1f, h * 0.15f) : 0.004f;
            p.grow = h * 0.02f;
            p.floor = w.y;
            p.rotV = (rnd.nextFloat() - 0.5f) * 0.1f;
        }
        return false;
    }

    private static <T> void removeAt(List<T> l, int i) {
        int last = l.size() - 1;
        if (i != last) l.set(i, l.get(last));
        l.remove(last);
    }

    public static void clear() {
        puffs.clear(); chunks.clear(); sparks.clear(); rings.clear(); cracks.clear(); waves.clear(); flashes.clear(); emitters.clear();
    }

    /** how many of each there are now (for the autotest's log) */
    public static String counts() {
        String d = "(frames " + framesDrawn + ", puffs drawn " + puffsDrawn + ") ";
        framesDrawn = 0; puffsDrawn = 0;
        return d + " puffs " + puffs.size() + " chunks " + chunks.size() + " sparks " + sparks.size() + " rings " + rings.size() + " cracks " + cracks.size()
                + " waves " + waves.size() + " flashes " + flashes.size() + " emitters " + emitters.size();
    }

    public static int live() { return puffs.size() + chunks.size() + sparks.size() + rings.size() + cracks.size() + waves.size() + flashes.size(); }

    // ------------------------------------------------------------------ drawing

    private static double cx, cy, cz;
    private static float far;
    private static Vector3f left, up;

    /** scale toward the camera for what's past the far edge of the view (looks the same, isn't cut off) */
    private static float k(double rx, double ry, double rz) {
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        return d > far ? (float) (far / d) : 1f;
    }

    /** for the autotest's log: frames drawn and puffs drawn since it last asked */
    static int framesDrawn, puffsDrawn;

    public static void draw(Camera cam, float partial) {
        if (level == null || live() == 0) return;
        framesDrawn++;
        Minecraft mc = Minecraft.getInstance();
        var p = cam.getPosition();
        cx = p.x; cy = p.y; cz = p.z;
        far = mc.gameRenderer.getDepthFar() * 0.8f;
        left = cam.getLeftVector(); up = cam.getUpVector();
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
        // (pushed well out, so a slam far off still shows past where the ground fades)
        float fs0 = RenderSystem.getShaderFogStart(), fe0 = RenderSystem.getShaderFogEnd();
        boolean fogged = fe0 > 64f;
        if (fogged) { RenderSystem.setShaderFogStart(Math.max(fs0, 0.7f * Math.max(fe0, 560f))); RenderSystem.setShaderFogEnd(Math.max(fe0, 560f)); }
        try {
            if (!chunks.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
                for (Chunk c : chunks) drawChunk(vc, c, partial);
                buffers.endBatch();
            }
            if (!cracks.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(CRACK));
                for (Crack c : cracks) drawCrack(vc, c, partial);
                buffers.endBatch();
            }
            if (!waves.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
                for (Wave w : waves) drawWave(vc, w, partial);
                buffers.endBatch();
            }
            if (!rings.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(RING));
                for (Ring r : rings) if (!r.glow) drawRing(vc, r, partial);
                buffers.endBatch();
            }
            if (!puffs.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(PUFF));
                for (Puff q : puffs) if (!q.glow) drawPuff(vc, q, partial);
                buffers.endBatch();
                vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PUFF));
                for (Puff q : puffs) if (q.glow) drawPuff(vc, q, partial);
                buffers.endBatch();
            }
            if (!sparks.isEmpty() || !flashes.isEmpty() || !rings.isEmpty()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
                for (Spark s : sparks) drawSpark(vc, s, partial);
                for (Flash f : flashes) drawFlash(vc, f, partial);
                for (Ring r : rings) if (r.glow) drawGlowRing(vc, r, partial);
                buffers.endBatch();
            }
        } finally {
            if (fogged) { RenderSystem.setShaderFogStart(fs0); RenderSystem.setShaderFogEnd(fe0); }
        }
    }

    private static void vert(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a, float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    private static void drawPuff(VertexConsumer vc, Puff p, float partial) {
        float t = (p.life + partial) / p.max;
        // in quickly, out slowly
        float alpha = p.a * Math.min(1f, (p.life + partial) / 3f) * (1f - t) * (1f - t * 0.3f);
        if (alpha < 0.01f) return;
        puffsDrawn++;
        double rx = Mth.lerp(partial, p.px, p.x) - cx, ry = Mth.lerp(partial, p.py, p.y) - cy, rz = Mth.lerp(partial, p.pz, p.z) - cz;
        float k = k(rx, ry, rz);
        float s = (p.size + p.grow * partial) * 0.5f * k;
        float x = (float) rx * k, y = (float) ry * k, z = (float) rz * k;
        float rot = p.rot + p.rotV * partial, cs = Mth.cos(rot), sn = Mth.sin(rot);
        float ax = (left.x * cs + up.x * sn) * s, ay = (left.y * cs + up.y * sn) * s, az = (left.z * cs + up.z * sn) * s;
        float bx = (-left.x * sn + up.x * cs) * s, by = (-left.y * sn + up.y * cs) * s, bz = (-left.z * sn + up.z * cs) * s;
        float u0 = (p.variant & 1) * 0.5f, v0 = (p.variant >> 1) * 0.5f;
        vert(vc, x - ax - bx, y - ay - by, z - az - bz, p.r, p.g, p.b, alpha, u0, v0 + 0.5f, p.light, 0, 1, 0);
        vert(vc, x + ax - bx, y + ay - by, z + az - bz, p.r, p.g, p.b, alpha, u0 + 0.5f, v0 + 0.5f, p.light, 0, 1, 0);
        vert(vc, x + ax + bx, y + ay + by, z + az + bz, p.r, p.g, p.b, alpha, u0 + 0.5f, v0, p.light, 0, 1, 0);
        vert(vc, x - ax + bx, y - ay + by, z - az + bz, p.r, p.g, p.b, alpha, u0, v0, p.light, 0, 1, 0);
    }

    private static final float[][] FACES = {
            // normal, then the 4 corners (unit cube, -1..1)
            {0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
            {0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
            {0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1},
            {0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1},
            {1, 0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1},
            {-1, 0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1}};

    private static void drawChunk(VertexConsumer vc, Chunk c, float partial) {
        double rx = Mth.lerp(partial, c.px, c.x) - cx, ry = Mth.lerp(partial, c.py, c.y) - cy, rz = Mth.lerp(partial, c.pz, c.z) - cz;
        float k = k(rx, ry, rz);
        float left0 = c.max - c.life - partial;
        float h = c.half * k * Math.min(1f, left0 / 15f) * Math.min(1f, (c.life + partial) / 2f);
        if (h <= 0.01f) return;
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateAxis(Mth.lerp(partial, c.pang, c.ang), c.ax, c.ay, c.az);
        // (a chunk is a little flat, like a slab of turf)
        Vector3f sc = new Vector3f(h, h * 0.7f, h * 0.85f);
        Vector3f corner = new Vector3f(), n = new Vector3f();
        float x = (float) rx * k, y = (float) ry * k, z = (float) rz * k;
        for (int f = 0; f < 6; f++) {
            float[] F = FACES[f];
            TextureAtlasSprite sp = f == 0 ? c.top : c.side;
            int tint = f == 0 ? c.topTint : c.sideTint;
            float tr = tint == -1 ? 1f : ((tint >> 16) & 255) / 255f, tg = tint == -1 ? 1f : ((tint >> 8) & 255) / 255f, tb = tint == -1 ? 1f : (tint & 255) / 255f;
            q.transform(n.set(F[0], F[1], F[2]));
            float[] us = {sp.getU0(), sp.getU0(), sp.getU1(), sp.getU1()}, vs = {sp.getV0(), sp.getV1(), sp.getV1(), sp.getV0()};
            for (int i = 0; i < 4; i++) {
                corner.set(F[3 + i * 3] * sc.x, F[4 + i * 3] * sc.y, F[5 + i * 3] * sc.z);
                q.transform(corner);
                vert(vc, x + corner.x, y + corner.y, z + corner.z, tr, tg, tb, 1f, us[i], vs[i], c.light, n.x, n.y, n.z);
            }
        }
    }

    private static void drawSpark(VertexConsumer vc, Spark s, float partial) {
        double x1 = Mth.lerp(partial, s.px, s.x) - cx, y1 = Mth.lerp(partial, s.py, s.y) - cy, z1 = Mth.lerp(partial, s.pz, s.z) - cz;
        float k = k(x1, y1, z1);
        float heat = s.heat;
        // white-yellow hot, through orange, to a dull red
        float r = 1f, g = Mth.clamp(0.25f + heat * 0.85f, 0f, 1f), b = Mth.clamp(heat * heat * 0.7f, 0f, 1f);
        if (s.cr >= 0f) { r = Mth.lerp(heat * 0.5f, s.cr, 1f); g = Mth.lerp(heat * 0.5f, s.cg, 1f); b = Mth.lerp(heat * 0.5f, s.cb, 1f); }
        float a = Mth.clamp(heat * 1.4f, 0f, 1f);
        if (s.ember) { float fl = 0.6f + 0.4f * Mth.sin((s.life + partial) * 1.7f + s.wiggle * 3f); a *= fl; g *= 0.75f; }
        // a streak along the way it's going (an ember is just a speck)
        double tx = s.ember ? 0 : -s.vx * 2.5, ty = s.ember ? 0 : -s.vy * 2.5, tz = s.ember ? 0 : -s.vz * 2.5;
        float w = s.w * k * (s.ember ? 0.7f : 0.45f) * (0.5f + 0.5f * heat);
        float x = (float) x1 * k, y = (float) y1 * k, z = (float) z1 * k;
        float ex = (float) tx * k, ey = (float) ty * k, ez = (float) tz * k;
        // across the streak, facing the camera
        Vector3f along = new Vector3f(ex, ey, ez);
        Vector3f across;
        if (along.lengthSquared() < 1e-6f) { across = new Vector3f(left).mul(w); along.set(up).mul(w); x -= along.x * 0.5f; y -= along.y * 0.5f; z -= along.z * 0.5f; ex = along.x; ey = along.y; ez = along.z; }
        else { across = new Vector3f(along).cross(x, y, z); if (across.lengthSquared() < 1e-8f) across.set(left); across.normalize().mul(w); }
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1f : -1f;
            vc.addVertex(x - across.x * sg, y - across.y * sg, z - across.z * sg).setColor(r, g, b, a);
            vc.addVertex(x + across.x * sg, y + across.y * sg, z + across.z * sg).setColor(r, g, b, a);
            vc.addVertex(x + ex + across.x * sg, y + ey + across.y * sg, z + ez + across.z * sg).setColor(r, g * 0.6f, b * 0.3f, 0f);
            vc.addVertex(x + ex - across.x * sg, y + ey - across.y * sg, z + ez - across.z * sg).setColor(r, g * 0.6f, b * 0.3f, 0f);
        }
    }

    private static void drawFlash(VertexConsumer vc, Flash f, float partial) {
        float t = (f.life + partial) / f.max;
        float a = (1f - t) * (1f - t);
        if (a < 0.01f) return;
        double rx = f.x - cx, ry = f.y - cy, rz = f.z - cz;
        float k = k(rx, ry, rz);
        float x = (float) rx * k, y = (float) ry * k, z = (float) rz * k;
        float s = f.size * (0.6f + 0.6f * t) * k;
        // a soft disc: bright in the middle, nothing at the edge
        int n = 16;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * s, s0 = Mth.sin(a0) * s, c1 = Mth.cos(a1) * s, s1 = Mth.sin(a1) * s;
            // (both ways round: this kind of drawing hides the back of a quad)
            for (int side = 0; side < 2; side++) {
                float ca = side == 0 ? c0 : c1, sa = side == 0 ? s0 : s1, cb = side == 0 ? c1 : c0, sb = side == 0 ? s1 : s0;
                vc.addVertex(x, y, z).setColor(f.r, f.g, f.b, a);
                vc.addVertex(x + left.x * ca + up.x * sa, y + left.y * ca + up.y * sa, z + left.z * ca + up.z * sa).setColor(f.r, f.g * 0.8f, f.b * 0.5f, 0f);
                vc.addVertex(x + left.x * cb + up.x * sb, y + left.y * cb + up.y * sb, z + left.z * cb + up.z * sb).setColor(f.r, f.g * 0.8f, f.b * 0.5f, 0f);
                vc.addVertex(x, y, z).setColor(f.r, f.g, f.b, a);
            }
        }
    }

    private static void drawRing(VertexConsumer vc, Ring r, float partial) {
        float t = Mth.clamp((r.life + partial) / r.max, 0f, 1f);
        float rad = r.radius(t);
        float alpha = r.a * Math.min(1f, t * 10f) * (1f - t) * (1f - t * 0.5f);
        if (alpha < 0.01f || rad <= 0.05f) return;
        double rx0 = r.x - cx, ry0 = r.y - cy, rz0 = r.z - cz;
        float k = k(rx0, ry0, rz0);
        float w = r.width * (0.6f + 0.6f * t);
        float in = Math.max(0f, rad - w * 0.8f), out = rad + w * 0.2f;
        int n = Mth.clamp((int) (rad * 1.2f), 16, 64);
        float lift = 0.15f + (float) Math.sqrt(rx0 * rx0 + rz0 * rz0) * 0.002f;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            double gi0 = r.water ? r.y : ground(r.x + c0 * in, r.z + s0 * in, r.y), go0 = r.water ? r.y : ground(r.x + c0 * out, r.z + s0 * out, r.y);
            double gi1 = r.water ? r.y : ground(r.x + c1 * in, r.z + s1 * in, r.y), go1 = r.water ? r.y : ground(r.x + c1 * out, r.z + s1 * out, r.y);
            float u0 = i / (float) n * Math.max(1, (int) (rad / 6f)), u1 = (i + 1) / (float) n * Math.max(1, (int) (rad / 6f));
            vert(vc, (float) (rx0 + c0 * in) * k, (float) (gi0 + lift - cy) * k, (float) (rz0 + s0 * in) * k, r.r, r.g, r.b, alpha, u0, r.inward ? 1f : 0f, r.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c0 * out) * k, (float) (go0 + lift - cy) * k, (float) (rz0 + s0 * out) * k, r.r, r.g, r.b, alpha, u0, r.inward ? 0f : 1f, r.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * out) * k, (float) (go1 + lift - cy) * k, (float) (rz0 + s1 * out) * k, r.r, r.g, r.b, alpha, u1, r.inward ? 0f : 1f, r.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * in) * k, (float) (gi1 + lift - cy) * k, (float) (rz0 + s1 * in) * k, r.r, r.g, r.b, alpha, u1, r.inward ? 1f : 0f, r.light, 0, 1, 0);
        }
    }

    /** a ring of light (a flash going out along the ground) */
    private static void drawGlowRing(VertexConsumer vc, Ring r, float partial) {
        float t = Mth.clamp((r.life + partial) / r.max, 0f, 1f);
        float rad = r.radius(t);
        float alpha = r.a * (1f - t);
        if (alpha < 0.01f) return;
        double rx0 = r.x - cx, ry0 = r.y - cy, rz0 = r.z - cz;
        float k = k(rx0, ry0, rz0);
        float in = Math.max(0f, rad - r.width), out = rad;
        int n = Mth.clamp((int) (rad * 1.2f), 16, 64);
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float y = (float) ry0 + 0.3f;
            for (int side = 0; side < 2; side++) {
                float ca = side == 0 ? c0 : c1, sa = side == 0 ? s0 : s1, cb = side == 0 ? c1 : c0, sb = side == 0 ? s1 : s0;
                vc.addVertex((float) (rx0 + ca * in) * k, y * k, (float) (rz0 + sa * in) * k).setColor(r.r, r.g, r.b, 0f);
                vc.addVertex((float) (rx0 + ca * out) * k, y * k, (float) (rz0 + sa * out) * k).setColor(r.r, r.g, r.b, alpha);
                vc.addVertex((float) (rx0 + cb * out) * k, y * k, (float) (rz0 + sb * out) * k).setColor(r.r, r.g, r.b, alpha);
                vc.addVertex((float) (rx0 + cb * in) * k, y * k, (float) (rz0 + sb * in) * k).setColor(r.r, r.g, r.b, 0f);
            }
        }
    }

    private static void drawCrack(VertexConsumer vc, Crack c, float partial) {
        float t = c.life + partial;
        float fade = Math.min(1f, (c.max - t) / 40f);
        if (fade <= 0f) return;
        // grows out from the middle over its first ticks
        int shown = Math.min(c.xs.length, 1 + (int) (c.xs.length * Math.min(1f, t / Math.max(1, c.grow))));
        double rx0 = c.xs[0] - cx, rz0 = c.zs[0] - cz;
        float k = k(rx0, c.y0 - cy, rz0);
        float lift = 0.08f + (float) Math.sqrt(rx0 * rx0 + rz0 * rz0) * 0.0015f;
        float v = 0;
        for (int i = 0; i + 1 < shown; i++) {
            double x0 = c.xs[i], z0 = c.zs[i], x1 = c.xs[i + 1], z1 = c.zs[i + 1];
            double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-4) continue;
            double nx = -dz / len, nz = dx / len;
            float w0 = c.ws[i] * 0.5f, w1 = c.ws[i + 1] * 0.5f;
            double y0 = ground(x0, z0, c.y0) + lift, y1 = ground(x1, z1, c.y0) + lift;
            float v1 = v + (float) (len / Math.max(0.5f, c.ws[i] * 2f));
            vert(vc, (float) (x0 - nx * w0 - cx) * k, (float) (y0 - cy) * k, (float) (z0 - nz * w0 - cz) * k, 1, 1, 1, fade, 0, v, c.light, 0, 1, 0);
            vert(vc, (float) (x0 + nx * w0 - cx) * k, (float) (y0 - cy) * k, (float) (z0 + nz * w0 - cz) * k, 1, 1, 1, fade, 1, v, c.light, 0, 1, 0);
            vert(vc, (float) (x1 + nx * w1 - cx) * k, (float) (y1 - cy) * k, (float) (z1 + nz * w1 - cz) * k, 1, 1, 1, fade, 1, v1, c.light, 0, 1, 0);
            vert(vc, (float) (x1 - nx * w1 - cx) * k, (float) (y1 - cy) * k, (float) (z1 - nz * w1 - cz) * k, 1, 1, 1, fade, 0, v1, c.light, 0, 1, 0);
            v = v1;
        }
    }

    private static void drawWave(VertexConsumer vc, Wave w, float partial) {
        float k0 = Mth.clamp((w.life + partial) / w.max, 0f, 1f);
        float r = w.radius(k0), h = w.tall(k0), lean = w.lean(k0);
        if (h < 0.2f) return;
        double rx0 = w.x - cx, rz0 = w.z - cz;
        float k = k(rx0, w.y - cy, rz0);
        TextureAtlasSprite sp = w.water;
        float cr = ((w.colour >> 16) & 255) / 255f, cg = ((w.colour >> 8) & 255) / 255f, cb = (w.colour & 255) / 255f;
        float fade = Math.min(1f, (1f - k0) * 5f) * Math.min(1f, k0 * 12f);
        int n = Mth.clamp((int) (r * w.arc * 0.25f), 8, 72);
        float back = h * 2.6f;
        float y0 = (float) (w.y - cy) - 0.25f;
        for (int i = 0; i < n; i++) {
            float f0 = i / (float) n, f1 = (i + 1) / (float) n;
            float a0 = w.dir + (f0 - 0.5f) * w.arc, a1 = w.dir + (f1 - 0.5f) * w.arc;
            // (an arc thins away to nothing at its two ends)
            float e0 = w.arc > 6.2f ? 1f : Mth.sin(f0 * Mth.PI), e1 = w.arc > 6.2f ? 1f : Mth.sin(f1 * Mth.PI);
            float h0 = h * (0.35f + 0.65f * e0), h1 = h * (0.35f + 0.65f * e1);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float rb = Math.max(0f, r - back);
            float l0 = lean * e0 * 0.8f, l1 = lean * e1 * 0.8f;
            float u0 = Mth.lerp(f0 * 4 % 1f, sp.getU0(), sp.getU1()), u1 = Mth.lerp(Math.min(1f, (f0 * 4 % 1f) + 0.25f), sp.getU0(), sp.getU1());
            float vA = sp.getV0(), vB = Mth.lerp(0.5f, sp.getV0(), sp.getV1()), vC = sp.getV1();
            float al = 0.9f * fade, ac = 0.95f * fade, ab = 0.55f * fade;
            float tr = lighten(cr, 0.3f), tg = lighten(cg, 0.3f), tb = lighten(cb, 0.3f);
            // the back slope: from the flat water up to the crest (a swell of water, not a sheet)
            vert(vc, (float) (rx0 + c0 * rb) * k, y0 * k, (float) (rz0 + s0 * rb) * k, cr, cg, cb, 0f, u0, vA, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * rb) * k, y0 * k, (float) (rz0 + s1 * rb) * k, cr, cg, cb, 0f, u1, vA, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * (r + l1)) * k, (y0 + h1) * k, (float) (rz0 + s1 * (r + l1)) * k, tr, tg, tb, ab + 0.4f * fade, u1, vB, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c0 * (r + l0)) * k, (y0 + h0) * k, (float) (rz0 + s0 * (r + l0)) * k, tr, tg, tb, ab + 0.4f * fade, u0, vB, w.light, 0, 1, 0);
            // the face: from just under the crest, curling over, down to its foot: deep water, darker at the foot
            float fr0 = r + l0 * 0.15f, fr1 = r + l1 * 0.15f;
            float m0 = h0 * 0.82f, m1 = h1 * 0.82f, ml0 = l0 * 0.85f, ml1 = l1 * 0.85f;
            vert(vc, (float) (rx0 + c0 * (r + ml0)) * k, (y0 + m0) * k, (float) (rz0 + s0 * (r + ml0)) * k, tr, tg, tb, ac, u0, vB, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * (r + ml1)) * k, (y0 + m1) * k, (float) (rz0 + s1 * (r + ml1)) * k, tr, tg, tb, ac, u1, vB, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * fr1) * k, y0 * k, (float) (rz0 + s1 * fr1) * k, cr * 0.55f, cg * 0.65f, cb * 0.75f, al, u1, vC, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c0 * fr0) * k, y0 * k, (float) (rz0 + s0 * fr0) * k, cr * 0.55f, cg * 0.65f, cb * 0.75f, al, u0, vC, w.light, 0, 1, 0);
            // the foam on the lip: white, from the crest down the top of the face
            vert(vc, (float) (rx0 + c0 * (r + l0)) * k, (y0 + h0) * k, (float) (rz0 + s0 * (r + l0)) * k, 0.96f, 0.98f, 1f, ac, u0, vA, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * (r + l1)) * k, (y0 + h1) * k, (float) (rz0 + s1 * (r + l1)) * k, 0.96f, 0.98f, 1f, ac, u1, vA, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c1 * (r + ml1)) * k, (y0 + m1) * k, (float) (rz0 + s1 * (r + ml1)) * k, lighten(cr, 0.7f), lighten(cg, 0.7f), lighten(cb, 0.7f), ac, u1, vB, w.light, 0, 1, 0);
            vert(vc, (float) (rx0 + c0 * (r + ml0)) * k, (y0 + m0) * k, (float) (rz0 + s0 * (r + ml0)) * k, lighten(cr, 0.7f), lighten(cg, 0.7f), lighten(cb, 0.7f), ac, u0, vB, w.light, 0, 1, 0);
        }
    }

    private static float lighten(float c, float by) { return c + (1f - c) * by; }
}
