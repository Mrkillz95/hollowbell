package net.jj.hollowbell.client;

import net.jj.hollowbell.net.LookTrace;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * "Go after what I look at", on your screen: what is under your crosshair, from where your view really is (first
 * person, F5, riding him or being him), out to your render distance and never less than 512 blocks. Its id goes to
 * the server, which checks it. Once he takes the order, that one glows for a moment, for you only.
 */
public final class LookAim {
    private LookAim() {}

    /** the id of what you are looking at, or 0 for nothing (the server then looks for itself) */
    public static int pick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 from = cam.getPosition();
        Vec3 dir = new Vec3(cam.getLookVector());
        if (dir.lengthSqr() < 1e-6) dir = mc.player.getViewVector(1f);
        double range = Math.max(LookTrace.RANGE, mc.options.getEffectiveRenderDistance() * 16 + 32);
        Entity e = LookTrace.trace(mc.level, from, dir, range, mc.player, mc.getCameraEntity());
        return e == null ? 0 : e.getId();
    }

    private static int markId = -1;
    private static long markUntil;

    /** the server took the order: that one glows for three seconds */
    public static void mark(int id) {
        Minecraft mc = Minecraft.getInstance();
        markId = id;
        markUntil = mc.level == null ? 0 : mc.level.getGameTime() + 60;
    }

    public static boolean glows(Entity e) {
        if (markId < 0 || e.getId() != markId) return false;
        if (e.level().getGameTime() > markUntil) { markId = -1; return false; }
        return true;
    }
}
