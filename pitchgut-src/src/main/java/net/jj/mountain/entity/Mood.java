package net.jj.mountain.entity;

import net.jj.mountain.MountainConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What the book costs him.
 *
 * Two things are tracked. His wind is short: every order off the book takes some of it, and it comes back on its
 * own while he is left alone, faster while he sleeps or lies under the ground. Run him out of wind and the big
 * moves are simply not in him until he has had a moment.
 *
 * The other is slower. Keep hammering him when he has nothing left, or send him at things that cost him pieces
 * of himself, and he starts to resent whoever is holding the book. That builds far more slowly than it falls,
 * but it is what actually changes the game: first he drags his feet, then he starts ignoring lines, then he does
 * what he likes rather than what he was told, and at the top of it he comes for the person holding the book.
 */
public final class Mood {
    /** he does as he is told / he takes his time / he ignores some of it / he picks his own targets / he is done with you */
    public static final int FINE = 0, SLOW = 1, BALKY = 2, WILFUL = 3, TURNED = 4;
    /** where one stage gives way to the next */
    private static final float AT_SLOW = 0.30f, AT_BALKY = 0.55f, AT_WILFUL = 0.78f, AT_TURNED = 0.95f;

    private final MountainEntity m;
    private float wind = 1f;
    private final Map<UUID, Float> sour = new HashMap<>();
    /** whoever had him break the world: he hunts them until this gametime, book or no book */
    private final Map<UUID, Long> after = new HashMap<>();
    /** how many times each of them has hit him while his own book was keeping him off them */
    private final Map<UUID, Integer> struck = new HashMap<>();

    Mood(MountainEntity m) { this.m = m; }

    // ------------------------------------------------------------------ his wind
    public float wind() { return wind; }
    public boolean costsAnything() { return MountainConfig.V.bookCosts; }
    public boolean hasWind(float cost) { return !costsAnything() || cost <= 0f || wind >= cost; }

    public void spendWind(float cost) {
        if (!costsAnything() || cost <= 0f) return;
        wind = Math.max(0f, wind - cost);
    }

    /** an order that fell over after it was paid for: he gets his breath back */
    public void refund(float cost) {
        if (cost > 0f) wind = Math.min(1f, wind + cost);
    }

    /** every tick: his wind coming back, and whatever he is holding against people easing off */
    void tick() {
        int secs = Math.max(5, MountainConfig.V.windSeconds);
        float back = 1f / (secs * 20f);
        boolean resting = m.sleeping() || m.burrowed();
        if (resting) back *= 2.5f;
        else if (m.attackNow() != 0) back *= 0.35f;
        else if (m.staying() && m.getTarget() == null) back *= 1.5f;
        wind = Math.min(1f, wind + back);

        if (!sour.isEmpty()) {
            float off = (0.32f / 24000f) * (resting ? 4f : 1f);
            for (Iterator<Map.Entry<UUID, Float>> it = sour.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Float> e = it.next();
                float v = e.getValue() - off;
                if (v <= 0f) it.remove(); else e.setValue(v);
            }
        }
        if (!after.isEmpty()) {
            long now = m.level().getGameTime();
            after.entrySet().removeIf(e -> e.getValue() <= now);
        }
    }

    // ------------------------------------------------------------------ what he holds against you
    public float sourOf(UUID who) { return who == null ? 0f : sour.getOrDefault(who, 0f); }

    /** pushing him: the number is before his temper is taken into account */
    public void sour(UUID who, float amount) {
        if (who == null || amount <= 0f || !costsAnything()) return;
        float rate = Math.max(0f, MountainConfig.V.grudgeRate);
        if (rate <= 0f) return;
        sour.merge(who, Mth.clamp(amount * rate, 0f, 1f), (a, b) -> Math.min(1f, a + b));
    }

    /** feeding him takes the edge off, for everybody */
    public void fed() {
        if (sour.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Float>> it = sour.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Float> e = it.next();
            float v = e.getValue() - 0.06f;
            if (v <= 0f) it.remove(); else e.setValue(v);
        }
    }

    /** takes a line off someone, for the command that puts him right */
    public void settle(UUID who) {
        if (who == null) { sour.clear(); after.clear(); struck.clear(); wind = 1f; return; }
        sour.remove(who); after.remove(who); struck.remove(who);
    }

