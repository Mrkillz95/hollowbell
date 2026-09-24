package net.jj.mountain.client;

import net.jj.mountain.entity.MountainCollision;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Carries you along when you stand on his back. At the end of every tick (after you have walked) the spot under your
 * feet is followed on his moving, breathing body and you are moved by the same amount, so the game draws your
 * carried movement smoothly between ticks, just like his. His turning turns your view every frame, not in steps.
 */
public final class RiderCarry {
    private RiderCarry() {}

    private static int mountain = -1;
    private static MountainEntity.Anchor anchor;
    private static Vec3 was;
    private static float frameYaw = Float.NaN;

    /** end of each client tick */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { clear(); return; }
        if (p.isSpectator() || p.isPassenger() || p.getAbilities().flying) { clear(); return; }
        MountainEntity on = mountain >= 0 && mc.level.getEntity(mountain) instanceof MountainEntity m && !m.isDeadOrDying() && m.clientPoseReady() ? m : null;
        if (on != null && anchor != null) {
            Vec3 d = on.anchorWorld(anchor).subtract(was);
            if (d.lengthSqr() < 36) {
                Vec3 to = on.carriedTo(anchor, new Vec3(p.getX() + d.x, p.getY() + d.y, p.getZ() + d.z));
                p.setPos(to.x, to.y, to.z);
            }
        }
        MountainEntity.Anchor a = null; MountainEntity found = null;
        for (MountainEntity m : MountainCollision.clientMountains()) {
            if (m.level() != p.level() || m.isDeadOrDying() || !m.clientPoseReady()) continue;
            a = m.anchorUnder(p);
            if (a != null) { found = m; break; }
        }
        if (found == null) { clear(); return; }
        if (found.getId() != mountain) frameYaw = Float.NaN;
        mountain = found.getId(); anchor = a; was = p.position();
    }

    /** every frame: turn your view with his body, smoothly */
    static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mountain < 0) { frameYaw = Float.NaN; return; }
        Entity e = mc.level.getEntity(mountain);
        if (!(e instanceof MountainEntity m)) { frameYaw = Float.NaN; return; }
        float yaw = Mth.rotLerp(partial, m.yRotO, m.getYRot());
        if (!Float.isNaN(frameYaw)) {
            float dy = Mth.wrapDegrees(yaw - frameYaw);
            if (Math.abs(dy) < 20f) {
                p.setYRot(p.getYRot() + dy); p.yRotO += dy;
                p.setYBodyRot(p.yBodyRot + dy); p.yBodyRotO += dy;
                p.setYHeadRot(p.getYHeadRot() + dy); p.yHeadRotO += dy;
            }
        }
        frameYaw = yaw;
    }

    private static void clear() { mountain = -1; anchor = null; was = null; frameYaw = Float.NaN; }
}
