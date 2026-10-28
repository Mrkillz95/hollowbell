package net.jj.hollowbell.solid.client;

import net.jj.hollowbell.solid.SitHost;
import net.jj.hollowbell.solid.SitSeat;
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

    public static void tick(Minecraft mc) {
        restore();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            clear();
            return;
        }
        if (p.isSpectator() || p.isPassenger() || p.noPhysics) {
            clear();
            return;
        }
        long t0 = System.nanoTime();
        // carried first (moved with the part you stand on), then out of him if he's still into you
        if (p.getAbilities().flying || (p.isInWater() && !p.onGround())) CARRY.clear();
        else CARRY.step(p, Solid.bodies(true));
        boolean onHim = CARRY.riding() && CARRY.air == 0;
        for (SolidBody b : Solid.bodies(true)) {
            if (b.solidSelf().level() != p.level() || !b.solidReady() || b.solidIgnores(p)) continue;
            if (Solid.unstick(b, p, 0.08, onHim && CARRY.on == b)) {
                unstuck++;
                Solid.unstuckClient++;
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
                if (Solid.softPush(b, p)) softPushed++;
            }
        }
        Solid.nanosClient += System.nanoTime() - t0;
    }

    /**
     * before a frame is drawn: you where the spot under you is in the pose he's drawn in, and your view turned with him
     */
    public static void beforeFrame(float partial) {
        restore();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && p.getVehicle() instanceof SitSeat seat) {
            sitFrame(p, seat, partial);
            return;
        }
        SolidBody b = CARRY.on;
        if (p == null || b == null || CARRY.rest == null || !b.solidReady() || p.isPassenger()) {
            frameYaw = Float.NaN;
            return;
        }
        Entity s = b.solidSelf();
        int bone = b.solidShape().bone[CARRY.frame];
        Matrix4f[] tickPose = b.solidPose();
        if (scratch == null || scratch.length != tickPose.length) {
            scratch = new Matrix4f[tickPose.length];
            for (int i = 0; i < scratch.length; i++) scratch[i] = new Matrix4f();
        }
        b.solidPoseAt(partial, scratch);
        Matrix4f f = b.solidRoot(partial, new Matrix4f()).mul(scratch[bone]);
        double ex = Mth.lerp(partial, s.xo, s.getX()),
                ey = Mth.lerp(partial, s.yo, s.getY()),
                ez = Mth.lerp(partial, s.zo, s.getZ());
        // his turning turns your view, smoothly
        float yaw = Solid.yawOf(f);
        int key = s.getId() * 4096 + CARRY.frame;
        if (!Float.isNaN(frameYaw) && key == frameKey) {
            float dy = Mth.wrapDegrees(yaw - frameYaw);
            if (Math.abs(dy) < 20f) {
                p.setYRot(p.getYRot() + dy);
                p.yRotO += dy;
                p.setYBodyRot(p.yBodyRot + dy);
                p.yBodyRotO += dy;
                p.setYHeadRot(p.getYHeadRot() + dy);
                p.yHeadRotO += dy;
            }
        }
        frameYaw = yaw;
        frameKey = key;
        if (CARRY.restPrev == null) return;
        Vector3f r = new Vector3f(CARRY.restPrev).lerp(CARRY.rest, Mth.clamp(partial, 0f, 1f));
        Vector3f w = f.transformPosition(r);
        double dx = w.x + ex, dy = w.y + ey, dz = w.z + ez;
        double lx = Mth.lerp(partial, p.xo, p.getX()),
                ly = Mth.lerp(partial, p.yo, p.getY()),
                lz = Mth.lerp(partial, p.zo, p.getZ());
        double off = Math.sqrt((dx - lx) * (dx - lx) + (dy - ly) * (dy - ly) + (dz - lz) * (dz - lz));
        if (off > 1.5) return;
        frameFix = Math.max(frameFix, off);
        rx = p.getX();
        ry = p.getY();
        rz = p.getZ();
        rxo = p.xo;
        ryo = p.yo;
        rzo = p.zo;
        p.setPos(dx, dy, dz);
        p.xo = dx;
        p.yo = dy;
        p.zo = dz;
        moved = true;
    }

    /**
     * sitting on him: you where your seat's spot is in the pose he's drawn in this frame (so you never lag a tick
     * behind the part you sit on), and your view turned with that part
     */
    private static void sitFrame(LocalPlayer p, SitSeat seat, float partial) {
        SitHost h = seat.host();
        int fr = seat.frame();
        if (h == null || !h.solidReady() || fr < 0 || fr >= h.solidShape().frames()) {
            frameYaw = Float.NaN;
            return;
        }
        Entity s = h.solidSelf();
        int bone = h.solidShape().bone[fr];
        Matrix4f[] tickPose = h.solidPose();
        if (scratch == null || scratch.length != tickPose.length) {
            scratch = new Matrix4f[tickPose.length];
            for (int i = 0; i < scratch.length; i++) scratch[i] = new Matrix4f();
        }
        h.solidPoseAt(partial, scratch);
        Matrix4f f = h.solidRoot(partial, new Matrix4f()).mul(scratch[bone]);
        float yaw = Solid.yawOf(f);
        int key = s.getId() * 4096 + fr + 2048;
        if (!Float.isNaN(frameYaw) && key == frameKey) {
            float dy = Mth.wrapDegrees(yaw - frameYaw);
            if (Math.abs(dy) < 20f) {
                p.setYRot(p.getYRot() + dy);
                p.yRotO += dy;
                p.setYBodyRot(p.yBodyRot + dy);
                p.yBodyRotO += dy;
                p.setYHeadRot(p.getYHeadRot() + dy);
                p.yHeadRotO += dy;
            }
        }
        frameYaw = yaw;
        frameKey = key;
        Vector3f w = f.transformPosition(seat.rest());
        double dx = w.x + Mth.lerp(partial, s.xo, s.getX()),
                dy = w.y + Mth.lerp(partial, s.yo, s.getY()) + SitSeat.SIT_OVER - p.getVehicleAttachmentPoint(seat).y,
                dz = w.z + Mth.lerp(partial, s.zo, s.getZ());
        double lx = Mth.lerp(partial, p.xo, p.getX()),
                ly = Mth.lerp(partial, p.yo, p.getY()),
                lz = Mth.lerp(partial, p.zo, p.getZ());
        double off = Math.sqrt((dx - lx) * (dx - lx) + (dy - ly) * (dy - ly) + (dz - lz) * (dz - lz));
        if (off > 4.0) return;
        frameFix = Math.max(frameFix, off);
        rx = p.getX();
        ry = p.getY();
        rz = p.getZ();
        rxo = p.xo;
        ryo = p.yo;
        rzo = p.zo;
        p.setPos(dx, dy, dz);
        p.xo = dx;
        p.yo = dy;
        p.zo = dz;
        moved = true;
    }

    /** after the frame: your game's own position back */
    public static void afterFrame() {
        restore();
    }

    private static void restore() {
        if (!moved) return;
        moved = false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setPos(rx, ry, rz);
        p.xo = rxo;
        p.yo = ryo;
        p.zo = rzo;
    }

    public static void clear() {
        restore();
        CARRY.clear();
        frameYaw = Float.NaN;
        frameKey = Integer.MIN_VALUE;
    }
}
