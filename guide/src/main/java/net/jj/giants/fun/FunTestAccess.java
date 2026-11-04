package net.jj.giants.fun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** (the tests) what the game's own events would call */
public final class FunTestAccess {
    private FunTestAccess() {}

    public static void left(ServerPlayer p) {
        Watch.left(p);
        PlayAs.left(p);
    }

    public static void joined(MinecraftServer s, ServerPlayer p) {
        Watch.joined(s, p);
    }

    public static void loaded(MinecraftServer s, LivingEntity e) {
        Marks.loaded(s, e);
    }

    public static void buildTick(MinecraftServer s) {
        Builds.tick(s);
    }

    /** the den radius Builds gives a giant at a size */
    public static int denRadius(String kind, double size) {
        return Mth.clamp((int) Math.round(FunGiants.reach(kind, size) * 0.8), Dens.LEAST, Dens.MOST);
    }

    /** blocks that would trap or slow a giant, spread or grow out of the build, or fall */
    static final Set<Block> BANNED = Set.of(Blocks.COBWEB, Blocks.POWDER_SNOW, Blocks.HONEY_BLOCK, Blocks.SLIME_BLOCK,
            Blocks.SOUL_SAND, Blocks.SWEET_BERRY_BUSH, Blocks.BUDDING_AMETHYST, Blocks.VINE, Blocks.SUGAR_CANE,
            Blocks.BAMBOO, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.ICE, Blocks.FROSTED_ICE, Blocks.SCAFFOLDING,
            Blocks.TNT, Blocks.LAVA);

    /**
     * (the tests) a den or arena worked out on flat ground without building it: "r", "reach", "pieces" (piece
     * blocks), "blocks" (every block it would set), "top" (its highest block), "floorClutter" (blocks
     * standing on the ground inside the radius), "kinds" (how many of each block,
     * by id) and "problems" (banned blocks, water that would run out, blocks that would fall)
     */
    public static Map<String, Object> plan(String kind, double size) {
        int r = kind.startsWith("arena:") ? Builds.ARENAS.get(kind.substring(6)) : denRadius(kind, size);
        Plan p = kind.startsWith("arena:") ? (Plan) Builds.arena(r) : Dens.shape(kind, r, FunGiants.height(kind, size));
        Map<String, Object> out = new HashMap<>();
        out.put("r", r);
        out.put("reach", p.reach());
        out.put("pieces", p.pieceCount());
        int k = p.reach();
        Map<Long, Builds.Col> cols = new HashMap<>();
        long blocks = 0;
        int top = Integer.MIN_VALUE;
        Map<String, Integer> kinds = new HashMap<>();
        for (int x = -k; x <= k; x++)
            for (int z = -k; z <= k; z++) {
                Builds.Col c = new Builds.Col();
                p.column(x, z, Math.sqrt((double) x * x + z * z), 0, c);
                cols.put(((long) x << 32) ^ (z & 0xffffffffL), c);
                blocks += c.ys.size();
                for (int i = 0; i < c.ys.size(); i++) {
                    if (c.states.get(i).isAir()) continue;
                    top = Math.max(top, c.ys.get(i)[0]);
                    kinds.merge(BuiltInRegistries.BLOCK.getKey(c.states.get(i).getBlock()).getPath(), 1, Integer::sum);
                }
            }
        int clutter = 0;
        for (var e : cols.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            if (Math.sqrt((double) x * x + z * z) > r) continue;
            Builds.Col c = e.getValue();
            for (int i = 0; i < c.ys.size(); i++) if (c.ys.get(i)[0] >= 1 && c.states.get(i).blocksMotion()) clutter++;
        }
        out.put("floorClutter", clutter);
        out.put("blocks", blocks);
        out.put("top", top);
        out.put("kinds", kinds);
        List<String> problems = new ArrayList<>();
        for (var e : cols.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            Builds.Col c = e.getValue();
            for (int i = 0; i < c.ys.size(); i++) {
                BlockState s = c.states.get(i);
                int y = c.ys.get(i)[0];
                if (BANNED.contains(s.getBlock()) && problems.size() < 20) problems.add(kind + " " + size + ": "
                        + s.getBlock() + " at " + x + " " + y + " " + z);
                if (!s.getFluidState().isEmpty()) {
                    int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
                    for (int[] n : nb)
                        if (open(cols, x + n[0], y + n[1], z + n[2]) && problems.size() < 20)
                            problems.add(kind + " " + size + ": water runs out of " + s + " at " + x + " " + y + " "
                                    + z);
                }
                if (s.getBlock() instanceof FallingBlock && fallsInto(cols, x, y - 1, z) && problems.size() < 20)
                    problems.add(kind + " " + size + ": " + s.getBlock() + " would fall at " + x + " " + y + " " + z);
            }
        }
        out.put("problems", problems);
        return out;
    }

    /** what the build leaves at a spot: null if it doesn't touch it (flat ground: solid at and under 0) */
    private static BlockState after(Map<Long, Builds.Col> cols, int x, int y, int z) {
        Builds.Col c = cols.get(((long) x << 32) ^ (z & 0xffffffffL));
        BlockState s = null;
        if (c != null) for (int i = 0; i < c.ys.size(); i++) if (c.ys.get(i)[0] == y) s = c.states.get(i);
        if (s != null) return s;
        if (c != null && c.clearFrom <= y) return Blocks.AIR.defaultBlockState();
        return y <= 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    private static boolean open(Map<Long, Builds.Col> cols, int x, int y, int z) {
        BlockState s = after(cols, x, y, z);
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    private static boolean fallsInto(Map<Long, Builds.Col> cols, int x, int y, int z) {
        BlockState s = after(cols, x, y, z);
        return s.isAir() || !s.getFluidState().isEmpty() && !s.blocksMotion();
    }
}
