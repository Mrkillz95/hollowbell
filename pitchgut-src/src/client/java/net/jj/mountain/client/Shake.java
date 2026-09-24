package net.jj.mountain.client;

import net.jj.mountain.MountainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** How hard the ground is shaking right now, and which way. His feet feed this; the camera reads it. */
public final class Shake {
    private Shake() {}

    private static float amount, last;
    private static long seed = 1;

    /** a foot has come down: how hard you feel it falls off with distance */
    public static void thump(double dist, float scale) {
        if (!MountainConfig.V.screenShake) return;
        double reach = 60 + 340 * scale;
        if (dist > reach) return;
        float near = (float) (1.0 - dist / reach);
        amount = Math.min(1.4f, amount + (0.12f + 0.95f * scale) * near * near);
    }

    /** he has come down: one long shove instead of a footfall */
    public static void crash(double dist, float scale) {
        if (!MountainConfig.V.screenShake) return;
        double reach = 140 + 600 * scale;
        if (dist > reach) return;
        float near = (float) (1.0 - dist / reach);
        amount = Math.min(2.2f, amount + (0.8f + 1.4f * scale) * near);
    }

    public static void tick() {
        last = amount;
        amount *= amount > 1.2f ? 0.93f : 0.80f;
        if (amount < 0.002f) amount = 0f;
    }

    /** sideways, up-down and forward-back wobble for the camera this frame */
    public static float[] offset(float partial) {
        if (amount <= 0f || !MountainConfig.V.screenShake) return null;
        float a = Mth.lerp(partial, last, amount);
        if (a <= 0.002f) return null;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        // two speeds mixed so it reads as ground shaking, not a rattle
        float x = (Mth.sin(t * 2.7f) * 0.6f + Mth.sin(t * 7.3f) * 0.4f) * a * 0.30f;
        float y = (Mth.sin(t * 3.9f + 1.3f) * 0.6f + Mth.sin(t * 9.1f) * 0.4f) * a * 0.24f;
        float z = Mth.sin(t * 5.1f + 2.2f) * a * 0.12f;
        return new float[]{x, y, z};
    }
}
