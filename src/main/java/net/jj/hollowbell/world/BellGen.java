package net.jj.hollowbell.world;

import com.google.common.collect.ImmutableSet;
import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Bell Hollows are made by the world itself, as its land is made. The claim (where, how big, the seed) is
 * published here as a snapshot that never changes: the world's generator threads read only that. Two hooks use
 * it: the overworld's biome source says bell_hollows for his columns (so F3, /locate, mob spawns and structures all
 * know it), and after the vanilla decoration of a chunk his land is shaped and dressed there (see HomeGround).
 */
public final class BellGen {
    private BellGen() {}

    /** the other JJ giants' ground: a column one of them already took stays theirs */
    private static final Set<String> GIANTS = Set.of("mountain_breathes", "furrowmaw", "fire_ice_cerberus", "hollowbell", "lanternwillow");

    /** everything a generator thread may know. Never changed once made; a new claim makes a new one. */
    public static final class Snap {
        /** the overworld's own biome source and generator: compared by identity (the nether uses the same kinds) */
        public final Object source, generator;
        public final Holder<Biome> biome;
        final ChunkGenerator gen;
        final RandomState randomState;
        final LevelHeightAccessor heights;
        final int sea;
        /** the standard overworld ores, with the biome check turned to "is this the Bell Hollows" */
        final List<PlacedFeature> ores;

        public final boolean claimed;
        public final long seed;
        public final int cx, cz, radius;
        /** the block box his ground can reach (the outline never goes past the radius) */
        public final int minX, maxX, minZ, maxZ;
        /** his ground's outline; only its pure outline functions are used from here, so it is shared */
        public final @Nullable BellPlan outline;
        private volatile Set<Holder<Biome>> unionOf, union;

        Snap(Object source, ChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, int sea, Holder<Biome> biome,
             List<PlacedFeature> ores, boolean claimed, long seed, int cx, int cz, int radius) {
            this.source = source; this.generator = gen; this.gen = gen; this.randomState = rs; this.heights = heights;
            this.sea = sea; this.biome = biome; this.ores = ores;
            this.claimed = claimed; this.seed = seed; this.cx = cx; this.cz = cz; this.radius = radius;
            this.minX = cx - radius - 2; this.maxX = cx + radius + 2; this.minZ = cz - radius - 2; this.maxZ = cz + radius + 2;
            this.outline = claimed ? newPlan() : null;
        }

        Snap withClaim(boolean on, long seed, int cx, int cz, int radius) {
            return new Snap(source, gen, randomState, heights, sea, biome, ores, on, seed, cx, cz, radius);
        }

        /** a plan of his ground for one thread: it keeps caches, so each generator thread has its own */
        public BellPlan newPlan() {
            ChunkGenerator g = gen;
            RandomState rs = randomState;
            LevelHeightAccessor hs = heights;
            return new BellPlan(seed, cx, cz, radius, sea, hs.getMinBuildHeight(), hs.getMaxBuildHeight(),
                    (x, z) -> g.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, hs, rs) - 1);
        }

        public boolean inBox(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

        /** could any of this chunk be his? */
        public boolean touches(ChunkPos p) {
            return claimed && p.getMaxBlockX() >= minX && p.getMinBlockX() <= maxX && p.getMaxBlockZ() >= minZ && p.getMinBlockZ() <= maxZ;
        }

        /** the possible biomes of the overworld and his, so /locate biome knows his is there to be found */
        Set<Holder<Biome>> union(Set<Holder<Biome>> of) {
            Set<Holder<Biome>> u = union;
            if (u != null && unionOf == of) return u;
            if (of.contains(biome)) return of;
            u = ImmutableSet.<Holder<Biome>>builder().addAll(of).add(biome).build();
            unionOf = of;
            union = u;
            return u;
        }
    }

    /** the one snapshot the generator threads read; replaced whole, never changed */
    public static volatile @Nullable Snap SNAP;

    // ------------------------------------------------------------------ made on the server thread