    /**
     * A hit from somebody the book is protecting. He wears it, up to a point. Past that the book means nothing
     * coming from them: returns true on the hit that finishes his patience.
     */
    public boolean struck(UUID who) {
        int most = MountainConfig.V.freeHits;
        if (who == null || most <= 0 || !costsAnything()) return false;
        int n = struck.merge(who, 1, Integer::sum);
        if (n < most) return false;
        struck.remove(who);
        sour.put(who, 1f);                              // straight through to the end of his patience
        return true;
    }

    /** how many he has left in him for this one before the book stops meaning anything */
    public int strikesLeft(UUID who) {
        int most = MountainConfig.V.freeHits;
        if (who == null || most <= 0) return Integer.MAX_VALUE;
        return Math.max(0, most - struck.getOrDefault(who, 0));
    }

    /** he is done listening to this one, book or no book */
    public boolean turnedOn(UUID who) { return who != null && stage(who) == TURNED; }

    public int stage(UUID who) {
        if (!costsAnything()) return FINE;
        float v = sourOf(who);
        if (v >= AT_TURNED) return TURNED;
        if (v >= AT_WILFUL) return WILFUL;
        if (v >= AT_BALKY) return BALKY;
        if (v >= AT_SLOW) return SLOW;
        return FINE;
    }

    // ------------------------------------------------------------------ the one he is hunting
    public void hunt(UUID who, long ticks) {
        if (who == null) return;
        long until = m.level().getGameTime() + Math.max(0, ticks);
        after.merge(who, until, Math::max);
        sour.put(who, 1f);
    }

    public boolean hunting(UUID who) {
        if (who == null || after.isEmpty()) return false;
        Long until = after.get(who);
        return until != null && until > m.level().getGameTime();
    }

    public boolean huntingAnyone() { return !after.isEmpty(); }

    /** how much longer, in Minecraft days, rounded up */
    public int huntDaysLeft(UUID who) {
        Long until = after.get(who);
        if (until == null) return 0;
        long left = until - m.level().getGameTime();
        return left <= 0 ? 0 : (int) Math.ceil(left / 24000.0);
    }

    // ------------------------------------------------------------------ saved with him
    void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putFloat("Wind", wind);
        if (!sour.isEmpty()) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, Float> e : sour.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.put("Id", NbtUtils.createUUID(e.getKey()));
                one.putFloat("V", e.getValue());
                l.add(one);
            }
            t.put("Sour", l);
        }
        if (!after.isEmpty()) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, Long> e : after.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.put("Id", NbtUtils.createUUID(e.getKey()));
                one.putLong("Until", e.getValue());
                l.add(one);
            }
            t.put("After", l);
        }
        if (!struck.isEmpty()) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, Integer> e : struck.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.put("Id", NbtUtils.createUUID(e.getKey()));
                one.putInt("N", e.getValue());
                l.add(one);
            }
            t.put("Struck", l);
        }
        tag.put("Mood", t);
    }

    void load(CompoundTag tag) {
        sour.clear();
        after.clear();
        struck.clear();
        wind = 1f;
        if (!tag.contains("Mood", Tag.TAG_COMPOUND)) return;
        CompoundTag t = tag.getCompound("Mood");
        if (t.contains("Wind")) wind = Mth.clamp(t.getFloat("Wind"), 0f, 1f);
        if (t.contains("Sour", Tag.TAG_LIST)) {
            ListTag l = t.getList("Sour", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag one = l.getCompound(i);
                if (!one.contains("Id")) continue;
                sour.put(NbtUtils.loadUUID(one.get("Id")), Mth.clamp(one.getFloat("V"), 0f, 1f));
            }
        }
        if (t.contains("Struck", Tag.TAG_LIST)) {
            ListTag l = t.getList("Struck", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag one = l.getCompound(i);
                if (!one.contains("Id")) continue;
                struck.put(NbtUtils.loadUUID(one.get("Id")), Math.max(0, one.getInt("N")));
            }
        }
        if (t.contains("After", Tag.TAG_LIST)) {
            ListTag l = t.getList("After", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag one = l.getCompound(i);
                if (!one.contains("Id")) continue;
                after.put(NbtUtils.loadUUID(one.get("Id")), one.getLong("Until"));
            }
        }
    }
}
