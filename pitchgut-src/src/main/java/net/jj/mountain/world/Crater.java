package net.jj.mountain.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The hole he leaves.
 *
 * Far too much ground to take out in one tick, so it is eaten a chunk at a time with a budget of blocks per
 * tick, middle first and rim last, and the finished chunk is sent to everyone watching in one go. Taking the
 * blocks out one at a time and telling the clients about each one would be tens of thousands of little packets
 * and would stop the game dead.
 *
 * Nothing here is saved. Stop the game half way through and what is dug is dug; the rest of the ground stays.
 */
public final class Crater {
    private static final TicketType<Integer> TICKET = TicketType.create("mountain_crater", Integer::compareTo, 220);
    private static final List<Crater> live = new ArrayList<>();
    /** how many blocks come out in one tick for a small hole; a big one is dug faster or it would never finish */
    private static final int PER_TICK = 4000;
    /** how many chunks are held open ahead of the digging at once. Holding all of them for a two-thousand-block
     *  hole would be sixteen thousand loaded chunks and the end of the server. */
    private static final int WINDOW = 56;

    private final ServerLevel lvl;
    private final BlockPos mid;
    private final int r;
    private final int id = (int) (System.nanoTime() & 0x7fffffff);
    private final List<ChunkPos> chunks = new ArrayList<>();
    private int ci, cj, freed;
    private int held, taken, waited, told;

    private Crater(ServerLevel lvl, BlockPos mid, int r) {
        this.lvl = lvl;
        this.mid = mid;
        this.r = Math.max(4, Math.min(1200, r));
        int c0 = (mid.getX() - this.r) >> 4, c1 = (mid.getX() + this.r) >> 4;
        int z0 = (mid.getZ() - this.r) >> 4, z1 = (mid.getZ() + this.r) >> 4;
        List<ChunkPos> got = new ArrayList<>();
        for (int cx = c0; cx <= c1; cx++)
            for (int cz = z0; cz <= z1; cz++) got.add(new ChunkPos(cx, cz));
        got.sort((a, b) -> Double.compare(far(a), far(b)));      // middle outwards, so it opens while you watch
        chunks.addAll(got);
    }

    private double far(ChunkPos c) {
        double dx = (c.x * 16 + 8) - mid.getX(), dz = (c.z * 16 + 8) - mid.getZ();
        return dx * dx + dz * dz;
    }

    public static void start(ServerLevel lvl, BlockPos mid, int r) {
        Crater c = new Crater(lvl, mid, r);
        c.hold();
        live.add(c);
    }

    /** how many are still being dug, for the tests */
    public static int running() { return live.size(); }

    /** nothing half dug carries over into the next world */
    public static void forgetEverything() { live.clear(); }

    public static void serverTick(MinecraftServer server) {
        if (live.isEmpty()) return;
        for (Iterator<Crater> it = live.iterator(); it.hasNext(); ) {
            Crater c = it.next();
            if (c.step()) it.remove();
        }
    }

    /**
     * Holds open only the chunks about to be dug, not the whole hole. A window that moves along with the digging
     * means the ground ahead is being made while the ground under the cursor is coming out, and nothing behind
     * is kept in memory.
     */
    private void hold() {
        var cs = lvl.getChunkSource();
        int to = Math.min(chunks.size(), ci + WINDOW);
        for (int i = ci; i < to; i++) cs.addRegionTicket(TICKET, chunks.get(i), 1, id);
        // and let go of everything the digging has already passed
        while (freed < ci) { cs.removeRegionTicket(TICKET, chunks.get(freed), 1, id); freed++; }
    }

    /** how many blocks a hole this size can afford to take out in a tick */
    private int perTick() {
        if (r <= 160) return PER_TICK;
        double f = (double) r / 160.0;
        return (int) Math.min(26000, PER_TICK * f * f);
    }