    /**
     * The overworld has been made (before any of its chunks): what the generator threads will need is taken now,
     * once. Nothing here loads a chunk.
     */
    public static void capture(ServerLevel over) {
        var src = over.getChunkSource();
        Holder<Biome> biome;
        try {
            biome = over.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(HomeGround.BELL_HOLLOWS);
        } catch (Exception e) {
            HollowbellMod.LOG.error("The bell_hollows biome is missing from the data pack: {}", e.toString());
            SNAP = null;
            return;
        }
        List<PlacedFeature> ores = new ArrayList<>();
        PlacementFilter ours = new OursFilter(HomeGround.BELL_HOLLOWS);
        var reg = over.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (ResourceKey<PlacedFeature> k : ORES) {
            var holder = reg.getHolder(k);
            if (holder.isEmpty()) continue;
            PlacedFeature pf = holder.get().value();
            List<PlacementModifier> mods = new ArrayList<>();
            for (PlacementModifier m : pf.placement()) mods.add(m instanceof BiomeFilter ? ours : m);
            ores.add(new PlacedFeature(pf.feature(), List.copyOf(mods)));
        }
        LevelHeightAccessor heights = LevelHeightAccessor.create(over.getMinBuildHeight(), over.getHeight());
        SNAP = new Snap(src.getGenerator().getBiomeSource(), src.getGenerator(), src.randomState(), heights, over.getSeaLevel(),
                biome, List.copyOf(ores), false, 0, 0, 0, 0);
    }

    /** the claim changed (made, moved, let go): a new snapshot for the generator threads */
    public static void publish(WorldOne w) {
        Snap s = SNAP;
        if (s == null) return;
        SNAP = s.withClaim(w.homeClaimed(), w.homeSeed(), w.homeX(), w.homeZ(), w.homeRadius());
    }

    /** the server stopped: nothing is kept for the next world */
    public static void forget() { SNAP = null; }

    /** is this spot inside his ground's outline (the finder asks)? */
    public static boolean inGround(int x, int z) {
        Snap s = SNAP;
        return s != null && s.claimed && s.inBox(x, z) && s.outline.dn(x, z) < 1;
    }

    // ------------------------------------------------------------------ the biome hook (generator threads)

    /** a few thousand answers per thread, so the hundreds of calls for one column cost one outline check */
    private static final class Memo {
        final long[] keys = new long[2048];
        final byte[] vals = new byte[2048];
        Snap owner;
    }
    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

    /** the overworld's biome source was asked for a biome: his ground's columns (from y 0 up) are the Bell Hollows */
    public static Holder<Biome> biome(Object source, Holder<Biome> in, int qx, int qy, int qz) {
        Snap s = SNAP;
        if (s == null || !s.claimed || source != s.source) return in;
        return wants(s, in, qx, qy, qz) ? s.biome : in;
    }

    /**
     * The biome hook's whole decision, pure: above y 0, inside the box, not another giant's biome already, and
     * inside the outline of his ground (the middle of the 4x4 column decides). The caves below stay as they are.
     */
    public static boolean wants(Snap s, Holder<Biome> in, int qx, int qy, int qz) {
        if (!s.claimed || s.outline == null || QuartPos.toBlock(qy) < 0) return false;
        int bx = QuartPos.toBlock(qx) + 2, bz = QuartPos.toBlock(qz) + 2;
        if (!s.inBox(bx, bz)) return false;
        if (otherGiant(in)) return false;
        Memo m = MEMO.get();
        if (m.owner != s) { java.util.Arrays.fill(m.vals, (byte) 0); m.owner = s; }
        long key = ((long) qx << 32) ^ (qz & 0xffffffffL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 53);
        if (m.vals[slot] != 0 && m.keys[slot] == key) return m.vals[slot] == 2;
        boolean ours = s.outline.painted(bx, bz);
        m.keys[slot] = key;
        m.vals[slot] = (byte) (ours ? 2 : 1);
        return ours;
    }

