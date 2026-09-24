package net.jj.mountain.client;

import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.jj.mountain.net.StrugglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Climbing him, and fighting out of his hand.
 *
 * You take hold of whatever piece of him you are against — a leg, his flank, his head — and from then on you are
 * stuck to the outside of it: every tick the hold is worked out again from where that piece is now, so when he
 * walks or turns he carries you with him instead of leaving you in the air. Jump goes up, crouch goes down, and
 * letting go of both drops you off. Climbing past his hip the nearest piece of him becomes his side, so a leg
 * takes you all the way up onto his back.
 *
 * Fighting out of a hand is the other half: hammering jump sends a count to the server, which decides when the
 * fingers come apart.
 */
public final class Climbing {
    private Climbing() {}

    private static boolean jumpWasDown;
    private static int presses, sendIn;

    /** how long you stay stuck to him after you stop asking to go up or down */
    private static final int HANG = 8;
    private static int holding;
    private static double lastX, lastZ;
    private static boolean attached;

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { presses = 0; attached = false; return; }

        // ---- fighting out of a hand
        MountainEntity holder = null;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof MountainEntity m && m.held() == p) { holder = m; break; }
        boolean jump = mc.options.keyJump.isDown();
        if (holder != null) {
            if (jump && !jumpWasDown) presses++;
            if (--sendIn <= 0) {
                sendIn = 4;
                if (presses > 0) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new StrugglePayload(presses));
                    presses = 0;
                }
            }
            jumpWasDown = jump;
            attached = false;
            return;
        }
        jumpWasDown = jump;
        presses = 0;

        if (p.isSpectator() || p.isPassenger() || p.getAbilities().flying) { attached = false; return; }
        boolean down = mc.options.keyShift.isDown();
        if (!jump && !down && !attached) return;

        MountainEntity him = nearest(mc, p);
        if (him == null || !him.clientPoseReady()) { attached = false; return; }

        Vec3 me = p.position().add(0, p.getBbHeight() * 0.5, 0);
        Hold h = bestHold(him, p, me);
        if (h == null) { attached = false; return; }

        double gap = Math.hypot(me.x - h.surface.x, me.z - h.surface.z) - h.radius;
        if (!attached) {
            if (gap > 1.5 || (!jump && !down)) return;                 // you have to be against him, asking to climb
            attached = true;
            holding = HANG;
            lastX = p.getX(); lastZ = p.getZ();
        }
        if (jump || down) holding = HANG; else holding--;
        if (holding <= 0 || gap > 3.5) { attached = false; return; }

        // ---- sit on the outside of whatever piece of him is nearest, wherever it has got to this tick
        Vec3 out = new Vec3(me.x - h.surface.x, 0, me.z - h.surface.z);
        if (out.lengthSqr() < 1e-4) out = new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0, Math.cos(Math.toRadians(p.getYRot())));
        out = out.normalize();
        double wantX = h.surface.x + out.x * (h.radius + 0.45);
        double wantZ = h.surface.z + out.z * (h.radius + 0.45);
        // never snap: a step at a time, so his turning drags you round rather than flinging you
        double dx = clamp(wantX - p.getX(), 3.0), dz = clamp(wantZ - p.getZ(), 3.0);
        double rise = jump ? 0.24 : down ? -0.20 : 0.0;
        // hauling yourself over the lip of his body: while you are still under it, go up harder
        if (jump && !Double.isNaN(h.floorY) && me.y < h.floorY + 1.0) rise = 0.42;
        p.setPos(p.getX() + dx, p.getY() + rise, p.getZ() + dz);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0f;
        p.setOnGround(false);
        p.horizontalCollision = false;
        if (p.tickCount % 10 == 0)
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.SLIME_BLOCK_STEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.4f, false);
        lastX = p.getX(); lastZ = p.getZ();
    }

    private static double clamp(double v, double max) { return Math.max(-max, Math.min(max, v)); }

    private static @Nullable MountainEntity nearest(Minecraft mc, LocalPlayer p) {
        MountainEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MountainEntity m)) continue;
            double d = m.distanceToSqr(p);
            if (d < bd) { bd = d; best = m; }
        }
        if (best == null) return null;
        float s = best.mountainScale();
        return bd > Math.pow(340 * s + 90, 2) ? null : best;
    }

    /** a piece of him to hold: a point on its middle, how fat it is there, and the floor of it if it is a body piece */
    private record Hold(Vec3 surface, double radius, double floorY) {}

    private static @Nullable Hold bestHold(MountainEntity him, LocalPlayer p, Vec3 me) {
        Hold best = null;
        double bestGap = Double.MAX_VALUE;
        // his legs: thin, held round the middle of the limb
        for (int k = 0; k < him.legCount(); k++) {
            Vec3[] line = him.legLine(k);
            double r = Math.max(0.5, him.legThickness(k) * 0.5);
            for (int i = 0; i < line.length - 1; i++) {
                Vec3 c = closest(me, line[i], line[i + 1]);
                double gap = Math.hypot(me.x - c.x, me.z - c.z) - r;
                double up = Math.abs(me.y - c.y);
                if (up > r + 3.5) continue;                            // only the part of the leg beside you
                // at the top of a leg the leg stops being a hold, so the nearest piece becomes his body and you
                // are carried up over the hip onto his side instead of being stuck under him
                if (me.y > line[0].y - 1.5) continue;
                if (gap < bestGap) { bestGap = gap; best = new Hold(c, r, Double.NaN); }
            }
        }
        // and every other piece of him: body, head, arms
        for (MountainPart part : him.parts()) {
            AABB box = part.getBoundingBox();
            if (me.y < box.minY - 3.0 || me.y > box.maxY + 3.0) continue;
            Vec3 c = new Vec3(box.getCenter().x, me.y, box.getCenter().z);
            double r = radiusToward(box, me.x - c.x, me.z - c.z);
            double gap = Math.hypot(me.x - c.x, me.z - c.z) - r;
            if (gap < bestGap) { bestGap = gap; best = new Hold(c, r, box.minY); }
        }
        return best;
    }

    /** how far it is from the middle of a box out to its edge, the way you are standing */
    private static double radiusToward(AABB box, double dx, double dz) {
        double hx = box.getXsize() * 0.5, hz = box.getZsize() * 0.5;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return Math.max(hx, hz);
        double ux = dx / len, uz = dz / len;
        double tx = Math.abs(ux) < 1e-6 ? Double.MAX_VALUE : hx / Math.abs(ux);
        double tz = Math.abs(uz) < 1e-6 ? Double.MAX_VALUE : hz / Math.abs(uz);
        return Math.min(tx, tz);
    }

    private static Vec3 closest(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        if (len < 1e-9) return a;
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return a.add(ab.scale(t));
    }
}
