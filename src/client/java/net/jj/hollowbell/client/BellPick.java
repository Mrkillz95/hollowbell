package net.jj.hollowbell.client;

import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What your crosshair is on. The game only knows him as one small box at his middle, so after it has picked, this
 * looks along your view through the blocks he is actually made of and, if one of them is nearer, points you at him.
 * The bone you are on goes with the swing (see HitPayload).
 */
public final class BellPick {
    private BellPick() {}

    /** the bone of him under the crosshair right now, or -1 */
    public static int bone = -1;
    public static int bellId = -1;

    public static void afterPick(float partial) {
        bone = -1; bellId = -1;
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (mc.level == null || mc.player == null || cam == null || cam instanceof HollowbellEntity) return;
        double reach = mc.player.entityInteractionRange();
        Vec3 eye = cam.getEyePosition(partial), look = cam.getViewVector(partial);
        HitResult was = mc.hitResult;
        double best = was == null || was.getType() == HitResult.Type.MISS ? reach : Math.min(reach, was.getLocation().distanceTo(eye));
        HollowbellEntity got = null; int gotBone = -1; double gotT = best;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h) || h.isRemoved() || !h.clientPoseReady()) continue;
            if (!h.bodyBox().inflate(reach).contains(eye)) continue;
            h.ensurePose();
            BellRig.Hit hit = h.raycast(eye, look, gotT);
            if (hit != null && hit.t() < gotT) { got = h; gotBone = hit.bone(); gotT = hit.t(); }
        }
        if (got == null) return;
        bone = gotBone; bellId = got.getId();
        mc.hitResult = new EntityHitResult(got, eye.add(look.scale(gotT)));
        mc.crosshairPickEntity = got;
    }

    public static @Nullable HollowbellEntity looking() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && bellId >= 0 && mc.level.getEntity(bellId) instanceof HollowbellEntity h ? h : null;
    }
}
