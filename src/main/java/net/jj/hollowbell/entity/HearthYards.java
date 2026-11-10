package net.jj.hollowbell.entity;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Hearthstride's yards: the yard of his home ground, and the ground round him wherever he stands. They are safe
 * ground for giant fights: no fight between two giants starts in one on its own (a hunter may still go for him), and
 * the giants leave the players in one alone. A giant docked beside him carries jj_docked:&lt;his UUID&gt; and starts
 * no fight of its own either. The yards are kept on a board in the system properties that every giant mod reads
 * (this same file is in each): "&lt;dimension&gt;|&lt;name&gt;" to {x, z, radius}.
 */
public final class HearthYards {
    private HearthYards() {}

    public static final String BOARD = "jj.giants.yards";
    /** a giant docked beside a Hearthstride carries this tag followed by his UUID */
    public static final String DOCKED = "jj_docked:";

    private static final String HEARTH = "hearthstride";
    private static final String KIND = "jj_giant_kind:";

    @SuppressWarnings("unchecked")
    static Map<String, double[]> board() {
        Properties p = System.getProperties();
        Object o = p.get(BOARD);
        if (!(o instanceof Map)) {
            p.putIfAbsent(BOARD, new ConcurrentHashMap<String, double[]>());
            o = p.get(BOARD);
        }
        return (Map<String, double[]>) o;
    }

    /** a yard, or the same one moved */
    public static void put(String dim, String name, double x, double z, double r) {
        board().put(dim + "|" + name, new double[] {x, z, r});
    }

    public static void remove(String dim, String name) {
        board().remove(dim + "|" + name);
    }

    /** this yard, in whatever dimension it is */
    public static void removeAll(String name) {
        board().keySet().removeIf(k -> k.endsWith("|" + name));
    }

    public static void clear() {
        board().clear();
    }

    /** this spot is in a yard */
    public static boolean inside(Level l, double x, double z) {
        Map<String, double[]> b = board();
        if (b.isEmpty()) return false;
        String dim = l.dimension().location().toString();
        for (Map.Entry<String, double[]> e : b.entrySet()) {
            String k = e.getKey();
            double[] v = e.getValue();
            if (v == null || v.length < 3 || k.length() <= dim.length() || k.charAt(dim.length()) != '|'
                    || !k.startsWith(dim)) continue;
            double dx = x - v[0], dz = z - v[1];
            if (dx * dx + dz * dz < v[2] * v[2]) return true;
        }
        return false;
    }

    public static boolean inside(@Nullable Entity e) {
        return e != null && inside(e.level(), e.getX(), e.getZ());
    }

    /** docked beside a Hearthstride */
    public static boolean docked(@Nullable Entity e) {
        if (e == null) return false;
        for (String t : e.getTags()) if (t.startsWith(DOCKED)) return true;
        return false;
    }

    /** a Hearthstride himself */
    public static boolean isHearth(@Nullable Entity e) {
        if (e == null) return false;
        if (e.getTags().contains(KIND + HEARTH)) return true;
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(HEARTH);
    }

    /**
     * a fight between these two giants may not start on its own here: either is docked, or either stands in a yard.
     * A fight with the Hearthstride himself is left to the meeting table and the world's settings.
     */
    public static boolean noFight(@Nullable Entity a, @Nullable Entity b) {
        if (isHearth(a) || isHearth(b)) return false;
        return docked(a) || docked(b) || inside(a) || inside(b);
    }
}
