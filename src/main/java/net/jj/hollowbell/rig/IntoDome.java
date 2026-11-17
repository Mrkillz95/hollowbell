package net.jj.hollowbell.rig;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The way something he swallows is drawn up into his dome (model space): from the end of the strand that holds it,
 * in under the rim toward his middle, then up into the hollow to where it will stay, never through the glass. A
 * smooth curve (two Bezier pieces meeting heading straight up under the rim), slow off the strand, quickening as the
 * bell sucks it in and slowing as it settles.
 */
public final class IntoDome {
    private IntoDome() {}

    /** how far under the rim it passes in (model blocks), and how far in from the rim's inside */
    static final float UNDER = 8f, IN = 0.82f;

    /** the share of the way (in time) spent coming in under the rim, before going up into the hollow */
    static final float FIRST = 0.45f;

    /** where it is a share k (0-1) of the way from s (the strand's end) to e (its place in the dome) */
    public static Vector3f at(BellRig rig, Vector3f s, Vector3f e, float k, Vector3f out) {
        float u = Mth.clamp(k, 0f, 1f);
        // slow off the strand, sucked in fast, slowing into its place
        u = u * u * u * (u * (u * 6f - 15f) + 10f);
        float low = Math.min(s.y, rig.rimY - UNDER);
        float rIn = IN * rig.domeInner(rig.rimY);
        // under the rim, on the way from where it hangs to under its place
        float bx = Mth.lerp(0.7f, s.x, e.x), bz = Mth.lerp(0.7f, s.z, e.z);
        float br = (float) Math.sqrt(bx * bx + bz * bz);
        if (br > rIn) {
            bx *= rIn / br;
            bz *= rIn / br;
        }
        float up = Math.max(4f, (e.y - low) * 0.4f);
        // (how far it swings under before turning up: a little, so it never dips far toward the ground; the second
        // piece leaves at the same speed the first came in at)
        float dip = Math.min(up * 0.5f, 6f), rise = dip * (1f - FIRST) / FIRST;
        if (u < FIRST) {
            float v = u / FIRST;
            // (it leaves the strand toward the middle and comes in under the rim heading up)
            return bez(
                    s.x, s.y, s.z,
                    Mth.lerp(0.5f, s.x, bx), low, Mth.lerp(0.5f, s.z, bz),
                    bx, low - dip, bz,
                    bx, low, bz,
                    v, out);
        }
        float v = (u - FIRST) / (1f - FIRST);
        return bez(bx, low, bz, bx, low + rise, bz, e.x, e.y - up, e.z, e.x, e.y, e.z, v, out);
    }

    private static Vector3f bez(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            float t, Vector3f out) {
        float m = 1f - t, a = m * m * m, b = 3f * m * m * t, c = 3f * m * t * t, d = t * t * t;
        return out.set(
                a * ax + b * bx + c * cx + d * dx,
                a * ay + b * by + c * cy + d * dy,
                a * az + b * bz + c * cz + d * dz);
    }
}
