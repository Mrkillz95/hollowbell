package net.jj.hollowbell.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Little things the client adds round him: glow drifting off the spots and the crown at night, drips off the strands. */
public final class BellFx {
    private BellFx() {}

    static void ambient(HollowbellEntity h) {
        if (h.isDeadOrDying()) return;
        RandomSource r = h.getRandom();
        var rig = h.rig;
        float s = h.bellScale();
        boolean night = h.level().getSkyDarken() > 6 || h.level().isThundering();
        int n = night ? 3 : 1;
        for (int i = 0; i < n; i++) {
            var S = rig.spots[r.nextInt(rig.spots.length)];
            Vec3 c = h.toWorld(new Vector3f(S.centre()).add((r.nextFloat() - 0.5f) * 30f, (r.nextFloat() - 0.2f) * 20f, (r.nextFloat() - 0.5f) * 30f));
            h.level().addParticle(night ? ParticleTypes.GLOW : ParticleTypes.END_ROD, c.x, c.y, c.z, 0, 0.01 * s, 0);
        }
        if (r.nextInt(3) == 0) {
            var St = rig.strands[r.nextInt(rig.strands.length)];
            Vec3 c = h.toWorld(new Vector3f(St.bottom()).add(0, 3 + r.nextFloat() * 40f, 0));
            h.level().addParticle(ParticleTypes.DRIPPING_WATER, c.x, c.y, c.z, 0, 0, 0);
        }
    }
}
