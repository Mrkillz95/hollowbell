package net.jj.mountain.client;

import net.jj.mountain.client.render.VoxelModel;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.rig.MountainRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Client-side life: black smoke puffing from the holes all over him when he breathes out, goo dripping and
 * splashing under his mouth.
 */
public final class ClientEffects {
    private ClientEffects() {}

    public static void tick(MountainEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // the whole world jolts when he finally goes over
        if (e.isDeadOrDying() && e.deathTime == MountainEntity.FALL_AT)
            Shake.crash(Math.sqrt(mc.player.distanceToSqr(e)), e.mountainScale());
        if (mc.options.particles().get().getId() == 2) return;
        double dist = Math.sqrt(mc.player.distanceToSqr(e));
        float s = e.mountainScale();
        if (dist > 300 * Math.max(s, 0.2) + 60 || !e.clientPoseReady()) return;
        List<VoxelModel.Emitter> em = VoxelModel.MOUNTAIN.emitters();
        if (em.isEmpty()) return;
        RandomSource r = e.getRandom();
        float budget = 1f + 8f * s;
        if (mc.options.particles().get().getId() == 1) budget *= 0.4f;
        boolean exhale = e.stage() == MountainEntity.EXHALE && e.stageTime() < 25;
        Vector3f tmp = new Vector3f();
        for (VoxelModel.Emitter m : em) {
            if (m.kind == 0) {
                // the holes: smoke on the out-breath
                if (!exhale || r.nextFloat() > budget * 0.02f) continue;
                Vec3 p = e.clientWorld(tmp.set(m.x, m.y, m.z), m.bone);
                for (int i = 0; i < 2; i++)
                    e.level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.1, 0.05 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.1);
                e.level().addParticle(ParticleTypes.SQUID_INK, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.2, 0.1, (r.nextDouble() - 0.5) * 0.2);
            } else if (m.kind == 1 && e.gooOn() && e.sleepAmount() < 0.5f && !(e.isDeadOrDying() && e.deathTime > 150)) {
                // goo coming off the tips of the strings, splashing where it lands
                if (r.nextFloat() > 0.08f + budget * 0.03f) continue;
                Vec3 p = e.clientWorld(tmp.set(m.x, m.y, m.z), m.bone);
                e.level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState()),
                        p.x + (r.nextDouble() - 0.5) * s * 2, p.y, p.z + (r.nextDouble() - 0.5) * s * 2, 0, 0, 0);
                int gy = e.groundAt(Mth.floor(p.x), Mth.floor(p.z));
                if (r.nextInt(3) == 0)
                    e.level().addParticle(ParticleTypes.SQUID_INK, p.x + (r.nextDouble() - 0.5) * 2 * s, gy + 0.2, p.z + (r.nextDouble() - 0.5) * 2 * s,
                            (r.nextDouble() - 0.5) * 0.15, 0.08 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.15);
            }
        }
    }
}
