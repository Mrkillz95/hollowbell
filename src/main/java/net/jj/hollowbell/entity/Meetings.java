package net.jj.hollowbell.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Giants meeting in the world: when two of JJ's bosses wander near each other they either fight or keep away. The
 * same table and rules in all five mods, keyed by the other's mod id (read from its {@link #KIND} tag first, then
 * from its entity type).
 */
public final class Meetings {
    private Meetings() {}

    public enum Way { FIGHT, AVOID, NONE }

    /** a giant that has backed down carries this for two minutes: nobody fights it, and it fights no giant */
    public static final String YIELD = "jj_giant_yield";
    /** every giant's main body and part carries jj_giant_kind:<its mod id> */
    public static final String KIND = "jj_giant_kind:";
    public static final String ME = "hollowbell";

    /** the six, in the table's order: Pitchgut, Furrowmaw, Cerberus, Hollowbell, Willow, Wreckback */
    static final String[] KINDS = {"mountain_breathes", "furrowmaw", "fire_ice_cerberus", "hollowbell", "lanternwillow", "wreckback"};
    /** f fight, a avoid (the same table in all six mods) */
    private static final String[] TABLE = {
            // P    F    C    H    W    WB
            "-ffaaf",   // Pitchgut
            "f-faff",   // Furrowmaw
            "ff-fff",   // Cerberus
            "aaf-aa",   // Hollowbell
            "affa-a",   // Willow
            "fffaa-"};  // Wreckback

    private static int index(@Nullable String kind) {
        if (kind == null) return -1;
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return i;
        return -1;
    }

    /**
     * Who says "X and Y are fighting!": the one that starts the fight, once. A board shared by all five mods in the
     * same server (kept in the JVM's system properties, so no mod needs the others) stops the second one saying it
     * again within 6000 ticks. True for the first claim on a pair.
     */
    @SuppressWarnings("unchecked")
    public static boolean claimMeetingLine(java.util.UUID a, java.util.UUID b, long gameTime) {
        java.util.Properties p = System.getProperties();
        Object o = p.get("jj.giants.meetings");
        if (!(o instanceof java.util.Map)) { p.putIfAbsent("jj.giants.meetings", new java.util.concurrent.ConcurrentHashMap<String, Long>()); o = p.get("jj.giants.meetings"); }
        java.util.Map<String, Long> m = (java.util.Map<String, Long>) o;
        String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        boolean[] won = {false};
        m.compute(key, (k, v) -> { if (v == null || Math.abs(gameTime - v) > 6000L) { won[0] = true; return gameTime; } return v; });
        return won[0];
    }

    /** what a giant of kind "me" does on meeting one of kind "other" */
    public static Way way(String me, @Nullable String other) {
        int a = index(me), b = index(other);
        if (a < 0 || b < 0 || a == b) return Way.NONE;
        return TABLE[a].charAt(b) == 'f' ? Way.FIGHT : Way.AVOID;
    }

    /** which of the five this is (its kind tag first, then its entity type's mod), or null for anything else */
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

    /** how far off he notices another giant: 160 blocks at full size by default, kept between 48 and 320 */
    public static double range(float size) {
        return Math.max(48, Math.min(320, net.jj.hollowbell.HollowbellConfig.V.meetRange * size));
    }
}
