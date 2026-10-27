package net.jj.hollowbell.entity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone and everything that has hurt him, and when each was last anywhere near him. A grudge holds while its
 * target is alive and about: hiding, going out of sight, walking off a way, him stepping out of the world or the world
 * being reloaded don't end it. It ends when the target dies, keeps well away from him for {@link #LET_GO} ticks, he
 * is told to forget, or he backs down to it.
 */
public final class Grudges {
    /** how long the target has to keep well away before he lets it go (10 minutes) */
    public static final long LET_GO = 12000L;

    /** at most this many held at once (the oldest goes) */
    static final int MOST = 16;

    private final Map<UUID, Long> seen = new LinkedHashMap<>();

    public boolean has(@Nullable UUID who) {
        return who != null && seen.containsKey(who);
    }

    public boolean isEmpty() {
        return seen.isEmpty();
    }

    public int size() {
        return seen.size();
    }

    /** a new one, or an old one made fresh (it hurt him again) */
    public void hold(UUID who, long now) {
        seen.remove(who);
        seen.put(who, now);
        while (seen.size() > MOST) {
            Iterator<UUID> it = seen.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    public void drop(@Nullable UUID who) {
        if (who != null) seen.remove(who);
    }

    void clear() {
        seen.clear();
    }

    Iterable<UUID> all() {
        return seen.keySet();
    }

    /**
     * Once a second: a target within {@code near} blocks of him, in his world, is seen now; one that is dead is
     * dropped; one that has kept away past {@link #LET_GO} is let go.
     */
    void tick(ServerLevel sl, Entity him, double near) {
        long now = sl.getGameTime();
        for (Iterator<Map.Entry<UUID, Long>> it = seen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Entity t = find(sl, e.getKey());
            if (t instanceof LivingEntity le && le.isDeadOrDying()) it.remove();
            else if (t != null && t.level() == sl && t.distanceToSqr(him) < near * near) e.setValue(now);
            else if (now - e.getValue() > LET_GO || now < e.getValue() - LET_GO) it.remove();
        }
    }

    /** a target held anywhere it can be found: a player in any world, anything else in his */
    @Nullable
    static Entity find(ServerLevel sl, UUID who) {
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(who);
        return p != null ? p : sl.getEntity(who);
    }

    /** the nearest one within {@code near} blocks, in his world, that he may go after */
    @Nullable
    LivingEntity nearest(ServerLevel sl, Entity him, double near, Predicate<LivingEntity> may) {
        LivingEntity best = null;
        double bd = near * near;
        for (UUID u : seen.keySet()) {
            if (find(sl, u) instanceof LivingEntity le && le.level() == sl && le.isAlive() && may.test(le)) {
                double d = le.distanceToSqr(him);
                if (d < bd) {
                    bd = d;
                    best = le;
                }
            }
        }
        return best;
    }

    ListTag save() {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Long> e : seen.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Who", e.getKey());
            t.putLong("Seen", e.getValue());
            l.add(t);
        }
        return l;
    }

    /** reads the list back; worlds saved before kept just who it was, so those count as seen now */
    void load(CompoundTag tag, long now) {
        seen.clear();
        if (!tag.contains("Grudges", Tag.TAG_LIST)) return;
        ListTag old = tag.getList("Grudges", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < old.size(); i++) seen.put(NbtUtils.loadUUID(old.get(i)), now);
        ListTag l = tag.getList("Grudges", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.hasUUID("Who"))
                seen.put(t.getUUID("Who"), t.contains("Seen", Tag.TAG_LONG) ? t.getLong("Seen") : now);
        }
    }
}
