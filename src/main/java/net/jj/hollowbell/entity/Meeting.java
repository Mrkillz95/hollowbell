package net.jj.hollowbell.entity;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.jj.hollowbell.BellConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Giants meeting in the world: when two giant bosses wander near each other they either fight or keep away. The
 * same table and rules in every giant mod, keyed by the other's mod id (read from its {@link #KIND} tag first, then
 * from its entity type).
 */
public final class Meeting {
    private Meeting() {}

    public enum Way {
        FIGHT,
        AVOID,
        NONE
    }

    /** a giant that has backed down carries this for two minutes: nobody fights it, and it fights no giant */
    public static final String YIELD = "jj_giant_yield";
    /** every giant's main body and part carries jj_giant_kind:<its mod id> */
    public static final String KIND = "jj_giant_kind:";

    public static final String ME = "hollowbell";

    /** the nine, in the table's order: Pitchgut, Furrowmaw, Cerberus, Hollowbell, Willow, Wreckback, Swarmforge,
     *  Gloamwick, Hearthstride */
    static final String[] KINDS = {
        "mountain_breathes",
        "furrowmaw",
        "fire_ice_cerberus",
        "hollowbell",
        "lanternwillow",
        "wreckback",
        "swarmforge",
        "gloamwick",
        "hearthstride"
    };
    /** f fight, a avoid (the same table in every giant mod) */
    private static final String[] TABLE = {
        // P F C H W WB S G HS
        "-ffaaffff", // Pitchgut
        "f-fafffaa", // Furrowmaw
        "ff-ffffff", // Cerberus
        "aaf-aaaaa", // Hollowbell
        "affa-affa", // Willow
        "fffaa-aaa", // Wreckback
        "fffafa-fa", // Swarmforge
        "fafafaf-a", // Gloamwick
        "aaaaaaaa-" // Hearthstride, who never fights
    };

    private static int index(@Nullable String kind) {
        if (kind == null) return -1;
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return i;
        return -1;
    }

    /**
     * Who says "X and Y are fighting!": the one that starts the fight, once. A board shared by all the giant mods in
     * the same server (kept in the JVM's system properties, so no mod needs the others) stops the second one saying it
     * again within 6000 ticks. True for the first claim on a pair.
     */
    @SuppressWarnings("unchecked")
    public static boolean claimMeetingLine(UUID a, UUID b, long gameTime) {
        Properties p = System.getProperties();
        Object o = p.get("jj.giants.meetings");
        if (!(o instanceof Map)) {
            p.putIfAbsent("jj.giants.meetings", new ConcurrentHashMap<String, Long>());
            o = p.get("jj.giants.meetings");
        }
        Map<String, Long> m = (Map<String, Long>) o;
        String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        boolean[] won = {false};
        m.compute(key, (k, v) -> {
            if (v == null || Math.abs(gameTime - v) > 6000L) {
                won[0] = true;
                return gameTime;
            }
            return v;
        });
        return won[0];
    }

    /** what a giant of kind "me" does on meeting one of kind "other" */
    public static Way way(String me, @Nullable String other) {
        int a = index(me), b = index(other);
        if (a < 0 || b < 0 || a == b) return Way.NONE;
        return TABLE[a].charAt(b) == 'f' ? Way.FIGHT : Way.AVOID;
    }

    /**
     * (Part 4, 2C) what he does now on a wild meeting: his meetGiants setting, and as shipped (decide) the table, where
     * seeking other giants makes every meeting with another kind a fight
     */
    public static Way wayNow(@Nullable String other) {
        Way w = way(ME, other);
        if (w == Way.NONE) return w; // (his own kind, or not a giant: nothing, always)
        switch (net.jj.hollowbell.GiantsBehave.meets()) {
            case "fight":
                return Way.FIGHT;
            case "run_away":
                return Way.AVOID;
            case "ignore":
                return Way.NONE;
            default:
                return w == Way.AVOID && BellConfig.V.seekGiants ? Way.FIGHT : w;
        }
    }

    /** which giant this is (its kind tag first, then its entity type's mod), or null for anything else */
    public static @Nullable String kindOf(@Nullable Entity e) {
        if (e == null) return null;
        for (String t : e.getTags()) if (t.startsWith(KIND)) return t.substring(KIND.length());
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
        return index(ns) >= 0 ? ns : null;
    }

    /** its name for the chat: at the start of a sentence ("The Cerberus"), or in the middle ("the Cerberus") */
    public static String name(@Nullable String kind, boolean start) {
        String the = start ? "The " : "the ";
        if (kind == null) return the + "giant";
        return switch (kind) {
            case "mountain_breathes" -> "Pitchgut";
            case "furrowmaw" -> "Furrowmaw";
            case "fire_ice_cerberus" -> the + "Cerberus";
            case "hollowbell" -> the + "Hollowbell";
            case "lanternwillow" -> the + "Lantern Willow";
            case "wreckback" -> "Wreckback";
            case "swarmforge" -> the + "Swarmforge";
            case "gloamwick" -> the + "Gloamwick";
            case "hearthstride" -> the + "Hearthstride";
            default -> the + "giant";
        };
    }

    /** backing down right now (it, or the giant it is a part of) */
    public static boolean yielding(@Nullable Entity e) {
        if (e == null) return false;
        if (e.getTags().contains(YIELD)) return true;
        var o = Giants.ownerOf(e);
        return o != null && o != e && o.getTags().contains(YIELD);
    }

    /** the Hearthstride, the walking house: near him a giant that isn't one of his hunters starts no fights */
    public static final String HEARTH = "hearthstride";

    /** a Hearthstride within 96 blocks (times this giant's size) of here */
    public static boolean nearHearth(ServerLevel sl, @Nullable Entity self, Vec3 at, double size) {
        double r = 96.0 * size;
        return !sl.getEntities(self, new AABB(at, at).inflate(r), e -> e.isAlive() && HEARTH.equals(kindOf(e)))
                .isEmpty();
    }

    /** how far off he notices another giant: 160 blocks at full size by default, kept between 48 and 320 */
    public static double range(float size) {
        return Math.max(48, Math.min(320, BellConfig.V.meetRange * size));
    }
}
