package net.jj.giants.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.fun.FunGiants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) Grief healing: the blocks a giant broke grow back. While a giant's own entity ticks
 * (the Part 6 block mixin), every block he turns to air or changes is noted with what it was (only the first time,
 * and never a block a giant put there himself in the last half hour, a giant mod's own block, fire or a fluid). When
 * its time comes and its land is loaded, it is put back if the spot still holds air, a fluid, fire or what he left,
 * and nothing stands in it. Off (as shipped): nothing is noted.
 */
public final class Grief {
    private Grief() {}

    /** grief healing is on (set every tick from the setting, read by the mixin) */
    public static volatile boolean on;
    /** the delay, in ticks */
    private static long delay;

    /** one block waiting to grow back */
    private record Entry(BlockState was, BlockState left, long due, long givenUpAt) {}

    /** dimension -> packed position -> what it was */
    private static final Map<String, LinkedHashMap<Long, Entry>> waiting = new LinkedHashMap<>();
    /** spots a giant filled that were empty (dimension/packed position -> game time): his own, not grief */
    private static final Map<String, LinkedHashMap<Long, Long>> placed = new HashMap<>();
    private static int count;
    public static final int MOST = 200_000, PER_TICK = 400;
    private static final long PLACED_FOR = 36000, RETRY = 200, GIVE_UP = 12000;

    static void forget() {
        waiting.clear();
        placed.clear();
        count = 0;
        on = false;
    }

    public static int waiting() {
        return count;
    }

    static long delayOf(String option) {
        return switch (option) {
            case "slow" -> 12000;
            case "normal" -> 3600;
            case "fast" -> 600;
            case "instant" -> 40;
            default -> -1;
        };
    }

    /** the mixin: a giant is about to set this block */
    public static void changing(Level level, BlockPos pos, BlockState now) {
        if (!on || level.isClientSide()) return;
        BlockState old = level.getBlockState(pos);
        if (old == now) return;
        String dim = level.dimension().location().toString();
        long key = pos.asLong(), t = level.getGameTime();
        if (old.isAir()) {
            if (!now.isAir()) {
                LinkedHashMap<Long, Long> m = placed.computeIfAbsent(dim, k -> new LinkedHashMap<>());
                m.put(key, t);
                if (m.size() > 65536) {
                    Iterator<Long> it = m.keySet().iterator();
                    it.next();
                    it.remove();
                }
            }
            return;
        }
        LinkedHashMap<Long, Entry> w = waiting.computeIfAbsent(dim, k -> new LinkedHashMap<>());
        if (w.containsKey(key)) return;
        LinkedHashMap<Long, Long> pm = placed.get(dim);
        Long at = pm == null ? null : pm.get(key);
        if (at != null && t - at < PLACED_FOR) return;
        if (!natural(old)) return;
        w.put(key, new Entry(old, now, t + delay, t + delay + GIVE_UP));
        count++;
        if (count > MOST) dropOldest();
        dirty = true;
    }

    /** a block that grows back: not a giant mod's own, not fire, not a fluid on its own */
    static boolean natural(BlockState s) {
        if (s.isAir() || s.getBlock() instanceof BaseFireBlock || s.getBlock() instanceof LiquidBlock) return false;
        String ns = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        for (net.jj.giants.Giant g : net.jj.giants.Giants.ALL) if (g.modId().equals(ns)) return false;
        return !ns.equals("jj_giants");
    }

    private static void dropOldest() {
        for (LinkedHashMap<Long, Entry> w : waiting.values()) {
            Iterator<Long> it = w.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
                count--;
                return;
            }
        }
    }

    private static boolean dirty;

    /** every tick: the setting read; then up to 400 due blocks put back */
    public static void tick(MinecraftServer server) {
        String opt = Systems.option(server, "griefHeal");
        long d = delayOf(opt);
        if (d < 0) {
            if (on || count > 0) {
                on = false;
                waiting.clear();
                placed.clear();
                count = 0;
                SystemsData.get(server).setDirty();
            }
            return;
        }
        on = true;
        delay = d;
        if (dirty) {
            dirty = false;
            SystemsData.get(server).setDirty();
            SystemsAdmin.changed();
        }
        if (count == 0) return;
        int budget = PER_TICK;
        List<LivingEntity> giants = null;
        for (var de : waiting.entrySet()) {
            ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(de.getKey())));
            LinkedHashMap<Long, Entry> w = de.getValue();
            if (l == null) {
                count -= w.size();
                w.clear();
                continue;
            }
            long t = l.getGameTime();
            Map<Long, Entry> later = new LinkedHashMap<>();
            Iterator<Map.Entry<Long, Entry>> it = w.entrySet().iterator();
            int looked = 0;
            while (it.hasNext() && budget > 0 && looked < 4000) {
                Map.Entry<Long, Entry> x = it.next();
                looked++;
                Entry en = x.getValue();
                if (en.due() > t) continue;
                BlockPos pos = BlockPos.of(x.getKey());
                if (!l.isLoaded(pos)) {
                    if (t > en.givenUpAt() + 24000 * 3) {
                        it.remove();
                        count--;
                    }
                    continue;
                }
                budget--;
                BlockState cur = l.getBlockState(pos);
                if (cur == en.was()) {
                    it.remove();
                    count--;
                    continue;
                }
                boolean free = cur.isAir() || cur == en.left() || cur.getBlock() instanceof BaseFireBlock
                        || cur.getBlock() instanceof LiquidBlock;
                if (!free) {
                    // (somebody built there since: theirs now)
                    it.remove();
                    count--;
                    continue;
                }
                if (giants == null) giants = giants(server);
                if (blocked(l, pos, giants)) {
                    it.remove();
                    count--;
                    if (t < en.givenUpAt()) later.put(x.getKey(), new Entry(en.was(), en.left(), t + RETRY, en.givenUpAt()));
                    continue;
                }
                l.setBlock(pos, en.was(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                it.remove();
                count--;
                dirty = true;
            }
            // (tried again later: to the back of the queue)
            for (var e : later.entrySet()) {
                w.put(e.getKey(), e.getValue());
                count++;
            }
            if (budget <= 0) break;
        }
    }

    private static List<LivingEntity> giantsNow = List.of();
    private static int giantsAt = -100;

    /** the giants standing now (looked for once a second) */
    private static List<LivingEntity> giants(MinecraftServer server) {
        if (server.getTickCount() - giantsAt >= 20 || server.getTickCount() < giantsAt) {
            giantsNow = FunGiants.standing(server, null);
            giantsAt = server.getTickCount();
        }
        return giantsNow;
    }

    /** a living thing (or a giant's body) in the way */
    private static boolean blocked(ServerLevel l, BlockPos pos, List<LivingEntity> giants) {
        AABB box = new AABB(pos);
        for (Entity e : l.getEntities((Entity) null, box, x -> !(x instanceof ItemEntity) && x.isAlive()))
            return true;
        for (LivingEntity g : giants) {
            if (g.level() != l) continue;
            String kind = FunGiants.kindOf(g);
            double r = FunGiants.reach(kind, FunGiants.sizeOf(g)) + 2;
            double h = FunGiants.height(kind, FunGiants.sizeOf(g)) + 2;
            if (Math.abs(pos.getX() + 0.5 - g.getX()) < r && Math.abs(pos.getZ() + 0.5 - g.getZ()) < r
                    && pos.getY() >= g.getY() - 4 && pos.getY() <= g.getY() + h) return true;
        }
        return false;
    }

    // saved with the world: per dimension, a palette of block states and the entries as numbers

    static ListTag save() {
        ListTag out = new ListTag();
        for (var de : waiting.entrySet()) {
            if (de.getValue().isEmpty()) continue;
            Map<BlockState, Integer> pal = new LinkedHashMap<>();
            ListTag palette = new ListTag();
            int n = de.getValue().size();
            long[] pos = new long[n], due = new long[n], give = new long[n], states = new long[n];
            int i = 0;
            for (var x : de.getValue().entrySet()) {
                Entry en = x.getValue();
                int a = pal.computeIfAbsent(en.was(), s -> {
                    palette.add(NbtUtils.writeBlockState(s));
                    return palette.size() - 1;
                });
                int b = pal.computeIfAbsent(en.left(), s -> {
                    palette.add(NbtUtils.writeBlockState(s));
                    return palette.size() - 1;
                });
                pos[i] = x.getKey();
                due[i] = en.due();
                give[i] = en.givenUpAt();
                states[i] = ((long) a << 32) | (b & 0xffffffffL);
                i++;
            }
            CompoundTag t = new CompoundTag();
            t.putString("Dim", de.getKey());
            t.put("Palette", palette);
            t.put("Pos", new LongArrayTag(pos));
            t.put("Due", new LongArrayTag(due));
            t.put("GiveUp", new LongArrayTag(give));
            t.put("States", new LongArrayTag(states));
            out.add(t);
        }
        return out;
    }

    static void load(MinecraftServer server) {
        forget();
        ListTag list = SystemsData.get(server).grief;
        var blocks = server.overworld().holderLookup(Registries.BLOCK);
        for (Tag x : list) {
            if (!(x instanceof CompoundTag t)) continue;
            List<BlockState> pal = new ArrayList<>();
            for (Tag p : t.getList("Palette", Tag.TAG_COMPOUND)) pal.add(NbtUtils.readBlockState(blocks, (CompoundTag) p));
            long[] pos = t.getLongArray("Pos"), due = t.getLongArray("Due"), give = t.getLongArray("GiveUp"),
                    st = t.getLongArray("States");
            if (pos.length != due.length || pos.length != give.length || pos.length != st.length) continue;
            LinkedHashMap<Long, Entry> w = waiting.computeIfAbsent(t.getString("Dim"), k -> new LinkedHashMap<>());
            for (int i = 0; i < pos.length; i++) {
                int a = (int) (st[i] >>> 32), b = (int) st[i];
                if (a < 0 || a >= pal.size() || b < 0 || b >= pal.size()) continue;
                w.put(pos[i], new Entry(pal.get(a), pal.get(b), due[i], give[i]));
                count++;
            }
        }
        if (count > 0) Systems.log("{} broken blocks wait to grow back", count);
    }

    /** (the tests) a block noted as if a giant broke it now */
    public static void noteForTests(Level level, BlockPos pos, BlockState now) {
        changing(level, pos, now);
    }
}
