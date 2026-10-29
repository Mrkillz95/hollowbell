package net.jj.hollowbell.world;

import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground cracking under his landings: grass and dirt go to coarse dirt, plants go. Which spots crack is picked
 * the moment he lands (from his own random, as it always was); the cracking itself runs out from the middle over the
 * next ticks, a couple of milliseconds a tick, instead of all of it in the one tick (a sky dive's crack at full size
 * took 22-37 ms). It only ever touches loaded land.
 */
public final class Cracks {
    private Cracks() {}

    /** how long one tick may spend cracking; what's left goes on next tick */
    static final long BUDGET_NS = 2_000_000L;

    private static final class Job {
        final ServerLevel l;
        /** the spots, nearest the middle first: x in the high half, z in the low */
        final long[] at;

        int next;

        Job(ServerLevel l, long[] at) {
            this.l = l;
            this.at = at;
        }
    }

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();

    /** the spots that crack (picked by the caller), cracked from the middle (cx, cz) out over the next ticks */
    public static void add(ServerLevel l, int cx, int cz, long[] spots, int n) {
        if (n == 0) return;
        // nearest first: distance in the high bits, the place in the list in the low (so equal ones keep their order)
        long[] key = new long[n];
        for (int i = 0; i < n; i++) {
            int dx = x(spots[i]) - cx, dz = z(spots[i]) - cz;
            key[i] = ((long) (dx * dx + dz * dz) << 32) | i;
        }
        Arrays.sort(key);
        long[] at = new long[n];
        for (int i = 0; i < n; i++) at[i] = spots[(int) key[i]];
        JOBS.add(new Job(l, at));
    }

    public static long spot(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int x(long s) {
        return (int) (s >> 32);
    }

    static int z(long s) {
        return (int) s;
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        long until = System.nanoTime() + BUDGET_NS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!JOBS.isEmpty()) {
            Job j = JOBS.peek();
            while (j.next < j.at.length) {
                if (System.nanoTime() > until) return;
                long s = j.at[j.next++];
                crack(j.l, p.set(x(s), 0, z(s)));
            }
            JOBS.poll();
        }
    }

    /** one spot: the ground at the top goes to coarse dirt, a plant on it goes */
    static void crack(ServerLevel l, BlockPos at) {
        if (!NoWait.loaded(l, at)) return; // (never waits for land being made, or let go since he landed)
        BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        BlockPos below = top.below();
        BlockState st = l.getBlockState(below);
        if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM))
            l.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        BlockState up = l.getBlockState(top);
        if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty()) l.destroyBlock(top, false);
    }

    /** spots still to crack */
    public static int left() {
        int n = 0;
        for (Job j : JOBS) n += j.at.length - j.next;
        return n;
    }

    public static void forget() {
        JOBS.clear();
    }
}
