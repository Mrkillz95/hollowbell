package net.jj.giants.systems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.remains.Carcass;
import net.jj.giants.systems.remains.Design;
import net.jj.giants.systems.remains.Designs;
import net.jj.giants.systems.remains.GiantModel;
import net.jj.giants.systems.remains.GiantModels;
import net.jj.giants.systems.remains.Simple;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3, §24.5) What stays where a giant fell: his skeleton (left for good, mined like any
 * block) or his corpse (his own body, rotting in four stages over the corpse days, then gone). Each kind has his own
 * design (package remains) made from his real model, at his size, lying the way he faced. The blocks are worked out
 * off the server thread, then put down a few thousand a tick (at most a few ms), chunk by chunk, only into air,
 * plants or water (never bedrock, a block entity, a player's block or where a player stands). Each rot stage changes
 * only the blocks still as the last stage left them. What is kept per remains is how to make it again (kind, size,
 * heading, seed) and which blocks it couldn't have; the blocks are worked out again when they change.
 */
public final class Remains {
    private Remains() {}

    /** the most blocks one remains may have (bigger giants leave their remains a little smaller) */
    public static final int MOST_BLOCKS = 1_500_000;
    /** each tick: at most this long and this many blocks */
    static final long TICK_NANOS = 4_000_000L;
    static final int TICK_MOST = 8000;
    /** a corpse's last change: everything goes */
    static final int GONE = 5, PLACE = -2;
    /** the designs' version: blocks worked out by another version aren't told apart by number (only by look) */
    public static final int VERSION = 1;

    /** (the old plain plan's stages, for remains from before) layers: bones, inner flesh, outer flesh */
    static final int BONE = 0, INNER = 1, OUTER = 2;

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jj_giants remains");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** a remains' blocks, worked out: the plan, the order to go through them (chunk by chunk), their states */
    record Planned(Carcass.Out out, int[] order, BlockState[] states) {}

    static final class Job {
        final SystemsData.Remains r;
        Future<Planned> plan;
        Planned p;
        BitSet skip = new BitSet(), wet = new BitSet();
        int waited;
        long chunk = Long.MIN_VALUE;

        Job(SystemsData.Remains r) {
            this.r = r;
            for (int i : r.skip) skip.set(i);
            for (int i : r.wet) wet.set(i);
        }
    }

    private static final Map<SystemsData.Remains, Job> jobs = new LinkedHashMap<>();
    /** keeps the land a remains goes down on loaded while it does (half a minute) */
    static final net.minecraft.server.level.TicketType<ChunkPos> LAND = net.minecraft.server.level.TicketType.create(
            "jj_giants_remains", java.util.Comparator.comparingLong(ChunkPos::toLong), 600);

    static void forget() {
        for (Job j : jobs.values()) if (j.plan != null) j.plan.cancel(true);
        jobs.clear();
    }

    /** any remains being put down or rotting right now */
    public static boolean busy() {
        return !jobs.isEmpty();
    }

    // ---------------------------------------------------------------- a giant falls

    /** he fell: his remains go down where he lies, facing the way he faced */
    static void build(ServerLevel l, String kind, LivingEntity e, String rule, double corpseDays) {
        boolean corpse = rule.equals("corpse");
        lay(l, kind, e.getX(), e.getZ(), ground(l, e.getBlockX(), e.getBlockY(), e.getBlockZ(),
                FunGiants.reach(kind, FunGiants.sizeOf(e))), e.getYRot(), FunGiants.sizeOf(e), corpse ? "corpse"
                        : "skeleton", corpseDays, corpse ? 0 : Carcass.SKELETON,
                e.getUUID().getMostSignificantBits() ^ l.getGameTime());
        String name = AdminServer.giant(kind) == null ? kind : AdminServer.nameOf(AdminServer.giant(kind));
        Systems.log("a {} fell at {}: his {} goes down", name, e.blockPosition().toShortString(), rule);
    }

    /**
     * Remains of this kind at this spot (x z where he stood, y the first free block over the ground), facing this
     * yaw, this size; rule corpse (from this stage 0-3, rotting on from there) or skeleton (stage 4, left for good).
     */
    public static SystemsData.Remains lay(ServerLevel l, String kind, double x, double z, int y, float yaw,
            double size, String rule, double corpseDays, int stage, long seed) {
        SystemsData.Remains r = new SystemsData.Remains();
        r.kind = kind;
        r.dim = l.dimension().location().toString();
        r.rule = rule.equals("corpse") ? "corpse" : "skeleton";
        r.x = (int) Math.floor(x);
        r.y = y;
        r.z = (int) Math.floor(z);
        r.size = size;
        r.yaw = yaw;
        r.seed = seed;
        r.ver = VERSION;
        r.rotTicks = (long) (Math.max(0.5, corpseDays) * 24000);
        int first = r.rule.equals("corpse") ? Math.max(0, Math.min(3, stage)) : Carcass.SKELETON;
        // a corpse shown at a later stage has been lying there that long
        r.fell = l.getGameTime() - (r.rule.equals("corpse") ? r.rotTicks * first / 4 : 0);
        r.stage = -1;
        r.from = PLACE;
        r.to = first;
        r.at = 0;
        SystemsData d = SystemsData.get(l.getServer());
        d.s.remains.add(r);
        d.setDirty();
        slowest = 0;
        start(r);
        SystemsAdmin.changed();
        return r;
    }

    /** where he lies: the ground round the spot (the middle of a few samples, so a tree or a pit doesn't count) */
    static int ground(ServerLevel l, int x, int y, int z, double reach) {
        List<Integer> hs = new ArrayList<>();
        int r = (int) Math.min(48, Math.max(2, reach * 0.3));
        for (int i = -1; i <= 1; i++)
            for (int k = -1; k <= 1; k++) {
                int px = x + i * r, pz = z + k * r;
                if (!l.isLoaded(new BlockPos(px, y, pz))) continue;
                hs.add(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz));
            }
        if (hs.isEmpty()) return y;
        hs.sort(Integer::compare);
        int g = hs.get(hs.size() / 2);
        if (g < l.getMinBuildHeight() + 1) g = y;
        return g;
    }

    // ---------------------------------------------------------------- working the blocks out

    private static void start(SystemsData.Remains r) {
        Job j = new Job(r);
        String kind = r.kind;
        double size = r.size;
        float yaw = r.yaw;
        long seed = r.seed;
        int ox = r.x, oz = r.z;
        double len = plainLength(kind, size);
        j.plan = WORK.submit(() -> plan(kind, size, yaw, seed, ox, oz, len));
        jobs.put(r, j);
    }

    static double plainLength(String kind, double size) {
        return Math.max(6, Math.min(96, Math.max(FunGiants.height(kind, size), 2 * FunGiants.reach(kind, size))
                * (kind.equals("furrowmaw") ? 0.6 : 0.55)));
    }

    /** his remains' blocks (his own design from his model, else the plain plan), in the order they go down */
    static Planned plan(String kind, double size, float yaw, long seed, int ox, int oz, double plainLen) {
        Carcass.Out o = blocks(kind, size, yaw, seed, plainLen);
        // chunk by chunk, bottom up in each
        int n = o.n;
        int minCx = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE, maxCz = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            minCx = Math.min(minCx, (ox + o.x[i]) >> 4);
            minCz = Math.min(minCz, (oz + o.z[i]) >> 4);
            maxCz = Math.max(maxCz, (oz + o.z[i]) >> 4);
        }
        long w = maxCz - minCz + 1;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            long rank = (long) (((ox + o.x[i]) >> 4) - minCx) * w + (((oz + o.z[i]) >> 4) - minCz);
            keys[i] = (rank << 34) | ((long) (o.y[i] + 2048) << 22) | i;
        }
        Arrays.sort(keys);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = (int) (keys[i] & ((1 << 22) - 1));
        BlockState[] states = new BlockState[o.mats.length];
        // (a bone's tone tokens are only names on the way: never a block)
        for (int i = 0; i < states.length; i++)
            states[i] = o.mats[i].startsWith("@") ? Blocks.AIR.defaultBlockState() : parse(o.mats[i]);
        return new Planned(o, order, states);
    }

    /** the blocks, unordered (stage arrays 0-4) */
    public static Carcass.Out blocks(String kind, double size, float yaw, long seed, double plainLen) {
        Design d = Designs.of(kind);
        GiantModel m = d == null ? null : GiantModels.get(kind);
        if (m == null) return Simple.plan(kind, plainLen, yaw, true);
        double s = size;
        // (his biggest: no more than the most blocks; about his model's own block count at size 1, times size^2)
        double cap = Math.sqrt(MOST_BLOCKS / (double) Math.max(1, m.total()) / 1.3);
        if (s > cap) s = cap;
        s = Math.max(0.04, s);
        return Designs.plan(d, m, s, yaw, seed);
    }

    private static final Map<String, BlockState> PARSED = new java.util.concurrent.ConcurrentHashMap<>();

    static BlockState parse(String s) {
        return PARSED.computeIfAbsent(s, k -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
            } catch (Exception bad) {
                GiantsGuideMod.LOG.warn("Giants remains: no block {}", k);
                return Blocks.AIR.defaultBlockState();
            }
        });
    }

    // ---------------------------------------------------------------- each tick

    /** every tick: remains going down or changing; once a second, corpses rotting on */
    public static void tick(MinecraftServer server) {
        SystemsData d = null;
        if (!jobs.isEmpty()) work(server);
        if (server.getTickCount() % 20 != 0) return;
        d = SystemsData.get(server);
        if (d.s.remains.isEmpty()) return;
        boolean changed = false;
        for (SystemsData.Remains r : new ArrayList<>(d.s.remains)) {
            if (jobs.containsKey(r)) continue;
            if (!r.pos.isEmpty()) {
                changed |= oldRot(server, d, r);
                continue;
            }
            // (after a restart: what was going on carries on)
            if (r.to != -1) {
                start(r);
                continue;
            }
            if (!r.rule.equals("corpse") || r.stage < 0) continue;
            ServerLevel l = level(server, r.dim);
            if (l == null) continue;
            int want = (int) Math.min(4, 4 * (l.getGameTime() - r.fell) / Math.max(1, r.rotTicks));
            if (want <= r.stage) continue;
            if (!l.isLoaded(new BlockPos(r.x, r.y, r.z))) continue;
            r.from = r.stage;
            r.to = want >= 4 ? GONE : r.stage + 1;
            r.at = 0;
            start(r);
            changed = true;
        }
        if (changed) {
            d.setDirty();
            SystemsAdmin.changed();
        }
    }

    /** the longest a tick spent on remains since the last were laid (the tests) */
    private static long slowest;

    private static void work(MinecraftServer server) {
        long t0 = System.nanoTime();
        try {
            work(server, t0);
        } finally {
            slowest = Math.max(slowest, System.nanoTime() - t0);
        }
    }

    private static void work(MinecraftServer server, long t0) {
        long until = t0 + TICK_NANOS;
        int budget = TICK_MOST;
        SystemsData d = SystemsData.get(server);
        for (var it = jobs.values().iterator(); it.hasNext() && budget > 0 && System.nanoTime() < until; ) {
            Job j = it.next();
            if (j.p == null) {
                if (!j.plan.isDone()) continue;
                try {
                    j.p = j.plan.get();
                } catch (Exception e) {
                    GiantsGuideMod.LOG.warn("Giants remains: couldn't work out the {} remains", j.r.kind, e);
                    it.remove();
                    d.s.remains.remove(j.r);
                    d.setDirty();
                    continue;
                }
            }
            ServerLevel l = level(server, j.r.dim);
            if (l == null) {
                it.remove();
                continue;
            }
            budget = step(l, j, budget, until);
            if (j.r.at >= j.p.out().n) {
                finish(d, j);
                it.remove();
            }
            d.setDirty();
        }
    }

    /** this job's blocks, as many as the tick allows */
    private static int step(ServerLevel l, Job j, int budget, long until) {
        SystemsData.Remains r = j.r;
        Planned p = j.p;
        Carcass.Out o = p.out();
        List<AABB> players = new ArrayList<>();
        for (ServerPlayer pl : l.players()) players.add(pl.getBoundingBox().deflate(0.01));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] target = r.to == GONE ? null : o.st[r.to];
        int[] from = r.from >= 0 && r.from < Carcass.STAGES ? o.st[r.from] : null;
        boolean placing = r.from == PLACE;
        boolean sameVersion = r.ver == VERSION;
        int n = 0;
        while (r.at < o.n && budget > 0) {
            if ((n++ & 63) == 0 && System.nanoTime() > until) break;
            int i = p.order()[r.at];
            int a = from == null ? 0 : from[i], b = target == null ? 0 : target[i];
            if (placing ? b == 0 : a == b) {
                r.at++;
                continue;
            }
            if (!placing && sameVersion && j.skip.get(i)) {
                r.at++;
                continue;
            }
            int x = r.x + o.x[i], y = r.y + o.y[i], z = r.z + o.z[i];
            if (y < l.getMinBuildHeight() || y >= l.getMaxBuildHeight()) {
                skip(j, i);
                r.at++;
                continue;
            }
            // its chunk: asked for, waited on a little, else passed by
            long ck = ChunkPos.asLong(x >> 4, z >> 4);
            if (!l.hasChunk(x >> 4, z >> 4)) {
                if (ck != j.chunk) {
                    j.chunk = ck;
                    j.waited = 0;
                    ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
                    l.getChunkSource().addRegionTicket(LAND, cp, 1, cp);
                }
                if (++j.waited < 200) return budget;
                skip(j, i);
                r.at++;
                continue;
            }
            m.set(x, y, z);
            BlockState cur = l.getBlockState(m);
            boolean ours;
            if (placing || a == 0) ours = free(cur) && !onPlayer(players, m);
            else ours = cur == p.states()[a];
            if (!ours) {
                if (placing) skip(j, i);
                r.at++;
                continue;
            }
            if (placing && wetSource(cur)) {
                j.wet.set(i);
                r.wet.add(i);
            }
            BlockState now = b == 0 ? (j.wet.get(i) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState())
                    : p.states()[b];
            l.setBlock(m, now, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            budget--;
            r.at++;
        }
        return budget;
    }

    private static void skip(Job j, int i) {
        if (!j.skip.get(i)) {
            j.skip.set(i);
            j.r.skip.add(i);
        }
    }

    private static void finish(SystemsData d, Job j) {
        SystemsData.Remains r = j.r;
        Systems.log("the {} remains at {} {} {}: now stage {} ({} blocks worked through, {} kept out)", r.kind, r.x,
                r.y, r.z, r.to == GONE ? "gone" : r.to, j.p.out().n, r.skip.size());
        r.stage = r.to == GONE ? 4 : r.to;
        boolean placed = r.from == PLACE;
        r.to = -1;
        r.from = -1;
        r.at = 0;
        if (placed && r.rule.equals("skeleton") || r.stage >= 4 && r.rule.equals("corpse")) d.s.remains.remove(r);
        d.setDirty();
        SystemsAdmin.changed();
    }

    /** may remains go here: air, a plant, snow, water; never bedrock or a block entity */
    static boolean free(BlockState s) {
        if (s.isAir()) return true;
        if (s.is(Blocks.BEDROCK) || s.hasBlockEntity()) return false;
        FluidState f = s.getFluidState();
        if (!f.isEmpty() && !f.is(FluidTags.WATER)) return false;
        return s.canBeReplaced();
    }

    private static boolean wetSource(BlockState s) {
        FluidState f = s.getFluidState();
        return f.is(FluidTags.WATER) && f.isSource();
    }

    private static boolean onPlayer(List<AABB> players, BlockPos p) {
        if (players.isEmpty()) return false;
        AABB box = new AABB(p);
        for (AABB b : players) if (b.intersects(box)) return true;
        return false;
    }

    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** the remains rotting now */
    public static int rotting(MinecraftServer server) {
        int n = 0;
        for (SystemsData.Remains r : SystemsData.get(server).s.remains) if (r.rule.equals("corpse")) n++;
        return n;
    }

    // ---------------------------------------------------------------- remains from before (the plain plan's lists)

    private static BlockState state(ServerLevel l, String s) {
        try {
            CompoundTag t = TagParser.parseTag(s);
            return NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK), t);
        } catch (Exception bad) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /** a corpse laid down by 1.13.0 (its blocks listed): it rots as it always did */
    private static boolean oldRot(MinecraftServer server, SystemsData d, SystemsData.Remains r) {
        if (!r.rule.equals("corpse") || r.stage < 0) {
            d.s.remains.remove(r);
            return true;
        }
        ServerLevel l = level(server, r.dim);
        if (l == null) return false;
        int want = (int) Math.min(4, 4 * (l.getGameTime() - r.fell) / Math.max(1, r.rotTicks));
        if (want <= r.stage || !l.isLoaded(new BlockPos(r.x, r.y, r.z))) return false;
        BlockState rotten = Blocks.MUD.defaultBlockState();
        while (r.stage < want) {
            r.stage++;
            for (int i = 0; i < r.pos.size(); i++) {
                BlockPos pos = BlockPos.of(r.pos.get(i));
                if (!l.isLoaded(pos)) continue;
                int lay = r.layer.get(i);
                BlockState cur = l.getBlockState(pos), was = state(l, r.block.get(i));
                boolean ours = cur == was || r.stage >= 2 && lay == OUTER && cur.is(Blocks.MUD);
                if (!ours) continue;
                if (r.stage == 1 && lay == OUTER) l.setBlock(pos, rotten, Block.UPDATE_CLIENTS);
                else if (r.stage == 2 && lay == OUTER || r.stage == 3 && lay == INNER || r.stage == 4 && lay == BONE)
                    l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (r.stage >= 4) d.s.remains.remove(r);
        return true;
    }

    // ---------------------------------------------------------------- the tests

    /** (the tests) a kind's remains blocks, worked out now: his own design when his mod is here, else the plain one */
    public static Carcass.Out planForTests(String kind, double size, float yaw) {
        return blocks(kind, size, yaw, 1234L, plainLength(kind, size));
    }

    /** (the tests) the longest a tick spent on remains since the last were laid, in ms */
    public static double slowestTickMs() {
        return slowest / 1e6;
    }

    /** (the tests) how many of its blocks (as its stage left them) are there now */
    public static int ours(ServerLevel l, SystemsData.Remains r) {
        Carcass.Out o = blocks(r.kind, r.size, r.yaw, r.seed, plainLength(r.kind, r.size));
        int k = r.rule.equals("corpse") ? Math.max(0, Math.min(3, r.stage)) : Carcass.SKELETON;
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < o.n; i++) {
            int v = o.st[k][i];
            if (v == 0) continue;
            BlockState want = parse(o.mats[v]);
            if (l.getBlockState(m.set(r.x + o.x[i], r.y + o.y[i], r.z + o.z[i])) == want) n++;
        }
        return n;
    }

    /** (the tests) one of its blocks there now, or null */
    public static BlockPos anyBlock(ServerLevel l, SystemsData.Remains r) {
        Carcass.Out o = blocks(r.kind, r.size, r.yaw, r.seed, plainLength(r.kind, r.size));
        int k = r.rule.equals("corpse") ? Math.max(0, Math.min(3, r.stage)) : Carcass.SKELETON;
        for (int i = 0; i < o.n; i++) {
            int v = o.st[k][i];
            if (v == 0) continue;
            BlockPos p = new BlockPos(r.x + o.x[i], r.y + o.y[i], r.z + o.z[i]);
            if (l.getBlockState(p) == parse(o.mats[v]) && !l.getBlockState(p).isAir()) return p;
        }
        return null;
    }

    /** (the tests) does this kind have his own design from his model here */
    public static boolean ownDesign(String kind) {
        return Designs.of(kind) != null && GiantModels.get(kind) != null;
    }
}
