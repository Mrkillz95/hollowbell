package net.jj.hollowbell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.jj.hollowbell.world.GroundRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The behaviour settings every giant shares (ADMIN_BRIDGE.md 19): his temper, when he sleeps, his strength at night,
 * how he roams, seeking other giants, raids, his spawn size spread and his loot. The same file in every giant mod, only
 * the config class differs. As shipped (normal, home, usual, 1, 1-1, off) every helper hands back the old value, draws
 * no random number and looks at nothing.
 */
public final class GiantsBehave {
    private GiantsBehave() {}

    public static final List<String> TEMPERS = List.of("peaceful", "normal", "hunter");
    public static final List<String> ROAMS = List.of("home", "wander", "stay");
    public static final List<String> SLEEPS = List.of("usual", "day", "never");

    /** the config file's own limits for the shared settings */
    static void clamp(BellConfig.Values v) {
        v.spawnSizeMin = Float.isFinite(v.spawnSizeMin) ? Mth.clamp(v.spawnSizeMin, 0.01f, 4f) : 1f;
        v.spawnSizeMax = Float.isFinite(v.spawnSizeMax) ? Mth.clamp(v.spawnSizeMax, 0.01f, 4f) : 1f;
        if (v.spawnSizeMax < v.spawnSizeMin) {
            float t = v.spawnSizeMin;
            v.spawnSizeMin = v.spawnSizeMax;
            v.spawnSizeMax = t;
        }
        v.worldCount = Mth.clamp(v.worldCount, 1, 8);
        v.temper = option(v.temper, TEMPERS, "normal");
        v.roam = option(v.roam, ROAMS, "home");
        v.sleepSchedule = option(v.sleepSchedule, SLEEPS, "usual");
        v.nightStrength = Float.isFinite(v.nightStrength) ? Mth.clamp(v.nightStrength, 0.5f, 4f) : 1f;
        v.lootMultiplier = Float.isFinite(v.lootMultiplier) ? Mth.clamp(v.lootMultiplier, 0f, 10f) : 1f;
    }

    private static String option(@Nullable String s, List<String> ok, String or) {
        if (s == null) return or;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return ok.contains(t) ? t : or;
    }

    private static BellConfig.Values v() {
        return BellConfig.V;
    }

    // night and day

    /** a level with a day and a night (the overworld; not the nether or the end) */
    public static boolean hasNights(Level l) {
        return !l.dimensionType().hasFixedTime();
    }

    public static boolean night(Level l) {
        return hasNights(l) && !l.isDay();
    }

    public static boolean dayNow(Level l) {
        return hasNights(l) && l.isDay();
    }

    // temper and sleep

    /** temper peaceful: he never starts a fight with a player */
    public static boolean peaceful() {
        return "peaceful".equals(v().temper);
    }

    public static boolean hunterTemper() {
        return "hunter".equals(v().temper);
    }

    /** sleeps all day, hunts all night (only where there are nights) */
    public static boolean daySleeper(Level l) {
        return "day".equals(v().sleepSchedule) && hasNights(l);
    }

    public static boolean neverSleeps() {
        return "never".equals(v().sleepSchedule);
    }

    /**
     * Hunts every player he sees just now, whatever his mood: temper hunter, a day sleeper at night, or raiding. Never
     * when peaceful.
     */
    public static boolean huntsNow(Level l) {
        if (peaceful()) return false;
        return hunterTemper() || v().raidVillages || "day".equals(v().sleepSchedule) && night(l);
    }

    /** the quiet ticks a day sleeper waits by day before he lies down (10 seconds) */
    public static final int DAY_SLEEP_AFTER = 200;

    // his hits

    /** at night every hit he lands times nightStrength (1: the same number) */
    public static float nightHit(Level l, float dmg) {
        float n = v().nightStrength;
        if (n == 1f || !night(l)) return dmg;
        return dmg * n;
    }

    // spawning

    /** the size the world's own one (and a companion) comes at, before the all-giants size: worldScale x the spread */
    public static float spawnScale(RandomSource r) {
        float lo = v().spawnSizeMin, hi = v().spawnSizeMax, w = v().worldScale;
        if (lo == 1f && hi == 1f) return w;
        float m = hi > lo ? lo + (hi - lo) * r.nextFloat() : lo;
        return w * m;
    }

    // loot

    /** what he drops when he falls times lootMultiplier: counts multiplied, the fraction rounded at random */
    public static List<ItemStack> loot(List<ItemStack> in, RandomSource r) {
        float m = v().lootMultiplier;
        if (m == 1f) return in;
        List<ItemStack> out = new ArrayList<>();
        if (m <= 0f) return out;
        int extra = 0;
        for (ItemStack s : in) {
            if (s.isEmpty()) continue;
            double want = s.getCount() * (double) m;
            int n = (int) Math.floor(want);
            if (r.nextDouble() < want - n) n++;
            int most = Math.max(1, s.getMaxStackSize());
            boolean first = true;
            while (n > 0) {
                if (!first && ++extra > 64) break;
                int k = Math.min(n, most);
                out.add(s.copyWithCount(k));
                n -= k;
                first = false;
            }
        }
        return out;
    }

