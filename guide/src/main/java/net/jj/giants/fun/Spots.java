package net.jj.giants.fun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7) Finding places for the fun tools without ever loading land on the server thread: the spot a player looks at,
 * the ground under a column (only in loaded land), deep water, a hollow under the ground, and asking for land to be
 * loaded (a region ticket) and running something once it is there.
 */
public final class Spots {
    private Spots() {}

    /** how far the tools reach when you point (blocks) */
    public static final double LOOK = 256;

    static final TicketType<ChunkPos> FUN =
            TicketType.create("jj_giants_fun", Comparator.comparingLong(ChunkPos::toLong), 1200);

    /** the block a player looks at, up to so far (null: only sky) */
    public static @Nullable BlockPos looked(ServerPlayer p, double far) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = eye.add(p.getViewVector(1f).scale(far));
        BlockHitResult r = p.level().clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p));
        return r.getType() == HitResult.Type.MISS ? null : r.getBlockPos();
    }

    /** the land there is loaded now */
    public static boolean loaded(ServerLevel l, int x, int z) {
        return l.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /** the top of the ground at a column (the block you'd stand on is y - 1), or NaN in land not loaded */
    public static double ground(ServerLevel l, int x, int z) {
        LevelChunk c = l.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null) return Double.NaN;
        return c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    /** the top of the solid ground, under any water (the sea floor), or NaN */
    public static double floor(ServerLevel l, int x, int z) {
        LevelChunk c = l.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null) return Double.NaN;
        return c.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
    }

    /** how deep the water is over a column (0: none, -1: not loaded) */
    public static int waterDepth(ServerLevel l, int x, int z) {
        double top = ground(l, x, z), bottom = floor(l, x, z);
        if (Double.isNaN(top)) return -1;
        BlockPos at = BlockPos.containing(x, top - 1, z);
        if (!l.getFluidState(at).is(FluidTags.WATER)) return 0;
        return (int) Math.max(0, top - bottom);
    }

    /** the nearest column with water at least `deep` deep, within `far` blocks, in loaded land (null: none) */
    public static @Nullable BlockPos deepWater(ServerLevel l, BlockPos from, int deep, int far) {
        for (int r = 0; r <= far; r += 8) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx += 8)
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = from.getX() + dx, z = from.getZ() + dz;
                    if (waterDepth(l, x, z) >= deep) {
                        double d = (double) dx * dx + (double) dz * dz;
                        if (d < bd) {
                            bd = d;
                            best = new BlockPos(x, (int) floor(l, x, z), z);
                        }
                    }
                }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * the nearest hollow under the ground with at least `tall` blocks of air over a solid floor, within `far` blocks
     * across and from the surface down (loaded land only); null: none
     */
    public static @Nullable BlockPos hollow(ServerLevel l, BlockPos from, int tall, int far) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= far; r += 4) {
            for (int dx = -r; dx <= r; dx += 4)
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = from.getX() + dx, z = from.getZ() + dz;
                    double top = ground(l, x, z);
                    if (Double.isNaN(top)) continue;
                    int air = 0;
                    for (int y = (int) top - 8; y > l.getMinBuildHeight() + 4; y--) {
                        BlockState s = l.getBlockState(m.set(x, y, z));
                        if (s.isAir()) air++;
                        else {
                            if (air >= tall && s.isSolid()) return new BlockPos(x, y + 1, z);
                            air = 0;
                        }
                    }
                }
        }
        return null;
    }

    // land made ready first

    private record Wait(ServerLevel level, List<ChunkPos> chunks, Runnable then, Runnable gaveUp, int[] ticks) {}

    private static final List<Wait> waits = new ArrayList<>();

    /**
     * runs `then` once the land round these spots (a chunk each way) is loaded, asked for with a ticket and never
     * waited for on this thread; after a minute without it, `gaveUp`
     */
    public static void whenLoaded(ServerLevel l, List<BlockPos> spots, Runnable then, Runnable gaveUp) {
        List<ChunkPos> cs = new ArrayList<>();
        for (BlockPos p : spots) {
            ChunkPos c = new ChunkPos(p);
            cs.add(c);
            l.getChunkSource().addRegionTicket(FUN, c, 2, c);
        }
        Wait w = new Wait(l, cs, then, gaveUp, new int[] {0});
        if (ready(w)) then.run();
        else waits.add(w);
    }

    private static boolean ready(Wait w) {
        for (ChunkPos c : w.chunks())
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (w.level().getChunkSource().getChunkNow(c.x + dx, c.z + dz) == null) return false;
        return true;
    }

    static void tick(MinecraftServer server) {
        if (waits.isEmpty()) return;
        for (Wait w : List.copyOf(waits)) {
            if (ready(w)) {
                waits.remove(w);
                w.then().run();
            } else if (++w.ticks()[0] > 1200) {
                waits.remove(w);
                w.gaveUp().run();
            }
        }
    }

    static void forget() {
        waits.clear();
    }
}
