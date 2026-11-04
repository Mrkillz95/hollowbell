package net.jj.giants.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.net.WeatherPayload;
import net.jj.giants.systems.WeatherKinds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.5) Weather Gods on the player's own game: the giant weather the server says they are in,
 * eased in and out over two seconds: the fog closes in and takes his colour (so does the sky), and what his weather
 * brings falls round them (ash, dust, sleet and embers, mist, smog, souls). The rain and thunder are the game's own.
 * Nothing at all while they're in no giant's weather.
 */
public final class WeatherClient {
    private WeatherClient() {}

    /** what the server last said */
    private static String target = "";
    private static float targetStrength;
    /** what is shown now, eased toward it */
    private static @Nullable WeatherKinds now;
    private static float strength;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> told(p.kind(), p.strength())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> forget());
        ClientTickEvents.END_CLIENT_TICK.register(WeatherClient::tick);
    }

    public static void told(String kind, float s) {
        target = kind == null ? "" : kind;
        targetStrength = Math.max(0, Math.min(1, s));
    }

    static void forget() {
        target = "";
        targetStrength = 0;
        now = null;
        strength = 0;
    }

    private static void tick(Minecraft mc) {
        if (now == null && target.isEmpty()) return;
        if (mc.level == null) {
            forget();
            return;
        }
        if (mc.isPaused()) return;
        WeatherKinds want = target.isEmpty() ? null : WeatherKinds.byId(target);
        if (now != null && want != now) {
            // another weather (or none): this one fades first
            strength = Math.max(0, strength - 0.025f);
            if (strength == 0) now = want;
        } else {
            now = want;
            float to = want == null ? 0 : targetStrength;
            strength += Math.max(-0.025f, Math.min(0.025f, to - strength));
            if (want == null && strength <= 0) now = null;
        }
        if (now != null && strength > 0) fall(mc, mc.level, now, strength);
    }

    /** the strength shown now (0: nothing) and of what */
    public static float strength() {
        return now == null ? 0 : strength;
    }

    public static @Nullable WeatherKinds kind() {
        return now;
    }

    private static boolean shows(Camera camera) {
        if (now == null || strength <= 0 || camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.getEntity() instanceof LivingEntity le)
                || !le.hasEffect(MobEffects.BLINDNESS) && !le.hasEffect(MobEffects.DARKNESS);
    }

    /** the fog's end (and start), closed in toward his weather's */
    public static float[] fog(Camera camera, float start, float end) {
        if (!shows(camera)) return null;
        float s = strength * strength * (3 - 2 * strength);
        float e2 = end + (Math.min(end, now.fogEnd()) - end) * s;
        float s2 = start + (Math.min(start, e2 * 0.05f) - start) * s;
        return new float[] {s2, e2};
    }

    /** the fog's colour blended toward his (r, g, b in 0-1), or null for as it is */
    public static float[] colour(Camera camera, float r, float g, float b) {
        if (!shows(camera)) return null;
        float s = strength * 0.8f;
        int c = now.fog();
        return new float[] {r + (((c >> 16) & 255) / 255f - r) * s, g + (((c >> 8) & 255) / 255f - g) * s,
            b + ((c & 255) / 255f - b) * s};
    }

    /** the sky blended toward his fog's colour */
    public static Vec3 sky(Vec3 was) {
        if (now == null || strength <= 0) return was;
        float s = strength * 0.7f;
        int c = now.fog();
        return new Vec3(was.x + (((c >> 16) & 255) / 255.0 - was.x) * s,
                was.y + (((c >> 8) & 255) / 255.0 - was.y) * s, was.z + ((c & 255) / 255.0 - was.z) * s);
    }

    /** what his weather brings, round the camera (fewer with fewer particles in the video settings) */
    private static void fall(Minecraft mc, ClientLevel level, WeatherKinds k, float s) {
        ParticleStatus ps = mc.options.particles().get();
        if (ps == ParticleStatus.MINIMAL) return;
        float rate = ps == ParticleStatus.DECREASED ? 0.4f : 1f;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 c = cam.getPosition();
        RandomSource r = level.random;
        switch (k.id()) {
            case "ash" -> {
                many(level, r, c, ParticleTypes.ASH, 10 * s * rate, 0, -0.02);
                many(level, r, c, ParticleTypes.WHITE_ASH, 4 * s * rate, 0, -0.02);
            }
            case "dust" -> many(level, r, c, new BlockParticleOption(ParticleTypes.FALLING_DUST,
                    Blocks.COARSE_DIRT.defaultBlockState()), 7 * s * rate, 0.05, 0);
            case "sleet" -> {
                many(level, r, c, ParticleTypes.SNOWFLAKE, 6 * s * rate, 0.02, -0.12);
                many(level, r, c, ParticleTypes.SMALL_FLAME, 1.2 * s * rate, 0.01, 0.02);
            }
            case "mist" -> {
                many(level, r, c, ParticleTypes.SPORE_BLOSSOM_AIR, 4 * s * rate, 0, 0);
                many(level, r, c, ParticleTypes.WHITE_SMOKE, 1 * s * rate, 0.01, 0);
            }
            case "smog" -> {
                many(level, r, c, ParticleTypes.SMOKE, 6 * s * rate, 0.02, 0.01);
                many(level, r, c, ParticleTypes.WHITE_ASH, 3 * s * rate, 0, -0.02);
            }
            case "gloaming" -> {
                many(level, r, c, ParticleTypes.SOUL, 0.6 * s * rate, 0, 0.02);
                many(level, r, c, ParticleTypes.ASH, 6 * s * rate, 0, -0.01);
            }
            // (Hollowbell's storm: sparks of his own glow on the wind, the bell-teal sky)
            case "storm" -> many(level, r, c, ParticleTypes.ELECTRIC_SPARK, 5 * s * rate, 0.3, -0.05);
            // (the Wreckback's squall: sea spray blown in off the waves)
            case "squall" -> {
                many(level, r, c, ParticleTypes.SPLASH, 8 * s * rate, 0.2, 0);
                many(level, r, c, ParticleTypes.FALLING_WATER, 4 * s * rate, 0.1, -0.1);
            }
            default -> {}
        }
    }

    private static void many(ClientLevel level, RandomSource r, Vec3 c, ParticleOptions what, double count,
            double drift, double fall) {
        int n = (int) count + (r.nextDouble() < count - (int) count ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double x = c.x + (r.nextDouble() - 0.5) * 32, y = c.y + r.nextDouble() * 14 - 3,
                    z = c.z + (r.nextDouble() - 0.5) * 32;
            level.addParticle(what, x, y, z, (r.nextDouble() - 0.5) * drift, fall, (r.nextDouble() - 0.5) * drift);
        }
    }
}
