package net.jj.giants.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The giant editor's material sets: each one a way of drawing a giant's model in other
 * blocks, worked out for his own palette. A set keeps his light and shade (his darkest blocks stay its darkest, his
 * brightest its brightest), keeps what glows glowing and what is see-through see-through, keeps the shape of shaped
 * blocks (a fence stays a fence), and gives small bright parts (eyes, mouths, gems) its own accent. Pure Java and the
 * game's block list, so the server, the book's preview and the tests all work it out the same way.
 */
public final class LookSets {
    private LookSets() {}

    /** the built-in sets, in the book's order */
    public static final List<String> BUILT_IN = List.of("frost", "gold", "shadow", "festive", "ancient");

    /** what a custom set is called in ids: "custom:<name>" */
    public static final String CUSTOM = "custom:";

    /** one set: its ramp of plain blocks dark to light (or a festive set's three), accent, glow, glass, shapes */
    record Set(List<String> ramp, Map<String, List<String>> families, List<String> accent, List<String> glow,
            @Nullable String clear, @Nullable String leaves, @Nullable String fence, @Nullable String trapdoor,
            @Nullable String lantern) {}

    private static List<String> mc(String... ids) {
        List<String> l = new ArrayList<>();
        for (String s : ids) l.add("minecraft:" + s);
        return List.copyOf(l);
    }

    private static final Map<String, Set> SETS = new LinkedHashMap<>();

    static {
        SETS.put("frost", new Set(mc("cyan_terracotta", "light_blue_terracotta", "blue_ice", "packed_ice",
                "white_concrete", "snow_block"), Map.of(), mc("lapis_block", "light_blue_concrete", "blue_ice"),
                mc("sea_lantern"), "minecraft:light_blue_stained_glass", "minecraft:snow_block",
                "minecraft:warped_fence", "minecraft:warped_trapdoor", "minecraft:soul_lantern"));
        SETS.put("gold", new Set(mc("gilded_blackstone", "orange_terracotta", "yellow_terracotta", "raw_gold_block",
                "gold_block", "smooth_sandstone"), Map.of(), mc("redstone_block", "red_concrete"),
                mc("shroomlight", "ochre_froglight"), "minecraft:yellow_stained_glass", "minecraft:yellow_wool",
                "minecraft:bamboo_fence", "minecraft:bamboo_trapdoor", "minecraft:lantern"));
        SETS.put("shadow", new Set(mc("obsidian", "blackstone", "polished_blackstone", "netherite_block",
                "polished_deepslate", "deepslate", "polished_basalt", "tuff"), Map.of(),
                mc("purple_concrete", "amethyst_block"), mc("crying_obsidian"), "minecraft:purple_stained_glass",
                "minecraft:black_wool", "minecraft:nether_brick_fence", "minecraft:crimson_trapdoor",
                "minecraft:soul_lantern"));
        SETS.put("festive", new Set(List.of(), Map.of("red", mc("red_nether_bricks", "red_concrete", "red_wool"),
                "green", mc("green_terracotta", "green_concrete", "green_wool", "lime_concrete"),
                "white", mc("white_wool", "snow_block")), mc("gold_block"),
                mc("ochre_froglight", "pearlescent_froglight"), "minecraft:red_stained_glass", "minecraft:green_wool",
                "minecraft:crimson_fence", "minecraft:crimson_trapdoor", "minecraft:lantern"));
        SETS.put("ancient", new Set(mc("cobbled_deepslate", "tuff", "mossy_cobblestone", "mossy_stone_bricks",
                "stone_bricks", "andesite", "smooth_stone"), Map.of(), mc("oxidized_copper", "moss_block"),
                mc("glowstone"), null, "minecraft:moss_block", "minecraft:mangrove_fence",
                "minecraft:iron_trapdoor", null));
    }

    /** blocks left as they are by every set (metal bits, roots, chests: they carry no colour of their own) */
    private static final List<String> KEEP = List.of("chain", "iron_bars", "ladder", "hanging_roots",
            "mangrove_roots", "chest", "barrel");

    /** a part this small (of all his blocks) takes the set's accent when it's bright and strongly coloured */
    private static final double ACCENT_SHARE = 0.015;

    public static boolean builtIn(String id) {
        return SETS.containsKey(id);
    }

