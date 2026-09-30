package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /hollowbell ground check [radius] (admins): goes over every column of his ground on the real world, loading (and
 * so making) its chunks a few at a time, and says for each one whether it came out as his ground should be there.
 * The answer is counted by cause and drawn as a map from above (his ground green, wrong columns red, the rest grey)
 * into the world folder: hollowbell_ground_check.png, a second map coloured by cause, and a text summary.
 */
public final class GroundCheck {
    private GroundCheck() {}

    /** why a column is not as it should be */
    public enum Cause {
        OK("fine", 0x3CB043),
        NOT_MADE("not made at all (plain world ground)", 0xFF2020),
        STRUCTURE("a structure", 0xB000FF),
        TREE("a tree or leaves", 0x7A4A10),
        WATER("water", 0x2060FF),
        CONTAINER("a chest or other container", 0xFFA000),
        HEIGHT("height off", 0xFF70C0),
        EDGE("biome at the thin edge", 0x80FFFF),
        OTHER("other", 0xFFFF00);
        public final String words;
        public final int rgb;
        Cause(String words, int rgb) { this.words = words; this.rgb = rgb; }
    }

    /** how far the ground may lie from where the plan puts it and still count as right */
    public static final int HEIGHT_TOLERANCE = 3;

    private static final TicketType<ChunkPos> CHECK = TicketType.create("hollowbell_check", Comparator.comparingLong(ChunkPos::toLong));
    private static final int IN_FLIGHT = 48;

    /** one column's answer, and what was found there (for the list of examples) */
    public static final class Col {
        public Cause cause = Cause.OK;
        public String what = "";
    }

    /** the counts for a run (or for one chunk, in the tests) */
    public static final class Tally {
        public final long[] n = new long[Cause.values().length];
        public long columns;
        public long wrong() { long w = 0; for (Cause c : Cause.values()) if (c != Cause.OK && c != Cause.EDGE) w += n[c.ordinal()]; return w; }
    }

    private static final class Job {
        CommandSourceStack src;
        ServerLevel level;
        BellPlan plan;
        int cx, cz, radius;
        final ArrayDeque<ChunkPos> todo = new ArrayDeque<>();
        final Map<Long, ChunkPos> flying = new LinkedHashMap<>();
        int total, done, told;
        int minX, minZ, w, h;
        byte[] map;
        final Tally tally = new Tally();
        final List<String> examples = new ArrayList<>();
        final Map<Cause, Integer> exampleCount = new java.util.EnumMap<>(Cause.class);
        int chunksUntouched, chunksMarked;
        long t0;
    }

    private static @Nullable Job job;

    public static boolean busy() { return job != null; }

    /** starts a check; the answer comes to the one who asked when it's done */
    public static Component start(CommandSourceStack src, int radius) {
        ServerLevel over = src.getServer().overworld();
        WorldOne w = WorldOne.get(src.getServer());
        if (!w.homeClaimed() || HomeGround.plan() == null) return Component.translatable("command.hollowbell.ground_none");
        if (job != null) return Component.translatable("command.hollowbell.check_busy", job.done, job.total);
        BellPlan plan = HomeGround.plan();
        Job j = new Job();
        j.src = src;
        j.level = over;
        j.plan = plan;
        j.cx = w.homeX(); j.cz = w.homeZ();
        j.radius = radius <= 0 ? w.homeRadius() : Math.min(radius, w.homeRadius());
        j.minX = j.cx - j.radius; j.minZ = j.cz - j.radius;
        j.w = j.h = j.radius * 2 + 1;
        j.map = new byte[j.w * j.h];
        java.util.Arrays.fill(j.map, (byte) -1);
        long r2 = (long) j.radius * j.radius;
        for (int x = Math.floorDiv(j.minX, 16); x <= Math.floorDiv(j.minX + j.w - 1, 16); x++)
            for (int z = Math.floorDiv(j.minZ, 16); z <= Math.floorDiv(j.minZ + j.h - 1, 16); z++) {
                if (!plan.near(x, z)) continue;
                boolean any = false;
                for (int i = 0; i < 256 && !any; i += 17) {           // a diagonal of the chunk, then the whole of it
                    int bx = (x << 4) + (i & 15), bz = (z << 4) + (i >> 4);
                    any = inside(j, bx, bz, r2) && plan.painted(bx, bz);
                }
                for (int i = 0; i < 256 && !any; i++) {
                    int bx = (x << 4) + (i & 15), bz = (z << 4) + (i >> 4);
                    any = inside(j, bx, bz, r2) && plan.painted(bx, bz);
                }
                if (any) j.todo.add(new ChunkPos(x, z));
            }
        j.total = j.todo.size();
        j.t0 = System.nanoTime();
        job = j;
        return Component.translatable("command.hollowbell.check_start", j.total, j.radius);
    }

