package net.jj.hollowbell.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What your crosshair is on: after the game picks, this traces your view through his blocks and, if one is nearer,
 * points you at him. The bone goes with the swing (see HitPayload). It traces him as of this tick and as he was last
 * drawn: his arms and strands swing a good way in a tick, and a swing goes by the tick while you aim at the picture.
 */
public final class BellPick {
    private BellPick() {}

    /** the bone of him under the crosshair right now, or -1 */
    public static int bone = -1;

    public static int bellId = -1;

    /** how far into the tick the last frame was drawn */
    private static float drawnPartial = 1f;

    private static final Map<HollowbellEntity, Matrix4f[]> DRAWN = new WeakHashMap<>();

    public static void afterPick(float partial) {
        bone = -1;
        bellId = -1;
        // (the game picks once a tick at 1 and once a frame at the frame's own time)
        if (partial < 1f) drawnPartial = partial;
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (mc.level == null || mc.player == null || cam == null || cam instanceof HollowbellEntity) return;
        double reach = mc.player.entityInteractionRange();
        Vec3 eye = cam.getEyePosition(partial), look = cam.getViewVector(partial);
        HitResult was = mc.hitResult;
        double best = was == null || was.getType() == HitResult.Type.MISS
                ? reach
                : Math.min(reach, was.getLocation().distanceTo(eye));
        HollowbellEntity got = null;
        int gotBone = -1;
        double gotT = best;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h) || h.isRemoved() || !h.clientPoseReady()) continue;
            // (all of him, arms swung right out too, is within about 380 blocks of the spot under him at size 1)
            double far = 400 * h.bellScale() + reach + 4;
            if (eye.distanceToSqr(h.position()) > far * far) continue;
            h.ensurePose();
            BellRig.Hit hit = h.raycast(eye, look, gotT);
            if (hit != null && hit.t() < gotT) {
                got = h;
                gotBone = hit.bone();
                gotT = hit.t();
            }
            hit = drawnRay(h, partial < 1f ? partial : drawnPartial, eye, look, gotT);
            if (hit != null && hit.t() < gotT) {
                got = h;
                gotBone = hit.bone();
                gotT = hit.t();
            }
        }
        if (got == null) return;
        bone = gotBone;
        bellId = got.getId();
        mc.hitResult = new EntityHitResult(got, eye.add(look.scale(gotT)));
        mc.crosshairPickEntity = got;
    }

    /** the first block of him along a look, as he is drawn at partial */
    private static BellRig.@Nullable Hit drawnRay(HollowbellEntity h, float partial, Vec3 eye, Vec3 look, double max) {
        Matrix4f[] slices = DRAWN.computeIfAbsent(h, x -> BellPieces.get().newPose());
        h.solidPoseAt(partial, slices);
        Matrix4f toModel = h.modelToWorld(partial).invert();
        Vector3f from = toModel.transformPosition((float) eye.x, (float) eye.y, (float) eye.z, new Vector3f());
        Vec3 u = look.normalize();
        Vector3f dir = toModel.transformDirection((float) u.x, (float) u.y, (float) u.z, new Vector3f());
        return h.rig.raycast(h.state, h.pose, slices, from, dir, (float) max);
    }

    public static @Nullable HollowbellEntity looking() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && bellId >= 0 && mc.level.getEntity(bellId) instanceof HollowbellEntity h ? h : null;
    }
}
