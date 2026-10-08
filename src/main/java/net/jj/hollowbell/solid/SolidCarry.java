package net.jj.hollowbell.solid;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Carrying one entity along on a creature, the way a player's own game does it at the end of each tick: moved by as
 * much as the spot of him under its feet moved (his turning, leaning and bobbing too), then the spot looked for again.
 * After a jump, or a step off an edge, it is still carried while he's under it (up to 7 blocks down), so jumping on him
 * is like jumping on the ground. Used by the client for you (SolidClient) and by the tests for their players.
 *
 * It also keeps where you were on him as of the tick before, so the client can put you on him every frame from the
 * very pose he is drawn in.
 */
public final class SolidCarry {
    public @Nullable SolidBody on;
    public int frame = -1;
    /** the spot carried (its own position, in the frame as built), and where that was in the world */
    public @Nullable Vector3f rest;
    public @Nullable Vec3 was;
    /** the spot as of the tick before, in the same frame (null if it changed) */
    public @Nullable Vector3f restPrev;
    /** which way the frame faced when the spot was taken (degrees) */
    public float yaw;
    /** ticks carried off his top (jumping, stepping off) */
    public int air;

    /**
     * One tick: carried, then the spot found again. Returns how far the frame turned (degrees) since the last tick,
     * for turning a mob's facing with him (a player's view is turned every frame instead).
     */
    public float step(Entity p, List<SolidBody> bodies) {
        float turned = 0f;
        restPrev = null;
        SolidBody b = on;
        Vector3f oldRest = rest;
        int oldFrame = frame;
        if (b != null && rest != null && was != null && Solid.live(b, p)) {
            SolidCache c = Solid.frames(b);
            if (frame < c.toWorld.length && c.on[frame]) {
                Vec3 d = Solid.toWorld(c, frame, rest).subtract(was);
                if (d.lengthSqr() < 64) {
                    Vec3 to = new Vec3(p.getX() + d.x, p.getY() + d.y, p.getZ() + d.z);
                    if (air == 0) to = Solid.carriedTo(b, to);
                    p.setPos(to.x, to.y, to.z);
                }
                turned = Mth.wrapDegrees(Solid.yawOf(c.toWorld[frame]) - yaw);
            }
        }
        Solid.Anchor a = null;
        SolidBody found = null;
        for (SolidBody x : bodies) {
            a = Solid.anchorUnder(x, p, 0.9, 1.5);
            if (a != null) { found = x; break; }
        }
        int nowAir = 0;
        if (found == null && b != null && !p.onGround() && air < 100 && !(p.isInWater() && !p.onGround())) {
            a = Solid.anchorUnder(b, p, 7.0, 0.0);
            if (a != null) { found = b; nowAir = air + 1; }
        }
        if (found == null) { clear(); return turned; }
        SolidCache c = Solid.frames(found);
        if (found == b && a.frame() == oldFrame && oldRest != null) restPrev = oldRest;
        on = found; frame = a.frame(); rest = a.rest(); was = p.position(); yaw = Solid.yawOf(c.toWorld[frame]); air = nowAir;
        return turned;
    }

    public boolean riding() { return on != null; }

    public void clear() { on = null; frame = -1; rest = null; was = null; restPrev = null; air = 0; }
}