    // the blocks' colours (tools/editor_colours.py, from the game's own textures)

    private static @Nullable Map<String, int[]> colours;

    static synchronized Map<String, int[]> colours() {
        if (colours != null) return colours;
        Map<String, int[]> m = new HashMap<>();
        try (InputStream in = LookSets.class.getResourceAsStream("/assets/jj_giants/editor/colours.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (var e : o.entrySet()) {
                    var a = e.getValue().getAsJsonArray();
                    m.put(e.getKey(), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
                }
            }
        } catch (Exception e) {
            GiantsGuideMod.LOG.warn("Giant editor: the block colours can't be read", e);
        }
        colours = m;
        return m;
    }

    /** a block's average colour (0xRRGGBB), or grey when unknown */
    public static int rgb(String block) {
        int[] c = colours().get(blockId(block));
        return c == null ? 0x808080 : c[0] << 16 | c[1] << 8 | c[2];
    }

    /** "minecraft:oak_log" of "oak_log[axis=y]" */
    public static String blockId(String entry) {
        int i = entry.indexOf('[');
        String b = i < 0 ? entry : entry.substring(0, i);
        return b.indexOf(':') < 0 ? "minecraft:" + b : b;
    }

    private static String props(String entry) {
        int i = entry.indexOf('[');
        return i < 0 ? "" : entry.substring(i);
    }

    private static double lum(int[] c) {
        return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
    }

    private static double sat(int[] c) {
        int mx = Math.max(c[0], Math.max(c[1], c[2])), mn = Math.min(c[0], Math.min(c[1], c[2]));
        return mx == 0 ? 0 : (mx - mn) / (double) mx;
    }

    private static double hue(int[] c) {
        float[] hsb = java.awt.Color.RGBtoHSB(c[0], c[1], c[2], null);
        return hsb[0] * 360;
    }

    /** the ramp block whose brightness is nearest the point t (0 dark to 1 light) of the ramp's own range */
    private static String pick(List<String> ramp, double t) {
        double lo = Double.MAX_VALUE, hi = -1;
        for (String r : ramp) {
            double l = lum(colourOf(r));
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
        }
        double want = lo + t * (hi - lo);
        String best = ramp.get(0);
        double bd = Double.MAX_VALUE;
        for (String r : ramp) {
            double d = Math.abs(lum(colourOf(r)) - want);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    private static int[] colourOf(String block) {
        int[] c = colours().get(blockId(block));
        return c == null ? new int[] {128, 128, 128} : c;
    }

    // the game's own blocks

    /** a block as the game reads it, or null */
    public static @Nullable BlockState state(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (Exception e) {
            return null;
        }
    }

    /** a block the editor may put on a giant ("gold_block", "minecraft:oak_stairs[facing=east]"), in full; or null */
    public static @Nullable String valid(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.length() > 120) return null;
        if (t.indexOf(':') < 0 || t.indexOf('[') >= 0 && t.indexOf(':') > t.indexOf('[')) t = "minecraft:" + t;
        return state(t) == null ? null : t;
    }

    private static boolean emits(String entry) {
        BlockState s = state(entry.indexOf(':') < 0 ? "minecraft:" + entry : entry);
        return s != null && s.getLightEmission() > 0;
    }

    /** see-through (drawn with his glass) */
    private static boolean clear(String id) {
        return id.contains("glass") || id.endsWith(":honey_block") || id.endsWith(":ice")
                || id.endsWith(":slime_block");
    }

    /**
     * A built-in set worked out for one giant: each palette entry of his model that it changes, to its block.
     * share: each entry's part of all his blocks (null: none is small).
     */
    public static Map<String, String> map(String setId, String[] palette, double @Nullable [] share) {
        Set set = SETS.get(setId);
        Map<String, String> out = new LinkedHashMap<>();
        if (set == null) return out;
        List<Integer> solid = new ArrayList<>();
        for (int i = 0; i < palette.length; i++) {
            String e = palette[i], id = blockId(e), n = id.substring(id.indexOf(':') + 1);
            if (KEEP.contains(n)) continue;
            String shaped = shaped(set, e, n);
            if (shaped != null) {
                if (!shaped.equals(e)) out.put(e, shaped);
                continue;
            }
            if (!colours().containsKey(id)) continue;
            if (emits(e)) {
                List<String> g = set.glow();
                out.put(e, g.size() == 1 ? g.get(0) : pick(g, Math.min(1, lum(colourOf(id)) / 255)));
                continue;
            }
            if (clear(id)) {
                if (set.clear() != null) out.put(e, set.clear());
                continue;
            }
            solid.add(i);
        }
        if (solid.isEmpty()) return out;
        double[] ls = new double[solid.size()];
        for (int k = 0; k < ls.length; k++) ls[k] = lum(colourOf(blockId(palette[solid.get(k)])));
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double l : ls) {
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
        }
        for (int k = 0; k < ls.length; k++) {
            int i = solid.get(k);
            String e = palette[i];
            int[] c = colourOf(blockId(e));
            double l = ls[k];
            // (half by rank among his own, half by how bright it is: his shading kept, the whole ramp used)
            int below = 0;
            for (double x : ls) if (x < l) below++;
            double rank = below / (double) Math.max(1, ls.length - 1), lin = (l - lo) / Math.max(1, hi - lo);
            double t = 0.5 * rank + 0.5 * lin;
            boolean small = share != null && i < share.length && share[i] < ACCENT_SHARE;
            String to;
            if (!set.families().isEmpty()) {
                double s = sat(c), h = hue(c);
                String fam;
                if (s > 0.25 && (h < 60 || h > 300)) fam = "red";
                else if (s > 0.25 && h <= 200) fam = "green";
                else if (l > 170) fam = "white";
                else fam = t < 1 / 3.0 ? "green" : t < 2 / 3.0 ? "red" : "white";
                to = pick(set.families().get(fam), fam.equals("white") ? 0.5 : t);
                if (small && s > 0.6 && h > 30 && h < 70) to = pick(set.accent(), t);
            } else if (small && sat(c) > 0.55 && l > 35 && l < 215) to = pick(set.accent(), t);
            else to = pick(set.ramp(), t);
            if (!to.equals(e)) out.put(e, to);
        }
        return out;
    }

