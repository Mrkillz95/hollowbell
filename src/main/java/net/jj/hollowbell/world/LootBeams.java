package net.jj.hollowbell.world;

import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.block.LootCacheBlock;
import net.jj.hollowbell.net.LootBeamsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The loot caches with their beam on, per dimension, kept with the world. A beam is drawn by the cache itself only
 * while its ground is loaded for you, so the server tells every player about the ones within 1024 blocks and
 * their game draws the rest itself. A cache that's emptied or broken is taken off at once; one found on loading
 * (from before this list existed) is put on.
 */
public final class LootBeams extends SavedData {
    public static final String NAME = "hollowbell_loot_beams";
    /** how far off a beam is sent */
    public static final double REACH = 1024;

    private final Set<Long> on = new LinkedHashSet<>();

    private static final SavedData.Factory<LootBeams> FACTORY = new SavedData.Factory<>(LootBeams::new, LootBeams::load, null);

    public static LootBeams get(ServerLevel l) { return l.getDataStorage().computeIfAbsent(FACTORY, NAME); }

    private static LootBeams load(CompoundTag tag, HolderLookup.Provider p) {
        LootBeams b = new LootBeams();
        for (long x : tag.getLongArray("On")) b.on.add(x);
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.put("On", new LongArrayTag(new ArrayList<>(on)));
        return tag;
    }

    /** caches found while their ground loads (maybe off the main thread): put on the list at the next tick */
    private record Seen(Level level, BlockPos pos, boolean lit) {}
    private static final Queue<Seen> seen = new ConcurrentLinkedQueue<>();
    private static volatile boolean changed;
    private static int clock;

    /** a cache's beam went on or off (on the server thread) */
    public static void mark(ServerLevel l, BlockPos pos, boolean lit) {
        LootBeams b = get(l);
        boolean did = lit ? b.on.add(pos.asLong()) : b.on.remove(pos.asLong());
        if (did) { b.setDirty(); changed = true; }
    }

    /** a cache's ground just loaded */
    public static void noteLoaded(Level l, BlockPos pos, boolean lit) {
        if (l instanceof ServerLevel) seen.add(new Seen(l, pos.immutable(), lit));
    }

    public static boolean isOn(ServerLevel l, BlockPos pos) { return get(l).on.contains(pos.asLong()); }

    private static boolean litCache(BlockState st) {
        return st.is(ModBlocks.LOOT_CACHE) && st.hasProperty(LootCacheBlock.LIT) && st.getValue(LootCacheBlock.LIT);
    }

    /** the beams this player should be told about: this dimension, within 1024 blocks across */
    public static List<BlockPos> listFor(ServerPlayer p) {
        List<BlockPos> out = new ArrayList<>();
        if (!(p.level() instanceof ServerLevel l)) return out;
        for (long x : get(l).on) {
            BlockPos b = BlockPos.of(x);
            double dx = b.getX() + 0.5 - p.getX(), dz = b.getZ() + 0.5 - p.getZ();
            if (dx * dx + dz * dz <= REACH * REACH) out.add(b);
            if (out.size() >= 256) break;
        }
        return out;
    }

    public static void serverTick(MinecraftServer server) {
        for (Seen s; (s = seen.poll()) != null; )
            // (what's there now counts: it may have been filled or emptied since it was seen)
            if (s.level() instanceof ServerLevel l && l.getServer() == server)
                mark(l, s.pos(), l.isLoaded(s.pos()) ? litCache(l.getBlockState(s.pos())) : s.lit());
        boolean check = --clock <= 0;
        if (!check && !changed) return;
        if (check) {
            clock = 40;
            // anything that isn't a lit cache any more (loaded ground only; the rest is checked when it loads)
            for (ServerLevel l : server.getAllLevels()) {
                LootBeams b = get(l);
                if (b.on.removeIf(x -> { BlockPos p = BlockPos.of(x); return l.isLoaded(p) && !litCache(l.getBlockState(p)); })) b.setDirty();
            }
        }
        changed = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, LootBeamsPayload.TYPE)) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new LootBeamsPayload(listFor(p)));
        }
    }
}