    /** one tick of digging; true when there is nothing left to take out */
    private boolean step() {
        if (lvl.getServer() == null || !lvl.getServer().isRunning()) return true;
        if (++held % 20 == 0) hold();
        int budget = perTick();
        while (ci < chunks.size() && budget > 0) {
            ChunkPos cp = chunks.get(ci);
            if (!lvl.hasChunk(cp.x, cp.z)) {
                // the ground there is not up yet. Wait for it rather than leaving a lump of world standing in
                // the middle of the hole; after a minute of waiting, give that chunk up and carry on.
                if (++waited < 1200) return false;
                waited = 0;
                nextChunk(null);
                continue;
            }
            waited = 0;
            LevelChunk ch = lvl.getChunk(cp.x, cp.z);
            while (cj < 256 && budget > 0) {
                int x = cp.getMinBlockX() + (cj & 15), z = cp.getMinBlockZ() + (cj >> 4);
                cj++;
                double dx = x - mid.getX(), dz = z - mid.getZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) continue;
                budget -= column(x, z, d);
            }
            if (cj >= 256) nextChunk(ch);
        }
        if (ci >= chunks.size()) {
            var cs = lvl.getChunkSource();
            while (freed < chunks.size()) { cs.removeRegionTicket(TICKET, chunks.get(freed), 1, id); freed++; }
            net.jj.mountain.MountainMod.LOG.info("crater at {} r={} chunks={} blocks={}", mid, r, chunks.size(), taken);
            if (r > 300) for (ServerPlayer p : lvl.players())
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.mountain_breathes.crater_done", r * 2), false);
            return true;
        }
        // a big hole takes minutes to open: say how far along it is rather than looking like nothing is happening
        if (r > 300 && held % 200 == 0) {
            int pct = (int) (ci * 100L / Math.max(1, chunks.size()));
            if (pct != told) {
                told = pct;
                for (ServerPlayer p : lvl.players())
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.mountain_breathes.crater_going", pct), true);
            }
        }
        if (held % 20 == 0) {
            double ring = Math.min(r, 6 + Math.sqrt(Math.max(0, taken)) * 0.3);
            lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.getX() + ring, mid.getY() + 4, mid.getZ(),
                    2, ring * 0.4, 2, ring * 0.4, 0);
        }
        return false;
    }

    /** the chunk is done: put its heightmaps right and hand the whole thing to everyone watching, once */
    private void nextChunk(LevelChunk ch) {
        if (ch != null) {
            Heightmap.primeHeightmaps(ch, java.util.EnumSet.of(Heightmap.Types.MOTION_BLOCKING,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
            ch.setUnsaved(true);
            ChunkPos cp = ch.getPos();
            ClientboundLevelChunkWithLightPacket pkt =
                    new ClientboundLevelChunkWithLightPacket(ch, lvl.getLightEngine(), null, null);
            int see = lvl.getServer().getPlayerList().getViewDistance() + 1;
            for (ServerPlayer p : lvl.players())
                if (p.chunkPosition().getChessboardDistance(cp) <= see) p.connection.send(pkt);
        }
        ci++;
        cj = 0;
    }

    /** takes one column out and gives back how many blocks that was */
    private int column(int x, int z, double d) {
        double f = Math.max(0.0, 1.0 - (d / r) * (d / r));
        int deep = (int) Math.round(r * 0.5 * Math.sqrt(f));
        int jag = (int) ((mix(x, z) & 7) - 3);                       // a torn edge rather than a dish
        deep = Math.max(0, deep + (d > r * 0.75 ? jag : jag / 2));
        if (deep <= 0) return 1;
        int bottom = Math.max(lvl.getMinBuildHeight(), mid.getY() - deep);
        int top = Math.min(lvl.getMaxBuildHeight() - 1,
                Math.max(lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), mid.getY() + 6));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int y = top; y >= bottom; y--) {
            p.set(x, y, z);
            BlockState st = lvl.getBlockState(p);
            n++;
            if (st.isAir()) continue;
            if (st.getDestroySpeed(lvl, p) < 0) continue;            // bedrock and anything else nothing can break
            // no neighbour updates and no per-block packet: the whole chunk goes out in one piece afterwards
            lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            taken++;
        }
        // the floor of it comes out burnt
        int floor = bottom - 1;
        if (floor >= lvl.getMinBuildHeight()) {
            p.set(x, floor, z);
            BlockState st = lvl.getBlockState(p);
            if (!st.isAir() && st.getDestroySpeed(lvl, p) >= 0) {
                long h = mix(x * 31, z * 17);
                BlockState burnt = (h & 15) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : (h & 3) == 0 ? Blocks.BASALT.defaultBlockState()
                        : Blocks.BLACKSTONE.defaultBlockState();
                lvl.setBlock(p, burnt, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                n++;
            }
        }
        return n;
    }

    private static long mix(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