    public static boolean otherGiant(Holder<Biome> in) {
        var k = in.unwrapKey();
        return k.isPresent() && GIANTS.contains(k.get().location().getNamespace());
    }

    /** /locate biome needs to know his biome can be found in the overworld */
    public static Set<Holder<Biome>> possible(Object source, Set<Holder<Biome>> in) {
        Snap s = SNAP;
        if (s == null || source != s.source) return in;
        return s.union(in);
    }

    // ------------------------------------------------------------------ the decoration hook (generator threads)

    /** a chunk of the overworld has had its vanilla decoration: if it is his, his land is made in it now */
    public static void decorated(Object generator, WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        Snap s = SNAP;
        if (s == null || !s.claimed || generator != s.generator || !s.touches(chunk.getPos())) return;
        try {
            HomeGround.decorate(level, chunk, structures, s);
        } catch (Throwable t) {
            // never break the world's generation over his ground: the chunk just stays as the world made it
            HollowbellMod.LOG.error("Could not make the Bell Hollows in chunk {}", chunk.getPos(), t);
        }
    }

    /** the standard overworld ores and stone blobs; the biome swap took them, so he puts them back himself */
    @SuppressWarnings("unchecked")
    private static final ResourceKey<PlacedFeature>[] ORES = new ResourceKey[]{
            OrePlacements.ORE_DIRT, OrePlacements.ORE_GRAVEL, OrePlacements.ORE_GRANITE_UPPER, OrePlacements.ORE_GRANITE_LOWER,
            OrePlacements.ORE_DIORITE_UPPER, OrePlacements.ORE_DIORITE_LOWER, OrePlacements.ORE_ANDESITE_UPPER,
            OrePlacements.ORE_ANDESITE_LOWER, OrePlacements.ORE_TUFF,
            OrePlacements.ORE_COAL_UPPER, OrePlacements.ORE_COAL_LOWER, OrePlacements.ORE_IRON_UPPER, OrePlacements.ORE_IRON_MIDDLE,
            OrePlacements.ORE_IRON_SMALL, OrePlacements.ORE_GOLD, OrePlacements.ORE_GOLD_LOWER, OrePlacements.ORE_REDSTONE,
            OrePlacements.ORE_REDSTONE_LOWER, OrePlacements.ORE_DIAMOND, OrePlacements.ORE_DIAMOND_MEDIUM, OrePlacements.ORE_DIAMOND_LARGE,
            OrePlacements.ORE_DIAMOND_BURIED, OrePlacements.ORE_LAPIS, OrePlacements.ORE_LAPIS_BURIED, OrePlacements.ORE_COPPER};

    public static int oreCount() { Snap s = SNAP; return s == null ? 0 : s.ores.size(); }

    /**
     * The ores for one chunk: the vanilla placements, seeded from the world seed and the chunk (never the level's
     * random), each only where the biome at that spot is his (the vanilla ones already cover the rest).
     */
    static void ores(WorldGenLevel level, ChunkAccess chunk, Snap s) {
        ChunkPos cp = chunk.getPos();
        var r = new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        long deco = r.setDecorationSeed(level.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
        BlockPos origin = new BlockPos(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ());
        int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        for (int k = 0; k < s.ores.size(); k++) {
            r.setFeatureSeed(deco, 7000 + k, step);
            s.ores.get(k).place(level, s.gen, r, origin);
        }
    }

    /** the vanilla biome check, asked the other way: only where this spot's biome is the Bell Hollows */
    static final class OursFilter extends PlacementFilter {
        private final ResourceKey<Biome> key;
        OursFilter(ResourceKey<Biome> key) { this.key = key; }
        @Override protected boolean shouldPlace(PlacementContext ctx, RandomSource r, BlockPos p) {
            return ctx.getLevel().getNoiseBiome(QuartPos.fromBlock(p.getX()), QuartPos.fromBlock(p.getY()), QuartPos.fromBlock(p.getZ())).is(key);
        }
        @Override public PlacementModifierType<?> type() { return PlacementModifierType.BIOME_FILTER; }
    }
}