    private static boolean inside(Job j, int x, int z, long r2) {
        long dx = x - j.cx, dz = z - j.cz;
        return dx * dx + dz * dz <= r2;
    }

    /** every tick: asks for more chunks, checks the ones that have come, 30 ms at most */
    public static void tick(MinecraftServer server) {
        Job j = job;
        if (j == null) return;
        var cache = j.level.getChunkSource();
        while (j.flying.size() < IN_FLIGHT && !j.todo.isEmpty()) {
            ChunkPos p = j.todo.poll();
            cache.addRegionTicket(CHECK, p, 0, p);
            j.flying.put(p.toLong(), p);
        }
        long t0 = System.nanoTime();
        for (var it = j.flying.values().iterator(); it.hasNext(); ) {
            if (System.nanoTime() - t0 > 30_000_000L) break;
            ChunkPos p = it.next();
            LevelChunk ch = cache.getChunkNow(p.x, p.z);
            if (ch == null) continue;
            try {
                checkChunk(j, ch);
            } catch (Throwable t) {
                HollowbellMod.LOG.error("Checking chunk {} failed", p, t);
            }
            cache.removeRegionTicket(CHECK, p, 0, p);
            it.remove();
            j.done++;
        }
        int pct = j.total == 0 ? 100 : j.done * 100 / j.total;
        if (pct / 10 > j.told / 10 && j.done < j.total) {
            j.told = pct;
            j.src.sendSuccess(() -> Component.translatable("command.hollowbell.check_progress", j.done, j.total), false);
            HollowbellMod.LOG.info("Ground check: {} of {} chunks", j.done, j.total);
        }
        if (j.todo.isEmpty() && j.flying.isEmpty()) {
            job = null;
            finish(j);
        }
    }

    /** the server stopped: a check going on is dropped */
    public static void forget() { job = null; }

    private static void checkChunk(Job j, LevelChunk ch) {
        long r2 = (long) j.radius * j.radius;
        Col[] cols = new Col[256];
        boolean[] want = new boolean[256];
        for (int i = 0; i < 256; i++) {
            int x = ch.getPos().getMinBlockX() + (i & 15), z = ch.getPos().getMinBlockZ() + (i >> 4);
            want[i] = inside(j, x, z, r2) && j.plan.painted(x, z);
        }
        check(j.level, ch, j.plan, want, cols);
        int made = 0, looked = 0;
        for (int i = 0; i < 256; i++) {
            if (!want[i]) continue;
            int x = ch.getPos().getMinBlockX() + (i & 15), z = ch.getPos().getMinBlockZ() + (i >> 4);
            Col c = cols[i];
            j.tally.columns++;
            j.tally.n[c.cause.ordinal()]++;
            j.map[(z - j.minZ) * j.w + (x - j.minX)] = (byte) c.cause.ordinal();
            looked++;
            if (c.cause != Cause.NOT_MADE) made++;
            if (c.cause != Cause.OK && c.cause != Cause.EDGE) {
                int k = j.exampleCount.merge(c.cause, 1, Integer::sum);
                if (k <= 12) j.examples.add(c.cause.name() + " at " + x + ", " + z + ": " + c.what);
            }
        }
        if (looked > 0 && made == 0) j.chunksUntouched++;
        if (BellGen.made(ch)) j.chunksMarked++;
    }

