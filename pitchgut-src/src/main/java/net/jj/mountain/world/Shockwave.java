package net.jj.mountain.world;

import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The wave that goes out after the hole. It does not take the ground with it — a thousand blocks of that would
 * be millions of blocks and the game would stop — but everything standing in its way goes over, the trees near
 * the middle come down, and you can hear it coming a long way before it reaches you.
 */
public final class Shockwave {
    private static final List<Shockwave> live = new ArrayList<>();
    /** how fast the edge travels, in blocks a tick */
    private static final double SPEED = 7.0;
    /** past this the trees stay standing: snapping every tree for a thousand blocks costs more than it is worth */
    private static final double SNAP_TO = 360;

    private final ServerLevel lvl;
    private final Vec3 mid;
    private final double max;
    private final float hurt;
    private final UUID spare;
    private final Set<Integer> hit = new HashSet<>();
    private double at;

    private Shockwave(ServerLevel lvl, Vec3 mid, double max, float hurt, UUID spare) {
        this.lvl = lvl;
        this.mid = mid;
        this.max = Math.max(32, max);
        this.hurt = hurt;
        this.spare = spare;
    }

    public static void start(ServerLevel lvl, Vec3 mid, double max, float hurt, UUID spare) {
        live.add(new Shockwave(lvl, mid, max, hurt, spare));
    }

    public static int running() { return live.size(); }

    public static void forgetEverything() { live.clear(); }

    public static void serverTick(MinecraftServer server) {
        if (live.isEmpty()) return;
        for (Iterator<Shockwave> it = live.iterator(); it.hasNext(); ) if (it.next().step()) it.remove();
    }

    private boolean step() {
        if (lvl.getServer() == null || !lvl.getServer().isRunning()) return true;
        double was = at;
        at += SPEED;
        double band = SPEED + 6;

        // everything the edge passes over goes off its feet, once
        AABB box = new AABB(mid.x - at - band, lvl.getMinBuildHeight(), mid.z - at - band,
                mid.x + at + band, lvl.getMaxBuildHeight(), mid.z + at + band);
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, box,
                x -> x.isAlive() && !(x instanceof MountainEntity) && !(x instanceof HeartEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e.getUUID().equals(spare)) continue;
            double d = Math.hypot(e.getX() - mid.x, e.getZ() - mid.z);
            if (d < was - band || d > at) continue;
            if (!hit.add(e.getId())) continue;
            float f = (float) Math.max(0.15, 1.0 - d / max);
            e.hurt(lvl.damageSources().explosion(null, null), hurt * f);
            Vec3 out = new Vec3(e.getX() - mid.x, 0, e.getZ() - mid.z);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(1, 0, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(1.2 + 1.6 * f)).add(0, 0.7 + 0.8 * f, 0));
            e.hurtMarked = true;
        }

        // the trees near the middle come down as it goes past
        if (at < SNAP_TO) {
            int n = (int) Math.min(40, 10 + at / 10);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + at * 0.03;
                double x = mid.x + Math.cos(a) * at, z = mid.z + Math.sin(a) * at;
                BlockPos p = new BlockPos((int) Math.floor(x), (int) mid.y, (int) Math.floor(z));
                if (!lvl.hasChunkAt(p)) continue;
                MountainEntity.flattenAt(lvl, x, z, 10, 26);
            }
        }

        // and you hear and see it arrive
        int marks = (int) Math.min(64, 16 + at / 12);
        for (int i = 0; i < marks; i++) {
            double a = i * Math.PI * 2 / marks;
            double x = mid.x + Math.cos(a) * at, z = mid.z + Math.sin(a) * at;
            if (!lvl.hasChunkAt(new BlockPos((int) x, (int) mid.y, (int) z))) continue;
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            lvl.sendParticles(ParticleTypes.EXPLOSION, x, y + 1, z, 2, 2, 1, 2, 0.0);
            lvl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 6, 3, 2, 3, 0.02);
        }
        if ((int) (at / SPEED) % 8 == 0) {
            for (var p : lvl.players()) {
                double d = Math.hypot(p.getX() - mid.x, p.getZ() - mid.z);
                if (Math.abs(d - at) < 140)
                    lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.HOSTILE, 4f, 0.3f);
            }
        }
        return at >= max;
    }
}
