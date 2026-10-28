package net.jj.hollowbell.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Whether a giant sees a player well enough to go after them: lenient, as befits something this big. He looks from
 * several points on his body (eyes, head, middle) to the player's eyes and the top of their head, and a block or so
 * right next to the player doesn't hide them (a fence post, one block in the way, the lip of a hole). Only solid,
 * see-proof blocks stop his eye: glass, leaves, fences' gaps and the like don't. Land that isn't loaded counts as
 * clear (it is never read, so nothing waits for it).
 *
 * <p>Used only when he picks someone new: one he is already after he remembers for a while ({@link #MEMORY}) out of
 * sight before he lets them go, and one he holds a grudge against he hunts seen or not.
 */
public final class HuntSight {
    private HuntSight() {}

    /** how long (ticks) a player he's after can be out of his sight before he gives them up: ten seconds */
    public static final int MEMORY = 200;

    /** a block this close (blocks) to the player doesn't hide them */
    public static final double SLACK = 1.5;

    /** how often (ticks) he looks again at one he's after */
    public static final int LOOK_EVERY = 10;

    /** on Peaceful no giant hunts a player */
    public static boolean peaceful(Level l) {
        return l.getDifficulty() == Difficulty.PEACEFUL;
    }

    /** the points on someone he looks at: their eyes and the top of their head */
    public static Vec3[] marks(LivingEntity who) {
        Vec3 eye = who.getEyePosition();
        double top = who.getY() + who.getBbHeight() - 0.05;
        return new Vec3[] {eye, new Vec3(who.getX(), Math.max(top, eye.y), who.getZ())};
    }

    /** from any of his points to either mark on them, a clear enough line */
    public static boolean sees(Level l, List<Vec3> from, LivingEntity who) {
        Vec3[] to = marks(who);
        for (Vec3 f : from) {
            if (f == null) continue;
            for (Vec3 t : to) if (clear(l, f, t, SLACK)) return true;
        }
        return false;
    }

    /**
     * No see-proof block on the way from {@code from} to {@code to}, short of the last {@code slack} blocks, in loaded
     * land (half-block steps).
     */
    public static boolean clear(Level l, Vec3 from, Vec3 to, double slack) {
        double len = from.distanceTo(to);
        double end = len - slack;
        if (end <= 0) return true;
        double dx = (to.x - from.x) / len, dy = (to.y - from.y) / len, dz = (to.z - from.z) / len;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        int ccx = Integer.MIN_VALUE, ccz = Integer.MIN_VALUE;
        int lx = Integer.MIN_VALUE, ly = Integer.MIN_VALUE, lz = Integer.MIN_VALUE;
        for (double s = 0.5; s < end; s += 0.5) {
            double x = from.x + dx * s, y = from.y + dy * s, z = from.z + dz * s;
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            if (l.isOutsideBuildHeight(by)) continue;
            int cx = bx >> 4, cz = bz >> 4;
            if (cx != ccx || cz != ccz) {
                ccx = cx;
                ccz = cz;
                chunk = l.getChunkSource().getChunkNow(cx, cz);
            }
            if (chunk == null) continue;
            // (the same open block as the last step: nothing new to read)
            if (bx == lx && by == ly && bz == lz) continue;
            q.set(bx, by, bz);
            BlockState st = chunk.getBlockState(q);
            if (st.isAir() || !st.canOcclude()) {
                lx = bx;
                ly = by;
                lz = bz;
                continue;
            }
            lx = Integer.MIN_VALUE;
            VoxelShape sh = st.getCollisionShape(l, q);
            if (sh.isEmpty()) continue;
            if (sh == Shapes.block()) return false;
            double ix = x - bx, iy = y - by, iz = z - bz;
            for (AABB b : sh.toAabbs())
                if (ix >= b.minX && ix <= b.maxX && iy >= b.minY && iy <= b.maxY && iz >= b.minZ && iz <= b.maxZ)
                    return false;
        }
        return true;
    }

    /**
     * How long one he's after has been out of his sight. {@link #keep} says whether he still holds on to them: looked
     * at again every half second, and dropped once unseen for {@link #MEMORY} ticks.
     */
    public static final class Memory {
        private int who = Integer.MIN_VALUE;
        private long seenAt;
        private long lookedAt = Long.MIN_VALUE;

        /** he picked (or saw) this one now */
        public void saw(LivingEntity e, long now) {
            who = e.getId();
            seenAt = now;
            lookedAt = now;
        }

        /** whether he still holds on to {@code e}, looking again when it's time */
        public boolean keep(Level l, List<Vec3> from, LivingEntity e, long now) {
            if (who != e.getId()) {
                // (picked some other way: from now he counts)
                saw(e, now);
                return true;
            }
            if (now - lookedAt >= LOOK_EVERY || now < lookedAt) {
                lookedAt = now;
                if (sees(l, from, e)) seenAt = now;
            }
            return now - seenAt <= MEMORY || now < seenAt;
        }

        public void forget() {
            who = Integer.MIN_VALUE;
        }
    }
}