    /**
     * The check itself, for one loaded chunk: for each wanted column, is it his ground as the plan says it should be?
     * (The plan is worked out again from the world generator's own heights, the same way the world made it.)
     */
    public static void check(ServerLevel level, LevelChunk ch, BellPlan plan, boolean[] want, Col[] out) {
        ChunkPos cp = ch.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        var src = level.getChunkSource();
        int[] y0 = new int[256], lowest = new int[256];
        boolean[] ok = new boolean[256], wet = new boolean[256];
        for (int i = 0; i < 256; i++) {
            out[i] = new Col();
            if (!want[i]) continue;
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            y0[i] = src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState()) - 1;
            lowest[i] = y0[i] - BellPlan.MAX_DOWN - 1;
            ok[i] = true;
        }
        BellPlan.Out o = new BellPlan.Out();
        plan.chunk(cp.x, cp.z, y0, ok, wet, lowest, o);
        List<BoundingBox> boxes = HomeGround.structureBoxes(level, ch, level.structureManager());
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            if (!want[i]) continue;
            int lx = i & 15, lz = i >> 4, x = x0 + lx, z = z0 + lz;
            Col c = out[i];
            int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            Cause found = null;
            String what = "";
            int gy = Integer.MIN_VALUE;
            boolean inWater = false;
            for (int y = top; y > minY; y--) {
                BlockState s = ch.getBlockState(m.set(x, y, z));
                if (s.isAir()) continue;
                boolean ours = HomeGround.isOurs(s);
                boolean fluid = !s.getFluidState().isEmpty();
                if (fluid && (s.is(Blocks.WATER) || s.is(Blocks.LAVA) || s.is(Blocks.BUBBLE_COLUMN) || !ours && !HomeGround.treePart(s))) {
                    if (s.is(Blocks.WATER) && o.above(i, y) == BellPlan.Mat.WATER) continue;   // his own pool
                    inWater = true;
                    int by = y;
                    while (by > minY && !ch.getBlockState(m.set(x, by, z)).getFluidState().isEmpty()) by--;
                    gy = by;
                    if (!s.is(Blocks.WATER)) { found = pick(found, Cause.OTHER); what = name(s) + " at " + y; }
                    break;
                }
                if (ours && s.is(Blocks.GRASS_BLOCK) || ours && s.is(Blocks.MOSS_BLOCK) || ours && kindOf(s) == 1) {
                    if (o.above(i, y) != null) continue;           // his own things standing on the ground
                    gy = y;
                    break;
                }
                if (ours) continue;                                 // his glass, lights, plants
                if (HomeGround.natural(s) || HomeGround.leftAlone(s) && !s.is(Blocks.SNOW)) { gy = y; break; }
                Cause k;
                if (s.hasBlockEntity()) k = Cause.CONTAINER;
                else if (HomeGround.treePart(s) || s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(Blocks.COCOA)) k = Cause.TREE;
                else k = Cause.OTHER;
                if (found == null) what = name(s) + " at " + y + " (top " + top + ")";
                found = pick(found, k);
            }
            boolean inBox = HomeGround.inStructure(boxes, x, z, gy == Integer.MIN_VALUE ? top : gy);
            if (gy == Integer.MIN_VALUE) { c.cause = inBox ? Cause.STRUCTURE : Cause.OTHER; c.what = "no ground found; " + what; continue; }
            BlockState g = ch.getBlockState(m.set(x, gy, z));
            if (inWater) {
                if (found != null) { c.cause = inBox ? Cause.STRUCTURE : found; c.what = what; }
                else if (!HomeGround.isOurs(g)) { c.cause = inBox ? Cause.STRUCTURE : Cause.WATER; c.what = "bed " + name(g) + " at " + gy + " under water"; }
                continue;
            }
            BellPlan.Mat e = o.layers[i] > 0 ? o.layer[i][0] : BellPlan.Mat.KEEP;
            boolean soft = g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.MOSS_BLOCK);
            boolean right;
            if (e == BellPlan.Mat.KEEP) right = true;                                       // the fringe keeps the world's own top
            else if (e == BellPlan.Mat.GRASS || e == BellPlan.Mat.MOSS) right = HomeGround.isOurs(g);
            else right = HomeGround.isOurs(g) && !soft;
            if (inBox && (found != null || !right)) { c.cause = Cause.STRUCTURE; c.what = what.isEmpty() ? name(g) + " at " + gy : what; continue; }
            if (found != null && (right || found != Cause.OTHER)) { c.cause = found; c.what = what + (right ? "" : ", ground not made"); continue; }
            if (!right) {
                c.cause = Cause.NOT_MADE;
                c.what = name(g) + " at " + gy + ", should be " + e + " at " + o.top[i];
                continue;
            }
            if (Math.abs(gy - o.top[i]) > HEIGHT_TOLERANCE) {
                c.cause = Cause.HEIGHT;
                c.what = name(g) + " at " + gy + ", the plan says " + o.top[i] + " (world " + y0[i] + ")";
                continue;
            }
            if (!ch.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(gy + 1), QuartPos.fromBlock(z)).is(HomeGround.BELL_HOLLOWS)) {
                c.cause = Cause.EDGE;
                c.what = "biome " + ch.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(gy + 1), QuartPos.fromBlock(z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
            }
        }
    }

    /** 1 solid ground block of his, 2 anything else of his */
    private static int kindOf(BlockState s) {
        for (BellPlan.Mat mt : BellPlan.Mat.values()) {
            BlockState b = HomeGround.state(mt);
            if (b != null && s.is(b.getBlock())) return mt.kind == 1 ? 1 : 2;
        }
        return 0;
    }

    /** the cause that says most: a container over a tree over anything else */
    private static Cause pick(@Nullable Cause had, Cause now) {
        if (had == null) return now;
        return rank(now) > rank(had) ? now : had;
    }

    private static int rank(Cause c) {
        return switch (c) { case CONTAINER -> 4; case TREE -> 3; case WATER -> 2; default -> 1; };
    }

    private static String name(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(); }

    private static void finish(Job j) {
        Path dir = j.level.getServer().getWorldPath(LevelResource.ROOT);
        StringBuilder sb = new StringBuilder();
        long cols = j.tally.columns, wrong = j.tally.wrong();
        double secs = (System.nanoTime() - j.t0) / 1e9;
        sb.append("Hollowbell ground check\n");
        sb.append("ground at ").append(j.cx).append(", ").append(j.cz).append(", checked ").append(j.radius).append(" blocks out\n");
        sb.append("chunks: ").append(j.total).append(" (").append(j.chunksUntouched).append(" not made at all, ").append(j.chunksMarked).append(" marked as made), took ")
                .append(String.format("%.0f", secs)).append(" s\n");
        sb.append("columns of his ground: ").append(cols).append("\n");
        sb.append("right: ").append(j.tally.n[Cause.OK.ordinal()]).append("\n");
        sb.append("wrong: ").append(wrong).append(String.format(" (%.3f%%)", cols == 0 ? 0 : wrong * 100.0 / cols)).append("\n");
        for (Cause c : Cause.values()) {
            if (c == Cause.OK) continue;
            sb.append("  ").append(c.name().toLowerCase()).append(" - ").append(c.words).append(": ").append(j.tally.n[c.ordinal()]).append("\n");
        }
        sb.append("(the thin edge is not counted as wrong)\n");
        sb.append("\nexamples:\n");
        for (String e : j.examples) sb.append("  ").append(e).append("\n");
        String text = sb.toString();
        try {
            Files.writeString(dir.resolve(HollowbellMod.MOD_ID + "_ground_check.txt"), text);
            draw(j, dir.resolve(HollowbellMod.MOD_ID + "_ground_check.png"), false);
            draw(j, dir.resolve(HollowbellMod.MOD_ID + "_ground_check_causes.png"), true);
        } catch (Exception e) {
            HollowbellMod.LOG.error("Could not write the ground check", e);
        }
        HollowbellMod.LOG.info("Ground check done:\n{}", text);
        j.src.sendSuccess(() -> Component.translatable("command.hollowbell.check_done", cols, wrong,
                String.format("%.2f", cols == 0 ? 0 : wrong * 100.0 / cols), HollowbellMod.MOD_ID + "_ground_check.png"), true);
    }

    private static void draw(Job j, Path to, boolean causes) throws java.io.IOException {
        var img = new java.awt.image.BufferedImage(j.w, j.h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < j.h; z++) for (int x = 0; x < j.w; x++) {
            int v = j.map[z * j.w + x];
            int rgb;
            if (v < 0) rgb = 0x808080;
            else {
                Cause c = Cause.values()[v];
                if (causes) rgb = c.rgb;
                else rgb = c == Cause.OK ? 0x3CB043 : c == Cause.EDGE ? 0x9FD8A6 : 0xE02020;
            }
            img.setRGB(x, z, rgb);
        }
        javax.imageio.ImageIO.write(img, "png", to.toFile());
    }
}
