package net.jj.hollowbell.solid.client;

import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidBody;
import net.jj.hollowbell.solid.SolidCarry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The solid kit on your own game: you are never inside him (moved out at once, the same tick), strands nudge you
 * aside, and standing on him you're carried along. At the end of each tick you are moved with the spot under your feet;
 * then every frame, before the camera is set, you are put exactly where that spot is in the pose he is drawn in
 * (and put back after the frame, so your game's own movement is untouched). His turning turns your view every frame.
 *
 * Hook up: END_CLIENT_TICK → tick, GameRenderer.renderLevel HEAD/RETURN → beforeFrame/afterFrame (SolidFrameMixin),
 * leaving a world → clear.
 */
public final class SolidClient {
    private SolidClient() {}

    public static final SolidCarry CARRY = new SolidCarry();
    /** how many times you were moved out of him, and nudged by his soft parts (for the autotest) */
    public static int unstuck, softPushed;
    /** the most the frame put you off from where the tick had you (for the autotest), blocks */
    public static double frameFix;

    private static float frameYaw = Float.NaN;
    private static int frameKey = Integer.MIN_VALUE;
    private static boolean moved;
    private static double rx, ry, rz, rxo, ryo, rzo;
    private static Matrix4f[] scratch;

    public static int traceTicks;
    static String tr(net.minecraft.world.entity.player.Player p) {
        String f = "-";
        if (CARRY.on != null) {
            var c = Solid.frames(CARRY.on);
            var u0 = Solid.toWorld(c, CARRY.frame, new org.joml.Vector3f(0, 0, 0));
            var up = Solid.toWorld(c, CARRY.frame, new org.joml.Vector3f(0, 1, 0)).subtract(u0).normalize();
            f = CARRY.frame + " tilt " + String.format("%.1f", Math.toDegrees(Math.acos(Math.min(1, up.y)))) + " air " + CARRY.air;
        }
        return String.format("pos %.3f %.3f %.3f v %.3f %.3f %.3f og %s on %s", p.getX(), p.getY(), p.getZ(),
                p.getDeltaMovement().x, p.getDeltaMovement().y, p.getDeltaMovement().z, p.onGround(), f);
    }

    public static void tick(Minecraft mc) {
        restore();
        if (traceTicks > 0 && mc.player != null) { traceTicks--; net.jj.hollowbell.HollowbellMod.LOG.info("trace start {}", tr(mc.player)); }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { clear(); return; }
        if (p.isSpectator() || p.isPassenger() || p.noPhysics) { clear(); return; }
        long t0 = System.nanoTime();
        // carried first (moved with the part you stand on), then out of him if he's still into you
        if (p.getAbilities().flying || (p.isInWater() && !p.onGround())) CARRY.clear();
        else CARRY.step(p, Solid.bodies(true));
        if (traceTicks > 0) {
            net.jj.hollowbell.HollowbellMod.LOG.info("trace carried {}", tr(p));
            for (SolidBody b : Solid.bodies(true)) {
                Solid.Runs r = Solid.column(b, p.getX(), p.getZ(), p.getY() - 6, p.getY() + 3, true, new Solid.Runs());
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < r.n; j++) sb.append(String.format(" [f%d k%d %.2f..%.2f]", r.frame[j], r.kind[j], r.bot[j], r.top[j]));
                net.jj.hollowbell.HollowbellMod.LOG.info("trace runs{}", sb);
            }
        }
        for (SolidBody b : Solid.bodies(true)) {
            if (b.solidSelf().level() != p.level() || !b.solidReady() || b.solidIgnores(p)) continue;
            int[] tf = {-1};
            double td = Solid.depthIn(b, p.getBoundingBox(), 0.08, tf, true);
            if (Solid.unstick(b, p, 0.08)) {
                if (traceTicks > 0) net.jj.hollowbell.HollowbellMod.LOG.info("trace UNSTICK depth {} frame {} -> {}", td, tf[0], tr(p));
                unstuck++; Solid.unstuckClient++;
                // (the spot under you looked for again where you are now)
                CARRY.clear();
                if (!p.getAbilities().flying) CARRY.step(p, Solid.bodies(true));
                break;
            }
        }
        // his strands don't shove you off the part you stand on (a pod hangs from one, right where you stand)
        if (!CARRY.riding() || CARRY.air > 0) {
            for (SolidBody b : Solid.bodies(true)) {
                if (b.solidSelf().level() != p.level() || !b.solidReady() || b.solidIgnores(p)) continue;
                if (Solid.softPush(b, p)) { softPushed++; if (traceTicks > 0) net.jj.hollowbell.HollowbellMod.LOG.info("trace SOFT {}", tr(p)); }
            }
        }
        Solid.nanosClient += System.nanoTime() - t0;
    }

    /** before a frame is drawn: you where the spot under you is in the pose he's drawn in, and your view turned with him */
    public static void beforeFrame(float partial) {
        restore();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        SolidBody b = CARRY.on;
        if (p == null || b == null || CARRY.rest == null || !b.solidReady() || p.isPassenger()) { frameYaw = Float.NaN; return; }
        Entity s = b.solidSelf();
        int bone = b.solidShape().bone[CARRY.frame];
        Matrix4f[] tickPose = b.solidPose();
        if (scratch == null || scratch.length != tickPose.length) { scratch = new Matrix4f[tickPose.length]; for (int i = 0; i < scratch.length; i++) scratch[i] = new Matrix4f(); }
        b.solidPoseAt(partial, scratch);
        Matrix4f f = b.solidRoot(partial, new Matrix4f()).mul(scratch[bone]);
        double ex = Mth.lerp(partial, s.xo, s.getX()), ey = Mth.lerp(partial, s.yo, s.getY()), ez = Mth.lerp(partial, s.zo, s.getZ());
        // his turning turns your view, smoothly
        float yaw = Solid.yawOf(f);
        int key = s.getId() * 4096 + CARRY.frame;
        if (!Float.isNaN(frameYaw) && key == frameKey) {
            float dy = Mth.wrapDegrees(yaw - frameYaw);
            if (Math.abs(dy) < 20f) {
                p.setYRot(p.getYRot() + dy); p.yRotO += dy;
                p.setYBodyRot(p.yBodyRot + dy); p.yBodyRotO += dy;
                p.setYHeadRot(p.getYHeadRot() + dy); p.yHeadRotO += dy;
            }
        }
        frameYaw = yaw; frameKey = key;
        if (CARRY.restPrev == null) return;
        Vector3f r = new Vector3f(CARRY.restPrev).lerp(CARRY.rest, Mth.clamp(partial, 0f, 1f));
        Vector3f w = f.transformPosition(r);
        double dx = w.x + ex, dy = w.y + ey, dz = w.z + ez;
        double lx = Mth.lerp(partial, p.xo, p.getX()), ly = Mth.lerp(partial, p.yo, p.getY()), lz = Mth.lerp(partial, p.zo, p.getZ());
        double off = Math.sqrt((dx - lx) * (dx - lx) + (dy - ly) * (dy - ly) + (dz - lz) * (dz - lz));
        if (off > 1.5) return;
        frameFix = Math.max(frameFix, off);
        rx = p.getX(); ry = p.getY(); rz = p.getZ(); rxo = p.xo; ryo = p.yo; rzo = p.zo;
        p.setPos(dx, dy, dz);
        p.xo = dx; p.yo = dy; p.zo = dz;
        moved = true;
    }

    /** after the frame: your game's own position back */
    public static void afterFrame() { restore(); }

    private static void restore() {
        if (!moved) return;
        moved = false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setPos(rx, ry, rz);
        p.xo = rxo; p.yo = ryo; p.zo = rzo;
    }

    public static void clear() { restore(); CARRY.clear(); frameYaw = Float.NaN; frameKey = Integer.MIN_VALUE; }
}