    // roaming, seeking and raids

    /** the shared once-every-10-seconds look-around, spread over the ticks by entity id */
    public static boolean lookNow(Entity e) {
        return Math.floorMod(e.tickCount + e.getId(), 200) == 0;
    }

    public static boolean wanders() {
        return "wander".equals(v().roam);
    }

    public static boolean stays() {
        return "stay".equals(v().roam);
    }

    /**
     * A far wander spot (roam wander): 300-700 blocks off along heading (degrees, 0 = +z), turned up to 45 degrees
     * either way, turned back from the world border and kept 64 blocks inside it. Returns {x, z, new heading}.
     */
    public static double[] farSpot(Level l, double x, double z, double heading, RandomSource r) {
        double h = heading + (r.nextDouble() * 90 - 45);
        double d = 300 + r.nextDouble() * 400;
        WorldBorder b = l.getWorldBorder();
        double m = Math.min(64, b.getSize() / 2 - 1);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(h);
            double tx = x - Math.sin(a) * d, tz = z + Math.cos(a) * d;
            if (tx > b.getMinX() + m && tx < b.getMaxX() - m && tz > b.getMinZ() + m && tz < b.getMaxZ() - m)
                return new double[] {tx, tz, h};
            h += 45 + r.nextDouble() * 90; // (the border: turn away from it)
        }
        double a = Math.toRadians(h);
        return new double[] {
            Mth.clamp(x - Math.sin(a) * d, b.getMinX() + m, b.getMaxX() - m),
            Mth.clamp(z + Math.cos(a) * d, b.getMinZ() + m, b.getMaxZ() - m),
            h
        };
    }

    /** the most a giant looks for another one he seeks, and for the middle of another's ground */
    public static final double SEEK_RANGE = 4000, SEEK_GROUND_RANGE = 20000;

    /**
     * Seeking other giants: the nearest loaded giant that ok accepts (another kind, free to fight) within SEEK_RANGE,
     * else the nearest other giant's ground middle (from the grounds bridge, asked at most once a minute). Null if none.
     */
    public static @Nullable Vec3 seekSpot(ServerLevel sl, Entity me, Predicate<Entity> ok) {
        Entity best = null;
        double bd = SEEK_RANGE * SEEK_RANGE;
        for (Entity e : sl.getAllEntities()) {
            if (e == me || !(e instanceof LivingEntity) || !ok.test(e)) continue;
            double d = e.distanceToSqr(me);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) return best.position();
        if (!net.minecraft.world.level.Level.OVERWORLD.equals(sl.dimension())) return null;
        long now = sl.getGameTime();
        if (grounds == null || now - groundsAt > 1200 || now < groundsAt) {
            grounds = GroundRules.others(sl.getServer(), HollowbellMod.MOD_ID);
            groundsAt = now;
        }
        Vec3 g = null;
        double gd = SEEK_GROUND_RANGE * SEEK_GROUND_RANGE;
        for (GroundRules.Ground o : grounds) {
            double d = Mth.square(o.x() - me.getX()) + Mth.square(o.z() - me.getZ());
            // (already there: nothing to walk to)
            if (d < Mth.square(Math.max(48, o.r() * 0.5))) continue;
            if (d < gd) {
                gd = d;
                g = new Vec3(o.x() + 0.5, me.getY(), o.z() + 0.5);
            }
        }
        return g;
    }

    private static List<GroundRules.Ground> grounds;
    private static long groundsAt;

    /** for the tests: forget the cached grounds */
    public static void forgetGrounds() {
        grounds = null;
    }

    /** the most a raider looks for villagers, and for players */
    public static final double RAID_VILLAGE_RANGE = 384, RAID_PLAYER_RANGE = 2000;

    /** a raid's next stop: the nearest villager within 384 blocks (loaded), else the nearest player he may attack */
    public static @Nullable Vec3 raidSpot(ServerLevel sl, Entity me, Predicate<Player> mayAttack) {
        Vec3 at = me.position();
        double r = RAID_VILLAGE_RANGE;
        Villager best = null;
        double bd = r * r;
        for (Villager vl : sl.getEntitiesOfClass(
                Villager.class, new AABB(at, at).inflate(r, 192, r), e -> e.isAlive() && !e.isRemoved())) {
            double d = vl.distanceToSqr(me);
            if (d < bd) {
                bd = d;
                best = vl;
            }
        }
        if (best != null) return best.position();
        Player bp = null;
        double pd = RAID_PLAYER_RANGE * RAID_PLAYER_RANGE;
        for (Player p : sl.players()) {
            if (p.isSpectator() || p.isCreative() || !mayAttack.test(p)) continue;
            double d = p.distanceToSqr(me);
            if (d < pd) {
                pd = d;
                bp = p;
            }
        }
        return bp == null ? null : bp.position();
    }

    /** while raiding: villagers and iron golems are his targets, like hostile creatures */
    public static boolean raidPrey(@Nullable Entity e) {
        return v().raidVillages && (e instanceof Villager || e instanceof IronGolem);
    }
}