    /** a shaped block's own kind in the set (props kept), the entry itself to leave it, or null: not shaped */
    private static @Nullable String shaped(Set set, String entry, String n) {
        if (n.endsWith("_leaves")) return set.leaves() == null ? entry : set.leaves();
        if (n.endsWith("_fence")) return set.fence() == null ? entry : set.fence() + props(entry);
        if (n.endsWith("_trapdoor")) return set.trapdoor() == null ? entry : set.trapdoor() + props(entry);
        if (n.equals("lantern") || n.equals("soul_lantern"))
            return set.lantern() == null ? entry : set.lantern() + props(entry);
        if (n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_wall") || n.endsWith("_pane")
                || n.endsWith("_door") || n.endsWith("_button") || n.endsWith("_carpet")) return entry;
        return null;
    }

    /**
     * A custom set for one giant: its base (a built-in set, or none) and then the admin's own blocks, each one by the
     * block it replaces ("minecraft:bone_block" changes every entry of that block, whatever its props).
     */
    public static Map<String, String> custom(@Nullable String base, Map<String, String> own, String[] palette,
            double @Nullable [] share) {
        Map<String, String> out = base == null || base.isEmpty() ? new LinkedHashMap<>() : map(base, palette, share);
        for (String e : palette) {
            String to = own.get(blockId(e));
            if (to == null) continue;
            if (to.equals(blockId(e))) out.remove(e);
            else out.put(e, to);
        }
        return out;
    }

    /** each palette entry's part of all his blocks */
    public static double[] share(String[] palette, short[][] pal) {
        double[] s = new double[palette.length];
        long total = 0;
        for (short[] b : pal)
            for (short p : b) {
                if (p >= 0 && p < s.length) s[p]++;
                total++;
            }
        if (total > 0) for (int i = 0; i < s.length; i++) s[i] /= total;
        return s;
    }

    /** a look's key: its set and a short check of its blocks (a changed custom set is a new look, built afresh) */
    public static String key(String setId, Map<String, String> map) {
        int h = new java.util.TreeMap<>(map).hashCode();
        return setId + "#" + Integer.toHexString(h);
    }
}
