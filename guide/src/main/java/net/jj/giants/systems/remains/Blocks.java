package net.jj.giants.systems.remains;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * (remains) Block states as text: turning a block that faces a way with the bone it is on, smoothing a bone's top and
 * underside with stairs and slabs, and joining walls, fences, panes and bars to what is beside them.
 */
public final class Blocks {
    private Blocks() {}

    public static String base(String s) {
        int i = s.indexOf('[');
        return i < 0 ? s : s.substring(0, i);
    }

    static Map<String, String> props(String s) {
        Map<String, String> o = new LinkedHashMap<>();
        int i = s.indexOf('[');
        if (i < 0) return o;
        for (String kv : s.substring(i + 1, s.length() - 1).split(",")) {
            int e = kv.indexOf('=');
            if (e > 0) o.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
        }
        return o;
    }

    static String with(String base, Map<String, String> p) {
        if (p.isEmpty()) return base;
        StringBuilder b = new StringBuilder(base).append('[');
        boolean first = true;
        for (var e : p.entrySet()) {
            if (!first) b.append(',');
            b.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return b.append(']').toString();
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] DXZ = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    static int dirIndex(String d) {
        for (int i = 0; i < 4; i++) if (DIRS[i].equals(d)) return i;
        return -1;
    }

    /** a block put down turned (axis and facing follow the turn); leaves kept; nothing that falls */
    public static String orient(String state, double[] turn) {
        String b = base(state);
        Map<String, String> p = props(state);
        if (p.containsKey("axis")) {
            String a = p.get("axis");
            double[] v = Pose.dir(turn, a.equals("x") ? 1 : 0, a.equals("y") ? 1 : 0, a.equals("z") ? 1 : 0);
            double ax = Math.abs(v[0]), ay = Math.abs(v[1]), az = Math.abs(v[2]);
            p.put("axis", ay >= ax && ay >= az ? "y" : ax >= az ? "x" : "z");
        }
        if (p.containsKey("facing")) {
            int di = dirIndex(p.get("facing"));
            if (di >= 0) {
                double[] v = Pose.dir(turn, DXZ[di][0], 0, DXZ[di][1]);
                if (Math.abs(v[0]) > 0.2 || Math.abs(v[2]) > 0.2)
                    p.put("facing", Math.abs(v[0]) > Math.abs(v[2]) ? (v[0] > 0 ? "east" : "west")
                            : (v[2] > 0 ? "south" : "north"));
            }
        }
        if (b.endsWith("_pane") || b.equals("minecraft:iron_bars")) p.clear();
        return fixed(with(b, p));
    }

    /** what may stand in the world on its own: leaves that never fall off, powder that never falls */
    public static String fixed(String state) {
        String b = base(state);
        if (b.endsWith("_leaves")) {
            Map<String, String> p = props(state);
            p.put("persistent", "true");
            p.remove("distance");
            return with(b, p);
        }
        if (b.endsWith("_concrete_powder")) return b.replace("_concrete_powder", "_concrete");
        if (b.equals("minecraft:sand")) return "minecraft:sandstone";
        if (b.equals("minecraft:red_sand")) return "minecraft:red_sandstone";
        if (b.equals("minecraft:gravel")) return "minecraft:tuff";
        return state;
    }

    // ---------------------------------------------------------------- stairs and slabs

    private static final Set<String> STAIRS = Set.of("smooth_quartz", "quartz", "diorite", "polished_diorite",
            "andesite", "polished_andesite", "granite", "cobblestone", "mossy_cobblestone", "stone", "stone_brick",
            "mossy_stone_brick", "blackstone", "polished_blackstone", "polished_blackstone_brick", "cobbled_deepslate",
            "polished_deepslate", "deepslate_brick", "deepslate_tile", "tuff", "polished_tuff", "tuff_brick",
            "mud_brick", "nether_brick", "red_nether_brick", "end_stone_brick", "prismarine", "sandstone",
            "red_sandstone", "brick", "oak", "spruce", "birch", "dark_oak", "mangrove", "cherry", "jungle", "acacia",
            "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper", "purpur");

    /** the stairs/slab family of a full block, or null */
    static String family(String block) {
        String b = base(block).replace("minecraft:", "");
        String f;
        if (b.equals("bone_block") || b.equals("calcite") || b.equals("smooth_quartz")) f = "smooth_quartz";
        else if (b.equals("quartz_block")) f = "quartz";
        else if (b.endsWith("_planks")) f = b.substring(0, b.length() - 7);
        else if (b.equals("bricks")) f = "brick";
        else if (b.endsWith("_bricks")) f = b.substring(0, b.length() - 1);
        else if (b.equals("deepslate_tiles")) f = "deepslate_tile";
        else if (b.equals("purpur_block")) f = "purpur";
        else f = b;
        return STAIRS.contains(f) ? f : null;
    }

    static boolean full(String block) {
        if (block == null) return false;
        String b = base(block);
        return !(b.endsWith("_stairs") || b.endsWith("_slab") || b.endsWith("_wall") || b.endsWith("_fence")
                || b.endsWith("_pane") || b.endsWith("iron_bars") || b.endsWith("chain") || b.endsWith("lantern")
                || b.endsWith("pointed_dripstone") || b.endsWith("_carpet") || b.endsWith("ladder")
                || b.endsWith("fire") || b.endsWith("cobweb") || b.endsWith("hanging_roots") || b.endsWith("vine")
                || b.endsWith("_button") || b.endsWith("air") || b.endsWith("mangrove_roots")
                || b.endsWith("candle") || b.endsWith("_rod") || b.endsWith("scaffolding"));
    }

    /**
     * Each bone block (the bone layer, a block with stairs) with open air above: one side open makes a stair rising
     * away from it, two sides round a corner an outer corner stair, three or four open a slab. The same under an
     * overhang, upside down (one side open only).
     */
    static void smooth(Carcass c, int[] st) {
        int[] out = st.clone();
        for (int i = 0; i < c.n; i++) {
            int v = st[i];
            if (v == 0 || v != c.bone[i]) continue;
            String block = c.name(v);
            String fam = family(block);
            if (fam == null) continue;
            int x = c.cx[i], y = c.cy[i], z = c.cz[i];
            boolean up = solid(c, st, x, y + 1, z), down = solid(c, st, x, y - 1, z);
            if (up == down) continue;
            boolean[] open = new boolean[4];
            int nOpen = 0;
            for (int d = 0; d < 4; d++) {
                open[d] = !solid(c, st, x + DXZ[d][0], y, z + DXZ[d][1]);
                if (open[d]) nOpen++;
            }
            String half = up ? "top" : "bottom";
            String rep = null;
            if (nOpen == 1) {
                int o = open[0] ? 0 : open[1] ? 1 : open[2] ? 2 : 3;
                rep = "minecraft:" + fam + "_stairs[facing=" + DIRS[(o + 2) % 4] + ",half=" + half
                        + ",shape=straight]";
            } else if (nOpen == 2 && !up) {
                int a = -1, b = -1;
                for (int d = 0; d < 4; d++) if (open[d]) {
                    if (a < 0) a = d;
                    else b = d;
                }
                if ((b - a) % 2 != 0) {
                    // the two closed sides: the high quarter sits between them
                    int s1 = (a + 2) % 4, s2 = (b + 2) % 4;
                    // facing s1; s2 on its right (clockwise) -> outer_right
                    String shape = s2 == (s1 + 1) % 4 ? "outer_right" : "outer_left";
                    rep = "minecraft:" + fam + "_stairs[facing=" + DIRS[s1] + ",half=bottom,shape=" + shape + "]";
                }
            } else if (nOpen >= 3 && !up) {
                rep = "minecraft:" + fam + "_slab[type=bottom]";
            }
            if (rep != null) out[i] = c.mat(rep);
        }
        System.arraycopy(out, 0, st, 0, st.length);
    }

    private static boolean solid(Carcass c, int[] st, int x, int y, int z) {
        int j = c.find(x, y, z);
        if (j < 0 || st[j] == 0) return y < 0;
        return full(c.name(st[j]));
    }

    private static boolean any(Carcass c, int[] st, int x, int y, int z) {
        int j = c.find(x, y, z);
        return j >= 0 && st[j] != 0;
    }

    // ---------------------------------------------------------------- walls, fences, panes, bars

    static void connect(Carcass c, int[] st) {
        for (int i = 0; i < c.n; i++) {
            int v = st[i];
            if (v == 0) continue;
            String block = c.name(v);
            String b = base(block);
            boolean wall = b.endsWith("_wall"), fence = b.endsWith("_fence"), pane = b.endsWith("_pane")
                    || b.equals("minecraft:iron_bars");
            if (!wall && !fence && !pane) continue;
            int x = c.cx[i], y = c.cy[i], z = c.cz[i];
            boolean[] j = new boolean[4];
            for (int d = 0; d < 4; d++) {
                int k = c.find(x + DXZ[d][0], y, z + DXZ[d][1]);
                if (k < 0 || st[k] == 0) continue;
                String nb = c.name(st[k]);
                String nbase = base(nb);
                j[d] = full(nb) || (wall && nbase.endsWith("_wall")) || (fence && nbase.endsWith("_fence"))
                        || (pane && (nbase.endsWith("_pane") || nbase.equals("minecraft:iron_bars")))
                        || (wall && (nbase.endsWith("_pane") || nbase.equals("minecraft:iron_bars")));
            }
            Map<String, String> p = new LinkedHashMap<>();
            for (int d = 0; d < 4; d++) p.put(DIRS[d], wall ? (j[d] ? "low" : "none") : j[d] ? "true" : "false");
            if (wall) {
                boolean straight = (j[0] && j[2] && !j[1] && !j[3]) || (j[1] && j[3] && !j[0] && !j[2]);
                p.put("up", straight && !any(c, st, x, y + 1, z) ? "false" : "true");
            }
            st[i] = c.mat(with(b, p));
        }
    }
}
