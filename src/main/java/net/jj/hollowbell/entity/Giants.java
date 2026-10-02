package net.jj.hollowbell.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The giants: JJ's six bosses (Pitchgut, the Furrowmaw, the Cerberus, the Hollowbell, the Lantern Willow and Wreckback). They
 * all follow the same rules with each other: a blow from one giant lands on another by the other's own armour
 * against giants, and none of them ever picks another up, swallows it or drags it about. Each one tags itself
 * {@link #TAG}; the ids below catch the others even from older versions that don't.
 */
public final class Giants {
    private Giants() {}

    /** the scoreboard tag every giant (and each part of one) carries */
    public static final String TAG = "jj_giant";
    /** the tag a giant's small helpers carry (the Hollowbell's bellings, the Willow's mudlings, ...): their blows
     *  count as their giant's */
    public static final String KIN = "jj_giant_kin";

    private static final Set<String> IDS = Set.of(
            "mountain_breathes:mountain", "mountain_breathes:mountain_part",
            "furrowmaw:furrowmaw", "furrowmaw:furrowmaw_part",
            "fire_ice_cerberus:cerberus", "fire_ice_cerberus:cerberus_part",
            "hollowbell:hollowbell", "lanternwillow:lanternwillow",
            "wreckback:wreckback");

    /** one of the giants, or a part of one */
    public static boolean isGiant(@Nullable Entity e) {
        if (e == null) return false;
        if (e.getTags().contains(TAG)) return true;
        return IDS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    /** who is really behind a blow: the one who threw or shot it, if it was thrown or shot */
    public static @Nullable Entity behind(DamageSource src) {
        Entity e = src.getEntity();
        if (e == null) e = src.getDirectEntity();
        for (int i = 0; i < 3 && e != null; i++) {
            Entity owner = e instanceof Projectile p ? p.getOwner() : e instanceof TraceableEntity t ? t.getOwner() : null;
            if (owner == null || owner == e) break;
            e = owner;
        }
        return e;
    }

    /** the blow came from another giant (not from this one) */
    public static boolean fromGiant(DamageSource src, Entity self) {
        Entity e = behind(src);
        return e != null && e != self && (isGiant(e) || e.getTags().contains(KIN));
    }

    private static final java.util.Map<Class<?>, java.util.Optional<java.lang.reflect.Method>> OWNER = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The giant a part box belongs to. The other giants are mostly made of part boxes that aren't creatures themselves
     * (Pitchgut's, the Furrowmaw's rings, the Cerberus's body): each has an {@code owner()}. A creature is its own.
     */
    public static @Nullable net.minecraft.world.entity.LivingEntity ownerOf(@Nullable Entity e) {
        if (e == null) return null;
        if (e instanceof net.minecraft.world.entity.LivingEntity le) return le;
        var m = OWNER.computeIfAbsent(e.getClass(), c -> {
            try { var mm = c.getMethod("owner"); mm.setAccessible(true); return java.util.Optional.of(mm); }
            catch (Exception x) { return java.util.Optional.empty(); }
        });
        if (m.isEmpty()) return null;
        try { return m.get().invoke(e) instanceof net.minecraft.world.entity.LivingEntity le ? le : null; }
        catch (Exception x) { return null; }
    }

    /**
     * How much harder a blow from another giant lands on a giant of this size, so that two giants of the same size
     * take about as long to fight it out whatever their size (their health grows with size faster than their blows do).
     * 1 at size 0.3, which is where the numbers were tuned; about 2 at full size, about half at 0.1.
     * hpExponent: how this giant's health grows with its size (1 = in step with it, 0.8 for Pitchgut and the Cerberus).
     */
    public static float fightPace(float size, float hpExponent) {
        return (float) (pace(size, hpExponent) / pace(0.3f, hpExponent));
    }

    private static double pace(float s, float hpExponent) {
        s = Math.max(0.02f, s);
        double blow = Math.min(1.5, Math.max(0.25, 0.2 + 0.8 * Math.pow(s, 0.6)));
        return Math.pow(s, hpExponent) / blow;
    }

    /** a giant, or riding one, or anything that mustn't be picked up or dragged about by a giant */
    public static boolean carriesGiant(@Nullable Entity e) {
        for (int i = 0; i < 4 && e != null; i++) { if (isGiant(e)) return true; e = e.getVehicle(); }
        return false;
    }
}
