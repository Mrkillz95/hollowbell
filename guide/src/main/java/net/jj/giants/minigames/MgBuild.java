package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import net.jj.giants.fun.MgShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.5) Builds a round's map into its own world: the pieces (the arena, dens, the keep,
 * the road...) one column at a time, a few milliseconds a tick, without updating neighbours (water stays put, sand
 * doesn't fall), the land under it asked for by the world's ticket and never loaded here. Nothing is written down to
 * take it down again: the world goes when the round ends.
 */
final class MgBuild {
    /** one piece: its plan and where its middle goes */
    record Piece(MgShapes.Shape shape, int x, int z) {}

    final ServerLevel level;
    final int floor;
    private final List<Piece> pieces = new ArrayList<>();
    private int piece, col, waited;
    long blocks;
    private int total, done;

    MgBuild(ServerLevel level, int floor) {
        this.level = level;
        this.floor = floor;
    }

    MgBuild add(MgShapes.Shape s, int x, int z) {
        pieces.add(new Piece(s, x, z));
        int side = 2 * s.reach() + 1;
        total += side * side;
        return this;
    }

    boolean done() {
        return piece >= pieces.size();
    }

    /** how far it has got, 0 to 1 */
    double progress() {
        return total == 0 ? 1 : Math.min(1, done / (double) total);
    }

    /** builds until the time is up; true when everything is built */
    boolean tick(long until) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (piece < pieces.size() && System.nanoTime() < until) {
            Piece p = pieces.get(piece);
            int reach = p.shape().reach(), side = 2 * reach + 1, cols = side * side;
            if (col >= cols) {
                piece++;
                col = 0;
                continue;
            }
            int dx = col % side - reach, dz = col / side - reach;
            int x = p.x() + dx, z = p.z() + dz;
            LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null) {
                // (still being made: the world's ticket asked for it; never waited for here)
                if (++waited < 2400) return false;
                waited = 0;
                col++;
                done++;
                continue;
            }
            waited = 0;
            column(c, p.shape(), dx, dz, x, z, m);
            col++;
            done++;
        }
        return done();
    }

    private void column(LevelChunk c, MgShapes.Shape s, int dx, int dz, int x, int z, BlockPos.MutableBlockPos m) {
        int[] clear = {Integer.MAX_VALUE};
        List<int[]> ys = new ArrayList<>(4);
        List<BlockState> states = new ArrayList<>(4);
        BlockState[] support = {null};
        s.column(dx, dz, floor, new MgShapes.Out() {
            @Override
            public void set(int y, BlockState st) {
                ys.add(new int[] {y});
                states.add(st);
            }

            @Override
            public void clearAbove(int y) {
                clear[0] = Math.min(clear[0], y);
            }

            @Override
            public void support(BlockState st) {
                support[0] = st;
            }
        });
        if (clear[0] != Integer.MAX_VALUE) {
            int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            for (int y = top; y >= clear[0]; y--)
                if (!c.getBlockState(m.set(x, y, z)).isAir()) put(m, Blocks.AIR.defaultBlockState());
        }
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < ys.size(); i++) {
            int y = ys.get(i)[0];
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            put(m.set(x, y, z), states.get(i));
            lowest = Math.min(lowest, y);
        }
        if (support[0] != null && lowest != Integer.MAX_VALUE)
            for (int y = lowest - 1, k = 0; y > level.getMinBuildHeight() && k < 24; y--, k++) {
                BlockState was = c.getBlockState(m.set(x, y, z));
                if (!(was.isAir() || was.canBeReplaced() || !was.getFluidState().isEmpty())) break;
                put(m, support[0]);
            }
    }

    private void put(BlockPos at, BlockState s) {
        BlockState was = level.getBlockState(at);
        if (was == s || was.is(Blocks.BEDROCK)) return;
        level.setBlock(at, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        blocks++;
    }
}
