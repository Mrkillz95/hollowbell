package net.jj.giants.fun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Arena and den builder") Ready-made places for giants, built round where an admin
 * stands: an arena (small, medium or large) or a den for one kind of giant, sized to him. A few thousand blocks a
 * tick (about 10 ms), land asked for ahead with a ticket and never loaded here, and every block it replaces written
 * down first, so it can be taken down again (up to two million blocks; a bigger build says it can't be). Bedrock is
 * never touched, nor a block where a player stands.
 */
public final class Builds {
    private Builds() {}

    /** most blocks written down for taking a build down */
    static final int MOST_UNDO = 2_000_000;
    public static final Map<String, Integer> ARENAS = Map.of("small", 40, "medium", 64, "large", 96);

    /** what one column gets: blocks set (absolute y), everything above a height cleared, a support under it */
    static final class Col {
        final List<int[]> ys = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        int clearFrom = Integer.MAX_VALUE;
        @Nullable BlockState support;

        void set(int y, BlockState s) {
            ys.add(new int[] {y});
            states.add(s);
        }

        void clearAbove(int y) {
            clearFrom = Math.min(clearFrom, y);
        }
    }

    /** a build's shape: what each column round its middle gets (dx, dz from the middle; f the floor's height) */
    interface Shape {
        void column(int dx, int dz, double d, int f, Col out);

        /** how far out it reaches (columns beyond are left alone) */
        int reach();
    }

    /** a running build or take-down */
    static final class Job {
        FunWorld.Build b;
        ServerLevel level;
        @Nullable Shape shape;
        // columns left (build) or the record index (take down)
        int col, cols, side;
        boolean down;
        // what it replaced
        final Map<BlockState, Integer> palette = new HashMap<>();
        final List<BlockState> paletteList = new ArrayList<>();
        long[] pos = new long[4096];
        int[] idx = new int[4096];
        int n;
        final ListTag entities = new ListTag();
        boolean full;
        int waited;
        @Nullable UUID who;
        // taking down: what to put back
        @Nullable List<BlockState> back;
        @Nullable Map<Long, CompoundTag> backEntities;
    }

    static final List<Job> jobs = new ArrayList<>();

    static int x(Job j, int i) {
        return j.b.x - j.side + i % (2 * j.side + 1);
    }

    static int z(Job j, int i) {
        return j.b.z - j.side + i / (2 * j.side + 1);
    }

    // starting

    public static AdminServer.Result arena(MinecraftServer server, String who, ServerPlayer p, String size) {
        Integer r = ARENAS.get(size);
        if (r == null) return AdminServer.Result.fail("An arena is small, medium or large.");
        return start(server, who, p, "arena", size, "", r, arena(r));
    }

    public static AdminServer.Result den(MinecraftServer server, String who, ServerPlayer p, String kind, double size) {
        Giant g = AdminServer.giant(kind);
        if (g == null) return AdminServer.Result.fail("No giant called " + kind + ".");
        int r = Mth.clamp((int) Math.round(FunGiants.reach(g.key(), size) * 0.8), 10, 64);
        Shape s = Dens.shape(g.key(), r, FunGiants.height(g.key(), size));
        if (s == null) return AdminServer.Result.fail("There's no den for the " + AdminServer.nameOf(g) + " yet.");
        return start(server, who, p, "den", g.key(), g.key(), r, s);
    }

    private static AdminServer.Result start(MinecraftServer server, String who, ServerPlayer p, String kind,
            String style, String giant, int r, Shape shape) {
        for (Job j : jobs)
            if (!j.down) return AdminServer.Result.fail("Another build is still going (" + j.b.done + " of " + j.b.total
                    + " columns). /giants admin build stop stops it.");
        FunWorld w = FunWorld.get(server);
        FunWorld.Build b = new FunWorld.Build();
        b.n = w.s.nextBuild++;
        b.kind = kind;
        b.style = style;
        b.giant = giant;
        b.dim = p.level().dimension().location().toString();
        b.x = p.getBlockX();
        b.y = p.getBlockY() - 1;
        b.z = p.getBlockZ();
        b.r = r;
        b.who = who;
        b.time = System.currentTimeMillis();
        b.state = "building";
        Job j = new Job();
        j.b = b;
        j.level = p.serverLevel();
        j.shape = shape;
        j.side = shape.reach();
        j.cols = (2 * j.side + 1) * (2 * j.side + 1);
        j.who = p.getUUID();
        b.total = j.cols;
        w.s.builds.add(b);
        w.setDirty();
        jobs.add(j);
        String what = kind.equals("arena") ? "a " + style + " arena" : "a den for the "
                + AdminServer.nameOf(AdminServer.giant(giant));
        Fun.log(server, who, giant.isEmpty() ? "all" : giant, "build", "build " + b.n, what + " at " + b.x + " " + b.y
                + " " + b.z);
        Fun.changed();
        return new AdminServer.Result(true, List.of("Building " + what + " round you (" + r
                + " blocks round). It can be taken down again: /giants admin build takedown " + b.n + "."),
                List.of(who + " is building " + what + " at " + b.x + " " + b.y + " " + b.z + "."));
    }

    public static AdminServer.Result stop(MinecraftServer server, String who) {
        for (Job j : List.copyOf(jobs))
            if (!j.down) {
                jobs.remove(j);
                j.b.state = "stopped";
                saveUndo(server, j);
                FunWorld.get(server).setDirty();
                Fun.changed();
                return AdminServer.Result.said(List.of("Stopped building after " + j.b.done + " of " + j.b.total
                        + " columns. Take it down to put the land back."));
            }
        return AdminServer.Result.fail("Nothing is being built.");
    }

    public static AdminServer.Result takeDown(MinecraftServer server, String who, int n) {
        FunWorld w = FunWorld.get(server);
        FunWorld.Build b = w.build(n);
        if (b == null) return AdminServer.Result.fail("There's no build " + n + ". /giants admin build list");
        for (Job j : jobs) if (j.b == b) return AdminServer.Result.fail("Build " + n + " is still busy.");
        if (b.state.equals("gone")) return AdminServer.Result.fail("Build " + n + " was taken down already.");
        if (!b.undo) return AdminServer.Result.fail("Build " + n + " replaced too much to be taken down.");
        CompoundTag t = readUndo(server, n);
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(b.dim)));
        if (t == null || l == null) return AdminServer.Result.fail("Build " + n + "'s record is missing.");
        Job j = new Job();
        j.b = b;
        j.level = l;
        j.down = true;
        j.pos = t.getLongArray("Pos");
        j.idx = t.getIntArray("Idx");
        j.n = Math.min(j.pos.length, j.idx.length);
        j.back = new ArrayList<>();
        var lookup = l.holderLookup(Registries.BLOCK);
        for (net.minecraft.nbt.Tag s : t.getList("Palette", 10)) j.back.add(NbtUtils.readBlockState(lookup, (CompoundTag) s));
        j.backEntities = new HashMap<>();
        for (net.minecraft.nbt.Tag e : t.getList("Entities", 10)) {
            CompoundTag c = (CompoundTag) e;
            j.backEntities.put(c.getLong("JJPos"), c);
        }
        j.col = j.n;
        b.state = "taking down";
        b.done = 0;
        b.total = j.n;
        w.setDirty();
        jobs.add(j);
        Fun.log(server, who, b.giant.isEmpty() ? "all" : b.giant, "build", "build " + n, "taking down");
        Fun.changed();
        return new AdminServer.Result(true, List.of("Taking build " + n + " down (" + j.n + " blocks back as they were)."),
                List.of(who + " is taking build " + n + " down."));
    }

    public static boolean busy() {
        return !jobs.isEmpty();
    }

    // each tick

    static void tick(MinecraftServer server) {
        if (jobs.isEmpty()) return;
        Job j = jobs.get(0);
        long until = System.nanoTime() + 10_000_000L;
        if (j.down) downTick(server, j, until);
        else buildTick(server, j, until);
    }

    private static void buildTick(MinecraftServer server, Job j, long until) {
        ServerLevel l = j.level;
        // the land ahead asked for
        int w = 2 * j.side + 1;
        for (int k = 0; k < 6; k++) {
            int i = Math.min(j.cols - 1, j.col + k * 16 * w / 4);
            ChunkPos c = new ChunkPos(new BlockPos(x(j, i), 0, z(j, i)));
            l.getChunkSource().addRegionTicket(Spots.FUN, c, 1, c);
        }
        List<AABB> players = new ArrayList<>();
        for (ServerPlayer p : l.players()) players.add(p.getBoundingBox().deflate(0.01));
        while (j.col < j.cols && System.nanoTime() < until) {
            int x = x(j, j.col), z = z(j, j.col);
            LevelChunk c = l.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null) {
                if (++j.waited < 1200) return;
                j.waited = 0;
                j.col++;
                continue;
            }
            j.waited = 0;
            int dx = x - j.b.x, dz = z - j.b.z;
            Col col = new Col();
            j.shape.column(dx, dz, Math.sqrt((double) dx * dx + dz * dz), j.b.y, col);
            doColumn(j, l, c, x, z, col, players);
            j.col++;
            j.b.done = j.col;
        }
        if (j.col >= j.cols) {
            jobs.remove(j);
            j.b.state = "built";
            j.b.done = j.b.total;
            saveUndo(server, j);
            FunWorld.get(server).setDirty();
            ServerPlayer p = j.who == null ? null : server.getPlayerList().getPlayer(j.who);
            if (p != null) p.displayClientMessage(Component.literal("Build " + j.b.n + " is done" + (j.full
                    ? " (it replaced too much to be taken down)." : ".")), false);
            // a den: his biome under it, his own paint
            if (j.b.kind.equals("den") && p != null) {
                Giant g = AdminServer.giant(j.b.giant);
                if (g != null) AdminServer.call(server, g, "paint", Math.max(16, j.b.r + 6) + " biome "
                        + p.getStringUUID() + " depth=5 at=" + j.b.x + "," + j.b.z);
            }
            Fun.changed();
        }
    }

    private static void doColumn(Job j, ServerLevel l, LevelChunk c, int x, int z, Col col, List<AABB> players) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (col.clearFrom != Integer.MAX_VALUE) {
            int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            for (int y = top; y >= col.clearFrom; y--) {
                BlockState s = c.getBlockState(m.set(x, y, z));
                if (!s.isAir()) put(j, l, m, Blocks.AIR.defaultBlockState(), players);
            }
        }
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < col.ys.size(); i++) {
            int y = col.ys.get(i)[0];
            if (y < l.getMinBuildHeight() || y >= l.getMaxBuildHeight()) continue;
            put(j, l, m.set(x, y, z), col.states.get(i), players);
            if (!col.states.get(i).isAir()) lowest = Math.min(lowest, y);
        }
        if (col.support != null && lowest != Integer.MAX_VALUE)
            for (int y = lowest - 1, k = 0; y > l.getMinBuildHeight() && k < 24; y--, k++) {
                BlockState s = c.getBlockState(m.set(x, y, z));
                if (!(s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty())) break;
                put(j, l, m, col.support, players);
            }
    }

    /** one block, its old self written down first; never bedrock, never where a player stands */
    private static void put(Job j, ServerLevel l, BlockPos at, BlockState now, List<AABB> players) {
        BlockState was = l.getBlockState(at);
        if (was == now || was.is(Blocks.BEDROCK)) return;
        if (!now.isAir()) for (AABB b : players) if (b.intersects(new AABB(at))) return;
        if (!j.full) {
            if (j.n >= MOST_UNDO) {
                j.full = true;
                j.b.undo = false;
                j.pos = new long[0];
                j.idx = new int[0];
                j.n = 0;
            } else {
                Integer i = j.palette.get(was);
                if (i == null) {
                    i = j.paletteList.size();
                    j.palette.put(was, i);
                    j.paletteList.add(was);
                }
                if (j.n == j.pos.length) {
                    j.pos = java.util.Arrays.copyOf(j.pos, j.n * 2);
                    j.idx = java.util.Arrays.copyOf(j.idx, j.n * 2);
                }
                j.pos[j.n] = at.asLong();
                j.idx[j.n] = i;
                j.n++;
                BlockEntity be = l.getBlockEntity(at);
                if (be != null) {
                    CompoundTag t = be.saveWithFullMetadata(l.registryAccess());
                    t.putLong("JJPos", at.asLong());
                    j.entities.add(t);
                }
            }
        }
        // (no neighbour updates: water stays put, sand doesn't fall, nothing pops off)
        l.setBlock(at, now, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void downTick(MinecraftServer server, Job j, long until) {
        ServerLevel l = j.level;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (j.col > 0 && System.nanoTime() < until) {
            int i = j.col - 1;
            m.set(BlockPos.of(j.pos[i]));
            if (l.getChunkSource().getChunkNow(m.getX() >> 4, m.getZ() >> 4) == null) {
                ChunkPos c = new ChunkPos(m);
                l.getChunkSource().addRegionTicket(Spots.FUN, c, 1, c);
                if (++j.waited < 1200) return;
                j.waited = 0;
            } else {
                j.waited = 0;
                BlockState s = j.back.get(Math.max(0, Math.min(j.back.size() - 1, j.idx[i])));
                l.setBlock(m, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                CompoundTag be = j.backEntities.remove(j.pos[i]);
                if (be != null) {
                    BlockEntity e = l.getBlockEntity(m);
                    if (e != null) e.loadWithComponents(be, l.registryAccess());
                }
            }
            j.col--;
            j.b.done = j.n - j.col;
        }
        if (j.col <= 0) {
            jobs.remove(j);
            j.b.state = "gone";
            FunWorld.get(server).setDirty();
            // a den: his biome erased there too
            if (j.b.kind.equals("den")) Brush.eraseJob(l, j.b.x, j.b.z, Math.max(16, j.b.r + 6), null, null);
            try {
                Files.deleteIfExists(undoFile(server, j.b.n));
            } catch (IOException e) {
                GiantsGuideMod.LOG.debug("Couldn't delete build {}'s record: {}", j.b.n, e.toString());
            }
            Fun.changed();
        }
    }

    // the record

    static Path undoFile(MinecraftServer server, int n) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("jj_giants_builds").resolve(n + ".dat");
    }

    private static void saveUndo(MinecraftServer server, Job j) {
        if (j.full) return;
        CompoundTag t = new CompoundTag();
        ListTag pal = new ListTag();
        for (BlockState s : j.paletteList) pal.add(NbtUtils.writeBlockState(s));
        t.put("Palette", pal);
        t.put("Pos", new LongArrayTag(java.util.Arrays.copyOf(j.pos, j.n)));
        t.put("Idx", new IntArrayTag(java.util.Arrays.copyOf(j.idx, j.n)));
        t.put("Entities", j.entities);
        try {
            Path f = undoFile(server, j.b.n);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(t, f);
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Couldn't keep build {}'s record: {}", j.b.n, e.toString());
            j.b.undo = false;
        }
    }

    private static @Nullable CompoundTag readUndo(MinecraftServer server, int n) {
        try {
            Path f = undoFile(server, n);
            if (!Files.exists(f)) return null;
            return NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Couldn't read build {}'s record: {}", n, e.toString());
            return null;
        }
    }

    static void forget() {
        jobs.clear();
    }

    /** a build left half done when the server stopped: its record kept, said "stopped" */
    static void stoppedOnStart(MinecraftServer server) {
        FunWorld w = FunWorld.get(server);
        for (FunWorld.Build b : w.s.builds)
            if (b.state.equals("building") || b.state.equals("taking down")) {
                b.state = "stopped";
                b.undo = b.undo && Files.exists(undoFile(server, b.n));
                w.setDirty();
            }
    }

    // the arena

    static double hash(int x, int z, int salt) {
        long h = x * 341873128712L + z * 132897987541L + salt * 2654435761L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    static BlockState b(Block k) {
        return k.defaultBlockState();
    }

    static Shape arena(int r) {
        return new Shape() {
            @Override
            public int reach() {
                return r + 9;
            }

            @Override
            public void column(int dx, int dz, double d, int f, Col out) {
                boolean gate = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
                double ang = Math.atan2(dz, dx);
                if (d <= r) {
                    BlockState s = d < 2.5 ? b(Blocks.CHISELED_STONE_BRICKS)
                            : Math.abs(d - r / 2.0) < 0.7 || Math.abs(d - (r - 2)) < 0.7 ? b(Blocks.POLISHED_ANDESITE)
                            : hash(dx, dz, 1) < 0.06 ? b(Blocks.CRACKED_STONE_BRICKS)
                            : hash(dx, dz, 2) < 0.04 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
                    out.set(f, s);
                    out.clearAbove(f + 1);
                    out.support = b(Blocks.STONE_BRICKS);
                } else if (d <= r + 2) {
                    out.set(f, b(Blocks.STONE_BRICKS));
                    out.support = b(Blocks.STONE_BRICKS);
                    if (gate) {
                        out.clearAbove(f + 1);
                        return;
                    }
                    // a pillar every 30 degrees (between the gates), with a lantern on top
                    boolean pillar = false;
                    for (int k = 0; k < 12; k++) {
                        double a = Math.toRadians(15 + 30 * k);
                        double px = Math.cos(a) * (r + 1), pz = Math.sin(a) * (r + 1);
                        if ((dx - px) * (dx - px) + (dz - pz) * (dz - pz) < 1.6) pillar = true;
                    }
                    if (pillar) {
                        for (int y = f + 1; y <= f + 8; y++) out.set(y, b(Blocks.DEEPSLATE_BRICKS));
                        out.set(f + 9, b(Blocks.LANTERN));
                        out.clearAbove(f + 10);
                        return;
                    }
                    for (int y = f + 1; y <= f + 4; y++) out.set(y, b(Blocks.STONE_BRICKS));
                    int along = (int) Math.floor((ang + Math.PI) * (r + 1));
                    if ((along & 1) == 0) out.set(f + 5, b(Blocks.STONE_BRICK_WALL));
                    out.clearAbove(f + 6);
                } else if (d <= r + 8) {
                    out.support = b(Blocks.STONE_BRICKS);
                    if (gate) {
                        out.set(f, b(Blocks.STONE_BRICKS));
                        out.clearAbove(f + 1);
                        return;
                    }
                    int tier = Math.min(3, (int) ((d - (r + 2)) / 2) + 1);
                    for (int y = f; y < f + tier; y++) out.set(y, b(Blocks.STONE_BRICKS));
                    out.set(f + tier, b(Blocks.POLISHED_ANDESITE));
                    out.clearAbove(f + tier + 1);
                }
            }
        };
    }
}
