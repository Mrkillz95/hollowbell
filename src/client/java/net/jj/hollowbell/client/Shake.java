package net.jj.hollowbell.client;

import net.jj.hollowbell.BellConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** How hard the ground is shaking right now, and which way. His slams feed this; the camera reads it. */
public final class Shake {
    private Shake() {}

    private static float amount, last;
    private static long seed = 1;

    /** a foot has come down: how hard you feel it falls off with distance */
    public static void thump(double dist, float scale) {
        if (!BellConfig.V.screenShake) return;
        double reach = 60 + 340 * scale;
        if (dist > reach) return;
        float near = (float) (1.0 - dist / reach);
        amount = Math.min(1.4f, amount + (0.12f + 0.95f * scale) * near * near);
    }

    /** a swim beat close by: the softest of nudges, gone a little way off, and next to nothing from a small one */
    public static void beat(double dist, float scale) {
        if (!BellConfig.V.screenShake) return;
        double reach = 40 + 120 * scale;
        if (dist > reach) return;
        float near = (float) (1.0 - dist / reach);
        amount = Math.max(amount, 0.22f * Math.min(1f, scale) * near * near);
    }

    /** he has come down: one long shove instead of a footfall */
    public static void crash(double dist, float scale) {
        if (!BellConfig.V.screenShake) return;
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
        if (amount <= 0f || !BellConfig.V.screenShake) return null;
        float a = Mth.lerp(partial, last, amount);
        if (a <= 0.002f) return null;
        Minecraft mc = Minecraft.getInstance();
        // (kept small: the game's sine table stops turning once its angle passes about 200000)
        double t = (mc.level == null ? 0L : mc.level.getGameTime() % 24000L) + partial;
        // two speeds mixed so it reads as ground shaking, not a rattle
        float x = (float) (Math.sin(t * 2.7) * 0.6 + Math.sin(t * 7.3) * 0.4) * a * 0.30f;
        float y = (float) (Math.sin(t * 3.9 + 1.3) * 0.6 + Math.sin(t * 9.1) * 0.4) * a * 0.24f;
        float z = (float) Math.sin(t * 5.1 + 2.2) * a * 0.12f;
        return new float[] {x, y, z};
    }
}
