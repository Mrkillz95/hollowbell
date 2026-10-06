package net.jj.hollowbell.fx.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.jj.hollowbell.fx.BigFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * The big-moment kit on the player's side: takes each message from the server and turns it into the pieces in
 * {@link FxWorld} (what a slam, a splash, a blast... is made of), plus the camera shake, the white flash on the
 * screen and the sound, which comes late from far off (sound is slow: about 17 blocks a tick).
 *
 * Fewer pieces far away when the mod's "simple far away" (detail) setting is on, none at all at effects amount 0;
 * the shake follows the mod's screenShake setting and the sound its volume.
 */
public final class BigFxClient {
    private BigFxClient() {}

    private static BooleanSupplier shakeOn = () -> true, simpleFar = () -> true;
    private static DoubleSupplier volume = () -> 1.0, amount = () -> 1.0;
    /** how much of each effect to show right now (1 = all of it): smaller far away, when busy, or turned down */
    static float quality = 1f;

    /**
     * Once, in the mod's client init. shake: the mod's screenShake setting; volume: its soundVolume (0-2);
     * simpleFar: its "simpler far away" (detail) setting; amount: its bigEffects setting (0 = none, 1 = normal, 2 = more).
     */
    public static void init(BooleanSupplier shake, DoubleSupplier vol, BooleanSupplier simpler, DoubleSupplier amt) {
        shakeOn = shake; volume = vol; simpleFar = simpler; amount = amt;
        ClientPlayNetworking.registerGlobalReceiver(BigFx.Payload.TYPE, (p, ctx) -> ctx.client().execute(() -> play(p)));
        ClientTickEvents.END_CLIENT_TICK.register(BigFxClient::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> FxWorld.draw(ctx.camera(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((g, t) -> hud(g));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clear());
    }

    public static void clear() {
        FxWorld.clear();
        sounds.clear();
        shakes.clear();
        shake = lastShake = 0f;
        screenFlash = 0f;
    }

    // ------------------------------------------------------------------ each tick

    private static void tick(Minecraft mc) {
        if (mc.level == null) { if (FxWorld.live() > 0 || !sounds.isEmpty()) clear(); return; }
        if (mc.isPaused()) return;
        FxWorld.tick();
        // the shake: what has reached us, then dying away
        lastShake = shake;
        for (int i = shakes.size() - 1; i >= 0; i--) {
            float[] s = shakes.get(i);
            if (--s[0] <= 0) { shake = Math.min(2.4f, shake + s[1]); shakes.remove(i); }
        }
        shake *= shake > 1.2f ? 0.93f : 0.84f;
        if (shake < 0.002f) shake = 0f;
        screenFlash *= 0.72f;
        if (screenFlash < 0.01f) screenFlash = 0f;
        for (int i = sounds.size() - 1; i >= 0; i--) {
            Late l = sounds.get(i);
            if (--l.in > 0) continue;
            sounds.remove(i);
            mc.getSoundManager().play(new SimpleSoundInstance(l.ev.getLocation(), SoundSource.HOSTILE, Math.min(1f, l.vol), l.pitch,
                    RandomSource.create(l.seed), false, 0, SoundInstance.Attenuation.NONE, l.x, l.y, l.z, false));
        }
        landedThisTick = 0;
    }

    // ------------------------------------------------------------------ shake

    private static final List<float[]> shakes = new ArrayList<>();
    private static float shake, lastShake;

    /** the ground shakes this hard (about 0.2 a footfall, 1 a slam, 2 the most) where you are, after it gets to you */
    static void shake(double dist, float power, double reach) { shake(dist, power, reach, 0); }

    static void shake(double dist, float power, double reach, int after) {
        if (!shakeOn.getAsBoolean() || dist > reach || shakes.size() > 64) return;
        float near = (float) (1.0 - dist / reach);
        // (through the ground it travels faster than the sound: about 40 blocks a tick)
        shakes.add(new float[]{1 + after + (int) (dist / 40), power * near * near});
    }

    /** {sideways, up, forward} for the camera this frame (the mod's camera mixin adds it), or null when still */
    public static float[] cameraShake(float partial) {
        if (shake <= 0f || !shakeOn.getAsBoolean()) return null;
        float a = Mth.lerp(partial, lastShake, shake);
        if (a <= 0.002f) return null;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        float x = (Mth.sin(t * 2.9f) * 0.6f + Mth.sin(t * 7.7f) * 0.4f) * a * 0.32f;
        float y = (Mth.sin(t * 4.1f + 1.3f) * 0.6f + Mth.sin(t * 9.3f) * 0.4f) * a * 0.26f;
        float z = Mth.sin(t * 5.3f + 2.2f) * a * 0.12f;
        return new float[]{x, y, z};
    }

    // ------------------------------------------------------------------ sound

    private static final class Late { SoundEvent ev; double x, y, z; float vol, pitch; int in; long seed; }
    private static final List<Late> sounds = new ArrayList<>();

    /** a sound from there, quieter and later the further off it is; reach: where it can't be heard any more */
    static void sound(SoundEvent ev, double x, double y, double z, float vol, float pitch, int after, double reach, long seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || sounds.size() > 64) return;
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z));
        if (d > reach) return;
        float fall = (float) (1.0 - d / reach);
        float v = vol * (0.15f + 0.85f * fall * fall) * (float) Mth.clamp(volume.getAsDouble(), 0.0, 2.0);
        if (v < 0.02f) return;
        Late l = new Late();
        l.ev = ev; l.x = x; l.y = y; l.z = z; l.vol = v; l.pitch = Mth.clamp(pitch, 0.5f, 2f); l.seed = seed;
        l.in = 1 + after + (int) (d / 17.0);
        sounds.add(l);
    }

    private static int landedThisTick;

    /** a big chunk hit the ground near you: a knock */
    static void landed(FxWorld.Chunk c) {
        if (c.hit == null || ++landedThisTick > 3) return;
        sound(c.hit, c.x, c.y, c.z, Math.min(1f, 0.25f + c.half * 0.3f), 0.55f + 0.3f / (1f + c.half), 0, 40 + c.half * 30, (long) (c.x * 31 + c.z));
    }

    // ------------------------------------------------------------------ the white flash on the screen

    private static float screenFlash;

    private static void hud(GuiGraphics g) {
        if (screenFlash <= 0.01f) return;
        int a = Mth.clamp((int) (screenFlash * 255), 0, 220);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), a << 24 | 0xFFF8E8);
    }

    // ------------------------------------------------------------------ what each kind is made of

    /** where it happened: the ground (or water) there and what it's made of */
    static final class Spot {
        double x, y, z;
        boolean water;
        BlockState state;
        BlockPos pos;
        float dr, dg, db;
        int light, waterColour;
    }

    static Spot spot(Level lv, double x, double y, double z) {
        Spot s = new Spot();
        s.x = x; s.z = z;
        double g = FxWorld.ground(x, z, y);
        s.y = Math.abs(g - y) < 24 ? g : y;
        BlockPos top = BlockPos.containing(x, s.y - 0.5, z);
        BlockState st = lv.getBlockState(top);
        if (st.isAir()) { top = top.below(); st = lv.getBlockState(top); }
        s.water = st.getFluidState().is(FluidTags.WATER);
        s.state = st.isAir() ? Blocks.DIRT.defaultBlockState() : st;
        s.pos = top;
        // (dust is the soil under the grass, not the grass)
        BlockState soil = st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.MYCELIUM) || st.is(Blocks.PODZOL) || st.is(net.minecraft.tags.BlockTags.LEAVES) ? Blocks.DIRT.defaultBlockState() : st;
        int col = s.water ? 0 : soil.getMapColor(lv, top).col;
        if (col == 0) col = 0x8a7a66;
        // dust is the ground's colour, paler and greyer
        s.dr = Mth.lerp(0.45f, ((col >> 16) & 255) / 255f, 0.72f);
        s.dg = Mth.lerp(0.45f, ((col >> 8) & 255) / 255f, 0.68f);
        s.db = Mth.lerp(0.45f, (col & 255) / 255f, 0.62f);
        s.light = FxWorld.lightAt(lv, x, s.y, z);
        s.waterColour = BiomeColors.getAverageWaterColor(lv, top);
        return s;
    }

    private static int n(float want) { return Math.max(0, Math.round(want * quality)); }

    private static float rnd(RandomSource r, float a, float b) { return a + r.nextFloat() * (b - a); }

    static void play(BigFx.Payload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level lv = mc.level;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(new Vec3(p.x(), p.y(), p.z()));
        float s = Math.min(p.size(), 600f);
        float amt = (float) Mth.clamp(amount.getAsDouble(), 0.0, 2.0);
        float q = amt;
        if (simpleFar.getAsBoolean()) {
            double near = 64 + s * 3;
            if (d > near) q *= (float) Math.max(0.2, Math.pow(near / d, 0.8));
        }
        // (busy: fewer of each)
        q *= Mth.clamp(1f - FxWorld.live() / 3200f, 0.3f, 1f);
        quality = q;
        boolean show = amt > 0f && d < BigFx.RANGE + s * 2 + 60;
        boolean near = d < 160 + s * 4 || !simpleFar.getAsBoolean();
        RandomSource r = RandomSource.create(p.seed());
        Spot at = spot(lv, p.x(), p.y(), p.z());
        float hx = p.dx(), hz = p.dz(), hl = Mth.sqrt(hx * hx + hz * hz);
        if (hl > 1e-4f) { hx /= hl; hz /= hl; }
        switch (p.kind()) {
            case BigFx.SLAM -> slam(lv, at, s, hx, hz, d, r, show, near, true);
            case BigFx.STOMP -> slam(lv, at, s, hx, hz, d, r, show, near, false);
            case BigFx.DEBRIS -> {
                if (show && near) {
                    // (extra: what they are made of, a block state id: ship timber, scrap iron... 0 = the ground there)
                    given = p.extra() > 0 ? net.minecraft.world.level.block.Block.stateById(p.extra()) : null;
                    debris(lv, at, s, hx, hz, r, 8 + s * 0.8f, 1f);
                    given = null;
                }
            }
            case BigFx.DUST -> { if (show) plume(at, s, hx, hz, r, 1f); }
            case BigFx.DUST_RING -> { if (show) dustRing(at, s * 0.2f, s, 20 + (int) (s * 0.2f), r, 1f); }
            case BigFx.SHOCKWAVE -> {
                if (show && at.water) {
                    // (on water the shock is a wave: a ring of water rolling out at the same pace, spray in the middle)
                    waveTicks = p.extra() > 0 ? p.extra() + 6 : 0;
                    wave(lv, at, s, 0f, Mth.TWO_PI, d, r, true, p.seed());
                    waveTicks = 0;
                    spray(at, s * 0.3f, 0, 1, 0, r, 0.7f);
                } else if (show) {
                    int ticks = p.extra() > 0 ? p.extra() : 16 + (int) (s * 0.12f);
                    FxWorld.Ring g = shockRing(at, s * 0.08f, s, Math.max(1.5f, s * 0.14f), ticks);
                    if (g != null && p.extra() > 0) g.ease = 1f;
                    if (near) cracks(at, s * 0.45f, r, Mth.clamp(4 + (int) (s * 0.1f), 4, 14));
                    dustRing(at, s * 0.08f, s, ticks, r, 0.6f);
                }
                shake(d, 0.3f + s * 0.012f, 100 + s * 6);
                sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, at.x, at.y, at.z, 0.9f, 0.5f, 0, 160 + s * 6, p.seed());
            }
            case BigFx.SPRAY -> { if (show) spray(at, s, p.dx(), p.dy(), p.dz(), r, 1f); sound(SoundEvents.GENERIC_SPLASH, at.x, at.y, at.z, 0.8f, 0.6f, 0, 80 + s * 8, p.seed()); }
            case BigFx.SPLASH -> splash(lv, at, s, d, r, show);
            case BigFx.WAVE -> {
                // (extra: the arc in degrees, plus 1000 x the ticks it takes to roll out, to match a wave the server runs)
                int arcDeg = p.extra() % 1000, ticks = p.extra() / 1000;
                waveTicks = ticks;
                wave(lv, at, s, hl > 1e-4f ? (float) Math.atan2(hz, hx) : 0f, hl > 1e-4f ? (arcDeg > 0 ? arcDeg : 120) * Mth.DEG_TO_RAD : Mth.TWO_PI, d, r, show, p.seed());
                waveTicks = 0;
            }
            case BigFx.SPARKS -> {
                int before = FxWorld.sparks.size();
                if (show) sparks(p.x(), p.y(), p.z(), s, p.dx(), p.dy(), p.dz(), r, 1f);
                if (p.extra() != 0) colour(before, p.extra());
                if (p.extra() == 0) metal(p.x(), p.y(), p.z(), s, p.seed());
                else sound(SoundEvents.GLASS_BREAK, p.x(), p.y(), p.z(), 0.8f, 0.6f, 0, 80 + s * 8, p.seed());
            }
            case BigFx.EMBERS -> { if (show) embers(p.x(), p.y(), p.z(), s, p.extra() > 0 ? p.extra() : 16, r); }
            case BigFx.FLASH -> {
                int fl = FxWorld.flashes.size(), rg = FxWorld.rings.size();
                flash(p.x(), p.y(), p.z(), s, d, show, at, true);
                if (p.extra() != 0) {
                    float cr = ((p.extra() >> 16) & 255) / 255f, cg = ((p.extra() >> 8) & 255) / 255f, cb = (p.extra() & 255) / 255f;
                    for (int i = fl; i < FxWorld.flashes.size(); i++) { var f = FxWorld.flashes.get(i); f.r = cr; f.g = cg; f.b = cb; }
                    for (int i = rg; i < FxWorld.rings.size(); i++) { var g = FxWorld.rings.get(i); g.r = cr; g.g = cg; g.b = cb; }
                }
            }
            case BigFx.EXPLOSION -> explosion(lv, at, p.x(), p.y(), p.z(), s, d, r, show, near, p.seed());
            case BigFx.SMOKE -> { if (show) smoke(p.x(), p.y(), p.z(), s, hx, hz, at.light, r, 1f); }
            case BigFx.TRAIL -> { if (show) trail(lv, at, s, hx, hz, r, near); }
            case BigFx.SHAKE -> {
                shake(d, 0.2f + s * 0.02f, 90 + s * 8);
                sound(SoundEvents.MACE_SMASH_GROUND, at.x, at.y, at.z, 0.7f, 0.5f, 0, 100 + s * 6, p.seed());
            }
            case BigFx.WHIRL -> whirl(at, s, p.extra() > 0 ? p.extra() : 80, r, show, p.seed());
            case BigFx.CLOUD -> { if (show) cloud(at, s, p.extra() != 0 ? p.extra() : 0x7FBF3F, r); }
            default -> {}
        }
    }

    /** a slam (or, lighter, a stomp): chunks, dust, a ring and cracks, shake, a boom. On water, a splash */
    private static void slam(Level lv, Spot at, float s, float hx, float hz, double d, RandomSource r, boolean show, boolean near, boolean heavy) {
        long seed = r.nextLong();
        if (at.water) {
            splash(lv, at, heavy ? s : s * 0.6f, d, r, show);
            if (heavy && s > 10 && show) {
                Spot w = at;
                wave(lv, w, s * 1.6f, 0f, Mth.TWO_PI, d, r, true, seed);
            }
            return;
        }
        if (show) {
            if (near) debris(lv, at, s, hx, hz, r, heavy ? 10 + s * 0.9f : 4 + s * 0.35f, heavy ? 1f : 0.55f);
            if (heavy) plume(at, s, hx, hz, r, 1f);
            dustRing(at, s * (heavy ? 0.25f : 0.15f), s * (heavy ? 1.7f : 1.15f), heavy ? 22 + (int) (s * 0.15f) : 14 + (int) (s * 0.1f), r, heavy ? 1f : 0.6f);
            shockRing(at, s * 0.1f, s * (heavy ? 2.0f : 1.3f), Math.max(1.2f, s * (heavy ? 0.32f : 0.22f)), heavy ? 14 + (int) (s * 0.1f) : 10 + (int) (s * 0.06f));
            if (heavy && near) cracks(at, s * 0.55f, r, Mth.clamp(5 + (int) (s * 0.18f), 5, 16));
        }
        float reach = 160 + s * 10;
        if (heavy) {
            shake(d, 0.5f + s * 0.03f, 150 + s * 10);
            sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, at.x, at.y, at.z, 1f, 0.5f, 0, reach, seed);
            sound(SoundEvents.GENERIC_EXPLODE.value(), at.x, at.y, at.z, 0.7f, 0.5f, 1, reach * 1.3f, seed + 1);
            sound(at.state.getSoundType().getBreakSound(), at.x, at.y, at.z, 1f, 0.5f, 2, reach * 0.7f, seed + 2);
            sound(SoundEvents.GRAVEL_BREAK, at.x, at.y, at.z, 0.8f, 0.55f, 14, reach * 0.6f, seed + 3);
            sound(SoundEvents.ROOTED_DIRT_BREAK, at.x, at.y, at.z, 0.6f, 0.5f, 26, reach * 0.5f, seed + 4);
        } else {
            shake(d, 0.15f + s * 0.02f, 80 + s * 8);
            sound(SoundEvents.MACE_SMASH_GROUND, at.x, at.y, at.z, 0.8f, 0.6f, 0, 80 + s * 8, seed);
            sound(at.state.getSoundType().getBreakSound(), at.x, at.y, at.z, 0.7f, 0.6f, 1, 60 + s * 6, seed + 1);
        }
    }

    private static BlockState given;

    /** chunks of the ground (each from a real block near the middle, or `given`) thrown up, tumbling */
    private static void debris(Level lv, Spot at, float s, float hx, float hz, RandomSource r, float want, float power) {
        int count = Math.min(160, n(want));
        // a few spots round the middle to take blocks from
        BlockPos[] from = new BlockPos[6];
        for (int i = 0; i < from.length; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * s * 0.5;
            double x = at.x + Math.cos(a) * rr, z = at.z + Math.sin(a) * rr;
            double g = FxWorld.ground(x, z, at.y);
            BlockPos b = BlockPos.containing(x, g - 0.5, z);
            if (lv.getBlockState(b).isAir() || !lv.getFluidState(b).isEmpty()) b = at.pos;
            from[i] = b;
        }
        for (int i = 0; i < count; i++) {
            float half = Mth.clamp(s * 0.045f * rnd(r, 0.5f, 1.6f), 0.15f, 5f) * (0.7f + 0.3f * power);
            double a = r.nextDouble() * Math.PI * 2, rr = s * (0.15 + 0.5 * Math.sqrt(r.nextDouble()));
            double x = at.x + Math.cos(a) * rr, z = at.z + Math.sin(a) * rr;
            FxWorld.Chunk c = FxWorld.chunk(lv, from[i % from.length], given, x, at.y + half, z, half, 120 + r.nextInt(60));
            if (c == null) break;
            float grav = 0.08f + 0.02f * Math.min(4f, half);
            float hgt = s * rnd(r, 0.25f, 0.95f) * power;
            float up = Mth.sqrt(2f * grav * hgt);
            float out = up * rnd(r, 0.12f, 0.45f);
            c.vx = Math.cos(a) * out + hx * up * 0.35f;
            c.vz = Math.sin(a) * out + hz * up * 0.35f;
            c.vy = up;
            Vec3 ax = new Vec3(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
            c.ax = (float) ax.x; c.ay = (float) ax.y; c.az = (float) ax.z;
            c.spin = rnd(r, 0.1f, 0.45f) * (r.nextBoolean() ? 1 : -1);
            c.ang = r.nextFloat() * Mth.TWO_PI;
            c.pang = c.ang;
        }
    }

    /** the dust thrown up in the middle: a plume rising and spreading */
    private static void plume(Spot at, float s, float hx, float hz, RandomSource r, float power) {
        int count = Math.min(120, n((14 + s * 0.6f) * power));
        for (int i = 0; i < count; i++) {
            // (out round whatever came down, not under it: a giant's own body would hide it)
            double a = r.nextDouble() * Math.PI * 2, rr = s * rnd(r, 0.2f, 0.85f);
            double gx = at.x + Math.cos(a) * rr, gz = at.z + Math.sin(a) * rr;
            FxWorld.Puff p = FxWorld.puff(gx, FxWorld.ground(gx, gz, at.y) + r.nextDouble() * s * 0.1, gz, s * rnd(r, 0.25f, 0.5f), 80 + r.nextInt(70));
            if (p == null) break;
            float up = s * rnd(r, 0.012f, 0.05f) * power;
            p.vx = Math.cos(a) * up * 0.9 + hx * up * 0.4; p.vz = Math.sin(a) * up * 0.9 + hz * up * 0.4; p.vy = up;
            p.drag = 0.93f; p.grav = -0.0015f * Mth.sqrt(s);
            p.grow = s * rnd(r, 0.004f, 0.008f);
            dustColour(p, at, r);
            p.a = rnd(r, 0.6f, 0.9f);
            p.variant = r.nextInt(4); p.rot = r.nextFloat() * 6f; p.rotV = (r.nextFloat() - 0.5f) * 0.03f;
        }
    }

    private static void dustColour(FxWorld.Puff p, Spot at, RandomSource r) {
        float v = rnd(r, 0.85f, 1.1f);
        p.r = Mth.clamp(at.dr * v, 0f, 1f); p.g = Mth.clamp(at.dg * v, 0f, 1f); p.b = Mth.clamp(at.db * v, 0f, 1f);
        p.light = at.light;
    }

    /** a ring of dust rolling out along the ground from r0 to r1 over that many ticks */
    private static void dustRing(Spot at, float r0, float r1, int ticks, RandomSource r, float power) {
        long seed = r.nextLong();
        float s = r1;
        int each = Math.max(1, n((4 + s * 0.1f) * power));
        FxWorld.emit(FxWorld.emitter(ticks, age -> {
            RandomSource q = RandomSource.create(seed + age);
            float k0 = (float) age / ticks, k1 = (float) (age + 1) / ticks;
            float e0 = 1f - (1f - k0) * (1f - k0), e1 = 1f - (1f - k1) * (1f - k1);
            float rad = Mth.lerp(e0, r0, r1), speed = (e1 - e0) * (r1 - r0);
            for (int i = 0; i < each; i++) {
                double a = q.nextDouble() * Math.PI * 2;
                double x = at.x + Math.cos(a) * rad, z = at.z + Math.sin(a) * rad;
                double g = FxWorld.ground(x, z, at.y);
                float size = Math.max(1f, s * rnd(q, 0.08f, 0.15f)) * (1.2f - 0.5f * k0);
                FxWorld.Puff p = FxWorld.puff(x, g + size * 0.3, z, size, 40 + q.nextInt(35));
                if (p == null) return;
                p.vx = Math.cos(a) * speed * 0.7; p.vz = Math.sin(a) * speed * 0.7; p.vy = size * 0.02;
                p.drag = 0.9f; p.grow = size * 0.025f;
                dustColour(p, at, q);
                p.a = rnd(q, 0.5f, 0.8f) * (1f - 0.5f * k0);
                p.variant = q.nextInt(4); p.rot = q.nextFloat() * 6f; p.rotV = (q.nextFloat() - 0.5f) * 0.05f;
                p.floor = g;
            }
        }));
    }

    /** the shockwave: a pale band running out along the ground */
    private static FxWorld.Ring shockRing(Spot at, float r0, float r1, float width, int ticks) {
        FxWorld.Ring g = FxWorld.ring(at.x, at.y, at.z, r0, r1, width, ticks);
        if (g == null) return null;
        g.r = Mth.lerp(0.5f, at.dr, 1f); g.g = Mth.lerp(0.5f, at.dg, 1f); g.b = Mth.lerp(0.5f, at.db, 1f);
        g.a = 0.75f; g.light = at.light;
        return g;
    }

    /** cracks running out from the middle, jagged */
    private static void cracks(Spot at, float len, RandomSource r, int count) {
        for (int c = 0; c < count; c++) {
            if (!FxWorld.room(FxWorld.cracks, FxWorld.MAX_CRACKS)) return;
            int m = 9 + r.nextInt(6);
            double a = (c + r.nextFloat() * 0.6) * Math.PI * 2 / count;
            double L = len * rnd(r, 0.55f, 1.25f), step = L / m;
            float w0 = Mth.clamp(len * 0.07f, 0.35f, 4f) * rnd(r, 0.7f, 1.2f);
            FxWorld.Crack k = new FxWorld.Crack();
            k.xs = new double[m + 1]; k.zs = new double[m + 1]; k.ws = new float[m + 1];
            double x = at.x + Math.cos(a) * len * 0.08, z = at.z + Math.sin(a) * len * 0.08;
            for (int i = 0; i <= m; i++) {
                k.xs[i] = x; k.zs[i] = z;
                k.ws[i] = w0 * (1f - 0.85f * i / m);
                a += (r.nextDouble() - 0.5) * 0.7;
                x += Math.cos(a) * step; z += Math.sin(a) * step;
            }
            k.y0 = at.y; k.grow = 5 + (int) (L * 0.08); k.max = 200 + r.nextInt(80); k.light = at.light;
            FxWorld.cracks.add(k);
        }
    }

    /** a column of water thrown up (leaning along dx dy dz, if given) */
    private static void spray(Spot at, float s, float dx, float dy, float dz, RandomSource r, float power) {
        int count = Math.min(90, n((14 + s * 0.5f) * power));
        float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float lx = dl > 1e-4f ? dx / dl : 0f, ly = dl > 1e-4f ? dy / dl : 1f, lz = dl > 1e-4f ? dz / dl : 0f;
        float grav = 0.06f;
        for (int i = 0; i < count; i++) {
            FxWorld.Puff p = FxWorld.puff(at.x + (r.nextDouble() - 0.5) * s * 0.3, at.y + 0.2, at.z + (r.nextDouble() - 0.5) * s * 0.3, s * rnd(r, 0.1f, 0.2f), 40 + r.nextInt(30));
            if (p == null) break;
            float v = Mth.sqrt(2f * grav * s * 1.4f * power) * rnd(r, 0.55f, 1f);
            p.vx = lx * v + (r.nextFloat() - 0.5f) * v * 0.25f; p.vy = ly * v; p.vz = lz * v + (r.nextFloat() - 0.5f) * v * 0.25f;
            p.drag = 0.985f; p.grav = grav; p.grow = s * 0.006f;
            p.r = 0.88f; p.g = 0.94f; p.b = 1f; p.a = 0.85f;
            p.light = at.light; p.floor = at.y; p.variant = r.nextInt(4); p.rot = r.nextFloat() * 6f;
        }
        // the mist left hanging
        for (int i = 0; i < Math.min(20, n(4 + s * 0.12f)); i++) {
            FxWorld.Puff p = FxWorld.puff(at.x + (r.nextDouble() - 0.5) * s * 0.6, at.y + r.nextDouble() * s * power, at.z + (r.nextDouble() - 0.5) * s * 0.6, s * rnd(r, 0.3f, 0.5f), 70 + r.nextInt(40));
            if (p == null) break;
            p.vy = 0.01; p.grow = s * 0.005f; p.r = 0.9f; p.g = 0.95f; p.b = 1f; p.a = 0.35f; p.light = at.light; p.variant = r.nextInt(4);
        }
    }

    /** something big hits the water: a crown of spray, a ring of foam, the column in the middle */
    private static void splash(Level lv, Spot at, float s, double d, RandomSource r, boolean show) {
        long seed = r.nextLong();
        if (show) {
            int count = Math.min(120, n(20 + s * 0.6f));
            float grav = 0.06f;
            for (int i = 0; i < count; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rr = s * rnd(r, 0.2f, 0.4f);
                FxWorld.Puff p = FxWorld.puff(at.x + Math.cos(a) * rr, at.y + 0.3, at.z + Math.sin(a) * rr, s * rnd(r, 0.1f, 0.22f), 50 + r.nextInt(30));
                if (p == null) break;
                float up = Mth.sqrt(2f * grav * s * rnd(r, 0.35f, 0.9f));
                float out = up * rnd(r, 0.15f, 0.4f);
                p.vx = Math.cos(a) * out; p.vz = Math.sin(a) * out; p.vy = up;
                p.drag = 0.985f; p.grav = grav; p.grow = s * 0.008f;
                p.r = 0.9f; p.g = 0.95f; p.b = 1f; p.a = 0.9f; p.light = at.light; p.floor = at.y;
                p.variant = r.nextInt(4); p.rot = r.nextFloat() * 6f;
            }
            spray(at, s * 0.8f, 0, 1, 0, r, 1f);
            FxWorld.Ring g = FxWorld.ring(at.x, at.y + 0.1, at.z, s * 0.2f, s * 1.8f, Math.max(1.2f, s * 0.3f), 30 + (int) (s * 0.2f));
            if (g != null) { g.water = true; g.r = 0.95f; g.g = 0.98f; g.b = 1f; g.a = 0.85f; g.light = at.light; }
            for (int i = 0; i < Math.min(30, n(8 + s * 0.2f)); i++) {
                double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * s;
                FxWorld.Puff p = FxWorld.puff(at.x + Math.cos(a) * rr, at.y + 0.2, at.z + Math.sin(a) * rr, s * rnd(r, 0.2f, 0.35f), 90 + r.nextInt(40));
                if (p == null) break;
                p.vx = Math.cos(a) * s * 0.01; p.vz = Math.sin(a) * s * 0.01; p.drag = 0.95f; p.grow = s * 0.004f;
                p.r = 0.95f; p.g = 0.98f; p.b = 1f; p.a = 0.55f; p.light = at.light; p.floor = at.y; p.variant = r.nextInt(4);
            }
        }
        shake(d, 0.2f + s * 0.015f, 100 + s * 6);
        float reach = 140 + s * 8;
        sound(SoundEvents.GENERIC_SPLASH, at.x, at.y, at.z, 1f, 0.45f, 0, reach, seed);
        sound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, at.x, at.y, at.z, 1f, 0.5f, 1, reach, seed + 1);
        sound(SoundEvents.BOAT_PADDLE_WATER, at.x, at.y, at.z, 0.8f, 0.5f, 22, reach * 0.6f, seed + 2);
    }

    private static int waveTicks;

    /** a wave wall rolling out (along dir, over arc radians; a whole ring when arc is a full turn), breaking at the end */
    private static void wave(Level lv, Spot at, float s, float dir, float arc, double d, RandomSource r, boolean show, long seed) {
        int life = waveTicks > 0 ? Mth.clamp(waveTicks, 10, 400) : Mth.clamp(40 + (int) (s * 0.6f), 40, 170);
        if (show && FxWorld.room(FxWorld.waves, FxWorld.MAX_WAVES)) {
            FxWorld.Wave w = new FxWorld.Wave();
            w.x = at.x; w.y = at.y; w.z = at.z;
            w.r0 = Math.max(2f, s * 0.12f); w.r1 = s;
            w.height = Mth.clamp(s * 0.2f, 1.5f, 40f);
            w.dir = dir; w.arc = arc; w.max = life; w.seed = seed;
            w.colour = at.waterColour; w.light = at.light;
            w.water = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.WATER.defaultBlockState());
            FxWorld.waves.add(w);
        }
        float reach = 160 + s * 6;
        sound(SoundEvents.AMBIENT_UNDERWATER_EXIT, at.x, at.y, at.z, 1f, 0.5f, 0, reach, seed);
        sound(SoundEvents.GENERIC_SPLASH, at.x, at.y, at.z, 0.9f, 0.35f, 4, reach, seed + 1);
        // it breaks out where it ends up
        double bx = at.x + Mth.cos(dir) * s * (arc > 6.2f ? 0f : 0.9f), bz = at.z + Mth.sin(dir) * s * (arc > 6.2f ? 0f : 0.9f);
        sound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, bx, at.y, bz, 1f, 0.35f, (int) (life * 0.6f), reach, seed + 2);
        sound(SoundEvents.GENERIC_SPLASH, bx, at.y, bz, 1f, 0.3f, (int) (life * 0.62f), reach, seed + 3);
        Minecraft mc = Minecraft.getInstance();
        double db = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(bx, at.y, bz));
        shake(d, 0.15f + s * 0.004f, 80 + s * 3, 0);
        shake(db, 0.25f + s * 0.008f, 80 + s * 3, (int) (life * 0.6f));
    }

    /** the sparks added since `from` take that colour (0xRRGGBB) */
    private static void colour(int from, int rgb) {
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        for (int i = from; i < FxWorld.sparks.size(); i++) { var k = FxWorld.sparks.get(i); k.cr = cr; k.cg = cg; k.cb = cb; }
        for (var f : FxWorld.flashes) if (f.life == 0) { f.r = cr; f.g = cg; f.b = cb; }
    }

    /** a burst of sparks (mostly along dx dy dz, if given) */
    private static void sparks(double x, double y, double z, float s, float dx, float dy, float dz, RandomSource r, float power) {
        int count = Math.min(220, n((24 + s * 3f) * power));
        float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        Vec3 main = dl > 1e-4f ? new Vec3(dx / dl, dy / dl, dz / dl) : null;
        float speed = 0.45f * Mth.sqrt(Math.max(1f, s));
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.8 + 0.2, r.nextGaussian()).normalize();
            if (main != null) v = v.scale(0.55).add(main).normalize();
            v = v.scale(speed * rnd(r, 0.3f, 1.2f));
            FxWorld.Spark k = FxWorld.spark(x, y, z, v.x, v.y, v.z, Mth.clamp(s * 0.08f, 0.15f, 1.2f), 12 + r.nextInt(22), false);
            if (k == null) break;
        }
        FxWorld.flash(x, y, z, Math.max(2f, s * 0.9f), 5);
    }

    private static void metal(double x, double y, double z, float s, long seed) {
        float reach = 90 + s * 8;
        sound(SoundEvents.ANVIL_LAND, x, y, z, 0.6f, 0.55f, 0, reach, seed);
        sound(SoundEvents.CHAIN_BREAK, x, y, z, 0.8f, 0.6f, 1, reach, seed + 1);
    }

    /** embers drifting up from round there for a while */
    private static void embers(double x, double y, double z, float s, int ticks, RandomSource r) {
        long seed = r.nextLong();
        int each = Math.max(1, n(2 + s * 0.12f));
        FxWorld.emit(FxWorld.emitter(ticks, age -> {
            RandomSource q = RandomSource.create(seed + age);
            for (int i = 0; i < each; i++) {
                double a = q.nextDouble() * Math.PI * 2, rr = Math.sqrt(q.nextDouble()) * s * 0.6;
                float up = 0.04f * Mth.sqrt(Math.max(1f, s)) * rnd(q, 0.5f, 1.5f);
                if (FxWorld.spark(x + Math.cos(a) * rr, y + q.nextDouble() * s * 0.3, z + Math.sin(a) * rr, (q.nextFloat() - 0.5f) * up, up, (q.nextFloat() - 0.5f) * up,
                        Mth.clamp(s * 0.06f, 0.2f, 0.9f), 40 + q.nextInt(50), true) == null) return;
            }
        }));
    }

    /** a flash of light there; withRing: a ring of light runs out along the ground too */
    private static void flash(double x, double y, double z, float s, double d, boolean show, Spot at, boolean withRing) {
        if (show) {
            FxWorld.flash(x, y, z, Math.max(3f, s * 1.5f), 9);
            if (withRing && s > 6) {
                // (on the ground under it, however high the flash is)
                FxWorld.Ring g = FxWorld.ring(x, FxWorld.groundBelow(x, z, y), z, s * 0.2f, s * 2.2f, Math.max(2f, s * 0.5f), 14);
                if (g != null) { g.glow = true; g.r = 1f; g.g = 0.9f; g.b = 0.65f; g.a = 0.6f; }
            }
        }
        // white on the screen of whoever is near and looking at it
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 to = new Vec3(x - eye.x, y - eye.y, z - eye.z);
            double len = to.length();
            double reach = s * 5 + 40;
            if (len < reach) {
                float look = len < 1e-3 ? 1f : (float) Mth.clamp((mc.player.getViewVector(1f).dot(to.scale(1 / len)) + 0.2) / 1.2, 0, 1);
                screenFlash = Math.max(screenFlash, (float) (1 - len / reach) * look * 0.75f);
            }
        }
    }

    /** a blast: light, sparks, embers, black smoke, chunks, a shockwave */
    private static void explosion(Level lv, Spot at, double x, double y, double z, float s, double d, RandomSource r, boolean show, boolean near, long seed) {
        flash(x, y, z, s * 1.4f, d, show, at, false);
        boolean low = y - at.y < s * 0.8;
        if (show) {
            sparks(x, y, z, s, 0, 0, 0, r, 1f);
            embers(x, y, z, s, 24, r);
            smoke(x, y, z, s, 0, 0, at.light, r, 1f);
            if (low) {
                if (at.water) spray(at, s * 1.2f, 0, 1, 0, r, 1f);
                else {
                    if (near) debris(lv, at, s * 0.9f, 0, 0, r, 6 + s * 0.5f, 1.2f);
                    if (near) cracks(at, s * 0.4f, r, Mth.clamp(4 + (int) (s * 0.1f), 4, 10));
                }
                shockRing(at, s * 0.1f, s * 1.8f, Math.max(1.2f, s * 0.3f), 10 + (int) (s * 0.08f));
                dustRing(at, s * 0.2f, s * 1.4f, 16 + (int) (s * 0.1f), r, 0.7f);
            }
        }
        shake(d, 0.6f + s * 0.04f, 160 + s * 10);
        float reach = 200 + s * 10;
        sound(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 1f, 0.55f, 0, reach, seed);
        sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, x, y, z, 1f, 0.5f, 2, reach, seed + 1);
        sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, x, y, z, 0.6f, 0.5f, 3, reach * 0.8f, seed + 2);
        sound(SoundEvents.GRAVEL_BREAK, x, y, z, 0.6f, 0.6f, 16, reach * 0.5f, seed + 3);
    }

    /** dark smoke going up (and blown along hx hz) */
    private static void smoke(double x, double y, double z, float s, float hx, float hz, int light, RandomSource r, float power) {
        int count = Math.min(70, n((10 + s * 0.5f) * power));
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * s * 0.3;
            FxWorld.Puff p = FxWorld.puff(x + Math.cos(a) * rr, y + r.nextDouble() * s * 0.2, z + Math.sin(a) * rr, Math.max(1f, s * rnd(r, 0.25f, 0.45f)), 90 + r.nextInt(80));
            if (p == null) break;
            float up = Math.max(0.05f, s * rnd(r, 0.008f, 0.03f));
            p.vx = Math.cos(a) * up * 0.4 + hx * up; p.vz = Math.sin(a) * up * 0.4 + hz * up; p.vy = up;
            p.drag = 0.96f; p.grav = -0.002f * Mth.sqrt(Math.max(1f, s)); p.grow = Math.max(0.02f, s * 0.01f);
            float g = rnd(r, 0.14f, 0.26f);
            p.r = g; p.g = g * 0.97f; p.b = g * 0.95f; p.a = rnd(r, 0.7f, 0.9f);
            p.light = light; p.variant = r.nextInt(4); p.rot = r.nextFloat() * 6f; p.rotV = (r.nextFloat() - 0.5f) * 0.02f;
        }
    }

    /** dust kicked up behind something charging (going along hx hz) */
    private static void trail(Level lv, Spot at, float s, float hx, float hz, RandomSource r, boolean near) {
        int count = Math.min(30, n(3 + s * 0.25f));
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * s * 0.4;
            double x = at.x + Math.cos(a) * rr, z = at.z + Math.sin(a) * rr;
            double g = FxWorld.ground(x, z, at.y);
            FxWorld.Puff p = FxWorld.puff(x, g + s * 0.1, z, Math.max(1f, s * rnd(r, 0.2f, 0.35f)), 40 + r.nextInt(30));
            if (p == null) break;
            float back = s * rnd(r, 0.004f, 0.012f);
            p.vx = -hx * back + Math.cos(a) * back * 0.5; p.vz = -hz * back + Math.sin(a) * back * 0.5; p.vy = s * rnd(r, 0.004f, 0.012f);
            p.drag = 0.92f; p.grow = s * 0.008f; p.floor = g;
            dustColour(p, at, r);
            p.a = rnd(r, 0.45f, 0.75f); p.variant = r.nextInt(4); p.rot = r.nextFloat() * 6f;
        }
        if (near && !at.water) debris(lv, at, s * 0.5f, -hx, -hz, r, 1 + s * 0.06f, 0.4f);
        if (at.water) spray(at, s * 0.6f, -hx, 1f, -hz, r, 0.5f);
    }

    /** a whirl: water (or dust) spiralling in to the middle for that many ticks, a dark hole in it */
    private static void whirl(Spot at, float s, int ticks, RandomSource r, boolean show, long seed) {
        if (show) {
            int each = Math.max(1, n(3 + s * 0.15f));
            boolean wet = at.water;
            FxWorld.emit(FxWorld.emitter(ticks, age -> {
                RandomSource q = RandomSource.create(seed + age);
                float turn = age * 0.12f;
                for (int i = 0; i < each; i++) {
                    float u = rnd(q, 0.1f, 1f);
                    float a = turn + (i % 3) * Mth.TWO_PI / 3 + u * 4f + (q.nextFloat() - 0.5f) * 0.4f;
                    float rad = s * u;
                    double x = at.x + Mth.cos(a) * rad, z = at.z + Mth.sin(a) * rad;
                    FxWorld.Puff p = FxWorld.puff(x, at.y + 0.3, z, Math.max(1f, s * rnd(q, 0.08f, 0.16f)), 26 + q.nextInt(20));
                    if (p == null) return;
                    float sp = s * 0.035f * (1.2f - u * 0.6f);
                    p.vx = -Mth.sin(a) * sp - Mth.cos(a) * sp * 0.3f; p.vz = Mth.cos(a) * sp - Mth.sin(a) * sp * 0.3f; p.vy = 0;
                    p.drag = 0.97f; p.floor = at.y + 0.1; p.grow = s * 0.003f;
                    if (wet) { p.r = 0.9f; p.g = 0.96f; p.b = 1f; p.a = 0.7f; p.light = at.light; }
                    else dustColour(p, at, q);
                    p.variant = q.nextInt(4); p.rot = q.nextFloat() * 6f;
                }
            }));
            FxWorld.Ring hole = FxWorld.ring(at.x, at.y + 0.05, at.z, s * 0.05f, s * 0.35f, Math.max(1.5f, s * 0.4f), ticks);
            if (hole != null) { hole.water = wet; hole.ease = 6f; hole.r = wet ? 0.05f : 0.2f; hole.g = wet ? 0.13f : 0.16f; hole.b = wet ? 0.2f : 0.12f; hole.a = 0.7f; hole.light = at.light; }
        }
        float reach = 120 + s * 6;
        for (int i = 0; i < ticks; i += 25)
            sound(at.water ? SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT : SoundEvents.SAND_BREAK, at.x, at.y, at.z, 1f, 0.5f, i, reach, seed + i);
    }

    /** a coloured cloud lingering low round there */
    private static void cloud(Spot at, float s, int colour, RandomSource r) {
        long seed = r.nextLong();
        int ticks = Mth.clamp(60 + (int) (s * 1.5f), 60, 260);
        int each = Math.max(1, n(1 + s * 0.05f));
        float cr = ((colour >> 16) & 255) / 255f, cg = ((colour >> 8) & 255) / 255f, cb = (colour & 255) / 255f;
        FxWorld.emit(FxWorld.emitter(ticks, age -> {
            if (age % 2 != 0) return;
            RandomSource q = RandomSource.create(seed + age);
            for (int i = 0; i < each; i++) {
                double a = q.nextDouble() * Math.PI * 2, rr = Math.sqrt(q.nextDouble()) * s;
                double x = at.x + Math.cos(a) * rr, z = at.z + Math.sin(a) * rr;
                double g = FxWorld.ground(x, z, at.y);
                FxWorld.Puff p = FxWorld.puff(x, g + s * rnd(q, 0.05f, 0.4f), z, Math.max(1.5f, s * rnd(q, 0.3f, 0.55f)), 70 + q.nextInt(50));
                if (p == null) return;
                p.vx = (q.nextFloat() - 0.5f) * s * 0.006f; p.vz = (q.nextFloat() - 0.5f) * s * 0.006f; p.vy = s * 0.002f;
                p.drag = 0.95f; p.grow = s * 0.004f;
                float v = rnd(q, 0.85f, 1.1f);
                p.r = Mth.clamp(cr * v, 0f, 1f); p.g = Mth.clamp(cg * v, 0f, 1f); p.b = Mth.clamp(cb * v, 0f, 1f);
                p.a = rnd(q, 0.3f, 0.5f); p.light = at.light; p.variant = q.nextInt(4); p.rot = q.nextFloat() * 6f; p.rotV = (q.nextFloat() - 0.5f) * 0.02f;
                p.floor = g;
            }
        }));
    }
}
